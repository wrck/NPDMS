package com.dp.deviceops.adapter.web.masterdata;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.MasterDataQueryPort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/master-data")
public class MasterDataController {
    private final MasterDataQueryPort masterData; private final ProjectClaimAuthorizer projects;
    public MasterDataController(MasterDataQueryPort masterData, ProjectClaimAuthorizer projects) { this.masterData = masterData; this.projects = projects; }
    @GetMapping("/projects") @PreAuthorize("hasAuthority('SCOPE_device-ops:projects:read')") public List<MasterDataQueryPort.ProjectProjection> projects(@RequestParam(defaultValue = "") String query) { return masterData.findProjects(query); }
    @GetMapping("/projects/{projectKey}/devices") @PreAuthorize("hasAuthority('SCOPE_device-ops:devices:read')") public List<MasterDataQueryPort.DeviceProjection> devices(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey, @RequestParam(defaultValue = "") String query) { projects.require(jwt, projectKey); return masterData.findDevices(projectKey, query); }
}
