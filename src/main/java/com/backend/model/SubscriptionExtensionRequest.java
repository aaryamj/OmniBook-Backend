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
@Table(name = "subscription_extension_requests")
public class SubscriptionExtensionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private Integer requestedDays;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(nullable = false)
    private String requestedByEmail;

    private String requestedByName;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING"; // "PENDING", "APPROVED", "REJECTED"

    private LocalDate previousExpiryDate;

    private LocalDate newExpiryDate;

    private Integer approvedDays;

    private String processedByEmail; // Super Admin email

    private String processedByName;

    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String adminNotes; // Super Admin decision justification / notes

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    @Transient
    private Tenant tenant;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
        if (tenant != null) {
            this.tenantId = tenant.getId();
            this.organizationName = tenant.getName();
        }
    }

    public String getRequestedBy() {
        return requestedByEmail;
    }

    public void setRequestedBy(String email) {
        this.requestedByEmail = email;
    }

    public String getReviewedBy() {
        return processedByEmail;
    }

    public void setReviewedBy(String email) {
        this.processedByEmail = email;
    }

    public LocalDateTime getReviewedAt() {
        return processedAt;
    }

    public void setReviewedAt(LocalDateTime at) {
        this.processedAt = at;
    }
}
