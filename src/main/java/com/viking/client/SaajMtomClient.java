package com.viking.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.samples.mtom.schema.LoadContentRequest;
import org.springframework.ws.samples.mtom.schema.LoadContentResponse;
import org.springframework.ws.samples.mtom.schema.ObjectFactory;
import org.springframework.ws.samples.mtom.schema.StoreContentRequest;

import jakarta.activation.DataHandler;

@Component
public class SaajMtomClient {

    private static final Logger logger = LoggerFactory.getLogger(SaajMtomClient.class);

    private final WebServiceTemplate webServiceTemplate;
    private final ObjectFactory objectFactory = new ObjectFactory();
    private final StopWatch stopWatch = new StopWatch(getClass().getSimpleName());

    public SaajMtomClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public void storeContent() {
        try {
            StoreContentRequest storeContentRequest =
                    objectFactory.createStoreContentRequest();

            storeContentRequest.setName("SampleFile");
            storeContentRequest.setContent(
                    new DataHandler(
                            Thread.currentThread()
                                  .getContextClassLoader()
                                  .getResource("Normandi.jpg"))
            );

            stopWatch.start("store");
            try {
                webServiceTemplate.marshalSendAndReceive(storeContentRequest);
            } finally {
                if (stopWatch.isRunning()) {
                    stopWatch.stop();
                }
            }

            logger.info(stopWatch.prettyPrint());
        } catch (Exception e) {
            logger.error("Failed to store content", e);
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

