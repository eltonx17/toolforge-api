package com.tooling.toolforge.controller;

import com.tooling.toolforge.service.OpenRouterService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/stream")
@CrossOrigin(origins = "http://localhost:4200")
public class StreamingController {

    private final OpenRouterService openRouterService;

    public StreamingController(OpenRouterService openRouterService) {
        this.openRouterService = openRouterService;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam(defaultValue = "Tell me a short joke about programming.") String message) {
        return openRouterService.streamChatCompletion(message);
    }

    @GetMapping(value = "/chat-model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatWithModel(
            @RequestParam(defaultValue = "Write a haiku about clouds.") String message,
            @RequestParam(defaultValue = "mistralai/mistral-7b-instruct") String model // Example different model
    ) {
        return openRouterService.streamChatCompletionWithModel(message, model);
    }
}