package com.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProviderServiceRequest {
    private List<ServiceDto> services;

    @Data
    public static class ServiceDto {
        private String serviceName;
        private Integer durationMinutes;
        private Double fee;
        private Boolean isTelemedicine;
        private String category;
        private Boolean isActive;
    }
}
