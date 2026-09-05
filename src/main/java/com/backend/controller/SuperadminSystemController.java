package com.backend.controller;

import com.backend.service.SystemStateService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/superadmin/system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SuperadminSystemController {

    private final SystemStateService systemStateService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        return ResponseEntity.ok(systemStateService.getSystemMetrics());
    }

    @PostMapping("/lockdown")
    public ResponseEntity<Map<String, Object>> initiateLockdown(@RequestBody LockdownRequest request) {
        systemStateService.initiateLockdown(
                request.isSuspendApis(),
                request.isForceReadOnly(),
                request.isGlobalTokenEviction()
        );
        return ResponseEntity.ok(systemStateService.getSystemMetrics());
    }

    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restoreSystem() {
        systemStateService.restoreSystem();
        return ResponseEntity.ok(systemStateService.getSystemMetrics());
    }

    @Data
    public static class LockdownRequest {
        private boolean suspendApis;
        private boolean forceReadOnly;
        private boolean globalTokenEviction;
    }
}
