package com.tooling.toolforge.controller;

import com.tooling.toolforge.model.chat.ChatRepository;
import com.tooling.toolforge.model.chat.ChatSession;
import com.tooling.toolforge.model.chat.Message;
// import com.tooling.toolforge.model.user.ProfileResponse; // Not used in this snippet
import com.tooling.toolforge.service.OpenRouterService;
import lombok.extern.slf4j.Slf4j;
// import org.apache.commons.lang3.StringUtils; // Not strictly needed if sessionId.isBlank() is used and Java 11+
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException; // Import for specific exception handling
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stream")
@CrossOrigin(origins = {
        "http://localhost:4200",
        "https://tool-forge.vercel.app",
        "http://192.168.0.109:4200"
})
@Slf4j
public class StreamingController {

    private final OpenRouterService openRouterService;
    private final ChatRepository chatRepository; // Made final
    private final RedisTemplate<String, String> redisTemplate; // Made final

    // Constructor injection for all dependencies
    public StreamingController(OpenRouterService openRouterService,
                               ChatRepository chatRepository,
                               RedisTemplate<String, String> redisTemplate) {
        this.openRouterService = openRouterService;
        this.chatRepository = chatRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/session")
    public ResponseEntity<String> generateSession() {
        return ResponseEntity.ok(UUID.randomUUID().toString());
    }

    @PostMapping(value = "/chat", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> streamChat(
            @RequestHeader(value = "Session-Id", required = false) String sessionId,
            @RequestHeader(value = "Userid", required = false) String userId,
            @RequestBody String newMessage) {

        if (sessionId == null || sessionId.isBlank()) { // sessionId.isBlank() is Java 11+
            sessionId = UUID.randomUUID().toString();
            log.info("No Session-Id provided, generated new one: {}", sessionId);
        }

        String redisKey = sessionId.trim();
        List<String> history = redisTemplate.opsForList().range(redisKey, 0, -1);
        String context = (history != null && !history.isEmpty())
                ? String.join("\n", history) + "\nUser: " + newMessage
                : "User: " + newMessage;

        redisTemplate.opsForList().rightPush(redisKey, "User: " + newMessage);

        // Upsert user message to MongoDB
        try {
            ChatSession chatSession = chatRepository.findById(redisKey)
                    .orElseGet(() -> {
                        log.info("ChatSession with id '{}' not found. Creating new session.", redisKey);
                        ChatSession newSession = new ChatSession();
                        newSession.setId(redisKey);
                        newSession.setUserId(userId);
                        newSession.setMessages(new ArrayList<>()); // Initialize messages list
                        return newSession;
                    });

            chatSession.getMessages().add(new Message("user", newMessage));
            chatSession.setLastUpdated(Instant.now());

            chatRepository.save(chatSession);
            log.info("Successfully saved/updated user message for session id: {}", redisKey);
        } catch (DataAccessException e) {
            log.error("MongoDB Error: Failed to save user message for session id: {}. Reason: {}", redisKey, e.getMessage(), e);
            // Consider if you should return an error response here or if the stream should still proceed
        } catch (Exception e) {
            log.error("Unexpected Error: Failed to save user message for session id: {}. Reason: {}", redisKey, e.getMessage(), e);
        }


        List<String> responseCollector = new ArrayList<>();

        Flux<String> stream = openRouterService.streamChatCompletion(context)
                .doOnNext(responseCollector::add)
                .doOnError(e -> log.error("Error during chat streaming for session {}: {}", redisKey, e.getMessage(), e))
                .doOnCancel(() -> log.info("Chat stream cancelled for session {}", redisKey))
                .doOnComplete(() -> {
                    String fullResponse = String.join("", responseCollector);
                    if (fullResponse.isEmpty() && !responseCollector.isEmpty()) {
                        log.warn("Collected response parts but fullResponse is empty for session {}. This might indicate non-string elements or an issue with String.join.", redisKey);
                    } else if (fullResponse.isEmpty()) {
                        log.info("Stream completed with an empty response for session {}.", redisKey);
                        // Decide if an empty bot response should be saved
                        // For now, we proceed to save it as an empty message if the session exists
                    }

                    redisTemplate.opsForList().rightPush(redisKey, "Bot: " + fullResponse);

                    try {
                        chatRepository.findById(redisKey).ifPresentOrElse(session -> {
                            session.getMessages().add(new Message("bot", fullResponse));
                            session.setLastUpdated(Instant.now());
                            chatRepository.save(session);
                            log.info("Successfully saved bot response for session id: {}", redisKey);
                        }, () -> {
                            log.warn("ChatSession with id: {} not found when trying to save bot response. This might indicate the initial user message save failed or the session was unexpectedly deleted.", redisKey);
                        });
                    } catch (DataAccessException e) {
                        log.error("MongoDB Error: Failed to save bot response for session id: {}. Reason: {}", redisKey, e.getMessage(), e);
                    } catch (Exception e) {
                        log.error("Unexpected Error: Failed to save bot response for session id: {}. Reason: {}", redisKey, e.getMessage(), e);
                    }
                    log.info("Chat stream completed for session {}", redisKey);
                });

        return ResponseEntity.ok()
                .header("Session-Id", sessionId) // Send back the session ID (new or existing)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }
}