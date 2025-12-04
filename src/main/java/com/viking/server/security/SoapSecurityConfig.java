package com.viking.server.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

import com.viking.server.repository.UserRepository;

@EnableWs
@Configuration
public class SoapSecurityConfig implements WsConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(SoapSecurityConfig.class);

    private final UserRepository userRepository;

    public SoapSecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public DatabaseUsernameTokenValidator databaseUsernameTokenValidator() {
        return new DatabaseUsernameTokenValidator(userRepository, passwordEncoder());
    }

    @Bean
    public Wss4jSecurityInterceptor securityInterceptor() {
        Wss4jSecurityInterceptor interceptor = new Wss4jSecurityInterceptor();
        interceptor.setValidationActions("UsernameToken");
        interceptor.setValidationCallbackHandler(databaseUsernameTokenValidator());
        return interceptor;
    }

    /**
     * Вспомогательный перехватчик для диагностики MTOM-вложений на сервере.
     * Не трогает тело сообщения, только считает количество attachment'ов
     * и их заявленный размер через SAAJ API.
     */
    @Bean
    public EndpointInterceptor attachmentLoggingInterceptor() {
        return new EndpointInterceptor() {
            @Override
            public boolean handleRequest(MessageContext messageContext, Object endpoint) {
                WebServiceMessage message = messageContext.getRequest();
                if (message instanceof SaajSoapMessage saaj) {
                    try {
                        jakarta.xml.soap.SOAPMessage soapMessage = saaj.getSaajMessage();

                        int count = 0;
                        long totalSize = 0;

                        @SuppressWarnings("unchecked")
                        java.util.Iterator<jakarta.xml.soap.AttachmentPart> it =
                                soapMessage.getAttachments();
                        while (it.hasNext()) {
                            jakarta.xml.soap.AttachmentPart part = it.next();
                            count++;
                            int size = part.getSize(); // может вернуть -1, это тоже важно увидеть
                            totalSize += Math.max(size, 0);
                        }

                        logger.info("Server SAAJ attachments: count={}, declaredTotalSize={} bytes",
                                count, totalSize);
                    } catch (Exception e) {
                        logger.warn("Failed to inspect incoming SOAP attachments: {}", e.getMessage());
                    }
                }
                return true;
            }

            @Override
            public boolean handleResponse(MessageContext messageContext, Object endpoint) {
                return true;
            }

            @Override
            public boolean handleFault(MessageContext messageContext, Object endpoint) {
                return true;
            }

            @Override
            public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) {
                // no-op
            }
        };
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(securityInterceptor());
        interceptors.add(attachmentLoggingInterceptor());
    }
}

