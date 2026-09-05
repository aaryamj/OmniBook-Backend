package com.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ProviderProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String credentials;
    private String medicalLicense;
    private String primarySpecialty;
    
    @Column(length = 1000)
    private String profilePictureUrl;

    @Column(length = 1000)
    private String licenseImageUrl;

    @Enumerated(EnumType.STRING)
    private ProviderStatus status = ProviderStatus.SETUP_IN_PROGRESS;

    private String tier;
}
