package com.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DepartmentRequest {
    private String name;
    private String code;
    private String headName;
    private String description;

    @JsonAlias({"active", "isActive"})
    @JsonProperty("isActive")
    private Boolean isActive;

    public Boolean getIsActive() {
        return isActive != null ? isActive : Boolean.TRUE;
    }
}
