package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantScheduleDTO {
    private Long id;
    private String dayOfWeek;
    
    private Boolean isActive;
    
    private LocalTime openingTime;
    private LocalTime closingTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
    private String closedMessage;
}
