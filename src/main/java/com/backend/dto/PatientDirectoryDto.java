package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDirectoryDto {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String lastVisitDate;
    private String lastVisitReason;
    private String bookings;
    private String status; // 'Active', 'Inactive', 'Missed'
    private String noshows;
    private String avatarUrl;
}
