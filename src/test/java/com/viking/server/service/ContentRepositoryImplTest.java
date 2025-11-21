package com.viking.server.service; // ВАЖНО: тот же package!


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.activation.DataHandler;

@SpringBootTest
public class ContentRepositoryImplTest {
    
    @Autowired
    private ContentServiceImpl contentRepository;

    private DataHandler createDataHandler(byte[] data) {
        return new DataHandler(new ByteArrayDataSource(data, "application/octet-stream"));
    }

    private static class ByteArrayDataSource implements jakarta.activation.DataSource {
        private final byte[] data;
        private final String contentType;

        public ByteArrayDataSource(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new java.io.ByteArrayInputStream(data);
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Not Supported");
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return "ByteArrayDataSource";
        }
    }
}
