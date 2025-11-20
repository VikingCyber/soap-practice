package com.viking.server.service; // ВАЖНО: тот же package!

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.activation.DataHandler;
import jakarta.xml.bind.ValidationException;

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

    @Test
    void testValidateFileName_WithForbiddenLetter() {
        assertThrows(ValidationException.class, () -> {
            contentRepository.validateFileName("файлЖ.txt");
        });
    }

    @Test
    void testValidateFileName_WithoutForbiddenLetter() throws ValidationException {
        assertDoesNotThrow(() -> {
            contentRepository.validateFileName("normal_file.txt");
        });
    }

    @Test
    void testValidateFileSize_EmptyFile() {
        DataHandler emptyDataHandler = createDataHandler(new byte[0]);
        assertThrows(ValidationException.class, () -> {
            contentRepository.validateFileSize(emptyDataHandler);
        });
    }

    @Test
    void testValidateFileSize_ValidFile() throws IOException, ValidationException {
        byte[] validData = "Some valid content".getBytes();
        DataHandler validDataHandler = createDataHandler(validData);
        long size = contentRepository.validateFileSize(validDataHandler);
        assertEquals(validData.length, size);
    }

    @Test
    void testValidateNotJSON_WithValidJSON() throws IOException {
        String json = "{\"name\":\"test\",\"value\":123}";
        DataHandler jsoDataHandler = createDataHandler(json.getBytes());
        assertThrows(RuntimeException.class, () -> {
            contentRepository.validateNotJSON(jsoDataHandler);
        });
    }

    @Test 
    void testValidateNotJSON_WithInvalidJSON() throws IOException {
        String invalidJson = "This is not JSON";
        DataHandler nonJsonDataHandler = createDataHandler(invalidJson.getBytes());
        assertDoesNotThrow(() -> {
            contentRepository.validateNotJSON(nonJsonDataHandler);
        });
    }

    @Test
    void testValidateNotJSON_WithJSONArray() throws IOException {
        String jsonArray = "[1, 2, 3]";
        DataHandler handler = createDataHandler(jsonArray.getBytes());
        
        assertThrows(RuntimeException.class, () -> {
            contentRepository.validateNotJSON(handler);
        });
    }

    // @Test
    // void testValidateDiskSpace_WhenNotEnoughSpace() throws Exception {
    //     ContentRepositoryImpl repo = new ContentRepositoryImpl();
    //     Field field = ContentRepositoryImpl.class.getDeclaredField("fieldStorePath");
    //     field.setAccessible(true);
    //     field.set(repo, tempDir.toString());
        
    // }
}
