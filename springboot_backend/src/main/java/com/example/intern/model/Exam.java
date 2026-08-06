package com.example.intern.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exams")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String departmentName;
    private Integer durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String createdByTeacherUsername;

    @Column(name = "target_semester")
    private Integer targetSemester;

    @Column(name = "is_visible_to_students", columnDefinition = "boolean default true")
    private boolean isVisibleToStudents = true;

    @Column(name = "subject_name")
    private String subjectName;

    @Column(name = "college_name")
    private String collegeName;

    // Relationship to Questions
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Question> questions;

    // Relationship to Results
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Result> results = new ArrayList<>();

    // Helper method to add results and keep the bidirectional link consistent
    public void addResult(Result result) {
        results.add(result);
        result.setExam(this);
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedByTeacherUsername() { return createdByTeacherUsername; }
    public void setCreatedByTeacherUsername(String createdByTeacherUsername) { this.createdByTeacherUsername = createdByTeacherUsername; }

    public Integer getTargetSemester() { return targetSemester; }
    public void setTargetSemester(Integer targetSemester) { this.targetSemester = targetSemester; }

    public boolean isVisibleToStudents() { return isVisibleToStudents; }
    public void setVisibleToStudents(boolean visibleToStudents) { this.isVisibleToStudents = visibleToStudents; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }

    public List<Result> getResults() { return results; }
    public void setResults(List<Result> results) { this.results = results; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }
}