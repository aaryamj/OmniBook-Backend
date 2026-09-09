package com.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class PaymentRequestDTO {
    private Long tenantId;
    private Long providerId;
    private String patientName;
    private String patientPhone;
    private String patientEmail;
    private String reasonForVisit;
    private String serviceName;
    private List<String> selectedSlots; // Example: ["68-2024-11-11-0"]
    private Double totalAmount;
    private String paymentMethod; // e.g. ESEWA
    private String appointmentType; // e.g. IN_PERSON, VIRTUAL
    private Long userId;
}
