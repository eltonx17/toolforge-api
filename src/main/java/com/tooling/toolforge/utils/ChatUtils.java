package com.tooling.toolforge.utils;

import com.tooling.toolforge.dto.chat.ChatRepository;
import com.tooling.toolforge.dto.chat.ChatSession;
import com.tooling.toolforge.dto.chat.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatUtils {

    private final ChatRepository chatRepository;

    public ChatUtils(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

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

    public static String getDayWithOrdinalSuffix(int day) {
        if (day >= 1 && day <= 31) {
            if (day >= 11 && day <= 13) {
                return day + "th";
            }
            switch (day % 10) {
                case 1:  return day + "st";
                case 2:  return day + "nd";
                case 3:  return day + "rd";
                default: return day + "th";
            }
        }
        throw new IllegalArgumentException("Invalid day of month: " + day);
    }

    public void checkRedisAndLoadIfAbsent(RedisTemplate<String, String> redisTemplate, String redisKey) {
        // Check if session history needs to be populated from MongoDB to Redis
        Boolean keyExistsInRedis = redisTemplate.hasKey(redisKey);
        if (keyExistsInRedis == null || !keyExistsInRedis) {
            log.info("SessionId {} not found in Redis. Attempting to populate from MongoDB.", redisKey);
            try {
                Optional<ChatSession> sessionOptional = chatRepository.findById(redisKey);
                if (sessionOptional.isPresent()) {
                    ChatSession mongoSession = sessionOptional.get();
                    List<Message> messages = mongoSession.getMessages();
                    if (messages != null && !messages.isEmpty()) {
                        List<String> messageContents = messages.stream()
                                .map(Message::getContent)
                                .filter(Objects::nonNull) // Ensure no null content is pushed
                                .collect(Collectors.toList());
                        if (!messageContents.isEmpty()) {
                            redisTemplate.opsForList().rightPushAll(redisKey, messageContents);
                            log.info("Successfully populated Redis with {} messages from MongoDB for session {}.", messageContents.size(), redisKey);
                        } else {
                            log.info("Session {} found in MongoDB, but no message content to populate Redis.", redisKey);
                        }
                    } else {
                        log.info("Session {} found in MongoDB but has no messages.", redisKey);
                    }
                } else {
                    log.info("SessionId {} not found in MongoDB either. Proceeding with an empty history for Redis.", redisKey);
                }
            } catch (DataAccessException e) {
                log.error("MongoDB Error: Failed to fetch session {} from MongoDB for Redis population. Reason: {}", redisKey, e.getMessage(), e);
                // Continue, Redis will be treated as empty for this session if population fails
            } catch (Exception e) {
                log.error("Unexpected Error: Failed during Redis population from MongoDB for session {}. Reason: {}", redisKey, e.getMessage(), e);
                // Continue
            }
        }
    }
}
