package com.distributed_payment.wallet.repo;

import com.distributed_payment.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByOwnerIdAndOwnerType(Long ownerId, String ownerType);

    /**
     * SELECT ... FOR UPDATE. Used by credit/debit so that concurrent
     * transfers touching the same wallet serialize at the DB row level
     * instead of racing on the in-memory balance and colliding via
     * OptimisticLockException (which a Kafka consumer has no automatic
     * retry for).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.ownerId = :ownerId AND w.ownerType = :ownerType")
    Optional<Wallet> findByOwnerIdAndOwnerTypeForUpdate(
            @Param("ownerId") Long ownerId,
            @Param("ownerType") String ownerType
    );
}
