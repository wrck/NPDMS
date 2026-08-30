package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalSubmissionUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalConfirmationUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidencePublishUpdate;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArrivalAcceptanceApplicationServiceTest {

    @Test
    void createsDraftWithFrozenProjectDeliveryAndDeviceFacts() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        when(projectPort.inspect(1L, 100L, 8L)).thenReturn(projectFact());
        when(deliveryPort.inspectAssignedScope(100L)).thenReturn(deliveryScope());
        when(devicePort.resolveBySerials(1L, 100L, Set.of("SN-1"))).thenReturn(deviceScope(100L));
        doAnswer(invocation -> {
            ArrivalAcceptanceDO row = invocation.getArgument(0);
            row.setId(900L);
            return 1;
        }).when(mapper).insert(any(ArrivalAcceptanceDO.class));
        RecordingCommandExecutionApi commands = new RecordingCommandExecutionApi();
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                mapper, mock(ArrivalLineMapper.class), mock(ArrivalDifferenceMapper.class),
                mock(DeliveryEvidenceMapper.class),
                mock(DeliveryEvidenceRevisionMapper.class), projectPort, deliveryPort, devicePort,
                mock(FileArtifactFactPort.class), commands);

        ArrivalAcceptanceDO created = service.createDraft(command());

        assertEquals(900L, created.getId());
        assertEquals("DRAFT", created.getStatus());
        assertEquals(1, created.getBatchRootMarker());
        assertEquals(5, created.getProjectVersion());
        assertEquals(6L, created.getProjectParticipantFactVersion());
        assertEquals(7L, created.getProjectScopeVersion());
        assertEquals(8L, created.getDeliveryScopeVersion());
        assertTrue(created.getExpectedScopeSnapshot().contains("SN-1"));
        assertTrue(created.getExpectedScopeSnapshot().contains("MODEL-1"));
        assertTrue(created.getScopeWatermark().contains("\"11\":9"));
        ArgumentCaptor<ArrivalAcceptanceDO> inserted = ArgumentCaptor.forClass(ArrivalAcceptanceDO.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("8", inserted.getValue().getCreator());
        assertEquals("corr-create", commands.successFacts.correlationId());
    }

    @Test
    void rejectsForeignDeviceBeforeWritingDraft() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        when(projectPort.inspect(1L, 100L, 8L)).thenReturn(projectFact());
        when(deliveryPort.inspectAssignedScope(100L)).thenReturn(deliveryScope());
        when(devicePort.resolveBySerials(1L, 100L, Set.of("SN-1"))).thenReturn(deviceScope(200L));
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                mapper, mock(ArrivalLineMapper.class), mock(ArrivalDifferenceMapper.class),
                mock(DeliveryEvidenceMapper.class),
                mock(DeliveryEvidenceRevisionMapper.class), projectPort, deliveryPort, devicePort,
                mock(FileArtifactFactPort.class), new RecordingCommandExecutionApi());

        assertThrows(IllegalStateException.class, () -> service.createDraft(command()));

        verify(mapper, never()).insert(any(ArrivalAcceptanceDO.class));
    }

    @Test
    void rejectsStaleExpectedDeliveryScopeBeforeWritingDraft() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        when(projectPort.inspect(1L, 100L, 8L)).thenReturn(projectFact());
        when(deliveryPort.inspectAssignedScope(100L)).thenReturn(deliveryScope());
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                mapper, mock(ArrivalLineMapper.class), mock(ArrivalDifferenceMapper.class),
                mock(DeliveryEvidenceMapper.class), mock(DeliveryEvidenceRevisionMapper.class),
                projectPort, deliveryPort, devicePort, mock(FileArtifactFactPort.class),
                new RecordingCommandExecutionApi());

        ArrivalAcceptanceContractException stale = assertThrows(ArrivalAcceptanceContractException.class,
                () -> service.createDraft(
                new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                        1L, 100L, 8L, "B-1", "L-1", LocalDateTime.now(), "Signer", 7L,
                        "create-key", "corr-create")));
        assertEquals("SCOPE_STALE", stale.category());
        assertEquals("DELIVERY_SCOPE_STALE", stale.reasonCode());
        assertEquals("COM", stale.ownerContext());

        verify(devicePort, never()).resolveBySerials(any(), any(), any());
        verify(mapper, never()).insert(any(ArrivalAcceptanceDO.class));
    }

    @Test
    void createAndSubmitRejectInvalidCorrelationBeforePlatformClaim() {
        for (String invalid : Arrays.asList(null, "", " corr", "corr ", "x".repeat(129))) {
            RecordingCommandExecutionApi commands = new RecordingCommandExecutionApi();
            ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                    mock(ArrivalAcceptanceMapper.class), mock(ArrivalLineMapper.class),
                    mock(ArrivalDifferenceMapper.class), mock(DeliveryEvidenceMapper.class),
                    mock(DeliveryEvidenceRevisionMapper.class), mock(ProjectQualificationPort.class),
                    mock(DeliveryScopePort.class), mock(DeviceScopeFactPort.class),
                    mock(FileArtifactFactPort.class), commands);

            assertThrows(IllegalArgumentException.class, () -> service.createDraft(
                    new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                            1L, 100L, 8L, "B-1", "L-1", LocalDateTime.now(), "Signer", 8L,
                            "create-key", invalid)));
            assertThrows(IllegalArgumentException.class, () -> service.submit(
                    new ArrivalAcceptanceApplicationService.SubmitCommand(
                            1L, 900L, 8L, 0, "submit-key", invalid)));
            assertEquals(0, commands.requestDigests.size());
        }
    }

    @Test
    void createAndSubmitCorrelationDoesNotChangeBusinessDigest() {
        ArrivalAcceptanceMapper mapper = mock(ArrivalAcceptanceMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        when(projectPort.inspect(1L, 100L, 8L)).thenReturn(projectFact());
        when(deliveryPort.inspectAssignedScope(100L)).thenReturn(deliveryScope());
        when(devicePort.resolveBySerials(1L, 100L, Set.of("SN-1"))).thenReturn(deviceScope(100L));
        doAnswer(invocation -> {
            ((ArrivalAcceptanceDO) invocation.getArgument(0)).setId(900L);
            return 1;
        }).when(mapper).insert(any(ArrivalAcceptanceDO.class));
        RecordingCommandExecutionApi createCommands = new RecordingCommandExecutionApi();
        ArrivalAcceptanceApplicationService createService = new ArrivalAcceptanceApplicationService(
                mapper, mock(ArrivalLineMapper.class), mock(ArrivalDifferenceMapper.class),
                mock(DeliveryEvidenceMapper.class), mock(DeliveryEvidenceRevisionMapper.class),
                projectPort, deliveryPort, devicePort, mock(FileArtifactFactPort.class), createCommands);

        createService.createDraft(command("corr-create-one"));
        createService.createDraft(command("corr-create-two"));
        assertEquals(createCommands.requestDigests.get(0), createCommands.requestDigests.get(1));
        assertEquals(List.of("corr-create-one", "corr-create-two"), createCommands.successFactsHistory.stream()
                .map(PlatformCommandExecutionApi.SuccessFacts::correlationId).toList());

        SubmissionFixture submission = submissionFixture();
        when(submission.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(submission.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);
        submission.service().submit(submitCommand("corr-submit-one"));
        submission.service().submit(submitCommand("corr-submit-two"));
        assertEquals(submission.commandExecutionApi().requestDigests.get(0),
                submission.commandExecutionApi().requestDigests.get(1));
        assertEquals(List.of("corr-submit-one", "corr-submit-two"),
                submission.commandExecutionApi().successFactsHistory.stream()
                        .map(PlatformCommandExecutionApi.SuccessFacts::correlationId).toList());
    }

    @Test
    void submitsCompleteDeviceScopeWithFrozenOwnerFacts() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                submitCommand());

        assertEquals(900L, result.arrivalAcceptanceId());
        assertEquals("ACCEPTED", result.status());
        assertEquals(1, result.version());
        assertEquals(50L, result.evidenceId());
        assertEquals(1, result.evidenceRevision());
        ArgumentCaptor<ArrivalSubmissionUpdate> update = ArgumentCaptor.forClass(ArrivalSubmissionUpdate.class);
        verify(fixture.acceptanceMapper()).updateSubmittedIfMatch(update.capture());
        assertEquals("ACCEPTED", update.getValue().submittedStatus());
        assertEquals(50L, update.getValue().evidenceId());
        verify(fixture.projectPort()).lockAndRevalidate(any());
        verify(fixture.deliveryPort()).lockAndRevalidate(100L, 8L);
        verify(fixture.devicePort()).lockAndRevalidate(any(), any(), any());
        verify(fixture.filePort()).lockAndRevalidateArrivalEvidence(any());
    }

    @Test
    void submitsThroughPlatformIdempotencyScopeWhenKeyIsPresent() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        fixture.service().submit(new ArrivalAcceptanceApplicationService.SubmitCommand(
                1L, 900L, 8L, 0, "submit-key", "corr-submit"));

        assertEquals("IMP:ARRIVAL_SUBMIT:900", fixture.commandExecutionApi().scope.scopeCode());
        assertEquals(64, fixture.commandExecutionApi().requestDigest.length());
        assertEquals("ARRIVAL_ACCEPTANCE_SUBMIT", fixture.commandExecutionApi().successFacts.operationCode());
        assertEquals("corr-submit", fixture.commandExecutionApi().successFacts.correlationId());
    }

    @Test
    void rejectsChangedFileScopeBeforeWritingSubmission() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(7L));

        ArrivalAcceptanceContractException invalid = assertThrows(ArrivalAcceptanceContractException.class,
                () -> fixture.service().submit(submitCommand()));
        assertEquals("EVIDENCE_INVALID", invalid.category());
        assertEquals("EVIDENCE_SCOPE_INVALID", invalid.reasonCode());
        assertEquals("PLT", invalid.ownerContext());

        verify(fixture.acceptanceMapper(), never()).updateSubmittedIfMatch(any());
        verify(fixture.lineMapper(), never()).selectCurrentListForUpdate(any());
    }

    @Test
    void distinguishesIdempotencyConflictFromInProgressBeforeBusinessOperation() {
        SubmissionFixture conflictFixture = submissionFixture();
        conflictFixture.commandExecutionApi().decision = PlatformCommandExecutionApi.Decision.CONFLICT;
        ArrivalAcceptanceContractException conflict = assertThrows(ArrivalAcceptanceContractException.class,
                () -> conflictFixture.service().submit(submitCommand()));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.category());
        assertEquals("IDEMPOTENCY_PAYLOAD_CONFLICT", conflict.reasonCode());
        verify(conflictFixture.acceptanceMapper(), never()).selectForUpdate(any());

        SubmissionFixture progressFixture = submissionFixture();
        progressFixture.commandExecutionApi().decision = PlatformCommandExecutionApi.Decision.IN_PROGRESS;
        ArrivalAcceptanceContractException progress = assertThrows(ArrivalAcceptanceContractException.class,
                () -> progressFixture.service().submit(submitCommand()));
        assertEquals("IDEMPOTENCY_IN_PROGRESS", progress.category());
        assertEquals("IDEMPOTENCY_COMMAND_IN_PROGRESS", progress.reasonCode());
        verify(progressFixture.acceptanceMapper(), never()).selectForUpdate(any());
    }

    @Test
    void submitsOpenDifferenceToDifferencePending() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(openDifference()));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                submitCommand());

        assertEquals("DIFFERENCE_PENDING", result.status());
    }

    @Test
    void combinesConfirmedBatchWithCurrentBatchBeforePromotingCandidate() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(draftTwoDevices());
        when(fixture.deliveryPort().lockAndRevalidate(100L, 8L)).thenReturn(deliveryScopeTwoDevices());
        when(fixture.devicePort().lockAndRevalidate(any(), any(), any())).thenReturn(deviceScopeTwoDevices());
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(acceptedDeviceLine(12L, 900L)));
        when(fixture.lineMapper().selectConfirmedAcceptedByProject(any()))
                .thenReturn(List.of(acceptedDeviceLine(11L, 800L)));
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                submitCommand());

        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void combinesCurrentAcceptedDeviceWithCurrentEffectiveExemption() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(draftTwoDevices());
        when(fixture.deliveryPort().lockAndRevalidate(100L, 8L)).thenReturn(deliveryScopeTwoDevices());
        when(fixture.devicePort().lockAndRevalidate(any(), any(), any())).thenReturn(deviceScopeTwoDevices());
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(acceptedDeviceLine(12L, 900L)));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any()))
                .thenReturn(List.of(exemptedDevice(900L, 11L)));
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                submitCommand());

        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void combinesConfirmedEffectiveExemptionWithCurrentAcceptedDevice() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(draftTwoDevices());
        when(fixture.deliveryPort().lockAndRevalidate(100L, 8L)).thenReturn(deliveryScopeTwoDevices());
        when(fixture.devicePort().lockAndRevalidate(any(), any(), any())).thenReturn(deviceScopeTwoDevices());
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(acceptedDeviceLine(12L, 900L)));
        when(fixture.differenceMapper().selectEffectiveExemptionsByProject(any()))
                .thenReturn(List.of(exemptedDevice(800L, 11L)));
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                submitCommand());

        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void combinesCurrentAcceptedQuantityWithCurrentEffectiveExemption() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(draftQuantityScope());
        when(fixture.deliveryPort().lockAndRevalidate(100L, 8L)).thenReturn(deliveryQuantityScope());
        when(fixture.devicePort().lockAndRevalidate(any(), any(), any()))
                .thenReturn(new DeviceScopeFactPort.DeviceScopeFact(100L, List.of()));
        when(fixture.lineMapper().selectCurrentListForUpdate(any()))
                .thenReturn(List.of(acceptedQuantityLine(new BigDecimal("3"))));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any()))
                .thenReturn(List.of(exemptedQuantity(new BigDecimal("2"))));
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                submitCommand());

        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void rejectsActiveExemptionWithLegacyScopeShapeBeforeWritingSubmission() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(draftTwoDevices());
        when(fixture.deliveryPort().lockAndRevalidate(100L, 8L)).thenReturn(deliveryScopeTwoDevices());
        when(fixture.devicePort().lockAndRevalidate(any(), any(), any())).thenReturn(deviceScopeTwoDevices());
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(acceptedDeviceLine(12L, 900L)));
        ArrivalDifferenceDO legacy = exemptedDevice(800L, 11L);
        legacy.setScopeSnapshot("{\"scopeType\":\"DEVICE\",\"deviceId\":\"11\"}");
        when(fixture.differenceMapper().selectEffectiveExemptionsByProject(any())).thenReturn(List.of(legacy));
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));

        assertThrows(IllegalArgumentException.class, () -> fixture.service().submit(
                submitCommand()));

        verify(fixture.acceptanceMapper(), never()).updateSubmittedIfMatch(any());
    }

    @Test
    void confirmsCandidateWithProjectFactVersionEvidenceStateAndOutbox() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(confirmableCandidate());
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().selectMaxAllocatedProjectFactVersion(any())).thenReturn(4L);
        when(fixture.acceptanceMapper().updateConfirmedIfMatch(any())).thenReturn(1);
        when(fixture.evidenceMapper().markPublishedPendingAccIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.ConfirmationResult result = fixture.service().confirm(
                new ArrivalAcceptanceApplicationService.ConfirmCommand(
                        1L, 900L, 8L, 1, "confirm-key", "corr-1"));

        assertEquals("CONFIRMED", result.status());
        assertEquals(2, result.version());
        assertEquals(5L, result.projectFactVersion());
        ArgumentCaptor<ArrivalConfirmationUpdate> rootUpdate =
                ArgumentCaptor.forClass(ArrivalConfirmationUpdate.class);
        verify(fixture.acceptanceMapper()).updateConfirmedIfMatch(rootUpdate.capture());
        assertEquals(5L, rootUpdate.getValue().projectFactVersion());
        ArgumentCaptor<DeliveryEvidencePublishUpdate> evidenceUpdate =
                ArgumentCaptor.forClass(DeliveryEvidencePublishUpdate.class);
        verify(fixture.evidenceMapper()).markPublishedPendingAccIfMatch(evidenceUpdate.capture());
        assertEquals(result.eventId(), evidenceUpdate.getValue().eventId());
        assertEquals("corr-1", evidenceUpdate.getValue().correlationId());
        ArgumentCaptor<ProjectQualificationPort.RevalidationCommand> qualification =
                ArgumentCaptor.forClass(ProjectQualificationPort.RevalidationCommand.class);
        verify(fixture.projectPort()).lockAndRevalidate(qualification.capture());
        assertEquals(8L, qualification.getValue().subjectUserId());
        assertTrue(qualification.getValue().requireActorAsProjectManager());
        assertEquals("IMP:ARRIVAL_CONFIRM:900", fixture.commandExecutionApi().scope.scopeCode());
        assertEquals(64, fixture.commandExecutionApi().requestDigest.length());
        assertEquals(1, fixture.commandExecutionApi().successFacts.businessEvents().size());
        PlatformCommandExecutionApi.BusinessEvent event =
                fixture.commandExecutionApi().successFacts.businessEvents().getFirst();
        assertEquals("ImplementationEvidencePublished", event.eventType());
        assertTrue(event.eventPayload().contains("\"evidenceId\":50"));
        assertTrue(event.eventPayload().contains("\"evidenceRevision\":1"));
        assertTrue(event.eventPayload().contains("\"artifactId\":40"));
        assertTrue(event.eventPayload().contains("\"correlationId\":\"corr-1\""));
        assertEquals("corr-1", fixture.commandExecutionApi().successFacts.correlationId());
    }

    @Test
    void rejectsNonNormalizedOrOversizedCorrelationBeforeClaimingCommand() {
        SubmissionFixture spaced = submissionFixture();
        assertThrows(IllegalArgumentException.class, () -> spaced.service().confirm(
                new ArrivalAcceptanceApplicationService.ConfirmCommand(
                        1L, 900L, 8L, 1, "confirm-key", " corr-1")));
        assertEquals(null, spaced.commandExecutionApi().scope);

        SubmissionFixture oversized = submissionFixture();
        assertThrows(IllegalArgumentException.class, () -> oversized.service().confirm(
                new ArrivalAcceptanceApplicationService.ConfirmCommand(
                        1L, 900L, 8L, 1, "confirm-key", "c".repeat(129))));
        assertEquals(null, oversized.commandExecutionApi().scope);
    }

    @Test
    void replaysCompletedConfirmationWithoutTouchingBusinessRows() {
        SubmissionFixture fixture = submissionFixture();
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 8, 30, 10, 0);
        ArrivalAcceptanceApplicationService.ConfirmationResult replay =
                new ArrivalAcceptanceApplicationService.ConfirmationResult(
                        900L, "CONFIRMED", 2, 5L, 50L, 1, 40L, 5,
                        "REF-1", "hash", 3L, "{}", "event-1", confirmedAt);
        fixture.commandExecutionApi().decision = PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED;
        fixture.commandExecutionApi().replay = new ConfirmationResultHolder(replay);

        ArrivalAcceptanceApplicationService.ConfirmationResult result = fixture.service().confirm(
                new ArrivalAcceptanceApplicationService.ConfirmCommand(
                        1L, 900L, 8L, 1, "confirm-key", "corr-1"));

        assertEquals(replay, result);
        verify(fixture.acceptanceMapper(), never()).selectForUpdate(any());
        verify(fixture.acceptanceMapper(), never()).updateConfirmedIfMatch(any());
        verify(fixture.evidenceMapper(), never()).markPublishedPendingAccIfMatch(any());
    }

    @Test
    void rejectsStaleConfirmationBeforeOwnerFactsOrBusinessWrites() {
        SubmissionFixture fixture = submissionFixture();
        ArrivalAcceptanceDO stale = confirmableCandidate();
        stale.setVersion(2);
        when(fixture.acceptanceMapper().selectForUpdate(any())).thenReturn(stale);

        ArrivalAcceptanceContractException exception = assertThrows(ArrivalAcceptanceContractException.class,
                () -> fixture.service().confirm(new ArrivalAcceptanceApplicationService.ConfirmCommand(
                        1L, 900L, 8L, 1, "confirm-key", "corr-1")));
        assertEquals("AGGREGATE_OR_LINE_VERSION_CONFLICT", exception.category());
        assertEquals("AGGREGATE_VERSION_STALE", exception.reasonCode());
        assertEquals(2, exception.currentAggregateVersion());

        verify(fixture.projectPort(), never()).lockAndRevalidate(any());
        verify(fixture.acceptanceMapper(), never()).updateConfirmedIfMatch(any());
        verify(fixture.evidenceMapper(), never()).markPublishedPendingAccIfMatch(any());
    }

    private static ArrivalAcceptanceApplicationService.CreateDraftCommand command() {
        return command("corr-create");
    }

    private static ArrivalAcceptanceApplicationService.CreateDraftCommand command(String correlationId) {
        return new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                1L, 100L, 8L, "ARRIVAL-001", "LOGISTICS-001",
                LocalDateTime.of(2026, 8, 30, 9, 0), "客户签收人", 8L,
                "create-key", correlationId);
    }

    private static ArrivalAcceptanceApplicationService.SubmitCommand submitCommand() {
        return submitCommand("corr-submit");
    }

    private static ArrivalAcceptanceApplicationService.SubmitCommand submitCommand(String correlationId) {
        return new ArrivalAcceptanceApplicationService.SubmitCommand(
                1L, 900L, 8L, 0, "submit-key", correlationId);
    }

    private static ProjectQualificationPort.ProjectQualificationFact projectFact() {
        return new ProjectQualificationPort.ProjectQualificationFact(
                100L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "ACTIVE", "S4", 5, 6L, 7L);
    }

    private static DeliveryScopePort.AssignedScope deliveryScope() {
        return new DeliveryScopePort.AssignedScope(100L, 8L, List.of(
                new DeliveryScopePort.AssignedLine(20L, new BigDecimal("1"),
                        "台", "PRODUCT-1", "MODEL-1", Set.of("SN-1"))));
    }

    private static DeviceScopeFactPort.DeviceScopeFact deviceScope(Long currentProjectId) {
        return new DeviceScopeFactPort.DeviceScopeFact(100L, List.of(
                new DeviceScopeFactPort.DeviceFact(11L, "SN-1", currentProjectId, 9L)));
    }

    private static SubmissionFixture submissionFixture() {
        ArrivalAcceptanceMapper acceptanceMapper = mock(ArrivalAcceptanceMapper.class);
        ArrivalLineMapper lineMapper = mock(ArrivalLineMapper.class);
        ArrivalDifferenceMapper differenceMapper = mock(ArrivalDifferenceMapper.class);
        DeliveryEvidenceMapper evidenceMapper = mock(DeliveryEvidenceMapper.class);
        DeliveryEvidenceRevisionMapper revisionMapper = mock(DeliveryEvidenceRevisionMapper.class);
        ProjectQualificationPort projectPort = mock(ProjectQualificationPort.class);
        DeliveryScopePort deliveryPort = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devicePort = mock(DeviceScopeFactPort.class);
        FileArtifactFactPort filePort = mock(FileArtifactFactPort.class);
        RecordingCommandExecutionApi commandExecutionApi = new RecordingCommandExecutionApi();
        when(acceptanceMapper.selectForUpdate(any())).thenReturn(draft());
        when(projectPort.lockAndRevalidate(any())).thenReturn(projectFact());
        when(deliveryPort.lockAndRevalidate(100L, 8L)).thenReturn(deliveryScope());
        when(devicePort.lockAndRevalidate(any(), any(), any())).thenReturn(deviceScope(100L));
        when(evidenceMapper.selectBySourceForUpdate(any())).thenReturn(evidence());
        when(revisionMapper.selectRevision(any())).thenReturn(evidenceRevision());
        when(lineMapper.selectCurrentListForUpdate(any())).thenReturn(List.of(acceptedDeviceLine()));
        when(differenceMapper.selectCurrentListForUpdate(any())).thenReturn(List.of());
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                acceptanceMapper, lineMapper, differenceMapper, evidenceMapper, revisionMapper,
                projectPort, deliveryPort, devicePort, filePort, commandExecutionApi);
        return new SubmissionFixture(service, acceptanceMapper, lineMapper, differenceMapper,
                evidenceMapper, projectPort, deliveryPort, devicePort, filePort, commandExecutionApi);
    }

    private static ArrivalAcceptanceDO draft() {
        ArrivalAcceptanceDO row = new ArrivalAcceptanceDO();
        row.setId(900L);
        row.setTenantId(1L);
        row.setProjectId(100L);
        row.setStatus("DRAFT");
        row.setCreator("8");
        row.setVersion(0);
        row.setProjectVersion(5);
        row.setProjectParticipantFactVersion(6L);
        row.setProjectScopeVersion(7L);
        row.setDeliveryScopeVersion(8L);
        row.setExpectedScopeSnapshot("{\"deliveryLines\":[{\"orderLineId\":20,\"assignedQuantity\":1," +
                "\"unitCode\":\"台\",\"productCode\":\"PRODUCT-1\",\"modelCode\":\"MODEL-1\"," +
                "\"serialNumbers\":[\"SN-1\"]}],\"devices\":[{\"deviceId\":11," +
                "\"serialNumber\":\"SN-1\",\"currentProjectId\":100," +
                "\"projectAssignmentVersion\":9}]}");
        return row;
    }

    private static ArrivalAcceptanceDO draftTwoDevices() {
        ArrivalAcceptanceDO row = draft();
        row.setExpectedScopeSnapshot("{\"deliveryLines\":[{\"orderLineId\":20,\"assignedQuantity\":2," +
                "\"unitCode\":\"台\",\"productCode\":\"PRODUCT-1\",\"modelCode\":\"MODEL-1\"," +
                "\"serialNumbers\":[\"SN-1\",\"SN-2\"]}],\"devices\":[{\"deviceId\":11," +
                "\"serialNumber\":\"SN-1\",\"currentProjectId\":100,\"projectAssignmentVersion\":9}," +
                "{\"deviceId\":12,\"serialNumber\":\"SN-2\",\"currentProjectId\":100," +
                "\"projectAssignmentVersion\":10}]}");
        return row;
    }

    private static ArrivalAcceptanceDO draftQuantityScope() {
        ArrivalAcceptanceDO row = draft();
        row.setExpectedScopeSnapshot("{\"deliveryLines\":[{\"orderLineId\":20," +
                "\"assignedQuantity\":5,\"unitCode\":\"台\",\"productCode\":\"PRODUCT-1\"," +
                "\"modelCode\":\"MODEL-1\",\"serialNumbers\":[]}],\"devices\":[]}");
        return row;
    }

    private static ArrivalAcceptanceDO confirmableCandidate() {
        ArrivalAcceptanceDO row = draft();
        row.setStatus("ACCEPTED");
        row.setVersion(1);
        row.setEvidenceId(50L);
        row.setEvidenceRevision(1);
        row.setScopeWatermark("{\"deliveryScopeVersion\":8}");
        return row;
    }

    private static DeliveryEvidenceDO evidence() {
        DeliveryEvidenceDO row = new DeliveryEvidenceDO();
        row.setId(50L);
        row.setTenantId(1L);
        row.setProjectId(100L);
        row.setCurrentRevisionNo(1);
        row.setAccSyncStatus("NOT_PUBLISHED");
        row.setVersion(0);
        return row;
    }

    private static DeliveryEvidenceRevisionDO evidenceRevision() {
        DeliveryEvidenceRevisionDO row = new DeliveryEvidenceRevisionDO();
        row.setEvidenceId(50L);
        row.setRevisionNo(1);
        row.setFileArtifactId(40L);
        row.setFileReferenceId("REF-1");
        row.setFileVersionNo(5);
        row.setFileScopeVersion(6L);
        row.setFileFactVersion(JsonUtils.toJsonString(new FileFactVersion(2, 3, 4)));
        row.setFileHash("hash");
        row.setSourceRecordId(900L);
        row.setSourceVersion(3L);
        return row;
    }

    private static FileArtifactVersionFact fileFact(Long scopeVersion) {
        return new FileArtifactVersionFact(40L, 5, "REF-1", "RECEIPT", "签收单.pdf",
                128L, "application/pdf", "hash", "AVAILABLE", "ACTIVE",
                new FileFactVersion(2, 3, 4), scopeVersion);
    }

    private static ArrivalLineDO acceptedDeviceLine() {
        return acceptedDeviceLine(11L, 900L);
    }

    private static ArrivalLineDO acceptedDeviceLine(Long deviceId, Long acceptanceId) {
        ArrivalLineDO row = new ArrivalLineDO();
        row.setArrivalAcceptanceId(acceptanceId);
        row.setScopeType("DEVICE");
        row.setDeviceId(deviceId);
        row.setStatus("ACCEPTED");
        return row;
    }

    private static ArrivalLineDO acceptedQuantityLine(BigDecimal quantity) {
        ArrivalLineDO row = new ArrivalLineDO();
        row.setArrivalAcceptanceId(900L);
        row.setScopeType("ORDER_MODEL_QUANTITY");
        row.setOrderLineId(20L);
        row.setProductCode("PRODUCT-1");
        row.setModelCode("MODEL-1");
        row.setAcceptedQuantity(quantity);
        row.setUnit("台");
        row.setStatus("ACCEPTED");
        return row;
    }

    private static DeliveryScopePort.AssignedScope deliveryQuantityScope() {
        return new DeliveryScopePort.AssignedScope(100L, 8L, List.of(
                new DeliveryScopePort.AssignedLine(20L, new BigDecimal("5"),
                        "台", "PRODUCT-1", "MODEL-1", Set.of())));
    }

    private static DeliveryScopePort.AssignedScope deliveryScopeTwoDevices() {
        return new DeliveryScopePort.AssignedScope(100L, 8L, List.of(
                new DeliveryScopePort.AssignedLine(20L, new BigDecimal("2"),
                        "台", "PRODUCT-1", "MODEL-1", Set.of("SN-1", "SN-2"))));
    }

    private static DeviceScopeFactPort.DeviceScopeFact deviceScopeTwoDevices() {
        return new DeviceScopeFactPort.DeviceScopeFact(100L, List.of(
                new DeviceScopeFactPort.DeviceFact(11L, "SN-1", 100L, 9L),
                new DeviceScopeFactPort.DeviceFact(12L, "SN-2", 100L, 10L)));
    }

    private static ArrivalDifferenceDO openDifference() {
        ArrivalDifferenceDO row = new ArrivalDifferenceDO();
        row.setArrivalAcceptanceId(900L);
        row.setResolutionStatus("OPEN");
        return row;
    }

    private static ArrivalDifferenceDO exemptedDevice(Long acceptanceId, Long deviceId) {
        ArrivalDifferenceDO row = new ArrivalDifferenceDO();
        row.setArrivalAcceptanceId(acceptanceId);
        row.setResolutionStatus("EXEMPTED");
        row.setReason("approved reason");
        row.setRiskDescription("accepted risk");
        row.setScopeSnapshot(ArrivalDifferenceScopeCodec.serialize(
                new ArrivalDifferenceScopeCodec.DeviceScope(deviceId)));
        row.setApprovedBy(8L);
        row.setApprovedAt(LocalDateTime.now().minusHours(1));
        row.setEvidenceId(50L);
        row.setEvidenceRevision(1);
        row.setExemptionExpiresAt(LocalDateTime.now().plusDays(1));
        return row;
    }

    private static ArrivalDifferenceDO exemptedQuantity(BigDecimal quantity) {
        ArrivalDifferenceDO row = exemptedDevice(900L, 11L);
        row.setScopeSnapshot(ArrivalDifferenceScopeCodec.serialize(
                new ArrivalDifferenceScopeCodec.QuantityScope(
                        20L, "PRODUCT-1", "MODEL-1", quantity, "台")));
        return row;
    }

    private record SubmissionFixture(
            ArrivalAcceptanceApplicationService service,
            ArrivalAcceptanceMapper acceptanceMapper,
            ArrivalLineMapper lineMapper,
            ArrivalDifferenceMapper differenceMapper,
            DeliveryEvidenceMapper evidenceMapper,
            ProjectQualificationPort projectPort,
            DeliveryScopePort deliveryPort,
            DeviceScopeFactPort devicePort,
            FileArtifactFactPort filePort,
            RecordingCommandExecutionApi commandExecutionApi) {
    }

    private static final class RecordingCommandExecutionApi implements PlatformCommandExecutionApi {
        private Decision decision = Decision.NEW;
        private ConfirmationResultHolder replay;
        private IdempotencyScope scope;
        private String requestDigest;
        private SuccessFacts successFacts;
        private final List<String> requestDigests = new ArrayList<>();
        private final List<SuccessFacts> successFactsHistory = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                              Class<T> responseType, Supplier<T> operation,
                                              Function<T, SuccessFacts> successFactsFactory) {
            this.scope = scope;
            this.requestDigest = requestDigest;
            requestDigests.add(requestDigest);
            if (decision == Decision.REPLAY_COMPLETED) {
                return new ExecutionResult<>(decision, (T) replay.value());
            }
            if (decision == Decision.CONFLICT || decision == Decision.IN_PROGRESS) {
                return new ExecutionResult<>(decision, null);
            }
            T response = operation.get();
            successFacts = successFactsFactory.apply(response);
            if (successFacts == null || successFacts.correlationId() == null
                    || successFacts.correlationId().isBlank()
                    || successFacts.correlationId().length() > 128) {
                throw new IllegalArgumentException("platform command success facts are incomplete");
            }
            successFactsHistory.add(successFacts);
            return new ExecutionResult<>(decision, response);
        }
    }

    private record ConfirmationResultHolder(Object value) {
    }
}
