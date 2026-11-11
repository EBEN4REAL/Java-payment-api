package com.example.payment_api.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.payment_api.client.BankClient;
import com.example.payment_api.client.BankRequestRejectedException;
import com.example.payment_api.client.BankTransferRequest;
import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.client.BankUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Calls the bank's transfer API over HTTP.
 *
 * <p>Wrapped in the "bank" circuit breaker: after repeated failures it stops calling the
 * bank for a while and fails fast with CallNotPermittedException, so a bank outage doesn't
 * tie up every consumer thread on timeouts. Configured under resilience4j.circuitbreaker.instances.bank.
 */
@Component
public class BankClientImpl implements BankClient {

    static final String CIRCUIT_BREAKER_NAME = "bank";

    private static final Logger log = LoggerFactory.getLogger(BankClientImpl.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    // bankRestClient: configured in BankClientConfig with the bank's base URL and timeouts.
    public BankClientImpl(RestClient bankRestClient, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restClient = bankRestClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

        // An open breaker pauses all payment processing, so make every state change visible.
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Bank circuit breaker: {}", event.getStateTransition()));
    }

    @Override
    public BankTransferResult transfer(BankTransferRequest request) {
        return circuitBreaker.executeSupplier(() -> callBank(request));
    }

    private BankTransferResult callBank(BankTransferRequest request) {
        BankTransferResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/transfers")
                    .header("Idempotency-Key", request.reference())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(BankTransferResponse.class);
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            // 5xx, timeouts and connection failures: the bank may recover.
            throw new BankUnavailableException("Bank unavailable for transfer " + request.reference(), ex);
        } catch (HttpClientErrorException ex) {
            throw new BankRequestRejectedException(
                    "Bank rejected transfer " + request.reference() + " with " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            // E.g. a response body that can't be read.
            throw new BankRequestRejectedException(
                    "Unreadable bank response for transfer " + request.reference(), ex);
        }

        return toResult(request, response);
    }

    private static BankTransferResult toResult(BankTransferRequest request, BankTransferResponse response) {
        if (response == null || response.status() == null) {
            throw new BankRequestRejectedException("Empty bank response for transfer " + request.reference());
        }
        return switch (response.status()) {
            case "ACCEPTED" -> new BankTransferResult.Accepted(requirePresent(response.transferId(), request));
            case "DECLINED" -> new BankTransferResult.Declined(requirePresent(response.reason(), request));
            default -> throw new BankRequestRejectedException(
                    "Unknown bank status '" + response.status() + "' for transfer " + request.reference());
        };
    }

    private static String requirePresent(String value, BankTransferRequest request) {
        if (value == null || value.isBlank()) {
            throw new BankRequestRejectedException("Incomplete bank response for transfer " + request.reference());
        }
        return value;
    }

    // The bank's response body: transferId is set when ACCEPTED, reason when DECLINED.
    record BankTransferResponse(String status, String transferId, String reason) {}
}
