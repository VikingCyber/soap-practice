package com.viking.server.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.activation.DataHandler;

@Service
public class ContentRepositoryImpl implements ContentRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentRepositoryImpl.class);

    @Value(value = "#{ systemProperties['java.io.tmpdir'] }")
    private String fileStorePath;

    @Override
    public File loadContent(String name) {
        return new File(this.fileStorePath, name + ".tmp");
    }

    @Override
    public void storeContent(String name, DataHandler content) throws IOException {
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
}
