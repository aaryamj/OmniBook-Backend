package com.backend.service;

import com.backend.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemStateService {

    private final UserSessionRepository userSessionRepository;

    private boolean isLockdown = false;
    private boolean suspendApis = false;
    private boolean forceReadOnly = false;
    private boolean globalTokenEviction = false;

    public void initiateLockdown(boolean suspendApis, boolean forceReadOnly, boolean globalTokenEviction) {
        this.isLockdown = true;
        this.suspendApis = suspendApis;
        this.forceReadOnly = forceReadOnly;
        this.globalTokenEviction = globalTokenEviction;

        if (globalTokenEviction) {
            // Evict all sessions
            userSessionRepository.deleteAll();
        }
    }

    public void restoreSystem() {
        this.isLockdown = false;
        this.suspendApis = false;
        this.forceReadOnly = false;
        this.globalTokenEviction = false;
    }

    public boolean isLockdown() {
        return isLockdown;
    }

    public boolean isSuspendApis() {
        return suspendApis;
    }

    public boolean isForceReadOnly() {
        return forceReadOnly;
    }

    public boolean isGlobalTokenEviction() {
        return globalTokenEviction;
    }

    public Map<String, Object> getSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("platformState", isLockdown ? "LOCKED" : "ACTIVE");
        metrics.put("concurrentSessions", userSessionRepository.count());
        metrics.put("liveDatabaseWrites", isLockdown && suspendApis ? 0 : (int)(Math.random() * 50) + 100);
        metrics.put("circuitBreakerStatus", isLockdown ? "TRIPPED" : "ARMED");
        
        metrics.put("isLockdown", isLockdown);
        metrics.put("suspendApis", suspendApis);
        metrics.put("forceReadOnly", forceReadOnly);
        metrics.put("globalTokenEviction", globalTokenEviction);

        return metrics;
    }
}
