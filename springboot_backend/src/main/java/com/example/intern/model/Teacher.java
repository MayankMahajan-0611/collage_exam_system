package com.example.intern.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String username; // Serves as the login email / credential identifier

    @Column(nullable = false)
    private String password;

    @Column(name = "college_name")
    private String collegeName;

    @Column(name = "branch_name")
    private String branchName; // Department name boundary node (e.g., "Computer Science Engineering")

    @Column(name = "employee_id")
    private String employeeId;

    private boolean isPrincipal;
    private boolean isApproved;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.TEACHER; // Retained to support Principal / HOD controller actions

    // Stores list of subjects mapped to this teacher, eagerly fetched for dashboard routing authorization
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teacher_subjects", joinColumns = @JoinColumn(name = "teacher_id"))
    @Column(name = "subject_name")
    private List<String> subjects = new ArrayList<>();

    // Default Constructor
    public Teacher() {}

    // Parametric Constructor
    public Teacher(String name, String username, String password, String collegeName, String branchName, String employeeId, boolean isPrincipal, boolean isApproved, UserRole role) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.collegeName = collegeName;
        this.branchName = branchName;
        this.employeeId = employeeId;
        this.isPrincipal = isPrincipal;
        this.isApproved = isApproved;
        this.role = role;
    }

    // ==========================================
    // GETTERS AND SETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCollegeName() {
        return collegeName;
    }

    public void setCollegeName(String collegeName) {
        this.collegeName = collegeName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public boolean isPrincipal() {
        return isPrincipal;
    }

    public void setPrincipal(boolean principal) {
        isPrincipal = principal;
    }

    public boolean isApproved() {
        return isApproved;
    }

    public void setApproved(boolean approved) {
        isApproved = approved;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }
}