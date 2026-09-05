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
    private final com.backend.repository.ReminderRepository reminderRepository;

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

            ProviderProfile profile = providerProfileRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Provider profile not found"));

            List<ProviderService> services = providerServiceRepository.findByProviderProfile(profile);

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

    @GetMapping("/appointments")
    public ResponseEntity<?> getAppointments() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Appointment> appointments = appointmentRepository.findByProviderIdOrderByAppointmentDateDesc(user.getId());

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

    @PutMapping("/appointments/checkin/{transactionId}")
    public ResponseEntity<?> checkInAppointment(@PathVariable String transactionId) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("DEBUG CHECKIN: Received transactionId: " + transactionId);
            List<Appointment> appointments = appointmentRepository.findByTransactionId(transactionId);
            System.out.println("DEBUG CHECKIN: Found appointments: " + appointments.size());
            
            if (appointments.isEmpty()) {
                System.out.println("DEBUG CHECKIN: No appointments found for transactionId: " + transactionId);
                throw new RuntimeException("Appointment not found");
            }

            boolean checkedInAny = false;
            for (Appointment appointment : appointments) {
                System.out.println("DEBUG CHECKIN: Checking appointment ID: " + appointment.getId() + " Provider ID: " + appointment.getProviderId() + " Logged in User ID: " + user.getId());
                // Verify that the provider owns this appointment
                if (appointment.getProviderId().equals(user.getId())) {
                    appointment.setAppointmentStatus("CHECKED_IN");
                    appointment.setCheckedInAt(java.time.LocalDateTime.now());
                    appointmentRepository.save(appointment);
                    checkedInAny = true;
                    System.out.println("DEBUG CHECKIN: Successfully checked in appointment ID: " + appointment.getId());
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

            if (!appointment.getProviderId().equals(user.getId())) {
                throw new RuntimeException("Unauthorized to approve this appointment");
            }

            appointment.setAppointmentStatus("SCHEDULED");
            appointment.setApprovedAt(java.time.LocalDateTime.now());
            appointmentRepository.save(appointment);

            // Send approval email
            try {
                emailService.sendAppointmentApprovedEmail(appointment);
            } catch(Exception e) {
                e.printStackTrace();
            }

            // Create notification for the patient (user) if they exist
            try {
                userRepository.findByEmail(appointment.getPatientEmail()).ifPresent(patientUser -> {
                    com.backend.model.Notification notification = com.backend.model.Notification.builder()
                            .userId(patientUser.getId())
                            .title("Appointment Approved")
                            .message("Your appointment for " + appointment.getServiceName() + " has been approved and is now Scheduled.")
                            .type("APPOINTMENT_APPROVED")
                            .isRead(false)
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    notificationRepository.save(notification);
                });
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

            if (!appointment.getProviderId().equals(user.getId())) {
                throw new RuntimeException("Unauthorized to decline this appointment");
            }

            appointment.setAppointmentStatus("CANCELLED");
            appointmentRepository.save(appointment);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Appointment declined successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error declining appointment: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/appointments/{id}/complete")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            UserDetails userDetails = (UserDetails) 
SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!appointment.getProviderId().equals(user.getId())) {
                throw new RuntimeException("Unauthorized to complete this appointment");
            }

            appointment.setAppointmentStatus("COMPLETED");
            appointment.setCompletedAt(java.time.LocalDateTime.now());
            
            if (payload.containsKey("treatmentSummary")) {
                appointment.setTreatmentSummary((String) payload.get("treatmentSummary"));
            }
            if (payload.containsKey("internalNotes")) {
                appointment.setInternalNotes((String) payload.get("internalNotes"));
            }
            
            String feedbackToken = java.util.UUID.randomUUID().toString().substring(0, 8);
            appointment.setFeedbackToken(feedbackToken);
            
            // Handle follow-up
            if (payload.containsKey("followUpMonths") && payload.get("followUpMonths") != null) {
                int months = Integer.parseInt(payload.get("followUpMonths").toString());
                if (months > 0) {
                    java.time.LocalDate followUpDate = java.time.LocalDate.now().plusMonths(months);
                    appointment.setFollowUpDate(followUpDate);
                    
                    // Create a reminder for 1 week before the followUpDate (which is roughly ~3 months minus 7 days)
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

    @GetMapping("/patients")
    public ResponseEntity<?> getPatientsForProvider() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User provider = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Provider not found"));

            List<Appointment> allAppointments = appointmentRepository.findByProviderIdOrderByAppointmentDateDesc(provider.getId());

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
                long noShows = appts.stream().filter(a -> "CANCELLED".equals(a.getAppointmentStatus())).count();
                
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

}
