package com.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ProviderService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "provider_profile_id")
    @JsonIgnore
    private ProviderProfile providerProfile;

    private String serviceName;
    private Integer durationMinutes;
    private Double fee;
    private Boolean isTelemedicine;
    private String category;
    private Boolean isActive = true;

    @Column(name = "max_capacity", nullable = false, columnDefinition = "int default 1")
    private Integer maxCapacity = 1;
}
