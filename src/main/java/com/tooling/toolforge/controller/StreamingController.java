package com.tooling.toolforge.controller;

import com.tooling.toolforge.dto.chat.ChatRepository;
import com.tooling.toolforge.dto.chat.ChatSession;
import com.tooling.toolforge.dto.chat.Message;
// import com.tooling.toolforge.model.user.ProfileResponse; // Not used in this snippet
import com.tooling.toolforge.dto.history.PaginatedHistoryResponse;
import com.tooling.toolforge.dto.history.SessionHistoryItem;
import com.tooling.toolforge.service.OpenRouterService;
import com.tooling.toolforge.utils.ChatUtils;
import lombok.extern.slf4j.Slf4j;
// import org.apache.commons.lang3.StringUtils; // Not strictly needed if sessionId.isBlank() is used and Java 11+
import org.springframework.dao.DataAccessException; // Import for specific exception handling
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final int HISTORY_PAGE_SIZE = 20; // Define page size as a constant


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

    @GetMapping("/history")
    public ResponseEntity<PaginatedHistoryResponse> getHistory(
            @RequestHeader(value = "Userid", required = false) String userId,
            @RequestParam(value = "page", defaultValue = "1") int page) {
        List<ChatSession> sessions;

        if (page < 1) {
            page = 1; // Ensure page is at least 1
        }

        try {
            if (userId != null && !userId.isBlank()) {
                log.info("Fetching chat history for userId: {}, page: {}", userId, page);
                sessions = chatRepository.findByUserIdOrderByLastUpdatedDesc(userId);
            } else {
                log.info("Fetching all chat history, ordered by lastUpdated descending, page: {}", page);
                sessions = chatRepository.findAll(Sort.by(Sort.Direction.DESC, "lastUpdated"));
            }

            if (sessions == null || sessions.isEmpty()) {
                PaginatedHistoryResponse emptyResponse = new PaginatedHistoryResponse(Collections.emptyMap(), page, 0, 0);
                return ResponseEntity.ok(emptyResponse);
            }

            DateTimeFormatter monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
            ZoneId utcZone = ZoneId.of("UTC");

            Map<String, List<SessionHistoryItem>> historyGroupedByDate = sessions.stream()
                    .collect(Collectors.groupingBy(
                            session -> {
                                LocalDate localDate = session.getLastUpdated().atZone(utcZone).toLocalDate();
                                String dayWithSuffix = ChatUtils.getDayWithOrdinalSuffix(localDate.getDayOfMonth());
                                String monthYear = monthYearFormatter.format(localDate);
                                return dayWithSuffix + " " + monthYear;
                            },
                            LinkedHashMap::new,
                            Collectors.mapping(session -> {
                                String title = "Chat";
                                if (session.getMessages() != null && !session.getMessages().isEmpty()) {
                                    Message firstMessage = session.getMessages().get(0);
                                    if (firstMessage != null && firstMessage.getContent() != null && !firstMessage.getContent().isBlank()) {
                                        title = firstMessage.getContent();
                                        int maxLength = 50;
                                        if (title.length() > maxLength) {
                                            title = title.substring(0, maxLength - 3) + "...";
                                        }
                                    }
                                }
                                return new SessionHistoryItem(session.getId(), session.getLastUpdated(), title);
                            }, Collectors.toList())
                    ));

            long totalUniqueDates = historyGroupedByDate.size();
            int totalPages = 0;
            if (totalUniqueDates > 0) {
                totalPages = (int) Math.ceil((double) totalUniqueDates / HISTORY_PAGE_SIZE);
            }

            long skipCount = (long) (page - 1) * HISTORY_PAGE_SIZE;

            Map<String, List<SessionHistoryItem>> paginatedData = historyGroupedByDate.entrySet().stream()
                    .skip(skipCount)
                    .limit(HISTORY_PAGE_SIZE)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (v1, v2) -> v1,
                            LinkedHashMap::new
                    ));

            PaginatedHistoryResponse response = new PaginatedHistoryResponse(paginatedData, page, totalPages, totalUniqueDates);
            return ResponseEntity.ok(response);

        } catch (DataAccessException e) {
            log.error("MongoDB Error: Failed to fetch chat history. UserId: '{}', Page: {}. Reason: {}", userId, page, e.getMessage(), e);
            PaginatedHistoryResponse errorResponse = new PaginatedHistoryResponse(Collections.emptyMap(), page, 0, 0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        } catch (Exception e) {
            log.error("Unexpected Error: Failed to fetch chat history. UserId: '{}', Page: {}. Reason: {}", userId, page, e.getMessage(), e);
            PaginatedHistoryResponse errorResponse = new PaginatedHistoryResponse(Collections.emptyMap(), page, 0, 0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
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
                ? String.join("\n", history) + "\n" + newMessage
                : newMessage;

        redisTemplate.opsForList().rightPush(redisKey, newMessage);

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

                    redisTemplate.opsForList().rightPush(redisKey, fullResponse);

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