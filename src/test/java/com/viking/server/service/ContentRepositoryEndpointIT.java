package com.viking.server.service;

import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.*;



import org.junit.jupiter.api.Test;


class ContentRepositoryEndpointIT {

    private MockWebServiceClient client;

    @Test
    void testValidationFault() throws Exception {
        String request = "<StoreContentRequest xmlns='http://viking/soap/mtom/lab2025'>"
                + "<name>плохойЖ</name>"
                + "<content>VGhpcyBpcyBhIHRlc3Q=</content>"
                + "</StoreContentRequest>";

        client.sendRequest(withPayload(new StringSource(request)))
              .andExpect(clientOrSenderFault());
    }
}
