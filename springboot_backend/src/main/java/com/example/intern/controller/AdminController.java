package com.example.intern.controller;

import com.example.intern.model.Teacher;
import com.example.intern.model.Student;
import com.example.intern.repository.TeacherRepository;
import com.example.intern.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private TeacherRepository teacherRepository;
    @Autowired private StudentRepository studentRepository;

    // ==========================================
    // 1. TOGGLE TEACHER / PRINCIPAL SERVICE
    // ==========================================
    @PutMapping("/toggle-teacher/{id}")
    public ResponseEntity<?> toggleTeacherService(@PathVariable Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        teacher.setActive(!teacher.isActive()); // Flips true to false, or false to true
        teacherRepository.save(teacher);

        String status = teacher.isActive() ? "Restored" : "Suspended";
        return ResponseEntity.ok(Map.of("message", "Teacher account has been " + status));
    }

    // ==========================================
    // 2. TOGGLE STUDENT SERVICE
    // ==========================================
    @PutMapping("/toggle-student/{id}")
    public ResponseEntity<?> toggleStudentService(@PathVariable Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        student.setActive(!student.isActive());
        studentRepository.save(student);

        String status = student.isActive() ? "Restored" : "Suspended";
        return ResponseEntity.ok(Map.of("message", "Student account has been " + status));
    }

    // ==========================================
    // 3. HARD DELETE USERS (If absolutely necessary)
    // ==========================================
    @DeleteMapping("/remove-user/{role}/{id}")
    public ResponseEntity<?> removeUser(@PathVariable String role, @PathVariable Long id) {
        try {
            if ("STUDENT".equalsIgnoreCase(role)) {
                studentRepository.deleteById(id);
            } else if ("TEACHER".equalsIgnoreCase(role) || "PRINCIPAL".equalsIgnoreCase(role)) {
                teacherRepository.deleteById(id);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
            }
            return ResponseEntity.ok(Map.of("message", "User permanently removed."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Cannot delete user. They may have existing exam records."));
        }
    }
    @GetMapping("/teachers")
    public ResponseEntity<List<Teacher>> getAllTeachers() {
        return ResponseEntity.ok(teacherRepository.findAll());
    }

    @GetMapping("/students")
    public ResponseEntity<List<Student>> getAllStudents() {
        return ResponseEntity.ok(studentRepository.findAll());
    }
}