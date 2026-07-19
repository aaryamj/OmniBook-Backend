package com.backend.controller;

import com.backend.dto.AuthResponse;
import com.backend.dto.LoginRequest;
import com.backend.dto.RegisterRequest;
import com.backend.dto.VerifyRequest;
import com.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(AuthResponse.builder().success(false).message("Invalid credentials").build());
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyRequest request) {
        AuthResponse response = authService.verify(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<AuthResponse> googleOAuth(@RequestBody com.backend.dto.OAuthLoginRequest request) {
        AuthResponse response = authService.processGoogleOAuth(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/oauth/facebook")
    public ResponseEntity<AuthResponse> facebookOAuth(@RequestBody com.backend.dto.OAuthLoginRequest request) {
        AuthResponse response = authService.processFacebookOAuth(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
}
