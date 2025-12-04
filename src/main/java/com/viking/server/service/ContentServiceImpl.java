package com.viking.server.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.server.entity.UploadedFile;
import com.viking.server.entity.User;
import com.viking.server.repository.UploadedFileRepository;
import com.viking.server.repository.UserRepository;

import jakarta.activation.DataHandler;
import jakarta.xml.bind.ValidationException;

@Service
public class ContentServiceImpl implements ContentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant serverStartTime;
    private final UploadedFileRepository uploadedFileRepository;
    private final UserRepository userRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value(value = "#{ systemProperties['java.io.tmpdir'] }")
    private String fileStorePath;

    public ContentServiceImpl(Instant serverStartTime,
                             UploadedFileRepository uploadedFileRepository,
                             UserRepository userRepository) {
        this.serverStartTime = serverStartTime;
        this.uploadedFileRepository = uploadedFileRepository;
        this.userRepository = userRepository;
    }

    @Override
    public File loadContent(String name) {
        return new File(this.fileStorePath, name + ".tmp");
    }

    @Override
    @Transactional
    public void storeContent(String username, String name, DataHandler content, String callbackUrl) throws IOException {
        logger.info("storeContent invoked. username={}, filename={}, dataHandler={}",
                username, name, content != null ? content.getClass().getName() : "null");
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IOException("User not found: " + username));
        
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setFilename(name);
        uploadedFile.setUser(user);
        uploadedFile.setUploadTime(LocalDateTime.now());
        uploadedFile.setStatus("IN_PROGRESS");
        uploadedFile = uploadedFileRepository.save(uploadedFile);
        
        String errorMessage = null;
        byte[] fileBytes = null;
        
        try {
            // Читаем файл один раз в память для всех проверок
            fileBytes = readFileContent(content);
            
            validateFileSize(fileBytes);
            validateFileName(name);
            validateNotJSON(fileBytes);
            validateDiskSpace();
            
            // Сохраняем файл на диск
            File outFile = new File(this.fileStorePath, name + ".tmp");
            logger.info("Storing content in file: {}", outFile.getAbsolutePath());
            try (OutputStream out = new FileOutputStream(outFile)) {
                out.write(fileBytes);
            }
            
            uploadedFile.setStatus("SUCCESS");
            uploadedFile.setSizeBytes(fileBytes.length);
            uploadedFileRepository.save(uploadedFile);
            logger.info("Content stored successfully.");
            
            // Асинхронный callback при успехе
            if (callbackUrl != null && !callbackUrl.isBlank()) {
                sendCallback(callbackUrl, "SUCCESS", null, uploadedFile);
            }
            
        } catch (ValidationException | RuntimeException e) {
            errorMessage = e.getMessage();
            uploadedFile.setStatus("FAILED");
            uploadedFile.setErrorMessage(errorMessage);
            uploadedFileRepository.save(uploadedFile);
            logger.error("Validation failed: {}", errorMessage);
            
            // Асинхронный callback при ошибке
            if (callbackUrl != null && !callbackUrl.isBlank()) {
                sendCallback(callbackUrl, "FAILED", errorMessage, uploadedFile);
            }
            
            throw new IOException("Validation failed: " + errorMessage, e);
        }
    }
    
    private byte[] readFileContent(DataHandler content) throws IOException, ValidationException {
        long size = 0;
        byte[] buffer = new byte[8192];
        final long MAX_SIZE = 3 * 1024 * 1024;
        
        logger.info("Reading attachment via DataHandler: {}", content != null ? content.getClass().getName() : "null");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (InputStream in = content.getInputStream()) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                size += bytesRead;
                if (size > MAX_SIZE) {
                    throw new ValidationException(String.format("File exceeded 3 MB: %d bytes (> %d bytes)", size, MAX_SIZE));
                }
                baos.write(buffer, 0, bytesRead);
            }
        }
        
        byte[] fileBytes = baos.toByteArray();
        logger.info("Finished reading attachment. Total bytes read={}", fileBytes.length);
        if (fileBytes.length == 0) {
            throw new ValidationException("File is empty.");
        }
        
        return fileBytes;
    }

    void validateFileSize(byte[] fileBytes) throws ValidationException {
        final long MAX_SIZE = 3 * 1024 * 1024;
        if (fileBytes.length > MAX_SIZE) {
            throw new ValidationException(String.format("File exceeded 3 MB: %d bytes (> %d bytes)", 
                    fileBytes.length, MAX_SIZE));
        }
    }

    void validateFileName(String name) throws ValidationException {
        if (name.toLowerCase().contains("ж")) {
            throw new ValidationException("Name of the file containts 'ж' letter " + name);
        }
    }

    void validateNotJSON(byte[] fileBytes) throws ValidationException {
        try {
            String contentString = new String(fileBytes);
            JsonNode jsonNode = objectMapper.readTree(contentString);
            String normalized = objectMapper.writeValueAsString(jsonNode);
            String originalNormalized = objectMapper.writeValueAsString(objectMapper.readTree(contentString.trim()));
            if (normalized.equals(originalNormalized)) {
                throw new ValidationException("File contains valid JSON (not allowed)");
            }
        } catch (IOException e) {
            // Файл не является валидным JSON - это нормально
            logger.debug("File is not JSON, which is OK");
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
    
    @Override
    public Instant getServerStartTime() {
        return serverStartTime;
    }
    
    @Override
    public long getUptimeSeconds(Instant startTime) {
        return Instant.now().getEpochSecond() - startTime.getEpochSecond();
    }
    
    @Override
    public UploadedFile getLastUploadedFile(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return null;
        }
        return uploadedFileRepository.findTopByUserOrderByUploadTimeDesc(userOpt.get()).orElse(null);
    }
    
    @Override
    public String getFilesCsv() {
        List<UploadedFile> files = uploadedFileRepository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Filename,Username,SizeBytes,UploadTime,Status,ErrorMessage\n");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (UploadedFile file : files) {
            csv.append(file.getId()).append(",");
            csv.append(escapeCsv(file.getFilename())).append(",");
            csv.append(escapeCsv(file.getUser() != null ? file.getUser().getUsername() : "")).append(",");
            csv.append(file.getSizeBytes()).append(",");
            csv.append(file.getUploadTime() != null ? file.getUploadTime().format(formatter) : "").append(",");
            csv.append(escapeCsv(file.getStatus() != null ? file.getStatus() : "")).append(",");
            csv.append(escapeCsv(file.getErrorMessage() != null ? file.getErrorMessage() : "")).append("\n");
        }
        
        return csv.toString();
    }
    
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    @Async
    private void sendCallback(String callbackUrl, String status, String errorMessage, UploadedFile file) {
        try {
            // Безопасное экранирование JSON
            String filenameJson = escapeJson(file.getFilename() != null ? file.getFilename() : "");
            String errorJson = errorMessage != null ? "\"" + escapeJson(errorMessage) + "\"" : "null";
            
            String jsonBody = String.format(
                "{\"status\":\"%s\",\"filename\":\"%s\",\"errorMessage\":%s}",
                escapeJson(status),
                filenameJson,
                errorJson
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            future.thenAccept(response -> {
                logger.info("Callback sent to {}: status={}, responseCode={}", callbackUrl, status, response.statusCode());
            }).exceptionally(e -> {
                logger.error("Failed to send callback to {}: {}", callbackUrl, e.getMessage());
                return null;
            });
            
        } catch (Exception e) {
            logger.error("Error preparing callback to {}: {}", callbackUrl, e.getMessage());
        }
    }
    
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}