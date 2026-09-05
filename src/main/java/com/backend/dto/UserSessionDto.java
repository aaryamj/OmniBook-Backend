package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionDto {
    private Long id;
    private String deviceOS;
    private String browser;
    private String ipAddress;
    private String location;
    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;
    private boolean isActive;
    private boolean isCurrentSession;
}
