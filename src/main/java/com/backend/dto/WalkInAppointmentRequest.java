package com.backend.dto;

import lombok.Data;

@Data
public class WalkInAppointmentRequest {
    private String patientName;
    private String date;
    private String time;
    private String providerName;
    private String department;
}
