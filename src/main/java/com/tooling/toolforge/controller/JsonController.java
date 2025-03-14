package com.tooling.toolforge.controller;

import com.tooling.toolforge.model.JsonRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/json")
@CrossOrigin(origins = "http://localhost:4200") // Allow Angular frontend
public class JsonController {
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @PostMapping("/format")
    public ResponseEntity<String> formatJson(@RequestBody JsonRequest request) {
        try {
            Object jsonObject = objectMapper.readValue(request.getJson(), Object.class);
            String formattedJson = objectMapper.writeValueAsString(jsonObject);
            return ResponseEntity.ok(formattedJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid JSON");
        }
    }
}
