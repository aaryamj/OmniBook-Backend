package com.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // Recipient of the notification (null if role-broadcast)

    private String targetRole; // e.g. SUPER_ADMIN, ADMIN, SERVICE_PROVIDER, USER

    private Long tenantId; // Organization / Tenant ID (null for platform SuperAdmin)

    private String title;
    
    private String message;
    
    private String type; // e.g. BOOKING, APPROVED, CHECKED_IN, COMPLETED, CANCELLED, RESCHEDULED, PAYMENT, TENANT_REGISTER, EMERGENCY_STOP
    
    private String link; // Frontend direct route link
    
    private boolean isRead;
    
    private LocalDateTime createdAt;
}
