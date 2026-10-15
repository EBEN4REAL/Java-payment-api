
package com.example.payment_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // Importing the post method to simulate HTTP POST requests in the test.
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.security.user.name=test-user",
    "spring.security.user.password=test-password",
    "spring.security.user.roles=USER"
})
class PaymentApiIntegrationTest {

    @Autowired // Injecting the MockMvc instance to perform HTTP requests in the test context.
    private MockMvc mockMvc; // MockMvc is used to simulate HTTP requests and test the API endpoints without starting a full web server.

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
                        .with(httpBasic("test-user", "test-password"))
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
                        .with(httpBasic("test-user", "test-password"))
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
                                .with(httpBasic("test-user", "test-password"))
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
}