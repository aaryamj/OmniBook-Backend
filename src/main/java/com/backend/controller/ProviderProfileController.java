package com.backend.controller;

import com.backend.dto.ProviderProfileDTO;
import com.backend.service.ProviderProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/provider/settings/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;

    @GetMapping
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<ProviderProfileDTO> getProfile(Principal principal) {
        return ResponseEntity.ok(providerProfileService.getProfile(principal.getName()));
    }

    @PutMapping
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<ProviderProfileDTO> updateProfile(
            Principal principal,
            @RequestBody ProviderProfileDTO request) {
        return ResponseEntity.ok(providerProfileService.updateProfile(principal.getName(), request));
    }

    @PostMapping("/picture")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<ProviderProfileDTO> uploadProfilePicture(
            Principal principal,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(providerProfileService.uploadProfilePicture(principal.getName(), file));
    }
}
