package com.viking.server.ws;


import java.io.File;
import java.io.IOException;


import org.springframework.util.Assert;
import org.springframework.ws.samples.mtom.schema.LoadContentRequest;
import org.springframework.ws.samples.mtom.schema.LoadContentResponse;
import org.springframework.ws.samples.mtom.schema.ObjectFactory;
import org.springframework.ws.samples.mtom.schema.StoreContentRequest;
import org.springframework.ws.samples.mtom.schema.StoreContentResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import com.viking.exception.ValidationException;
import com.viking.exception.ValidationSoapException;
import com.viking.server.service.ContentRepository;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;


@Endpoint
public class ContentRepositoryEndpoint {

    private ContentRepository contentRepository;
    private ObjectFactory objectFactory;

    public ContentRepositoryEndpoint(ContentRepository contentRepository) {
        Assert.notNull(contentRepository, "'imageRepository' must not be null");
        this.contentRepository = contentRepository;
        this.objectFactory = new ObjectFactory();
    }

    @PayloadRoot(localPart = "StoreContentRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public StoreContentResponse storeContent(@RequestPayload StoreContentRequest request) throws IOException {
        try {
            this.contentRepository.storeContent(request.getName(), request.getContent());
        } catch (ValidationException e) {
            throw new ValidationSoapException(e.getMessage());
        }
        StoreContentResponse response = this.objectFactory.createStoreContentResponse();
        response.setMessage("Success");
        return response;
    }

    @PayloadRoot(localPart = "LoadContentRequest", namespace = "http://viking/soap/mtom/lab2025")
    @ResponsePayload
    public LoadContentResponse load(@RequestPayload LoadContentRequest request) throws IOException {
        LoadContentResponse response = this.objectFactory.createLoadContentResponse();
        File contentFile = this.contentRepository.loadContent(request.getName());
        DataHandler dataHandler = new DataHandler(new FileDataSource(contentFile));
        response.setName(request.getName());
        response.setContent(dataHandler);
        return response;
    }
    
}
