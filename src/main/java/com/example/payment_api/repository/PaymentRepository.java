
package com.example.payment_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment_api.entity.Payment;
import com.example.payment_api.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Soft-deleted payments are treated as if they don't exist.
    Optional<Payment> findByIdAndDeletedFalse(Long id);

    // Served by idx_payments_deleted_id.
    Page<Payment> findAllByDeletedFalse(Pageable pageable);

    // Served by idx_payments_status_deleted_id.
    Page<Payment> findAllByDeletedFalseAndStatus(PaymentStatus status, Pageable pageable);

    // Includes deleted payments: a key stays used even after its payment is deleted.
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
