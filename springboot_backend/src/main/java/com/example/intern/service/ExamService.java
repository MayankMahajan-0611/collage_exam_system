package com.example.intern.service;

import com.example.intern.model.Exam;
import com.example.intern.model.Question;
import com.example.intern.model.QuestionResult;
import com.example.intern.model.Result;
import com.example.intern.model.Student;
import com.example.intern.model.Teacher;
import com.example.intern.repository.ExamRepository;
import com.example.intern.repository.QuestionResultRepository;
import com.example.intern.repository.ResultRepository;
import com.example.intern.repository.StudentRepository;
import com.example.intern.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExamService {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private QuestionResultRepository questionResultRepository;

    // 🚨 INJECTED: Required to look up the student profile for relational mapping
    @Autowired
    private StudentRepository studentRepository;

    // ==========================================
    // 1. SAVE / PUBLISH EXAM WITH COLLEGE DATA
    // ==========================================
    public Exam saveExam(Exam examPayload, String username) {
        Exam exam = new Exam();
        exam.setTitle(examPayload.getTitle());
        exam.setDepartmentName(examPayload.getDepartmentName());
        exam.setSubjectName(examPayload.getSubjectName());
        exam.setTargetSemester(examPayload.getTargetSemester());
        exam.setDurationMinutes(examPayload.getDurationMinutes());
        exam.setStartTime(examPayload.getStartTime());
        exam.setEndTime(examPayload.getEndTime());
        exam.setCreatedByTeacherUsername(username);
        exam.setVisibleToStudents(true); // Default visible on publish

        // Extract and map the college layout from the creating user session
        Optional<Teacher> teacherOpt = teacherRepository.findByUsername(username);
        if (teacherOpt.isPresent()) {
            exam.setCollegeName(teacherOpt.get().getCollegeName());
        } else {
            throw new RuntimeException("Unauthorized: Creating teacher account profile context not found.");
        }

        // Map and link questions back to this exam container for cascading persistence
        List<Question> questions = new ArrayList<>();
        if (examPayload.getQuestions() != null) {
            for (Question qPayload : examPayload.getQuestions()) {
                Question q = new Question();
                q.setQuestionText(qPayload.getQuestionText());
                q.setOptions(qPayload.getOptions());
                q.setCorrectAnswer(qPayload.getCorrectAnswer());
                q.setMarks(qPayload.getMarks());
                q.setExam(exam); // Establishes directional Relationship link
                questions.add(q);
            }
        }
        exam.setQuestions(questions);

        return examRepository.save(exam);
    }

    // ==========================================
    // 2. EVALUATE SUBMISSION & RECORD METRICS
    // ==========================================
    public Result evaluateExam(Long examId, String studentName, String rollNo, Map<String, String> studentAnswers) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found mapping reference id: " + examId));

        // 🚨 NEW: Fetch the real student object for our relational database mapping
        Student student = studentRepository.findByRollNo(rollNo)
                .orElseThrow(() -> new RuntimeException("Student not found for roll number: " + rollNo));

        int marksObtained = 0;
        int totalMarks = 0;

        for (Question q : exam.getQuestions()) {
            totalMarks += q.getMarks();
            String submittedAnswer = studentAnswers.get(q.getId().toString());

            // Null-safe grading evaluation logic
            boolean isCorrect = q.getCorrectAnswer() != null && q.getCorrectAnswer().equalsIgnoreCase(submittedAnswer);

            if (isCorrect) {
                marksObtained += q.getMarks();
            }

            // Save individual question result configurations for AI/Smart Failure Analysis pipelines
            QuestionResult qr = new QuestionResult();
            qr.setExamId(examId);
            qr.setQuestionId(q.getId());
            qr.setRollNo(rollNo);
            qr.setCorrect(isCorrect);
            questionResultRepository.save(qr);
        }

        // Bundle metrics back into database entity layer securely
        Result result = new Result();
        result.setExamId(exam.getId());
        result.setStudentId(student.getId()); // 🚨 NEW: Locks the result to the student via foreign key!
        result.setMarksObtained(marksObtained);
        result.setTotalMarks(totalMarks);

        return resultRepository.save(result);
    }
}