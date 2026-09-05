package com.backend.controller;

import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.UserRepository;
import com.backend.service.FileStorageService;
import com.backend.service.TenantService;
import com.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TenantController {

    private final TenantService tenantService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyTenant() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.getTenant() == null) {
                return ResponseEntity.badRequest().body("User does not belong to a tenant");
            }

            return ResponseEntity.ok(user.getTenant());
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching tenant details: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "primaryAccentColor", required = false) String primaryAccentColor,
            @RequestParam(value = "phoneContact", required = false) String phoneContact,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestParam(value = "openingTime", required = false) String openingTime,
            @RequestParam(value = "closingTime", required = false) String closingTime,
            @RequestParam(value = "slotDuration", required = false) Integer slotDuration,
            @RequestParam(value = "twoFactorEnabled", required = false) Boolean twoFactorEnabled,
            @RequestParam(value = "sessionTimeout", required = false) Integer sessionTimeout
    ) {
        try {
            // Get currently logged in user
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User admin = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getTenant() == null) {
                return ResponseEntity.badRequest().body("User does not belong to a tenant");
            }

            String logoUrl = null;
            if (logo != null && !logo.isEmpty()) {
                String fileName = fileStorageService.storeFile(logo);
                logoUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/")
                        .path(fileName)
                        .toUriString();
            }

            Tenant updatedTenant = tenantService.updateTenantProfile(
                    admin.getTenant().getId(),
                    primaryAccentColor,
                    phoneContact,
                    timezone,
                    openingTime,
                    closingTime,
                    slotDuration,
                    logoUrl,
                    twoFactorEnabled,
                    sessionTimeout
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile updated successfully");
            response.put("tenant", updatedTenant);

            auditLogService.logAction(admin, "Updated Tenant Settings/Profile", "127.0.0.1");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/financials")
    public ResponseEntity<?> updateFinancials(
            @RequestParam(value = "medicalLicense", required = false) MultipartFile medicalLicense,
            @RequestParam(value = "legalBusinessName", required = false) String legalBusinessName,
            @RequestParam(value = "businessEntityType", required = false) String businessEntityType,
            @RequestParam(value = "ibanAccountNumber", required = false) String ibanAccountNumber
    ) {
        try {
            // Get currently logged in user
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User admin = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getTenant() == null) {
                return ResponseEntity.badRequest().body("User does not belong to a tenant");
            }

            String medicalLicenseUrl = null;
            if (medicalLicense != null && !medicalLicense.isEmpty()) {
                String fileName = fileStorageService.storeFile(medicalLicense);
                medicalLicenseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/")
                        .path(fileName)
                        .toUriString();
            }

            Tenant updatedTenant = tenantService.updateTenantFinancials(
                    admin.getTenant().getId(),
                    legalBusinessName,
                    businessEntityType,
                    ibanAccountNumber,
                    medicalLicenseUrl
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Financial details updated successfully");
            response.put("tenant", updatedTenant);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating financials: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
