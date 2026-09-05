package com.backend.repository;

import com.backend.model.ProviderProfile;
import com.backend.model.ProviderService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderServiceRepository extends JpaRepository<ProviderService, Long> {
    List<ProviderService> findByProviderProfile(ProviderProfile providerProfile);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.serviceName FROM ProviderService p WHERE p.providerProfile.user.tenant.id = :tenantId AND p.isActive = true")
    List<String> findDistinctServiceNamesByTenantId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}
