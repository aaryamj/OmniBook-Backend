package com.backend.service;

import com.backend.config.JwtService;
import com.backend.dto.AuthResponse;
import com.backend.dto.LoginRequest;
import com.backend.dto.RegisterRequest;
import com.backend.dto.VerifyRequest;
import com.backend.model.User;
import com.backend.repository.UserRepository;
import com.backend.model.ProviderProfile;
import com.backend.repository.ProviderProfileRepository;
import com.backend.model.UserSettings;
import com.backend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import com.backend.model.AuthProvider;
import com.backend.dto.OAuthLoginRequest;
import com.backend.dto.AcceptInviteRequest;
import com.backend.model.Invitation;
import com.backend.repository.InvitationRepository;
import com.backend.model.Tenant;
import com.backend.repository.TenantRepository;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProviderProfileRepository providerProfileRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final TenantRepository tenantRepository;
    private final UserSessionService userSessionService;

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

    public AuthResponse acceptInvite(AcceptInviteRequest request) {
        Optional<Invitation> inviteOptional = invitationRepository.findByToken(request.getToken());
        if (inviteOptional.isEmpty()) {
            return AuthResponse.builder().success(false).message("Invalid or expired invitation token").build();
        }

        Invitation invitation = inviteOptional.get();

        if (invitation.isUsed()) {
            return AuthResponse.builder().success(false).message("This invitation has already been used").build();
        }

        if (invitation.getExpiryDate().isBefore(LocalDateTime.now())) {
            return AuthResponse.builder().success(false).message("This invitation has expired").build();
        }

        String finalEmail = request.getEmail() != null && !request.getEmail().trim().isEmpty() ? request.getEmail() : invitation.getEmail();

        if (userRepository.findByEmail(finalEmail).isPresent()) {
            return AuthResponse.builder().success(false).message("A user with this email already exists").build();
        }

        Tenant tenant = invitation.getTenant();
        if (tenant != null) {
            boolean tenantUpdated = false;
            if (request.getOrganizationName() != null && !request.getOrganizationName().trim().isEmpty()) {
                tenant.setOrganizationName(request.getOrganizationName());
                tenantUpdated = true;
            }
            if (tenant.getPhoneContact() == null || tenant.getPhoneContact().isEmpty() || tenant.getPhoneContact().equals(invitation.getPhone())) {
                tenant.setPhoneContact(request.getPhone());
                tenantUpdated = true;
            }
            if (tenantUpdated) {
                tenantRepository.save(tenant);
            }
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(finalEmail)
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(invitation.getRole())
                .enabled(true)
                .tenant(tenant)
                .build();

        userRepository.save(user);

        if ("service_provider".equals(invitation.getRole()) || "provider".equals(invitation.getRole())) {
            ProviderProfile profile = new ProviderProfile();
            profile.setUser(user);
            profile.setTier(invitation.getTier());
            profile.setPrimarySpecialty(invitation.getSpecialization());
            providerProfileRepository.save(profile);
        }

        invitation.setUsed(true);
        invitationRepository.save(invitation);

        String jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .success(true)
                .message("Account setup successfully")
                .token(jwtToken)
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }

    public AuthResponse getInviteInfo(String token) {
        Optional<Invitation> inviteOptional = invitationRepository.findByToken(token);
        if (inviteOptional.isEmpty()) {
            return AuthResponse.builder().success(false).message("Invalid or expired invitation token").build();
        }

        Invitation invitation = inviteOptional.get();

        if (invitation.isUsed()) {
            return AuthResponse.builder().success(false).message("This invitation has already been used").build();
        }

        if (invitation.getExpiryDate().isBefore(LocalDateTime.now())) {
            return AuthResponse.builder().success(false).message("This invitation has expired").build();
        }

        String tier = invitation.getTenant() != null ? invitation.getTenant().getSubscriptionTier() : "Enterprise";
        return AuthResponse.builder()
                .success(true)
                .message(invitation.getEmail())
                .subscriptionTier(invitation.getTier())
                .specialization(invitation.getSpecialization())
                .fullName(invitation.getFullName())
                .phone(invitation.getPhone())
                .organizationName(invitation.getTenant() != null ? invitation.getTenant().getOrganizationName() : null)
                .build();
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
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

        if ("provider".equalsIgnoreCase(user.getRole()) || "service_provider".equalsIgnoreCase(user.getRole())) {
            java.util.Optional<com.backend.model.ProviderProfile> profileOpt = providerProfileRepository.findByUser(user);
            if (profileOpt.isPresent() && profileOpt.get().getStatus() == com.backend.model.ProviderStatus.SUSPENDED) {
                return AuthResponse.builder().success(false).message("Your access has been suspended. Please contact your clinic administrator.").build();
            }
        }

        if ("superadmin".equalsIgnoreCase(user.getRole()) || "super_admin".equalsIgnoreCase(user.getRole())) {
            notificationService.createNotification(
                    "System Access",
                    "Superadmin " + user.getFullName() + " has logged into the system.",
                    "AUTH_LOGIN"
            );
        }
        
        boolean tenantTwoStep = user.getTenant() != null && Boolean.TRUE.equals(user.getTenant().getTwoFactorEnabled());
        boolean userTwoStep = userSettingsRepository.findByUser(user).map(UserSettings::isTwoStepEnabled).orElse(false);
        boolean twoStepEnabled = tenantTwoStep || userTwoStep;
        
        if (twoStepEnabled) {
            String code = generateVerificationCode();
            user.setTwoFactorCode(code);
            user.setTwoFactorExpiry(LocalDateTime.now().plusMinutes(10));
            
            userRepository.save(user);
            emailService.sendVerificationEmail(user.getEmail(), code);
            
            return AuthResponse.builder()
                    .success(true)
                    .message("2FA verification required")
                    .requires2fa(true)
                    .email(user.getEmail())
                    .build();
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginLocation("Web Browser");
        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user);
        userSessionService.createSession(user, jwtToken, httpRequest);

        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .token(jwtToken)
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }

    public AuthResponse verify2FA(VerifyRequest request, HttpServletRequest httpRequest) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty()) {
            return AuthResponse.builder().success(false).message("User not found").build();
        }

        User user = userOptional.get();
        
        if (user.getTwoFactorCode() == null || !user.getTwoFactorCode().equals(request.getCode())) {
            return AuthResponse.builder().success(false).message("Invalid 2FA code").build();
        }
        
        if (user.getTwoFactorExpiry() != null && user.getTwoFactorExpiry().isBefore(LocalDateTime.now())) {
            return AuthResponse.builder().success(false).message("2FA code expired").build();
        }
        
        // Clear OTP
        user.setTwoFactorCode(null);
        user.setTwoFactorExpiry(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginLocation("Web Browser");
        userRepository.save(user);
        
        String jwtToken = jwtService.generateToken(user);
        userSessionService.createSession(user, jwtToken, httpRequest);

        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .token(jwtToken)
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }

    public AuthResponse processGoogleOAuth(OAuthLoginRequest request, HttpServletRequest httpRequest) {
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

            return handleOAuthUser(email, name, googleId, AuthProvider.GOOGLE, request.getRole(), httpRequest);
        } catch (Exception e) {
            e.printStackTrace();
            return AuthResponse.builder().success(false).message("Failed to verify Google token: " + e.getMessage()).build();
        }
    }

    public AuthResponse processFacebookOAuth(OAuthLoginRequest request, HttpServletRequest httpRequest) {
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

            return handleOAuthUser(email, name, facebookId, AuthProvider.FACEBOOK, request.getRole(), httpRequest);
        } catch (Exception e) {
            return AuthResponse.builder().success(false).message("Failed to verify Facebook token").build();
        }
    }

    private AuthResponse handleOAuthUser(String email, String name, String providerId, AuthProvider provider, String requestedRole, HttpServletRequest httpRequest) {
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
        userSessionService.createSession(user, jwtToken, httpRequest);

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
