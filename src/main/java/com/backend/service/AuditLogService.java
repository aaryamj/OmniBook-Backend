package com.backend.service;

import com.backend.model.AuditLog;
import com.backend.model.User;
import com.backend.repository.AuditLogRepository;
import com.backend.dto.AuditLogEntryDTO;
import com.backend.dto.GlobalAuditDashboardDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void logAction(User user, String eventAction, String sourceIp) {
        AuditLog log = AuditLog.builder()
                .user(user)
                .eventAction(eventAction)
                .sourceIp(sourceIp)
                .tenant(user.getTenant())
                .build();
        auditLogRepository.save(log);
    }

    public List<AuditLog> getRecentLogs(Long tenantId) {
        return auditLogRepository.findTop20ByTenantIdOrderByTimestampDesc(tenantId);
    }

    public List<AuditLog> getGlobalLogs(LocalDateTime startDate) {
        if (startDate == null) {
            return auditLogRepository.findTop50ByOrderByTimestampDesc();
        }
        return auditLogRepository.findTop50ByTimestampAfterOrderByTimestampDesc(startDate);
    }

    public List<AuditLog> getGlobalLogsFiltered(String timeFilter) {
        LocalDateTime startDate = null;
        if (timeFilter != null) {
            switch (timeFilter) {
                case "Last 24 Hours":
                    startDate = LocalDateTime.now().minusDays(1);
                    break;
                case "Last 7 Days":
                    startDate = LocalDateTime.now().minusDays(7);
                    break;
                case "Last 30 Days":
                    startDate = LocalDateTime.now().minusDays(30);
                    break;
                case "This Quarter":
                    startDate = LocalDateTime.now().minusMonths(3);
                    break;
                case "This Year":
                    startDate = LocalDateTime.now().minusYears(1);
                    break;
            }
        }
        return getGlobalLogs(startDate);
    }

    public GlobalAuditDashboardDTO getGlobalDashboardMetrics(double uptime, String timeFilter) {
        LocalDateTime startDate = null;
        if (timeFilter != null) {
            switch (timeFilter) {
                case "Last 24 Hours":
                    startDate = LocalDateTime.now().minusDays(1);
                    break;
                case "Last 7 Days":
                    startDate = LocalDateTime.now().minusDays(7);
                    break;
                case "Last 30 Days":
                    startDate = LocalDateTime.now().minusDays(30);
                    break;
                case "This Quarter":
                    startDate = LocalDateTime.now().minusMonths(3);
                    break;
                case "This Year":
                    startDate = LocalDateTime.now().minusYears(1);
                    break;
            }
        }
        
        long totalEvents = startDate == null ? auditLogRepository.count() : auditLogRepository.countByTimestampAfter(startDate);
        long criticalAlerts = startDate == null ? 
                auditLogRepository.countByEventActionIn(Arrays.asList("FAILED_LOGIN", "TENANT_SUSPENDED", "API_ERROR")) :
                auditLogRepository.countByEventActionInAndTimestampAfter(Arrays.asList("FAILED_LOGIN", "TENANT_SUSPENDED", "API_ERROR"), startDate);
        long superadminActions = startDate == null ? 
                auditLogRepository.countByUserRole("super_admin") :
                auditLogRepository.countByUserRoleAndTimestampAfter("super_admin", startDate);
        
        List<Object[]> frequencyRaw = auditLogRepository.getLogFrequencyLast24Hours();
        List<Integer> logFrequency = new ArrayList<>(24);
        // Default to a small graph if empty, else parse
        if (frequencyRaw == null || frequencyRaw.isEmpty()) {
            for (int i=0; i<24; i++) logFrequency.add(0);
        } else {
            // Very simple mapping for the sake of the graph
            for (Object[] row : frequencyRaw) {
                if (row[0] != null) {
                    logFrequency.add(((Number) row[0]).intValue());
                }
            }
            // Pad to at least 12 elements for UI
            while(logFrequency.size() < 12) {
                logFrequency.add(0, 0);
            }
        }

        List<AuditLogEntryDTO> recentLogs = getGlobalLogs(startDate).stream().map(this::mapToDTO).collect(Collectors.toList());

        return GlobalAuditDashboardDTO.builder()
                .totalEvents(totalEvents)
                .criticalAlerts(criticalAlerts)
                .superadminActions(superadminActions)
                .complianceScore(100.0) // Mocked baseline score
                .uptime(uptime)
                .logFrequency(logFrequency)
                .logs(recentLogs)
                .build();
    }

    private AuditLogEntryDTO mapToDTO(AuditLog log) {
        boolean isCritical = log.getEventAction().contains("FAIL") || log.getEventAction().contains("SUSPEND") || log.getEventAction().contains("ERROR");
        
        String actionType = log.getEventAction();
        String category = "All Logs";
        String colorClass = "bg-surface-container text-on-surface-variant";
        
        if (actionType.contains("LOGIN") || actionType.contains("AUTH")) {
            category = "Auth Events";
            colorClass = isCritical ? "bg-error text-white animate-pulse" : "bg-green-50 text-green-700";
        } else if (actionType.contains("TENANT") || actionType.contains("PROVIDER")) {
            category = "Tenant Mutated";
            colorClass = isCritical ? "bg-red-100 text-red-800" : "bg-purple-50 text-purple-700";
        } else {
            category = "Security";
            colorClass = isCritical ? "bg-red-100 text-red-800" : "bg-blue-50 text-blue-700";
        }

        String initials = "";
        String email = log.getUser().getEmail();
        if (email != null && email.length() > 0) {
            initials = email.substring(0, 1).toUpperCase();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return AuditLogEntryDTO.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp().format(formatter))
                .isCritical(isCritical)
                .actorEmail(email)
                .actorInitials(initials)
                .actorBg(isCritical ? "bg-error" : "bg-primary-container")
                .actorText("text-white")
                .eventType(actionType)
                .eventCategory(category)
                .eventColor(colorClass)
                .tenantName(log.getTenant() != null ? log.getTenant().getOrganizationName() : "Global Platform")
                .ipAddress(log.getSourceIp())
                .build();
    }
}
