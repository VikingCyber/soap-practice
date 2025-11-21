package com.viking.server.service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import com.viking.server.entity.UploadedFile;

import jakarta.activation.DataHandler;

public interface ContentService {
    File loadContent(String name);
    void storeContent(String username, String name, DataHandler content, String callbackUrl) throws IOException;
    
    // Новые методы для требований задания
    Instant getServerStartTime();
    long getUptimeSeconds(Instant startTime);
    UploadedFile getLastUploadedFile(String username);
    String getFilesCsv();
}
