package com.distributed_payment.settlement.repository;

import com.distributed_payment.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    List<Settlement> findAllByOrderByCreatedAtDesc();
}
