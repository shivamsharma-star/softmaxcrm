package com.softmax.shardaos.AIservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiApiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGemini(String prompt) {
        System.out.println("\n🌐 [GeminiApiService] callGemini START");
        System.out.println("📡 API URL: " + apiUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(
                    Map.of("parts", List.of(
                            Map.of("text", prompt)
                    ))
            ));

            String url = apiUrl + "?key=" + apiKey;
            System.out.println("📡 Full URL: " + url);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            System.out.println("📥 Response Status Code: " + response.getStatusCode());
            System.out.println("📥 Response Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String extractedText = extractTextFromResponse(response.getBody());
                System.out.println("✅ Extracted Text: " + extractedText);
                return extractedText;
            }

            return "AI response failed - Status: " + response.getStatusCode();
        } catch (Exception e) {
            System.out.println("❌ Exception: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");

                if (parts.isArray() && parts.size() > 0) {
                    // Get the text field
                    JsonNode textNode = parts.get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }

                    // If text not found, return the whole parts as string
                    return parts.toString();
                }
            }

            // If structure is different, return full response
            System.out.println("⚠️ Could not parse response structure, returning full response");
            return responseBody;
        } catch (Exception e) {
            System.out.println("❌ Parse Error: " + e.getMessage());
            return responseBody;
        }
    }
}