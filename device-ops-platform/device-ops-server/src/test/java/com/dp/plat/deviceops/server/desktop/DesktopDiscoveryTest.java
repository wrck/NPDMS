package com.dp.plat.deviceops.server.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesktopDiscoveryTest {
    @Test void configurationRejectsUnsafeRemoteDestinationsAndPathOrigins() {
        for (String server : new String[] {"ftp://npdms.example", "file:///tmp", "https://u:p@npdms.example",
                "https://npdms.example?token=secret", "https://npdms.example#fragment", "https://npdms.example:0"}) {
            assertThrows(IllegalArgumentException.class, () -> new DesktopProperties(server, "https://web.example"));
        }
        assertThrows(IllegalArgumentException.class, () -> new DesktopProperties("https://npdms.example", "https://web.example/page"));
        assertEquals("http://127.0.0.1:59191", new DesktopProperties("http://127.0.0.1:59191/", "http://localhost:3000").serverUrl());
        var intranet = new DesktopProperties("http://10.210.0.11:59191/", "http://10.210.0.11:19191/");
        assertEquals("http://10.210.0.11:59191", intranet.serverUrl());
        assertEquals("http://10.210.0.11:19191", intranet.webOrigin());
    }

    @Test void statusReportsReadinessAndExplicitEnvironmentWithoutSecrets() throws Exception {
        var availability = mock(ApplicationAvailability.class);
        var identity = mock(DesktopIdentity.class);
        when(identity.instanceId()).thenReturn("instance-1");
        when(identity.publicKey()).thenReturn("public-key");
        when(identity.sign(anyString())).thenReturn(new byte[64]);
        var controller = new DesktopStatusController(new DesktopProperties("https://npdms.example:8443", "https://web.example"), availability, identity);
        var request = new MockHttpServletRequest(); request.setLocalPort(8289);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals("STARTING", controller.status(null, request).getBody().status());
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        var response = controller.status("a".repeat(64), request);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(3, response.getBody().protocolVersion());
        assertEquals(8289, response.getBody().port());
        verify(identity).sign("sdp-dac-discovery-v1\n" + "a".repeat(64) + "\n8289\ninstance-1\n3\nREADY\nhttps://npdms.example:8443\nhttps://web.example");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> controller.status("bad-nonce", request));
    }

    @Test void loopbackHostGuardRejectsRebindingAndNonLoopbackPeers() throws Exception {
        for (String[] attempt : new String[][] {{"attacker.example", "127.0.0.1"}, {"127.0.0.1", "10.0.0.8"}}) {
            var request = new MockHttpServletRequest();
            request.setServerName(attempt[0]);
            request.setRemoteAddr(attempt[1]);
            var response = new MockHttpServletResponse();
            var called = new AtomicBoolean();
            new DesktopDiscoverySecurity.LoopbackHostFilter().doFilter(request, response, (req, res) -> called.set(true));
            assertEquals(403, response.getStatus());
            assertFalse(called.get());
        }
        var request = new MockHttpServletRequest();
        request.setServerName("127.0.0.1");
        request.setRemoteAddr("127.0.0.1");
        var called = new AtomicBoolean();
        new DesktopDiscoverySecurity.LoopbackHostFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertTrue(called.get());
    }
}
