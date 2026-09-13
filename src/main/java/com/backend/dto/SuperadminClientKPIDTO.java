package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminClientKPIDTO {
    private long totalClients;
    private long activeClients;
    private long suspendedClients;
    private long totalBookings;
    private long newClientsThisWeek;
}
