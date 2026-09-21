package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;

import java.time.Clock;
import java.util.Objects;

public final class ParserReleaseService {

    private final ParserReleaseRepository releases;
    private final WorkerCapabilityRepository workers;
    private final ParserPlanCompiler compiler;
    private final ParserReleaseVerifier verifier;
    private final Clock clock;

    public ParserReleaseService(ParserReleaseRepository releases, WorkerCapabilityRepository workers,
            ParserPlanCompiler compiler, ParserReleaseVerifier verifier, Clock clock) {
        this.releases = Objects.requireNonNull(releases, "releases");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ParserRelease saveDraft(ParserRelease requested, ParserReleaseBundle bundle) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(bundle, "bundle");
        ParserRelease current = releases.findRelease(requested.releaseId()).orElse(null);
        if (current != null && current.state() != ReleaseState.DRAFT) {
            throw error("RELEASE_IMMUTABLE", "published release cannot be updated");
        }
        long revision = current == null ? 1 : current.draftRevision() + 1;
        ParserRelease draft = new ParserRelease(requested.releaseId(), requested.logType(),
                requested.releaseVersion(), ReleaseState.DRAFT, requested.coordinate(), revision,
                null, current == null ? requested.createdAt() : current.createdAt(), null);
        return releases.saveDraft(draft, bundle);
    }

    public ParserReleaseValidation validate(String releaseId) {
        ParserRelease release = requireRelease(releaseId);
        if (release.state() != ReleaseState.DRAFT) {
            throw error("RELEASE_IMMUTABLE", "only draft releases can be validated");
        }
        ParserReleaseBundle bundle = releases.loadBundle(releaseId);
        ParserPlan plan = compiler.compile(bundle);
        ParserReleaseValidation validation = verifier.verify(
                release.releaseId(), release.draftRevision(), plan, bundle.verificationCases());
        if (!releases.saveValidation(validation)) {
            throw error("RELEASE_DRAFT_CHANGED", "release draft changed during validation");
        }
        return validation;
    }

    public ParserRelease publish(String releaseId) {
        ParserRelease release = requireRelease(releaseId);
        ParserReleaseValidation validation = release.validation();
        if (release.state() != ReleaseState.DRAFT || validation == null || !validation.passed()
                || validation.draftRevision() != release.draftRevision()) {
            throw error("RELEASE_NOT_VALIDATED", "current release draft has not passed validation");
        }
        if (!releases.publish(releaseId, ReleaseState.DRAFT)) {
            throw error("RELEASE_STATE_CONFLICT", "release state changed during publication");
        }
        return requireRelease(releaseId);
    }

    public ParserRelease activate(String logType, String releaseId,
            String expectedCurrentReleaseId, int minimumCapableWorkers) {
        if (minimumCapableWorkers < 0) {
            throw new IllegalArgumentException("minimumCapableWorkers must not be negative");
        }
        ParserRelease release = requirePublished(logType, releaseId);
        long available = workers.countAvailable(release.coordinate(), clock.instant());
        if (available < minimumCapableWorkers) {
            throw error("INSUFFICIENT_CAPABLE_WORKERS", "not enough capable parser workers are available");
        }
        if (!releases.activate(logType, releaseId, expectedCurrentReleaseId)) {
            throw error("VERSION_ACTIVATION_CONFLICT", "active release changed");
        }
        return release;
    }

    public void clearActive(String logType, String expectedCurrentReleaseId) {
        if (!releases.clearActive(logType, expectedCurrentReleaseId)) {
            throw error("VERSION_ACTIVATION_CONFLICT", "active release changed");
        }
    }

    public void disable(String releaseId) {
        if (!releases.disable(releaseId)) {
            throw error("RELEASE_STATE_CONFLICT", "release cannot be disabled");
        }
    }

    public ParserRelease resolveForSubmission(String logType, String requestedReleaseId) {
        String releaseId = requestedReleaseId == null || requestedReleaseId.isBlank()
                ? releases.findActive(logType)
                        .orElseThrow(() -> error("ACTIVE_RELEASE_NOT_FOUND",
                                "no active parser release exists for the requested log type"))
                        .releaseId()
                : requestedReleaseId;
        return requirePublished(logType, releaseId);
    }

    public ParserRelease resolveForSubmission(String logType, String requestedReleaseId, String inputFormat) {
        ParserRelease release = resolveForSubmission(logType, requestedReleaseId);
        String expected = releases.loadBundle(release.releaseId()).manifest().inputAdapter();
        if (!expected.equals(inputFormat)) {
            throw error("INPUT_FORMAT_MISMATCH", "input format does not match the parser release");
        }
        return release;
    }

    private ParserRelease requirePublished(String logType, String releaseId) {
        ParserRelease release = requireRelease(releaseId);
        if (!release.logType().equals(logType) || release.state() != ReleaseState.PUBLISHED
                || release.validation() == null || !release.validation().passed()) {
            throw error("RELEASE_NOT_PUBLISHED", "release is not published for the requested log type");
        }
        return release;
    }

    private ParserRelease requireRelease(String releaseId) {
        return releases.findRelease(releaseId)
                .orElseThrow(() -> error("RELEASE_NOT_FOUND", "parser release was not found"));
    }

    private static ParserRuntimeError error(String code, String message) {
        return new ParserRuntimeError(code, message);
    }
}
