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

    @Test void statusReportsReadinessAndExplicitEnvironmentWithoutSecrets() {
        var availability = mock(ApplicationAvailability.class);
        var controller = new DesktopStatusController(new DesktopProperties("https://npdms.example:8443", "https://web.example"), availability);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals("STARTING", controller.status().getBody().status());
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        var response = controller.status();
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(new DesktopStatusController.Status("npdms-dac", 1, "READY", "https://npdms.example:8443", "https://web.example"), response.getBody());
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
