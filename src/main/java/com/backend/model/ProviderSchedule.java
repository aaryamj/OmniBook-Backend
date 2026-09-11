package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "provider_schedules")
public class ProviderSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private String dayOfWeek; // e.g., "Monday", "Tuesday"

    @Column(nullable = false)
    private Boolean isActive;

    private LocalTime openingTime;
    
    private LocalTime closingTime;
    
    private LocalTime breakStartTime;
    
    private LocalTime breakEndTime;
    
    private String closedMessage;

    @Builder.Default
    @Column(name = "is_closed_by_admin")
    private Boolean isClosedByAdmin = false;
}
