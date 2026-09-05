package com.backend.config;

import com.backend.service.SystemKPIService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiMetricsFilter implements Filter {

    @Autowired
    private SystemKPIService systemKPIService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            chain.doFilter(request, response);
            if (response instanceof HttpServletResponse) {
                int status = ((HttpServletResponse) response).getStatus();
                if (status >= 400) {
                    isError = true;
                }
            }
        } catch (Exception e) {
            isError = true;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            systemKPIService.recordApiRequest(duration, isError);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
