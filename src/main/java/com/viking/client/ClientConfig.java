package com.viking.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.wss4j.dom.WSConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.interceptor.PayloadLoggingInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

import jakarta.xml.soap.AttachmentPart;
import jakarta.xml.soap.SOAPMessage;

@Configuration
public class ClientConfig {

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setContextPath("com.viking.client.ws");
        m.setMtomEnabled(true);
        return m;
    }

    @Bean
    public SaajSoapMessageFactory saajSoapMessageFactory() {
        SaajSoapMessageFactory factory = new SaajSoapMessageFactory();
		factory.setSoapVersion(SoapVersion.SOAP_11);
		factory.afterPropertiesSet();
		return factory;
    }

    @Bean
    public Wss4jSecurityInterceptor clientSecurityInterceptor() {
        Wss4jSecurityInterceptor interceptor = new Wss4jSecurityInterceptor();
        interceptor.setSecurementActions("UsernameToken");
        interceptor.setSecurementUsername("admin");
        interceptor.setSecurementPassword("secret");
        interceptor.setSecurementPasswordType("PasswordText");
        return interceptor;
    }

    @Bean
    public ClientInterceptor soapLoggingInterceptor() {
        return new ClientInterceptorAdapter() {

            @Override
            public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
                logSoapMessage("Request", messageContext.getRequest());
                return true;
            }

            @Override
            public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
                logSoapMessage("Response", messageContext.getResponse());
                return true;
            }

            @Override
            public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
                logSoapMessage("Fault", messageContext.getResponse());
                return true;
            }

            private void logSoapMessage(String type, WebServiceMessage message) {
                if (!(message instanceof SaajSoapMessage saajMessage)) return;

                try {
                    SOAPMessage soapMessage = saajMessage.getSaajMessage();

                    // Логируем только XML (само тело запроса/ответа)
                    TransformerFactory tf = TransformerFactory.newInstance();
                    Transformer transformer = tf.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    transformer.transform(soapMessage.getSOAPPart().getContent(), new StreamResult(out));

                    System.out.println("=== SOAP " + type + " ===");
                    System.out.println(out.toString(StandardCharsets.UTF_8));
                    System.out.println("=== End of " + type + " ===");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Bean
    public WebServiceTemplate webServiceTemplate(
            SaajSoapMessageFactory messageFactory,
            Jaxb2Marshaller marshaller,
            Wss4jSecurityInterceptor securityInterceptor,
            @Value("${soap.client.uri:http://localhost:8080/mtom-server/services}") String defaultUri) {

        WebServiceTemplate template = new WebServiceTemplate(messageFactory);
        template.setDefaultUri(defaultUri);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setInterceptors(new ClientInterceptor[]{securityInterceptor, soapLoggingInterceptor()});
        template.afterPropertiesSet();
        return template;
    }
}
