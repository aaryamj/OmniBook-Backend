package com.backend.dto;

import lombok.Data;

@Data
public class DepartmentRequest {
    private String name;
    private String description;
    private Boolean isActive;
}
