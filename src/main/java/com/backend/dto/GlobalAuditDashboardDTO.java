package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalAuditDashboardDTO {
    private long totalEvents;
    private long criticalAlerts;
    private long superadminActions;
    private double complianceScore;
    private double uptime;
    private List<Integer> logFrequency;
    private List<AuditLogEntryDTO> logs;
}
