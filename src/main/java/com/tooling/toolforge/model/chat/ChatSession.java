package com.tooling.toolforge.model.chat;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat")
@Data
public class ChatSession {
    @Id
    private String id;
    private List<Message> messages = new ArrayList<>();
    private Instant lastUpdated;

    // Getters and Setters
}

