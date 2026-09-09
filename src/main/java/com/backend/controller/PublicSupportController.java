package com.backend.controller;

import com.backend.dto.SupportTicketRequestDTO;
import com.backend.dto.SupportTicketResponseDTO;
import com.backend.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicSupportController {

    private final SupportTicketService supportTicketService;

    @PostMapping("/support-tickets")
    public ResponseEntity<SupportTicketResponseDTO> createPublicSupportTicket(
            @Valid @RequestBody SupportTicketRequestDTO request) {
        SupportTicketResponseDTO response = supportTicketService.createPublicTicket(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/newsletter/subscribe")
    public ResponseEntity<com.backend.model.NewsletterSubscriber> subscribeNewsletter(
            @Valid @RequestBody com.backend.dto.NewsletterSubscribeRequest request) {
        com.backend.model.NewsletterSubscriber response = supportTicketService.subscribeNewsletter(request);
        return ResponseEntity.ok(response);
    }
}
