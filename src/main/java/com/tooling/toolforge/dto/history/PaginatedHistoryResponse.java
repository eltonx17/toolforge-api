package com.tooling.toolforge.dto.history;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PaginatedHistoryResponse {
    private Map<String, List<SessionHistoryItem>> data;
    private int currentPage;
    private int totalPages;
    private long totalItems; // Total unique dates

    // Constructors
    public PaginatedHistoryResponse() {
    }

    public PaginatedHistoryResponse(Map<String, List<SessionHistoryItem>> data, int currentPage, int totalPages, long totalItems) {
        this.data = data;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
    }


}