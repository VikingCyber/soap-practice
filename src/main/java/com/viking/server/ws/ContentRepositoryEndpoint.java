package com.viking.server.ws;

import com.viking.client.ws.*;
import com.viking.server.entity.UploadedFile;
import com.viking.server.service.ContentService;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Endpoint
public class ContentRepositoryEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(ContentRepositoryEndpoint.class);

    private final ContentService contentService;
    private final ObjectFactory objectFactory;

    public ContentRepositoryEndpoint(ContentService contentService) {
        if (contentService == null) {
            throw new IllegalArgumentException("'contentRepository' must not be null");
        }
        this.contentService = contentService;
        this.objectFactory = new ObjectFactory();
    }

    @PayloadRoot(localPart = "StoreContentRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public StoreContentResponse storeContent(@RequestPayload StoreContentRequest request,
                                            MessageContext messageContext) throws IOException {
        String username = (String) messageContext.getProperty(Wss4jSecurityInterceptor.SECUREMENT_USER_PROPERTY_NAME);
        logger.info("Authenticated username: {}", username);
        if (username == null) {
            throw new IOException("Username not found in SOAP message");
        }

        contentService.storeContent(username, request.getName(), request.getContent(), request.getCallbackUrl());

        StoreContentResponse response = objectFactory.createStoreContentResponse();
        response.setMessage("Success");
        return response;
    }

    @PayloadRoot(localPart = "LoadContentRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public LoadContentResponse load(@RequestPayload LoadContentRequest request) {
        LoadContentResponse response = objectFactory.createLoadContentResponse();
        File contentFile = contentService.loadContent(request.getName());
        DataHandler dataHandler = new DataHandler(new FileDataSource(contentFile));
        response.setName(request.getName());
        response.setContent(dataHandler);
        return response;
    }

    @PayloadRoot(localPart = "GetUptimeRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public GetUptimeResponse getUptime() {
        GetUptimeResponse response = objectFactory.createGetUptimeResponse();
        Instant startTime = contentService.getServerStartTime();
        long uptimeSeconds = contentService.getUptimeSeconds(startTime);
        response.setUptimeSeconds(uptimeSeconds);
        response.setStartTime(startTime.toString());
        return response;
    }

    @PayloadRoot(localPart = "GetLastUploadedFileRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public GetLastUploadedFileResponse getLastUploadedFile(Principal principal) {
        String username = principal.getName();
        GetLastUploadedFileResponse response = objectFactory.createGetLastUploadedFileResponse();

        UploadedFile file = contentService.getLastUploadedFile(username);
        if (file != null) {
            response.setFilename(file.getFilename());
            response.setSizeBytes(file.getSizeBytes());
            if (file.getUploadTime() != null) {
                response.setUploadTime(file.getUploadTime().format(DateTimeFormatter.ISO_DATE_TIME));
            }
            response.setStatus(file.getStatus());
            response.setErrorMessage(file.getErrorMessage());
        }

        return response;
    }

    @PayloadRoot(localPart = "GetFilesCsvRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public GetFilesCsvResponse getFilesCsv() {
        GetFilesCsvResponse response = objectFactory.createGetFilesCsvResponse();
        response.setCsvContent(contentService.getFilesCsv());
        return response;
    }
}
