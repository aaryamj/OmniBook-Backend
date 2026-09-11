package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPolicyDTO {
    private Long tenantId;
    private String organizationName;
    private String organizationType;

    // Cancellation rules
    private Boolean cancellationAllowed;
    private Integer cancellationDeadlineHours; // e.g. 6 hours before appointment
    private Integer fullRefundHours;           // e.g. 24 hours before appointment (100% refund)
    private Double partialRefundPercentage;    // e.g. 50.0% refund between 6h and 24h
    private Double lateRefundPercentage;       // e.g. 0.0% refund if < 6h or no-show

    // Rescheduling rules
    private Boolean reschedulingAllowed;
    private Integer maxReschedules;            // e.g. 2 times
    private Integer reschedulingDeadlineHours; // e.g. 6 hours before appointment

    private Boolean autoProcessRefunds;

    // No-Show rules
    private Integer noShowGracePeriodMinutes; // e.g. 15 minutes after appointment time
    private Double noShowRefundPercentage;    // default 0.0%
    private Boolean autoClassifyNoShow;       // default true

    // Commission Configuration
    private Double defaultCommissionRate;     // Organization baseline commission percentage
}
