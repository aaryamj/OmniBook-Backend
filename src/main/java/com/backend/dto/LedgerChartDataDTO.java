package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerChartDataDTO {
    private String date;
    private Double stripe;
    private Double esewa;
    private Boolean peak;
}
