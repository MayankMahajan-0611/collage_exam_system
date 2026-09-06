package com.example.intern.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
public class MlIntegrationService {

    // 🚨 Ensure this points correctly to your running Flask server
    private final String FLASK_URL = "http://localhost:5000/generate_mcq";
    private final RestTemplate restTemplate = new RestTemplate();

    public String generateQuestionsFromText(String text, int numQuestions) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("num_questions", numQuestions);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(FLASK_URL, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            // This prints the exact reason why the background thread failed
            System.err.println("❌ Flask ML Pipeline Error: " + e.getMessage());
            throw new RuntimeException("Failed to communicate with Python AI microservice: " + e.getMessage());
        }
    }
}