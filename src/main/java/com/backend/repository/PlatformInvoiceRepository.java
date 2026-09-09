package com.backend.repository;

import com.backend.model.PlatformInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformInvoiceRepository extends JpaRepository<PlatformInvoice, Long> {
    List<PlatformInvoice> findAllByOrderByCreatedAtDesc();
    java.util.Optional<PlatformInvoice> findByOrderNumber(String orderNumber);
    java.util.Optional<PlatformInvoice> findByInvoiceNumber(String invoiceNumber);
}
