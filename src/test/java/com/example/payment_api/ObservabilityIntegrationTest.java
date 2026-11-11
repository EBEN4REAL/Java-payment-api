package com.example.payment_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;

// Real HTTP server, because Actuator runs on its own management port.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ObservabilityIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    private final RestClient http = RestClient.builder()
            // Return every response as-is, so tests can assert on error statuses.
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .build();

    @Test
    void shouldExposeLivenessAndReadinessProbes() {
        assertEquals("UP", JsonPath.read(get(managementPort, "/actuator/health/liveness"), "$.status"));

        String readiness = get(managementPort, "/actuator/health/readiness");
        assertEquals("UP", JsonPath.read(readiness, "$.status"));
        assertEquals("UP", JsonPath.read(readiness, "$.components.db.status"));
        assertEquals("UP", JsonPath.read(readiness, "$.components.redis.status"));
    }

    @Test
    void shouldExposePrometheusMetricsIncludingTheCustomOnes() {
        String metrics = get(managementPort, "/actuator/prometheus");

        assertTrue(metrics.contains("outbox_events_pending"), "outbox backlog gauge");
        assertTrue(metrics.contains("outbox_events_oldest_age_seconds"), "outbox age gauge");
        assertTrue(metrics.contains("payments_processed_total"), "processed payments counter");
        assertTrue(metrics.contains("resilience4j_circuitbreaker_state"), "circuit breaker state");
        assertTrue(metrics.contains("application=\"payment-api\""), "common application tag");
    }

    @Test
    void shouldRefuseOtherActuatorEndpoints() {
        // Not exposed, so they fall through to the application's rules, which require a login.
        assertEquals(401, status(managementPort, "/actuator/env"));
        assertEquals(401, status(managementPort, "/actuator/beans"));
    }

    @Test
    void shouldNotServeActuatorOnTheApplicationPort() {
        assertTrue(status(serverPort, "/actuator/health") >= 400);
    }

    private String get(int port, String path) {
        return http.get().uri("http://localhost:" + port + path).retrieve().body(String.class);
    }

    private int status(int port, String path) {
        return http.get().uri("http://localhost:" + port + path).retrieve().toBodilessEntity().getStatusCode().value();
    }
}
