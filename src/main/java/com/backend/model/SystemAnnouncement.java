package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_announcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String messageBody;

    @Column(nullable = false)
    @Builder.Default
    private String audience = "All Users"; // Target Specific Roles, All Users, All Tenants

    private String targetRoles; // comma separated roles e.g. "Clinic Admin, Front Desk"

    @Column(nullable = false)
    @Builder.Default
    private String priority = "Normal"; // Low, Normal, Urgent

    @Builder.Default
    private Boolean inAppBanner = true;

    @Builder.Default
    private Boolean emailAlert = false;

    private String createdBy;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
