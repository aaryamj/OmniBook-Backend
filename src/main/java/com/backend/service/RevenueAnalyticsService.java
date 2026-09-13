package com.backend.service;

import com.backend.model.AppointmentCommission;
import com.backend.model.PlatformInvoice;
import com.backend.model.SubscriptionOrder;
import com.backend.model.Tenant;
import com.backend.repository.AppointmentCommissionRepository;
import com.backend.repository.PlatformInvoiceRepository;
import com.backend.repository.SubscriptionOrderRepository;
import com.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueAnalyticsService {

    private final TenantRepository tenantRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final AppointmentCommissionRepository appointmentCommissionRepository;
    private final SubscriptionPlanService subscriptionPlanService;

    /**
     * Finds the latest successful, non-refunded subscription payment for a tenant.
     * Matches by tenantId first, then falls back to registrationNumber or organizationName.
     */
    public SubscriptionOrder getLatestValidSubscriptionOrder(Tenant tenant) {
        if (tenant == null) return null;

        List<SubscriptionOrder> allOrders = subscriptionOrderRepository.findAllByOrderByCreatedAtDesc();
        for (SubscriptionOrder order : allOrders) {
            // Must be PAID and not REFUNDED
            if ("PAID".equalsIgnoreCase(order.getPaymentStatus()) 
                    && !"REFUNDED".equalsIgnoreCase(order.getPaymentStatus())
                    && !"REJECTED".equalsIgnoreCase(order.getVerificationStatus())) {

                if (order.getTenantId() != null && order.getTenantId().equals(tenant.getId())) {
                    return order;
                }
                if (tenant.getRegistrationNumber() != null 
                        && tenant.getRegistrationNumber().equalsIgnoreCase(order.getRegistrationNumber())) {
                    return order;
                }
                if (tenant.getOrganizationName() != null 
                        && tenant.getOrganizationName().equalsIgnoreCase(order.getOrganizationName())) {
                    return order;
                }
            }
        }
        return null;
    }

    /**
     * Source of truth: Calculates monthly MRR contribution for a single tenant.
     * For active tenants:
     * - If paid transaction exists: Monthly = amount, Annual = amount / 12.0
     * - If unbilled/seeded tenant: retrieves authentic monthly catalog price from DB.
     */
    public double calculateTenantMRR(Tenant tenant) {
        if (tenant == null || !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            return 0.0;
        }

        SubscriptionOrder order = getLatestValidSubscriptionOrder(tenant);
        if (order != null && order.getAmount() != null && order.getAmount() > 0) {
            String cycle = order.getBillingCycle() != null ? order.getBillingCycle().toLowerCase() : "";
            if (cycle.contains("annual") || cycle.contains("year")) {
                return Math.round((order.getAmount() / 12.0) * 100.0) / 100.0;
            } else {
                return order.getAmount();
            }
        }

        // Fallback for seeded or manually onboarded clinics without historical payment order:
        // Use the tenant's assigned plan tier and its authentic catalog monthly price from DB!
        String tier = tenant.getSubscriptionTier();
        Double catalogPrice = subscriptionPlanService.getFallbackMonthlyPriceForTier(tier);
        return catalogPrice != null ? catalogPrice : 2000.0;
    }

    /**
     * Total Subscription MRR across all active tenants.
     */
    public double calculateTotalMRR() {
        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE");
        return calculateMRRForTenants(activeTenants);
    }

    /**
     * Calculates Subscription MRR dynamically for a filtered list of tenants.
     */
    public double calculateMRRForTenants(List<Tenant> tenants) {
        if (tenants == null || tenants.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Tenant t : tenants) {
            total += calculateTenantMRR(t);
        }
        return Math.round(total * 100.0) / 100.0;
    }

    /**
     * Calculates Subscription Revenue (actual cash received from paid non-refunded orders)
     * during a given time window.
     */
    public double calculateSubscriptionRevenueBetween(LocalDateTime start, LocalDateTime end) {
        List<SubscriptionOrder> orders = subscriptionOrderRepository.findAll();
        double revenue = 0.0;
        for (SubscriptionOrder o : orders) {
            if ("PAID".equalsIgnoreCase(o.getPaymentStatus()) 
                    && !"REFUNDED".equalsIgnoreCase(o.getPaymentStatus())
                    && !"REJECTED".equalsIgnoreCase(o.getVerificationStatus())) {
                LocalDateTime paidAt = o.getCreatedAt();
                if (paidAt != null) {
                    if (start != null && paidAt.isBefore(start)) continue;
                    if (end != null && paidAt.isAfter(end)) continue;
                    revenue += (o.getAmount() != null ? o.getAmount() : 0.0);
                }
            }
        }
        return Math.round(revenue * 100.0) / 100.0;
    }

    /**
     * Calculates total appointment platform commission collected during a given time window.
     */
    public double calculateAppointmentCommissionRevenueBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            Double allTime = appointmentCommissionRepository.sumTotalCommissionAllTime();
            return allTime != null ? Math.round(allTime * 100.0) / 100.0 : 0.0;
        }
        LocalDateTime effectiveStart = start != null ? start : LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now();
        Double sum = appointmentCommissionRepository.sumCommissionBetween(effectiveStart, effectiveEnd);
        return sum != null ? Math.round(sum * 100.0) / 100.0 : 0.0;
    }

    /**
     * Calculates Combined Total Platform Revenue (Subscription Revenue + Appointment Commission Revenue)
     * for a given time window without double-counting.
     */
    public double calculateTotalPlatformRevenueBetween(LocalDateTime start, LocalDateTime end) {
        double subRevenue = calculateSubscriptionRevenueBetween(start, end);
        double commRevenue = calculateAppointmentCommissionRevenueBetween(start, end);
        return Math.round((subRevenue + commRevenue) * 100.0) / 100.0;
    }

    /**
     * Calculates MRR of subscriptions that were legitimately active at a specific target point in history.
     */
    public double calculateActiveMRRAtDate(List<Tenant> allTenants, LocalDateTime targetDate) {
        double periodMRR = 0.0;
        LocalDate targetDay = targetDate.toLocalDate();

        for (Tenant t : allTenants) {
            // Tenant must have been created on or before target date
            if (t.getCreatedAt() != null && !t.getCreatedAt().isAfter(targetDate)) {
                // Check if active at target date
                SubscriptionOrder order = getLatestValidSubscriptionOrder(t);
                if (order != null && order.getCreatedAt() != null && !order.getCreatedAt().isAfter(targetDate)) {
                    // Check if subscription was unexpired at targetDate
                    LocalDate start = order.getSubscriptionStartDate() != null 
                            ? order.getSubscriptionStartDate() 
                            : order.getCreatedAt().toLocalDate();
                    LocalDate expiry = order.getSubscriptionExpiryDate() != null 
                            ? order.getSubscriptionExpiryDate() 
                            : (SubscriptionService.isAnnualCycle(order.getBillingCycle()) ? start.plusYears(1) : start.plusMonths(1));

                    if (!targetDay.isBefore(start) && !targetDay.isAfter(expiry)) {
                        String cycle = order.getBillingCycle() != null ? order.getBillingCycle().toLowerCase() : "";
                        if (cycle.contains("annual") || cycle.contains("year")) {
                            periodMRR += (order.getAmount() != null ? order.getAmount() / 12.0 : 0.0);
                        } else {
                            periodMRR += (order.getAmount() != null ? order.getAmount() : 0.0);
                        }
                        continue;
                    }
                }

                // If seeded clinic active at that time
                if ("ACTIVE".equalsIgnoreCase(t.getStatus())) {
                    periodMRR += subscriptionPlanService.getFallbackMonthlyPriceForTier(t.getSubscriptionTier());
                }
            }
        }
        return Math.round(periodMRR * 100.0) / 100.0;
    }

    /**
     * Generates dynamic historical revenue trajectory data for 1W (7 days), 1M (4 weeks), and 1Y (12 months).
     */
    public Map<String, List<Double>> calculateHistoricalRevenueChartData() {
        List<Tenant> allTenants = tenantRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        // 1W: Daily data for last 7 days
        List<Double> weekData = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime day = now.minusDays(i);
            weekData.add(calculateActiveMRRAtDate(allTenants, day));
        }

        // 1M: Weekly data for last 4 weeks
        List<Double> monthData = new ArrayList<>();
        for (int i = 3; i >= 0; i--) {
            LocalDateTime week = now.minusWeeks(i);
            monthData.add(calculateActiveMRRAtDate(allTenants, week));
        }

        // 1Y: Monthly data for last 12 months
        List<Double> yearData = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            LocalDateTime month = now.minusMonths(i);
            yearData.add(calculateActiveMRRAtDate(allTenants, month));
        }

        Map<String, List<Double>> chartData = new HashMap<>();
        chartData.put("1W", weekData);
        chartData.put("1M", monthData);
        chartData.put("1Y", yearData);
        return chartData;
    }
}
