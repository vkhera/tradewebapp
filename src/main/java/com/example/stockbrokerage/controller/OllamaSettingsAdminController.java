package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.OllamaSettingsRequest;
import com.example.stockbrokerage.dto.OllamaSettingsResponse;
import com.example.stockbrokerage.service.OllamaSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/llm-settings")
@RequiredArgsConstructor
@Tag(name = "Admin - LLM Settings", description = "Admin: configure the Ollama model and endpoint used for news analysis")
public class OllamaSettingsAdminController {

    private final OllamaSettingsService settingsService;

    @GetMapping
    @Operation(summary = "Get Ollama news-analysis settings")
    public ResponseEntity<OllamaSettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    @Operation(summary = "Update Ollama news-analysis settings")
    public ResponseEntity<OllamaSettingsResponse> updateSettings(@RequestBody OllamaSettingsRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }
}
