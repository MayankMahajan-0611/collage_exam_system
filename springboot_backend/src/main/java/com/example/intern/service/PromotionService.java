package com.example.intern.service;

import com.example.intern.model.Student;
import com.example.intern.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;

@Service
public class PromotionService {

    @Autowired
    private StudentRepository studentRepository;

    @Transactional
    public void promoteDepartmentStudents(String collegeName, String departmentName) {
        // 🚨 FIXED: Isolated lookup to ensure students from other colleges are never altered
        List<Student> students = studentRepository.findByCollegeNameAndDepartmentNameAndIsActiveTrue(collegeName, departmentName);
        int currentYear = Year.now().getValue();

        String deptCode = departmentName.replaceAll("[^A-Z]", "");
        if (deptCode.isEmpty() || deptCode.length() < 2) {
            deptCode = departmentName.substring(0, Math.min(3, departmentName.length())).toUpperCase();
        }

        for (Student student : students) {
            if (student.getAcademicYear() < 4) {
                // 1. Advance Metrics
                student.setAcademicYear(student.getAcademicYear() + 1);
                if (student.getCurrentSemester() != null) {
                    student.setCurrentSemester(student.getCurrentSemester() + 2);
                }

                // 2. Permanent PRN (Stays fixed based on entry year, e.g., PRN2026CSE0001)
                if (student.getPrnNo() == null || student.getPrnNo().startsWith("OLD-") || student.getPrnNo().equals("OLD-PRN")) {
                    String uniquePrn = "PRN" + currentYear + deptCode + String.format("%04d", student.getId());
                    student.setPrnNo(uniquePrn);
                }

                // 3. Dynamic Roll Number: Resolves class standings (FE, SE, TE, BE) + ID
                String yearPrefix = getYearPrefix(student.getAcademicYear());
                String updatedRollNo = yearPrefix + String.format("%04d", student.getId());
                student.setRollNo(updatedRollNo);

                studentRepository.save(student);
            } else {
                student.setActive(false); // Graduate seniors
                studentRepository.save(student);
            }
        }
    }

    /**
     * Feature: Mid-term Semester Increment Only (Does not advance Academic Year or overwrite Roll No)
     */
    @Transactional
    public void incrementSemesterOnly(String collegeName, String departmentName) {
        // 🚨 FIXED: Isolated lookup bound strictly to the calling organization's context
        List<Student> students = studentRepository.findByCollegeNameAndDepartmentNameAndIsActiveTrue(collegeName, departmentName);
        for (Student student : students) {
            if (student.getCurrentSemester() != null) {
                student.setCurrentSemester(student.getCurrentSemester() + 1);
                studentRepository.save(student);
            }
        }
    }

    // Helper translation matrix mapping numeric year to collegiate prefix
    private String getYearPrefix(int academicYear) {
        switch (academicYear) {
            case 1:  return "FE";
            case 2:  return "SE";
            case 3:  return "TE";
            case 4:  return "BE";
            default: return "STUDENT";
        }
    }
}