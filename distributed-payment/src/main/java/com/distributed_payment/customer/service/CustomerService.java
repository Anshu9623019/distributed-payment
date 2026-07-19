package com.distributed_payment.customer.service;

import com.distributed_payment.customer.dto.CustomerResponse;
import com.distributed_payment.customer.dto.UpdateCustomerRequest;
import com.distributed_payment.customer.entity.Customer;
import com.distributed_payment.customer.mapper.CustomerMapper;
import com.distributed_payment.customer.repository.CustomerRepository;
import com.distributed_payment.customer.repository.UserRepository;
import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.security.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerMapper customerMapper;

    public CustomerResponse getCustomerProfile(String email) {
        Customer customer = customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for: " + email));
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse updateCustomerProfile(String email, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for: " + email));

        User user = customer.getUser();
        user.setFullName(request.fullName());
        userRepository.save(user);

        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        Customer updated = customerRepository.save(customer);

        log.info("Updated profile for customerId={}", updated.getId());
        return customerMapper.toResponse(updated);
    }

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deactivateCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));

        User user = customer.getUser();
        user.setEnabled(false);
        userRepository.save(user);

        log.info("Deactivated customerId={}", id);
    }
}
