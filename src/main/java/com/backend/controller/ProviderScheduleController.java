package com.backend.controller;

import com.backend.dto.ProviderScheduleSettingsDTO;
import com.backend.service.ProviderScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ProviderScheduleController {

    private final ProviderScheduleService providerScheduleService;

    @GetMapping("/admin/providers/{providerId}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getSchedule(Principal principal, @PathVariable Long providerId) {
        try {
            return ResponseEntity.ok(providerScheduleService.getScheduleSettings(principal.getName(), providerId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/provider/schedule/me")
    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<?> getMySchedule(Principal principal) {
        try {
            return ResponseEntity.ok(providerScheduleService.getMyScheduleSettings(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/admin/providers/{providerId}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSchedule(
            Principal principal,
            @PathVariable Long providerId,
            @RequestBody ProviderScheduleSettingsDTO request) {
        try {
            return ResponseEntity.ok(providerScheduleService.updateScheduleSettings(principal.getName(), providerId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/provider/schedule/me")
    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<ProviderScheduleSettingsDTO> updateMySchedule(
            Principal principal,
            @RequestBody ProviderScheduleSettingsDTO request) {
        return ResponseEntity.ok(providerScheduleService.updateMyScheduleSettings(principal.getName(), request));
    }
}
