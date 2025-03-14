package com.tooling.toolforge.controller;

import com.tooling.toolforge.service.HashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hash")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class HashController {
    private final HashService hashService;

    public HashController(HashService hashService) {
        this.hashService = hashService;
    }

    @PostMapping("/sha256")
    public ResponseEntity<String> generateSHA256(@RequestBody String input) {
        log.info("Hashing");
        return ResponseEntity.ok(hashService.generateSHA256(input));
    }
}

