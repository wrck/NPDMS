package com.dp.deviceops.adapter.web.callback;

import com.dp.deviceops.core.port.CallbackOutboxPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NpdmsCallbackSenderTest {
    @Test void failureCategoriesPreserveOnlySafeCodes() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("AUTH_FAILED",NpdmsCallbackSender.failureCategory(json.readTree("{\"status\":\"FAILED\",\"outcome\":\"AUTH_FAILED\"}")));
        assertEquals("COMMAND_REJECTED",NpdmsCallbackSender.failureCategory(json.readTree("{\"status\":\"FAILED\",\"outcome\":\"COMMAND_REJECTED\"}")));
        assertEquals("EXECUTION_FAILED",NpdmsCallbackSender.failureCategory(json.readTree("{\"status\":\"FAILED\",\"outcome\":\"untrusted device text\"}")));
        assertNull(NpdmsCallbackSender.failureCategory(json.readTree("{\"status\":\"SUCCEEDED\"}")));
        assertEquals("OUTPUT_TRUNCATED",NpdmsCallbackSender.failureCategory(json.readTree("{\"status\":\"SUCCEEDED\",\"truncated\":true}")));
    }
    @Test void deliveryRequiresMatchingApplicationAcknowledgement() throws Exception {
        var properties = new CallbackProperties();
        properties.setNpdmsDestination("http://127.0.0.1/callback");
        properties.setNpdmsSigningKey("test-only-callback-signature-material");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = builder.build();
        var event = new CallbackOutboxPort.Event("a".repeat(64), properties.getNpdmsDestination(),
                "{\"namespace\":\"npdms-7\",\"externalRequestId\":\"task\",\"collectionId\":\"dac\",\"status\":\"SUCCEEDED\",\"stdout\":\"evidence\"}", 0);
        server.expect(requestTo(properties.getNpdmsDestination())).andExpect(header("tenant-id", "7"))
                .andExpect(header("X-DAC-Nonce", event.eventId()))
                .andRespond(withSuccess("{\"code\":500,\"msg\":\"failed\"}", MediaType.APPLICATION_JSON));
        assertFalse(NpdmsCallbackSender.send(event, properties, client));
        server.verify();
        server.reset();
        server.expect(requestTo(properties.getNpdmsDestination())).andRespond(withSuccess(
                "{\"status\":\"ACKNOWLEDGED\",\"receiptId\":3,\"callbackId\":\"" + event.eventId() + "\"}", MediaType.APPLICATION_JSON));
        assertTrue(NpdmsCallbackSender.send(event, properties, client));
        server.verify();
    }
}
