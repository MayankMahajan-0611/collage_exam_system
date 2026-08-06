package com.example.intern.repository;

import com.example.intern.model.QuestionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionResultRepository extends JpaRepository<QuestionResult, Long> {

    // Analyzes which questions have the highest failure rate for a specific exam
    @Query("SELECT q.questionText, COUNT(r) FROM QuestionResult r, Question q WHERE r.questionId = q.id AND r.examId = :examId AND r.isCorrect = false GROUP BY q.questionText ORDER BY COUNT(r) DESC")
    List<Object[]> getHardestQuestions(@Param("examId") Long examId);
    void deleteByExamId(Long examId);
}