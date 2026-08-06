package com.example.intern.repository;

import com.example.intern.model.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    // 🚨 FEATURE 4 & 5: Fetches exams strictly for a student's cohort that haven't been hidden
    List<Exam> findByDepartmentNameAndTargetSemesterAndIsVisibleToStudentsTrue(String departmentName, Integer targetSemester);

    List<Exam> findByCreatedByTeacherUsername(String username);

    // 🚨 ADD THIS LINE RIGHT HERE:
    List<Exam> findByCollegeName(String collegeName);

    //ADD this for 
    List<Exam> findByDepartmentName(String departmentName);
    // Add this exact line to your ExamRepository.java file
    List<Exam> findByCollegeNameAndDepartmentName(String collegeName, String departmentName);


}