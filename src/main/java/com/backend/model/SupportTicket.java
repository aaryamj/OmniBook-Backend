package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticketNumber; // e.g. #TK-8821

    @Column(nullable = false)
    private String organizationName; // e.g. Lalitpur Wellness Center

    private String organizationType; // e.g. Clinic, Salon, College, General

    private String requesterName;

    @Column(nullable = false)
    private String adminAccount; // email of requester

    @Column(nullable = false)
    private String issueType; // Critical (SLA), Billing Issues, Integration Bugs, Technical Issue

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private String priority = "Normal"; // Low, Normal, Urgent

    @Column(nullable = false)
    @Builder.Default
    private String status = "Open"; // Open, In Progress, Urgent, Resolved, Closed

    private Integer slaMinutesRemaining; // countdown minutes

    private Double resolutionHours; // e.g. 1.4

    private Double csatRating; // e.g. 98.4

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
