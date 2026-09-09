package com.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketRequestDTO {

    private String requesterName;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email address is required")
    private String email;

    private String category; // technical, billing, onboarding, feature, or direct issue type

    @NotBlank(message = "Message description is required")
    private String message;

    private String organizationName;

    private String organizationType;
}
