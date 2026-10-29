package com.example.payment_api;

import com.example.payment_api.config.PaymentEventsProperties;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Needs the local Kafka broker from compose.yaml, like the other integration tests need MySQL and Redis.
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PaymentEventsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentEventsProperties eventsProperties;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void shouldPublishPaymentCreatedEventAndConsumeIt(CapturedOutput output) throws Exception {
        Long paymentId;

        try (KafkaConsumer<String, String> consumer = rawConsumer()) {
            // Start at the end of the topic so only events from this test are read.
            List<TopicPartition> partitions = consumer.partitionsFor(eventsProperties.topic())
                    .stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            partitions.forEach(consumer::position); // seekToEnd is lazy; resolve it before producing.

            paymentId = createPayment();

            ConsumerRecord<String, String> record = pollForKey(consumer, String.valueOf(paymentId));

            // The JSON body is the contract, so check it as another service would see it.
            String json = record.value();
            assertEquals(paymentId.intValue(), (Integer) JsonPath.read(json, "$.paymentId"));
            assertEquals("PENDING", JsonPath.read(json, "$.status"));
            assertEquals("NGN", JsonPath.read(json, "$.currency"));
            assertNotNull(UUID.fromString(JsonPath.read(json, "$.eventId")));
            assertNotNull(Instant.parse(JsonPath.read(json, "$.occurredAt")));
            assertNull(record.headers().lastHeader("__TypeId__"), "Java type names must not be sent");
        }

        // The app's own @KafkaListener receives the same event.
        await().atMost(Duration.ofSeconds(15))
                .until(() -> output.getOut().contains("Received PaymentCreated for payment " + paymentId + " "));
    }

    private Long createPayment() throws Exception {
        String body = mockMvc.perform(post("/api/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sourceAccount": "1234567890",
                                    "destinationAccount": "0987654321",
                                    "amount": 5000.00,
                                    "currency": "NGN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static ConsumerRecord<String, String> pollForKey(KafkaConsumer<String, String> consumer, String key) {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        return fail("No PaymentCreated event with key " + key + " within 15s");
    }

    // Reads values as plain strings, without the app's deserializer, and commits nothing.
    private KafkaConsumer<String, String> rawConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        ));
    }
}
