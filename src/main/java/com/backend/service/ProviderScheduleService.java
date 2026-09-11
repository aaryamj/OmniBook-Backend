package com.backend.service;

import com.backend.dto.ProviderScheduleDTO;
import com.backend.dto.ProviderScheduleSettingsDTO;
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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderScheduleService {

    private final ProviderScheduleRepository providerScheduleRepository;
    private final TenantScheduleRepository tenantScheduleRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    private User getAdminUser(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin has no tenant associated");
        }
        return admin;
    }

    private User getProvider(Long providerId, Tenant adminTenant) {
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));
        if (!provider.getTenant().getId().equals(adminTenant.getId())) {
            throw new RuntimeException("Provider does not belong to your tenant");
        }
        if (!"service_provider".equals(provider.getRole())) {
            throw new RuntimeException("User is not a service provider");
        }
        return provider;
    }

    public ProviderScheduleSettingsDTO getScheduleSettings(String adminEmail, Long providerId) {
        User admin = getAdminUser(adminEmail);
        Tenant tenant = admin.getTenant();
        User provider = getProvider(providerId, tenant);

        if (provider.getIsScheduleDelegated() != null && !provider.getIsScheduleDelegated()) {
            throw new RuntimeException("Access Denied: Provider has revoked schedule delegation");
        }

        List<ProviderSchedule> schedules = providerScheduleRepository.findByProviderOrderByDayOfWeek(provider);
        
        if (schedules.isEmpty()) {
            schedules = initializeDefaultSchedules(provider, tenant);
        }

        List<TenantSchedule> tenantSchedules = tenantScheduleRepository.findByTenantId(tenant.getId());

        List<ProviderScheduleDTO> scheduleDTOs = schedules.stream().map(s -> {
            TenantSchedule matchingTs = tenantSchedules.stream()
                    .filter(ts -> ts.getDayOfWeek().equalsIgnoreCase(s.getDayOfWeek()))
                    .findFirst()
                    .orElse(null);

            boolean isTenantActive = matchingTs != null ? Boolean.TRUE.equals(matchingTs.getIsActive()) : true;
            boolean isClosedByAdmin = Boolean.TRUE.equals(s.getIsClosedByAdmin());
            boolean effectiveActive = isTenantActive && !isClosedByAdmin && Boolean.TRUE.equals(s.getIsActive());

            String closedMsg = s.getClosedMessage();
            if (!isTenantActive) {
                if (matchingTs != null && matchingTs.getClosedMessage() != null && !matchingTs.getClosedMessage().isBlank()) {
                    closedMsg = matchingTs.getClosedMessage();
                } else {
                    closedMsg = "Closed for the weekend";
                }
            } else if (isClosedByAdmin) {
                closedMsg = (s.getClosedMessage() != null && !s.getClosedMessage().isBlank())
                        ? s.getClosedMessage() : "Scheduled off by Administrator";
            }

            return ProviderScheduleDTO.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .isActive(effectiveActive)
                .isTenantActive(isTenantActive)
                .isClosedByAdmin(isClosedByAdmin)
                .openingTime(s.getOpeningTime())
                .closingTime(s.getClosingTime())
                .breakStartTime(s.getBreakStartTime())
                .breakEndTime(s.getBreakEndTime())
                .closedMessage(closedMsg)
                .build();
        }).collect(Collectors.toList());

        return ProviderScheduleSettingsDTO.builder()
                .timezone(tenant.getTimezone() != null ? tenant.getTimezone() : "Asia/Kathmandu")
                .slotDuration(tenant.getSlotDuration() != null ? tenant.getSlotDuration() : 30)
                .schedules(scheduleDTOs)
                .build();
    }

    private List<ProviderSchedule> initializeDefaultSchedules(User provider, Tenant tenant) {
        List<TenantSchedule> tenantSchedules = tenant != null ? tenantScheduleRepository.findByTenantId(tenant.getId()) : Collections.emptyList();
        List<ProviderSchedule> defaultSchedules = new ArrayList<>();
        
        if (!tenantSchedules.isEmpty()) {
            for (TenantSchedule ts : tenantSchedules) {
                ProviderSchedule schedule = ProviderSchedule.builder()
                        .provider(provider)
                        .dayOfWeek(ts.getDayOfWeek())
                        .isActive(ts.getIsActive())
                        .openingTime(ts.getOpeningTime())
                        .closingTime(ts.getClosingTime())
                        .breakStartTime(ts.getBreakStartTime())
                        .breakEndTime(ts.getBreakEndTime())
                        .closedMessage(ts.getClosedMessage())
                        .build();
                defaultSchedules.add(providerScheduleRepository.save(schedule));
            }
        } else {
            List<String> days = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
            for (String day : days) {
                ProviderSchedule schedule = ProviderSchedule.builder()
                        .provider(provider)
                        .dayOfWeek(day)
                        .isActive(!"Saturday".equals(day) && !"Sunday".equals(day))
                        .openingTime(tenant != null && tenant.getOpeningTime() != null ? tenant.getOpeningTime() : LocalTime.of(9, 0))
                        .closingTime(tenant != null && tenant.getClosingTime() != null ? tenant.getClosingTime() : LocalTime.of(17, 0))
                        .breakStartTime(LocalTime.of(13, 0))
                        .breakEndTime(LocalTime.of(14, 0))
                        .closedMessage(!"Saturday".equals(day) && !"Sunday".equals(day) ? null : "Closed for the weekend")
                        .build();
                defaultSchedules.add(providerScheduleRepository.save(schedule));
            }
        }
        
        return defaultSchedules;
    }

    public ProviderScheduleSettingsDTO updateScheduleSettings(String adminEmail, Long providerId, ProviderScheduleSettingsDTO request) {
        User admin = getAdminUser(adminEmail);
        Tenant tenant = admin.getTenant();
        User provider = getProvider(providerId, tenant);

        if (provider.getIsScheduleDelegated() != null && !provider.getIsScheduleDelegated()) {
            throw new RuntimeException("Access Denied: Provider has revoked schedule delegation");
        }

        List<TenantSchedule> tenantSchedules = tenantScheduleRepository.findByTenantId(tenant.getId());

        if (request.getSchedules() != null) {
            for (ProviderScheduleDTO dto : request.getSchedules()) {
                ProviderSchedule schedule = null;
                if (dto.getId() != null) {
                    schedule = providerScheduleRepository.findById(dto.getId()).orElse(null);
                }
                if (schedule == null && dto.getDayOfWeek() != null) {
                    schedule = providerScheduleRepository.findByProviderAndDayOfWeek(provider, dto.getDayOfWeek()).orElse(null);
                }
                if (schedule == null && dto.getDayOfWeek() != null) {
                    schedule = ProviderSchedule.builder()
                            .provider(provider)
                            .dayOfWeek(dto.getDayOfWeek())
                            .build();
                }

                if (schedule != null) {
                    final String day = schedule.getDayOfWeek();
                    TenantSchedule matchingTs = tenantSchedules.stream()
                            .filter(ts -> ts.getDayOfWeek().equalsIgnoreCase(day))
                            .findFirst()
                            .orElse(null);

                    boolean isTenantActive = matchingTs != null ? Boolean.TRUE.equals(matchingTs.getIsActive()) : true;
                    boolean requestedActive = Boolean.TRUE.equals(dto.getIsActive());
                    boolean active = isTenantActive && requestedActive;
                    schedule.setIsActive(active);

                    if (isTenantActive && !requestedActive) {
                        // Admin explicitly scheduled this provider off on an open day
                        schedule.setIsClosedByAdmin(true);
                        schedule.setClosedMessage(dto.getClosedMessage() != null && !dto.getClosedMessage().isBlank()
                                ? dto.getClosedMessage() : "Scheduled off by Administrator");
                    } else if (!isTenantActive) {
                        // Tenant itself is closed on this day
                        schedule.setIsClosedByAdmin(false);
                        schedule.setClosedMessage(matchingTs != null && matchingTs.getClosedMessage() != null && !matchingTs.getClosedMessage().isBlank()
                                ? matchingTs.getClosedMessage() : "Closed for the weekend");
                    } else {
                        // Admin enabled this day for the provider
                        schedule.setIsClosedByAdmin(false);
                        schedule.setClosedMessage(null);
                    }

                    if (dto.getOpeningTime() != null) schedule.setOpeningTime(dto.getOpeningTime());
                    if (dto.getClosingTime() != null) schedule.setClosingTime(dto.getClosingTime());

                    // Validate break time bounds only if active
                    if (active && dto.getBreakStartTime() != null && dto.getBreakEndTime() != null) {
                        if (!dto.getBreakStartTime().isBefore(dto.getBreakEndTime())) {
                            throw new IllegalArgumentException("Break start time must be before break end time on " + day);
                        }
                        if (schedule.getOpeningTime() != null && schedule.getClosingTime() != null) {
                            if (dto.getBreakStartTime().isBefore(schedule.getOpeningTime()) || dto.getBreakEndTime().isAfter(schedule.getClosingTime())) {
                                throw new IllegalArgumentException("Break time must be within opening and closing hours on " + day);
                            }
                        }
                    }

                    if (dto.getBreakStartTime() != null) schedule.setBreakStartTime(dto.getBreakStartTime());
                    if (dto.getBreakEndTime() != null) schedule.setBreakEndTime(dto.getBreakEndTime());
                    
                    providerScheduleRepository.save(schedule);
                }
            }
        }

        return getScheduleSettings(adminEmail, providerId);
    }

    public ProviderScheduleSettingsDTO getMyScheduleSettings(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider not found"));
        
        if (provider.getRole() == null || !provider.getRole().toLowerCase().contains("provider")) {
            throw new RuntimeException("User is not a service provider");
        }

        Tenant tenant = provider.getTenant();
        List<ProviderSchedule> schedules = providerScheduleRepository.findByProviderOrderByDayOfWeek(provider);
        
        if (schedules.isEmpty()) {
            schedules = initializeDefaultSchedules(provider, tenant);
        }

        List<TenantSchedule> tenantSchedules = tenant != null ? tenantScheduleRepository.findByTenantId(tenant.getId()) : new ArrayList<>();

        List<ProviderScheduleDTO> scheduleDTOs = schedules.stream().map(s -> {
            TenantSchedule matchingTs = tenantSchedules.stream()
                    .filter(ts -> ts.getDayOfWeek().equalsIgnoreCase(s.getDayOfWeek()))
                    .findFirst()
                    .orElse(null);

            boolean isTenantActive = matchingTs != null ? Boolean.TRUE.equals(matchingTs.getIsActive()) : true;
            boolean isClosedByAdmin = Boolean.TRUE.equals(s.getIsClosedByAdmin());
            boolean effectiveActive = isTenantActive && !isClosedByAdmin && Boolean.TRUE.equals(s.getIsActive());

            String closedMsg = s.getClosedMessage();
            if (!isTenantActive) {
                if (matchingTs != null && matchingTs.getClosedMessage() != null && !matchingTs.getClosedMessage().isBlank()) {
                    closedMsg = matchingTs.getClosedMessage();
                } else {
                    closedMsg = "Closed for the weekend";
                }
            } else if (isClosedByAdmin) {
                closedMsg = (s.getClosedMessage() != null && !s.getClosedMessage().isBlank())
                        ? s.getClosedMessage() : "Scheduled off by Administrator";
            }

            return ProviderScheduleDTO.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .isActive(effectiveActive)
                .isTenantActive(isTenantActive)
                .isClosedByAdmin(isClosedByAdmin)
                .openingTime(s.getOpeningTime())
                .closingTime(s.getClosingTime())
                .breakStartTime(s.getBreakStartTime())
                .breakEndTime(s.getBreakEndTime())
                .closedMessage(closedMsg)
                .build();
        }).collect(Collectors.toList());

        return ProviderScheduleSettingsDTO.builder()
                .timezone(tenant != null && tenant.getTimezone() != null ? tenant.getTimezone() : "Asia/Kathmandu")
                .slotDuration(tenant != null && tenant.getSlotDuration() != null ? tenant.getSlotDuration() : 30)
                .isScheduleDelegated(provider.getIsScheduleDelegated() != null ? provider.getIsScheduleDelegated() : true)
                .schedules(scheduleDTOs)
                .build();
    }

    public ProviderScheduleSettingsDTO updateMyScheduleSettings(String providerEmail, ProviderScheduleSettingsDTO request) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider not found"));
                
        if (provider.getRole() == null || !provider.getRole().toLowerCase().contains("provider")) {
            throw new RuntimeException("User is not a service provider");
        }

        Tenant tenant = provider.getTenant();
        if (request.getIsScheduleDelegated() != null) {
            boolean wasDelegated = Boolean.TRUE.equals(provider.getIsScheduleDelegated());
            provider.setIsScheduleDelegated(request.getIsScheduleDelegated());
            userRepository.save(provider);

            // If delegation was just enabled, immediately pull real clinic operating hours
            if (Boolean.TRUE.equals(request.getIsScheduleDelegated()) && !wasDelegated && tenant != null) {
                List<TenantSchedule> tenantSchedules = tenantScheduleRepository.findByTenantId(tenant.getId());
                for (TenantSchedule ts : tenantSchedules) {
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

        if (tenant != null && request.getTimezone() != null) {
            tenant.setTimezone(request.getTimezone());
            tenantRepository.save(tenant);
        }

        List<TenantSchedule> tenantSchedules = tenant != null ? tenantScheduleRepository.findByTenantId(tenant.getId()) : Collections.emptyList();

        if (request.getSchedules() != null) {
            for (ProviderScheduleDTO dto : request.getSchedules()) {
                ProviderSchedule schedule = null;
                if (dto.getId() != null) {
                    schedule = providerScheduleRepository.findById(dto.getId()).orElse(null);
                }
                if (schedule == null && dto.getDayOfWeek() != null) {
                    schedule = providerScheduleRepository.findByProviderAndDayOfWeek(provider, dto.getDayOfWeek()).orElse(null);
                }
                if (schedule == null && dto.getDayOfWeek() != null) {
                    schedule = ProviderSchedule.builder()
                            .provider(provider)
                            .dayOfWeek(dto.getDayOfWeek())
                            .build();
                }

                if (schedule != null) {
                    final String day = schedule.getDayOfWeek();
                    boolean isTenantActive = tenantSchedules.stream()
                            .filter(ts -> ts.getDayOfWeek().equalsIgnoreCase(day))
                            .findFirst()
                            .map(TenantSchedule::getIsActive)
                            .orElse(true);

                    if (!isTenantActive && Boolean.TRUE.equals(dto.getIsActive())) {
                        String facilityName = tenant != null && tenant.getOrganizationName() != null ? tenant.getOrganizationName() : "Facility";
                        throw new IllegalArgumentException("Cannot enable working hours on " + day + ": " + facilityName + " is closed on this day by the Administrator.");
                    }

                    if (Boolean.TRUE.equals(schedule.getIsClosedByAdmin()) && Boolean.TRUE.equals(dto.getIsActive())) {
                        throw new IllegalArgumentException("Cannot enable working hours on " + day + ": You have been scheduled off on this day by the Administrator.");
                    }

                    boolean active = isTenantActive && !Boolean.TRUE.equals(schedule.getIsClosedByAdmin()) && Boolean.TRUE.equals(dto.getIsActive());
                    schedule.setIsActive(active);
                    
                    if (dto.getOpeningTime() != null) schedule.setOpeningTime(dto.getOpeningTime());
                    if (dto.getClosingTime() != null) schedule.setClosingTime(dto.getClosingTime());

                    // Validate break time bounds only if active
                    if (active && dto.getBreakStartTime() != null && dto.getBreakEndTime() != null) {
                        if (!dto.getBreakStartTime().isBefore(dto.getBreakEndTime())) {
                            throw new IllegalArgumentException("Break start time must be before break end time on " + schedule.getDayOfWeek());
                        }
                        if (schedule.getOpeningTime() != null && schedule.getClosingTime() != null) {
                            if (dto.getBreakStartTime().isBefore(schedule.getOpeningTime()) || dto.getBreakEndTime().isAfter(schedule.getClosingTime())) {
                                throw new IllegalArgumentException("Break time must be within opening and closing hours on " + schedule.getDayOfWeek());
                            }
                        }
                    }

                    if (dto.getBreakStartTime() != null) schedule.setBreakStartTime(dto.getBreakStartTime());
                    if (dto.getBreakEndTime() != null) schedule.setBreakEndTime(dto.getBreakEndTime());
                    
                    if (schedule.getIsActive()) {
                        schedule.setClosedMessage(null);
                    } else {
                        schedule.setClosedMessage(dto.getClosedMessage() != null ? dto.getClosedMessage() : "Closed");
                    }
                    
                    providerScheduleRepository.save(schedule);
                }
            }
        }

        return getMyScheduleSettings(providerEmail);
    }
}
