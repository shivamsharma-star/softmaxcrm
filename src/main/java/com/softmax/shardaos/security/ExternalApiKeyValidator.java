package com.softmax.shardaos.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExternalApiKeyValidator {

    @Value("${app.external.consultant.apikey}")
    private String consultantApiKey;

    @Value("${app.external.client.apikey}")
    private String clientApiKey;

    @Value("${app.external.project.apikey}")
    private String projectApiKey;

    public boolean isValidConsultantKey(String key) {
        return key != null && !key.isBlank() && consultantApiKey != null
                && consultantApiKey.equals(key.trim());
    }

    public boolean isValidClientKey(String key) {
        return key != null && !key.isBlank() && clientApiKey != null
                && clientApiKey.equals(key.trim());
    }

    public boolean isValidProjectKey(String key) {
        return key != null && !key.isBlank() && projectApiKey != null
                && projectApiKey.equals(key.trim());
    }

    public String extractApiKey(HttpServletRequest request) {
        String key = request.getHeader("apikey");
        if (key == null || key.isBlank()) {
            key = request.getHeader("api-key");
        }
        return key;
    }
}