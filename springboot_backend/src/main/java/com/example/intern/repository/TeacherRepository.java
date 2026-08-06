package com.example.intern.repository;

import com.example.intern.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    // For Login and Validation
    Optional<Teacher> findByUsername(String username);

    // For Principal Dashboard: Fetching all teachers in their specific college
    List<Teacher> findByCollegeNameAndIsPrincipalFalse(String collegeName);

    // For Admin Workflow: Fetching principals waiting for approval
    List<Teacher> findByIsPrincipalTrueAndIsApprovedFalse();

    // For Principal Workflow: Fetching teachers waiting for approval in their college
    List<Teacher> findByIsPrincipalFalseAndCollegeNameAndIsApprovedFalse(String collegeName);

    // Isolated query to show ALL teachers/HODs belonging only to this specific college
    List<Teacher> findByCollegeName(String collegeName);

    // Fixed from previous department error property check
    List<Teacher> findByCollegeNameAndBranchName(String collegeName, String branchName);

    // REQUIRED FOR HOD CONTROLLER: Fetches pending teachers filtered by both College AND Branch
    List<Teacher> findByCollegeNameAndBranchNameAndIsPrincipalFalseAndIsApprovedFalse(String collegeName, String branchName);
}