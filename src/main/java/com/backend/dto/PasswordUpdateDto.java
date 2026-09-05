package com.backend.dto;

import lombok.Data;

@Data
public class PasswordUpdateDto {
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
}
