package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminClientDTO {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private boolean enabled;
    private String status; // ACTIVE, SUSPENDED
    private String profilePicture;

    // Demographics & Health Profile
    private LocalDate dateOfBirth;
    private Integer age;
    private String bloodGroup;
    private String allergies;
    private String weight;
    private String heartRate;

    // Auth & Security
    private String authProvider;
    private boolean googleConnected;
    private boolean facebookConnected;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginLocation;

    // Aggregates
    private int totalAppointments;
    private double totalSpend;
}
