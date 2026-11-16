package com.softmax.shardaos.AIservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaApiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String OLLAMA_URL = "http://localhost:11434/api/generate";

    public String generateResponse(String prompt) {

        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "llama3");
            body.put("prompt", prompt);
            body.put("stream", false);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            OLLAMA_URL,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            if (response.getBody() != null) {
                return extractResponse(response.getBody());
            }

            return "No response from Ollama";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR : " + e.getMessage();
        }
    }

    private String extractResponse(String responseBody) {

        try {

            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode responseNode = root.path("response");

            if (!responseNode.isMissingNode()) {
                return responseNode.asText();
            }

            return responseBody;

        } catch (Exception e) {
            e.printStackTrace();
            return responseBody;
        }
    }
}