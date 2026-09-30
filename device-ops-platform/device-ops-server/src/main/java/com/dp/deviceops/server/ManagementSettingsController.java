package com.dp.deviceops.server;

import com.dp.deviceops.adapter.web.callback.CallbackProperties;
import com.dp.deviceops.adapter.web.masterdata.MasterDataHttpProperties;
import com.dp.deviceops.adapter.web.parser.ParserControlProperties;
import com.dp.deviceops.adapter.web.schedule.ScheduleProperties;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.CredentialStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Deliberate allowlist, never serializes Environment, properties beans or JWT claims wholesale. */
@RestController
public class ManagementSettingsController {
    private final ProjectClaimAuthorizer claims;
    private final ScheduleProperties schedule;
    private final CallbackProperties callback;
    private final MasterDataHttpProperties masterData;
    private final TelnetProperties telnet;
    private final SerialProperties serial;
    private final ParserControlProperties parser;
    private final CredentialStore credentials;
    private final String authMode;

    public ManagementSettingsController(ProjectClaimAuthorizer claims, ScheduleProperties schedule,
            CallbackProperties callback, MasterDataHttpProperties masterData, TelnetProperties telnet,
            SerialProperties serial, ParserControlProperties parser, CredentialStore credentials,
            @Value("${device-ops.security.mode:oauth2}") String authMode) {
        this.claims = claims;
        this.schedule = schedule;
        this.callback = callback;
        this.masterData = masterData;
        this.telnet = telnet;
        this.serial = serial;
        this.parser = parser;
        this.credentials = credentials;
        this.authMode = authMode;
    }
    @GetMapping("/api/v1/management/settings")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public Settings settings(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        var scope = claims.visibleScope(jwt);
        var scopes = authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("SCOPE_")).map(a -> a.substring(6)).sorted().toList();
        return new Settings("Device Ops Platform", "v1", true, authMode, "local".equals(authMode),
                claims.requireSubject(jwt), scope.namespaces(), scope.projects(), scope.allNamespaces(), scopes, parser.getMaxInputBytes(),
                new Capabilities(schedule.isEnabled(), callback.isEnabled(), masterData.isEnabled(),
                        telnet.isEnabled(), serial.isEnabled(), credentials.available()));
    }
    public record Settings(String platformName, String apiVersion, boolean readOnly, String authMode,
                           boolean localDebug, String subject, List<String> namespaces, List<String> projects,
                           boolean allNamespaces, List<String> scopes, int maxParserInputBytes, Capabilities capabilities) { }
    public record Capabilities(boolean scheduleEnabled, boolean callbackEnabled, boolean masterDataEnabled,
                               boolean telnetEnabled, boolean serialEnabled, boolean credentialStorageAvailable) { }
}
