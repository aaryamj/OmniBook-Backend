package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntryDTO {
    private Long id;
    private String timestamp;
    private boolean isCritical;
    private String actorEmail;
    private String actorInitials;
    private String actorBg;
    private String actorText;
    private String eventType;
    private String eventColor;
    private String eventCategory;
    private String tenantName;
    private String ipAddress;
}
