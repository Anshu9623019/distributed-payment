package com.distributed_payment.merchant.repo;

import com.distributed_payment.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    Optional<Merchant> findByApiKey(String apiKey);
    Optional<Merchant> findByUserId(Long userId);
    List<Merchant> findByStatus(String status);
}