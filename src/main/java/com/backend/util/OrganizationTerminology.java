package com.backend.util;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizationTerminology {

    private String rawType;
    private String category;
    private String organizationLabel;
    private String customerTerm;
    private String providerTerm;
    private String serviceTerm;
    private String appointmentTerm;
    private boolean isHealthcare;
    private String iconName;

    /**
     * Resolves localized domain terminology based on tenant organizationType string.
     */
    public static OrganizationTerminology from(String rawOrgType) {
        String norm = (rawOrgType != null) ? rawOrgType.trim().toLowerCase() : "general";

        if (norm.contains("clinic") || norm.contains("hosp") || norm.contains("medic") || norm.contains("health") || norm.contains("dent")) {
            return OrganizationTerminology.builder()
                    .rawType("Clinic")
                    .category("Healthcare & Medical")
                    .organizationLabel("Clinic / Healthcare Center")
                    .customerTerm("Patient")
                    .providerTerm("Doctor")
                    .serviceTerm("Medical Consultation / Treatment")
                    .appointmentTerm("Check-up / Consultation")
                    .isHealthcare(true)
                    .iconName("medical_services")
                    .build();
        }

        if (norm.contains("saloon") || norm.contains("salon") || norm.contains("spa") || norm.contains("beauty") || norm.contains("hair")) {
            return OrganizationTerminology.builder()
                    .rawType("Salon")
                    .category("Personal Care & Styling")
                    .organizationLabel("Salon & Spa")
                    .customerTerm("Client")
                    .providerTerm("Stylist")
                    .serviceTerm("Styling Service / Treatment")
                    .appointmentTerm("Styling Session / Appointment")
                    .isHealthcare(false)
                    .iconName("spa")
                    .build();
        }

        if (norm.contains("college") || norm.contains("school") || norm.contains("acad") || norm.contains("univ") || norm.contains("educ")) {
            return OrganizationTerminology.builder()
                    .rawType("College")
                    .category("Education & Academic Advising")
                    .organizationLabel("College / Academic Institution")
                    .customerTerm("Student")
                    .providerTerm("Instructor")
                    .serviceTerm("Advising Session / Lab Slot")
                    .appointmentTerm("Office Hour / Advising Session")
                    .isHealthcare(false)
                    .iconName("school")
                    .build();
        }

        if (norm.contains("gym") || norm.contains("fitness") || norm.contains("train") || norm.contains("crossfit")) {
            return OrganizationTerminology.builder()
                    .rawType("Fitness")
                    .category("Fitness & Athletics")
                    .organizationLabel("Fitness Center / Gym")
                    .customerTerm("Member")
                    .providerTerm("Trainer")
                    .serviceTerm("Training Session / Workout")
                    .appointmentTerm("Training Session")
                    .isHealthcare(false)
                    .iconName("fitness_center")
                    .build();
        }

        if (norm.contains("law") || norm.contains("legal") || norm.contains("consult") || norm.contains("financ") || norm.contains("advis")) {
            return OrganizationTerminology.builder()
                    .rawType("Consulting")
                    .category("Professional & Legal Services")
                    .organizationLabel("Consulting Firm / Office")
                    .customerTerm("Client")
                    .providerTerm("Consultant")
                    .serviceTerm("Advisory Service / Consultation")
                    .appointmentTerm("Consultation Session")
                    .isHealthcare(false)
                    .iconName("business")
                    .build();
        }

        // General Enterprise default
        return OrganizationTerminology.builder()
                .rawType("General")
                .category("Professional Appointment Services")
                .organizationLabel("Organization")
                .customerTerm("Customer")
                .providerTerm("Specialist")
                .serviceTerm("Service")
                .appointmentTerm("Appointment")
                .isHealthcare(false)
                .iconName("domain")
                .build();
    }

    /**
     * Formats a provider's full name with optional specialty and appropriate prefix.
     */
    public static String formatProviderDisplay(String fullName, String specialty, String orgType) {
        OrganizationTerminology terms = from(orgType);
        String cleanName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : terms.getProviderTerm();

        boolean alreadyHasDr = cleanName.toLowerCase().startsWith("dr.") || cleanName.toLowerCase().startsWith("dr ");
        String formattedName;

        if (terms.isHealthcare()) {
            formattedName = alreadyHasDr ? cleanName : "Dr. " + cleanName;
        } else {
            // Non-healthcare (e.g. Salon, College, Gym): strip any accidental "Dr." unless part of actual name
            formattedName = cleanName;
        }

        if (specialty != null && !specialty.trim().isEmpty()) {
            return formattedName + " (" + specialty.trim() + ")";
        }
        return formattedName;
    }
}
