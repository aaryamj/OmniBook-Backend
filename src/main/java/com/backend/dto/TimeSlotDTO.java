package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDTO {
    private String id;
    private String title;
    private String date; // E.g., "Nov 23, 2024"
    private String time; // E.g., "10:00 AM"
    private String price; // E.g., "रू 1,500"
    private String provider; // E.g., "Dr. Sarah - Consultation"
    private Boolean topMatch;
    private String providerId;
    
    private Boolean isBreak;
    private String providerImageUrl;
}
