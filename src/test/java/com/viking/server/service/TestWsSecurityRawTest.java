package com.viking.server.service;


import org.junit.jupiter.api.Test;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

public class TestWsSecurityRawTest {

    @Test
    public void testUsernameTokenHeader() throws Exception {

        // --- SOAP FACTORY ---
        SaajSoapMessageFactory messageFactory = new SaajSoapMessageFactory();
        messageFactory.afterPropertiesSet();

        // --- MARSHALLER ---
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.viking.client.ws"); // ПОМЕНЯЙ НА СВОЙ ПАКЕТ
        marshaller.afterPropertiesSet();

        // --- WS-SECURITY ---
        Wss4jSecurityInterceptor sec = new Wss4jSecurityInterceptor();
        sec.setSecurementActions("UsernameToken");
        sec.setSecurementUsername("admin");
        sec.setSecurementPassword("secret");
        sec.setSecurementPasswordType("PasswordText");
        sec.afterPropertiesSet();

        // --- LOGGING INTERCEPTOR ---
        ClientInterceptor logger = new ClientInterceptor() {
            @Override
            public boolean handleRequest(MessageContext messageContext) {
                try {
                    SoapMessage msg = (SoapMessage) messageContext.getRequest();

                    Transformer transformer = TransformerFactory.newInstance().newTransformer();
                    transformer.transform(
                            msg.getEnvelope().getSource(),
                            new StreamResult(System.out)
                    );

                    System.out.println("\n===== END OF SOAP REQUEST =====\n");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }

            @Override public boolean handleResponse(MessageContext m) { return true; }
            @Override public boolean handleFault(MessageContext m) { return true; }

            @Override
            public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'afterCompletion'");
            }
        };

        // --- TEMPLATE ---
        WebServiceTemplate template = new WebServiceTemplate(messageFactory);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setInterceptors(new ClientInterceptor[]{sec, logger});

        // Создаём пустой объект как тело
        Object dummyRequest = new Object() {};

        try {
            template.marshalSendAndReceive("http://localhost:9999/nowhere", dummyRequest);
        } catch (Exception ignore) {
            // сервер не нужен
        }
    }
}
