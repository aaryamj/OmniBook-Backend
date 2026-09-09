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
    
    @com.fasterxml.jackson.annotation.JsonProperty("isActive")
    @com.fasterxml.jackson.annotation.JsonAlias({"active", "isActive", "is_active"})
    private Boolean isActive;
    
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime openingTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime closingTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime breakStartTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime breakEndTime;

    private String closedMessage;
}
