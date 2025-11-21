package com.viking.client;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.viking.client.ws.GetLastUploadedFileResponse;


@SpringBootApplication(
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
    }
)
public class ClientApp {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientApp.class);
    private static final int CALLBACK_TIMEOUT_SECONDS = 10;

    public static void main(String[] args) {
        SpringApplication.run(ClientApp.class, args);
    }

    @Bean
    CommandLineRunner runner(SaajMtomClient client, UploadStatusCallbackController callbackController) {
        return args -> {
            Scanner scanner = new Scanner(System.in);
            
            // Проверка доступности сервера
            logger.info("Checking server availability...");
            if (!client.checkServerAvailability()) {
                logger.error("Server is not available. Exiting.");
                return;
            }
            showMenu(client, callbackController, scanner);

            
            // // Если файл передан как аргумент
            // if (args.length > 0 && args[0] != null) {
            //     File file = new File(args[0]);
            //     uploadFile(client, callbackController, file);
            // } else {
            //     // Интерактивное меню
            //     showMenu(client, callbackController, scanner);
            // }
        };
    }
    
    private static void uploadFile(SaajMtomClient client, UploadStatusCallbackController callbackController, File file) {
        logger.info("Uploading file: {}", file.getAbsolutePath());
        callbackController.clearStatus();
        
        if (!client.storeContent(file)) {
            logger.error("Failed to initiate upload");
            return;
        }
        
        // Ждем callback или делаем проактивную проверку
        waitForCallbackOrProactiveCheck(client, callbackController, file.getName());
    }
    
    private static void waitForCallbackOrProactiveCheck(SaajMtomClient client, 
                                                        UploadStatusCallbackController callbackController,
                                                        String filename) {
        logger.info("Waiting for callback (timeout: {} seconds)...", CALLBACK_TIMEOUT_SECONDS);
        
        long startTime = System.currentTimeMillis();
        long timeoutMillis = TimeUnit.SECONDS.toMillis(CALLBACK_TIMEOUT_SECONDS);
        
        // Ожидание callback
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            UploadStatusCallbackController.UploadStatusResponse status = callbackController.getLastStatus();
            if (status != null && filename.equals(status.filename)) {
                logger.info("Received callback: status={}, errorMessage={}", 
                        status.status, status.errorMessage);
                displayUploadResult(status);
                return;
            }
            
            try {
                Thread.sleep(500); // Проверяем каждые 500мс
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // Callback не пришел - проактивная проверка
        logger.warn("Callback not received within {} seconds. Performing proactive check...", CALLBACK_TIMEOUT_SECONDS);
        performProactiveCheck(client, filename);
    }
    
    private static void performProactiveCheck(SaajMtomClient client, String filename) {
        GetLastUploadedFileResponse response = client.getLastUploadedFile();
        if (response != null && filename.equals(response.getFilename())) {
            logger.info("Proactive check result:");
            logger.info("  Filename: {}", response.getFilename());
            logger.info("  Status: {}", response.getStatus());
            logger.info("  Size: {} bytes", response.getSizeBytes());
            logger.info("  Upload time: {}", response.getUploadTime());
            if (response.getErrorMessage() != null) {
                logger.warn("  Error: {}", response.getErrorMessage());
            }
        } else {
            logger.warn("Proactive check: file '{}' not found in last uploaded files", filename);
        }
    }
    
    private static void displayUploadResult(UploadStatusCallbackController.UploadStatusResponse status) {
        if ("SUCCESS".equals(status.status)) {
            logger.info("✓ Upload successful: {}", status.filename);
        } else {
            logger.error("✗ Upload failed: {} - {}", status.filename, status.errorMessage);
        }
    }
    
    private static void showMenu(SaajMtomClient client, UploadStatusCallbackController callbackController, Scanner scanner) {
        while (true) {
            System.out.println("\n=== SOAP Client Menu ===");
            System.out.println("1. Upload file");
            System.out.println("2. Check last uploaded file");
            System.out.println("3. Get all files (CSV)");
            System.out.println("4. Check server availability");
            System.out.println("0. Exit");
            System.out.print("Select option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    System.out.print("Enter file path: ");
                    String filePath = scanner.nextLine().trim();
                    File file = new File(filePath);
                    if (file.exists() && file.isFile()) {
                        uploadFile(client, callbackController, file);
                    } else {
                        logger.error("File not found: {}", filePath);
                    }
                    break;
                    
                case "2":
                    GetLastUploadedFileResponse lastFile = client.getLastUploadedFile();
                    if (lastFile != null && lastFile.getFilename() != null) {
                        System.out.println("\nLast uploaded file:");
                        System.out.println("  Filename: " + lastFile.getFilename());
                        System.out.println("  Size: " + lastFile.getSizeBytes() + " bytes");
                        System.out.println("  Status: " + lastFile.getStatus());
                        System.out.println("  Upload time: " + lastFile.getUploadTime());
                        if (lastFile.getErrorMessage() != null) {
                            System.out.println("  Error: " + lastFile.getErrorMessage());
                        }
                    } else {
                        System.out.println("No files uploaded yet.");
                    }
                    break;
                    
                case "3":
                    String csv = client.getFilesCsv();
                    if (csv != null) {
                        System.out.println("\nAll files on server (CSV):");
                        System.out.println(csv);
                    } else {
                        logger.error("Failed to get files CSV");
                    }
                    break;
                    
                case "4":
                    client.checkServerAvailability();
                    break;
                    
                case "0":
                    System.out.println("Exiting...");
                    return;
                    
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }
}
