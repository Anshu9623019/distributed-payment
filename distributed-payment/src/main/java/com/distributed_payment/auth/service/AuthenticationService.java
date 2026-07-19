package com.distributed_payment.auth.service;

import com.distributed_payment.auth.dto.AuthResponse;
import com.distributed_payment.auth.dto.LoginRequest;
import com.distributed_payment.auth.dto.RegisterRequest;
import com.distributed_payment.auth.mapper.AuthMapper;
import com.distributed_payment.customer.entity.Customer;
import com.distributed_payment.customer.repository.CustomerRepository;
import com.distributed_payment.customer.repository.UserRepository;
import com.distributed_payment.exception.DuplicateResourceException;
import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.security.JwtService;
import com.distributed_payment.security.User;
import com.distributed_payment.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthMapper authMapper;
    private final WalletService walletService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateResourceException("Email is already registered");
        }

        User user = authMapper.toUserEntity(request);
        user.setPassword(passwordEncoder.encode(request.password()));
        User savedUser = userRepository.save(user);

        Customer customer = authMapper.toCustomerEntity(request);
        customer.setUser(savedUser);
        Customer savedCustomer = customerRepository.save(customer);

        walletService.ensureWalletExists(savedCustomer.getId(), "CUSTOMER", "USD");

        log.info("Registered new customer, userId={}", savedUser.getId());

        String jwtToken = jwtService.generateToken(savedUser);
        return new AuthResponse(jwtToken, "Bearer", jwtService.getExpirationMs());
    }

    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException on failure; handled by GlobalExceptionHandler.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.email()));

        log.info("Login successful, userId={}", user.getId());

        String jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken, "Bearer", jwtService.getExpirationMs());
    }
}
