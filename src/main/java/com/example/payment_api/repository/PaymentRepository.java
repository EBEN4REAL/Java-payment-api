
package com.example.payment_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment_api.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Soft-deleted payments are treated as if they don't exist.
    Optional<Payment> findByIdAndDeletedFalse(Long id);

    List<Payment> findAllByDeletedFalseOrderByIdAsc();

    // Includes deleted payments: a key stays used even after its payment is deleted.
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
