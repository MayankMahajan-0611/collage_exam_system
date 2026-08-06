package com.example.intern.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_archives")
public class ExamArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String examTitle;
    private String archivedByUsername;
    private LocalDateTime archivedAt = LocalDateTime.now();

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] pdfData;

    // Default Constructor
    public ExamArchive() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public void setExamTitle(String examTitle) {
        this.examTitle = examTitle;
    }

    public String getArchivedByUsername() {
        return archivedByUsername;
    }

    public void setArchivedByUsername(String archivedByUsername) {
        this.archivedByUsername = archivedByUsername;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public byte[] getPdfData() {
        return pdfData;
    }

    public void setPdfData(byte[] pdfData) {
        this.pdfData = pdfData;
    }
}