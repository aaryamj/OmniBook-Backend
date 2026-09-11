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
@Table(name = "appointment_lifecycle_events")
public class AppointmentLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long appointmentId;

    private Long tenantId;

    @Column(nullable = false)
    private String eventType; // BOOKED, CONFIRMED, CHECKED_IN, COMPLETED, RESCHEDULE_REQUESTED, RESCHEDULED, CANCELLATION_REQUESTED, CANCELLED, REFUND_REQUESTED, REFUND_PROCESSED, REFUND_FAILED, NO_SHOW

    private Long actorId;
    private String actorName;
    private String actorRole; // patient, client, student, admin, service_provider, system

    private String fromStatus;
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadataJson; // Stores old/new date/time, amounts, currency, exchange rate, policy details

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
