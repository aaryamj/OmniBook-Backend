package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperadminRBACDTO {
    private long totalRoles;
    private String twoFactorEnforcement;
    private long activeTenantsCount;
    private String securityPosture;

    private List<String> systemRoleColumns;
    private List<RBACFeatureRowDTO> systemFeatureRows;

    private List<TenantRoleItemDTO> tenantCustomRoles;
    private Map<String, IndustryTemplateDTO> industryTemplates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RBACFeatureRowDTO {
        private String feature;
        private String icon;
        private String module;
        private List<Boolean> permissions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantRoleItemDTO {
        private Long id;
        private String roleName;
        private String tenantName;
        private String tenantType;
        private String accessScope;
        private String privilegeLevel;
        private long assignedUsers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryTemplateDTO {
        private List<String> columns;
        private List<RBACFeatureRowDTO> rows;
    }
}
