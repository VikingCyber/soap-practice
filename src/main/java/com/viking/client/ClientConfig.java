package com.viking.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

@Configuration
public class ClientConfig {

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setContextPath("org.springframework.ws.samples.mtom.schema");
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
    public WebServiceTemplate webServiceTemplate(
            SaajSoapMessageFactory messageFactory,
            Jaxb2Marshaller marshaller,
            Wss4jSecurityInterceptor securityInterceptor,
            @Value("${soap.client.uri:http://localhost:8080/mtom-server/services}") String defaultUri) {

        WebServiceTemplate template = new WebServiceTemplate(messageFactory);
        template.setDefaultUri(defaultUri);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setInterceptors(new ClientInterceptor[]{securityInterceptor});
        return template;
    }
}
