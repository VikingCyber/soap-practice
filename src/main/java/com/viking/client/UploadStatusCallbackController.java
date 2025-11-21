package com.viking.client;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@RestController
@RequestMapping("/upload-status")
public class UploadStatusCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(UploadStatusCallbackController.class);
    
    // Храним последний статус загрузки для проверки клиентом
    private final AtomicReference<UploadStatusResponse> lastStatus = new AtomicReference<>();

    @PostMapping
    public ResponseEntity<String> receiveCallback(@RequestBody UploadStatusRequest request) {
        logger.info("Received upload status callback: status={}, filename={}, errorMessage={}", 
                request.status, request.filename, request.errorMessage);
        
        UploadStatusResponse response = new UploadStatusResponse();
        response.status = request.status;
        response.filename = request.filename;
        response.errorMessage = request.errorMessage;
        response.receivedAt = System.currentTimeMillis();
        
        lastStatus.set(response);
        
        return ResponseEntity.ok("OK");
    }
    
    public UploadStatusResponse getLastStatus() {
        return lastStatus.get();
    }
    
    public void clearStatus() {
        lastStatus.set(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UploadStatusRequest {
        public String status;
        public String filename;
        public String errorMessage;
    }
    
    public static class UploadStatusResponse {
        public String status;
        public String filename;
        public String errorMessage;
        public long receivedAt;
    }
}

