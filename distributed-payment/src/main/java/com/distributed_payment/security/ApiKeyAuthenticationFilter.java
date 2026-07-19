package com.distributed_payment.security;



import com.distributed_payment.merchant.entity.Merchant;
import com.distributed_payment.merchant.repo.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String API_SECRET_HEADER = "X-API-SECRET";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);
        String apiSecret = request.getHeader(API_SECRET_HEADER);

        // If headers are missing, skip this filter and let the JWT filter handle it
        if (apiKey == null || apiSecret == null) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Authenticating B2B request via API Key: {}", apiKey);

        Optional<Merchant> merchantOpt = merchantRepository.findByApiKey(apiKey);

        if (merchantOpt.isPresent()) {
            Merchant merchant = merchantOpt.get();

            // Verify the raw incoming secret against the hashed database secret
            if (passwordEncoder.matches(apiSecret, merchant.getApiSecret())) {
                if (!"ACTIVE".equals(merchant.getStatus())) {
                    log.warn("Rejected API Key: Merchant {} is suspended.", merchant.getId());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Merchant account is suspended.");
                    return;
                }

                // Authentication successful, set the security context
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        merchant.getId(), // Principal is the Merchant ID
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("API Key authentication successful for Merchant ID: {}", merchant.getId());
            } else {
                log.warn("Invalid API Secret provided for Key: {}", apiKey);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Credentials");
                return;
            }
        } else {
            log.warn("API Key not found: {}", apiKey);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Credentials");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") ||
                path.startsWith("/api/v1/webhooks/") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-ui");
    }
}
