package com.backend.service;

import com.backend.dto.SubscriptionPlanDTO;
import com.backend.model.SubscriptionPlan;
import com.backend.repository.SubscriptionPlanRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @PostConstruct
    @Transactional
    public void seedInitialCatalogIfEmpty() {
        if (subscriptionPlanRepository.count() == 0) {
            log.info("Seeding authentic OmniBook platform subscription catalog into database...");

            SubscriptionPlan starter = SubscriptionPlan.builder()
                    .name("Starter")
                    .displayName("Starter")
                    .monthlyPrice(2000.0)
                    .annualPrice(20400.0)
                    .description("Perfect for solo professionals, small clinics, salon studios, and specialized practitioners getting started.")
                    .features(String.join("\n",
                            "3 Active Service Providers / Staff",
                            "1 Operating Branch or Facility",
                            "Online Appointment Booking Calendar",
                            "Automated Email Confirmations & Alerts",
                            "Client Self-Scheduling Portal",
                            "Standard Support (Email)"))
                    .userLimit(3)
                    .appointmentLimit(100)
                    .active(true)
                    .isPopular(false)
                    .displayOrder(1)
                    .build();

            SubscriptionPlan professional = SubscriptionPlan.builder()
                    .name("Professional")
                    .displayName("Professional")
                    .monthlyPrice(5000.0)
                    .annualPrice(51000.0)
                    .description("Advanced appointment scheduling, automated alerts, and analytics for growing clinics, salons, colleges, and practices.")
                    .features(String.join("\n",
                            "15 Active Service Providers / Specialists",
                            "Multi-Branch & Location Management",
                            "AI Scheduling Chatbot for Client Bookings",
                            "WebSockets Real-Time Calendar Sync",
                            "Automated SMS & WhatsApp Alerts",
                            "Advanced KPI, Revenue & Attendance Dashboard",
                            "Audit Trail & Staff Delegated Access",
                            "Priority 24/7 Dedicated Support"))
                    .userLimit(15)
                    .appointmentLimit(500)
                    .active(true)
                    .isPopular(true)
                    .displayOrder(2)
                    .build();

            SubscriptionPlan enterprise = SubscriptionPlan.builder()
                    .name("Enterprise")
                    .displayName("Enterprise")
                    .monthlyPrice(15000.0)
                    .annualPrice(153000.0)
                    .description("Custom, high-throughput solutions for multi-location institutions, colleges, hospital networks, and large organizations.")
                    .features(String.join("\n",
                            "Unlimited Providers, Specialists & Staff",
                            "Unlimited Branches, Campuses & Operating Rooms",
                            "QR Code Visitor / Client Check-In & Queuing",
                            "Integrated Video Consultations & Virtual Appointments",
                            "Dedicated Customer Success Manager",
                            "Full REST API & Webhooks Access",
                            "Custom SLA & Enterprise Security Audit",
                            "White-Glove Onboarding & Workflow Migration"))
                    .userLimit(-1)
                    .appointmentLimit(-1)
                    .active(true)
                    .isPopular(false)
                    .displayOrder(3)
                    .build();

            subscriptionPlanRepository.saveAll(List.of(starter, professional, enterprise));
            log.info("OmniBook subscription catalog seeded successfully: Starter (Rs. 2,000/mo), Professional (Rs. 5,000/mo), Enterprise (Rs. 15,000/mo).");
        }
    }

    public List<SubscriptionPlanDTO> getActivePlans() {
        return subscriptionPlanRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<SubscriptionPlanDTO> getAllPlans() {
        return subscriptionPlanRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public SubscriptionPlan getPlanEntityById(Long id) {
        return subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription plan not found with ID: " + id));
    }

    public SubscriptionPlan getPlanEntityByName(String name) {
        return subscriptionPlanRepository.findByNameIgnoreCase(name)
                .orElse(null);
    }

    public Double getFallbackMonthlyPriceForTier(String tier) {
        if (tier == null || tier.isBlank()) return 2000.0;
        String clean = tier.trim().toLowerCase();
        SubscriptionPlan plan = subscriptionPlanRepository.findAll().stream()
                .filter(p -> clean.contains(p.getName().toLowerCase()))
                .findFirst()
                .orElse(null);
        if (plan != null && plan.getMonthlyPrice() != null) {
            return plan.getMonthlyPrice();
        }
        if (clean.contains("enterprise")) return 15000.0;
        if (clean.contains("pro")) return 5000.0;
        return 2000.0;
    }

    @Transactional
    public SubscriptionPlanDTO createPlan(SubscriptionPlanDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("Plan identifier name is required");
        }
        if (subscriptionPlanRepository.existsByNameIgnoreCase(dto.getName().trim())) {
            throw new RuntimeException("A plan with name '" + dto.getName() + "' already exists.");
        }

        String featuresText = dto.getFeaturesRaw();
        if ((featuresText == null || featuresText.isBlank()) && dto.getFeatures() != null) {
            featuresText = String.join("\n", dto.getFeatures());
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(dto.getName().trim())
                .displayName(dto.getDisplayName() != null && !dto.getDisplayName().isBlank() ? dto.getDisplayName().trim() : dto.getName().trim())
                .monthlyPrice(dto.getMonthlyPrice() != null ? dto.getMonthlyPrice() : 2000.0)
                .annualPrice(dto.getAnnualPrice() != null ? dto.getAnnualPrice() : (dto.getMonthlyPrice() != null ? dto.getMonthlyPrice() * 10.2 : 20400.0))
                .description(dto.getDescription())
                .features(featuresText != null ? featuresText.trim() : "")
                .userLimit(dto.getUserLimit() != null ? dto.getUserLimit() : -1)
                .appointmentLimit(dto.getAppointmentLimit() != null ? dto.getAppointmentLimit() : -1)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .isPopular(dto.getIsPopular() != null ? dto.getIsPopular() : false)
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 99)
                .build();

        return mapToDTO(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanDTO updatePlan(Long id, SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription plan not found with ID: " + id));

        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) {
            plan.setDisplayName(dto.getDisplayName().trim());
        }
        if (dto.getMonthlyPrice() != null) {
            plan.setMonthlyPrice(dto.getMonthlyPrice());
        }
        if (dto.getAnnualPrice() != null) {
            plan.setAnnualPrice(dto.getAnnualPrice());
        }
        if (dto.getDescription() != null) {
            plan.setDescription(dto.getDescription());
        }
        if (dto.getFeaturesRaw() != null) {
            plan.setFeatures(dto.getFeaturesRaw().trim());
        } else if (dto.getFeatures() != null) {
            plan.setFeatures(String.join("\n", dto.getFeatures()).trim());
        }
        if (dto.getUserLimit() != null) {
            plan.setUserLimit(dto.getUserLimit());
        }
        if (dto.getAppointmentLimit() != null) {
            plan.setAppointmentLimit(dto.getAppointmentLimit());
        }
        if (dto.getActive() != null) {
            plan.setActive(dto.getActive());
        }
        if (dto.getIsPopular() != null) {
            plan.setIsPopular(dto.getIsPopular());
        }
        if (dto.getDisplayOrder() != null) {
            plan.setDisplayOrder(dto.getDisplayOrder());
        }

        return mapToDTO(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanDTO togglePlanStatus(Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription plan not found with ID: " + id));
        plan.setActive(plan.getActive() == null || !plan.getActive());
        return mapToDTO(subscriptionPlanRepository.save(plan));
    }

    public SubscriptionPlanDTO mapToDTO(SubscriptionPlan plan) {
        if (plan == null) return null;
        List<String> featuresList = Collections.emptyList();
        if (plan.getFeatures() != null && !plan.getFeatures().isBlank()) {
            featuresList = Arrays.stream(plan.getFeatures().split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return SubscriptionPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .displayName(plan.getDisplayName())
                .monthlyPrice(plan.getMonthlyPrice())
                .annualPrice(plan.getAnnualPrice())
                .description(plan.getDescription())
                .features(featuresList)
                .featuresRaw(plan.getFeatures())
                .userLimit(plan.getUserLimit())
                .appointmentLimit(plan.getAppointmentLimit())
                .active(plan.getActive())
                .isPopular(plan.getIsPopular())
                .displayOrder(plan.getDisplayOrder())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
