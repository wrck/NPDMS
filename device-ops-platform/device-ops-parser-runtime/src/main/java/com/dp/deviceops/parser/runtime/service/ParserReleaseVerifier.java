package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ParserReleaseVerifier {

    private final DefaultDynamicSemanticParser parser;
    private final CanonicalJson canonicalJson;
    private final Clock clock;

    public ParserReleaseVerifier(Clock clock) {
        this(new DefaultDynamicSemanticParser(), new CanonicalJson(), clock);
    }

    ParserReleaseVerifier(DefaultDynamicSemanticParser parser, CanonicalJson canonicalJson, Clock clock) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ParserReleaseValidation verify(String releaseId, long draftRevision, ParserPlan plan,
            List<ParserReleaseBundle.VerificationCase> cases) {
        List<ParserReleaseValidation.CaseFailure> failures = new ArrayList<>();
        for (ParserReleaseBundle.VerificationCase verificationCase : List.copyOf(cases)) {
            try {
                byte[] actual = canonicalJson.bytes(parser.parse(plan, () -> new ByteArrayInputStream(
                        verificationCase.inputContent().getBytes(StandardCharsets.UTF_8))));
                byte[] expected = verificationCase.expectedResultJson().getBytes(StandardCharsets.UTF_8);
                if (!Arrays.equals(expected, actual)) {
                    failures.add(new ParserReleaseValidation.CaseFailure(
                            verificationCase.caseId(), "RESULT_MISMATCH", "result differs from expected JSON"));
                }
            } catch (SemanticParserError error) {
                failures.add(new ParserReleaseValidation.CaseFailure(
                        verificationCase.caseId(), error.code(), error.getMessage()));
            } catch (RuntimeException error) {
                failures.add(new ParserReleaseValidation.CaseFailure(
                        verificationCase.caseId(), "VERIFICATION_FAILED", "verification case failed"));
            }
        }
        return new ParserReleaseValidation(releaseId, draftRevision, failures.isEmpty(),
                cases.size(), failures, clock.instant());
    }
}
