package com.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AISlotDTO {
    private String id;
    private String title;
    private String date;
    private String time;
    private String price;
    private String provider;
    private boolean topMatch;
    private String matchReason; // Explains why AI recommends this slot based on user history
    private String rawDate;     // YYYY-MM-DD
    private String rawTime;     // HH:mm
    private Long tenantId;
    private Long providerId;
    private String serviceName;
}
