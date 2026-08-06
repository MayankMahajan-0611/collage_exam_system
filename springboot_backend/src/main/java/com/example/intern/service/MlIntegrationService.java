package com.example.intern.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException; // Add this import
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.HashMap;
import java.util.Map;

@Service
public class MlIntegrationService {

    private final String FLASK_API_URL = "http://python-service:5000/generate_mcq";

    public String generateQuestionsFromText(String text, int num) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(300000);

        RestTemplate restTemplate = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        requestBody.put("num_questions", num);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(FLASK_API_URL, request, String.class);
        } catch (HttpClientErrorException e) {
            // THIS IS THE FIX: If Python throws a 400, catch it and return Python's exact error message to the frontend!
            throw new RuntimeException(e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Python ML Service: " + e.getMessage());
        }
    }
}