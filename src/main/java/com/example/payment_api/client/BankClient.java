package com.example.payment_api.client;

public interface BankClient {

    /**
     * Asks the bank to transfer the money. Calling it again with the same reference
     * returns the original result instead of transferring twice.
     *
     * @throws BankUnavailableException if the bank can't be reached or fails; safe to retry
     * @throws BankRequestRejectedException if the bank rejects the request; don't retry
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException if the bank
     *         circuit breaker is open; retry later
     */
    BankTransferResult transfer(BankTransferRequest request);
}
