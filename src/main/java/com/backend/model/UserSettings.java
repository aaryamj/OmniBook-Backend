package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String country;
    private boolean twoStepEnabled;

    private boolean notifBookingEmail;
    private boolean notifBookingSms;
    private boolean notifBookingInApp;

    private boolean notifReminderEmail;
    private boolean notifReminderSms;
    private boolean notifReminderInApp;

    private boolean notifCancellationEmail;
    private boolean notifCancellationSms;
    private boolean notifCancellationInApp;

    private boolean notifExclusiveDiscounts;
    private boolean notifNewsletter;
}
