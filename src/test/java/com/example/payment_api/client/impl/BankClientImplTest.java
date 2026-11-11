package com.example.payment_api.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.payment_api.client.BankRequestRejectedException;
import com.example.payment_api.client.BankTransferRequest;
import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.client.BankUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

class BankClientImplTest {

    private static final String TRANSFERS_URL = "http://bank.test/v1/transfers";

    private final BankTransferRequest request = new BankTransferRequest(
            "payment-42", "1234567890", "0987654321", new BigDecimal("5000.00"), "NGN");

    private MockRestServiceServer bank;
    private CircuitBreakerRegistry circuitBreakers;
    private BankClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://bank.test");
        bank = MockRestServiceServer.bindTo(builder).build();

        // Small window so the breaker opens after two failures; same failure rules as production.
        circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(BankUnavailableException.class)
                .ignoreExceptions(BankRequestRejectedException.class)
                .build());

        client = new BankClientImpl(builder.build(), circuitBreakers);
    }

    @Test
    void shouldReturnAcceptedWithTheBankTransferId() {
        bank.expect(requestTo(TRANSFERS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "payment-42"))
                .andExpect(jsonPath("$.reference").value("payment-42"))
                .andExpect(jsonPath("$.destinationAccount").value("0987654321"))
                .andExpect(jsonPath("$.amount").value(5000.00))
                .andRespond(withSuccess("""
                        {"status": "ACCEPTED", "transferId": "TRF-1"}
                        """, MediaType.APPLICATION_JSON));

        BankTransferResult result = client.transfer(request);

        assertEquals(new BankTransferResult.Accepted("TRF-1"), result);
        bank.verify();
    }

    @Test
    void shouldReturnDeclinedWithTheReason() {
        bank.expect(requestTo(TRANSFERS_URL))
                .andRespond(withSuccess("""
                        {"status": "DECLINED", "reason": "ACCOUNT_CLOSED"}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(new BankTransferResult.Declined("ACCOUNT_CLOSED"), client.transfer(request));
    }

    @Test
    void shouldTreatServerErrorsAsBankUnavailable() {
        bank.expect(requestTo(TRANSFERS_URL)).andRespond(withServiceUnavailable());

        assertThrows(BankUnavailableException.class, () -> client.transfer(request));
    }

    @Test
    void shouldTreatClientErrorsAsRejected() {
        bank.expect(requestTo(TRANSFERS_URL)).andRespond(withBadRequest());

        assertThrows(BankRequestRejectedException.class, () -> client.transfer(request));
    }

    @Test
    void shouldRejectAnUnknownStatus() {
        bank.expect(requestTo(TRANSFERS_URL))
                .andRespond(withSuccess("""
                        {"status": "MAYBE"}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(BankRequestRejectedException.class, () -> client.transfer(request));
    }

    @Test
    void shouldOpenTheCircuitAfterRepeatedOutagesAndStopCallingTheBank() {
        bank.expect(times(2), requestTo(TRANSFERS_URL)).andRespond(withServiceUnavailable());

        assertThrows(BankUnavailableException.class, () -> client.transfer(request));
        assertThrows(BankUnavailableException.class, () -> client.transfer(request));

        // Open: fails fast without an HTTP call.
        assertThrows(CallNotPermittedException.class, () -> client.transfer(request));
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker().getState());
        bank.verify(); // Exactly two requests reached the bank.
    }

    @Test
    void shouldNotOpenTheCircuitForRejectedRequests() {
        bank.expect(times(3), requestTo(TRANSFERS_URL)).andRespond(withBadRequest());

        for (int i = 0; i < 3; i++) {
            assertThrows(BankRequestRejectedException.class, () -> client.transfer(request));
        }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker().getState());
    }

    private CircuitBreaker circuitBreaker() {
        return circuitBreakers.circuitBreaker(BankClientImpl.CIRCUIT_BREAKER_NAME);
    }
}
