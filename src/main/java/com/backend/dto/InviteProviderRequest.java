package com.backend.dto;

import lombok.Data;

@Data
public class InviteProviderRequest {
    private String email;
    private String name;
    private String specialization;
    private String tier;
}
