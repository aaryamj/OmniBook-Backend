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
import com.backend.repository.TenantRepository;
import com.backend.model.Tenant;
import com.backend.model.UserSession;
import com.backend.dto.UserSessionDto;
import com.backend.dto.UserProfileDto;
import com.backend.dto.PasswordUpdateDto;
import com.backend.dto.SocialLoginDto;
import com.backend.service.SocialLoginService;
import com.backend.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.time.LocalDateTime;

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
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SocialLoginService socialLoginService;
    private final UserSessionRepository userSessionRepository;
    private final FileStorageService fileStorageService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<String, Object> resp = new HashMap<>();
            resp.put("id", user.getId());
            resp.put("fullName", user.getFullName());
            resp.put("email", user.getEmail());
            resp.put("phone", user.getPhone());
            resp.put("role", user.getRole());
            resp.put("tenantId", user.getTenant() != null ? user.getTenant().getId() : null);
            if (user.getTenantRole() != null) {
                resp.put("tenantRoleId", user.getTenantRole().getId());
                resp.put("tenantRoleName", user.getTenantRole().getRoleName());
                resp.put("accessScope", user.getTenantRole().getAccessScope());
                resp.put("privilegeLevel", user.getTenantRole().getPrivilegeLevel());
                resp.put("permissionsJson", user.getTenantRole().getPermissionsJson());
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "Error fetching user info: " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> getUserAppointments() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String userEmail = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";
            Long userId = user.getId();
            String userPhone = user.getPhone() != null ? user.getPhone().trim() : "";

            List<Appointment> appointments = appointmentRepository.findAll().stream()
                    .filter(app -> {
                        // 1. If booked by this user account, it is strictly theirs
                        if (app.getBookedByUserId() != null && app.getBookedByUserId().equals(userId)) {
                            return true;
                        }
                        // 2. If patient email matches AND it wasn't booked by a different registered user account
                        if (app.getPatientEmail() != null && !app.getPatientEmail().isBlank() 
                                && app.getPatientEmail().trim().equalsIgnoreCase(userEmail)) {
                            return app.getBookedByUserId() == null || app.getBookedByUserId().equals(userId);
                        }
                        // 3. If phone matches for guest bookings
                        if (!userPhone.isBlank() && app.getPatientPhone() != null 
                                && app.getPatientPhone().trim().equals(userPhone)) {
                            return app.getBookedByUserId() == null || app.getBookedByUserId().equals(userId);
                        }
                        return false;
                    })
                    .sorted((a, b) -> {
                        if (a.getAppointmentDate() != null && b.getAppointmentDate() != null) {
                            int cmp = b.getAppointmentDate().compareTo(a.getAppointmentDate());
                            if (cmp != 0) return cmp;
                        }
                        if (a.getAppointmentTime() != null && b.getAppointmentTime() != null) {
                            return b.getAppointmentTime().compareTo(a.getAppointmentTime());
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> responseList = appointments.stream().map(app -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", app.getId());
                map.put("providerId", app.getProviderId());
                map.put("serviceName", app.getServiceName());
                map.put("appointmentDate", app.getAppointmentDate());
                map.put("appointmentTime", app.getAppointmentTime());
                map.put("appointmentStatus", app.getAppointmentStatus());
                map.put("reasonForVisit", app.getReasonForVisit());
                map.put("price", app.getPrice());
                String txId = app.getTransactionId();
                if (txId == null || txId.isBlank()) {
                    txId = "TXN-" + java.util.UUID.randomUUID().toString();
                    app.setTransactionId(txId);
                    appointmentRepository.save(app);
                }
                map.put("transactionId", txId);
                map.put("appointmentType", app.getAppointmentType());
                map.put("meetingLink", app.getMeetingLink());
                map.put("videoCallEnabled", Boolean.TRUE.equals(app.getVideoCallEnabled()) || "VIRTUAL".equalsIgnoreCase(app.getAppointmentType()));

                // Tenant details
                map.put("tenantId", app.getTenantId());
                Tenant appTenant = null;
                if (app.getTenantId() != null) {
                    appTenant = tenantRepository.findById(app.getTenantId()).orElse(null);
                    if (appTenant != null) {
                        map.put("organizationType", appTenant.getOrganizationType());
                        map.put("organizationName", appTenant.getOrganizationName());
                        map.put("organizationLogo", appTenant.getLogoUrl());
                        map.put("organizationAddress", appTenant.getAddress());
                        map.put("organizationPhone", appTenant.getPhoneContact());
                        map.put("primaryAccentColor", appTenant.getPrimaryAccentColor());
                    }
                }
                
                boolean isClinic = appTenant != null && "Clinic".equalsIgnoreCase(appTenant.getOrganizationType());

                // get doctor / provider info
                User provider = app.getProviderId() != null ? userRepository.findById(app.getProviderId()).orElse(null) : null;
                String docName = null;
                String docPic = null;
                String docSpecialty = null;
                if (provider != null) {
                    String pFullName = provider.getFullName() != null ? provider.getFullName() : "Unknown";
                    String prefix = (isClinic && !pFullName.toLowerCase().startsWith("dr.") ? "Dr. " : "");
                    docName = prefix + pFullName;
                    map.put("doctorName", docName);
                    ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
                    if (profile != null && profile.getProfilePictureUrl() != null && !profile.getProfilePictureUrl().trim().isEmpty()) {
                        docPic = profile.getProfilePictureUrl();
                    } else if (provider.getProfilePicture() != null && !provider.getProfilePicture().trim().isEmpty()) {
                        docPic = provider.getProfilePicture();
                    }
                    map.put("doctorProfilePicture", docPic);
                    if (profile != null && profile.getPrimarySpecialty() != null) {
                        docSpecialty = profile.getPrimarySpecialty();
                    } else if (provider.getSpecialization() != null) {
                        docSpecialty = provider.getSpecialization();
                    } else {
                        docSpecialty = app.getServiceName();
                    }
                    map.put("doctorSpecialty", docSpecialty);
                }

                // Patient details
                map.put("patientName", app.getPatientName());
                map.put("patientPhone", app.getPatientPhone());
                map.put("patientEmail", app.getPatientEmail());
                map.put("patientProfilePicture", user.getProfilePicture());

                // Notes & Feedback
                map.put("treatmentSummary", app.getTreatmentSummary());
                map.put("internalNotes", app.getInternalNotes());
                map.put("followUpDate", app.getFollowUpDate());
                map.put("feedbackToken", app.getFeedbackToken());
                map.put("patientRating", app.getPatientRating());
                map.put("patientReview", app.getPatientReview());

                // Lifecycle Timestamps & Attribution
                map.put("bookedAt", app.getBookedAt());
                map.put("bookedByName", app.getBookedByName() != null && !app.getBookedByName().isEmpty() ? app.getBookedByName() : app.getPatientName());
                map.put("bookedByRole", app.getBookedByRole() != null && !app.getBookedByRole().isEmpty() ? app.getBookedByRole() : "patient");

                map.put("approvedAt", app.getApprovedAt());
                String approvedBy = app.getApprovedByName();
                String approvedRole = app.getApprovedByRole();
                if (app.getApprovedAt() != null && (approvedBy == null || approvedBy.isEmpty())) {
                    approvedBy = docName != null ? docName : "Staff";
                    approvedRole = "service_provider";
                }
                map.put("approvedByName", approvedBy);
                map.put("approvedByRole", approvedRole);

                map.put("checkedInAt", app.getCheckedInAt());
                String checkedInBy = app.getCheckedInByName();
                String checkedInRole = app.getCheckedInByRole();
                if (app.getCheckedInAt() != null && (checkedInBy == null || checkedInBy.isEmpty())) {
                    checkedInBy = app.getCompletedByName() != null ? app.getCompletedByName() : (docName != null ? docName : "Front Desk Staff");
                    checkedInRole = "service_provider";
                }
                map.put("checkedInByName", checkedInBy);
                map.put("checkedInByRole", checkedInRole);

                map.put("completedAt", app.getCompletedAt());
                map.put("completedByName", app.getCompletedByName());
                map.put("completedByRole", app.getCompletedByRole() != null ? app.getCompletedByRole() : "service_provider");

                map.put("cancelledAt", app.getCancelledAt());
                map.put("cancelledByName", app.getCancelledByName());
                map.put("cancelledByRole", app.getCancelledByRole());
                map.put("cancellationReason", app.getCancellationReason());

                map.put("rejectedAt", app.getRejectedAt());
                map.put("rejectedByName", app.getRejectedByName());
                map.put("rejectedByRole", app.getRejectedByRole());
                map.put("rejectionReason", app.getRejectionReason());

                // Rescheduling fields
                map.put("rescheduleCount", app.getRescheduleCount() != null ? app.getRescheduleCount() : 0);
                map.put("originalAppointmentDate", app.getOriginalAppointmentDate());
                map.put("originalAppointmentTime", app.getOriginalAppointmentTime());
                map.put("rescheduledAt", app.getRescheduledAt());
                map.put("rescheduledByName", app.getRescheduledByName());
                map.put("rescheduledByRole", app.getRescheduledByRole());

                // Payment & Refund fields
                map.put("paymentMethod", app.getPaymentMethod());
                map.put("paymentStatus", app.getPaymentStatus());
                map.put("chargedAmount", app.getChargedAmount());
                map.put("chargedCurrency", app.getChargedCurrency());
                map.put("exchangeRate", app.getExchangeRate());
                map.put("originalNprAmount", app.getBasePriceNpr() != null ? app.getBasePriceNpr() : app.getPrice());
                map.put("gatewayPaymentRef", app.getGatewayPaymentRef());

                map.put("refundStatus", app.getRefundStatus());
                map.put("refundEligibilityPercentage", app.getRefundEligibilityPercentage());
                map.put("refundAmount", app.getRefundAmount());
                map.put("refundCurrency", app.getRefundCurrency());
                map.put("refundTransactionId", app.getRefundTransactionId());
                map.put("refundRequestedAt", app.getRefundRequestedAt());
                map.put("refundedAt", app.getRefundedAt());
                map.put("refundFailureReason", app.getRefundFailureReason());

                // Tenant Policy snapshot
                Map<String, Object> policyMap = new HashMap<>();
                policyMap.put("cancellationAllowed", appTenant != null && appTenant.getCancellationAllowed() != null ? appTenant.getCancellationAllowed() : true);
                policyMap.put("cancellationDeadlineHours", appTenant != null && appTenant.getCancellationDeadlineHours() != null ? appTenant.getCancellationDeadlineHours() : 6);
                policyMap.put("fullRefundHours", appTenant != null && appTenant.getFullRefundHours() != null ? appTenant.getFullRefundHours() : 24);
                policyMap.put("partialRefundPercentage", appTenant != null && appTenant.getPartialRefundPercentage() != null ? appTenant.getPartialRefundPercentage() : 50.0);
                policyMap.put("lateRefundPercentage", appTenant != null && appTenant.getLateRefundPercentage() != null ? appTenant.getLateRefundPercentage() : 0.0);
                policyMap.put("reschedulingAllowed", appTenant != null && appTenant.getReschedulingAllowed() != null ? appTenant.getReschedulingAllowed() : true);
                policyMap.put("maxReschedules", appTenant != null && appTenant.getMaxReschedules() != null ? appTenant.getMaxReschedules() : 2);
                policyMap.put("reschedulingDeadlineHours", appTenant != null && appTenant.getReschedulingDeadlineHours() != null ? appTenant.getReschedulingDeadlineHours() : 6);
                map.put("tenantPolicy", policyMap);

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
            
            String country = settings.getCountry();
            if (country == null || country.isBlank()) {
                String phone = user.getPhone() != null ? user.getPhone().trim() : "";
                if (phone.startsWith("+977") || phone.startsWith("977")) {
                    country = "+977";
                } else if (phone.length() == 10 && (phone.startsWith("98") || phone.startsWith("97"))) {
                    country = "+977"; // Standard Nepali mobile number format
                } else if (phone.startsWith("+91")) {
                    country = "+91";
                } else if (phone.startsWith("+44")) {
                    country = "+44";
                } else if (phone.startsWith("+61")) {
                    country = "+61";
                } else if (phone.startsWith("+1")) {
                    country = "+1";
                } else {
                    country = "+977"; // Default for Nepal / South Asia deployment
                }
            }
            dto.setCountry(country);
            dto.setRole(user.getRole());
            
            String profilePic = user.getProfilePicture();
            if (user.getTenant() != null) {
                dto.setOrganizationName(user.getTenant().getOrganizationName());
                dto.setOrganizationType(user.getTenant().getOrganizationType());
                dto.setLogoUrl(user.getTenant().getLogoUrl());
                if ((profilePic == null || profilePic.isBlank()) && "admin".equalsIgnoreCase(user.getRole()) && user.getTenant().getLogoUrl() != null) {
                    profilePic = user.getTenant().getLogoUrl();
                }

                // Add tenant admin details
                java.util.List<User> admins = userRepository.findByTenantIdAndRole(user.getTenant().getId(), "admin");
                if (!admins.isEmpty()) {
                    dto.setTenantAdminName(admins.get(0).getFullName());
                    dto.setTenantAdminEmail(admins.get(0).getEmail());
                } else if ("admin".equalsIgnoreCase(user.getRole())) {
                    dto.setTenantAdminName(user.getFullName());
                    dto.setTenantAdminEmail(user.getEmail());
                }
            }
            dto.setProfilePicture(profilePic);
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
            if (dto.getDateOfBirth() != null && !dto.getDateOfBirth().trim().isEmpty()) {
                try {
                    user.setDateOfBirth(java.time.LocalDate.parse(dto.getDateOfBirth().trim()));
                } catch (Exception e) {
                    user.setDateOfBirth(null);
                }
            } else if (dto.getDateOfBirth() != null && dto.getDateOfBirth().trim().isEmpty()) {
                user.setDateOfBirth(null);
            }

            if (dto.getBloodGroup() != null) {
                user.setBloodGroup(dto.getBloodGroup().trim().isEmpty() ? null : dto.getBloodGroup().trim());
            }
            if (dto.getAllergies() != null) {
                user.setAllergies(dto.getAllergies().trim().isEmpty() ? null : dto.getAllergies().trim());
            }
            if (dto.getWeight() != null) {
                user.setWeight(dto.getWeight().trim().isEmpty() ? null : dto.getWeight().trim());
            }
            if (dto.getHeartRate() != null) {
                user.setHeartRate(dto.getHeartRate().trim().isEmpty() ? null : dto.getHeartRate().trim());
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
            response.put("profilePicture", user.getProfilePicture());
            response.put("fullName", user.getFullName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping(value = "/profile-picture", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(err);
            }

            // Validate content type
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") 
                    && !contentType.equalsIgnoreCase("image/png") 
                    && !contentType.equalsIgnoreCase("image/webp")
                    && !contentType.equalsIgnoreCase("image/jpg"))) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Only JPG, PNG, and WebP image formats are supported");
                return ResponseEntity.badRequest().body(err);
            }

            // Validate file size (max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "File size cannot exceed 5MB");
                return ResponseEntity.badRequest().body(err);
            }

            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String fileName = fileStorageService.storeFile(file);
            String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(fileName)
                    .toUriString();

            user.setProfilePicture(fileUrl);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile picture updated successfully");
            response.put("profilePicture", fileUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error uploading profile picture: " + e.getMessage());
            return ResponseEntity.badRequest().body(err);
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
