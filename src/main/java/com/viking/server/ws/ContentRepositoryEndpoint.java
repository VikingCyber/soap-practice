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
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPElement;

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

            contentService.storeContent(username, request.getName(), request.getContent(), request.getCallbackUrl());

            StoreContentResponse response = objectFactory.createStoreContentResponse();
            response.setMessage("Success");
            return response;
        } finally {
            // Clean up ThreadLocal after request processing
            com.viking.server.security.DatabaseUsernameTokenValidator.clearCurrentUsername();
        }
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
    
    private String extractUsername(MessageContext messageContext) {
        logger.info("Attempting to extract username from MessageContext");
        
        // First, try to get username from ThreadLocal (set by DatabaseUsernameTokenValidator)
        String threadLocalUsername = com.viking.server.security.DatabaseUsernameTokenValidator.getCurrentUsername();
        if (threadLocalUsername != null && !threadLocalUsername.isEmpty()) {
            logger.info("Found username in ThreadLocal: {}", threadLocalUsername);
            return threadLocalUsername;
        }
        
        // Log all properties in MessageContext for debugging
        String[] propertyNames = messageContext.getPropertyNames();
        logger.info("MessageContext has {} properties", propertyNames.length);
        for (String propName : propertyNames) {
            logger.info("MessageContext property: {} = {}", propName, messageContext.getProperty(propName));
        }
        
        // Try to get Principal from MessageContext (set by Wss4jSecurityInterceptor after validation)
        Principal principal = (Principal) messageContext.getProperty("org.springframework.ws.soap.security.user");
        if (principal != null) {
            logger.info("Found Principal in MessageContext: {}", principal.getName());
            return principal.getName();
        }
        
        // Try to get username directly from MessageContext property
        String username = (String) messageContext.getProperty("org.springframework.ws.soap.security.username");
        if (username != null && !username.isEmpty()) {
            logger.info("Found username in MessageContext property: {}", username);
            return username;
        }
        
        // Try using SAAJ API directly
        if (messageContext.getRequest() instanceof SaajSoapMessage) {
            SaajSoapMessage saajMessage = (SaajSoapMessage) messageContext.getRequest();
            try {
                SOAPMessage soapMessage = saajMessage.getSaajMessage();
                SOAPHeader soapHeader = soapMessage.getSOAPHeader();
                if (soapHeader != null) {
                    logger.debug("SOAP header found, searching for Username element");
                    String saajUsername = extractUsernameFromSAAJ(soapHeader);
                    if (saajUsername != null && !saajUsername.isEmpty()) {
                        logger.info("Extracted username using SAAJ API: {}", saajUsername);
                        return saajUsername;
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to extract username using SAAJ API: {}", e.getMessage());
            }
        }
        
        // Fallback: extract from SOAP message using DOM traversal
        if (messageContext.getRequest() instanceof SoapMessage) {
            SoapMessage soapMessage = (SoapMessage) messageContext.getRequest();
            try {
                // Get the SOAP envelope as DOM
                javax.xml.transform.Source source = soapMessage.getEnvelope().getSource();
                if (source instanceof javax.xml.transform.dom.DOMSource) {
                    javax.xml.transform.dom.DOMSource domSource = (javax.xml.transform.dom.DOMSource) source;
                    org.w3c.dom.Node node = domSource.getNode();
                    if (node != null) {
                        org.w3c.dom.Document doc = node.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE 
                            ? (org.w3c.dom.Document) node 
                            : node.getOwnerDocument();
                        if (doc != null) {
                            // Try XPath first
                            try {
                                javax.xml.xpath.XPathFactory xPathFactory = javax.xml.xpath.XPathFactory.newInstance();
                                javax.xml.xpath.XPath xpath = xPathFactory.newXPath();
                                xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                                    @Override
                                    public String getNamespaceURI(String prefix) {
                                        if ("wsse".equals(prefix)) {
                                            return "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
                                        }
                                        return null;
                                    }
                                    
                                    @Override
                                    public String getPrefix(String namespaceURI) {
                                        return null;
                                    }
                                    
                                    @Override
                                    public java.util.Iterator<String> getPrefixes(String namespaceURI) {
                                        return null;
                                    }
                                });
                                String extractedUsername = xpath.evaluate("//wsse:Username", doc);
                                if (extractedUsername != null && !extractedUsername.isEmpty()) {
                                    logger.info("Extracted username using XPath: {}", extractedUsername);
                                    return extractedUsername;
                                }
                            } catch (Exception xpathEx) {
                                logger.debug("XPath extraction failed: {}", xpathEx.getMessage());
                            }
                            
                            // Fallback: use DOM traversal
                            String domUsername = findUsernameInDOM(doc);
                            if (domUsername != null && !domUsername.isEmpty()) {
                                logger.info("Extracted username using DOM traversal: {}", domUsername);
                                return domUsername;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to extract username from SOAP message: {}", e.getMessage(), e);
            }
        }
        
        logger.error("Could not extract username from SOAP message using any method");
        return null;
    }
    
    private String extractUsernameFromSAAJ(SOAPHeader soapHeader) {
        try {
            // Search for Security element
            java.util.Iterator<?> headerElements = soapHeader.getChildElements();
            while (headerElements.hasNext()) {
                Object element = headerElements.next();
                if (element instanceof SOAPElement) {
                    SOAPElement soapElement = (SOAPElement) element;
                    String localName = soapElement.getLocalName();
                    String namespaceURI = soapElement.getNamespaceURI();
                    
                    logger.debug("Found header element: {} in namespace {}", localName, namespaceURI);
                    
                    // Check if this is a Security element
                    if ("Security".equals(localName) && 
                        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd".equals(namespaceURI)) {
                        // Search for UsernameToken
                        java.util.Iterator<?> securityChildren = soapElement.getChildElements();
                        while (securityChildren.hasNext()) {
                            Object child = securityChildren.next();
                            if (child instanceof SOAPElement) {
                                SOAPElement childElement = (SOAPElement) child;
                                if ("UsernameToken".equals(childElement.getLocalName())) {
                                    // Search for Username
                                    java.util.Iterator<?> tokenChildren = childElement.getChildElements();
                                    while (tokenChildren.hasNext()) {
                                        Object tokenChild = tokenChildren.next();
                                        if (tokenChild instanceof SOAPElement) {
                                            SOAPElement usernameElement = (SOAPElement) tokenChild;
                                            if ("Username".equals(usernameElement.getLocalName())) {
                                                String username = usernameElement.getValue();
                                                if (username != null && !username.trim().isEmpty()) {
                                                    return username.trim();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting username from SAAJ header: {}", e.getMessage());
        }
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
