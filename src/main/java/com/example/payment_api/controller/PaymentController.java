
package com.example.payment_api.controller;

import com.example.payment_api.dto.PageResponse;
import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;
import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return paymentService.createPayment(request, idempotencyKey);
    }

    // Example: GET /api/payments?status=PENDING&page=0&size=20
    @GetMapping
    public PageResponse<PaymentResponse> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return paymentService.listPayments(status, page, size);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return paymentService.getPayment(id);
    }

    @PutMapping("/{id}")
    public PaymentResponse updatePayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        return paymentService.updatePayment(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
    }
}