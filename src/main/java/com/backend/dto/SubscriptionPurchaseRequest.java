package com.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPurchaseRequest {

    @NotBlank(message = "Organization name is required")
    private String organizationName;

    @NotBlank(message = "Organization type is required")
    private String organizationType;

    @NotBlank(message = "Registration or PAN number is required")
    private String registrationNumber;

    private String address;

    @NotBlank(message = "Administrator name is required")
    private String adminFullName;

    @NotBlank(message = "Administrator email is required")
    @Email(message = "Invalid email address")
    private String adminEmail;

    private String adminPhone;

    @NotBlank(message = "Plan tier is required")
    private String planTier;

    @NotBlank(message = "Billing cycle is required")
    private String billingCycle;

    @NotNull(message = "Amount is required")
    private Double amount;

    private String currency;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}
