package com.backend.service;

import com.backend.dto.ProviderScheduleDTO;
import com.backend.dto.ProviderScheduleSettingsDTO;
import com.backend.model.ProviderSchedule;
import com.backend.model.Tenant;
import com.backend.model.TenantSchedule;
import com.backend.model.User;
import com.backend.repository.ProviderScheduleRepository;
import com.backend.repository.TenantScheduleRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderScheduleService {

    private final ProviderScheduleRepository providerScheduleRepository;
    private final TenantScheduleRepository tenantScheduleRepository;
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
            boolean isTenantActive = tenantSchedules.stream()
                    .filter(ts -> ts.getDayOfWeek().equals(s.getDayOfWeek()))
                    .findFirst()
                    .map(TenantSchedule::getIsActive)
                    .orElse(true);

            return ProviderScheduleDTO.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .isActive(s.getIsActive())
                .isTenantActive(isTenantActive)
                .openingTime(s.getOpeningTime())
                .closingTime(s.getClosingTime())
                .breakStartTime(s.getBreakStartTime())
                .breakEndTime(s.getBreakEndTime())
                .closedMessage(s.getClosedMessage())
                .build();
        }).collect(Collectors.toList());

        return ProviderScheduleSettingsDTO.builder()
                .timezone(tenant.getTimezone() != null ? tenant.getTimezone() : "Asia/Kathmandu")
                .slotDuration(tenant.getSlotDuration() != null ? tenant.getSlotDuration() : 30)
                .schedules(scheduleDTOs)
                .build();
    }

    private List<ProviderSchedule> initializeDefaultSchedules(User provider, Tenant tenant) {
        List<TenantSchedule> tenantSchedules = tenantScheduleRepository.findByTenantId(tenant.getId());
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
                        .openingTime(tenant.getOpeningTime() != null ? tenant.getOpeningTime() : LocalTime.of(9, 0))
                        .closingTime(tenant.getClosingTime() != null ? tenant.getClosingTime() : LocalTime.of(17, 0))
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

        for (ProviderScheduleDTO dto : request.getSchedules()) {
            if (dto.getId() != null) {
                ProviderSchedule schedule = providerScheduleRepository.findById(dto.getId()).orElse(null);
                if (schedule != null && schedule.getProvider().getId().equals(provider.getId())) {
                    // Prevent enabling if tenant is not active on this day
                    boolean isTenantActive = tenantSchedules.stream()
                            .filter(ts -> ts.getDayOfWeek().equals(schedule.getDayOfWeek()))
                            .findFirst()
                            .map(TenantSchedule::getIsActive)
                            .orElse(true);
                            
                    schedule.setIsActive(isTenantActive && dto.getIsActive() != null ? dto.getIsActive() : false);
                    schedule.setOpeningTime(dto.getOpeningTime());
                    schedule.setClosingTime(dto.getClosingTime());

                    // Validate break time bounds
                    if (dto.getBreakStartTime() != null && dto.getBreakEndTime() != null && dto.getOpeningTime() != null && dto.getClosingTime() != null) {
                        if (dto.getBreakStartTime().isBefore(dto.getOpeningTime()) || dto.getBreakEndTime().isAfter(dto.getClosingTime())) {
                            throw new IllegalArgumentException("Break time must be within opening and closing hours on " + schedule.getDayOfWeek());
                        }
                    }

                    schedule.setBreakStartTime(dto.getBreakStartTime());
                    schedule.setBreakEndTime(dto.getBreakEndTime());
                    
                    // Clear closed message if active
                    if (schedule.getIsActive()) {
                        schedule.setClosedMessage(null);
                    } else {
                        schedule.setClosedMessage(dto.getClosedMessage());
                    }
                    
                    providerScheduleRepository.save(schedule);
                }
            }
        }

        return getScheduleSettings(adminEmail, providerId);
    }

    public ProviderScheduleSettingsDTO getMyScheduleSettings(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider not found"));
        
        if (!"service_provider".equals(provider.getRole())) {
            throw new RuntimeException("User is not a service provider");
        }

        Tenant tenant = provider.getTenant();
        List<ProviderSchedule> schedules = providerScheduleRepository.findByProviderOrderByDayOfWeek(provider);
        
        if (schedules.isEmpty()) {
            schedules = initializeDefaultSchedules(provider, tenant);
        }

        List<TenantSchedule> tenantSchedules = tenantScheduleRepository.findByTenantId(tenant.getId());

        List<ProviderScheduleDTO> scheduleDTOs = schedules.stream().map(s -> {
            boolean isTenantActive = tenantSchedules.stream()
                    .filter(ts -> ts.getDayOfWeek().equals(s.getDayOfWeek()))
                    .findFirst()
                    .map(TenantSchedule::getIsActive)
                    .orElse(true);

            return ProviderScheduleDTO.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .isActive(s.getIsActive())
                .isTenantActive(isTenantActive)
                .openingTime(s.getOpeningTime())
                .closingTime(s.getClosingTime())
                .breakStartTime(s.getBreakStartTime())
                .breakEndTime(s.getBreakEndTime())
                .closedMessage(s.getClosedMessage())
                .build();
        }).collect(Collectors.toList());

        return ProviderScheduleSettingsDTO.builder()
                .timezone(tenant.getTimezone() != null ? tenant.getTimezone() : "Asia/Kathmandu")
                .slotDuration(tenant.getSlotDuration() != null ? tenant.getSlotDuration() : 30)
                .schedules(scheduleDTOs)
                .build();
    }

    public ProviderScheduleSettingsDTO updateMyScheduleSettings(String providerEmail, ProviderScheduleSettingsDTO request) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider not found"));
                
        if (!"service_provider".equals(provider.getRole())) {
            throw new RuntimeException("User is not a service provider");
        }

        Tenant tenant = provider.getTenant();
        List<TenantSchedule> tenantSchedules = tenantScheduleRepository.findByTenantId(tenant.getId());

        for (ProviderScheduleDTO dto : request.getSchedules()) {
            if (dto.getId() != null) {
                ProviderSchedule schedule = providerScheduleRepository.findById(dto.getId()).orElse(null);
                if (schedule != null && schedule.getProvider().getId().equals(provider.getId())) {
                    // Prevent enabling if tenant is not active on this day
                    boolean isTenantActive = tenantSchedules.stream()
                            .filter(ts -> ts.getDayOfWeek().equals(schedule.getDayOfWeek()))
                            .findFirst()
                            .map(TenantSchedule::getIsActive)
                            .orElse(true);
                            
                    schedule.setIsActive(isTenantActive && dto.getIsActive() != null ? dto.getIsActive() : false);
                    schedule.setOpeningTime(dto.getOpeningTime());
                    schedule.setClosingTime(dto.getClosingTime());

                    // Validate break time bounds
                    if (dto.getBreakStartTime() != null && dto.getBreakEndTime() != null && dto.getOpeningTime() != null && dto.getClosingTime() != null) {
                        if (dto.getBreakStartTime().isBefore(dto.getOpeningTime()) || dto.getBreakEndTime().isAfter(dto.getClosingTime())) {
                            throw new IllegalArgumentException("Break time must be within opening and closing hours on " + schedule.getDayOfWeek());
                        }
                    }

                    schedule.setBreakStartTime(dto.getBreakStartTime());
                    schedule.setBreakEndTime(dto.getBreakEndTime());
                    
                    // Clear closed message if active
                    if (schedule.getIsActive()) {
                        schedule.setClosedMessage(null);
                    } else {
                        schedule.setClosedMessage(dto.getClosedMessage());
                    }
                    
                    providerScheduleRepository.save(schedule);
                }
            }
        }

        return getMyScheduleSettings(providerEmail);
    }
}
