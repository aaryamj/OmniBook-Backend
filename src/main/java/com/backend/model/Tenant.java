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

    @Column(columnDefinition = "varchar(50) default 'ACTIVE'")
    private String subscriptionStatus; // ACTIVE, EXPIRING_SOON, EXPIRED, SUSPENDED, RENEWAL_PENDING, CANCELLED

    private java.time.LocalDate subscriptionStartDate;

    private java.time.LocalDate subscriptionExpiryDate;

    @Column(columnDefinition = "varchar(50) default 'Monthly'")
    private String billingCycle;

    @Column(columnDefinition = "int default 0")
    private Integer emergencyExtensionDays;

    private String lastSuspendedReason;

    private String lastReminderDaysSent; // Comma-separated list of reminder thresholds sent, e.g. "7,3,1"

    // Security & Permissions fields
    @Column(columnDefinition = "boolean default false")
    private Boolean twoFactorEnabled;

    @Column(columnDefinition = "int default 30")
    private Integer sessionTimeout;

    @Column(columnDefinition = "boolean default false")
    private Boolean requireHipaa;

    // Cancellation & Refund Policy Configuration
    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private Boolean cancellationAllowed = true;

    @Builder.Default
    @Column(columnDefinition = "int default 6")
    private Integer cancellationDeadlineHours = 6;

    @Builder.Default
    @Column(columnDefinition = "int default 24")
    private Integer fullRefundHours = 24;

    @Builder.Default
    @Column(columnDefinition = "double default 50.0")
    private Double partialRefundPercentage = 50.0;

    @Builder.Default
    @Column(columnDefinition = "double default 0.0")
    private Double lateRefundPercentage = 0.0;

    // Rescheduling Policy Configuration
    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private Boolean reschedulingAllowed = true;

    @Builder.Default
    @Column(columnDefinition = "int default 2")
    private Integer maxReschedules = 2;

    @Builder.Default
    @Column(columnDefinition = "int default 6")
    private Integer reschedulingDeadlineHours = 6;

    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private Boolean autoProcessRefunds = true;

    // No-Show Configuration
    @Builder.Default
    @Column(columnDefinition = "int default 15")
    private Integer noShowGracePeriodMinutes = 15;

    @Builder.Default
    @Column(columnDefinition = "double default 0.0")
    private Double noShowRefundPercentage = 0.0;

    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private Boolean autoClassifyNoShow = true;

    // Organization Commission Configuration
    @Builder.Default
    @Column(name = "default_commission_rate", columnDefinition = "double default 10.0")
    private Double defaultCommissionRate = 10.0;

    public String getName() {
        return organizationName;
    }

    public void setName(String name) {
        this.organizationName = name;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public String getContactEmail() {
        return legalBusinessName; // or fallback
    }

    public String getContactPhone() {
        return phoneContact;
    }
}
