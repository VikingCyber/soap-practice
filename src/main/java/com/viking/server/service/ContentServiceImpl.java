package com.viking.server.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.activation.DataHandler;
import jakarta.xml.bind.ValidationException;

@Service
public class ContentServiceImpl implements ContentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value(value = "#{ systemProperties['java.io.tmpdir'] }")
    private String fileStorePath;

    @Override
    public File loadContent(String name) {
        return new File(this.fileStorePath, name + ".tmp");
    }

    @Override
    public void storeContent(String name, DataHandler content) throws IOException {
        try {
            validateFile(name, content);
        } catch (ValidationException e) {
            throw new IOException("Validation failed: " + e.getMessage(), e);
        }
        
        File outFile = new File(this.fileStorePath, name + ".tmp");
        logger.info("Storing content in file: {}", outFile.getAbsolutePath());
        int i = 0;
        byte[] buffer = new byte[1024];
        try (InputStream in = content.getInputStream()) {
            try (OutputStream out = new FileOutputStream(outFile)) {
                while ((i = in.read(buffer, 0, buffer.length)) > 0) {
                    out.write(buffer, 0, i);
                }
            }
        }
        logger.info("Content stored.");
    }

    void validateFile(String name, DataHandler content) throws IOException, ValidationException {
        validateFileSize(content);
        validateFileName(name);
        validateNotJSON(content);
        validateDiskSpace();
    }

    long validateFileSize(DataHandler content) throws IOException, ValidationException {
        long size = 0;
        byte[] buffer = new byte[8192];
        final long MAX_SIZE = 3 * 1024 * 1024;
        try (InputStream in = content.getInputStream()) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1){ 
                size += bytesRead;
                if (size > MAX_SIZE) {
                    throw new ValidationException(String.format("File exited 3 Mb: %d byte (> %d byte)", size, MAX_SIZE));
                }
            }
        }
        if (size == 0) { 
            throw new ValidationException("File is empty.");
        }
        return size;
    }

    void validateFileName(String name) throws ValidationException {
        if (name.toLowerCase().contains("ж")) {
            throw new ValidationException("Name of the file containts 'ж' letter " + name);
        }
    }

    void validateNotJSON(DataHandler content) throws IOException {
        try (InputStream in = content.getInputStream()) {
            String contentString = new String(in.readAllBytes());

            try {
                JsonNode jsonNode = objectMapper.readTree(contentString);
                String normalized = objectMapper.writeValueAsString(jsonNode);
                String originalNormalized = objectMapper.writeValueAsString(objectMapper.readTree(contentString.trim()));
                if (normalized.equals(originalNormalized)) {
                    // Вместо ValidationException используем RuntimeException
                    throw new RuntimeException("File contains valid JSON (not allowed)");
                }
            } catch (IOException e) {
                logger.info("File is not JSON");
            }
        }
    }

    void validateDiskSpace() throws ValidationException {
        try {
            File storeDir = new File(fileStorePath);
            FileStore store = Files.getFileStore(storeDir.toPath());
            long freeSpace = store.getUsableSpace();
            final long MIN_FREE_SPACE = 1024 * 1024 * 10; // 10 MB
            if (freeSpace < MIN_FREE_SPACE) {
                throw new ValidationException("Not enough disk space");
            }
        } catch (IOException e) {
            throw new ValidationException("Cannot check disk space: " + e.getMessage());
        }
    }
}