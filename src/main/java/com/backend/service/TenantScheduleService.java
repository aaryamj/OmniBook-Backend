package com.backend.service;

import com.backend.dto.TenantScheduleDTO;
import com.backend.dto.TenantScheduleSettingsDTO;
import com.backend.model.ProviderSchedule;
import com.backend.model.Tenant;
import com.backend.model.TenantSchedule;
import com.backend.model.User;
import com.backend.repository.ProviderScheduleRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.TenantScheduleRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantScheduleService {

    private final TenantScheduleRepository scheduleRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ProviderScheduleRepository providerScheduleRepository;

    private User getAdminUser(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin has no tenant associated");
        }
        return admin;
    }

    public TenantScheduleSettingsDTO getScheduleSettings(String adminEmail) {
        User admin = getAdminUser(adminEmail);
        Tenant tenant = admin.getTenant();

        List<TenantSchedule> schedules = scheduleRepository.findByTenantId(tenant.getId());
        
        if (schedules.isEmpty()) {
            schedules = initializeDefaultSchedules(tenant);
        }

        List<TenantScheduleDTO> scheduleDTOs = schedules.stream().map(s -> TenantScheduleDTO.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .isActive(s.getIsActive())
                .openingTime(s.getOpeningTime())
                .closingTime(s.getClosingTime())
                .breakStartTime(s.getBreakStartTime())
                .breakEndTime(s.getBreakEndTime())
                .closedMessage(s.getClosedMessage())
                .build()).collect(Collectors.toList());

        return TenantScheduleSettingsDTO.builder()
                .timezone(tenant.getTimezone() != null ? tenant.getTimezone() : "Asia/Kathmandu")
                .slotDuration(tenant.getSlotDuration() != null ? tenant.getSlotDuration() : 30)
                .schedules(scheduleDTOs)
                .build();
    }

    private List<TenantSchedule> initializeDefaultSchedules(Tenant tenant) {
        List<String> days = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
        List<TenantSchedule> defaultSchedules = new ArrayList<>();
        
        for (String day : days) {
            TenantSchedule schedule = TenantSchedule.builder()
                    .tenant(tenant)
                    .dayOfWeek(day)
                    .isActive(!"Saturday".equals(day) && !"Sunday".equals(day))
                    .openingTime(tenant.getOpeningTime() != null ? tenant.getOpeningTime() : java.time.LocalTime.of(9, 0))
                    .closingTime(tenant.getClosingTime() != null ? tenant.getClosingTime() : java.time.LocalTime.of(17, 0))
                    .breakStartTime(java.time.LocalTime.of(13, 0))
                    .breakEndTime(java.time.LocalTime.of(14, 0))
                    .closedMessage(!"Saturday".equals(day) && !"Sunday".equals(day) ? null : "Closed for the weekend")
                    .build();
            defaultSchedules.add(scheduleRepository.save(schedule));
        }
        return defaultSchedules;
    }

    public TenantScheduleSettingsDTO updateScheduleSettings(String adminEmail, TenantScheduleSettingsDTO request) {
        User admin = getAdminUser(adminEmail);
        Tenant tenant = admin.getTenant();

        // Update global settings
        if (request.getTimezone() != null) {
            tenant.setTimezone(request.getTimezone());
        }
        if (request.getSlotDuration() != null) {
            tenant.setSlotDuration(request.getSlotDuration());
        }
        tenantRepository.save(tenant);

        // Update daily schedules
        if (request.getSchedules() != null) {
            for (TenantScheduleDTO dto : request.getSchedules()) {
                TenantSchedule schedule = null;
                if (dto.getId() != null) {
                    schedule = scheduleRepository.findById(dto.getId()).orElse(null);
                }
                if (schedule == null && dto.getDayOfWeek() != null) {
                    schedule = scheduleRepository.findByTenantIdAndDayOfWeek(tenant.getId(), dto.getDayOfWeek()).orElse(null);
                }
                if (schedule == null && dto.getDayOfWeek() != null) {
                    schedule = TenantSchedule.builder()
                            .tenant(tenant)
                            .dayOfWeek(dto.getDayOfWeek())
                            .build();
                }

                if (schedule != null) {
                    boolean active = Boolean.TRUE.equals(dto.getIsActive());
                    schedule.setIsActive(active);
                    if (dto.getOpeningTime() != null) schedule.setOpeningTime(dto.getOpeningTime());
                    if (dto.getClosingTime() != null) schedule.setClosingTime(dto.getClosingTime());

                    // Validate break time bounds
                    if (active && dto.getBreakStartTime() != null && dto.getBreakEndTime() != null) {
                        if (dto.getOpeningTime() != null && dto.getClosingTime() != null) {
                            if (dto.getBreakStartTime().isBefore(dto.getOpeningTime()) || dto.getBreakEndTime().isAfter(dto.getClosingTime())) {
                                throw new IllegalArgumentException("Break time must be within opening and closing hours on " + schedule.getDayOfWeek());
                            }
                        }
                    }

                    if (dto.getBreakStartTime() != null) schedule.setBreakStartTime(dto.getBreakStartTime());
                    if (dto.getBreakEndTime() != null) schedule.setBreakEndTime(dto.getBreakEndTime());

                    // Clear closed message if active
                    if (active) {
                        schedule.setClosedMessage(null);
                    } else {
                        schedule.setClosedMessage(dto.getClosedMessage() != null ? dto.getClosedMessage() : "Closed");
                    }

                    scheduleRepository.save(schedule);
                }
            }
        }

        // Delegation Rule: Automatically update schedules of service providers who have delegated their schedule
        List<User> tenantUsers = userRepository.findByTenantId(tenant.getId());
        List<User> delegatedProviders = tenantUsers.stream()
                .filter(u -> u.getRole() != null && u.getRole().toLowerCase().contains("provider"))
                .filter(u -> u.getIsScheduleDelegated() == null || Boolean.TRUE.equals(u.getIsScheduleDelegated()))
                .collect(Collectors.toList());

        if (!delegatedProviders.isEmpty()) {
            List<TenantSchedule> updatedTenantSchedules = scheduleRepository.findByTenantId(tenant.getId());
            for (User provider : delegatedProviders) {
                for (TenantSchedule ts : updatedTenantSchedules) {
                    ProviderSchedule ps = providerScheduleRepository.findByProviderAndDayOfWeek(provider, ts.getDayOfWeek())
                            .orElseGet(() -> ProviderSchedule.builder()
                                    .provider(provider)
                                    .dayOfWeek(ts.getDayOfWeek())
                                    .build());

                    ps.setIsActive(ts.getIsActive());
                    ps.setOpeningTime(ts.getOpeningTime());
                    ps.setClosingTime(ts.getClosingTime());
                    ps.setBreakStartTime(ts.getBreakStartTime());
                    ps.setBreakEndTime(ts.getBreakEndTime());
                    ps.setClosedMessage(ts.getClosedMessage());

                    providerScheduleRepository.save(ps);
                }
            }
        }

        return getScheduleSettings(adminEmail);
    }
}
