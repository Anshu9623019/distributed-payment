package com.distributed_payment.customer.repository;

import com.distributed_payment.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUserEmail(String email);
    Optional<Customer> findByUserId(Long userId);
}
