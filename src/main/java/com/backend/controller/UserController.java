package com.backend.controller;

import com.backend.model.Appointment;
import com.backend.model.ProviderProfile;
import com.backend.model.User;
import com.backend.model.UserSettings;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.UserRepository;
import com.backend.repository.UserSettingsRepository;
import com.backend.repository.UserSessionRepository;
import com.backend.model.UserSession;
import com.backend.dto.UserSessionDto;
import com.backend.dto.UserProfileDto;
import com.backend.dto.PasswordUpdateDto;
import com.backend.dto.SocialLoginDto;
import com.backend.service.SocialLoginService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final SocialLoginService socialLoginService;
    private final UserSessionRepository userSessionRepository;

    @GetMapping("/appointments")
    public ResponseEntity<?> getUserAppointments() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Appointment> appointments = appointmentRepository.findByPatientEmailOrderByAppointmentDateDesc(user.getEmail());

            List<Map<String, Object>> responseList = appointments.stream().map(app -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", app.getId());
                map.put("serviceName", app.getServiceName());
                map.put("appointmentDate", app.getAppointmentDate());
                map.put("appointmentTime", app.getAppointmentTime());
                map.put("appointmentStatus", app.getAppointmentStatus());
                map.put("reasonForVisit", app.getReasonForVisit());
                map.put("price", app.getPrice());
                map.put("transactionId", app.getTransactionId());
                map.put("appointmentType", app.getAppointmentType());
                map.put("meetingLink", app.getMeetingLink());
                
                // get doctor info
                User provider = userRepository.findById(app.getProviderId()).orElse(null);
                if (provider != null) {
                    map.put("doctorName", provider.getFullName());
                    ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
                    if (profile != null) {
                        map.put("doctorProfilePicture", profile.getProfilePictureUrl());
                        map.put("doctorSpecialty", profile.getPrimarySpecialty());
                    }
                }
                return map;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("appointments", responseList);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching appointments: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(HttpServletRequest request) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            UserSettings settings = userSettingsRepository.findByUser(user).orElse(new UserSettings());

            UserProfileDto dto = new UserProfileDto();
            dto.setFullName(user.getFullName());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setCountry(settings.getCountry() != null ? settings.getCountry() : "+1");
            dto.setProfilePicture(user.getProfilePicture());
            dto.setTwoStepEnabled(settings.isTwoStepEnabled());
            dto.setUpdatedAt(user.getUpdatedAt());
            
            // Patient Profile Fields
            dto.setDateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
            dto.setBloodGroup(user.getBloodGroup());
            dto.setAllergies(user.getAllergies());
            dto.setWeight(user.getWeight());
            dto.setHeartRate(user.getHeartRate());
            
            dto.setIsGoogleConnected(user.isGoogleConnected());
            dto.setGoogleEmail(user.getGoogleEmail());
            dto.setIsFacebookConnected(user.isFacebookConnected());
            dto.setFacebookEmail(user.getFacebookEmail());
            
            dto.setCreatedAt(user.getCreatedAt());
            dto.setLastLoginAt(user.getLastLoginAt());
            dto.setLastLoginLocation(user.getLastLoginLocation());
            
            dto.setNotifBookingEmail(settings.isNotifBookingEmail());
            dto.setNotifBookingSms(settings.isNotifBookingSms());
            dto.setNotifBookingInApp(settings.isNotifBookingInApp());
            dto.setNotifReminderEmail(settings.isNotifReminderEmail());
            dto.setNotifReminderSms(settings.isNotifReminderSms());
            dto.setNotifReminderInApp(settings.isNotifReminderInApp());
            dto.setNotifCancellationEmail(settings.isNotifCancellationEmail());
            dto.setNotifCancellationSms(settings.isNotifCancellationSms());
            dto.setNotifCancellationInApp(settings.isNotifCancellationInApp());
            dto.setNotifExclusiveDiscounts(settings.isNotifExclusiveDiscounts());
            dto.setNotifNewsletter(settings.isNotifNewsletter());

            // Map Sessions
            String currentToken = null;
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                currentToken = authHeader.substring(7);
            }
            String finalToken = currentToken;

            List<UserSession> sessions = userSessionRepository.findByUserIdOrderByLoginAtDesc(user.getId());
            List<UserSessionDto> sessionDtos = sessions.stream()
                .filter(UserSession::isActive)
                .map(s -> UserSessionDto.builder()
                    .id(s.getId())
                    .deviceOS(s.getDeviceOS())
                    .browser(s.getBrowser())
                    .ipAddress(s.getIpAddress())
                    .location(s.getLocation())
                    .loginAt(s.getLoginAt())
                    .lastActiveAt(s.getLastActiveAt())
                    .isActive(s.isActive())
                    .isCurrentSession(finalToken != null && finalToken.equals(s.getToken()))
                    .build())
                .collect(Collectors.toList());
            dto.setSessions(sessionDtos);

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody UserProfileDto dto) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setFullName(dto.getFullName());
            user.setPhone(dto.getPhone());
            if (dto.getProfilePicture() != null) {
                user.setProfilePicture(dto.getProfilePicture());
            }
            
            // Patient Profile Fields
            if (dto.getDateOfBirth() != null) {
                user.setDateOfBirth(java.time.LocalDate.parse(dto.getDateOfBirth()));
            }
            if (dto.getBloodGroup() != null) {
                user.setBloodGroup(dto.getBloodGroup());
            }
            if (dto.getAllergies() != null) {
                user.setAllergies(dto.getAllergies());
            }
            if (dto.getWeight() != null) {
                user.setWeight(dto.getWeight());
            }
            if (dto.getHeartRate() != null) {
                user.setHeartRate(dto.getHeartRate());
            }
            
            userRepository.save(user);

            UserSettings settings = userSettingsRepository.findByUser(user).orElse(new UserSettings());
            settings.setUser(user);
            settings.setCountry(dto.getCountry());
            if (dto.getTwoStepEnabled() != null) settings.setTwoStepEnabled(dto.getTwoStepEnabled());
            if (dto.getNotifBookingEmail() != null) settings.setNotifBookingEmail(dto.getNotifBookingEmail());
            if (dto.getNotifBookingSms() != null) settings.setNotifBookingSms(dto.getNotifBookingSms());
            if (dto.getNotifBookingInApp() != null) settings.setNotifBookingInApp(dto.getNotifBookingInApp());
            if (dto.getNotifReminderEmail() != null) settings.setNotifReminderEmail(dto.getNotifReminderEmail());
            if (dto.getNotifReminderSms() != null) settings.setNotifReminderSms(dto.getNotifReminderSms());
            if (dto.getNotifReminderInApp() != null) settings.setNotifReminderInApp(dto.getNotifReminderInApp());
            if (dto.getNotifCancellationEmail() != null) settings.setNotifCancellationEmail(dto.getNotifCancellationEmail());
            if (dto.getNotifCancellationSms() != null) settings.setNotifCancellationSms(dto.getNotifCancellationSms());
            if (dto.getNotifCancellationInApp() != null) settings.setNotifCancellationInApp(dto.getNotifCancellationInApp());
            if (dto.getNotifExclusiveDiscounts() != null) settings.setNotifExclusiveDiscounts(dto.getNotifExclusiveDiscounts());
            if (dto.getNotifNewsletter() != null) settings.setNotifNewsletter(dto.getNotifNewsletter());
            userSettingsRepository.save(settings);

            // Also update the User's updatedAt manually if needed
            user.setUpdatedAt(java.time.LocalDateTime.now());
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody PasswordUpdateDto dto) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Incorrect current password");
                return ResponseEntity.badRequest().body(response);
            }

            if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Passwords do not match");
                return ResponseEntity.badRequest().body(response);
            }

            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Password updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating password: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/connect/google")
    public ResponseEntity<?> connectGoogle(@RequestBody SocialLoginDto dto) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String googleEmail = socialLoginService.verifyGoogleTokenAndGetEmail(dto.getToken());
            user.setGoogleConnected(true);
            user.setGoogleEmail(googleEmail);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Google account connected successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error connecting Google: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/connect/facebook")
    public ResponseEntity<?> connectFacebook(@RequestBody SocialLoginDto dto) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String facebookEmail = socialLoginService.verifyFacebookTokenAndGetEmail(dto.getToken());
            user.setFacebookConnected(true);
            user.setFacebookEmail(facebookEmail);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Facebook account connected successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error connecting Facebook: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/disconnect/{provider}")
    public ResponseEntity<?> disconnectProvider(@PathVariable String provider) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if ("google".equalsIgnoreCase(provider)) {
                user.setGoogleConnected(false);
                user.setGoogleEmail(null);
            } else if ("facebook".equalsIgnoreCase(provider)) {
                user.setFacebookConnected(false);
                user.setFacebookEmail(null);
            } else {
                throw new RuntimeException("Unknown provider");
            }
            
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", provider + " disconnected successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error disconnecting " + provider + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<?> revokeOtherSessions(HttpServletRequest request) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String currentToken = null;
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                currentToken = authHeader.substring(7);
            }
            String finalToken = currentToken;

            List<UserSession> sessions = userSessionRepository.findByUserIdOrderByLoginAtDesc(user.getId());
            int revokedCount = 0;
            for (UserSession session : sessions) {
                if (session.isActive() && (finalToken == null || !finalToken.equals(session.getToken()))) {
                    session.setActive(false);
                    userSessionRepository.save(session);
                    revokedCount++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully logged out from " + revokedCount + " other session(s).");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error revoking sessions: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
