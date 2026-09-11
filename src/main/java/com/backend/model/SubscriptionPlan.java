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
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. "Starter", "Professional", "Enterprise"

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Double monthlyPrice;

    @Column(nullable = false)
    private Double annualPrice;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String features; // Newline-delimited list of features

    private Integer userLimit; // Max staff / providers, null or -1 for unlimited

    private Integer appointmentLimit; // Monthly appointment limit, null or -1 for unlimited

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean isPopular = false;

    private Integer displayOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) active = true;
        if (isPopular == null) isPopular = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
