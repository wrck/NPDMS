package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitOutcome;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AcceptanceReportSourceProjectionServiceTest {

    private final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    private final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    private final AcceptanceReportSourceProjectionService service =
            new AcceptanceReportSourceProjectionService(platform, rules);

    @Test
    void effectiveReportProjectsAsAutoProjectionWithFrozenAttachmentsAndBackfillsDecision() {
        TemplateFrozenView view = view(3);
        when(platform.lockById(50L)).thenReturn(Optional.of(view));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.empty());
        when(platform.findCurrentSubmission(50L)).thenReturn(Optional.empty());
        when(platform.registerProjectionFile(eq(50L), any(), isNull(),
                eq(PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION)))
                .thenReturn(9001L, 9002L);
        when(platform.submitTemplateFrozen(any())).thenReturn(
                new TemplateFrozenSubmitOutcome(77L, false, "OPEN", null));
        when(rules.evaluate(80L, "RENAMED_CUSTOM_REPORT")).thenReturn(
                new cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi.Decision(
                        true, "DELIVERABLE_RULE_SATISFIED", "{}"));

        service.project(event("EFFECTIVE", 300L, null, 1));

        ArgumentCaptor<TemplateFrozenSubmitCommand> submit =
                ArgumentCaptor.forClass(TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(submit.capture());
        assertEquals(50L, submit.getValue().requirementId());
        assertEquals("report:300", submit.getValue().requestKey());
        assertEquals(List.of(9001L, 9002L), submit.getValue().materialIds());
        assertEquals(PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, submit.getValue().sourceType());
        assertTrue(submit.getValue().requestPayloadJson().contains("ACCEPTANCE_REPORT"));
        assertTrue(submit.getValue().requestPayloadJson().contains("\"reportVersionId\":300"));
        verify(platform).registerProjectionFile(eq(50L), eq(attachment(11L, 2, "slot-a")), isNull(),
                eq(PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION));
        verify(rules).evaluate(80L, "RENAMED_CUSTOM_REPORT");
        ArgumentCaptor<String> decision = ArgumentCaptor.forClass(String.class);
        verify(platform).updateSubmissionDecision(eq(77L), decision.capture());
        assertTrue(decision.getValue().contains("\"satisfied\":true"));
    }

    @Test
    void replayedRequestKeyIsANoOpRegardlessOfCurrentStatus() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.of(submission()));

        service.project(event("EFFECTIVE", 300L, null, 1));

        verify(platform, never()).registerProjectionFile(any(), any(), any(), any());
        verify(platform, never()).submitTemplateFrozen(any());
        verify(platform, never()).updateSubmissionDecision(any(), any());
        verifyNoInteractions(rules);
    }

    @Test
    void effectiveReportCannotLandWhileAnotherCurrentSubmissionOccupiesTheRequirement() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.empty());
        when(platform.findCurrentSubmission(50L)).thenReturn(Optional.of(submission()));

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> service.project(event("EFFECTIVE", 300L, null, 1)));
        assertEquals("acceptance source current conflict", conflict.getMessage());
        verify(platform, never()).registerProjectionFile(any(), any(), any(), any());
        verify(platform, never()).submitTemplateFrozen(any());
    }

    @Test
    void replacedReportExtendsTheRecordedPreviousVersionChain() {
        TemplateFrozenView view = view(4);
        when(platform.lockById(50L)).thenReturn(Optional.of(view));
        when(platform.findSubmissionByRequestKey(50L, "report:301")).thenReturn(Optional.empty());
        when(platform.findCurrentSubmission(50L)).thenReturn(Optional.of(submission()));
        when(platform.registerProjectionFile(eq(50L), any(), isNull(), any())).thenReturn(9100L);
        when(platform.submitTemplateFrozen(any())).thenReturn(
                new TemplateFrozenSubmitOutcome(78L, false, "OPEN", null));
        when(rules.evaluate(80L, "RENAMED_CUSTOM_REPORT")).thenReturn(
                new cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi.Decision(
                        true, "DELIVERABLE_RULE_SATISFIED", "{}"));

        service.project(event("REPLACED", 301L, 300L, 2));

        ArgumentCaptor<TemplateFrozenSubmitCommand> submit =
                ArgumentCaptor.forClass(TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(submit.capture());
        assertEquals("report:301", submit.getValue().requestKey());
        verify(platform).updateSubmissionDecision(eq(78L), any());
    }

    @Test
    void replacedReportRejectsACurrentChainThatIsNotTheRecordedPreviousVersion() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(4)));
        when(platform.findSubmissionByRequestKey(50L, "report:301")).thenReturn(Optional.empty());
        TemplateFrozenSubmissionView stale = new TemplateFrozenSubmissionView(1001L, 50L, "report:299",
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", "{}", null,
                List.of(9001L), null);
        when(platform.findCurrentSubmission(50L)).thenReturn(Optional.of(stale));

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> service.project(event("REPLACED", 301L, 300L, 2)));
        assertEquals("acceptance source current conflict", conflict.getMessage());
        verify(platform, never()).submitTemplateFrozen(any());
    }

    @Test
    void wrongProjectOrMissingRootIsRejectedBeforeAnyLedgerWrite() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        IllegalStateException wrongProject = assertThrows(IllegalStateException.class,
                () -> service.project(new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE",
                        100L, 999L, 50L, "PRELIMINARY", 19L, 300L, null, 1, List.of(attachment(11L, 2, "slot-a")))));
        assertEquals("acceptance deliverable root unavailable", wrongProject.getMessage());
        when(platform.lockById(50L)).thenReturn(Optional.empty());
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> service.project(event("EFFECTIVE", 300L, null, 1)));
        assertEquals("acceptance deliverable root unavailable", missing.getMessage());
        verify(platform, never()).registerProjectionFile(any(), any(), any(), any());
        verify(platform, never()).submitTemplateFrozen(any());
    }

    @Test
    void revocationRevokesTheRecordedProjectionAndStaysIdempotent() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.of(submission()));
        when(platform.revokeProjectionSubmission(50L, "report:300", PlatformDeliveryRequirementApi.ARCHIVE_INVALID))
                .thenReturn(true);

        service.project(revokeEvent());

        verify(platform, never()).registerProjectionFile(any(), any(), any(), any());
        verify(platform, never()).submitTemplateFrozen(any());
    }

    @Test
    void revocationConflictsWhileAReplacementStillOccupiesTheCurrentChain() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.of(submission()));
        when(platform.revokeProjectionSubmission(50L, "report:300", PlatformDeliveryRequirementApi.ARCHIVE_INVALID))
                .thenReturn(false);
        when(platform.findCurrentSubmission(50L)).thenReturn(Optional.of(submission()));

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> service.project(revokeEvent()));
        assertEquals("acceptance source revoke conflict", conflict.getMessage());
    }

    @Test
    void revocationAfterTheChainHasFullyClosedIsAnIdempotentPass() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.of(submission()));
        when(platform.revokeProjectionSubmission(50L, "report:300", PlatformDeliveryRequirementApi.ARCHIVE_INVALID))
                .thenReturn(false);
        when(platform.findCurrentSubmission(50L)).thenReturn(Optional.empty());

        service.project(revokeEvent());

        verify(platform, never()).submitTemplateFrozen(any());
    }

    @Test
    void revocationWithoutRecordedProjectionFailsClosed() {
        when(platform.lockById(50L)).thenReturn(Optional.of(view(3)));
        when(platform.findSubmissionByRequestKey(50L, "report:300")).thenReturn(Optional.empty());

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> service.project(revokeEvent()));
        assertEquals("acceptance source current missing", missing.getMessage());
        verify(platform, never()).revokeProjectionSubmission(any(), any(), any());
    }

    @Test
    void malformedEventsAreRejectedBeforeAnyLockOrLedgerWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.project(
                new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE", 100L, 80L, null,
                        "PRELIMINARY", 19L, 300L, null, 1, List.of(attachment(11L, 2, "slot-a")))));
        assertThrows(IllegalArgumentException.class, () -> service.project(
                new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE", 100L, 80L, 50L,
                        "OTHER", 19L, 300L, null, 1, List.of(attachment(11L, 2, "slot-a")))));
        assertThrows(IllegalArgumentException.class, () -> service.project(
                new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE", 100L, 80L, 50L,
                        "PRELIMINARY", 19L, 300L, null, 0, List.of(attachment(11L, 2, "slot-a")))));
        assertThrows(IllegalArgumentException.class, () -> service.project(
                new AcceptanceReportVersionChangedMessage("event-1", 7L, "REVOKED", 100L, 80L, 50L,
                        "PRELIMINARY", 19L, 300L, 299L, null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.project(
                new AcceptanceReportVersionChangedMessage("event-1", 7L, "EFFECTIVE", 100L, 80L, 50L,
                        "PRELIMINARY", 19L, 300L, null, 1, List.of())));
        verifyNoInteractions(platform, rules);
    }

    private TemplateFrozenView view(int version) {
        return new TemplateFrozenView(50L, 80L, "RENAMED_CUSTOM_REPORT", "验收报告", "S4", "T-40",
                null, null, true, 1, null, "OPEN", "{}", version);
    }

    private TemplateFrozenSubmissionView submission() {
        return new TemplateFrozenSubmissionView(1001L, 50L, "report:300",
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", "{}", null,
                List.of(9001L, 9002L), null);
    }

    private AcceptanceReportVersionChangedMessage event(String changeType, Long currentVersionId,
                                                        Long previousVersionId, Integer versionNo) {
        return new AcceptanceReportVersionChangedMessage("event-1", 7L, changeType, 100L, 80L, 50L,
                "PRELIMINARY", 19L, currentVersionId, previousVersionId, versionNo,
                List.of(attachment(11L, 2, "slot-a"), attachment(12L, 1, "slot-b")));
    }

    private AcceptanceReportVersionChangedMessage revokeEvent() {
        return new AcceptanceReportVersionChangedMessage("event-2", 7L, "REVOKED", 100L, 80L, 50L,
                "PRELIMINARY", 19L, null, 300L, null, List.of());
    }

    private FileArtifactVersionFact attachment(long artifactId, int versionNo, String referenceKey) {
        return new FileArtifactVersionFact(artifactId, versionNo, referenceKey, null, null, null, null,
                "a".repeat(64), "AVAILABLE", "ACTIVE", new FileFactVersion(3, 4, 5), 8L);
    }
}
