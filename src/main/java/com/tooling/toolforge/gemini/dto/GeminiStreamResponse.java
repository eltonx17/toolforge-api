package com.tooling.toolforge.gemini.dto;

import lombok.Data;
import java.util.List;

@Data
public class GeminiStreamResponse {
    private List<Candidate> candidates;
}