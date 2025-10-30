package com.viking.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.util.StopWatch;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.samples.mtom.schema.LoadContentRequest;
import org.springframework.ws.samples.mtom.schema.LoadContentResponse;
import org.springframework.ws.samples.mtom.schema.ObjectFactory;
import org.springframework.ws.samples.mtom.schema.StoreContentRequest;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;

import jakarta.activation.DataHandler;

@SpringBootApplication
public class SaajMtomClient extends WebServiceGatewaySupport{
    public static void main(String[] args) {
        SpringApplication.run(SaajMtomClient.class, args);
    }

    @Bean
    CommandLineRunner invoke(SaajMtomClient saajClient) {
        return args -> {
            saajClient.storeContent();
            saajClient.loadContent();
        };
    }

    private static final Logger logger = LoggerFactory.getLogger(SaajMtomClient.class);
    private ObjectFactory objectFactory = new ObjectFactory();
    private StopWatch stopWatch = new StopWatch(ClassUtils.getShortName(getClass()));
    public SaajMtomClient(SaajSoapMessageFactory messageFactory) {
        super(messageFactory);
    }

    public void storeContent() {
        StoreContentRequest storeContentRequest = this.objectFactory.createStoreContentRequest();
        storeContentRequest.setName("SampleFile");
        storeContentRequest.setContent(new DataHandler(Thread.currentThread().getContextClassLoader().getResource("Normandi.jpg")));
        this.stopWatch.start("store");
        getWebServiceTemplate().marshalSendAndReceive(storeContentRequest);
        this.stopWatch.stop();
        logger.info(this.stopWatch.prettyPrint());
    }

    public void loadContent() throws IOException {
        LoadContentRequest loadContentRequest = this.objectFactory.createLoadContentRequest();
        loadContentRequest.setName("SampleFile");
        String tmpDir = System.getProperty("java.io.tmpdir");
        File out = new File(tmpDir, "Normandi.bin");
        long freeBefore = Runtime.getRuntime().freeMemory();
        this.stopWatch.start("load");
        LoadContentResponse loadContentResponse = (LoadContentResponse) getWebServiceTemplate()
                .marshalSendAndReceive(loadContentRequest);
        this.stopWatch.stop();
        DataHandler content = loadContentResponse.getContent();
        long freeAfter = Runtime.getRuntime().freeMemory();
        logger.info("Memory usage [kB]: " + ((freeAfter - freeBefore) / 1024));
        this.stopWatch.start("loadAttachmentContent");
        long size = saveContentToFile(content, out);
        this.stopWatch.stop();
        logger.info("Received file size [kB]: " + size);
        logger.info("Stored at " + out.getAbsolutePath());
        logger.info(this.stopWatch.prettyPrint());
    }

    private static long saveContentToFile(DataHandler content, File outFile) throws IOException {
        long size = 0;
        byte[] buffer = new byte[1024];
        try (InputStream in = content.getInputStream()) {
            try (OutputStream out = new FileOutputStream(outFile)) {
                for (int readBytes; (readBytes = in.read(buffer, 0, buffer.length)) > 0;) {
                    size += readBytes;
                    out.write(buffer, 0, readBytes);
                }
            }
        }
        return size;
    }
}
