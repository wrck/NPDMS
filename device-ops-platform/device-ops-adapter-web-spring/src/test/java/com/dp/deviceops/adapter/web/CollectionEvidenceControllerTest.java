package com.dp.deviceops.adapter.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionEvidenceControllerTest {
    @Test void exposesDedicatedReadScopedRoutes() throws Exception {
        var type = assertDoesNotThrow(() -> Class.forName("com.dp.deviceops.adapter.web.CollectionEvidenceController"));
        long count = java.util.Arrays.stream(type.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class))
                .peek(m -> assertEquals("hasAuthority('SCOPE_device-ops:collections:read')", m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())).count();
        assertEquals(2, count);
    }
}
