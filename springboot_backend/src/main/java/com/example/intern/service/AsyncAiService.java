package com.example.intern.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
public class AsyncAiService {

    @Autowired
    private MlIntegrationService mlIntegrationService;

    // Thread-safe memory store for background job statuses
    private final Map<String, JobStatus> jobStore = new ConcurrentHashMap<>();

    public static class JobStatus {
        private String status; // "PENDING", "COMPLETED", "FAILED"
        private String result; // The raw AI JSON string or error message

        public JobStatus(String status, String result) {
            this.status = status;
            this.result = result;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
    }

    // 1. Kick off the background task and return an immediate Job ID
    public String startBackgroundGeneration(String text, int num) {
        String jobId = UUID.randomUUID().toString();
        jobStore.put(jobId, new JobStatus("PENDING", null));

        // Trigger the asynchronous background thread
        executeAsyncAiCall(jobId, text, num);

        return jobId;
    }

    // 2. Runs asynchronously on a separate thread pool worker
    @Async
    public void executeAsyncAiCall(String jobId, String text, int num) {
        try {
            String aiResponse = mlIntegrationService.generateQuestionsFromText(text, num);

            // Clean markdown syntax wrapping if sent by LLM
            if (aiResponse != null) {
                aiResponse = aiResponse.trim();
                if (aiResponse.startsWith("```json")) {
                    aiResponse = aiResponse.substring(7);
                }
                if (aiResponse.endsWith("```")) {
                    aiResponse = aiResponse.substring(0, aiResponse.length() - 3);
                }
                aiResponse = aiResponse.trim();
            }

            jobStore.put(jobId, new JobStatus("COMPLETED", aiResponse));
        } catch (Exception e) {
            e.printStackTrace();
            jobStore.put(jobId, new JobStatus("FAILED", e.getMessage()));
        }
    }

    // 3. Allows the frontend to check progress
    public JobStatus getJobStatus(String jobId) {
        return jobStore.getOrDefault(jobId, new JobStatus("NOT_FOUND", "Job ID does not exist."));
    }
}