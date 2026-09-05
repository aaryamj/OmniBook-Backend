package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScheduleDTO {
    private Long id;
    private String dayOfWeek;
    private Boolean isActive;
    private Boolean isTenantActive;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
    private String closedMessage;
}
