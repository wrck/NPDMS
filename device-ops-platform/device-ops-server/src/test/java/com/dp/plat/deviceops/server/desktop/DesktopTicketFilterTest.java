package com.dp.plat.deviceops.server.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesktopTicketFilterTest {
    private final DesktopServerClient server = mock(DesktopServerClient.class);
    private final DesktopTicketFilter filter = new DesktopTicketFilter(server,
            new DesktopProperties("https://npdms.example", "https://web.example"));

    @Test void unrelatedBrowserOriginCannotResolveATicket() throws Exception {
        var request = request("https://attacker.example");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a,b) -> fail("must not reach controller"));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(server);
    }

    @Test void serverGrantIsBoundToOneProjectAndAuthenticationIsCleared() throws Exception {
        when(server.resolve(any())).thenReturn(new DesktopServerClient.Grant(1, 42, "7", "npdms-1", "TASK_READ", "task-1", null, null, null));
        var request = request("https://web.example");
        var called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> {
            called.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("42", authentication.getName());
            assertEquals("SCOPE_device-ops:collections:read", authentication.getAuthorities().iterator().next().getAuthority());
            assertNotNull(request.getAttribute(DesktopTicketFilter.GRANT));
        });
        assertTrue(called.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(DesktopTicketFilter.GRANT));
    }

    @Test void unreachableServerNeverFallsBackToLocalDebugAuthentication() throws Exception {
        when(server.resolve(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));
        var response = new MockHttpServletResponse();
        filter.doFilter(request("https://web.example"), response, (a,b) -> fail("must not execute offline without a grant"));
        assertEquals(503, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String origin) {
        var request = new MockHttpServletRequest("GET", "/api/v1/desktop/tasks/task-1");
        request.addHeader("Origin", origin);
        request.addHeader("X-NPDMS-DAC-Ticket", "short-lived-ticket");
        request.addHeader("tenant-id", "1");
        return request;
    }
}
