package com.backend.dto;

import lombok.Data;

@Data
public class AcceptInviteRequest {
    private String token;
    private String password;
    private String fullName;
    private String phone;
    private String organizationName;
    private String email;
    private String profilePicture;
}
