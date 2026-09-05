package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderStatusDTO {
    private String name;
    private String role; // e.g. General Physician
    private String status; // ACTIVE, ON_BREAK, OFFLINE
    private String profilePictureUrl;
}
