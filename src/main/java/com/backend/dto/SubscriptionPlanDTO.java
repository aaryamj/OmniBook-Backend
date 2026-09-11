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
public class SubscriptionPlanDTO {
    private Long id;
    private String name;
    private String displayName;
    private Double monthlyPrice;
    private Double annualPrice;
    private String description;
    private List<String> features;
    private String featuresRaw; // Raw newline-delimited text
    private Integer userLimit;
    private Integer appointmentLimit;
    private Boolean active;
    private Boolean isPopular;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
