package com.tooling.toolforge.dto.history;

import com.tooling.toolforge.dto.chat.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedSessionMessagesResponse {
    private List<Message> messages;
    private int currentPage;
    private int totalPages;
    private long totalMessagesInSession;
}