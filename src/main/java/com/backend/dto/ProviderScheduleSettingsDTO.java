package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScheduleSettingsDTO {
    private String timezone;
    private Integer slotDuration;
    private Boolean isScheduleDelegated;
    private List<ProviderScheduleDTO> schedules;
}
