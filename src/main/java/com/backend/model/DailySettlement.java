package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "daily_settlements",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_settlement_date", columnNames = {"tenantId", "settlementDate"})
    }
)
public class DailySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    private String tenantName;

    private String organizationType;

    @Column(nullable = false)
    private LocalDate settlementDate;

    @Builder.Default
    private Integer totalAppointments = 0;

    @Builder.Default
    private Double grossRevenue = 0.0;

    @Builder.Default
    private Double totalRefunds = 0.0;

    @Builder.Default
    private Double netRetained = 0.0;

    @Builder.Default
    private Double platformCommissionRate = 10.0;

    @Builder.Default
    private Double platformCommission = 0.0;

    @Builder.Default
    private Double gatewayFees = 0.0;

    @Builder.Default
    private Double remainingOrgAmount = 0.0;

    @Builder.Default
    private Double providerPayoutsTotal = 0.0;

    @Builder.Default
    private Double orgAdminPayout = 0.0;

    @Builder.Default
    @Column(nullable = false)
    private String settlementStatus = "IN_ESCROW";

    @Builder.Default
    @Column(nullable = false)
    private Boolean isLocked = false;

    private LocalDateTime settledAt;

    private String settledBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isLocked == null) {
            isLocked = false;
        }
        if (settlementStatus == null) {
            settlementStatus = "IN_ESCROW";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
