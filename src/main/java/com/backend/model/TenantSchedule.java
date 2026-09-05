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
@Table(name = "tenant_schedules")
public class TenantSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String dayOfWeek; // e.g., "Monday", "Tuesday"

    @Column(nullable = false)
    private Boolean isActive;

    private LocalTime openingTime;
    
    private LocalTime closingTime;
    
    private LocalTime breakStartTime;
    
    private LocalTime breakEndTime;
    
    private String closedMessage;
}
