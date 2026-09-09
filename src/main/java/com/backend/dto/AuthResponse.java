package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String token;
    private String message;
    private boolean success;
    private String role;
    private String fullName;
    private String subscriptionTier;
    private String specialization;
    
    // 2FA support
    private boolean requires2fa;
    private String email;
    private String phone;
    private String organizationName;
    private String organizationType;
    private String primaryAccentColor;
    private String profilePicture;

    // Custom Tenant Role & Feature Permissions
    private String tenantRoleName;
    private String accessScope;
    private String privilegeLevel;
    private String permissionsJson;

    // Additional Identity Attributes
    private Long id;
    private Long tenantId;
    private String organizationLogo;
    private boolean twoStepEnabled;
}
