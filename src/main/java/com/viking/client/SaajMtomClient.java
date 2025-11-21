package com.viking.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.ws.client.core.WebServiceTemplate;

import com.viking.client.ws.GetFilesCsvRequest;
import com.viking.client.ws.GetFilesCsvResponse;
import com.viking.client.ws.GetLastUploadedFileRequest;
import com.viking.client.ws.GetLastUploadedFileResponse;
import com.viking.client.ws.GetUptimeRequest;
import com.viking.client.ws.GetUptimeResponse;
import com.viking.client.ws.LoadContentRequest;
import com.viking.client.ws.LoadContentResponse;
import com.viking.client.ws.ObjectFactory;
import com.viking.client.ws.StoreContentRequest;
import com.viking.client.ws.StoreContentResponse;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;

@Component
public class SaajMtomClient {

    private static final Logger logger = LoggerFactory.getLogger(SaajMtomClient.class);

    private final WebServiceTemplate webServiceTemplate;
    private final ObjectFactory objectFactory = new ObjectFactory();
    private final StopWatch stopWatch = new StopWatch(getClass().getSimpleName());
    private final String clientCallbackUrl;

    public SaajMtomClient(WebServiceTemplate webServiceTemplate,
                         @Value("${soap.client.callback-url:http://localhost:8081/upload-status}") String clientCallbackUrl) {
        this.webServiceTemplate = webServiceTemplate;
        this.clientCallbackUrl = clientCallbackUrl;
    }

    /**
     * Проверка доступности сервера через GetUptime
     */
    public boolean checkServerAvailability() {
        try {
            GetUptimeRequest request = objectFactory.createGetUptimeRequest();
            GetUptimeResponse response = (GetUptimeResponse) webServiceTemplate.marshalSendAndReceive(request);
            logger.info("Server is available. Uptime: {} seconds, Started at: {}", 
                    response.getUptimeSeconds(), response.getStartTime());
            return true;
        } catch (Exception e) {
            logger.error("Server is not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Загрузка файла на сервер с callback
     */
    public boolean storeContent(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            logger.error("File does not exist or is not a file: {}", file);
            return false;
        }
        
        try {
            StoreContentRequest storeContentRequest = objectFactory.createStoreContentRequest();
            storeContentRequest.setName(file.getName());
            storeContentRequest.setContent(new DataHandler(new FileDataSource(file)));
            storeContentRequest.setCallbackUrl(clientCallbackUrl);

            stopWatch.start("store");
            try {
                StoreContentResponse response = (StoreContentResponse) webServiceTemplate.marshalSendAndReceive(storeContentRequest);
                logger.info("File upload initiated. Server response: {}", response.getMessage());
                return true;
            } finally {
                if (stopWatch.isRunning()) {
                    stopWatch.stop();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to store content", e);
            return false;
        }
    }
    
    /**
     * Получить информацию о последнем загруженном файле текущего пользователя
     */
    public GetLastUploadedFileResponse getLastUploadedFile() {
        try {
            GetLastUploadedFileRequest request = objectFactory.createGetLastUploadedFileRequest();
            GetLastUploadedFileResponse response = 
                    (GetLastUploadedFileResponse) webServiceTemplate.marshalSendAndReceive(request);
            
            if (response.getFilename() != null) {
                logger.info("Last uploaded file: {}, size: {} bytes, status: {}", 
                        response.getFilename(), response.getSizeBytes(), response.getStatus());
                if (response.getErrorMessage() != null) {
                    logger.warn("Error message: {}", response.getErrorMessage());
                }
            } else {
                logger.info("No files found for current user");
            }
            
            return response;
        } catch (Exception e) {
            logger.error("Failed to get last uploaded file", e);
            return null;
        }
    }
    
    /**
     * Получить CSV-выгрузку всех файлов на сервере
     */
    public String getFilesCsv() {
        try {
            GetFilesCsvRequest request = objectFactory.createGetFilesCsvRequest();
            GetFilesCsvResponse response = (GetFilesCsvResponse) webServiceTemplate.marshalSendAndReceive(request);
            return response.getCsvContent();
        } catch (Exception e) {
            logger.error("Failed to get files CSV", e);
            return null;
        }
    }

    public void loadContent() throws IOException {
        LoadContentRequest request = objectFactory.createLoadContentRequest();
        request.setName("SampleFile");

        String tmpDir = System.getProperty("java.io.tmpdir");
        File out = new File(tmpDir, "Normandi.bin");

        long freeBefore = Runtime.getRuntime().freeMemory();

        stopWatch.start("load");
        LoadContentResponse response =
                (LoadContentResponse) webServiceTemplate.marshalSendAndReceive(request);
        stopWatch.stop();

        DataHandler content = response.getContent();
        long freeAfter = Runtime.getRuntime().freeMemory();

        logger.info("Memory usage [kB]: " + ((freeAfter - freeBefore) / 1024));

        stopWatch.start("saveAttachment");
        long size = saveContentToFile(content, out);
        stopWatch.stop();

        logger.info("Received file size [kB]: " + size);
        logger.info("Stored at " + out.getAbsolutePath());
        logger.info(stopWatch.prettyPrint());
    }

    private static long saveContentToFile(DataHandler content, File outFile) throws IOException {
        long size = 0;
        byte[] buffer = new byte[1024];

        try (InputStream in = content.getInputStream();
             OutputStream out = new FileOutputStream(outFile)) {

            for (int readBytes; (readBytes = in.read(buffer)) > 0;) {
                size += readBytes;
                out.write(buffer, 0, readBytes);
            }
        }

        return size;
    }
}

