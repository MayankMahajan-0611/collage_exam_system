package com.example.intern.repository;

import com.example.intern.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    // For Login
    Optional<Student> findByUsername(String username);

    // For taking exams and fetching targeted dashboards (Roll Number is synced with PRN via Principal rule)
    Optional<Student> findByRollNo(String rollNo);

    // For tracking unique registration codes during promotion and registration checks
    Optional<Student> findByPrnNo(String prnNo);

    // Checks if a student with this specific PRN already exists to avoid collisions
    boolean existsByPrnNo(String prnNo);

    // Standard lookup for all students in a department (including inactive/graduated)
    List<Student> findByDepartmentName(String departmentName);

    // 🚨 REQUIRED FOR PRINCIPAL & HOD CONTROLLERS:
    // Fetches only active, ungraduated students within a department cohort for promotion and analytics
    List<Student> findByDepartmentNameAndIsActiveTrue(String departmentName);

    //isolation
    List<Student> findByCollegeNameAndDepartmentName(String collegeName, String departmentName);
    List<Student> findByCollegeNameAndDepartmentNameAndIsActiveTrue(String collegeName, String departmentName);



}