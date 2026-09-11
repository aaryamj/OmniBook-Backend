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
public class LedgerReconciliationDTO {
    private Double commissionRate;
    private Double grossVolumeUSD;
    private Double grossVolumeNPR;
    private Double stripeEscrow;
    private Double esewaSettled;
    private Double platformFeesUSD;
    private Double platformFeesNPR;
    private List<LedgerChartDataDTO> chartData;
    private List<LedgerTransactionDTO> transactions;
}
