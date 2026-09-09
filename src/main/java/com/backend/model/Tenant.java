package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String organizationName;

    @Column(name = "organization_type")
    private String organizationType;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String status; // e.g., 'ACTIVE', 'INACTIVE', 'SUSPENDED'

    @Column(columnDefinition = "varchar(255)")
    private String address;


    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Profiling fields
    private String logoUrl;
    
    @Column(columnDefinition = "varchar(255) default '#0D9488'")
    private String primaryAccentColor;
    
    private String phoneContact;
    
    @Column(columnDefinition = "varchar(255) default 'Asia/Kathmandu'")
    private String timezone;
    
    private java.time.LocalTime openingTime;
    
    private java.time.LocalTime closingTime;
    
    private Integer slotDuration;

    // Financial Activation fields
    private String legalBusinessName;
    private String businessEntityType;
    private String ibanAccountNumber;
    private String medicalLicenseUrl;
    
    @Column(columnDefinition = "boolean default false")
    private Boolean kycVerified;

    @Column(columnDefinition = "varchar(255) default 'Enterprise'")
    private String subscriptionTier;

    // Security & Permissions fields
    @Column(columnDefinition = "boolean default false")
    private Boolean twoFactorEnabled;

    @Column(columnDefinition = "int default 30")
    private Integer sessionTimeout;

    @Column(columnDefinition = "boolean default false")
    private Boolean requireHipaa;
}
