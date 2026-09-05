package com.backend.controller;

import com.backend.dto.AIChatRequestDTO;
import com.backend.dto.AIChatResponseDTO;
import com.backend.dto.AISlotDTO;
import com.backend.model.AIChatMessage;
import com.backend.model.AIConversation;
import com.backend.repository.AIChatMessageRepository;
import com.backend.repository.AIConversationRepository;
import com.backend.service.AIAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public/ai")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class AIBookingController {

    private final AIAssistantService aiAssistantService;
    private final AIConversationRepository conversationRepository;
    private final AIChatMessageRepository chatMessageRepository;

    @PostMapping("/chat")
    public ResponseEntity<AIChatResponseDTO> chat(@RequestBody AIChatRequestDTO request) {
        return ResponseEntity.ok(aiAssistantService.getChatResponse(request));
    }

    @GetMapping("/recommend")
    public ResponseEntity<List<AISlotDTO>> getRecommendations(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String clinicId,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String service) {
        return ResponseEntity.ok(aiAssistantService.getRecommendations(userEmail, clinicId, providerId, service));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getConversationMessages(@PathVariable String conversationId) {
        Optional<AIConversation> convOpt = conversationRepository.findByConversationId(conversationId);
        if (convOpt.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<AIChatMessage> messages = chatMessageRepository.findByConversationOrderByCreatedAtAsc(convOpt.get());
        List<Map<String, Object>> result = messages.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "role", m.getRole(),
                "content", m.getContent(),
                "actionSlotId", m.getActionSlotId() != null ? m.getActionSlotId() : "",
                "createdAt", m.getCreatedAt().toString()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
