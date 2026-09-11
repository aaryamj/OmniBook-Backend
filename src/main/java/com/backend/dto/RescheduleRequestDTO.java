package com.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleRequestDTO {
    @NotNull(message = "New appointment date is required")
    private LocalDate newDate;

    @NotNull(message = "New appointment time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime newTime;

    private String reason;
}
