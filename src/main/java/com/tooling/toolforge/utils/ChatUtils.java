package com.tooling.toolforge.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
public class ChatUtils {

    /**
     * Formats the response string flux to ensure that each chunk starts with a space.
     * @param message
     * @param originalFlux
     * @return
     */
    public static Flux<String> formatStringFlux(String message, Flux<String> originalFlux) {
        return originalFlux
                .map(chunk -> {
                    // Guard against null/empty chunks
                    if (StringUtils.isEmpty(chunk)) {
                        return "";
                    }

                    // Check if it starts with exactly one space
                    if (chunk.startsWith(" ") && (!chunk.startsWith("  "))) {
                        // It starts with a single space. Prepend another one.
                        log.trace("Doubling leading space for chunk: '{}'", chunk);
                        return " " + chunk;
                    } else {
                        // It doesn't start with a single space. Return as is.
                        log.trace("Returning chunk as-is: '{}'", chunk);
                        return chunk;
                    }
                })
                .doOnError(error -> log.error("Error during AI stream processing", error))
                .doOnComplete(() -> log.info("AI stream completed"));
    }
}
