package com.example.intern.repository;

import com.example.intern.model.ExamArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamArchiveRepository extends JpaRepository<ExamArchive, Long> {
    List<ExamArchive> findByArchivedByUsername(String username);
}