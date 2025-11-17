package com.viking.server.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.exception.ValidationException;

import jakarta.activation.DataHandler;

@Service
public class ContentRepositoryImpl implements ContentRepository  {

    private static final Logger log = LoggerFactory.getLogger(ContentRepositoryImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    static final long MAX_FILE_SIZE = 3 * 1024 * 1024; // 3 MB
    private static final long STORAGE_LIMIT = 10 * 1024 * 1024; // 10 MB free

    @Value("#{ systemProperties['java.io.tmpdir'] }") String fileStorePath;

    @Override
    public File loadContent(String name) {
        return new File(fileStorePath, name + ".tmp");
    }

    @Override
    public void storeContent(String name, DataHandler handler) throws IOException, ValidationException {
        validateFileName(name);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long size = 0;

        try (InputStream in = new BufferedInputStream(handler.getInputStream())) {
            byte[] tmp = new byte[8192];
            int read;
            while ((read = in.read(tmp)) != -1) {
                size += read;
                if (size > MAX_FILE_SIZE) {
                    throw new ValidationException("File exceeded 3MB limit: " + size + " bytes");
                }
                buffer.write(tmp, 0, read);
            }
        }

        validateNotJSON(buffer.toByteArray());

        validateStorageQuota(size);

        File outFile = new File(fileStorePath, name + ".tmp");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            buffer.writeTo(out);
        }

        log.info("Content stored successfully: {}, size={}", outFile.getAbsolutePath(), size);
    }
    

    /* ==================== VALIDATION METHODS ==================== */

    void validateFileName(String name) throws ValidationException {
        if (name.toLowerCase().contains("ж")) {
            throw new ValidationException("File name contains forbidden letter 'ж': " + name);
        }
    }

    void validateNotJSON(byte[] content) throws ValidationException {
        try {
            objectMapper.readTree(content);
            throw new ValidationException("File contains valid JSON (not allowed)");
        } catch (JsonProcessingException e) {
            // Good: not JSON
        } catch (IOException e) {
            throw new ValidationException("Error reading content: " + e.getMessage());
        }
    }

    void validateStorageQuota(long newFileSize) throws ValidationException {
        File storeDir = new File(fileStorePath);
        long currentSize = Arrays.stream(Objects.requireNonNull(storeDir.listFiles()))
            .filter(File::isFile)
            .mapToLong(File::length)
            .sum();
        if (currentSize + newFileSize > STORAGE_LIMIT) {
            throw new ValidationException(
                String.format("Storage quota exceeded. Used: %d bytes, limit: %d bytes",
                 currentSize, STORAGE_LIMIT)
            );
        }
    }
}
