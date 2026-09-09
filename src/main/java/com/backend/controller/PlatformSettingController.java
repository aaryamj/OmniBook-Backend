package com.backend.controller;

import com.backend.dto.PlatformInvoiceDTO;
import com.backend.dto.PlatformSettingDTO;
import com.backend.service.PlatformSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/superadmin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PlatformSettingController {

    private final PlatformSettingService platformSettingService;

    @GetMapping
    public ResponseEntity<PlatformSettingDTO> getSettings() {
        return ResponseEntity.ok(platformSettingService.getPlatformSettings());
    }

    @PutMapping
    public ResponseEntity<PlatformSettingDTO> updateSettings(@RequestBody PlatformSettingDTO request) {
        return ResponseEntity.ok(platformSettingService.updatePlatformSettings(request));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<PlatformInvoiceDTO>> getInvoices() {
        return ResponseEntity.ok(platformSettingService.getPlatformSettings().getInvoices());
    }

    @DeleteMapping("/invoices/{id}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        platformSettingService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
