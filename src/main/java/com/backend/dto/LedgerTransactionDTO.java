package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionDTO {
    private String id;
    private String date;
    private String time;
    private String patientInitials;
    private String patientName;
    private String patientProfilePicture;
    private String service;
    private String patientColor;
    private String gateway;
    private String gatewayColor;
    private String amount;
    private String accountType;
    private String status;
    private String statusColor;
    private String statusDot;
}
