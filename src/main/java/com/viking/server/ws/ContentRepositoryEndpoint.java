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
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import jakarta.xml.soap.SOAPMessage;

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
        try {
            String username = extractUsername(messageContext);
            if (username == null) {
                throw new IOException("Username not found in SOAP message");
            }
            logger.info("Authenticated username: {}", username);

            // Get DataHandler - prefer direct extraction from SOAP message for MTOM attachments
            // because JAXB unmarshaller sometimes doesn't properly link XOP references to attachments
            DataHandler content = extractAttachmentFromSoapMessage(messageContext, request);
            if (content == null) {
                // Fallback to unmarshalled DataHandler
                content = request.getContent();
                logger.info("Using DataHandler from unmarshalled request");
            } else {
                logger.info("Using DataHandler extracted directly from SOAP message attachment");
            }
            
            if (content == null) {
                throw new IOException("No attachment found in SOAP message");
            }

            contentService.storeContent(username, request.getName(), content, request.getCallbackUrl());

            StoreContentResponse response = objectFactory.createStoreContentResponse();
            response.setMessage("Success");
            return response;
        } finally {
            // Clean up ThreadLocal after request processing
            com.viking.server.security.DatabaseUsernameTokenValidator.clearCurrentUsername();
        }
    }
    
    private DataHandler extractAttachmentFromSoapMessage(MessageContext messageContext, StoreContentRequest request) {
        if (!(messageContext.getRequest() instanceof SaajSoapMessage saaj)) {
            return null;
        }
        
        try {
            SOAPMessage soapMessage = saaj.getSaajMessage();
            
            // Extract Content-ID from XOP reference in the request
            // The href in XOP:Include looks like "cid:df62fe48-0ea2-401b-bcac-5e49ffa48978@viking"
            String contentId = null;
            if (request.getContent() != null) {
                // Try to get Content-ID from the DataHandler's name/contentType
                // Or we can search for it in attachments
            }
            
            // Search for attachment by Content-ID or just take the first one
            java.util.Iterator<jakarta.xml.soap.AttachmentPart> it = soapMessage.getAttachments();
            if (it.hasNext()) {
                jakarta.xml.soap.AttachmentPart attachment = it.next();
                logger.info("Found attachment with Content-ID: {}", attachment.getContentId());
                return attachment.getDataHandler();
            }
        } catch (Exception e) {
            logger.warn("Failed to extract attachment from SOAP message: {}", e.getMessage());
        }
        
        return null;
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
    public GetLastUploadedFileResponse getLastUploadedFile(@RequestPayload GetLastUploadedFileRequest request,
                                                          MessageContext messageContext) throws IOException {
        String username = extractUsername(messageContext);
        if (username == null) {
            throw new IOException("Username not found in SOAP message");
        }

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
    
    private String extractUsername(MessageContext messageContext) {
        // Username is stored by DatabaseUsernameTokenValidator in ThreadLocal
        String threadLocalUsername = com.viking.server.security.DatabaseUsernameTokenValidator.getCurrentUsername();
        if (threadLocalUsername != null && !threadLocalUsername.isEmpty()) {
            return threadLocalUsername;
        }

        // Fallback to Principal if available
        Principal principal = (Principal) messageContext.getProperty("org.springframework.ws.soap.security.user");
        if (principal != null) {
            return principal.getName();
        }

        // Last attempt: username property (depends on interceptor configuration)
        String username = (String) messageContext.getProperty("org.springframework.ws.soap.security.username");
        if (username != null && !username.isEmpty()) {
            return username;
        }

        logger.warn("Username not available in ThreadLocal or MessageContext properties");
        return null;
    }
    
    private String findUsernameInDOM(org.w3c.dom.Document doc) {
        try {
            // Search for Username element in WS-Security namespace
            org.w3c.dom.NodeList nodeList = doc.getElementsByTagNameNS(
                "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                "Username"
            );
            if (nodeList != null && nodeList.getLength() > 0) {
                org.w3c.dom.Node usernameNode = nodeList.item(0);
                if (usernameNode != null) {
                    String username = usernameNode.getTextContent();
                    if (username != null && !username.trim().isEmpty()) {
                        return username.trim();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("DOM traversal failed: {}", e.getMessage());
        }
        return null;
    }
}
