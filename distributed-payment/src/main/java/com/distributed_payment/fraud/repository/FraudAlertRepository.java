package com.distributed_payment.fraud.repository;

import com.distributed_payment.fraud.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {
    List<FraudAlert> findByResolvedFalseOrderByCreatedAtAsc();
}
