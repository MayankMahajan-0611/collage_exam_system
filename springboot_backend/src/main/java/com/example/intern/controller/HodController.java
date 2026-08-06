package com.example.intern.controller;

import com.example.intern.model.Teacher;
import com.example.intern.model.Student;
import com.example.intern.model.Exam;
import com.example.intern.model.Question;
import com.example.intern.repository.TeacherRepository;
import com.example.intern.repository.StudentRepository;
import com.example.intern.repository.ExamRepository;
import com.example.intern.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/api/hod")
@CrossOrigin(origins = "*")
public class HodController {

    @Autowired private TeacherRepository teacherRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private PromotionService promotionService;

    // 🚨 NEW AI GENERATION DEPENDENCIES
    @Autowired private FileStorageService fileStorageService;
    @Autowired private PdfProcessingService pdfProcessingService;
    @Autowired private MlIntegrationService mlIntegrationService;

    // ==========================================
    // 1. AI QUESTION GENERATION PIPELINE
    // ==========================================
    @PostMapping("/upload-notes")
    public ResponseEntity<String> uploadNotesAndGenerateExam(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "num", defaultValue = "5") int num) {
        try {
            String savedFilePath = fileStorageService.saveTeacherNotes(file);
            String text = pdfProcessingService.extractTextFromPdf(savedFilePath);

            if (text == null || text.trim().length() < 100) {
                return ResponseEntity.badRequest().body("{\"error\": \"The uploaded PDF does not contain enough readable digital text.\"}");
            }

            String aiResponse = mlIntegrationService.generateQuestionsFromText(text, num);

            // Clean up Markdown formatting from AI response string block
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

            return ResponseEntity.ok().header("Content-Type", "application/json").body(aiResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // ==========================================
    // 2. DELEGATED TERM ADVANCEMENT ENGINE
    // ==========================================
    @PostMapping("/increment-semester")
    public ResponseEntity<?> runBranchSemesterIncrement(
            @RequestHeader("X-College-Name") String collegeName,
            @RequestParam String branchName) {
        try {
            promotionService.incrementSemesterOnly(collegeName, branchName);
            return ResponseEntity.ok(Map.of("success", true, "message", "Semesters updated smoothly for " + branchName));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================
    // 3. ISOLATED DEPARTMENT STAFF MANAGEMENT
    // ==========================================
    @GetMapping("/staff/pending")
    public ResponseEntity<?> getPendingBranchStaff(
            @RequestHeader("X-College-Name") String collegeName,
            @RequestParam String branchName) {
        try {
            List<Teacher> pendingStaff = teacherRepository.findByCollegeNameAndBranchNameAndIsPrincipalFalseAndIsApprovedFalse(collegeName, branchName);
            return ResponseEntity.ok(pendingStaff);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/staff/active")
    public ResponseEntity<?> getActiveBranchStaff(
            @RequestHeader("X-College-Name") String collegeName,
            @RequestParam String branchName) {
        try {
            List<Teacher> activeStaff = teacherRepository.findByCollegeNameAndBranchName(collegeName, branchName);
            return ResponseEntity.ok(activeStaff);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/staff/approve/{id}")
    public ResponseEntity<?> approveBranchStaff(
            @PathVariable Long id,
            @RequestParam String authorizedBranch) {
        try {
            Teacher teacher = teacherRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Teacher record missing"));

            if (!teacher.getBranchName().equalsIgnoreCase(authorizedBranch)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access. Department boundary breach."));
            }

            teacher.setApproved(true);
            teacher.setActive(true);
            teacherRepository.save(teacher);
            return ResponseEntity.ok(Map.of("success", true, "message", "Staff profile approved for " + teacher.getName()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================
    // 4. DEPARTMENTAL EXAM MANAGEMENT
    // ==========================================
    @GetMapping("/exams")
    public ResponseEntity<?> getBranchExams(
            @RequestHeader("X-College-Name") String collegeName,
            @RequestParam String branchName) {
        try {
            List<Exam> exams = examRepository.findByCollegeNameAndDepartmentName(collegeName, branchName);
            return ResponseEntity.ok(exams);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/exams/save")
    public ResponseEntity<?> saveBranchExam(
            @RequestHeader("X-College-Name") String collegeName,
            @RequestParam String branchName,
            @RequestBody Exam exam) {
        try {
            exam.setId(null);
            exam.setStatus("PUBLISHED");
            exam.setCollegeName(collegeName);
            exam.setDepartmentName(branchName);

            if (exam.getQuestions() != null) {
                for (Question q : exam.getQuestions()) {
                    q.setId(null);
                    q.setExam(exam);
                    if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                        q.setQuestionText("Exam Question");
                    }
                    if (q.getMarks() <= 0) {
                        q.setMarks(1);
                    }
                }
            }

            examRepository.save(exam);
            return ResponseEntity.ok(Map.of("success", true, "message", "Exam created successfully inside your department dashboard registry."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}