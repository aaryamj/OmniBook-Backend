package com.backend.repository;

import com.backend.model.SubscriptionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {
    List<SubscriptionOrder> findAllByOrderByCreatedAtDesc();
    Optional<SubscriptionOrder> findByOrderNumber(String orderNumber);
    Optional<SubscriptionOrder> findByInvoiceNumber(String invoiceNumber);
    boolean existsByRegistrationNumber(String registrationNumber);
}
