package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static com.dp.deviceops.parser.runtime.service.RuntimeServiceTestFixture.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserReleaseServiceTest {

    @Test
    void validatesPublishesAndActivatesIndependentLogTypes() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        ParserReleaseService service = service(repository);
        publish(service, "release-a1", "log-a", "1.0.0");
        publish(service, "release-a2", "log-a", "2.0.0");
        publish(service, "release-b1", "log-b", "1.0.0");

        service.activate("log-a", "release-a1", null, 1);
        service.activate("log-b", "release-b1", null, 1);
        service.activate("log-a", "release-a2", "release-a1", 1);

        assertEquals("release-a2", repository.findActive("log-a").orElseThrow().releaseId());
        assertEquals("release-b1", repository.findActive("log-b").orElseThrow().releaseId());

        service.activate("log-a", "release-a1", "release-a2", 1);
        assertEquals("release-a1", repository.findActive("log-a").orElseThrow().releaseId());
        assertEquals("release-b1", repository.findActive("log-b").orElseThrow().releaseId());
    }

    @Test
    void publicationRequiresCurrentPassingValidationAndActivationUsesOptimisticCheck() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        ParserReleaseService service = service(repository);
        ParserRelease draft = RuntimeServiceTestFixture.draft("release-a", "log-a", "1.0.0");
        service.saveDraft(draft, RuntimeServiceTestFixture.bundle("log-a", "1.0.0"));

        assertThrows(ParserRuntimeError.class, () -> service.publish("release-a"));
        assertEquals(true, service.validate("release-a").passed());
        assertEquals(ReleaseState.PUBLISHED, service.publish("release-a").state());
        service.activate("log-a", "release-a", null, 1);
        ParserRuntimeError conflict = assertThrows(ParserRuntimeError.class,
                () -> service.activate("log-a", "release-a", null, 1));
        assertEquals("VERSION_ACTIVATION_CONFLICT", conflict.code());
    }

    @Test
    void activationRequiresEnoughCapableWorkers() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        ParserReleaseService service = service(repository);
        publish(service, "release-a", "log-a", "1.0.0");
        repository.availableWorkers = 0;

        ParserRuntimeError error = assertThrows(ParserRuntimeError.class,
                () -> service.activate("log-a", "release-a", null, 1));

        assertEquals("INSUFFICIENT_CAPABLE_WORKERS", error.code());
    }

    private static void publish(ParserReleaseService service,
            String releaseId, String logType, String version) {
        service.saveDraft(RuntimeServiceTestFixture.draft(releaseId, logType, version),
                RuntimeServiceTestFixture.bundle(logType, version));
        service.validate(releaseId);
        service.publish(releaseId);
    }

    private static ParserReleaseService service(RuntimeServiceTestFixture repository) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new ParserReleaseService(repository, repository, new ParserPlanCompiler(),
                new ParserReleaseVerifier(clock), clock);
    }
}
