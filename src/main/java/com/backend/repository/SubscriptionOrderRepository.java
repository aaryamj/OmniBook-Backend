package com.backend.repository;

import com.backend.model.SubscriptionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {
    List<SubscriptionOrder> findAllByOrderByCreatedAtDesc();
    List<SubscriptionOrder> findTop20ByOrderByCreatedAtDesc();
    Optional<SubscriptionOrder> findByOrderNumber(String orderNumber);
    Optional<SubscriptionOrder> findByInvoiceNumber(String invoiceNumber);
    boolean existsByRegistrationNumber(String registrationNumber);

    @Query("SELECT s FROM SubscriptionOrder s WHERE " +
           "LOWER(COALESCE(s.organizationName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(s.registrationNumber, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(s.adminEmail, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(s.adminFullName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(s.adminPhone, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(s.address, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(s.orderNumber, '')) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY s.createdAt DESC")
    List<SubscriptionOrder> searchOrders(@Param("query") String query);
}
