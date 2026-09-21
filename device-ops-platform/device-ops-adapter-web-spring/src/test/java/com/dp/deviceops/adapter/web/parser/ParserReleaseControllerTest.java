package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParserReleaseControllerTest {

    @Test
    void creationPassesModelProfileArtifactsToTheReleaseService() {
        ParserReleaseService service = mock(ParserReleaseService.class);
        when(service.saveDraft(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ParserReleaseController controller = new ParserReleaseController(
                mock(ParserReleaseRepository.class), service, new ParserControlProperties(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "show-tech", "1.1.0",
                "command-output-block/v1", "1.0.0", "1.0.0", "1.1.0", "1.1.0", "1.0.0", null);
        String profiles = """
                {"schemaVersion":"1.0.0","baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","ruleSets":[]},"profiles":[]}
                """;
        Map<String, String> ruleSets = Map.of("rules/base.json",
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.1.0\",\"rules\":[]}");

        controller.createRelease("show-tech", new ParserReleaseController.ReleaseRequest(
                "release-model", manifest, null,
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.0.0\",\"profiles\":[]}",
                profiles, ruleSets,
                List.of(new ParserReleaseBundle.VerificationCase("case-1", "{}", "{}\n"))));

        ArgumentCaptor<ParserReleaseBundle> bundle = ArgumentCaptor.forClass(ParserReleaseBundle.class);
        verify(service).saveDraft(any(), bundle.capture());
        assertEquals(profiles.trim(), bundle.getValue().modelProfilesJson());
        assertEquals(ruleSets, bundle.getValue().ruleSetJsonByPath());
    }

    @Test
    void activationPassesOptimisticCurrentVersionAndConfiguredWorkerThreshold() {
        ParserReleaseService service = mock(ParserReleaseService.class);
        ParserControlProperties properties = new ParserControlProperties();
        properties.setActivationMinimumCapableWorkers(2);
        when(service.activate("show-tech", "release-1", "release-0", 2)).thenReturn(published());
        ParserReleaseController controller = new ParserReleaseController(
                mock(ParserReleaseRepository.class), service, properties,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        ParserRelease activated = controller.activate("show-tech",
                new ParserReleaseController.ActivationRequest("release-1", "release-0"));

        assertEquals("release-1", activated.releaseId());
        verify(service).activate("show-tech", "release-1", "release-0", 2);
    }

    @Test
    void activationConflictRetainsStableRuntimeCode() {
        ParserReleaseService service = mock(ParserReleaseService.class);
        when(service.activate("show-tech", "release-1", "release-0", 1))
                .thenThrow(new ParserRuntimeError("VERSION_ACTIVATION_CONFLICT"));
        ParserReleaseController controller = new ParserReleaseController(
                mock(ParserReleaseRepository.class), service, new ParserControlProperties(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        ParserRuntimeError error = assertThrows(ParserRuntimeError.class, () -> controller.activate("show-tech",
                new ParserReleaseController.ActivationRequest("release-1", "release-0")));

        assertEquals("VERSION_ACTIVATION_CONFLICT", error.code());
    }

    private static ParserRelease published() {
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        ParserCoordinate coordinate = new ParserCoordinate("show-tech", "1.0.0", "1.0.0",
                "1.0.0", "1.0.0", null, null);
        return new ParserRelease("release-1", "show-tech", "1.0.0", ReleaseState.PUBLISHED, coordinate, 1,
                new ParserReleaseValidation("release-1", 1, true, 1, List.of(), now), now, now);
    }
}
