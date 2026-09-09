package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String patientPhone;

    private String patientEmail;

    @Column(columnDefinition = "TEXT")
    private String reasonForVisit;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime appointmentTime;

    private String appointmentType; // IN_PERSON, VIRTUAL
    
    private String meetingLink; // URL for telehealth

    private Boolean videoCallEnabled; // Whether provider enabled virtual video consultation

    private String serviceName;

    private Double price;

    @Column(nullable = false)
    private String paymentStatus; // PENDING, SUCCESS, FAILED

    private String paymentMethod; // ESEWA

    private String transactionId; // Reference ID from payment gateway
    
    @Column(nullable = false)
    private String appointmentStatus = "PENDING_APPROVAL"; // PENDING_APPROVAL, SCHEDULED, CHECKED_IN, COMPLETED, CANCELLED

    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String treatmentSummary;

    @Column(columnDefinition = "TEXT")
    private String internalNotes;

    private LocalDate followUpDate;

    private String feedbackToken;

    private Integer patientRating;

    @Column(columnDefinition = "TEXT")
    private String patientReview;

    private LocalDateTime bookedAt;
    private String bookedByName;
    private String bookedByRole;
    private Long bookedByUserId;

    private LocalDateTime approvedAt;
    private String approvedByName;
    private String approvedByRole;
    private Long approvedByUserId;

    private LocalDateTime checkedInAt;
    private String checkedInByName;
    private String checkedInByRole;
    private Long checkedInByUserId;

    private LocalDateTime completedAt;
    private String completedByName;
    private String completedByRole;
    private Long completedByUserId;

    private String cancelledByName;
    private String cancelledByRole;
    private Long cancelledByUserId;
    private LocalDateTime cancelledAt;

    @Transient
    private String patientProfilePicture;

    @Transient
    private String doctorProfilePicture;

    @Transient
    private String doctorName;

    @Transient
    private String doctorSpecialty;
    
    @Transient
    private String organizationType;

    @Transient
    private String organizationName;

    @Transient
    private Boolean serviceAllowsVideo;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (bookedAt == null) {
            bookedAt = LocalDateTime.now();
        }
        if (appointmentStatus == null) {
            appointmentStatus = "PENDING_APPROVAL";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
