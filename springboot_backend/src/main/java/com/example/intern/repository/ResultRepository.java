package com.example.intern.repository;

import com.example.intern.model.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

    // For the Teacher/Principal to view all results for a specific exam
    List<Result> findByExamId(Long examId);

    // 🚨 FIXED: Traverses into the linked Student table to check the roll number
    boolean existsByExamIdAndStudentRollNo(Long examId, String rollNo);

    // 🚨 FIXED: Traverses into the linked Student table to find by their name
    List<Result> findByStudentName(String name);

    // (Recommended Addition) Fetching by ID is always the safest way to pull relational data!
    List<Result> findByStudentId(Long studentId);

    void deleteByExamId(Long examId);
}