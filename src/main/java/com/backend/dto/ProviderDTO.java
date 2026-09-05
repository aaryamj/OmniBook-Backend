package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDTO {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String primarySpecialty;
    private String tier;
    private String status;
    private String medicalLicense;
    private String profilePictureUrl;
    private String licenseImageUrl;
    private String credentials;
}
