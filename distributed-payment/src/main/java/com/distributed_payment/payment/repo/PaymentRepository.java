package com.distributed_payment.payment.repo;

import com.distributed_payment.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // Owner id alone is ambiguous (a Customer #3 and a Merchant #3 both exist),
    // so both sides of the OR must match id AND type together.
    @Query("""
            SELECT p FROM Payment p
            WHERE (p.senderOwnerId = :ownerId AND p.senderOwnerType = :ownerType)
               OR (p.receiverOwnerId = :ownerId AND p.receiverOwnerType = :ownerType)
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findForOwner(@Param("ownerId") Long ownerId, @Param("ownerType") String ownerType);
}
