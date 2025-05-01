package com.tooling.toolforge.model.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileResponse {
    private String message;
    private Long userId; // Optional: return the ID of the created user

    public ProfileResponse(String message, Long userId) {
        this.message = message;
        this.userId = userId;
    }

    public ProfileResponse(String message) {
        this.message = message;
        this.userId = null; // Or handle appropriately
    }
}