package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private String fullName;
    private String email;
    private String phone;
    private String country;
    private String profilePicture;
    private Boolean twoStepEnabled;
    
    // Patient Profile Fields
    private String dateOfBirth;
    private String bloodGroup;
    private String allergies;
    private String weight;
    private String heartRate;
    
    // Social Logins
    private Boolean isGoogleConnected;
    private String googleEmail;
    private Boolean isFacebookConnected;
    private String facebookEmail;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginLocation;

    private List<UserSessionDto> sessions;

    private Boolean notifBookingEmail;
    private Boolean notifBookingSms;
    private Boolean notifBookingInApp;

    private Boolean notifReminderEmail;
    private Boolean notifReminderSms;
    private Boolean notifReminderInApp;

    private Boolean notifCancellationEmail;
    private Boolean notifCancellationSms;
    private Boolean notifCancellationInApp;

    private Boolean notifExclusiveDiscounts;
    private Boolean notifNewsletter;
}
