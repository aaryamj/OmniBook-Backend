package com.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIChatRequestDTO {
    private String conversationId;
    private String message;
    private String userEmail;
    private Long userId;
    private String selectedClinic;
    private String selectedService;
    private String selectedProvider;
}
