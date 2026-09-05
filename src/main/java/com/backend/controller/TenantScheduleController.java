package com.backend.controller;

import com.backend.dto.TenantScheduleSettingsDTO;
import com.backend.model.User;
import com.backend.service.TenantScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/schedule")
@RequiredArgsConstructor
public class TenantScheduleController {

    private final TenantScheduleService tenantScheduleService;

    @GetMapping
    public ResponseEntity<TenantScheduleSettingsDTO> getScheduleSettings(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(tenantScheduleService.getScheduleSettings(user.getEmail()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping
    public ResponseEntity<TenantScheduleSettingsDTO> updateScheduleSettings(
            @AuthenticationPrincipal User user,
            @RequestBody TenantScheduleSettingsDTO request) {
        try {
            return ResponseEntity.ok(tenantScheduleService.updateScheduleSettings(user.getEmail(), request));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
