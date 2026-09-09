package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketResponseDTO {
    private Long id;
    private String ticketNumber; // e.g. #TK-8821
    private String organizationName;
    private String organizationType;
    private String requesterName;
    private String adminAccount;
    private String issueType; // Critical (SLA), Billing Issues, Integration Bugs, Technical Issue
    private String subject;
    private String message;
    private String priority; // Low, Normal, Urgent
    private String status; // Open, In Progress, Urgent, Resolved, Closed
    private String slaTimeRemaining; // e.g. "42 mins remaining"
    private String slaStatusClass; // e.g. "bg-green-500", "bg-yellow-500", "check_circle"
    private String slaTextColor; // e.g. "text-on-surface", "text-yellow-700 font-bold"
    private String statusBg; // e.g. "bg-blue-100"
    private String statusText; // e.g. "text-blue-700"
    private LocalDateTime createdAt;
}
