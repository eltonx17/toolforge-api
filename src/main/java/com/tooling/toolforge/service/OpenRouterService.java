package com.tooling.toolforge.service;

import com.tooling.toolforge.utils.ChatUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class OpenRouterService {

    private final ChatClient chatClient;

    public OpenRouterService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Sends a prompt to the configured OpenRouter model and returns a stream
     * of response content chunks.
     *
     * @param message The user's message/prompt.
     * @return A Flux<String> emitting response content chunks as they arrive.
     */
    public Flux<String> streamChatCompletion(String message) {
        Prompt prompt = new Prompt(message);

        Flux<String> originalFlux = chatClient.prompt(prompt)
                .stream()
                .content();

        return ChatUtils.formatStringFlux(message, originalFlux);
    }


    /**
     * Sends a prompt to a SPECIFIC OpenRouter model (overriding the default)
     * and returns a stream of response content chunks.
     *
     * @param message The user's message/prompt.
     * @param model   The specific OpenRouter model identifier to use.
     * @return A Flux<String> emitting response content chunks as they arrive.
     */
    public Flux<String> streamChatCompletionWithModel(String message, String model) {
        return chatClient.prompt()
                // Use .options() to set request-specific configurations
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .user(message) // Define the user message after setting options
                .stream()      // Initiate the streaming call
                .content();
    }
}