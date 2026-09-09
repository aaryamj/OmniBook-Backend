package com.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementRequestDTO {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message body is required")
    private String messageBody;

    private String audience; // Target Specific Roles, All Users, All Tenants

    private String targetRoles; // comma-separated roles

    private String priority; // Low, Normal, Urgent

    private Boolean inAppBanner;

    private Boolean emailAlert;
}
