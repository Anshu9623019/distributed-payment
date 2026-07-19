package com.distributed_payment.customer.controller;

import com.distributed_payment.customer.dto.CustomerLookupResponse;
import com.distributed_payment.customer.dto.CustomerResponse;
import com.distributed_payment.customer.dto.UpdateCustomerRequest;
import com.distributed_payment.customer.entity.Customer;
import com.distributed_payment.customer.repository.CustomerRepository;
import com.distributed_payment.customer.service.CustomerService;
import com.distributed_payment.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerService.getCustomerProfile(userDetails.getUsername()));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(customerService.updateCustomerProfile(userDetails.getUsername(), request));
    }

    // Lets the "send money" form resolve an email to a customer ID without
    // exposing the full profile of the person being paid.
    @GetMapping("/lookup")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerLookupResponse lookupByEmail(@RequestParam String email) {
        Customer customer = customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No customer found with email: " + email));
        return new CustomerLookupResponse(customer.getId(), customer.getUser().getFullName());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable Long id) {
        customerService.deactivateCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
