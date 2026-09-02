package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AuthenticationMode;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.*;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileExpectation;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverClosurePortContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void preservesImmutableFileFactAcrossInspectAndLock() {
        var port = new CutoverClosureControlledPorts.Files();
        var expected = new FileExpectation(1L, 7L, 11L, 21L,
                AttachmentPurpose.POST_COLLECTION_CHECKLIST, 31L, 2, "ref-31",
                new FileFactVersion(3, 4, 5), 6L, "a".repeat(64));

        assertThat(port.inspect(expected)).isEqualTo(port.lockAndRevalidate(expected));
        assertThat(port.inspect(expected).referenceKey()).isEqualTo("ref-31");
    }

    @Test
    void reusesOneExternalTaskForTheSameSavedCredentialIntent() {
        var port = new CutoverClosureControlledPorts.Collections(CLOCK);
        var identity = identity("intent-1");
        var request = new CollectionRequest(identity, 7L, 11L,
                new SavedCredential(41L, 2L), "P6-COLLECT", 1L, "corr-1");

        DispatchFact first = port.request(request);
        DispatchFact replay = port.request(request);

        assertThat(replay).isEqualTo(first);
        assertThat(port.inspectByIntent(identity)).isEqualTo(new DispatchLookup(LookupStatus.FOUND, first));
        assertThat(port.lastIdentity()).isEqualTo(identity);
        assertThat(first.requestDigest()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void keepsTransientSecretOutOfObservableControlledFacts() {
        var port = new CutoverClosureControlledPorts.Collections(CLOCK);
        var authentication = new TransientCredential("engineer", "secret-value", false);
        var request = new CollectionRequest(identity("intent-2"), 7L, 11L,
                authentication, "P6-COLLECT", 1L, "corr-2");

        DispatchFact result = port.request(request);

        assertThat(authentication.toString()).doesNotContain("secret-value").contains("<redacted>");
        assertThat(result.toString()).doesNotContain("secret-value");
        assertThat(result.requestDigest()).isEqualTo(new CollectionRequest(identity("intent-2"), 7L, 11L,
                new TransientCredential("engineer", "rotated-secret", false),
                "P6-COLLECT", 1L, "another-correlation").requestDigest());
        assertThat(port.lastIdentity()).isEqualTo(request.identity());
    }

    @Test
    void suppliesAControlledDispatchFailureForTheManualResultFlow() {
        var port = new CutoverClosureControlledPorts.Collections(CLOCK);
        port.nextDispatch(DispatchOutcome.FAILED, "REMOTE_COMMAND_FAILED");

        DispatchFact result = port.request(new CollectionRequest(identity("intent-3"), 7L, 11L,
                new SavedCredential(41L, 2L), "P6-COLLECT", 1L, "corr-3"));

        assertThat(result.outcome()).isEqualTo(DispatchOutcome.FAILED);
        assertThat(result.failureCode()).isEqualTo("REMOTE_COMMAND_FAILED");
    }

    @Test
    void suppliesDeterministicProjectScopeWithoutSpringRegistration() {
        var port = new CutoverClosureControlledPorts.ProjectScopes(11L, 9L);

        assertThat(port.inspect(7L, 11L, "ACTION_EDIT").projectScopeVersion()).isEqualTo(9L);
        assertThat(port.resolveAllCurrent(7L, "ACTION_VIEW")).containsExactly(11L);
        assertThat(port.getClass().getAnnotations()).isEmpty();
    }

    @Test
    void exposesOnlyTheLockedClosurePortEnums() {
        assertThat(List.of(CollectionStage.values())).containsExactly(
                CollectionStage.PRE_CHECK, CollectionStage.EXECUTION, CollectionStage.TEST,
                CollectionStage.ROLLBACK, CollectionStage.POST_COLLECTION);
        assertThat(List.of(AuthenticationMode.values())).containsExactly(
                AuthenticationMode.SAVED_CREDENTIAL, AuthenticationMode.TRANSIENT_CREDENTIAL);
        assertThat(List.of(AttachmentPurpose.values())).containsExactly(
                AttachmentPurpose.POST_COLLECTION_CHECKLIST, AttachmentPurpose.IMPLEMENTATION_COMMITMENT,
                AttachmentPurpose.OTHER_EVIDENCE, AttachmentPurpose.MANUAL_COLLECTION_RESULT);
    }

    private static CollectionIntentIdentity identity(String idempotencyKey) {
        return new CollectionIntentIdentity(1L, 10L, 20L, 30L,
                CollectionStage.POST_COLLECTION, idempotencyKey);
    }
}
