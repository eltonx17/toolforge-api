package com.tooling.toolforge.controller;

import com.tooling.toolforge.service.OpenRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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

    public StreamingController(OpenRouterService openRouterService) {
        this.openRouterService = openRouterService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody String message) {
        log.info("Received chat request (POST - text/plain) with message length: {}", message.length());
        // The core logic remains the same
        return openRouterService.streamChatCompletion(message)
                .doOnError(e -> log.error("Error during chat streaming", e))
                .doOnCancel(() -> log.info("Chat stream cancelled"))
                .doOnComplete(() -> log.info("Chat stream completed"));
    }


    @GetMapping(value = "/chat-model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatWithModel(
            @RequestParam(defaultValue = "Write a haiku about clouds.") String message,
            @RequestParam(defaultValue = "mistralai/mistral-7b-instruct") String model // Example different model
    ) {
        return openRouterService.streamChatCompletionWithModel(message, model);
    }
}