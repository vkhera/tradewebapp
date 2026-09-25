package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OllamaSettingsRequest;
import com.example.stockbrokerage.dto.OllamaSettingsResponse;
import com.example.stockbrokerage.entity.ApplicationSetting;
import com.example.stockbrokerage.repository.ApplicationSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
public class OllamaSettingsService {

    private static final String MODEL_KEY = "news-analysis.ollama.model";
    private static final String ENDPOINT_KEY = "news-analysis.ollama.url";

    private final ApplicationSettingRepository settingRepository;
    private final String defaultModel;
    private final String defaultEndpoint;

    public OllamaSettingsService(
            ApplicationSettingRepository settingRepository,
            @Value("${app.news-analysis.ollama.model:gemma3:1b}") String defaultModel,
            @Value("${app.news-analysis.ollama.url:http://localhost:11434/api/chat}") String defaultEndpoint) {
        this.settingRepository = settingRepository;
        this.defaultModel = defaultModel;
        this.defaultEndpoint = defaultEndpoint;
    }

    @Transactional(readOnly = true)
    public OllamaSettingsResponse getSettings() {
        return new OllamaSettingsResponse(
                getValue(MODEL_KEY, defaultModel),
                getValue(ENDPOINT_KEY, defaultEndpoint));
    }

    @Transactional
    public OllamaSettingsResponse updateSettings(OllamaSettingsRequest request) {
        String model = requireModel(request == null ? null : request.model());
        String endpoint = requireEndpoint(request == null ? null : request.endpoint());
        saveValue(MODEL_KEY, model);
        saveValue(ENDPOINT_KEY, endpoint);
        return new OllamaSettingsResponse(model, endpoint);
    }

    private String getValue(String key, String fallback) {
        return settingRepository.findBySettingKey(key)
                .map(ApplicationSetting::getSettingValue)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private void saveValue(String key, String value) {
        ApplicationSetting setting = settingRepository.findBySettingKey(key)
                .orElseGet(() -> ApplicationSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        settingRepository.save(setting);
    }

    private String requireModel(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new IllegalArgumentException("LLM model is required and must be 80 characters or fewer");
        }
        return normalized;
    }

    private String requireEndpoint(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 500) {
            throw new IllegalArgumentException("Ollama endpoint is required and must be 500 characters or fewer");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("Ollama endpoint must be a valid HTTP or HTTPS URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Ollama endpoint must be a valid HTTP or HTTPS URL", ex);
        }
        return normalized;
    }
}
