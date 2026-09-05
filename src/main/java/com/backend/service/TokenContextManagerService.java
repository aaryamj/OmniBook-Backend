package com.backend.service;

import com.backend.model.AIChatMessage;
import com.backend.model.AIConversation;
import com.backend.repository.AIChatMessageRepository;
import com.backend.repository.AIConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenContextManagerService {

    private static final int MAX_ACTIVE_CONVERSATION_TURNS = 20;

    private final AIChatMessageRepository chatMessageRepository;
    private final AIConversationRepository conversationRepository;
    private final GeminiClientService geminiClientService;

    /**
     * Prepares active messages bounded to the most recent 20 turns,
     * and asynchronously/proactively compresses older turns into the conversation summary.
     */
    public List<Map<String, Object>> getActiveContextAndMaintainSummary(AIConversation conversation) {
        // 1. Retrieve the most recent 20 messages (ordered descending, then reverse for chronological order)
        List<AIChatMessage> recentDesc = chatMessageRepository.findRecentMessagesByConversation(
                conversation,
                PageRequest.of(0, MAX_ACTIVE_CONVERSATION_TURNS)
        );

        List<AIChatMessage> activeChronological = new ArrayList<>(recentDesc);
        Collections.reverse(activeChronological);

        // 2. Check if older messages exist that need summarization
        if (!recentDesc.isEmpty()) {
            Long oldestActiveId = activeChronological.get(0).getId();
            compressOlderMessagesIntoSummary(conversation, oldestActiveId);
        }

        // 3. Format active 20 turns into Gemini contents structure
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AIChatMessage msg : activeChronological) {
            String role = "user".equalsIgnoreCase(msg.getRole()) ? "user" : "model";
            Map<String, Object> contentObj = new HashMap<>();
            contentObj.put("role", role);
            contentObj.put("parts", List.of(Map.of("text", msg.getContent())));
            contents.add(contentObj);
        }

        return contents;
    }

    /**
     * Checks for messages older than the active window and summarizes them into AIConversation.summary.
     */
    public void compressOlderMessagesIntoSummary(AIConversation conversation, Long oldestActiveId) {
        List<AIChatMessage> olderUnsummarized = chatMessageRepository.findUnsummarizedMessagesBefore(conversation, oldestActiveId);
        if (olderUnsummarized == null || olderUnsummarized.isEmpty()) {
            return;
        }

        log.info("Found {} older unsummarized messages for conversation {}. Compressing into persistent summary...",
                olderUnsummarized.size(), conversation.getConversationId());

        List<Map<String, String>> olderTurns = olderUnsummarized.stream()
                .map(m -> Map.of("role", m.getRole(), "text", m.getContent()))
                .collect(Collectors.toList());

        Optional<String> newSummary = geminiClientService.summarizeConversation(conversation.getSummary(), olderTurns);
        if (newSummary.isPresent() && !newSummary.get().trim().isEmpty()) {
            conversation.setSummary(newSummary.get().trim());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            // Mark these messages as summarized
            for (AIChatMessage m : olderUnsummarized) {
                m.setIsSummarized(true);
            }
            chatMessageRepository.saveAll(olderUnsummarized);
            log.info("Successfully compressed older conversation context into AIConversation.summary");
        }
    }
}
