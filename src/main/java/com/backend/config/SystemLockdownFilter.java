package com.backend.config;

import com.backend.service.SystemStateService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SystemLockdownFilter extends OncePerRequestFilter {

    private final SystemStateService systemStateService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Let auth endpoints and superadmin endpoints pass
        if (path.startsWith("/api/auth") || path.startsWith("/api/v1/superadmin")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (systemStateService.isLockdown()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"System is currently under global lockdown. All services are suspended.\"}");
            return;
        }

        if (systemStateService.isSuspendApis()) {
            // Block all other API requests
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"APIs are temporarily suspended.\"}");
            return;
        }

        if (systemStateService.isForceReadOnly()) {
            String method = request.getMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) 
                || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"System is in Read-Only mode. Write operations are disabled.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
