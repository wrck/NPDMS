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
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildRevisionMutation;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ArrivalDifferenceTypePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArrivalAcceptanceCommandServiceTest {

    @Test
    void patchesOwnedDraftByAppendingLineAndEvidenceRevisions() {
        Fixture fixture = fixture(draft(0));
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of());
        when(fixture.acceptanceMapper().mutateDraftIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceCommands.CommandResult result = fixture.service().patchDraft(
                new ArrivalAcceptanceCommands.PatchDraftCommand(1L, 900L, 8L, 0,
                        "L-2", null, null,
                        List.of(new ArrivalAcceptanceCommands.DeviceDraftLine(null, null, 11L, true)),
                        fileRevision()));

        assertEquals(1, result.aggregateVersion());
        ArgumentCaptor<ArrivalLineDO> line = ArgumentCaptor.forClass(ArrivalLineDO.class);
        verify(fixture.lineMapper()).insert(line.capture());
        assertEquals("ACCEPTED", line.getValue().getStatus());
        assertEquals(9L, line.getValue().getDeviceAssignmentVersion());
        verify(fixture.revisionMapper()).insert(any(DeliveryEvidenceRevisionDO.class));
    }

    @Test
    void partiallySupplementsQuantityAndPersistsExactRemainder() {
        ArrivalAcceptanceDO root = draft(1);
        root.setStatus("DIFFERENCE_PENDING");
        Fixture fixture = fixture(root);
        ArrivalLineDO line = quantityLine();
        ArrivalDifferenceDO difference = openQuantityDifference();
        difference.setArrivalLineId(9L); // 前一不可变明细revision；当前明细按严格scope身份重绑
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(line));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(difference));
        when(fixture.lineMapper().clearCurrentIfMatch(any())).thenReturn(1);
        when(fixture.differenceMapper().clearCurrentIfMatch(any())).thenReturn(1);
        when(fixture.acceptanceMapper().resolveDifferenceIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceCommands.CommandResult result = fixture.service().resolveDifference(
                new ArrivalAcceptanceCommands.ResolveDifferenceCommand(1L, 900L, 8L, 1,
                        new ArrivalAcceptanceCommands.Supplement(20L, 1, 0,
                                quantityScope("1"), "补签", fileRevision()), "resolve-key"));

        assertEquals("OPEN", result.resolutionStatus());
        assertEquals(new BigDecimal("2"),
                ((ArrivalDifferenceScopeCodec.QuantityScope) result.remainingScope()).quantity());
        ArgumentCaptor<ArrivalLineDO> appendedLine = ArgumentCaptor.forClass(ArrivalLineDO.class);
        verify(fixture.lineMapper()).insert(appendedLine.capture());
        assertEquals(new BigDecimal("3"), appendedLine.getValue().getAcceptedQuantity());
        ArgumentCaptor<ArrivalDifferenceDO> appendedDifference = ArgumentCaptor.forClass(ArrivalDifferenceDO.class);
        verify(fixture.differenceMapper()).insert(appendedDifference.capture());
        assertEquals(30L, appendedDifference.getValue().getArrivalLineId());
        assertEquals(new BigDecimal("2"), ArrivalDifferenceScopeCodec.parse(
                appendedDifference.getValue().getScopeSnapshot()) instanceof ArrivalDifferenceScopeCodec.QuantityScope q
                ? q.quantity() : null);
    }

    @Test
    void raisesDifferenceOnlyAfterEnabledTypeAndLockedFileFact() {
        Fixture fixture = fixture(draft(0));
        ArrivalLineDO line = quantityLine();
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(line));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of());
        when(fixture.acceptanceMapper().mutateDraftIfMatch(any())).thenReturn(1);

        fixture.service().raiseDifference(new ArrivalAcceptanceCommands.RaiseDifferenceCommand(
                1L, 900L, 8L, 0, 10L, 0, "QUANTITY_MISMATCH", quantityScope("1"),
                "少货", "影响交付", fileRevision(), "raise-key"));

        verify(fixture.differenceTypePort()).requireEnabled("QUANTITY_MISMATCH");
        verify(fixture.filePort()).lockAndRevalidateArrivalEvidence(any());
        verify(fixture.filePort(), never()).inspectArrivalEvidence(any(), any(), any(), any());
    }

    @Test
    void confirmedCloseCreatesLinearSuccessorWithoutMutatingSourceHistory() {
        ArrivalAcceptanceDO root = draft(2);
        root.setStatus("CONFIRMED");
        Fixture fixture = fixture(root);
        ArrivalLineDO line = quantityLine();
        ArrivalDifferenceDO difference = openQuantityDifference();
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(line));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(difference));
        when(fixture.differenceMapper().clearCurrentIfMatch(any())).thenReturn(1);
        when(fixture.acceptanceMapper().mutateDraftIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceCommands.CommandResult result = fixture.service().resolveDifference(
                new ArrivalAcceptanceCommands.ResolveDifferenceCommand(1L, 900L, 8L, 2,
                        new ArrivalAcceptanceCommands.Close(20L, 1, 0, "关闭", fileRevision()),
                        "resolve-key"));

        assertEquals(901L, result.successorAcceptanceId());
        ArgumentCaptor<ArrivalAcceptanceDO> successor = ArgumentCaptor.forClass(ArrivalAcceptanceDO.class);
        verify(fixture.acceptanceMapper()).insert(successor.capture());
        assertEquals("B-001", successor.getValue().getBatchCode());
        assertEquals(900L, successor.getValue().getPredecessorAcceptanceId());
        assertEquals("DIFFERENCE_CLOSURE", successor.getValue().getSuccessorReason());
        assertEquals(null, successor.getValue().getBatchRootMarker());
        verify(fixture.acceptanceMapper(), never()).resolveDifferenceIfMatch(any());
        ArgumentCaptor<ArrivalChildRevisionMutation> cleared =
                ArgumentCaptor.forClass(ArrivalChildRevisionMutation.class);
        verify(fixture.differenceMapper()).clearCurrentIfMatch(cleared.capture());
        assertEquals(901L, cleared.getValue().arrivalAcceptanceId());
        ArgumentCaptor<ArrivalDifferenceDO> successorDifferences =
                ArgumentCaptor.forClass(ArrivalDifferenceDO.class);
        verify(fixture.differenceMapper(), org.mockito.Mockito.times(2)).insert(successorDifferences.capture());
        assertEquals(List.of(901L, 901L), successorDifferences.getAllValues().stream()
                .map(ArrivalDifferenceDO::getArrivalAcceptanceId).toList());
        assertEquals(900L, root.getId());
        assertEquals("CONFIRMED", root.getStatus());
    }

    @Test
    void correctInformationCreatesSuccessorAndKeepsStableBatchCode() {
        ArrivalAcceptanceDO root = draft(3);
        root.setStatus("CONFIRMED");
        Fixture fixture = fixture(root);
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(quantityLine()));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of());
        when(fixture.acceptanceMapper().mutateDraftIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceCommands.CommandResult result = fixture.service().resolveDifference(
                new ArrivalAcceptanceCommands.ResolveDifferenceCommand(1L, 900L, 8L, 3,
                        new ArrivalAcceptanceCommands.CorrectInformation(3, "修正签收人",
                                new ArrivalAcceptanceCommands.CorrectionPatch(
                                        null, null, "新签收人", null), fileRevision()),
                        "correct-key"));

        assertEquals(901L, result.successorAcceptanceId());
        ArgumentCaptor<ArrivalAcceptanceDO> successor = ArgumentCaptor.forClass(ArrivalAcceptanceDO.class);
        verify(fixture.acceptanceMapper()).insert(successor.capture());
        assertEquals("B-001", successor.getValue().getBatchCode());
        assertEquals("CORRECTION", successor.getValue().getSuccessorReason());
        assertEquals(900L, successor.getValue().getPredecessorAcceptanceId());
        verify(fixture.acceptanceMapper()).mutateDraftIfMatch(any());
    }

    @Test
    void differentIntentCannotCreateSiblingSuccessor() {
        ArrivalAcceptanceDO root = draft(2);
        root.setStatus("CONFIRMED");
        Fixture fixture = fixture(root);
        ArrivalAcceptanceDO existing = draft(0);
        existing.setId(901L);
        existing.setPredecessorAcceptanceId(900L);
        existing.setSuccessorReason("SUPPLEMENT");
        when(fixture.acceptanceMapper().selectSuccessorForUpdate(any())).thenReturn(existing);
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(quantityLine()));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any()))
                .thenReturn(List.of(openQuantityDifference()));

        assertThrows(ArrivalAcceptanceCommandService.StateConflictException.class,
                () -> fixture.service().resolveDifference(
                        new ArrivalAcceptanceCommands.ResolveDifferenceCommand(1L, 900L, 8L, 2,
                                new ArrivalAcceptanceCommands.Close(20L, 1, 0, "关闭", fileRevision()),
                                "different-key")));

        verify(fixture.acceptanceMapper(), never()).insert(any(ArrivalAcceptanceDO.class));
        verify(fixture.lineMapper(), never()).insert(any(ArrivalLineDO.class));
        verify(fixture.differenceMapper(), never()).insert(any(ArrivalDifferenceDO.class));
    }

    @Test
    void partialQuantityExemptionDoesNotPromoteWholeLineToAccepted() {
        ArrivalAcceptanceDO root = draft(1);
        root.setStatus("DIFFERENCE_PENDING");
        Fixture fixture = fixture(root);
        ArrivalLineDO line = quantityLine();
        ArrivalDifferenceDO difference = openQuantityDifference();
        difference.setScopeSnapshot(ArrivalDifferenceScopeCodec.serialize(quantityScope("1")));
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(line));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(difference));
        when(fixture.differenceMapper().clearCurrentIfMatch(any())).thenReturn(1);
        when(fixture.acceptanceMapper().resolveDifferenceIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceCommands.CommandResult result = fixture.service().resolveDifference(
                new ArrivalAcceptanceCommands.ResolveDifferenceCommand(1L, 900L, 8L, 1,
                        new ArrivalAcceptanceCommands.Exempt(20L, 1, 0, "豁免", "已批准风险",
                                java.time.LocalDateTime.of(2026, 8, 31, 4, 0), fileRevision()), "resolve-key"));

        assertEquals("PARTIALLY_ACCEPTED", result.aggregateStatus());
    }

    @Test
    void expiresExemptionByCreatingFactAffectingSuccessorInOneCommand() {
        ArrivalAcceptanceDO root = draft(2);
        root.setStatus("CONFIRMED");
        Fixture fixture = fixture(root);
        ArrivalDifferenceDO exempt = openQuantityDifference();
        exempt.setResolutionStatus("EXEMPTED");
        exempt.setApprovedBy(8L);
        exempt.setApprovedAt(java.time.LocalDateTime.of(2026, 8, 29, 4, 0));
        exempt.setExemptionExpiresAt(java.time.LocalDateTime.of(2026, 8, 30, 3, 0));
        exempt.setEvidenceId(50L);
        exempt.setEvidenceRevision(1);
        when(fixture.differenceMapper().selectDueExemptions(any())).thenReturn(List.of(exempt));
        when(fixture.acceptanceMapper().selectRow(any())).thenReturn(root);
        when(fixture.lineMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(quantityLine()));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(exempt));
        when(fixture.differenceMapper().clearCurrentIfMatch(any())).thenReturn(1);
        when(fixture.acceptanceMapper().selectMaxAllocatedProjectFactVersion(any())).thenReturn(4L);
        when(fixture.revisionMapper().selectRevision(any())).thenReturn(storedEvidenceRevision());

        List<ArrivalAcceptanceCommands.CommandResult> results = fixture.service().expireExemptions(
                new ArrivalAcceptanceCommands.ExpireArrivalExemptionsCommand(10));

        assertEquals(1, results.size());
        assertEquals(5L, results.getFirst().projectFactVersion());
        assertEquals("EXEMPTION_INVALIDATION", results.getFirst().factImpactType());
        ArgumentCaptor<ArrivalDifferenceDO> revisions = ArgumentCaptor.forClass(ArrivalDifferenceDO.class);
        verify(fixture.differenceMapper(), org.mockito.Mockito.times(2)).insert(revisions.capture());
        ArrivalDifferenceDO invalidation = revisions.getAllValues().get(1);
        assertEquals("OPEN", invalidation.getResolutionStatus());
        assertEquals(5L, invalidation.getProjectFactVersion());
        assertEquals("EXEMPTION_INVALIDATION", invalidation.getFactImpactType());
        verify(fixture.acceptanceMapper(), never()).resolveDifferenceIfMatch(any());
        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(
                fixture.acceptanceMapper(), fixture.projectPort());
        lockOrder.verify(fixture.acceptanceMapper()).selectRow(any());
        lockOrder.verify(fixture.projectPort()).lockAndRevalidate(any());
        lockOrder.verify(fixture.acceptanceMapper()).selectForUpdate(any());
    }

    @Test
    void exemptionOwnerFailureLeavesSuccessorAndFactVersionUnwritten() {
        ArrivalAcceptanceDO root = draft(2);
        root.setStatus("CONFIRMED");
        Fixture fixture = fixture(root);
        ArrivalDifferenceDO exempt = openQuantityDifference();
        exempt.setResolutionStatus("EXEMPTED");
        exempt.setApprovedBy(8L);
        exempt.setExemptionExpiresAt(java.time.LocalDateTime.of(2026, 8, 30, 3, 0));
        when(fixture.differenceMapper().selectDueExemptions(any())).thenReturn(List.of(exempt));
        when(fixture.acceptanceMapper().selectRow(any())).thenReturn(root);
        when(fixture.projectPort().lockAndRevalidate(any()))
                .thenThrow(new IllegalStateException("project provider unavailable"));

        assertThrows(IllegalStateException.class, () -> fixture.service().expireExemptions(
                new ArrivalAcceptanceCommands.ExpireArrivalExemptionsCommand(10)));

        verify(fixture.acceptanceMapper(), never()).insert(any(ArrivalAcceptanceDO.class));
        verify(fixture.differenceMapper(), never()).insert(any(ArrivalDifferenceDO.class));
        verify(fixture.acceptanceMapper(), never()).selectMaxAllocatedProjectFactVersion(any());
    }

    @Test
    void commandServiceHasNoProductionBeanAnnotationBeforeTask12() {
        assertFalse(ArrivalAcceptanceCommandService.class.isAnnotationPresent(Service.class));
    }

    private static Fixture fixture(ArrivalAcceptanceDO root) {
        ArrivalAcceptanceMapper acceptance = mock(ArrivalAcceptanceMapper.class);
        ArrivalLineMapper lines = mock(ArrivalLineMapper.class);
        ArrivalDifferenceMapper differences = mock(ArrivalDifferenceMapper.class);
        DeliveryEvidenceMapper evidence = mock(DeliveryEvidenceMapper.class);
        DeliveryEvidenceRevisionMapper revisions = mock(DeliveryEvidenceRevisionMapper.class);
        ProjectQualificationPort project = mock(ProjectQualificationPort.class);
        DeliveryScopePort delivery = mock(DeliveryScopePort.class);
        DeviceScopeFactPort devices = mock(DeviceScopeFactPort.class);
        FileArtifactFactPort files = mock(FileArtifactFactPort.class);
        ArrivalDifferenceTypePort differenceTypes = mock(ArrivalDifferenceTypePort.class);
        PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
        when(acceptance.selectForUpdate(any())).thenReturn(root);
        when(acceptance.selectSuccessorForUpdate(any())).thenReturn(null);
        when(project.lockAndRevalidate(any())).thenReturn(new ProjectQualificationPort.ProjectQualificationFact(
                100L, 8L, Set.of("PROJECT_MANAGER"), "ACTIVE", "S4", 5, 6L, 7L));
        doAnswer(invocation -> {
            ArrivalAcceptanceDO value = invocation.getArgument(0);
            value.setId(901L);
            return 1;
        }).when(acceptance).insert(any(ArrivalAcceptanceDO.class));
        when(delivery.lockAndRevalidate(100L, 8L)).thenReturn(deliveryScope());
        when(devices.lockAndRevalidate(any(), any(), any())).thenReturn(deviceScope());
        when(files.lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact());
        doAnswer(invocation -> {
            DeliveryEvidenceDO value = invocation.getArgument(0);
            value.setId(50L);
            return 1;
        }).when(evidence).insert(any(DeliveryEvidenceDO.class));
        when(revisions.insert(any(DeliveryEvidenceRevisionDO.class))).thenReturn(1);
        doAnswer(invocation -> {
            ArrivalLineDO value = invocation.getArgument(0);
            value.setId(30L);
            return 1;
        }).when(lines).insert(any(ArrivalLineDO.class));
        when(differences.insert(any(ArrivalDifferenceDO.class))).thenAnswer(invocation -> {
            ArrivalDifferenceDO value = invocation.getArgument(0);
            value.setId(21L);
            return 1;
        });
        doAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object response = operation.get();
            facts.apply(response);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        }).when(commands).execute(any(), any(), any(), any(), any());
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T04:00:00Z"), ZoneOffset.UTC);
        return new Fixture(acceptance, lines, differences, evidence, revisions, project, files, differenceTypes,
                new ArrivalAcceptanceCommandService(acceptance, lines, differences, evidence, revisions,
                        project, delivery, devices, files, differenceTypes, commands, clock));
    }

    private static ArrivalAcceptanceDO draft(int version) {
        ArrivalAcceptanceDO root = new ArrivalAcceptanceDO();
        root.setId(900L);
        root.setTenantId(1L);
        root.setProjectId(100L);
        root.setBatchCode("B-001");
        root.setLogisticsNo("L-1");
        root.setArrivedAt(java.time.LocalDateTime.of(2026, 8, 29, 4, 0));
        root.setSignerSnapshot(JsonUtils.toJsonString(new ArrivalAcceptanceViews.SignerSnapshot("原签收人")));
        root.setStatus("DRAFT");
        root.setVersion(version);
        root.setCreator("8");
        root.setProjectVersion(5);
        root.setProjectParticipantFactVersion(6L);
        root.setProjectScopeVersion(7L);
        root.setDeliveryScopeVersion(8L);
        root.setScopeWatermark("{\"deliveryScopeVersion\":8}");
        root.setExpectedScopeSnapshot(JsonUtils.toJsonString(new ExpectedScopeSnapshot(
                deliveryScope().lines(), deviceScope().devices())));
        return root;
    }

    private static ArrivalLineDO quantityLine() {
        ArrivalLineDO line = new ArrivalLineDO();
        line.setId(10L);
        line.setTenantId(1L);
        line.setArrivalAcceptanceId(900L);
        line.setLineNo(1);
        line.setLineRevision(1);
        line.setScopeType("ORDER_MODEL_QUANTITY");
        line.setOrderLineId(200L);
        line.setProductCode("P-1");
        line.setModelCode("M-1");
        line.setExpectedQuantity(new BigDecimal("5"));
        line.setAcceptedQuantity(new BigDecimal("2"));
        line.setUnit("台");
        line.setStatus("ACCEPTED");
        line.setCurrentMarker(1);
        line.setVersion(0);
        return line;
    }

    private static ArrivalDifferenceDO openQuantityDifference() {
        ArrivalDifferenceDO difference = new ArrivalDifferenceDO();
        difference.setId(20L);
        difference.setTenantId(1L);
        difference.setArrivalAcceptanceId(900L);
        difference.setArrivalLineId(10L);
        difference.setDifferenceNo(1);
        difference.setRevisionNo(1);
        difference.setDifferenceType("SHORTAGE");
        difference.setResolutionStatus("OPEN");
        difference.setReason("少货");
        difference.setRiskDescription("风险");
        difference.setScopeSnapshot(ArrivalDifferenceScopeCodec.serialize(quantityScope("3")));
        difference.setCurrentMarker(1);
        difference.setVersion(0);
        return difference;
    }

    private static ArrivalDifferenceScopeCodec.QuantityScope quantityScope(String quantity) {
        return new ArrivalDifferenceScopeCodec.QuantityScope(200L, "P-1", "M-1",
                new BigDecimal(quantity), "台");
    }

    private static DeliveryScopePort.AssignedScope deliveryScope() {
        return new DeliveryScopePort.AssignedScope(100L, 8L, List.of(
                new DeliveryScopePort.AssignedLine(200L, new BigDecimal("5"), "台",
                        "P-1", "M-1", Set.of("SN-1"))));
    }

    private static DeviceScopeFactPort.DeviceScopeFact deviceScope() {
        return new DeviceScopeFactPort.DeviceScopeFact(100L,
                List.of(new DeviceScopeFactPort.DeviceFact(11L, "SN-1", 100L, 9L)));
    }

    private static ArrivalAcceptanceCommands.FileRevision fileRevision() {
        return new ArrivalAcceptanceCommands.FileRevision(70L, "ref-1", 2, 6L,
                new FileFactVersion(3, 4, 5), "hash-1");
    }

    private static FileArtifactVersionFact fileFact() {
        return new FileArtifactVersionFact(70L, 2, "ref-1", "RECEIPT", "receipt.pdf",
                10L, "application/pdf", "hash-1", "AVAILABLE", "ACTIVE",
                new FileFactVersion(3, 4, 5), 6L);
    }

    private static DeliveryEvidenceRevisionDO storedEvidenceRevision() {
        DeliveryEvidenceRevisionDO revision = new DeliveryEvidenceRevisionDO();
        revision.setTenantId(1L);
        revision.setEvidenceId(50L);
        revision.setRevisionNo(1);
        revision.setFileArtifactId(70L);
        revision.setFileReferenceId("ref-1");
        revision.setFileVersionNo(2);
        revision.setFileScopeVersion(6L);
        revision.setFileFactVersion(JsonUtils.toJsonString(new FileFactVersion(3, 4, 5)));
        revision.setFileHash("hash-1");
        revision.setSourceRecordId(900L);
        revision.setSourceVersion(2L);
        return revision;
    }

    private record ExpectedScopeSnapshot(List<DeliveryScopePort.AssignedLine> deliveryLines,
                                         List<DeviceScopeFactPort.DeviceFact> devices) {
    }

    private record Fixture(ArrivalAcceptanceMapper acceptanceMapper, ArrivalLineMapper lineMapper,
                           ArrivalDifferenceMapper differenceMapper, DeliveryEvidenceMapper evidenceMapper,
                           DeliveryEvidenceRevisionMapper revisionMapper, ProjectQualificationPort projectPort,
                           FileArtifactFactPort filePort, ArrivalDifferenceTypePort differenceTypePort,
                           ArrivalAcceptanceCommandService service) {
    }
}
