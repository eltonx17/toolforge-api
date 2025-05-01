package com.tooling.toolforge.controller;

import com.tooling.toolforge.model.user.ProfileRequest;
import com.tooling.toolforge.model.user.ProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = {
        "http://localhost:4200",
        "https://tool-forge.vercel.app",
        "http://192.168.0.109:4200"
})
@Slf4j
public class ProfileController {

    @PostMapping("/user")
    public ResponseEntity<ProfileResponse> handleProfile(@RequestBody ProfileRequest profileRequest) {

        if (profileRequest.getEmail() == null || profileRequest.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(new ProfileResponse("Username, email, and password are required."));
        }

        try {
            log.debug("Attempting to store user: {}", profileRequest.getEmail());
            log.debug("Email: {}", profileRequest.getEmail());
            Long createdUserId = 123L;

            ProfileResponse response = new ProfileResponse("User stored successfully!", createdUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("User transaction failed: {}", e.getMessage());
            ProfileResponse errorResponse = new ProfileResponse("Profile processing failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/user-details/{email}")
    public ResponseEntity<ProfileResponse> getUserByEmail(@PathVariable String email) {
        if (!StringUtils.isEmpty(email)) {
            ProfileResponse userDto = new ProfileResponse(email, 123L);
            return ResponseEntity.ok(userDto);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
