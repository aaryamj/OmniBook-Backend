package com.backend.dto;

import lombok.Data;

@Data
public class RoleRequest {
    private String roleName;
    private String accessScope;
    private String privilegeLevel;
    private String permissionsJson;
}
