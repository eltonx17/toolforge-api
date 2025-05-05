package com.tooling.toolforge.controller;

import com.tooling.toolforge.model.user.ProfileResponse;
import com.tooling.toolforge.service.OpenRouterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
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

    @Autowired
    private final RedisTemplate<String, String> redisTemplate;

    public StreamingController(OpenRouterService openRouterService, RedisTemplate<String, String> redisTemplate) {
        this.openRouterService = openRouterService;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/session")
    public ResponseEntity<String> generateSession() {
        return ResponseEntity.ok(UUID.randomUUID().toString());
    }

    @PostMapping(value = "/chat", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> streamChat(
            @RequestHeader(value = "Session-Id", required = false) String sessionId,
            @RequestBody String newMessage) {

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        String redisKey = sessionId.trim();
        List<String> history = redisTemplate.opsForList().range(redisKey, 0, -1);
        String context = history != null && !history.isEmpty()
                ? String.join("\n", history) + "\nUser: " + newMessage
                : "User: " + newMessage;

        redisTemplate.opsForList().rightPush(redisKey, "User: " + newMessage);

        List<String> responseCollector = new ArrayList<>();

        Flux<String> stream = openRouterService.streamChatCompletion(context)
                .doOnNext(responseCollector::add)
                .doOnError(e -> log.error("Error during chat streaming", e))
                .doOnCancel(() -> log.info("Chat stream cancelled"))
                .doOnComplete(() -> {
                    String fullResponse = String.join("", responseCollector);
                    redisTemplate.opsForList().rightPush(redisKey, "Bot: " + fullResponse);
                    log.info("Chat stream completed");
                });

        return ResponseEntity.ok()
                .header("Session-Id", sessionId)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }
}