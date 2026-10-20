
package com.example.payment_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc; // Importing the post method to simulate HTTP POST requests in the test.
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import com.jayway.jsonpath.JsonPath;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.repository.PaymentRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiIntegrationTest {

    @Autowired // Injecting the MockMvc instance to perform HTTP requests in the test context.
    private MockMvc mockMvc; // MockMvc is used to simulate HTTP requests and test the API endpoints without starting a full web server.

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldCreatePaymentAndReturn201() throws Exception {
        String requestJson = """
                {
                    "sourceAccount": "1234567890",
                    "destinationAccount": "0987654321",
                    "amount": 5000.00,
                    "currency": "NGN"
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("NGN"));
    }


    @Test
    void shouldRejectPaymentWithNegativeAmount() throws Exception {
        String requestJson = """
                {
                    "sourceAccount": "1234567890",
                    "destinationAccount": "0987654321",
                    "amount": -100,
                    "currency": "NGN"
                }
                """;

        mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Amount must be greater than zero"));
    }


    @Test
    void shouldReturn404WhenPaymentDoesNotExist() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/payments/999999")
                                .with(asUser())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Payment not found: 999999"));
    }

    @Test
    void shouldRejectUnauthenticatedPaymentRequest() throws Exception {
        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldUpdatePaymentAndReturnUpdatedData() throws Exception {
        Long id = createPayment();

        String updateJson = """
                {
                    "sourceAccount": "1111111111",
                    "destinationAccount": "2222222222",
                    "amount": 9000.00,
                    "currency": "NGN"
                }
                """;

        mockMvc.perform(put("/api/payments/" + id)
                        .with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sourceAccount").value("1111111111"))
                .andExpect(jsonPath("$.amount").value(9000.00));

        // GET must return the updated values, not a stale cached copy.
        mockMvc.perform(get("/api/payments/" + id)
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceAccount").value("1111111111"))
                .andExpect(jsonPath("$.amount").value(9000.00));
    }

    @Test
    void shouldReturn404WhenUpdatingMissingPayment() throws Exception {
        String updateJson = """
                {
                    "sourceAccount": "1111111111",
                    "destinationAccount": "2222222222",
                    "amount": 9000.00,
                    "currency": "NGN"
                }
                """;

        mockMvc.perform(put("/api/payments/999999")
                        .with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeletePaymentAndReturn204() throws Exception {
        Long id = createPayment();

        // Read first so the payment is cached, then make sure delete clears it.
        mockMvc.perform(get("/api/payments/" + id)
                        .with(asUser()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/payments/" + id)
                        .with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/payments/" + id)
                        .with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSoftDeletePaymentInDatabase() throws Exception {
        Long id = createPayment();

        mockMvc.perform(delete("/api/payments/" + id)
                        .with(asUser()))
                .andExpect(status().isNoContent());

        // The row is still in the table, just flagged as deleted.
        Payment payment = paymentRepository.findById(id).orElseThrow();
        assertTrue(payment.isDeleted());

        // A deleted payment can't be deleted or updated again.
        mockMvc.perform(delete("/api/payments/" + id)
                        .with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListOnlyNonDeletedPayments() throws Exception {
        Long keptId = createPayment();
        Long deletedId = createPayment();

        mockMvc.perform(delete("/api/payments/" + deletedId)
                        .with(asUser()))
                .andExpect(status().isNoContent());

        String keptPath = "$[?(@.id == " + keptId + ")]";
        String deletedPath = "$[?(@.id == " + deletedId + ")]";

        mockMvc.perform(get("/api/payments")
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(keptPath).isNotEmpty())
                .andExpect(jsonPath(deletedPath).isEmpty());
    }

    @Test
    void shouldReturn404WhenDeletingMissingPayment() throws Exception {
        mockMvc.perform(delete("/api/payments/999999")
                        .with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnSamePaymentWhenRequestIsRetriedWithSameKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestJson = """
                {
                    "sourceAccount": "1234567890",
                    "destinationAccount": "0987654321",
                    "amount": 5000.00,
                    "currency": "NGN"
                }
                """;

        String firstBody = mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long firstId = ((Number) JsonPath.read(firstBody, "$.id")).longValue();

        mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));

        // Only one row exists for this key.
        assertTrue(paymentRepository.findByIdempotencyKey(idempotencyKey).isPresent());
        long rowsForKey = paymentRepository.findAll().stream()
                .filter(p -> idempotencyKey.equals(p.getIdempotencyKey()))
                .count();
        assertEquals(1, rowsForKey);
    }

    @Test
    void shouldReturn422WhenKeyIsReusedWithDifferentBody() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sourceAccount": "1234567890",
                                    "destinationAccount": "0987654321",
                                    "amount": 5000.00,
                                    "currency": "NGN"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sourceAccount": "1234567890",
                                    "destinationAccount": "0987654321",
                                    "amount": 9999.00,
                                    "currency": "NGN"
                                }
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldReturn400WhenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "sourceAccount": "1234567890",
                                    "destinationAccount": "0987654321",
                                    "amount": 5000.00,
                                    "currency": "NGN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Idempotency-Key header is required"));
    }

    // A signed-in USER. Real login and token validation are covered in AuthIntegrationTest.
    private static RequestPostProcessor asUser() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Long createPayment() throws Exception {
        String requestJson = """
                {
                    "sourceAccount": "1234567890",
                    "destinationAccount": "0987654321",
                    "amount": 5000.00,
                    "currency": "NGN"
                }
                """;

        String body = mockMvc.perform(post("/api/payments")
                        .with(asUser())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
