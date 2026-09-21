package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ParserReleaseController {

    private final ParserReleaseRepository releases;
    private final ParserReleaseService service;
    private final ParserControlProperties properties;
    private final Clock clock;

    public ParserReleaseController(ParserReleaseRepository releases, ParserReleaseService service,
            ParserControlProperties properties, Clock clock) {
        this.releases = releases;
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/parser-log-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_parser:release:write')")
    public LogType createLogType(@Valid @RequestBody LogTypeRequest request) {
        var now = clock.instant();
        return releases.createLogType(new LogType(request.logType(), request.displayName(),
                request.description(), now, now));
    }

    @GetMapping("/parser-log-types")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:read')")
    public List<LogType> listLogTypes() {
        return releases.listLogTypes();
    }

    @GetMapping("/parser-log-types/{logType}")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:read')")
    public LogType getLogType(@PathVariable("logType") String logType) {
        return releases.findLogType(logType).orElseThrow(() -> new ParserApiNotFound("LOG_TYPE_NOT_FOUND"));
    }

    @PostMapping("/parser-log-types/{logType}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_parser:release:write')")
    public ParserRelease createRelease(@PathVariable("logType") String logType,
            @Valid @RequestBody ReleaseRequest request) {
        if (!logType.equals(request.manifest().logType())) {
            throw new IllegalArgumentException("path log type differs from release manifest");
        }
        String releaseId = request.releaseId() == null || request.releaseId().isBlank()
                ? UUID.randomUUID().toString() : request.releaseId();
        ParserReleaseManifest manifest = request.manifest();
        ParserCoordinate coordinate = new ParserCoordinate(logType, manifest.releaseVersion(),
                manifest.engineVersion(), manifest.ruleVersion(), manifest.projectionVersion(),
                manifest.extension() == null ? null : manifest.extension().extensionId(),
                manifest.extension() == null ? null : manifest.extension().extensionVersion());
        ParserRelease draft = new ParserRelease(releaseId, logType, manifest.releaseVersion(), ReleaseState.DRAFT,
                coordinate, 1, null, clock.instant(), null);
        return service.saveDraft(draft, new ParserReleaseBundle(manifest, request.rulesJson(),
                request.projectionsJson(), request.modelProfilesJson(), request.ruleSetJsonByPath(),
                request.verificationCases()));
    }

    @GetMapping("/parser-log-types/{logType}/releases")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:read')")
    public List<ParserRelease> listReleases(@PathVariable("logType") String logType) {
        return releases.listReleases(logType);
    }

    @GetMapping("/parser-releases/{releaseId}")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:read')")
    public ParserRelease getRelease(@PathVariable("releaseId") String releaseId) {
        return releases.findRelease(releaseId).orElseThrow(() -> new ParserApiNotFound("RELEASE_NOT_FOUND"));
    }

    @PostMapping("/parser-releases/{releaseId}/validations")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:write')")
    public ParserReleaseValidation validate(@PathVariable("releaseId") String releaseId) {
        return service.validate(releaseId);
    }

    @PostMapping("/parser-releases/{releaseId}/publications")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:write')")
    public ParserRelease publish(@PathVariable("releaseId") String releaseId) {
        return service.publish(releaseId);
    }

    @GetMapping("/parser-log-types/{logType}/active-release")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:read')")
    public ActiveRelease activeRelease(@PathVariable("logType") String logType) {
        getLogType(logType);
        return new ActiveRelease(logType, releases.findActive(logType).map(ParserRelease::releaseId).orElse(null));
    }

    public record ActiveRelease(String logType, String releaseId) { }

    @PutMapping("/parser-log-types/{logType}/active-release")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:write')")
    public ParserRelease activate(@PathVariable("logType") String logType,
            @Valid @RequestBody ActivationRequest request) {
        return service.activate(logType, request.releaseId(), request.expectedCurrentReleaseId(),
                properties.getActivationMinimumCapableWorkers());
    }

    @DeleteMapping("/parser-log-types/{logType}/active-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_parser:release:write')")
    public void clearActive(@PathVariable("logType") String logType,
            @RequestParam("expectedCurrentReleaseId") @NotBlank String expectedCurrentReleaseId) {
        service.clearActive(logType, expectedCurrentReleaseId);
    }

    public record LogTypeRequest(@NotBlank String logType, @NotBlank String displayName, String description) { }
    public record ReleaseRequest(String releaseId, @NotNull @Valid ParserReleaseManifest manifest,
            String rulesJson, @NotBlank String projectionsJson, String modelProfilesJson,
            Map<String, String> ruleSetJsonByPath,
            @NotEmpty List<ParserReleaseBundle.VerificationCase> verificationCases) {
        public ReleaseRequest(String releaseId, ParserReleaseManifest manifest, String rulesJson,
                String projectionsJson, List<ParserReleaseBundle.VerificationCase> verificationCases) {
            this(releaseId, manifest, rulesJson, projectionsJson, null, Map.of(), verificationCases);
        }
    }
    public record ActivationRequest(@NotBlank String releaseId, String expectedCurrentReleaseId) { }
}
