package com.backend;

import com.backend.dto.AdminSubscriptionOverviewDTO;
import com.backend.dto.SubscriptionExtensionRequestDTO;
import com.backend.model.SubscriptionExtensionRequest;
import com.backend.model.SubscriptionOrder;
import com.backend.model.SubscriptionPlan;
import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.PlatformInvoiceRepository;
import com.backend.repository.SubscriptionExtensionRequestRepository;
import com.backend.repository.SubscriptionOrderRepository;
import com.backend.repository.SubscriptionPlanRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import com.backend.service.AuditLogService;
import com.backend.service.NotificationService;
import com.backend.service.SubscriptionPlanService;
import com.backend.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SubscriptionLifecycleTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @Mock
    private SubscriptionOrderRepository subscriptionOrderRepository;

    @Mock
    private PlatformInvoiceRepository platformInvoiceRepository;

    @Mock
    private SubscriptionExtensionRequestRepository subscriptionExtensionRequestRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.backend.service.RevenueAnalyticsService revenueAnalyticsService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Tenant activeTenant;
    private Tenant expiredTenant;
    private User activeAdmin;
    private User expiredAdmin;
    private User superAdmin;
    private SubscriptionPlan starterPlan;

    @BeforeEach
    void setUp() {
        starterPlan = new SubscriptionPlan();
        starterPlan.setId(1L);
        starterPlan.setName("STARTER");
        starterPlan.setDisplayName("Starter Plan");
        starterPlan.setMonthlyPrice(2000.0);
        starterPlan.setAnnualPrice(20400.0);
        starterPlan.setUserLimit(3);
        starterPlan.setAppointmentLimit(100);
        starterPlan.setActive(true);

        activeTenant = new Tenant();
        activeTenant.setId(10L);
        activeTenant.setName("Healthy Smile Clinic");
        activeTenant.setSubscriptionTier("STARTER");
        activeTenant.setBillingCycle("Monthly");
        activeTenant.setSubscriptionStatus("ACTIVE");
        activeTenant.setStatus("ACTIVE");
        activeTenant.setSubscriptionStartDate(LocalDate.now().minusDays(10));
        activeTenant.setSubscriptionExpiryDate(LocalDate.now().plusDays(20));

        activeAdmin = new User();
        activeAdmin.setId(101L);
        activeAdmin.setEmail("active@clinic.com");
        activeAdmin.setTenant(activeTenant);

        expiredTenant = new Tenant();
        expiredTenant.setId(11L);
        expiredTenant.setName("City Care Clinic");
        expiredTenant.setSubscriptionTier("STARTER");
        expiredTenant.setBillingCycle("Monthly");
        expiredTenant.setSubscriptionStatus("EXPIRED");
        expiredTenant.setStatus("ACTIVE");
        expiredTenant.setSubscriptionStartDate(LocalDate.now().minusDays(40));
        expiredTenant.setSubscriptionExpiryDate(LocalDate.now().minusDays(10));

        expiredAdmin = new User();
        expiredAdmin.setId(102L);
        expiredAdmin.setEmail("expired@clinic.com");
        expiredAdmin.setTenant(expiredTenant);

        superAdmin = new User();
        superAdmin.setId(999L);
        superAdmin.setEmail("SuperAdmin");
        superAdmin.setFullName("Super Admin User");
        superAdmin.setRole("ROLE_SUPER_ADMIN");

        when(userRepository.findByEmail("SuperAdmin")).thenReturn(Optional.of(superAdmin));
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(activeTenant));
        when(tenantRepository.findById(11L)).thenReturn(Optional.of(expiredTenant));
    }

    @Test
    void testEarlyRenewal_PreservesRemainingValidity() {
        when(userRepository.findByEmail("active@clinic.com")).thenReturn(Optional.of(activeAdmin));
        when(subscriptionPlanService.getPlanEntityByName("STARTER")).thenReturn(starterPlan);

        LocalDate originalExpiry = activeTenant.getSubscriptionExpiryDate(); // 20 days in future

        // Admin initiates renewal
        var response = subscriptionService.renewSubscription("active@clinic.com", "Monthly", "eSewa");

        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));

        // New expiry date calculated in order MUST be originalExpiry + 1 month
        LocalDate expectedExpiry = originalExpiry.plusMonths(1);
        assertEquals(expectedExpiry.toString(), response.get("newExpiryDate").toString());
        verify(subscriptionOrderRepository, times(1)).save(any(SubscriptionOrder.class));
    }

    @Test
    void testLateRenewal_StartsFromToday() {
        when(userRepository.findByEmail("expired@clinic.com")).thenReturn(Optional.of(expiredAdmin));
        when(subscriptionPlanService.getPlanEntityByName("STARTER")).thenReturn(starterPlan);

        var response = subscriptionService.renewSubscription("expired@clinic.com", "Monthly", "eSewa");

        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));

        // For expired tenant, new cycle starts from today + 1 month
        LocalDate expectedExpiry = LocalDate.now().plusMonths(1);
        assertEquals(expectedExpiry.toString(), response.get("newExpiryDate").toString());
        verify(subscriptionOrderRepository, times(1)).save(any(SubscriptionOrder.class));
    }

    @Test
    void testEmergencyExtensionApproval_ExtendsExpiryAndRecordsAudit() {
        SubscriptionExtensionRequest req = new SubscriptionExtensionRequest();
        req.setId(50L);
        req.setTenant(activeTenant);
        req.setTenantId(10L);
        req.setRequestedDays(14);
        req.setReason("Banking transition in progress");
        req.setStatus("PENDING");
        req.setPreviousExpiryDate(activeTenant.getSubscriptionExpiryDate());

        when(subscriptionExtensionRequestRepository.findById(50L)).thenReturn(Optional.of(req));
        when(subscriptionExtensionRequestRepository.save(any(SubscriptionExtensionRequest.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate oldExpiry = activeTenant.getSubscriptionExpiryDate();

        // Super Admin approves with 10 approved days
        SubscriptionExtensionRequestDTO result = subscriptionService.reviewExtensionRequest(
                50L, true, 10, "Granted temporary grace period", "SuperAdmin"
        );

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertEquals(10, result.getApprovedDays());

        // Tenant expiry date extended by 10 days
        assertEquals(oldExpiry.plusDays(10), activeTenant.getSubscriptionExpiryDate());
        assertEquals("ACTIVE", activeTenant.getSubscriptionStatus());
        verify(tenantRepository, times(1)).save(activeTenant);
    }

    @Test
    void testManualSuspendAndReactivate() {
        // Super Admin manual suspension
        subscriptionService.manualSuspendSubscription(10L, "Terms violation investigation", "SuperAdmin");

        assertEquals("SUSPENDED", activeTenant.getSubscriptionStatus());
        assertEquals("Terms violation investigation", activeTenant.getLastSuspendedReason());

        // Overview reflects gating
        when(userRepository.findByEmail("active@clinic.com")).thenReturn(Optional.of(activeAdmin));
        when(subscriptionPlanService.getPlanEntityByName("STARTER")).thenReturn(starterPlan);
        when(userRepository.findByTenantId(10L)).thenReturn(Collections.singletonList(activeAdmin));

        AdminSubscriptionOverviewDTO overview = subscriptionService.getAdminSubscriptionOverview("active@clinic.com");
        assertTrue(overview.isGated());
        assertEquals("SUSPENDED", overview.getSubscriptionStatus());

        // Super Admin reactivates
        subscriptionService.manualReactivateSubscription(10L, "SuperAdmin");
        assertEquals("ACTIVE", activeTenant.getSubscriptionStatus());
        assertNull(activeTenant.getLastSuspendedReason());
    }

    @Test
    void testVerifyEsewaSubscription_Idempotency() {
        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNumber("ORD-IDEMP-101")
                .tenantId(10L)
                .amount(2000.0)
                .planTier("Starter")
                .billingCycle("Monthly")
                .paymentStatus("PENDING_PAYMENT")
                .verificationStatus("PENDING_VERIFICATION")
                .build();

        when(subscriptionOrderRepository.findByOrderNumber("ORD-IDEMP-101")).thenReturn(Optional.of(order));
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(activeTenant));

        String rawJson = "{\"status\":\"COMPLETE\",\"transaction_uuid\":\"ORD-IDEMP-101\",\"transaction_code\":\"ESEWA-TX-99\"}";
        String encoded = java.util.Base64.getEncoder().encodeToString(rawJson.getBytes());

        // First verification call
        String result1 = subscriptionService.verifyEsewaSubscription(encoded);
        assertEquals("ORD-IDEMP-101", result1);
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("ESEWA-TX-99", order.getTransactionId());
        verify(tenantRepository, times(1)).save(activeTenant);

        // Second duplicate verification call (webhook retry or browser reload)
        String result2 = subscriptionService.verifyEsewaSubscription(encoded);
        assertEquals("ORD-IDEMP-101", result2);
        // Tenant must NOT be saved/extended again
        verify(tenantRepository, times(1)).save(activeTenant);
    }

    @Test
    void testMarkOrderCancelled_PreservesExistingSubscription() {
        LocalDate originalExpiry = activeTenant.getSubscriptionExpiryDate();
        String originalTier = activeTenant.getSubscriptionTier();

        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNumber("ORD-CANCEL-202")
                .tenantId(10L)
                .amount(5000.0)
                .planTier("Professional")
                .billingCycle("Monthly")
                .paymentStatus("PENDING_PAYMENT")
                .verificationStatus("PENDING_VERIFICATION")
                .build();

        when(subscriptionOrderRepository.findByOrderNumber("ORD-CANCEL-202")).thenReturn(Optional.of(order));

        // Admin cancels gateway payment
        subscriptionService.markOrderCancelled("ORD-CANCEL-202");

        assertEquals("CANCELLED", order.getPaymentStatus());
        assertEquals("CANCELLED", order.getVerificationStatus());
        verify(subscriptionOrderRepository, times(1)).save(order);

        // Existing tenant subscription remains completely untouched
        assertEquals(originalExpiry, activeTenant.getSubscriptionExpiryDate());
        assertEquals(originalTier, activeTenant.getSubscriptionTier());
        verify(tenantRepository, never()).save(activeTenant);
    }
}
