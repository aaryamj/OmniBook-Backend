package com.backend.dto;

import lombok.Data;

@Data
public class OnboardClinicRequest {
    private String clinicName;
    private String registrationNumber;
    private String adminFullName;
    private String adminEmail;
    private String adminPhone;
    private String subscriptionTier;
    private String address;
    private Boolean enforce2FA;
    private Boolean requireHIPAA;
}
