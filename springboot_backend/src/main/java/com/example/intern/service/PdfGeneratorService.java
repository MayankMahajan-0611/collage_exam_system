package com.example.intern.service;

import com.example.intern.model.Exam;
import com.example.intern.model.Result;
import com.example.intern.model.Student;
import com.example.intern.repository.ExamRepository;
import com.example.intern.repository.ResultRepository;
import com.example.intern.repository.StudentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PdfGeneratorService {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private StudentRepository studentRepository;

    // ==========================================
    // 1. GENERATE COLLEGE EXAM REPORT
    // ==========================================
    public byte[] generateExamReport(Long examId) throws Exception {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found for PDF generation."));
        List<Result> results = resultRepository.findByExamId(examId);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Archived Exam Report: " + exam.getTitle());
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 710);
                contentStream.showText("Department Target: " + exam.getDepartmentName());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Total Student Submissions Saved: " + results.size());
                contentStream.endText();

                int yOffset = 640;
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD), 11);
                contentStream.newLineAtOffset(50, yOffset);
                contentStream.showText(String.format("%-15s %-30s %s", "Roll No", "Student Name", "Score"));
                contentStream.endText();

                contentStream.setLineWidth(1f);
                contentStream.moveTo(50, yOffset - 5);
                contentStream.lineTo(550, yOffset - 5);
                contentStream.stroke();

                yOffset -= 25;

                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 11);
                for (Result r : results) {
                    if (yOffset < 50) break;

                    Student linkedStudent = r.getStudent();
                    String rollNo = (linkedStudent != null && linkedStudent.getRollNo() != null) ? linkedStudent.getRollNo() : "N/A";
                    String name = (linkedStudent != null && linkedStudent.getName() != null) ? linkedStudent.getName() : "Unknown";

                    contentStream.beginText();
                    contentStream.newLineAtOffset(50, yOffset);

                    String rollNoStr = String.format("%-15s", rollNo);
                    if(name.length() > 28) name = name.substring(0, 25) + "...";
                    String nameStr = String.format("%-30s", name);
                    String scoreStr = r.getMarksObtained() + " / " + r.getTotalMarks();

                    contentStream.showText(rollNoStr + nameStr + scoreStr);
                    contentStream.endText();

                    yOffset -= 20;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // ==========================================
    // 2. GENERATE OFFICIAL ACADEMIC TRANSCRIPT
    // ==========================================
    public byte[] generateStudentMarksheet(Long studentId) throws Exception {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student Profile not found."));

        if (!student.isApproved()) {
            throw new RuntimeException("Access Denied: Your marksheet is currently hidden and pending HOD approval.");
        }

        List<Result> allResults = resultRepository.findAll();
        List<Result> studentResults = allResults.stream()
                .filter(r -> r.getStudent() != null && r.getStudent().getId().equals(studentId))
                .toList();

        // 🚨 NEW: Group results professionally by Semester!
        Map<Integer, List<Result>> resultsBySemester = studentResults.stream()
                .filter(r -> r.getExam() != null)
                .collect(Collectors.groupingBy(r -> r.getExam().getTargetSemester() != null ? r.getExam().getTargetSemester() : 0));

        List<Integer> semesters = new ArrayList<>(resultsBySemester.keySet());
        Collections.sort(semesters);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            int yOffset = 750;

            // 1. OFFICIAL HEADER
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 22);
            contentStream.newLineAtOffset(150, yOffset);
            contentStream.showText("OFFICIAL ACADEMIC TRANSCRIPT");
            contentStream.endText();

            yOffset -= 40;

            // 2. STUDENT DETAILS BLOCK
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            contentStream.newLineAtOffset(50, yOffset);
            contentStream.showText("Student Name : " + student.getName());
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText("Department   : " + student.getDepartmentName());
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText("College Name : " + student.getCollegeName());
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            contentStream.newLineAtOffset(350, yOffset);
            contentStream.showText("Roll Number : " + (student.getRollNo() != null ? student.getRollNo() : "N/A"));
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText("PRN Number  : " + (student.getPrnNo() != null ? student.getPrnNo() : "N/A"));
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText("Academic Yr : " + student.getAcademicYear());
            contentStream.endText();

            yOffset -= 45;

            int grandTotalEarned = 0;
            int grandTotalMax = 0;
            boolean hasAnyFails = false;

            // 3. LOOP THROUGH EACH SEMESTER
            for (Integer sem : semesters) {
                // Pagination Check: If we run out of room, create a new page automatically!
                if (yOffset < 200) {
                    contentStream.close();
                    page = new PDPage();
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    yOffset = 750;
                }

                String semTitle = sem == 0 ? "General / Unassigned Assessments" : "Semester " + sem;

                // Semester Header
                contentStream.setLineWidth(1.5f);
                contentStream.moveTo(50, yOffset);
                contentStream.lineTo(550, yOffset);
                contentStream.stroke();

                yOffset -= 15;
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                contentStream.newLineAtOffset(50, yOffset);
                contentStream.showText(semTitle);
                contentStream.endText();

                yOffset -= 20;

                // Table Column Headers
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD), 10);
                contentStream.newLineAtOffset(50, yOffset);
                contentStream.showText(String.format("%-35s %-10s %-12s %-10s %s", "Course / Subject Name", "Max", "Obtained", "Percent", "Status"));
                contentStream.endText();

                contentStream.setLineWidth(0.5f);
                contentStream.moveTo(50, yOffset - 5);
                contentStream.lineTo(550, yOffset - 5);
                contentStream.stroke();

                yOffset -= 20;

                int semTotalEarned = 0;
                int semTotalMax = 0;

                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 10);

                // Print Each Subject in the Semester
                for (Result r : resultsBySemester.get(sem)) {
                    if (yOffset < 100) {
                        contentStream.close();
                        page = new PDPage();
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yOffset = 750;
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 10);
                    }

                    String subject = (r.getExam().getSubjectName() != null && !r.getExam().getSubjectName().isEmpty())
                            ? r.getExam().getSubjectName() : r.getExam().getTitle();

                    if(subject.length() > 32) subject = subject.substring(0, 29) + "...";

                    int earned = r.getMarksObtained();
                    int max = r.getTotalMarks();
                    semTotalEarned += earned;
                    semTotalMax += max;

                    double pct = ((double) earned / max) * 100;
                    String status = pct >= 40.0 ? "PASS" : "FAIL";
                    if (pct < 40.0) hasAnyFails = true;

                    contentStream.beginText();
                    contentStream.newLineAtOffset(50, yOffset);
                    contentStream.showText(String.format("%-35s %-10d %-12d %-10s %s",
                            subject, max, earned, Math.round(pct) + "%", status));
                    contentStream.endText();

                    yOffset -= 15;
                }

                // Semester Aggregate Footer
                grandTotalEarned += semTotalEarned;
                grandTotalMax += semTotalMax;
                double semPct = semTotalMax > 0 ? ((double) semTotalEarned / semTotalMax) * 100 : 0;

                yOffset -= 5;
                contentStream.setLineWidth(0.5f);
                contentStream.moveTo(300, yOffset + 10);
                contentStream.lineTo(550, yOffset + 10);
                contentStream.stroke();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                contentStream.newLineAtOffset(300, yOffset);
                contentStream.showText(String.format("SGPA / Sem Total: %d / %d (%.1f%%)", semTotalEarned, semTotalMax, semPct));
                contentStream.endText();

                yOffset -= 30; // Spacing before next semester
            }

            // 4. FINAL GRAND AGGREGATE & SIGNATURES
            if (yOffset < 150) {
                contentStream.close();
                page = new PDPage();
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                yOffset = 750;
            }

            yOffset -= 20;
            contentStream.setLineWidth(1.5f);
            contentStream.moveTo(50, yOffset);
            contentStream.lineTo(550, yOffset);
            contentStream.stroke();

            yOffset -= 20;
            double finalPct = grandTotalMax > 0 ? ((double) grandTotalEarned / grandTotalMax) * 100 : 0;
            String finalResult = hasAnyFails ? "FAILED (Backlog Exists)" : (finalPct >= 75 ? "FIRST CLASS WITH DISTINCTION" : (finalPct >= 60 ? "FIRST CLASS" : "PASS CLASS"));

            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
            contentStream.newLineAtOffset(50, yOffset);
            contentStream.showText(String.format("OVERALL AGGREGATE : %d / %d", grandTotalEarned, grandTotalMax));
            contentStream.newLineAtOffset(0, -20);
            contentStream.showText(String.format("OVERALL PERCENTAGE: %.2f%%", finalPct));
            contentStream.newLineAtOffset(0, -20);
            contentStream.showText("FINAL RESULT      : " + finalResult);
            contentStream.endText();

            // Signature Block
            yOffset -= 80;
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            contentStream.newLineAtOffset(50, yOffset);
            contentStream.showText("_______________________");
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText("Date of Issue");

            contentStream.newLineAtOffset(350, 15);
            contentStream.showText("_______________________");
            contentStream.newLineAtOffset(0, -15);
            contentStream.showText("Head of Department (HOD)");
            contentStream.endText();

            // Footer Legend
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8);
            contentStream.newLineAtOffset(50, 30);
            contentStream.showText("* This is a system-generated transcript. Minimum passing criteria is 40% per subject.");
            contentStream.endText();

            contentStream.close(); // Close the final stream

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}