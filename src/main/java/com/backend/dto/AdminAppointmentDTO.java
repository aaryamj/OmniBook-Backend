package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAppointmentDTO {
    private String id;
    private String patientName;
    private String initials;
    private String date;
    private String time;
    private String providerName;
    private String department;
    private String status;
    private String paymentStatus;
}
