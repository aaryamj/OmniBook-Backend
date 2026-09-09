package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminCreateRoleRequestDTO {
    private String roleTitle;
    private String roleScope;
    private String cloneBase;
    private Boolean requireMFA;
    private Map<String, Map<String, Boolean>> modulePermissions;
}
