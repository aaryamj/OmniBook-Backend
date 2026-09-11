package com.backend.service;

import com.backend.dto.TimeSlotDTO;
import com.backend.model.*;
import com.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicBookingService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderScheduleRepository providerScheduleRepository;
    private final TenantScheduleRepository tenantScheduleRepository;
    private final AppointmentRepository appointmentRepository;

    public Map<String, Object> getProviderSlots(Long providerId, String dateStr, String serviceName) {
        return getProviderSlots(providerId, dateStr, serviceName, null, null);
    }

    public Map<String, Object> getProviderSlots(Long providerId, String dateStr, String serviceName, String userEmail, Long userId) {
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        if (!"service_provider".equals(provider.getRole())) {
            throw new RuntimeException("User is not a provider");
        }

        ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
        String providerTitle = provider.getFullName();
        if (profile != null && profile.getPrimarySpecialty() != null) {
            providerTitle += " - " + profile.getPrimarySpecialty();
        }

        int durationMinutes = 30; // Default 30 mins
        String priceStr = "रू 1,500"; // Default price
        int maxCapacity = 1; // Default 1 seat

        if (serviceName != null && !serviceName.trim().isEmpty() && profile != null) {
            Optional<ProviderService> serviceOpt = providerServiceRepository.findByProviderProfile(profile)
                    .stream()
                    .filter(s -> serviceName.equalsIgnoreCase(s.getServiceName()) && Boolean.TRUE.equals(s.getIsActive()))
                    .findFirst();
            if (serviceOpt.isPresent()) {
                ProviderService s = serviceOpt.get();
                if (s.getDurationMinutes() != null) {
                    durationMinutes = s.getDurationMinutes();
                }
                if (s.getMaxCapacity() != null && s.getMaxCapacity() > 0) {
                    maxCapacity = s.getMaxCapacity();
                }
                if (s.getFee() != null) {
                    double fee = s.getFee();
                    if (fee == Math.floor(fee)) {
                        priceStr = "रू " + String.format("%,d", (long) fee);
                    } else {
                        priceStr = "रू " + String.format("%,.2f", fee);
                    }
                }
            }
        }

        if (maxCapacity == 1 && serviceName != null && !serviceName.trim().isEmpty() && provider.getTenant() != null) {
            Optional<ProviderService> serviceOpt = providerServiceRepository.findByTenantId(provider.getTenant().getId())
                    .stream()
                    .filter(s -> serviceName.equalsIgnoreCase(s.getServiceName()) && Boolean.TRUE.equals(s.getIsActive()))
                    .findFirst();
            if (serviceOpt.isPresent()) {
                ProviderService s = serviceOpt.get();
                if (s.getDurationMinutes() != null) {
                    durationMinutes = s.getDurationMinutes();
                }
                if (s.getMaxCapacity() != null && s.getMaxCapacity() > 0) {
                    maxCapacity = s.getMaxCapacity();
                }
                if (s.getFee() != null) {
                    double fee = s.getFee();
                    if (fee == Math.floor(fee)) {
                        priceStr = "रू " + String.format("%,d", (long) fee);
                    } else {
                        priceStr = "रू " + String.format("%,.2f", fee);
                    }
                }
            }
        }

        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(zoneId);
        LocalTime nowTime = LocalTime.now(zoneId);

        LocalDate date = LocalDate.parse(dateStr);

        List<TimeSlotDTO> slots = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();

        if (date.isBefore(today)) {
            result.put("isClosed", true);
            result.put("closedMessage", "Cannot book appointments for past dates.");
            result.put("slots", slots);
            return result;
        }

        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        LocalTime openingTime = null;
        LocalTime closingTime = null;
        LocalTime breakStartTime = null;
        LocalTime breakEndTime = null;
        boolean isActive = false;
        String closedMessage = null;

        // Check clinic-level schedule constraint: if facility is closed on this day, no slots are available
        Tenant tenant = provider.getTenant();
        if (tenant != null) {
            TenantSchedule tenantSchedule = tenantScheduleRepository.findByTenantIdAndDayOfWeek(tenant.getId(), dayOfWeek)
                    .orElse(null);
            if (tenantSchedule != null && Boolean.FALSE.equals(tenantSchedule.getIsActive())) {
                result.put("isClosed", true);
                result.put("closedMessage", tenantSchedule.getClosedMessage() != null && !tenantSchedule.getClosedMessage().isBlank()
                        ? tenantSchedule.getClosedMessage()
                        : "Clinic is closed on " + dayOfWeek + "s.");
                result.put("slots", slots);
                return result;
            }
        }

        ProviderSchedule schedule = providerScheduleRepository.findByProviderAndDayOfWeek(provider, dayOfWeek)
                .orElse(null);
        if (schedule != null) {
            isActive = schedule.getIsActive();
            openingTime = schedule.getOpeningTime();
            closingTime = schedule.getClosingTime();
            breakStartTime = schedule.getBreakStartTime();
            breakEndTime = schedule.getBreakEndTime();
            closedMessage = schedule.getClosedMessage();
        }

        result.put("isClosed", !isActive);
        result.put("closedMessage", closedMessage);

        if (!isActive || openingTime == null || closingTime == null) {
            result.put("slots", slots);
            return result; // No slots
        }

        // Query active appointments on this date to check slot capacities
        List<Appointment> existingAppointments = appointmentRepository.findActiveAppointmentsByProviderAndDate(providerId, date);

        // Query active appointments for this specific user on this date if identity is provided
        List<Appointment> userAppointmentsOnDate = new ArrayList<>();
        if ((userEmail != null && !userEmail.trim().isEmpty()) || userId != null) {
            String cleanEmail = (userEmail != null && !userEmail.trim().isEmpty()) ? userEmail.trim() : null;
            userAppointmentsOnDate = appointmentRepository.findActiveUserAppointmentsOnDate(userId, cleanEmail, date);
        }

        LocalTime currentTime = openingTime;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        DateTimeFormatter time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

        int slotIndex = 0;
        while (currentTime.plusMinutes(durationMinutes).isBefore(closingTime) || currentTime.plusMinutes(durationMinutes).equals(closingTime)) {
            LocalTime slotEnd = currentTime.plusMinutes(durationMinutes);
            
            boolean isBreak = false;
            if (breakStartTime != null && breakEndTime != null) {
                // If the slot overlaps with break time
                if (currentTime.isBefore(breakEndTime) && slotEnd.isAfter(breakStartTime)) {
                    isBreak = true;
                }
            }

            boolean isPast = false;
            if (date.isEqual(today) && currentTime.isBefore(nowTime)) {
                isPast = true;
            }

            // Check how many active bookings overlap this slot
            final LocalTime currentSlotStart = currentTime;
            final LocalTime currentSlotEnd = slotEnd;
            final int slotDuration = durationMinutes;
            List<Appointment> matchingAppts = existingAppointments.stream()
                    .filter(a -> {
                        if (a.getAppointmentTime() == null) return false;
                        LocalTime aStart = a.getAppointmentTime();
                        LocalTime aEnd = aStart.plusMinutes(slotDuration);
                        return currentSlotStart.isBefore(aEnd) && currentSlotEnd.isAfter(aStart);
                    })
                    .collect(Collectors.toList());

            // Check if this specific user already has an active appointment at this time
            boolean alreadyBookedByUser = false;
            if (!userAppointmentsOnDate.isEmpty()) {
                alreadyBookedByUser = userAppointmentsOnDate.stream().anyMatch(a -> {
                    if (a.getAppointmentTime() == null) return false;
                    LocalTime aStart = a.getAppointmentTime();
                    LocalTime aEnd = aStart.plusMinutes(slotDuration);
                    return currentSlotStart.isBefore(aEnd) && currentSlotEnd.isAfter(aStart);
                });
            }

            int activeCount = matchingAppts.size();
            int availableSeats = Math.max(0, maxCapacity - activeCount);
            boolean isFull = availableSeats <= 0;
            boolean isCompleted = matchingAppts.stream().anyMatch(a -> "COMPLETED".equalsIgnoreCase(a.getAppointmentStatus()));
            String slotStatus = isFull ? "FULL" : (matchingAppts.isEmpty() ? null : matchingAppts.get(matchingAppts.size() - 1).getAppointmentStatus());

            slots.add(TimeSlotDTO.builder()
                    .id(providerId + "-" + dateStr + "-" + slotIndex)
                    .title("Appointment")
                    .date(date.format(dateFormatter))
                    .time(currentTime.format(timeFormatter))
                    .slotTime24(currentTime.format(time24Formatter))
                    .price(priceStr)
                    .provider(providerTitle)
                    .topMatch(false)
                    .providerId(String.valueOf(providerId))
                    .isBreak(isBreak)
                    .isPast(isPast)
                    .isBooked(isFull || alreadyBookedByUser)
                    .isCompleted(isCompleted)
                    .slotStatus(slotStatus)
                    .providerImageUrl(profile != null ? profile.getProfilePictureUrl() : null)
                    .maxCapacity(maxCapacity)
                    .currentBookings(activeCount)
                    .availableSeats(availableSeats)
                    .isFull(isFull)
                    .alreadyBookedByUser(alreadyBookedByUser)
                    .build());

            currentTime = slotEnd;
            slotIndex++;
        }

        result.put("slots", slots);

        long activeUpcomingCount = 0;
        if (userId != null || (userEmail != null && !userEmail.trim().isEmpty())) {
            activeUpcomingCount = appointmentRepository.countActiveUpcomingAppointmentsForUser(
                    userId,
                    userEmail != null ? userEmail.trim() : null,
                    today
            );
        }
        int maxAllowedAppointments = 3;
        int remainingCapacity = Math.max(0, maxAllowedAppointments - (int) activeUpcomingCount);
        result.put("activeAppointmentsCount", activeUpcomingCount);
        result.put("maxAllowedAppointments", maxAllowedAppointments);
        result.put("remainingCapacity", remainingCapacity);
        result.put("isLimitReached", activeUpcomingCount >= maxAllowedAppointments);

        return result;
    }

    public Map<String, Object> getUserAppointmentLimit(Long userId, String userEmail) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kathmandu"));
        long activeUpcomingCount = 0;
        if (userId != null || (userEmail != null && !userEmail.trim().isEmpty())) {
            activeUpcomingCount = appointmentRepository.countActiveUpcomingAppointmentsForUser(
                    userId,
                    userEmail != null ? userEmail.trim() : null,
                    today
            );
        }
        int maxAllowed = 3;
        int remaining = Math.max(0, maxAllowed - (int) activeUpcomingCount);
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        map.put("activeAppointmentsCount", activeUpcomingCount);
        map.put("maxAllowedAppointments", maxAllowed);
        map.put("remainingCapacity", remaining);
        map.put("isLimitReached", activeUpcomingCount >= maxAllowed);
        return map;
    }
}
