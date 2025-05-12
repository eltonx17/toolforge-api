package com.tooling.toolforge.dto.history;

import java.time.Instant;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class SessionHistoryItem {
    private String sessionId;
    private Instant lastUpdated;
    private String title; // A brief title, e.g., the first message content
}