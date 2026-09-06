package com.example.intern.controller;

import com.example.intern.model.Student;
import com.example.intern.model.Teacher;
import com.example.intern.repository.StudentRepository;
import com.example.intern.repository.TeacherRepository;
import com.example.intern.service.RefreshTokenService;
import com.example.intern.model.RefreshToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired private StudentRepository studentRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private RefreshTokenService refreshTokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> payload) {
        String role = payload.get("role");
        String username = payload.get("username");
        String rawPassword = payload.get("password");

        if ("ADMIN".equalsIgnoreCase(role)) return ResponseEntity.status(403).body(Map.of("error", "Admin accounts cannot be created via API."));

        if (studentRepository.findByUsername(username).isPresent() || teacherRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists!"));
        }

        if ("STUDENT".equalsIgnoreCase(role)) {
            Student student = new Student();
            student.setName(payload.get("name"));
            student.setUsername(username);
            student.setPassword(passwordEncoder.encode(rawPassword));
            student.setCollegeName(payload.get("collegeName"));
            student.setDepartmentName(payload.get("departmentName"));
            student.setRollNo(payload.get("rollNo"));
            if(payload.get("currentSemester") != null) student.setCurrentSemester(Integer.parseInt(payload.get("currentSemester")));
            student.setApproved(true);
            studentRepository.save(student);
            return ResponseEntity.ok(Map.of("message", "Student registration successful."));
        }
        else if ("TEACHER".equalsIgnoreCase(role) || "PRINCIPAL".equalsIgnoreCase(role)) {
            Teacher staff = new Teacher();
            staff.setName(payload.get("name"));
            staff.setUsername(username);
            staff.setPassword(passwordEncoder.encode(rawPassword));
            staff.setCollegeName(payload.get("collegeName"));
            staff.setPrincipal("PRINCIPAL".equalsIgnoreCase(role));
            staff.setApproved(false);

            if ("TEACHER".equalsIgnoreCase(role)) {
                staff.setBranchName(payload.get("departmentName"));
                staff.setEmployeeId(payload.get("employeeId"));

                if (payload.get("subjects") != null && !payload.get("subjects").isBlank()) {
                    List<String> subjectList = Arrays.stream(payload.get("subjects").split(","))
                            .map(String::trim).collect(Collectors.toList());
                    staff.setSubjects(subjectList);
                }
            } else {
                staff.setBranchName("ADMINISTRATION");
                staff.setEmployeeId("PRINCIPAL-" + System.currentTimeMillis());
            }

            teacherRepository.save(staff);
            String msg = staff.isPrincipal() ? "Principal registration successful. Waiting for Admin approval." : "Teacher registration successful. Waiting for Principal approval.";
            return ResponseEntity.ok(Map.of("message", msg));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String rawPassword = credentials.get("password");

        // --- Check Student Table ---
        Optional<Student> studentOpt = studentRepository.findByUsername(username);
        if (studentOpt.isPresent()) {
            Student s = studentOpt.get();
            if (passwordEncoder.matches(rawPassword, s.getPassword())) {
                if (!s.isActive()) return ResponseEntity.status(403).body(Map.of("error", "Your account has been suspended by the Administrator."));
                if (!s.isApproved()) return ResponseEntity.status(403).body(Map.of("error", "Account pending approval."));

                Map<String, Object> response = new HashMap<>();
                response.put("id", s.getId());
                response.put("username", s.getName());
                response.put("email", s.getUsername());
                response.put("role", "STUDENT");
                response.put("rollNo", s.getRollNo());
                response.put("collegeName", s.getCollegeName());
                response.put("departmentName", s.getDepartmentName());

                // 🚨 Generate Refresh Token Cookie for Student
                ResponseCookie jwtRefreshCookie = generateRefreshTokenCookie(username);
                return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString()).body(response);
            }
        }

        // --- Check Teacher/Admin/HOD Table ---
        Optional<Teacher> teacherOpt = teacherRepository.findByUsername(username);
        if (teacherOpt.isPresent()) {
            Teacher t = teacherOpt.get();
            if (passwordEncoder.matches(rawPassword, t.getPassword())) {
                if (!t.isActive()) return ResponseEntity.status(403).body(Map.of("error", "Your account has been suspended by the Administrator."));
                if (!t.isApproved()) return ResponseEntity.status(403).body(Map.of("error", "Account pending hierarchy approval."));

                Map<String, Object> response = new HashMap<>();
                response.put("id", t.getId());
                response.put("username", t.getName());
                response.put("email", t.getUsername());

                String roleAssignment = "TEACHER";
                if (t.isPrincipal()) {
                    roleAssignment = "PRINCIPAL";
                } else if (t.getRole() != null) {
                    String dbRole = String.valueOf(t.getRole()).trim();
                    if (!dbRole.isEmpty()) {
                        roleAssignment = dbRole.toUpperCase();
                    }
                } else if ("ADMIN".equals(t.getBranchName())) {
                    roleAssignment = "ADMIN";
                }

                response.put("role", roleAssignment);
                response.put("collegeName", t.getCollegeName());
                response.put("branchName", t.getBranchName());
                response.put("subjects", t.getSubjects() != null ? t.getSubjects() : new ArrayList<>());

                // 🚨 Generate Refresh Token Cookie for Teacher
                ResponseCookie jwtRefreshCookie = generateRefreshTokenCookie(username);
                return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString()).body(response);
            }
        }

        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials."));
    }

    // ==========================================
    // NEW: REFRESH & LOGOUT ENDPOINTS
    // ==========================================

    // Helper method to keep login code clean
    private ResponseCookie generateRefreshTokenCookie(String username) {
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(username);
        return ResponseCookie.from("refreshToken", refreshToken.getToken())
                .httpOnly(true)
                .secure(false) // TODO: Set to true in production when using HTTPS
                .path("/api/auth/refresh")
                .maxAge(7 * 24 * 60 * 60) // 7 Days
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@CookieValue(name = "refreshToken", required = false) String requestRefreshToken) {
        if (requestRefreshToken == null || requestRefreshToken.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Refresh Token is missing!"));
        }

        try {
            return refreshTokenService.findByToken(requestRefreshToken)
                    .map(refreshTokenService::verifyExpiration)
                    .map(RefreshToken::getUsername)
                    .map(username -> {
                        // Here is where you would issue a new Short-Lived Access JWT if you are using one
                        Map<String, String> response = new HashMap<>();
                        response.put("message", "Token refreshed successfully");
                        return ResponseEntity.ok(response);
                    })
                    .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> payload) {
        if (payload != null && payload.containsKey("username")) {
            refreshTokenService.deleteByUsername(payload.get("username"));
        }

        // Overwrite the cookie with a blank, expired one to destroy it in the browser
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    // ==========================================
    // EXISTING WORKFLOWS
    // ==========================================
    @GetMapping("/admin/pending-principals")
    public ResponseEntity<List<Teacher>> getPendingPrincipals() { return ResponseEntity.ok(teacherRepository.findByIsPrincipalTrueAndIsApprovedFalse()); }

    @PutMapping("/admin/approve-principal/{id}")
    public ResponseEntity<?> approvePrincipal(@PathVariable Long id) {
        Teacher principal = teacherRepository.findById(id).orElseThrow(); principal.setApproved(true); teacherRepository.save(principal);
        return ResponseEntity.ok(Map.of("message", "Principal Approved."));
    }

    @GetMapping("/principal/pending-teachers/{collegeName}")
    public ResponseEntity<List<Teacher>> getPendingTeachers(@PathVariable String collegeName) {
        return ResponseEntity.ok(teacherRepository.findByIsPrincipalFalseAndCollegeNameAndIsApprovedFalse(collegeName));
    }

    @PutMapping("/principal/approve-teacher/{id}")
    public ResponseEntity<?> approveTeacher(@PathVariable Long id) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow(); teacher.setApproved(true); teacherRepository.save(teacher);
        return ResponseEntity.ok(Map.of("message", "Teacher Approved."));
    }
}