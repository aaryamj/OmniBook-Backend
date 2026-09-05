package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfileDTO {
    private String fullName;
    private String email;
    private String phone;
    private String specialization;
    private String licenseNumber;
    private Boolean isScheduleDelegated;
    private String profilePictureUrl;
}
