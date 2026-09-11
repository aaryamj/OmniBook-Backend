package com.backend.repository;

import com.backend.model.SubscriptionExtensionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionExtensionRequestRepository extends JpaRepository<SubscriptionExtensionRequest, Long> {

    List<SubscriptionExtensionRequest> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<SubscriptionExtensionRequest> findAllByOrderByCreatedAtDesc();

    List<SubscriptionExtensionRequest> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByTenantIdAndStatus(Long tenantId, String status);
}
