package com.dp.deviceops.parser.runtime.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ParserReleaseValidation(
        String releaseId,
        long draftRevision,
        boolean passed,
        int caseCount,
        List<CaseFailure> failures,
        Instant validatedAt) {

    public ParserReleaseValidation {
        releaseId = ModelSupport.requireText(releaseId, "releaseId");
        if (draftRevision < 1 || caseCount < 0) {
            throw new IllegalArgumentException("validation revision and case count are invalid");
        }
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        Objects.requireNonNull(validatedAt, "validatedAt");
        if (passed && !failures.isEmpty()) {
            throw new IllegalArgumentException("passed validation must not contain failures");
        }
        if (!passed && failures.isEmpty()) {
            throw new IllegalArgumentException("failed validation must contain failures");
        }
    }

    public record CaseFailure(String caseId, String errorCode, String message) {
        public CaseFailure {
            caseId = ModelSupport.requireText(caseId, "caseId");
            errorCode = ModelSupport.requireText(errorCode, "errorCode");
            message = ModelSupport.requireText(message, "message");
        }
    }
}
