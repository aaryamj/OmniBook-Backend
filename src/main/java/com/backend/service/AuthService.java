package com.backend.service;

import com.backend.config.JwtService;
import com.backend.dto.AuthResponse;
import com.backend.dto.LoginRequest;
import com.backend.dto.RegisterRequest;
import com.backend.dto.VerifyRequest;
import com.backend.model.User;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import com.backend.model.AuthProvider;
import com.backend.dto.OAuthLoginRequest;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthResponse register(RegisterRequest request) {
        // Check uniqueness
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return AuthResponse.builder().success(false).message("Email already in use").build();
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty() && userRepository.findByPhone(request.getPhone()).isPresent()) {
            return AuthResponse.builder().success(false).message("Phone number already in use").build();
        }

        String verificationCode = generateVerificationCode();

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .enabled(false) // Wait for verification
                .verificationCode(verificationCode)
                .build();

        userRepository.save(user);

        // Send email
        emailService.sendVerificationEmail(user.getEmail(), verificationCode);

        return AuthResponse.builder()
                .success(true)
                .message("Registration successful. Please check your email for verification code.")
                .build();
    }

    public AuthResponse verify(VerifyRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty()) {
            return AuthResponse.builder().success(false).message("User not found").build();
        }

        User user = userOptional.get();
        if (user.isEnabled()) {
            return AuthResponse.builder().success(false).message("User already verified").build();
        }

        if (!user.getVerificationCode().equals(request.getCode())) {
            return AuthResponse.builder().success(false).message("Invalid verification code").build();
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        userRepository.save(user);

        return AuthResponse.builder()
                .success(true)
                .message("Account verified successfully. You can now login.")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getIdentity(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getIdentity())
                .or(() -> userRepository.findByPhone(request.getIdentity()))
                .orElseThrow();

        if (!user.isEnabled()) {
            return AuthResponse.builder().success(false).message("Account not verified").build();
        }

        String jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .token(jwtToken)
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }

    public AuthResponse processGoogleOAuth(OAuthLoginRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + request.getToken();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> payload = response.getBody();
            if (payload == null || !payload.containsKey("email")) {
                return AuthResponse.builder().success(false).message("Invalid Google token").build();
            }
            String email = (String) payload.get("email");
            String name = (String) payload.get("name");
            String googleId = (String) payload.get("sub");

            return handleOAuthUser(email, name, googleId, AuthProvider.GOOGLE, request.getRole());
        } catch (Exception e) {
            return AuthResponse.builder().success(false).message("Failed to verify Google token").build();
        }
    }

    public AuthResponse processFacebookOAuth(OAuthLoginRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://graph.facebook.com/me?fields=id,name,email&access_token=" + request.getToken();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> payload = response.getBody();
            if (payload == null || !payload.containsKey("email")) {
                return AuthResponse.builder().success(false).message("Invalid Facebook token").build();
            }
            String email = (String) payload.get("email");
            String name = (String) payload.get("name");
            String facebookId = (String) payload.get("id");

            return handleOAuthUser(email, name, facebookId, AuthProvider.FACEBOOK, request.getRole());
        } catch (Exception e) {
            return AuthResponse.builder().success(false).message("Failed to verify Facebook token").build();
        }
    }

    private AuthResponse handleOAuthUser(String email, String name, String providerId, AuthProvider provider, String requestedRole) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Optional: update provider/providerId if not set
            if (user.getAuthProvider() == AuthProvider.LOCAL) {
                user.setAuthProvider(provider);
                user.setProviderId(providerId);
                user.setEnabled(true); // OAuth verifies email implicitly
                userRepository.save(user);
            }
        } else {
            // Create new user
            String roleToSet = (requestedRole != null && !requestedRole.isEmpty()) ? requestedRole : "user";
            user = User.builder()
                    .fullName(name)
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(roleToSet)
                    .enabled(true)
                    .authProvider(provider)
                    .providerId(providerId)
                    .build();
            userRepository.save(user);
        }

        String jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .token(jwtToken)
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
