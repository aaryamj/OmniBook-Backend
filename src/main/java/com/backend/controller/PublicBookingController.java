package com.backend.controller;

import com.backend.model.Appointment;
import com.backend.model.ProviderProfile;
import com.backend.model.ProviderService;
import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.ProviderServiceRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public/booking")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PublicBookingController {

    private final TenantRepository tenantRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final com.backend.service.PublicBookingService publicBookingService;

    @GetMapping("/locations")
    public ResponseEntity<?> getLocations() {
        try {
            List<String> locations = tenantRepository.findDistinctAddress();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "locations", locations
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch locations: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/clinics")
    public ResponseEntity<?> getClinics(@RequestParam(required = false) String location) {
        try {
            List<Tenant> tenants;
            if (location != null && !location.trim().isEmpty()) {
                tenants = tenantRepository.findByAddressAndStatus(location, "ACTIVE");
            } else {
                tenants = tenantRepository.findByStatus("ACTIVE");
            }
            
            List<Map<String, Object>> response = tenants.stream().map(t -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", t.getId());
                map.put("organizationName", t.getOrganizationName());
                map.put("organizationType", t.getOrganizationType() != null ? t.getOrganizationType() : "Clinic");
                map.put("address", t.getAddress());
                map.put("logoUrl", t.getLogoUrl());

                String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter";
                boolean isExpiredOrSuspended = "EXPIRED".equalsIgnoreCase(t.getSubscriptionStatus()) 
                        || "SUSPENDED".equalsIgnoreCase(t.getSubscriptionStatus())
                        || (t.getSubscriptionExpiryDate() != null && java.time.LocalDate.now().isAfter(t.getSubscriptionExpiryDate()));
                boolean hasAiBooking = !isExpiredOrSuspended && ("Professional".equalsIgnoreCase(tier) || "Enterprise".equalsIgnoreCase(tier));

                map.put("subscriptionTier", tier);
                map.put("hasAiBooking", hasAiBooking);
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "clinics", response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch clinics: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/clinics/{tenantId}/services")
    public ResponseEntity<?> getServicesByClinic(@PathVariable Long tenantId) {
        try {
            List<String> serviceNames = providerServiceRepository.findDistinctServiceNamesByTenantId(tenantId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "services", serviceNames
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch services: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/clinics/{tenantId}/providers")
    public ResponseEntity<?> getProvidersByClinic(
            @PathVariable Long tenantId,
            @RequestParam(required = false) String serviceName) {
        try {
            List<ProviderProfile> profiles;
            if (serviceName != null && !serviceName.trim().isEmpty()) {
                profiles = providerProfileRepository.findByTenantIdAndServiceName(tenantId, serviceName);
            } else {
                profiles = providerProfileRepository.findByTenantId(tenantId);
            }

            List<Map<String, Object>> response = profiles.stream().map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getUser().getId());
                map.put("fullName", p.getUser().getFullName());
                map.put("primarySpecialty", p.getPrimarySpecialty());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "providers", response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch providers: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/providers/{providerId}/services")
    public ResponseEntity<?> getServicesByProvider(@PathVariable Long providerId) {
        try {
            User provider = userRepository.findById(providerId).orElse(null);
            if (provider == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Provider not found"));
            }
            ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
            List<ProviderService> services = profile != null ? providerServiceRepository.findByProviderProfile(profile) : Collections.emptyList();

            List<Map<String, Object>> response = services.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                    .map(s -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", s.getId());
                        map.put("serviceName", s.getServiceName());
                        map.put("durationMinutes", s.getDurationMinutes() != null ? s.getDurationMinutes() : 30);
                        map.put("fee", s.getFee() != null ? s.getFee() : 0.0);
                        map.put("category", s.getCategory());
                        map.put("isTelemedicine", Boolean.TRUE.equals(s.getIsTelemedicine()));
                        return map;
                    }).collect(Collectors.toList());

            // If provider has no custom services configured yet, fallback to their primary specialty or general session
            if (response.isEmpty()) {
                String fallbackName = (profile != null && profile.getPrimarySpecialty() != null && !profile.getPrimarySpecialty().trim().isEmpty())
                        ? profile.getPrimarySpecialty()
                        : "General Consultation & Session";
                Map<String, Object> defaultMap = new HashMap<>();
                defaultMap.put("id", 0L);
                defaultMap.put("serviceName", fallbackName);
                defaultMap.put("durationMinutes", 30);
                defaultMap.put("fee", 0.0);
                defaultMap.put("category", "General");
                defaultMap.put("isTelemedicine", false);
                response.add(defaultMap);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "services", response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch provider services: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/providers/{providerId}/slots")
    public ResponseEntity<?> getProviderSlots(
            @PathVariable Long providerId,
            @RequestParam String date,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) Long userId) {
        try {
            Map<String, Object> result = publicBookingService.getProviderSlots(providerId, date, serviceName, userEmail, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("slots", result.get("slots"));
            response.put("isClosed", result.get("isClosed"));
            response.put("closedMessage", result.get("closedMessage"));
            response.put("activeAppointmentsCount", result.get("activeAppointmentsCount"));
            response.put("maxAllowedAppointments", result.get("maxAllowedAppointments"));
            response.put("remainingCapacity", result.get("remainingCapacity"));
            response.put("isLimitReached", result.get("isLimitReached"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch slots: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/user-limit")
    public ResponseEntity<?> getUserAppointmentLimit(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) Long userId) {
        try {
            Map<String, Object> result = publicBookingService.getUserAppointmentLimit(userId, userEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to get appointment limit: " + e.getMessage()
            ));
        }
    }
    @GetMapping("/test-appointments")
    public ResponseEntity<?> testAppointments() {
        return ResponseEntity.ok(
            tenantRepository.findAll().stream().map(t -> t.getOrganizationName()).collect(Collectors.toList())
        );
    }

    @PutMapping("/appointments/{id}/feedback")
    public ResponseEntity<?> submitFeedback(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            String token = (String) payload.get("token");
            Integer rating = null;
            if (payload.get("rating") != null) {
                rating = Integer.valueOf(payload.get("rating").toString());
            }
            String review = (String) payload.get("review");

            if (token == null || rating == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing token or rating"));
            }

            Appointment appointment = appointmentRepository.findById(id).orElse(null);
            if (appointment == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Appointment not found"));
            }

            if (!token.equals(appointment.getFeedbackToken())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Invalid feedback token"));
            }

            appointment.setPatientRating(rating);
            appointment.setPatientReview(review);
            appointmentRepository.save(appointment);

            return ResponseEntity.ok(Map.of("success", true, "message", "Feedback submitted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "An error occurred: " + e.getMessage()));
        }
    }
}
