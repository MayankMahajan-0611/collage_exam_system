package com.example.intern.model;

import jakarta.persistence.*;

@Entity
@Table(name = "study_materials")
public class StudyMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String filePath; // Path to the uploaded PDF

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;
}