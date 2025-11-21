package com.viking.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viking.server.entity.UploadedFile;
import com.viking.server.entity.User;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    List<UploadedFile> findByUser(User user);
    
    Optional<UploadedFile> findTopByUserOrderByUploadTimeDesc(User user);
}
