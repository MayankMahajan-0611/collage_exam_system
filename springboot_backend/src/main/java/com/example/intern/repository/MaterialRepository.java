package com.example.intern.repository;

import com.example.intern.model.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface MaterialRepository extends JpaRepository<StudyMaterial, Long> {

    // Custom query method: Spring automatically writes the SQL to find
    // all materials uploaded by a specific teacher based on this method name.
    List<StudyMaterial> findByTeacherId(Long teacherId);
}