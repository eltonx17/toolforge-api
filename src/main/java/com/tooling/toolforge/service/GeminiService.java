package com.tooling.toolforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tooling.toolforge.gemini.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;
    @Value("${gemini.model.name}")
    private String modelName;
    @Value("${gemini.api.base-url}")
    private String baseUrl;
    @Value("${gemini.api.stream-path}")
    private String streamPath;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @PostConstruct
    public void init() {
        log.info("Initializing WebClient for Gemini API: {}", baseUrl);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_GOOGLE_AI_STUDIO_API_KEY")) {
            log.error("Gemini API Key is not configured properly in application.properties!");
            throw new IllegalStateException("gemini.api.key is not configured.");
        }
        log.info("Using Gemini model: {}", modelName);
    }

    public void generateContentStream(String prompt, Consumer<String> tokenConsumer, Runnable onComplete, Consumer<Throwable> onError) {
        log.info("Sending prompt to Gemini (Generative Language API): '{}'", prompt.substring(0, Math.min(prompt.length(), 50)) + "...");

        Part part = new Part(prompt);
        Content content = new Content("user", Collections.singletonList(part));
        GeminiRequest requestPayload = new GeminiRequest(Collections.singletonList(content));
        String finalUrl = streamPath.replace("{model}", modelName);
        final StringBuilder buffer = new StringBuilder();

        webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(finalUrl)
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToFlux(String.class)
                .concatMap(chunk -> { // Process chunks sequentially
                    buffer.append(chunk);
                    log.trace("Buffer content after append: >>>{}<<<", buffer.toString());
                    List<String> completeJsonObjects = extractCompleteJsonObjects(buffer);

                    return Flux.fromIterable(completeJsonObjects)
                            .map(this::extractTextDirectly);
                })
                .filter(Objects::nonNull)
                .filter(text -> !text.isEmpty())
                .doOnNext(textToken -> {
                    log.trace("Sending text token to consumer: {}", textToken);
                    tokenConsumer.accept(textToken);
                })
                .doOnError(error -> {
                    log.error("Error during Gemini stream processing: {}", error.getMessage(), mapWebClientException(error));
                    onError.accept(mapWebClientException(error));
                })
                .doOnComplete(() -> {
                    log.debug("Stream complete signal received. Processing remaining buffer: >>>{}<<<", buffer.toString());
                    List<String> remainingJsonObjects = extractCompleteJsonObjects(buffer);
                    remainingJsonObjects.forEach(json -> {
                        String remainingText = extractTextDirectly(json);
                        if (remainingText != null && !remainingText.isEmpty()) {
                            log.trace("Sending remaining text token from buffer: {}", remainingText);
                            tokenConsumer.accept(remainingText);
                        }
                    });
                    if (buffer.length() > 0) {
                        log.warn("Stream completed with non-empty, non-parsable buffer content left: >>>{}<<<", buffer.toString());
                    }
                    log.info("Gemini stream processing finished.");
                    onComplete.run();
                })
                .subscribe();
    }

    /**
     * Extracts one or more complete JSON objects or arrays from the buffer.
     */
    private List<String> extractCompleteJsonObjects(StringBuilder buffer) {
        List<String> completeObjects = new ArrayList<>();
        int scanPos = 0, validStart = 0;

        while (scanPos < buffer.length()) {
            while (scanPos < buffer.length() && (Character.isWhitespace(buffer.charAt(scanPos)) || buffer.charAt(scanPos) == ',')) scanPos++;
            if (scanPos >= buffer.length() || (buffer.charAt(scanPos) != '{' && buffer.charAt(scanPos) != '[')) break;

            Deque<Character> stack = new ArrayDeque<>();
            boolean inString = false;
            int endPos = -1;

            for (int i = scanPos; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c == '"') inString = !inString;
                if (!inString) {
                    if (c == '{' || c == '[') stack.push(c);
                    else if (c == '}' || c == ']') {
                        if (stack.isEmpty() || (c == '}' && stack.peek() != '{') || (c == ']' && stack.peek() != '[')) break;
                        stack.pop();
                    }
                }
                if (!inString && stack.isEmpty()) { endPos = i; break; }
            }

            if (endPos != -1) {
                completeObjects.add(buffer.substring(scanPos, endPos + 1));
                validStart = scanPos = endPos + 1;
            } else break;
        }
        if (validStart > 0) buffer.delete(0, validStart);
        return completeObjects;
    }


    /**
     * Parses a complete JSON string into a JsonNode and extracts text content directly.
     * Avoids mapping to specific DTOs.
     *
     * @param completeJsonString A string guaranteed to be a complete JSON object or array.
     * @return Extracted text as a single string, or null if parsing fails or no text found.
     */
    private String extractTextDirectly(String completeJsonString) {
        if (completeJsonString == null || completeJsonString.trim().isEmpty()) {
            return null;
        }
        String trimmedJson = completeJsonString.trim();
        log.trace("Attempting to extract text directly from JSON string: {}", trimmedJson.substring(0, Math.min(trimmedJson.length(), 100)));

        try {
            JsonNode rootNode = objectMapper.readTree(trimmedJson);
            StringBuilder extractedText = new StringBuilder();

            // Handle if the root is an array of response objects
            if (rootNode.isArray()) {
                for (JsonNode responseNode : rootNode) {
                    appendCandidateText(responseNode, extractedText);
                }
            }
            // Handle if the root is a single response object
            else if (rootNode.isObject()) {
                appendCandidateText(rootNode, extractedText);
            }
            else {
                log.warn("JSON root is neither an object nor an array: {}", trimmedJson.substring(0, Math.min(trimmedJson.length(), 100)));
                return null;
            }

            return extractedText.toString();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON string into JsonNode: '{}'. Error: {}", trimmedJson.substring(0, Math.min(trimmedJson.length(), 100)), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error extracting text directly from JSON string: '{}'. Error: {}", trimmedJson.substring(0, Math.min(trimmedJson.length(), 100)), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Helper to navigate a JsonNode representing a potential Gemini response object
     * and append text from its candidates/parts to a StringBuilder.
     *
     * @param responseNode The JsonNode potentially containing candidates.
     * @param builder The StringBuilder to append text to.
     */
    private void appendCandidateText(JsonNode responseNode, StringBuilder builder) {
        // Use .path() for safe navigation - returns MissingNode if path doesn't exist
        JsonNode candidatesNode = responseNode.path("candidates");
        if (candidatesNode.isArray()) {
            for (JsonNode candidateNode : candidatesNode) {
                JsonNode contentNode = candidateNode.path("content");
                JsonNode partsNode = contentNode.path("parts");
                if (partsNode.isArray()) {
                    // Iterate through all parts and append their text
                    StreamSupport.stream(partsNode.spliterator(), false) // Convert parts array to Stream
                            .map(partNode -> partNode.path("text")) // Get the text node for each part
                            .filter(JsonNode::isTextual) // Ensure it's actually text
                            .map(JsonNode::asText) // Extract the text value
                            .forEach(builder::append); // Append to the StringBuilder
                }
            }
        } else if (!candidatesNode.isMissingNode()) {
            log.warn("Expected 'candidates' to be an array, but got: {}", candidatesNode.getNodeType());
        }
    }


    /**
     * Helper to extract useful info from WebClientResponseException
     * (This method remains the same)
     */
    private Throwable mapWebClientException(Throwable error) {
        if (error instanceof WebClientResponseException clientEx) {
            String responseBody = clientEx.getResponseBodyAsString();
            log.error("Gemini API Error - Status: {}, Body: {}", clientEx.getStatusCode(), responseBody);
            return new RuntimeException(String.format("Gemini API error %s: %s",
                    clientEx.getStatusCode(), responseBody), error);
        }
        return error;
    }
}