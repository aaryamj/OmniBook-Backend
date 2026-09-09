package com.backend.dto;

import lombok.Data;

@Data
public class WalkInAppointmentRequest {
    private String patientName;
    private String patientEmail;
    private String patientPhone;
    private String date;
    private String time;
    private Long providerId;
    private String providerName;
    private String department;
    private String serviceName;
    private Double price;
    private String paymentStatus; // SUCCESS / PAID, PENDING
    private String paymentMethod; // CASH, IN_PERSON, ESEWA, CARD, COMPLIMENTARY
    private String appointmentStatus; // CHECKED_IN, SCHEDULED, IN_CONSULTATION, COMPLETED
    private String reasonForVisit;
    private String internalNotes;
}
