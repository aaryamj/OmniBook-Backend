package com.backend.controller;

import com.backend.model.Appointment;
import com.backend.model.ProviderProfile;
import com.backend.model.Tenant;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.ProviderServiceRepository;
import com.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                map.put("address", t.getAddress());
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

    @GetMapping("/providers/{providerId}/slots")
    public ResponseEntity<?> getProviderSlots(
            @PathVariable Long providerId,
            @RequestParam String date,
            @RequestParam(required = false) String serviceName) {
        try {
            Map<String, Object> result = publicBookingService.getProviderSlots(providerId, date, serviceName);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("slots", result.get("slots"));
            response.put("isClosed", result.get("isClosed"));
            response.put("closedMessage", result.get("closedMessage"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch slots: " + e.getMessage()
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
