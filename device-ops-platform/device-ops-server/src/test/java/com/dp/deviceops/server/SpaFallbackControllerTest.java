package com.dp.deviceops.server;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaFallbackControllerTest {
    @Test
    void directManagementNavigationForwardsOnlyKnownPageRoutes() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();
        for (String path : List.of("/overview", "/connections", "/scripts", "/tasks", "/records",
                "/records/collection-123", "/settings", "/parser", "/projects/project-a",
                "/embed/projects/project-a", "/auth/callback")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void unknownApiAssetsAndNestedPagesAreNotSwallowedByFallback() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();
        for (String path : List.of("/api/v1/unknown", "/api/v1/management/unknown", "/assets/missing.js",
                "/missing.css", "/unknown-page", "/records/id/output", "/scripts/missing.js")) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}
