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

@Service
@RequiredArgsConstructor
public class PublicBookingService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderScheduleRepository providerScheduleRepository;
    private final TenantScheduleRepository tenantScheduleRepository;

    public Map<String, Object> getProviderSlots(Long providerId, String dateStr, String serviceName) {
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
        
        if (serviceName != null && !serviceName.trim().isEmpty() && profile != null) {
            Optional<ProviderService> serviceOpt = providerServiceRepository.findByProviderProfile(profile)
                    .stream()
                    .filter(s -> serviceName.equals(s.getServiceName()) && Boolean.TRUE.equals(s.getIsActive()))
                    .findFirst();
            if (serviceOpt.isPresent()) {
                ProviderService s = serviceOpt.get();
                if (s.getDurationMinutes() != null) {
                    durationMinutes = s.getDurationMinutes();
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

        LocalDate date = LocalDate.parse(dateStr);
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        LocalTime openingTime = null;
        LocalTime closingTime = null;
        LocalTime breakStartTime = null;
        LocalTime breakEndTime = null;
        boolean isActive = false;
        String closedMessage = null;

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

        List<TimeSlotDTO> slots = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        result.put("isClosed", !isActive);
        result.put("closedMessage", closedMessage);

        if (!isActive || openingTime == null || closingTime == null) {
            result.put("slots", slots);
            return result; // No slots
        }

        LocalTime currentTime = openingTime;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy");

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

            slots.add(TimeSlotDTO.builder()
                    .id(providerId + "-" + dateStr + "-" + slotIndex)
                    .title("Appointment")
                    .date(date.format(dateFormatter))
                    .time(currentTime.format(timeFormatter))
                    .price(priceStr)
                    .provider(providerTitle)
                    .topMatch(false)
                    .providerId(String.valueOf(providerId))
                    .isBreak(isBreak)
                    .providerImageUrl(profile != null ? profile.getProfilePictureUrl() : null)
                    .build());

            currentTime = slotEnd;
            slotIndex++;
        }

        result.put("slots", slots);
        return result;
    }
}
