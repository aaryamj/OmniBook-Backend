package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private Long id;
    private String organizationName;
    private String registrationNumber;
    private String status;
    private LocalDateTime createdAt;
    private String address;
    
    // Profiling fields
    private String logoUrl;
    private String primaryAccentColor;
    private String phoneContact;
    private String timezone;
    private java.time.LocalTime openingTime;
    private java.time.LocalTime closingTime;
    private Integer slotDuration;

    // Financial Activation fields
    private String legalBusinessName;
    private String businessEntityType;
    private String ibanAccountNumber;
    private String medicalLicenseUrl;
    
    private Boolean kycVerified;

    // Computed or fetched fields
    private String adminEmail;
    private String subscriptionTier;
}
