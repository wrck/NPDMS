package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/parser-options")
public class ParserSelectionController {

    private final ParserReleaseRepository releases;
    private final ParserControlProperties properties;

    public ParserSelectionController(ParserReleaseRepository releases, ParserControlProperties properties) {
        this.releases = releases;
        this.properties = properties;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:execute')")
    public SelectionOptions options() {
        String defaultReleaseId = releases.findActive(properties.getDefaultLogType())
                .map(release -> release.releaseId()).orElse(null);
        List<ParserOption> options = releases.listLogTypes().stream()
                .flatMap(type -> releases.listReleases(type.logType()).stream()
                        .filter(release -> release.state() == ReleaseState.PUBLISHED)
                        .map(release -> new ParserOption(release.releaseId(), release.logType(), type.displayName(),
                                release.releaseVersion(), release.coordinate(),
                                release.releaseId().equals(defaultReleaseId))))
                .sorted(Comparator.comparing(ParserOption::displayName)
                        .thenComparing(ParserOption::releaseVersion).reversed())
                .toList();
        return new SelectionOptions(properties.isAutomaticParsingEnabled(), defaultReleaseId != null,
                defaultReleaseId, options);
    }

    public record SelectionOptions(boolean automaticEnabled, boolean defaultAvailable,
            String defaultReleaseId, List<ParserOption> options) { }

    public record ParserOption(String releaseId, String logType, String displayName,
            String releaseVersion, com.dp.deviceops.parser.semantic.ParserCoordinate coordinate,
            boolean activeDefault) { }
}
