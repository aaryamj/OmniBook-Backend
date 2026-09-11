package com.backend.controller;

import com.backend.model.ProviderProfile;
import com.backend.model.ProviderService;
import com.backend.model.ProviderStatus;
import com.backend.model.User;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.ProviderServiceRepository;
import com.backend.repository.UserRepository;
import com.backend.repository.AppointmentRepository;
import com.backend.model.Appointment;
import com.backend.dto.ProviderServiceRequest;
import com.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/v1/provider")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProviderController {

    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final com.backend.repository.AppointmentRepository appointmentRepository;
    private final com.backend.service.EmailService emailService;
    private final com.backend.repository.NotificationRepository notificationRepository;
    private final com.backend.service.NotificationService notificationService;
    private final com.backend.repository.ReminderRepository reminderRepository;
    private final com.backend.repository.TenantRepository tenantRepository;
    private final com.backend.service.ProviderAnalyticsService providerAnalyticsService;
    private final com.backend.service.AuditLogService auditLogService;
    private final com.backend.service.AdminService adminService;
    private final com.backend.service.AppointmentLifecycleService appointmentLifecycleService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Optional<ProviderProfile> existingProfile = providerProfileRepository.findByUser(user);
            ProviderProfile profile = existingProfile.orElse(null);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fullName", user.getFullName());
            response.put("email", user.getEmail());
            response.put("phone", user.getPhone());
            response.put("role", user.getRole());
            response.put("profilePicture", user.getProfilePicture());

            if (user.getTenant() != null) {
                com.backend.model.Tenant tenant = user.getTenant();
                response.put("tenantId", tenant.getId());
                response.put("organizationName", tenant.getOrganizationName());
                response.put("organizationType", tenant.getOrganizationType());
                response.put("primaryAccentColor", tenant.getPrimaryAccentColor());
                response.put("logoUrl", tenant.getLogoUrl());
            }

            if (profile != null) {
                response.put("credentials", profile.getCredentials());
                response.put("medicalLicense", profile.getMedicalLicense());
                response.put("primarySpecialty", profile.getPrimarySpecialty());
                response.put("profilePictureUrl", profile.getProfilePictureUrl() != null ? profile.getProfilePictureUrl() : user.getProfilePicture());
                response.put("licenseImageUrl", profile.getLicenseImageUrl());
                response.put("status", profile.getStatus() != null ? profile.getStatus().name() : null);
                response.put("tier", profile.getTier());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching provider profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestParam(value = "headshot", required = false) MultipartFile headshot,
            @RequestParam(value = "licenseImage", required = false) MultipartFile licenseImage,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "lastName", required = false) String lastName,
            @RequestParam(value = "credentials", required = false) String credentials,
            @RequestParam(value = "medicalLicense", required = false) String medicalLicense,
            @RequestParam(value = "primarySpecialty", required = false) String primarySpecialty
    ) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Optional<ProviderProfile> existingProfile = providerProfileRepository.findByUser(user);
            ProviderProfile profile = existingProfile.orElseGet(ProviderProfile::new);

            if (profile.getId() == null) {
                profile.setUser(user);
            }

            if (credentials != null) profile.setCredentials(credentials);
            if (medicalLicense != null) profile.setMedicalLicense(medicalLicense);
            if (primarySpecialty != null) profile.setPrimarySpecialty(primarySpecialty);
            
            // Also update User's full name if provided
            if (firstName != null && lastName != null) {
                user.setFullName(firstName + " " + lastName);
                userRepository.save(user);
            }

            if (headshot != null && !headshot.isEmpty()) {
                String fileName = fileStorageService.storeFile(headshot);
                String logoUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/")
                        .path(fileName)
                        .toUriString();
                profile.setProfilePictureUrl(logoUrl);
            }

            if (licenseImage != null && !licenseImage.isEmpty()) {
                String fileName = fileStorageService.storeFile(licenseImage);
                String licenseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/")
                        .path(fileName)
                        .toUriString();
                profile.setLicenseImageUrl(licenseUrl);
            }

            ProviderProfile savedProfile = providerProfileRepository.save(profile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Provider profile updated successfully");
            response.put("profile", savedProfile);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating provider profile: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/services")
    public ResponseEntity<?> addServices(@RequestBody ProviderServiceRequest request) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ProviderProfile profile = providerProfileRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Provider profile not found"));

            for (ProviderServiceRequest.ServiceDto serviceDto : request.getServices()) {
                ProviderService service = new ProviderService();
                service.setProviderProfile(profile);
                service.setServiceName(serviceDto.getServiceName());
                service.setDurationMinutes(serviceDto.getDurationMinutes());
                service.setFee(serviceDto.getFee());
                service.setIsTelemedicine(serviceDto.getIsTelemedicine());
                service.setCategory(serviceDto.getCategory());
                service.setIsActive(serviceDto.getIsActive() != null ? serviceDto.getIsActive() : true);
                service.setMaxCapacity(serviceDto.getMaxCapacity() != null && serviceDto.getMaxCapacity() > 0 ? serviceDto.getMaxCapacity() : 1);
                providerServiceRepository.save(service);
            }

            profile.setStatus(ProviderStatus.PENDING_APPROVAL);
            providerProfileRepository.save(profile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Services added and profile submitted for approval.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error adding services: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/services")
    public ResponseEntity<?> getServices() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<ProviderService> services = new java.util.ArrayList<>();
            java.util.Optional<ProviderProfile> profileOpt = providerProfileRepository.findByUser(user);
            if (profileOpt.isPresent()) {
                services = providerServiceRepository.findByProviderProfile(profileOpt.get());
            }

            if (services.isEmpty() && user.getTenant() != null) {
                services = providerServiceRepository.findByTenantId(user.getTenant().getId());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("services", services);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching services: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/services/single")
    public ResponseEntity<?> addSingleService(@RequestBody ProviderServiceRequest.ServiceDto serviceDto) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ProviderProfile profile = providerProfileRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Provider profile not found"));

            ProviderService service = new ProviderService();
            service.setProviderProfile(profile);
            service.setServiceName(serviceDto.getServiceName());
            service.setDurationMinutes(serviceDto.getDurationMinutes());
            service.setFee(serviceDto.getFee());
            service.setIsTelemedicine(serviceDto.getIsTelemedicine());
            service.setCategory(serviceDto.getCategory());
            service.setIsActive(serviceDto.getIsActive() != null ? serviceDto.getIsActive() : true);
            service.setMaxCapacity(serviceDto.getMaxCapacity() != null && serviceDto.getMaxCapacity() > 0 ? serviceDto.getMaxCapacity() : 1);
            ProviderService savedService = providerServiceRepository.save(service);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Service added successfully.");
            response.put("service", savedService);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error adding service: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<?> updateService(@PathVariable Long id, @RequestBody ProviderServiceRequest.ServiceDto serviceDto) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ProviderProfile profile = providerProfileRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Provider profile not found"));

            ProviderService service = providerServiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Service not found"));

            if (!service.getProviderProfile().getId().equals(profile.getId())) {
                throw new RuntimeException("Unauthorized to update this service");
            }

            if (serviceDto.getServiceName() != null) service.setServiceName(serviceDto.getServiceName());
            if (serviceDto.getDurationMinutes() != null) service.setDurationMinutes(serviceDto.getDurationMinutes());
            if (serviceDto.getFee() != null) service.setFee(serviceDto.getFee());
            if (serviceDto.getIsTelemedicine() != null) service.setIsTelemedicine(serviceDto.getIsTelemedicine());
            if (serviceDto.getCategory() != null) service.setCategory(serviceDto.getCategory());
            if (serviceDto.getIsActive() != null) service.setIsActive(serviceDto.getIsActive());
            if (serviceDto.getMaxCapacity() != null && serviceDto.getMaxCapacity() > 0) {
                service.setMaxCapacity(serviceDto.getMaxCapacity());
            }

            ProviderService updatedService = providerServiceRepository.save(service);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Service updated successfully.");
            response.put("service", updatedService);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating service: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<?> deleteService(@PathVariable Long id) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ProviderProfile profile = providerProfileRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Provider profile not found"));

            ProviderService service = providerServiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Service not found"));

            if (!service.getProviderProfile().getId().equals(profile.getId())) {
                throw new RuntimeException("Unauthorized to delete this service");
            }

            providerServiceRepository.delete(service);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Service deleted successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting service: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private boolean hasCalendarWritePermission(User user) {
        if (user == null) return false;
        // Primary admin or users without role restriction have write access
        if (user.getTenantRole() == null || "admin".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        String json = user.getTenantRole().getPermissionsJson();
        if (json == null || json.trim().isEmpty()) {
            return true;
        }
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"calendar\"\\s*:\\s*\\{[^}]*\"write\"\\s*:\\s*true", 
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            return pattern.matcher(json).find();
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean canManageAppointment(User user, Appointment appointment) {
        if (appointment == null) return false;
        if (appointment.getProviderId() != null && appointment.getProviderId().equals(user.getId())) {
            return true;
        }
        boolean isAdmin = "admin".equalsIgnoreCase(user.getRole()) 
                || "role_admin".equalsIgnoreCase(user.getRole()) 
                || "super_admin".equalsIgnoreCase(user.getRole());
        if (isAdmin && user.getTenant() != null && appointment.getTenantId() != null && appointment.getTenantId().equals(user.getTenant().getId())) {
            return true;
        }
        return false;
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> getAppointments() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Appointment> appointments;
            boolean isAdmin = "admin".equalsIgnoreCase(user.getRole()) 
                    || "role_admin".equalsIgnoreCase(user.getRole()) 
                    || "super_admin".equalsIgnoreCase(user.getRole());

            if (isAdmin && user.getTenant() != null) {
                appointments = appointmentRepository.findByTenantIdOrderByAppointmentDateDesc(user.getTenant().getId());
            } else {
                appointments = appointmentRepository.findByProviderIdOrderByAppointmentDateDesc(user.getId());
            }
            List<ProviderService> tenantServices = user.getTenant() != null 
                    ? providerServiceRepository.findByTenantId(user.getTenant().getId()) 
                    : java.util.Collections.emptyList();

            appointments.forEach(app -> {
                // Populate patient profile picture
                userRepository.findByEmailIgnoringTenant(app.getPatientEmail()).ifPresent(u -> {
                    app.setPatientProfilePicture(u.getProfilePicture());
                });
                // Populate doctor info
                userRepository.findById(app.getProviderId()).ifPresent(p -> {
                    app.setDoctorName(p.getFullName());
                    ProviderProfile profile = providerProfileRepository.findByUser(p).orElse(null);
                    if (profile != null && profile.getProfilePictureUrl() != null && !profile.getProfilePictureUrl().isEmpty()) {
                        app.setDoctorProfilePicture(profile.getProfilePictureUrl());
                    } else {
                        app.setDoctorProfilePicture(p.getProfilePicture());
                    }
                    if (profile != null) {
                        app.setDoctorSpecialty(profile.getPrimarySpecialty());
                    }
                });
                
                // Tenant details & dynamic customer role
                String defaultCustomerRole = "client";
                if (app.getTenantId() != null) {
                    com.backend.model.Tenant t = tenantRepository.findById(app.getTenantId()).orElse(null);
                    if (t != null) {
                        app.setOrganizationType(t.getOrganizationType());
                        app.setOrganizationName(t.getOrganizationName());
                        if (t.getOrganizationType() != null) {
                            String ot = t.getOrganizationType().toLowerCase();
                            if (ot.contains("college") || ot.contains("univ") || ot.contains("school") || ot.contains("educ")) {
                                defaultCustomerRole = "student";
                            } else if (ot.contains("clinic") || ot.contains("hosp") || ot.contains("med")) {
                                defaultCustomerRole = "patient";
                            }
                        }
                    }
                }

                // Check if the service allows virtual / online consultations
                boolean allowsVideo = false;
                if (app.getServiceName() != null) {
                    allowsVideo = tenantServices.stream()
                            .filter(s -> s.getServiceName() != null && s.getServiceName().equalsIgnoreCase(app.getServiceName().trim()))
                            .anyMatch(s -> Boolean.TRUE.equals(s.getIsTelemedicine()));
                    if (!allowsVideo && app.getProviderId() != null) {
                        ProviderProfile pProfile = providerProfileRepository.findByUser(userRepository.findById(app.getProviderId()).orElse(null)).orElse(null);
                        if (pProfile != null) {
                            allowsVideo = providerServiceRepository.findByProviderProfile(pProfile).stream()
                                    .filter(s -> s.getServiceName() != null && s.getServiceName().equalsIgnoreCase(app.getServiceName().trim()))
                                    .anyMatch(s -> Boolean.TRUE.equals(s.getIsTelemedicine()));
                        }
                    }
                }
                app.setServiceAllowsVideo(allowsVideo);
                if (!allowsVideo) {
                    app.setVideoCallEnabled(false);
                    if ("VIRTUAL".equalsIgnoreCase(app.getAppointmentType())) {
                        app.setAppointmentType("IN_PERSON");
                    }
                }

                // Display fallbacks for existing records
                if (app.getBookedByName() == null || app.getBookedByName().isEmpty()) {
                    app.setBookedByName(app.getPatientName());
                    app.setBookedByRole(defaultCustomerRole);
                } else if ("patient".equalsIgnoreCase(app.getBookedByRole()) && "student".equals(defaultCustomerRole)) {
                    app.setBookedByRole("student");
                }
                if (app.getApprovedAt() != null && (app.getApprovedByName() == null || app.getApprovedByName().isEmpty())) {
                    app.setApprovedByName(app.getDoctorName() != null ? app.getDoctorName() : "Staff");
                    app.setApprovedByRole("service_provider");
                }
                if (app.getCheckedInAt() != null && (app.getCheckedInByName() == null || app.getCheckedInByName().isEmpty())) {
                    if (app.getCompletedByName() != null) {
                        app.setCheckedInByName(app.getCompletedByName());
                        app.setCheckedInByRole(app.getCompletedByRole());
                    } else if (app.getDoctorName() != null) {
                        app.setCheckedInByName(app.getDoctorName());
                        app.setCheckedInByRole("service_provider");
                    } else {
                        app.setCheckedInByName("Front Desk Staff");
                        app.setCheckedInByRole("service_provider");
                    }
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("appointments", appointments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching appointments: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/appointments")
    public ResponseEntity<?> createAppointment(@RequestBody com.backend.dto.WalkInAppointmentRequest request) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            adminService.createWalkInAppointment(request, userDetails.getUsername());
            return ResponseEntity.ok(Map.of("success", true, "message", "Appointment created successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/appointments/checkin/{transactionId}")
    public ResponseEntity<?> checkInAppointment(@PathVariable String transactionId) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            System.out.println("DEBUG CHECKIN: Received transactionId: " + transactionId);
            List<Appointment> appointments = new java.util.ArrayList<>(appointmentRepository.findByTransactionId(transactionId));
            if (appointments.isEmpty() && transactionId != null) {
                String cleanId = transactionId.replace("APPT-", "").trim();
                if (cleanId.matches("^\\d+$")) {
                    appointmentRepository.findById(Long.parseLong(cleanId)).ifPresent(appointments::add);
                }
            }
            System.out.println("DEBUG CHECKIN: Found appointments: " + appointments.size());
            
            if (appointments.isEmpty()) {
                System.out.println("DEBUG CHECKIN: No appointments found for token/id: " + transactionId);
                throw new RuntimeException("Appointment not found");
            }

            boolean checkedInAny = false;
            for (Appointment appointment : appointments) {
                System.out.println("DEBUG CHECKIN: Checking appointment ID: " + appointment.getId() + " Provider ID: " + appointment.getProviderId() + " Logged in User ID: " + user.getId());
                // Verify provider ownership or tenant staff permission
                if (canManageAppointment(user, appointment)) {
                    appointment.setAppointmentStatus("CHECKED_IN");
                    appointment.setCheckedInAt(java.time.LocalDateTime.now());
                    appointment.setCheckedInByName(user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
                    appointment.setCheckedInByRole(user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole());
                    appointment.setCheckedInByUserId(user.getId());
                    appointmentRepository.save(appointment);
                    checkedInAny = true;
                    System.out.println("DEBUG CHECKIN: Successfully checked in appointment ID: " + appointment.getId());

                    // Record lifecycle audit event
                    try {
                        String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                        appointmentLifecycleService.recordLifecycleEvent(
                                appointment.getId(),
                                appointment.getTenantId(),
                                "CHECKED_IN",
                                user.getId(),
                                appointment.getCheckedInByName(),
                                actorRole,
                                "SCHEDULED",
                                "CHECKED_IN",
                                "Client checked in by " + actorRole + " (" + appointment.getCheckedInByName() + ")",
                                null
                        );
                    } catch (Exception ignored) {}

                    // Dispatch role-separated check-in notifications
                    try {
                        notificationService.notifyAppointmentCheckedIn(appointment);
                    } catch (Exception notifEx) {
                        System.err.println("Failed to send checkin notifications: " + notifEx.getMessage());
                    }
                }
            }

            if (!checkedInAny) {
                System.out.println("DEBUG CHECKIN: No appointments were checked in. Unauthorized.");
                throw new RuntimeException("Unauthorized to check-in this appointment");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Patient checked in successfully.");
            // Send back the first checked-in appointment for reference if needed
            response.put("appointment", appointments.get(0));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error checking in patient: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/approve")
    public ResponseEntity<?> approveAppointment(@PathVariable Long id) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!canManageAppointment(user, appointment)) {
                throw new RuntimeException("Unauthorized to approve this appointment");
            }

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            appointment.setAppointmentStatus("SCHEDULED");
            appointment.setApprovedAt(java.time.LocalDateTime.now());
            appointment.setApprovedByName(user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
            appointment.setApprovedByRole(user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole());
            appointment.setApprovedByUserId(user.getId());
            appointmentRepository.save(appointment);

            // Record lifecycle audit event
            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                appointmentLifecycleService.recordLifecycleEvent(
                        appointment.getId(),
                        appointment.getTenantId(),
                        "CONFIRMED",
                        user.getId(),
                        appointment.getApprovedByName(),
                        actorRole,
                        "PENDING_APPROVAL",
                        "SCHEDULED",
                        "Appointment approved and confirmed by " + actorRole + " (" + appointment.getApprovedByName() + ")",
                        null
                );
            } catch (Exception ignored) {}

            // Send approval email
            try {
                emailService.sendAppointmentApprovedEmail(appointment);
            } catch(Exception e) {
                e.printStackTrace();
            }

            // Dispatch role-separated notifications for Approval
            try {
                notificationService.notifyAppointmentApproved(appointment);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Appointment approved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error approving appointment: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/decline")
    public ResponseEntity<?> declineAppointment(@PathVariable Long id) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!canManageAppointment(user, appointment)) {
                throw new RuntimeException("Unauthorized to decline this appointment");
            }

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            appointment.setAppointmentStatus("CANCELLED");
            appointment.setCancelledAt(java.time.LocalDateTime.now());
            appointment.setCancelledByName(user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
            appointment.setCancelledByRole(user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole());
            appointment.setCancelledByUserId(user.getId());
            appointmentRepository.save(appointment);

            // Record lifecycle audit event
            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                appointmentLifecycleService.recordLifecycleEvent(
                        appointment.getId(),
                        appointment.getTenantId(),
                        "CANCELLED",
                        user.getId(),
                        appointment.getCancelledByName(),
                        actorRole,
                        "SCHEDULED",
                        "CANCELLED",
                        "Appointment declined and cancelled by " + actorRole + " (" + appointment.getCancelledByName() + ")",
                        null
                );
            } catch (Exception ignored) {}

            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                auditLogService.logAction(user, "Appointment #" + appointment.getId() + " cancelled by " + actorRole + " (" + appointment.getCancelledByName() + ")", "SYSTEM");
            } catch (Exception ignored) {}

            // Dispatch cancellation notifications
            try {
                notificationService.notifyAppointmentCancelled(appointment);
            } catch (Exception notifEx) {
                System.err.println("Failed to send cancellation notification: " + notifEx.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Appointment cancelled successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error declining appointment: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/reschedule")
    public ResponseEntity<?> rescheduleAppointment(
            @PathVariable Long id, 
            @RequestParam String newDate, 
            @RequestParam String newTime) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!canManageAppointment(user, appointment)) {
                throw new RuntimeException("Unauthorized to reschedule this appointment");
            }

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            java.time.LocalDate parsedDate = java.time.LocalDate.parse(newDate);
            java.time.LocalTime parsedTime = java.time.LocalTime.parse(newTime);

            appointment.setAppointmentDate(parsedDate);
            appointment.setAppointmentTime(parsedTime);
            appointment.setAppointmentStatus("SCHEDULED");
            appointmentRepository.save(appointment);

            try {
                notificationService.notifyAppointmentRescheduled(appointment, parsedDate, parsedTime);
            } catch (Exception ex) {
                System.err.println("Failed to dispatch reschedule notification: " + ex.getMessage());
            }

            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                String actorName = user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail();
                auditLogService.logAction(user, "Appointment #" + appointment.getId() + " rescheduled to " + newDate + " " + newTime + " by " + actorRole + " (" + actorName + ")", "SYSTEM");
            } catch (Exception ignored) {}

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Appointment rescheduled successfully");
            response.put("appointment", appointment);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error rescheduling appointment: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/no-show")
    public ResponseEntity<?> markNoShowAppointment(
            @PathVariable Long id, 
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!canManageAppointment(user, appointment)) {
                throw new RuntimeException("Unauthorized to update this appointment");
            }

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            appointment.setAppointmentStatus("NO_SHOW");
            appointment.setCancelledAt(java.time.LocalDateTime.now());
            appointment.setCancelledByName(user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
            appointment.setCancelledByRole(user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole());
            appointment.setCancelledByUserId(user.getId());
            if (payload != null && payload.containsKey("internalNotes")) {
                appointment.setInternalNotes((String) payload.get("internalNotes"));
            }
            appointmentRepository.save(appointment);

            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                auditLogService.logAction(user, "Appointment #" + appointment.getId() + " marked NO-SHOW by " + actorRole + " (" + appointment.getCancelledByName() + ")", "SYSTEM");
            } catch (Exception ignored) {}

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Appointment marked as No-Show successfully");
            response.put("appointment", appointment);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error marking appointment as no-show: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/complete")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!canManageAppointment(user, appointment)) {
                throw new RuntimeException("Unauthorized to complete this appointment");
            }

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            appointment.setAppointmentStatus("COMPLETED");
            appointment.setCompletedAt(java.time.LocalDateTime.now());
            appointment.setCompletedByName(user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
            appointment.setCompletedByRole(user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole());
            appointment.setCompletedByUserId(user.getId());
            
            if (payload.containsKey("treatmentSummary")) {
                appointment.setTreatmentSummary((String) payload.get("treatmentSummary"));
            }
            if (payload.containsKey("internalNotes")) {
                appointment.setInternalNotes((String) payload.get("internalNotes"));
            }
            
            String feedbackToken = java.util.UUID.randomUUID().toString().substring(0, 8);
            appointment.setFeedbackToken(feedbackToken);
            appointmentRepository.save(appointment);

            // Record lifecycle audit event
            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                appointmentLifecycleService.recordLifecycleEvent(
                        appointment.getId(),
                        appointment.getTenantId(),
                        "COMPLETED",
                        user.getId(),
                        appointment.getCompletedByName(),
                        actorRole,
                        "CHECKED_IN",
                        "COMPLETED",
                        "Appointment completed successfully by " + actorRole + " (" + appointment.getCompletedByName() + ")",
                        null
                );
            } catch (Exception ignored) {}
            
            // Handle follow-up
            if (payload.containsKey("followUpMonths") && payload.get("followUpMonths") != null) {
                int months = Integer.parseInt(payload.get("followUpMonths").toString());
                if (months > 0) {
                    java.time.LocalDate followUpDate = java.time.LocalDate.now().plusMonths(months);
                    appointment.setFollowUpDate(followUpDate);
                    
                    com.backend.model.Reminder reminder = com.backend.model.Reminder.builder()
                        .providerId(user.getId())
                        .patientName(appointment.getPatientName())
                        .patientEmail(appointment.getPatientEmail())
                        .patientPhone(appointment.getPatientPhone())
                        .message("It has been " + months + " months since your last visit. Please book your recommended follow-up appointment.")
                        .dueDate(followUpDate.minusDays(7))
                        .isSent(false)
                        .build();
                    reminderRepository.save(reminder);
                }
            }

            appointmentRepository.save(appointment);

            // Dispatch completion notifications
            try {
                notificationService.notifyAppointmentCompleted(appointment);
            } catch (Exception notifEx) {
                System.err.println("Failed to dispatch completion notification: " + notifEx.getMessage());
            }

            try {
                String actorRole = user.getTenantRole() != null ? user.getTenantRole().getRoleName() : user.getRole();
                auditLogService.logAction(user, "Appointment #" + appointment.getId() + " marked COMPLETED by " + actorRole + " (" + appointment.getCompletedByName() + ")", "SYSTEM");
            } catch (Exception ignored) {}

            // Send feedback email
            if (appointment.getPatientEmail() != null) {
                String feedbackUrl = "http://localhost:5173/patient/feedback?appt_id=" + appointment.getId() + "&token=" + feedbackToken;
                System.out.println("=================================================");
                System.out.println("FEEDBACK URL GENERATED FOR " + appointment.getPatientEmail());
                System.out.println(feedbackUrl);
                System.out.println("=================================================");
                emailService.sendFeedbackRequest(appointment.getPatientEmail(), user.getFullName(), feedbackUrl);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Appointment marked as completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error completing appointment: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/toggle-video")
    public ResponseEntity<?> toggleVideoCall(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!canManageAppointment(user, appointment)) {
                throw new RuntimeException("Unauthorized to manage this appointment's video call");
            }

            if (!hasCalendarWritePermission(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Access denied. Your role has read-only access to calendar and appointments.");
                return ResponseEntity.status(403).body(response);
            }

            boolean newState;
            if (payload != null && payload.containsKey("enabled")) {
                newState = Boolean.parseBoolean(payload.get("enabled").toString());
            } else {
                newState = !Boolean.TRUE.equals(appointment.getVideoCallEnabled());
            }

            if (newState) {
                // Verify service allows virtual / video consultation
                boolean serviceAllowsVirtual = false;
                if (appointment.getTenantId() != null && appointment.getServiceName() != null) {
                    List<ProviderService> services = providerServiceRepository.findByTenantId(appointment.getTenantId());
                    serviceAllowsVirtual = services.stream()
                            .filter(s -> s.getServiceName() != null && s.getServiceName().equalsIgnoreCase(appointment.getServiceName().trim()))
                            .anyMatch(s -> Boolean.TRUE.equals(s.getIsTelemedicine()));
                }
                if (!serviceAllowsVirtual && appointment.getProviderId() != null && appointment.getServiceName() != null) {
                    ProviderProfile profile = providerProfileRepository.findByUser(userRepository.findById(appointment.getProviderId()).orElse(null)).orElse(null);
                    if (profile != null) {
                        serviceAllowsVirtual = providerServiceRepository.findByProviderProfile(profile).stream()
                                .filter(s -> s.getServiceName() != null && s.getServiceName().equalsIgnoreCase(appointment.getServiceName().trim()))
                                .anyMatch(s -> Boolean.TRUE.equals(s.getIsTelemedicine()));
                    }
                }

                if (!serviceAllowsVirtual) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Video call cannot be enabled because the service '" 
                            + (appointment.getServiceName() != null ? appointment.getServiceName() : "selected") 
                            + "' does not have virtual/online enabled.");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            appointment.setVideoCallEnabled(newState);
            if (newState) {
                appointment.setAppointmentType("VIRTUAL");
                if (appointment.getMeetingLink() == null || appointment.getMeetingLink().isBlank()) {
                    String roomName = "OmniBook-Tenant" + (appointment.getTenantId() != null ? appointment.getTenantId() : "0") 
                            + "-Appt" + appointment.getId() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    appointment.setMeetingLink("https://meet.jit.si/" + roomName);
                }
            } else {
                appointment.setAppointmentType("IN_PERSON");
            }

            appointmentRepository.save(appointment);

            // Dispatch notification when video call is toggled
            try {
                notificationService.notifyVideoCallToggled(appointment, user.getFullName(), newState);
            } catch (Exception notifEx) {
                System.err.println("Failed to dispatch video notification: " + notifEx.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", newState ? "Video call enabled successfully" : "Video call disabled");
            response.put("videoCallEnabled", appointment.getVideoCallEnabled());
            response.put("meetingLink", appointment.getMeetingLink());
            response.put("appointmentType", appointment.getAppointmentType());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error toggling video call: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/patients")
    public ResponseEntity<?> getPatientsForProvider() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User provider = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Provider not found"));

            List<Appointment> allAppointments;
            boolean isAdmin = "admin".equalsIgnoreCase(provider.getRole()) 
                    || "role_admin".equalsIgnoreCase(provider.getRole()) 
                    || "super_admin".equalsIgnoreCase(provider.getRole());

            if (isAdmin && provider.getTenant() != null) {
                allAppointments = appointmentRepository.findByTenantIdOrderByAppointmentDateDesc(provider.getTenant().getId());
            } else {
                allAppointments = appointmentRepository.findByProviderIdOrderByAppointmentDateDesc(provider.getId())
                        .stream()
                        .filter(a -> a.getProviderId() != null && a.getProviderId().equals(provider.getId()))
                        .collect(java.util.stream.Collectors.toList());
            }

            // Group by email
            java.util.Map<String, List<Appointment>> groupedByEmail = allAppointments.stream()
                    .filter(a -> a.getPatientEmail() != null && !a.getPatientEmail().isEmpty())
                    .collect(java.util.stream.Collectors.groupingBy(Appointment::getPatientEmail));

            List<com.backend.dto.PatientDirectoryDto> directory = new java.util.ArrayList<>();
            int index = 1;

            for (java.util.Map.Entry<String, List<Appointment>> entry : groupedByEmail.entrySet()) {
                String email = entry.getKey();
                List<Appointment> appts = entry.getValue();

                int totalBookings = appts.size();
                long noShows = appts.stream().filter(a -> "CANCELLED".equals(a.getAppointmentStatus()) || "NO_SHOW".equals(a.getAppointmentStatus())).count();
                
                appts.sort((a,b) -> b.getAppointmentDate().compareTo(a.getAppointmentDate()));
                Appointment lastVisit = appts.get(0);
                
                String lastVisitDateStr = lastVisit.getAppointmentDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));

                String status = "Active";
                if (noShows > (totalBookings / 2)) {
                    status = "Missed";
                } else if (lastVisit.getAppointmentDate().isBefore(java.time.LocalDate.now().minusMonths(6))) {
                    status = "Inactive";
                }

                // Use pravatar for realistic fallback images based on email hash
                String avatarUrl = "https://i.pravatar.cc/150?u=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8.toString());
                java.util.Optional<User> patientUserOpt = userRepository.findByEmailIgnoringTenant(email);
                if(patientUserOpt.isPresent() && patientUserOpt.get().getProfilePicture() != null && !patientUserOpt.get().getProfilePicture().isEmpty()) {
                    avatarUrl = patientUserOpt.get().getProfilePicture();
                }

                directory.add(com.backend.dto.PatientDirectoryDto.builder()
                        .id("#PT-" + String.format("%04d", index++))
                        .name(lastVisit.getPatientName())
                        .email(email)
                        .phone(lastVisit.getPatientPhone())
                        .lastVisitDate(lastVisitDateStr)
                        .lastVisitReason(lastVisit.getReasonForVisit() != null ? lastVisit.getReasonForVisit() : "General Consultation")
                        .bookings(String.valueOf(totalBookings))
                        .status(status)
                        .noshows(String.valueOf(noShows))
                        .avatarUrl(avatarUrl)
                        .build());
            }

            return ResponseEntity.ok(directory);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching patient directory: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(@RequestParam(value = "range", defaultValue = "Last 30 Days") String range) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User provider = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Provider not found"));

            com.backend.dto.ProviderAnalyticsDTO analytics = providerAnalyticsService.getProviderAnalytics(provider, range);
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error calculating analytics: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

}
