package com.backend.config;

import com.backend.service.SystemStateService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            logger.warn("Invalid JWT token received: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
            return;
        }

        // --- GLOBAL TOKEN EVICTION CHECK ---
        // If the root system has evicted tokens, reject all stateless JWTs unless it's the superadmin
        SystemStateService systemStateService = null;
        try {
            systemStateService = org.springframework.web.context.support.WebApplicationContextUtils
                    .getRequiredWebApplicationContext(request.getServletContext())
                    .getBean(SystemStateService.class);
        } catch (Exception e) {
            // Context might not be fully initialized in some tests
        }

        if (systemStateService != null && systemStateService.isGlobalTokenEviction()) {
            if (userEmail != null && !userEmail.equals("superadmin@omnibook.com")) {
                logger.warn("Token evicted globally for user: " + userEmail);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Session has been globally terminated by administrator.\"}");
                return;
            }
        }
        // -----------------------------------

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                
                if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
                    // Extract tenant ID from token and set in context
                    Number tenantId = jwtService.extractClaim(jwt, claims -> claims.get("tenantId", Number.class));
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId.longValue());
                    } else {
                        TenantContext.clear();
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
                // User was deleted or not found, but token is still present. 
                logger.warn("Token received for non-existent user: " + userEmail);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User not found\"}");
                return;
            }
            
            // If we reached here and authentication is still null, it means the token was valid but the user is disabled
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Your account has been suspended or disabled\"}");
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear context to prevent memory leaks across threads
            TenantContext.clear();
        }
    }
}
