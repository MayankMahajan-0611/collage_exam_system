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
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private StudentRepository studentRepository;

    // ==========================================
    // 1. SAVE / PUBLISH EXAM WITH COLLEGE DATA
    // ==========================================
    @Transactional
    public Exam saveExam(Exam examPayload, String username) {
        Exam exam = new Exam();
        exam.setTitle(examPayload.getTitle());
        exam.setDepartmentName(examPayload.getDepartmentName());
        exam.setSubjectName(examPayload.getSubjectName());
        exam.setTargetSemester(examPayload.getTargetSemester());
        exam.setDurationMinutes(examPayload.getDurationMinutes());
        exam.setStartTime(examPayload.getStartTime());
        exam.setEndTime(examPayload.getEndTime());
        exam.setCreatedByTeacherUsername(username != null ? username : "HOD Admin");
        exam.setVisibleToStudents(true);

        Optional<Teacher> teacherOpt = Optional.empty();

        if (username != null && !username.isBlank() && !username.equals("undefined")) {
            teacherOpt = teacherRepository.findByUsername(username);
        }

        // Fallback: Check all records for a username match
        if (teacherOpt.isEmpty() && username != null) {
            teacherOpt = teacherRepository.findAll().stream()
                    .filter(t -> username.equalsIgnoreCase(t.getUsername()))
                    .findFirst();
        }

        // Fallback: Match by branch name if creating from HOD portal context
        if (teacherOpt.isEmpty() && examPayload.getDepartmentName() != null) {
            teacherOpt = teacherRepository.findAll().stream()
                    .filter(t -> examPayload.getDepartmentName().equalsIgnoreCase(t.getBranchName()))
                    .findFirst();
        }

        // Fallback: Principal registry fallback
        if (teacherOpt.isEmpty()) {
            List<Teacher> principals = teacherRepository.findByIsPrincipalTrue();
            if (!principals.isEmpty()) {
                teacherOpt = Optional.of(principals.get(0));
            }
        }

        if (teacherOpt.isPresent()) {
            Teacher t = teacherOpt.get();
            exam.setCollegeName(t.getCollegeName());
            if (exam.getDepartmentName() == null || exam.getDepartmentName().isBlank()) {
                exam.setDepartmentName(t.getBranchName());
            }
        } else {
            exam.setCollegeName(examPayload.getCollegeName() != null ? examPayload.getCollegeName() : "Tech Institute");
            if (exam.getDepartmentName() == null || exam.getDepartmentName().isBlank()) {
                exam.setDepartmentName("Computer Science Engineering");
            }
        }

        List<Question> questions = new ArrayList<>();
        if (examPayload.getQuestions() != null) {
            for (Question qPayload : examPayload.getQuestions()) {
                Question q = new Question();
                q.setQuestionText(qPayload.getQuestionText());
                q.setOptions(qPayload.getOptions());
                q.setCorrectAnswer(qPayload.getCorrectAnswer());
                q.setMarks(qPayload.getMarks() > 0 ? qPayload.getMarks() : 1);
                q.setExam(exam);
                questions.add(q);
            }
        }
        exam.setQuestions(questions);

        return examRepository.save(exam);
    }

    // ==========================================
    // 2. EVALUATE SUBMISSION & RECORD METRICS
    // ==========================================
    @Transactional
    public Result evaluateExam(Long examId, String studentName, String rollNo, Map<String, String> studentAnswers) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found mapping reference id: " + examId));

        Student student = studentRepository.findByRollNo(rollNo)
                .orElseThrow(() -> new RuntimeException("Student not found for roll number: " + rollNo));

        int marksObtained = 0;
        int totalMarks = 0;

        for (Question q : exam.getQuestions()) {
            totalMarks += q.getMarks();
            String submittedAnswer = studentAnswers.get(q.getId().toString());

            boolean isCorrect = q.getCorrectAnswer() != null && q.getCorrectAnswer().equalsIgnoreCase(submittedAnswer);

            if (isCorrect) {
                marksObtained += q.getMarks();
            }

            QuestionResult qr = new QuestionResult();
            qr.setExamId(examId);
            qr.setQuestionId(q.getId());
            qr.setRollNo(rollNo);
            qr.setCorrect(isCorrect);
            questionResultRepository.save(qr);
        }

        Result result = new Result();
        result.setExamId(exam.getId());
        result.setStudentId(student.getId());
        result.setMarksObtained(marksObtained);
        result.setTotalMarks(totalMarks);

        return resultRepository.save(result);
    }
}