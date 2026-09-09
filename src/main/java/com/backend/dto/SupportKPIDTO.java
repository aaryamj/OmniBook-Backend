package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportKPIDTO {
    private long openTickets;
    private String avgResolution;
    private long slaBreaches;
    private String csatScore;
}
