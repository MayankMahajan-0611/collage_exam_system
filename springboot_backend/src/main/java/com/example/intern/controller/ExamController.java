package com.example.intern.controller;

import com.example.intern.model.*;
import com.example.intern.repository.*;
import com.example.intern.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    @Autowired private FileStorageService fileStorageService;
    @Autowired private PdfProcessingService pdfProcessingService;
    @Autowired private MlIntegrationService mlIntegrationService;
    @Autowired private ExamRepository examRepository;
    @Autowired private ResultRepository resultRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private ExamArchiveRepository archiveRepository;
    @Autowired private ExamService examService;
    @Autowired private QuestionResultRepository questionResultRepository;
    @Autowired private PdfGeneratorService pdfGeneratorService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ==========================================
    // 1. CREATION & AI GENERATION (ISOLATED & FIXED)
    // ==========================================
    @PostMapping("/teacher/upload-notes")
    public ResponseEntity<String> uploadNotesAndGenerateExam(@RequestParam("file") MultipartFile file, @RequestParam(value = "num", defaultValue = "5") int num) {
        try {
            String savedFilePath = fileStorageService.saveTeacherNotes(file);
            String text = pdfProcessingService.extractTextFromPdf(savedFilePath);

            if (text == null || text.trim().length() < 100) {
                return ResponseEntity.badRequest().body("{\"error\": \"The uploaded PDF does not contain enough readable digital text.\"}");
            }

            String aiResponse = mlIntegrationService.generateQuestionsFromText(text, num);

            // 🚨 CRITICAL AI PARSING FIX: Strip Markdown syntax wrapping block comments if sent by LLM
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

    @PostMapping("/teacher/save")
    public ResponseEntity<?> saveExamToDatabase(@RequestBody Exam exam, @RequestParam(value = "username", required = false) String paramUsername) {
        try {
            exam.setId(null);
            exam.setStatus("PUBLISHED");

            // Ensure the mandatory creator field is set
            if (exam.getCreatedByTeacherUsername() == null || exam.getCreatedByTeacherUsername().isBlank()) {
                if (paramUsername != null && !paramUsername.isBlank()) {
                    exam.setCreatedByTeacherUsername(paramUsername);
                } else {
                    return ResponseEntity.badRequest().body("Error: Creator username is missing.");
                }
            }

            // 🚨 MULTI-TENANT LINK: Automatically lookup and bind the teacher's college to the saved exam
            Optional<Teacher> teacherOpt = teacherRepository.findByUsername(exam.getCreatedByTeacherUsername());
            if (teacherOpt.isPresent()) {
                exam.setCollegeName(teacherOpt.get().getCollegeName());
            }

            if (exam.getQuestions() != null) {
                for (Question q : exam.getQuestions()) {
                    q.setId(null);
                    q.setExam(exam);
                    if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                        q.setQuestionText("Generated Question");
                    }
                    if (q.getMarks() <= 0) {
                        q.setMarks(1); // Default to 1 mark
                    }
                }
            }

            examRepository.save(exam);
            return ResponseEntity.ok("Exam successfully saved with ID: " + exam.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error saving to database: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createExam(@RequestBody Map<String, Object> payload) {
        try {
            Exam exam = new Exam();
            exam.setTitle((String) payload.get("title"));
            exam.setDepartmentName((String) payload.get("departmentName"));

            if (payload.get("durationMinutes") != null) {
                exam.setDurationMinutes(Integer.valueOf(payload.get("durationMinutes").toString()));
            }
            if (payload.get("targetSemester") != null) {
                exam.setTargetSemester(Integer.valueOf(payload.get("targetSemester").toString()));
            }

            exam.setCreatedByTeacherUsername((String) payload.get("teacherUsername"));

            // 🚨 MULTI-TENANT LINK: Handle manual creations as well
            Optional<Teacher> teacherOpt = teacherRepository.findByUsername(exam.getCreatedByTeacherUsername());
            if (teacherOpt.isPresent()) {
                exam.setCollegeName(teacherOpt.get().getCollegeName());
            }

            examRepository.save(exam);
            return ResponseEntity.ok(Map.of("message", "Exam created successfully!", "id", exam.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Database error: " + e.getMessage()));
        }
    }

    // ==========================================
    // 2. EXAM VISIBILITY & TARGETING
    // ==========================================
    @PutMapping("/teacher/{examId}/toggle-visibility")
    public ResponseEntity<?> toggleExamVisibility(@PathVariable Long examId) {
        try {
            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new RuntimeException("Exam not found."));

            exam.setVisibleToStudents(!exam.isVisibleToStudents());
            examRepository.save(exam);

            String status = exam.isVisibleToStudents() ? "Visible to Students" : "Hidden from Students";
            return ResponseEntity.ok(Map.of("message", "Exam is now " + status, "isVisible", exam.isVisibleToStudents()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/student/available")
    public ResponseEntity<?> getAvailableExamsForStudent(@RequestParam String rollNo) {
        try {
            Student student = studentRepository.findByRollNo(rollNo)
                    .orElseThrow(() -> new RuntimeException("Student not found."));

            if (student.getCurrentSemester() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Your student profile is missing a semester/year assignment."));
            }

            // Fetch ONLY visible exams meant for this exact department and semester
            List<Exam> targetedExams = examRepository.findByDepartmentNameAndTargetSemesterAndIsVisibleToStudentsTrue(
                    student.getDepartmentName(),
                    student.getCurrentSemester()
            );

            // 🚨 SECURE BOUNDARY FILTER: Ensure student only sees exams matching their college
            List<Exam> isolatedExams = targetedExams.stream()
                    .filter(exam -> student.getCollegeName() != null && student.getCollegeName().equalsIgnoreCase(exam.getCollegeName()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(isolatedExams);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================
    // 3. STUDENT TAKING EXAMS
    // ==========================================
    @GetMapping("/student/take/{examId}")
    public ResponseEntity<?> getExamForStudent(@PathVariable Long examId, @RequestParam String rollNo) {
        try {
            Exam exam = examRepository.findById(examId).orElseThrow(() -> new RuntimeException("Exam not found."));

            Student activeStudent = studentRepository.findByRollNo(rollNo)
                    .orElseThrow(() -> new RuntimeException("Student record missing"));

            // 🚨 SECURE BOUNDARY CHECK: Deny crossover requests between differing colleges
            if (exam.getCollegeName() != null && !exam.getCollegeName().equalsIgnoreCase(activeStudent.getCollegeName())) {
                return ResponseEntity.status(403).body(Map.of("error", "Access Denied. Institutional boundary access violation."));
            }

            if (exam.getDepartmentName() != null && !exam.getDepartmentName().equalsIgnoreCase(activeStudent.getDepartmentName())) {
                return ResponseEntity.status(403).body(Map.of("error",
                        "Access Denied. This exam is explicitly restricted to the " + exam.getDepartmentName() + " branch."));
            }

            if (!exam.isVisibleToStudents()) {
                return ResponseEntity.status(403).body(Map.of("error", "This exam is currently hidden by the instructor."));
            }

            if (resultRepository.existsByExamIdAndStudentRollNo(examId, rollNo)) {
                return ResponseEntity.status(403).body(Map.of("error", "You have already completed this exam."));
            }

            LocalDateTime now = LocalDateTime.now();
            boolean bypassTimeCheck = false;

            if (!bypassTimeCheck) {
                if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Exam starts at: " + exam.getStartTime()));
                }
                if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Exam has ended!"));
                }
            }

            Map<String, Object> safeExamResponse = new HashMap<>();
            safeExamResponse.put("id", exam.getId());
            safeExamResponse.put("title", exam.getTitle());
            safeExamResponse.put("departmentName", exam.getDepartmentName());
            safeExamResponse.put("durationMinutes", exam.getDurationMinutes());
            safeExamResponse.put("startTime", exam.getStartTime());
            safeExamResponse.put("endTime", exam.getEndTime());

            if (exam.getQuestions() != null) {
                Collections.shuffle(exam.getQuestions());

                List<Map<String, Object>> safeQuestions = exam.getQuestions().stream().map(q -> {
                    Map<String, Object> safeQ = new HashMap<>();
                    safeQ.put("id", q.getId());
                    safeQ.put("questionText", q.getQuestionText());
                    safeQ.put("options", q.getOptions());
                    safeQ.put("marks", q.getMarks());
                    return safeQ;
                }).collect(Collectors.toList());

                safeExamResponse.put("questions", safeQuestions);
            }

            return ResponseEntity.ok(safeExamResponse);

        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/student/submit/{examId}")
    public ResponseEntity<?> submitStudentExam(
            @PathVariable Long examId,
            @RequestParam String studentName,
            @RequestParam String rollNo,
            @RequestBody Map<String, String> studentAnswers
    ) {
        try {
            Result result = examService.evaluateExam(examId, studentName, rollNo, studentAnswers);
            return ResponseEntity.ok("Score saved: " + result.getMarksObtained() + "/" + result.getTotalMarks());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // ==========================================
    // 4. ANALYTICS & RESULTS FETCHING (ISOLATED)
    // ==========================================
    @GetMapping("/all")
    public ResponseEntity<List<Exam>> getAllExams(@RequestHeader(value = "X-College-Name", required = false) String collegeName) {
        if (collegeName != null && !collegeName.isBlank()) {
            return ResponseEntity.ok(examRepository.findByCollegeName(collegeName));
        }
        return ResponseEntity.ok(examRepository.findAll());
    }

    @GetMapping("/results/exam/{examId}")
    public ResponseEntity<List<Result>> getResultsForExam(@PathVariable Long examId) {
        return ResponseEntity.ok(resultRepository.findByExamId(examId));
    }

    @GetMapping("/{examId}/analytics")
    public ResponseEntity<?> getExamAnalytics(@PathVariable Long examId, @RequestHeader(value = "X-College-Name", required = false) String collegeName) {
        try {
            Exam exam = examRepository.findById(examId).orElseThrow(() -> new RuntimeException("Exam not found"));
            List<Result> submissions = resultRepository.findByExamId(examId);

            List<Student> totalEligibleStudents;
            // 🚨 FIXED: Restricted query lookup to match indexed fields if the college parameter header is present
            if (collegeName != null && !collegeName.isBlank()) {
                totalEligibleStudents = studentRepository.findByCollegeNameAndDepartmentNameAndIsActiveTrue(collegeName, exam.getDepartmentName()).stream()
                        .filter(s -> exam.getTargetSemester() != null && exam.getTargetSemester().equals(s.getCurrentSemester()))
                        .collect(Collectors.toList());
            } else {
                totalEligibleStudents = studentRepository.findAll().stream()
                        .filter(s -> exam.getDepartmentName().equalsIgnoreCase(s.getDepartmentName()) &&
                                exam.getTargetSemester() != null &&
                                exam.getTargetSemester().equals(s.getCurrentSemester()))
                        .collect(Collectors.toList());
            }

            long passed = 0;
            long failed = 0;

            for(Result r : submissions) {
                double percentage = ((double) r.getMarksObtained() / r.getTotalMarks()) * 100;
                if(percentage >= 40.0) passed++; else failed++;
            }

            long absent = Math.max(0, totalEligibleStudents.size() - submissions.size());

            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalEligible", totalEligibleStudents.size());
            analytics.put("submitted", submissions.size());
            analytics.put("passed", passed);
            analytics.put("failed", failed);
            analytics.put("absent", absent);

            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    @GetMapping("/results/student/{studentName}")
    public ResponseEntity<?> getResultsForStudent(@PathVariable String studentName) {
        try {
            List<Result> results = resultRepository.findByStudentName(studentName);

            List<Map<String, Object>> safeResults = results.stream().map(r -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", r.getId());
                map.put("marksObtained", r.getMarksObtained());
                map.put("totalMarks", r.getTotalMarks());
                map.put("submissionTime", r.getSubmissionTime());

                if (r.getExam() != null) {
                    map.put("examTitle", r.getExam().getTitle());
                } else {
                    map.put("examTitle", "Exam #" + r.getId());
                }
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(safeResults);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // ==========================================
    // 5. SECURE ARCHIVAL & DELETION (ISOLATED)
    // ==========================================
    @Transactional
    @PostMapping("/archive-delete/{examId}")
    public ResponseEntity<?> archiveAndDelete(@PathVariable Long examId, @RequestBody Map<String, String> payload) {
        try {
            String rawPassword = payload.get("password");
            String username = payload.get("username");

            Teacher teacher = teacherRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Teacher not found."));

            if (!passwordEncoder.matches(rawPassword, teacher.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password. Exam not deleted."));
            }

            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new RuntimeException("Exam not found."));

            byte[] pdfBytes = pdfGeneratorService.generateExamReport(examId);

            ExamArchive archive = new ExamArchive();
            archive.setExamTitle(exam.getTitle());
            archive.setArchivedByUsername(username);
            archive.setPdfData(pdfBytes);
            archiveRepository.save(archive);

            examRepository.deleteById(examId);
            return ResponseEntity.ok(Map.of("message", "Exam securely archived and deleted."));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all-archives")
    public ResponseEntity<List<ExamArchive>> getAllArchives(@RequestHeader(value = "X-College-Name", required = false) String collegeName) {
        try {
            List<ExamArchive> archives = archiveRepository.findAll();

            // 🚨 FIXED: Filter multi-tenant archives efficiently by resolving owner credentials if header is present
            if (collegeName != null && !collegeName.isBlank()) {
                archives = archives.stream().filter(archive -> {
                    Optional<Teacher> t = teacherRepository.findByUsername(archive.getArchivedByUsername());
                    return t.isPresent() && collegeName.equalsIgnoreCase(t.get().getCollegeName());
                }).collect(Collectors.toList());
            }
            return ResponseEntity.ok(archives);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/retrieve-archive/{archiveId}")
    public ResponseEntity<?> retrieveArchive(@PathVariable Long archiveId, @RequestBody Map<String, String> payload) {
        try {
            String rawPassword = payload.get("password");
            String username = payload.get("username");

            Teacher teacher = teacherRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Teacher not found."));

            if (!passwordEncoder.matches(rawPassword, teacher.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password."));
            }

            ExamArchive archive = archiveRepository.findById(archiveId)
                    .orElseThrow(() -> new RuntimeException("Archive not found."));

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + archive.getExamTitle() + ".pdf\"")
                    .body(archive.getPdfData());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{examId}/hardest-questions")
    public ResponseEntity<?> getHardestQuestions(@PathVariable Long examId) {
        try {
            List<Object[]> hardest = questionResultRepository.getHardestQuestions(examId);
            return ResponseEntity.ok(hardest);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-archives")
    public ResponseEntity<?> getMyArchives(@RequestParam String username) {
        return ResponseEntity.ok(archiveRepository.findByArchivedByUsername(username));
    }
}