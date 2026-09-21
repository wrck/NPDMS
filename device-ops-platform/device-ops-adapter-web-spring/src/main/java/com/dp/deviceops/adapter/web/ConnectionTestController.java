package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.service.ConnectionTestService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionTestController {
    private final ConnectionRequestMapper mapper;
    private final ConnectionTestService tests;

    public ConnectionTestController(ConnectionRequestMapper mapper, ConnectionTestService tests) {
        this.mapper = mapper;
        this.tests = tests;
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public ConnectionTestService.TestResult test(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody ConnectionRequestMapper.Connection request) {
        try (ConnectionRequestMapper.MappedConnection mapped = mapper.map(jwt.getSubject(),
                request.credentialNamespace(), request)) {
            return tests.test(mapped.spec(), mapped.credential());
        } finally {
            request.clearCredentials();
        }
    }
}
