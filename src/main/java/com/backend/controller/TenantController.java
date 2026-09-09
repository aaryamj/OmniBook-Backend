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

            Tenant tenant = user.getTenant();
            Map<String, Object> response = new HashMap<>();
            response.put("id", tenant.getId());
            response.put("organizationName", tenant.getOrganizationName());
            response.put("organizationType", tenant.getOrganizationType());
            response.put("registrationNumber", tenant.getRegistrationNumber());
            response.put("status", tenant.getStatus());
            response.put("address", tenant.getAddress());
            response.put("logoUrl", tenant.getLogoUrl());
            response.put("primaryAccentColor", tenant.getPrimaryAccentColor());
            response.put("phoneContact", tenant.getPhoneContact());
            response.put("timezone", tenant.getTimezone());
            response.put("openingTime", tenant.getOpeningTime());
            response.put("closingTime", tenant.getClosingTime());
            response.put("slotDuration", tenant.getSlotDuration());
            response.put("subscriptionTier", tenant.getSubscriptionTier());
            response.put("twoFactorEnabled", tenant.getTwoFactorEnabled());
            response.put("sessionTimeout", tenant.getSessionTimeout());
            response.put("requireHipaa", tenant.getRequireHipaa());
            response.put("createdAt", tenant.getCreatedAt());

            // Add official admin details for this tenant
            java.util.List<User> admins = userRepository.findByTenantIdAndRole(tenant.getId(), "admin");
            if (!admins.isEmpty()) {
                User adminUser = admins.get(0);
                response.put("adminName", adminUser.getFullName());
                response.put("adminEmail", adminUser.getEmail());
                response.put("adminPhone", adminUser.getPhone());
                response.put("adminProfilePicture", adminUser.getProfilePicture());
            } else if ("admin".equalsIgnoreCase(user.getRole())) {
                response.put("adminName", user.getFullName());
                response.put("adminEmail", user.getEmail());
                response.put("adminPhone", user.getPhone());
                response.put("adminProfilePicture", user.getProfilePicture());
            }

            if (user.getTenantRole() != null) {
                response.put("tenantRoleId", user.getTenantRole().getId());
                response.put("tenantRoleName", user.getTenantRole().getRoleName());
                response.put("accessScope", user.getTenantRole().getAccessScope());
                response.put("privilegeLevel", user.getTenantRole().getPrivilegeLevel());
                response.put("permissionsJson", user.getTenantRole().getPermissionsJson());
            } else {
                response.put("tenantRoleId", null);
                response.put("tenantRoleName", null);
                response.put("accessScope", null);
                response.put("privilegeLevel", null);
                response.put("permissionsJson", null);
            }
            response.put("currentUserId", user.getId());
            response.put("currentUserRole", user.getRole());
            response.put("currentUserFullName", user.getFullName());

            return ResponseEntity.ok(response);
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

            if (logoUrl != null) {
                admin.setProfilePicture(logoUrl);
                userRepository.save(admin);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile updated successfully");
            response.put("tenant", updatedTenant);
            response.put("profilePicture", logoUrl != null ? logoUrl : updatedTenant.getLogoUrl());

            auditLogService.logAction(admin, "Updated Tenant Settings/Profile", "127.0.0.1");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/branding")
    public ResponseEntity<?> updateBranding(@RequestBody Map<String, String> request) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User admin = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getTenant() == null) {
                return ResponseEntity.badRequest().body("User does not belong to a tenant");
            }

            String primaryAccentColor = request.get("primaryAccentColor");
            Tenant updatedTenant = tenantService.updateTenantProfile(
                    admin.getTenant().getId(),
                    primaryAccentColor,
                    null, null, null, null, null, null, null, null
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Branding updated successfully");
            response.put("primaryAccentColor", updatedTenant.getPrimaryAccentColor());

            auditLogService.logAction(admin, "Updated Institutional Branding color to " + primaryAccentColor, "127.0.0.1");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating branding: " + e.getMessage());
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
