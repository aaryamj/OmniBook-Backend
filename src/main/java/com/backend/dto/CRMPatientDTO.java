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
public class CRMPatientDTO {
    private String id;
    private String initials;
    private String bgColor;
    private String textColor;
    private String name;
    private String phone;
    private String phoneType;
    private String lastVisit;
    private String provider;
    private String balance;
    private String balanceStatus;
    private String status;
    private String email;
    private Integer age;
    private String bloodGroup;
    private String allergies;
    private String patientSince;
    private String weight;
    private String heartRate;
    private List<CRMTransactionDTO> transactions;
    private String lifetimeBilledUSD;
    private String lifetimeBilledNPR;
    private String outstandingBalance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CRMTransactionDTO {
        private String id;
        private String method;
        private String amount;
        private String status;
        private String icon;
    }
}
