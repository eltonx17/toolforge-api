package com.tooling.toolforge.websocket.handler;


import com.tooling.toolforge.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiWebSocketHandler extends TextWebSocketHandler {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExecutorService geminiExecutor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        sendWebSocketMessage(session, Map.of("type", "status", "payload", "Connection established. Send prompt as JSON: {\"prompt\": \"Your question...\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received message from {}: {}", session.getId(), payload);

        try {
            Map<String, String> request = objectMapper.readValue(payload, Map.class);
            String prompt = request.get("prompt");

            if (prompt == null || prompt.isBlank()) {
                sendWebSocketMessage(session, Map.of("type", "error", "payload", "Prompt cannot be empty. Send JSON: {\"prompt\": \"Your question...\"}"));
                return;
            }

            // Submit the task to the executor to keep WS threads free
            geminiExecutor.submit(() -> processPrompt(session, prompt));

        } catch (Exception e) {
            log.error("Failed to parse incoming message or queue prompt processing for session {}: {}", session.getId(), e.getMessage());
            try {
                if(session.isOpen()) {
                    sendWebSocketMessage(session, Map.of("type", "error", "payload", "Invalid message format. Send JSON: {\"prompt\": \"Your question...\"}. Details: " + e.getMessage()));
                }
            } catch(Exception sendEx) {
                log.error("Failed to send error message back to session {}: {}", session.getId(), sendEx.getMessage());
            }
        }
    }

    // This method runs in the geminiExecutor thread pool
    private void processPrompt(WebSocketSession session, String prompt) {
        try {
            geminiService.generateContentStream(
                    prompt,
                    // Token Consumer
                    token -> sendWebSocketMessage(session, Map.of("type", "token", "payload", token)),

                    () -> {
                        log.info("Stream finished successfully for session {}", session.getId());
                        sendWebSocketMessage(session, Map.of("type", "end", "payload", "Stream finished."));
                    },
                    error -> {
                        log.error("Error callback invoked for session {}: {}", session.getId(), error.getMessage());
                        sendWebSocketMessage(session, Map.of("type", "error", "payload", "Error processing prompt: " + error.getMessage()));
                    }
            );
        } catch (Exception e) {
            log.error("Unexpected synchronous error initiating prompt processing for session {}: {}", session.getId(), e.getMessage(), e);
            sendWebSocketMessage(session, Map.of("type", "error", "payload", "Unexpected server error initiating stream: " + e.getMessage()));
        }
    }


    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        // TODO - closing the session or cleaning up related resources
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {} - Status: {}", session.getId(), status);
        // TODO - Clean up any session-specific resources
    }

    // Helper method
    private synchronized void sendWebSocketMessage(WebSocketSession session, Map<String, String> messageData) {
        if (session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(messageData);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.PROTOCOL_ERROR.withReason("Failed to send message: " + e.getMessage()));
                    }
                } catch (IOException closeEx) {
                    log.error("Failed to close session {} after send error: {}", session.getId(), closeEx.getMessage());
                }
            }
        } else {
            log.warn("Attempted to send message to closed session: {}", session.getId());
        }
    }

    // Shutdown executor on application exit
    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down Gemini processing executor service...");
        geminiExecutor.shutdown();
        try {
            if (!geminiExecutor.awaitTermination(5, TimeUnit.SECONDS)) { // Use TimeUnit.SECONDS
                log.warn("Executor did not terminate in time, forcing shutdown...");
                geminiExecutor.shutdownNow();
            }
            log.info("Gemini executor service shut down gracefully.");
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for executor shutdown, forcing now.");
            geminiExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}