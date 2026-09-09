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
    private String patientEmail;
    private String patientPhone;
    private String patientProfilePicture;
    private String initials;
    private String date;
    private String time;
    private String providerName;
    private String doctorSpecialty;
    private String doctorProfilePicture;
    private String department;
    private String status;
    private String paymentStatus;
    private String appointmentType;
    private String meetingLink;
    private Boolean videoCallEnabled;
    private Boolean serviceAllowsVideo;
    private Double price;
    private String reasonForVisit;
    private String organizationType;
    private String organizationName;
    
    // Lifecycle attribution
    private String bookedAt;
    private String bookedByName;
    private String bookedByRole;
    private String approvedAt;
    private String approvedByName;
    private String approvedByRole;
    private String checkedInAt;
    private String checkedInByName;
    private String checkedInByRole;
    private String completedAt;
    private String completedByName;
    private String completedByRole;
    private String cancelledAt;
    private String cancelledByName;
    private String cancelledByRole;
    
    // Notes & Feedback
    private String treatmentSummary;
    private String internalNotes;
    private Integer patientRating;
    private String patientReview;
}
