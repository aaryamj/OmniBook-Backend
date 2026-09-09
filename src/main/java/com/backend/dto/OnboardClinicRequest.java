package com.backend.dto;

import lombok.Data;

@Data
public class OnboardClinicRequest {
    private String organizationName;
    private String registrationNumber;
    private String adminFullName;
    private String adminEmail;
    private String adminPhone;
    private String subscriptionTier;
    private String address;
    private String organizationType;
    private Boolean enforce2FA;
    private Boolean requireHIPAA;
}
