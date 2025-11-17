package com.viking.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viking.exception.ValidationException;

import jakarta.activation.DataHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ContentRepositoryImplTest {

    private ContentRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ContentRepositoryImpl();
        repository.fileStorePath = System.getProperty("java.io.tmpdir");
    }

    @Test
    void testValidFile() throws Exception {
        String name = "goodFile";
        byte[] data = "Hello World".getBytes();
        DataHandler handler = new DataHandler(new ByteArrayDataSource(data));

        repository.storeContent(name, handler);

        assertTrue(repository.loadContent(name).exists());
    }

    @Test
    void testFileNameValidation() {
        String name = "плохойЖ";
        byte[] data = "Hello".getBytes();
        DataHandler handler = new DataHandler(new ByteArrayDataSource(data));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> repository.storeContent(name, handler));

        assertTrue(ex.getMessage().contains("forbidden letter"));
    }

    @Test
    void testFileSizeExceeded() {
        String name = "bigFile";
        byte[] data = new byte[(int) (ContentRepositoryImpl.MAX_FILE_SIZE + 1)];
        DataHandler handler = new DataHandler(new ByteArrayDataSource(data));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> repository.storeContent(name, handler));

        assertTrue(ex.getMessage().contains("File exceeded"));
    }

    @Test
    void testJsonValidation() {
        String name = "jsonFile";
        byte[] data = "{\"key\":123}".getBytes();
        DataHandler handler = new DataHandler(new ByteArrayDataSource(data));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> repository.storeContent(name, handler));

        assertTrue(ex.getMessage().contains("valid JSON"));
    }

    @Test
    void testVirtualQuotaExceeded() throws Exception {
        byte[] data = new byte[3 * 1024 * 1024];
        DataHandler handler1 = new DataHandler(new ByteArrayDataSource(data));
        DataHandler handler2 = new DataHandler(new ByteArrayDataSource(data));
        DataHandler handler3 = new DataHandler(new ByteArrayDataSource(data));
        DataHandler handler4 = new DataHandler(new ByteArrayDataSource(data));

        repository.storeContent("file1", handler1);
        repository.storeContent("file2", handler2);
        repository.storeContent("file3", handler3);

        ValidationException ex = assertThrows(ValidationException.class,
            () -> repository.storeContent("file4", handler4));
        assertTrue(ex.getMessage().contains("Storage quota exceeded"));
    }

    // вспомогательный класс
    static class ByteArrayDataSource implements jakarta.activation.DataSource {
        private final byte[] data;

        ByteArrayDataSource(byte[] data) { this.data = data; }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(data); }

        @Override
        public OutputStream getOutputStream() { throw new UnsupportedOperationException(); }

        @Override
        public String getContentType() { return "application/octet-stream"; }

        @Override
        public String getName() { return "ByteArrayDataSource"; }
    }
}
