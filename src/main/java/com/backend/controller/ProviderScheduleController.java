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
    public ResponseEntity<ProviderScheduleSettingsDTO> getSchedule(Principal principal, @PathVariable Long providerId) {
        return ResponseEntity.ok(providerScheduleService.getScheduleSettings(principal.getName(), providerId));
    }

    @GetMapping("/provider/schedule/me")
    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<ProviderScheduleSettingsDTO> getMySchedule(Principal principal) {
        return ResponseEntity.ok(providerScheduleService.getMyScheduleSettings(principal.getName()));
    }

    @PutMapping("/admin/providers/{providerId}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProviderScheduleSettingsDTO> updateSchedule(
            Principal principal,
            @PathVariable Long providerId,
            @RequestBody ProviderScheduleSettingsDTO request) {
        return ResponseEntity.ok(providerScheduleService.updateScheduleSettings(principal.getName(), providerId, request));
    }

    @PutMapping("/provider/schedule/me")
    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<ProviderScheduleSettingsDTO> updateMySchedule(
            Principal principal,
            @RequestBody ProviderScheduleSettingsDTO request) {
        return ResponseEntity.ok(providerScheduleService.updateMyScheduleSettings(principal.getName(), request));
    }
}
