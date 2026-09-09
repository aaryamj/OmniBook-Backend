package com.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleResponse {
    private Long id;
    private String roleName;
    private String accessScope;
    private String privilegeLevel;
    private String permissionsJson;
    private Long assignedUsers;
}
