package com.dp.deviceops.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaFallbackController {
    @GetMapping({"/projects/{*path}", "/embed/projects/{*path}", "/auth/callback",
            "/overview", "/connections", "/scripts", "/tasks", "/records",
            "/records/{collectionId}", "/settings", "/parser"})
    public String index() {
        return "forward:/index.html";
    }
}
