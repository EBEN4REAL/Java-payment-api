package com.example.payment_api;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.example.payment_api.config.PaymentEventsProperties;
import com.example.payment_api.messaging.EventHeaders;
import com.jayway.jsonpath.JsonPath;

/**
 * The whole flow against real infrastructure: API -> MySQL + outbox -> relay -> Kafka ->
 * processor -> bank (WireMock) -> MySQL + outbox -> Kafka. Needs everything in compose.yaml.
 * The simulated bank's rules are in infra/wiremock.
 */
@SpringBootTest(properties = {
        "payment.processing.enabled=true",
        // Short retries so the dead-letter tests finish quickly.
        "payment.processing.retry.initial-interval=100ms",
        "payment.processing.retry.max-interval=200ms",
        "payment.processing.retry.max-retries=2",
        // The outage test fails on purpose; keep the breaker closed so it can't block the other tests.
        // Resilience4j caps minimum calls at the window size, so both are raised.
        "resilience4j.circuitbreaker.instances.bank.sliding-window-size=1000",
        "resilience4j.circuitbreaker.instances.bank.minimum-number-of-calls=1000"
})
@AutoConfigureMockMvc
// Stops this context's processor afterwards, so it can't race with test classes that edit payments.
@DirtiesContext
class PaymentEventsIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentEventsProperties eventsProperties;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaConsumer<String, String> eventsTopic;
    private KafkaConsumer<String, String> deadLetterTopic;

    // Positioned at the end before each test, so only records produced by the test are read.
    @BeforeEach
    void openTopics() {
        eventsTopic = openAtEnd(eventsProperties.topic());
        deadLetterTopic = openAtEnd(eventsProperties.deadLetterTopic());
    }

    @AfterEach
    void closeTopics() {
        eventsTopic.close();
        deadLetterTopic.close();
    }

    @Test
    void shouldCompleteThePaymentWhenTheBankAccepts() throws Exception {
        Long paymentId = createPayment("0987654321");

        ConsumerRecord<String, String> created = awaitEvent(eventsTopic, paymentId, "PaymentCreated");
        assertEquals(paymentId.intValue(), (Integer) JsonPath.read(created.value(), "$.paymentId"));
        assertNotNull(UUID.fromString(header(created, EventHeaders.EVENT_ID)));
        assertNull(created.headers().lastHeader("__TypeId__"), "Java type names must not be sent");

        ConsumerRecord<String, String> completed = awaitEvent(eventsTopic, paymentId, "PaymentCompleted");
        String bankReference = JsonPath.read(completed.value(), "$.bankReference");
        assertTrue(bankReference.startsWith("TRF-"), bankReference);

        String payment = awaitStatus(paymentId, "COMPLETED");
        assertEquals(bankReference, JsonPath.read(payment, "$.bankReference"));

        // One trace from the HTTP request to the completion event, across the outbox, Kafka and the bank call.
        assertEquals(traceId(created), traceId(completed));
    }

    @Test
    void shouldFailThePaymentWhenTheBankDeclines() throws Exception {
        Long paymentId = createPayment("0000000000");

        ConsumerRecord<String, String> failed = awaitEvent(eventsTopic, paymentId, "PaymentFailed");
        assertEquals("ACCOUNT_CLOSED", JsonPath.read(failed.value(), "$.reason"));

        String payment = awaitStatus(paymentId, "FAILED");
        assertEquals("ACCOUNT_CLOSED", JsonPath.read(payment, "$.failureReason"));
    }

    @Test
    void shouldDeadLetterTheEventWhenTheBankStaysDown() throws Exception {
        Long paymentId = createPayment("9999999999");

        ConsumerRecord<String, String> deadLettered = awaitRecord(deadLetterTopic, String.valueOf(paymentId), r -> true);

        assertEquals("PaymentCreated", header(deadLettered, EventHeaders.EVENT_TYPE));
        assertTrue(header(deadLettered, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).endsWith("BankUnavailableException"));
        // Claimed but never finished: replaying the event from the DLT resumes it.
        awaitStatus(paymentId, "PROCESSING");
    }

    @Test
    void shouldDeadLetterTheEventImmediatelyWhenTheBankRejectsTheRequest() throws Exception {
        Long paymentId = createPayment("4000000000");

        ConsumerRecord<String, String> deadLettered = awaitRecord(deadLetterTopic, String.valueOf(paymentId), r -> true);

        assertTrue(header(deadLettered, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).endsWith("BankRequestRejectedException"));
    }

    @Test
    void shouldRejectEditsOnceThePaymentIsProcessed() throws Exception {
        Long paymentId = createPayment("0987654321");
        awaitStatus(paymentId, "COMPLETED");

        mockMvc.perform(put("/api/payments/" + paymentId)
                        .with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("0987654321")))
                .andExpect(status().isConflict());
    }

    private Long createPayment(String destinationAccount) throws Exception {
        String body = mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(destinationAccount)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // Reads through the API, so this also checks the cache is evicted when the status changes.
    private String awaitStatus(Long paymentId, String expectedStatus) {
        AtomicReference<String> body = new AtomicReference<>();
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() -> {
            body.set(mockMvc.perform(get("/api/payments/" + paymentId).with(asUser()))
                    .andReturn().getResponse().getContentAsString());
            return expectedStatus.equals(JsonPath.read(body.get(), "$.status"));
        });
        return body.get();
    }

    private ConsumerRecord<String, String> awaitEvent(
            KafkaConsumer<String, String> consumer, Long paymentId, String eventType) {
        return awaitRecord(consumer, String.valueOf(paymentId),
                record -> eventType.equals(header(record, EventHeaders.EVENT_TYPE)));
    }

    private static ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer, String key, Predicate<ConsumerRecord<String, String>> matches) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key()) && matches.test(record)) {
                    return record;
                }
            }
        }
        return fail("No matching record with key " + key + " within " + TIMEOUT);
    }

    // traceparent is "00-<trace id>-<span id>-<flags>" (W3C Trace Context).
    private static String traceId(ConsumerRecord<String, String> record) {
        String traceparent = header(record, "traceparent");
        assertNotNull(traceparent, "missing traceparent header");
        return traceparent.split("-")[1];
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    // Reads values as plain strings, without the app's deserializer, and commits nothing.
    private KafkaConsumer<String, String> openAtEnd(String topic) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        ));
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position); // seekToEnd is lazy; resolve it before producing.
        return consumer;
    }

    private static String paymentJson(String destinationAccount) {
        return """
                {
                    "sourceAccount": "1234567890",
                    "destinationAccount": "%s",
                    "amount": 5000.00,
                    "currency": "NGN"
                }
                """.formatted(destinationAccount);
    }

    private static JwtRequestPostProcessor asUser() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
