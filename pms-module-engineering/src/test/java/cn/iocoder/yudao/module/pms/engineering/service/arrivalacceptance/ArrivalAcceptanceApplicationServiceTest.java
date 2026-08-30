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
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
        ArrivalAcceptanceApplicationService service = new ArrivalAcceptanceApplicationService(
                mapper, mock(ArrivalLineMapper.class), mock(ArrivalDifferenceMapper.class),
                mock(DeliveryEvidenceMapper.class),
                mock(DeliveryEvidenceRevisionMapper.class), projectPort, deliveryPort, devicePort,
                mock(FileArtifactFactPort.class));

        ArrivalAcceptanceDO created = service.createDraft(command());

        assertEquals(900L, created.getId());
        assertEquals("DRAFT", created.getStatus());
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
                mock(FileArtifactFactPort.class));

        assertThrows(IllegalStateException.class, () -> service.createDraft(command()));

        verify(mapper, never()).insert(any(ArrivalAcceptanceDO.class));
    }

    @Test
    void submitsCompleteDeviceScopeWithFrozenOwnerFacts() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                new ArrivalAcceptanceApplicationService.SubmitCommand(1L, 900L, 8L, 0));

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
    void rejectsChangedFileScopeBeforeWritingSubmission() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(7L));

        assertThrows(IllegalStateException.class, () -> fixture.service().submit(
                new ArrivalAcceptanceApplicationService.SubmitCommand(1L, 900L, 8L, 0)));

        verify(fixture.acceptanceMapper(), never()).updateSubmittedIfMatch(any());
        verify(fixture.lineMapper(), never()).selectCurrentListForUpdate(any());
    }

    @Test
    void submitsOpenDifferenceToDifferencePending() {
        SubmissionFixture fixture = submissionFixture();
        when(fixture.filePort().lockAndRevalidateArrivalEvidence(any())).thenReturn(fileFact(6L));
        when(fixture.differenceMapper().selectCurrentListForUpdate(any())).thenReturn(List.of(openDifference()));
        when(fixture.acceptanceMapper().updateSubmittedIfMatch(any())).thenReturn(1);

        ArrivalAcceptanceApplicationService.SubmissionResult result = fixture.service().submit(
                new ArrivalAcceptanceApplicationService.SubmitCommand(1L, 900L, 8L, 0));

        assertEquals("DIFFERENCE_PENDING", result.status());
    }

    private static ArrivalAcceptanceApplicationService.CreateDraftCommand command() {
        return new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                1L, 100L, 8L, "ARRIVAL-001", "LOGISTICS-001",
                LocalDateTime.of(2026, 8, 30, 9, 0), "客户签收人");
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
                projectPort, deliveryPort, devicePort, filePort);
        return new SubmissionFixture(service, acceptanceMapper, lineMapper, differenceMapper,
                projectPort, deliveryPort, devicePort, filePort);
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

    private static DeliveryEvidenceDO evidence() {
        DeliveryEvidenceDO row = new DeliveryEvidenceDO();
        row.setId(50L);
        row.setTenantId(1L);
        row.setProjectId(100L);
        row.setCurrentRevisionNo(1);
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
        return row;
    }

    private static FileArtifactVersionFact fileFact(Long scopeVersion) {
        return new FileArtifactVersionFact(40L, 5, "REF-1", "RECEIPT", "签收单.pdf",
                128L, "application/pdf", "hash", "AVAILABLE", "ACTIVE",
                new FileFactVersion(2, 3, 4), scopeVersion);
    }

    private static ArrivalLineDO acceptedDeviceLine() {
        ArrivalLineDO row = new ArrivalLineDO();
        row.setArrivalAcceptanceId(900L);
        row.setScopeType("DEVICE");
        row.setDeviceId(11L);
        row.setStatus("ACCEPTED");
        return row;
    }

    private static ArrivalDifferenceDO openDifference() {
        ArrivalDifferenceDO row = new ArrivalDifferenceDO();
        row.setArrivalAcceptanceId(900L);
        row.setResolutionStatus("OPEN");
        return row;
    }

    private record SubmissionFixture(
            ArrivalAcceptanceApplicationService service,
            ArrivalAcceptanceMapper acceptanceMapper,
            ArrivalLineMapper lineMapper,
            ArrivalDifferenceMapper differenceMapper,
            ProjectQualificationPort projectPort,
            DeliveryScopePort deliveryPort,
            DeviceScopeFactPort devicePort,
            FileArtifactFactPort filePort) {
    }
}
