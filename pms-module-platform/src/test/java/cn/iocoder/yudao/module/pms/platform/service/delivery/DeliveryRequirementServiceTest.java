package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliverySubmissionMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryRequirementService.SubmissionOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交付要求/提交服务语义验证：requestKey 幂等重放、旧 CURRENT 提交取代、
 * 三种计数单位口径（材料/文件版本去重/提交）、撤回后确认退回 OPEN、归属校验拒绝。
 */
@ExtendWith(MockitoExtension.class)
class DeliveryRequirementServiceTest {

    private static final String OWNER_MODULE = "demo";
    private static final String ENTITY_TYPE = "ticket";
    private static final Long ENTITY_ID = 5L;
    private static final String TYPE_CODE = "RECEIPT";

    @Mock DeliveryRequirementMapper requirementMapper;
    @Mock DeliverySubmissionMapper submissionMapper;
    @Mock DeliveryMaterialMapper materialMapper;
    @Mock DeliveryCatalogService catalogService;
    @Mock DeliveryMaterialService materialService;
    @Mock PlatformBusinessEventApi outbox;

    private DeliveryRequirementService service;

    private final List<DeliveryMaterialDO> entityMaterials = new ArrayList<>();
    private final List<DeliverySubmissionDO> requirementSubmissions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeliveryRequirementService(requirementMapper, submissionMapper,
                materialMapper, catalogService, materialService, outbox);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private DeliveryRequirementDO requirement(long id, String status, String countingUnit, int minimum) {
        DeliveryRequirementDO row = new DeliveryRequirementDO();
        row.setId(id);
        row.setOwnerModule(OWNER_MODULE);
        row.setEntityType(ENTITY_TYPE);
        row.setEntityId(ENTITY_ID);
        row.setTypeCode(TYPE_CODE);
        row.setRequired(true);
        row.setMinimumQuantity(minimum);
        row.setCountingUnit(countingUnit);
        row.setStatus(status);
        if (DeliveryRequirementDO.STATUS_CONFIRMED.equals(status)) {
            row.setConfirmedBy("9");
        }
        return row;
    }

    private DeliveryMaterialDO material(long id, long artifactId, int versionNo) {
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setId(id);
        row.setOwnerModule(OWNER_MODULE);
        row.setEntityType(ENTITY_TYPE);
        row.setEntityId(ENTITY_ID);
        row.setTypeCode(TYPE_CODE);
        row.setFileReferenceId(1000L + id);
        row.setFileArtifactId(artifactId);
        row.setFileVersionNo(versionNo);
        row.setFileSha256("sha-" + id);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        entityMaterials.add(row);
        return row;
    }

    private DeliverySubmissionDO submission(long id, String requestKey, String status,
                                            List<Long> materialIds) {
        DeliverySubmissionDO row = new DeliverySubmissionDO();
        row.setId(id);
        row.setRequirementId(10L);
        row.setRequestKey(requestKey);
        row.setMaterialIdsJson(materialIds.stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).map(v -> "[" + v + "]").orElse("[]"));
        row.setStatus(status);
        requirementSubmissions.add(row);
        return row;
    }

    private void stubEntityState() {
        org.mockito.Mockito.lenient().when(materialMapper.selectByEntity(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE))
                .thenAnswer(invocation -> new ArrayList<>(entityMaterials));
        org.mockito.Mockito.lenient().when(submissionMapper.selectByRequirement(10L))
                .thenAnswer(invocation -> new ArrayList<>(requirementSubmissions));
        org.mockito.Mockito.lenient().when(materialMapper.selectByMaterialIds(any()))
                .thenAnswer(invocation -> entityMaterials.stream()
                        .filter(material -> ((List<Long>) invocation.getArgument(0)).contains(material.getId()))
                        .toList());
        org.mockito.Mockito.lenient().when(materialService.revalidateActive(any())).thenAnswer(invocation -> {
            DeliveryMaterialDO material = invocation.getArgument(0);
            return new FileEvidenceApi.Document(material.getFileReferenceId(),
                    DeliveryMaterialService.FILE_OWNER_CONTEXT, DeliveryMaterialService.FILE_OBJECT_TYPE,
                    DeliveryMaterialService.fileObjectId(material.getOwnerModule(), material.getEntityType(),
                            material.getEntityId()), material.getTypeCode(), "ref-key",
                    material.getFileArtifactId(), material.getFileVersionNo(), material.getFileSha256(),
                    material.getFileName(), true);
        });
    }

    @Test
    void materialUnitCountsOnlyActiveMaterialsNotExclusivelySuperseded() {
        material(1, 100, 1);
        material(2, 101, 1);
        submission(21, "k-old", "WITHDRAWN", List.of(2L));
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        when(materialMapper.selectByEntity(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE))
                .thenReturn(entityMaterials);
        when(submissionMapper.selectByRequirement(10L)).thenReturn(requirementSubmissions);

        assertEquals(1, service.countOf(requirement));
    }

    @Test
    void fileVersionUnitDeduplicatesSameArtifactVersion() {
        material(1, 100, 1);
        material(2, 100, 1);
        material(3, 101, 1);
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_FILE_VERSION, 2);
        when(materialMapper.selectByEntity(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE))
                .thenReturn(entityMaterials);

        assertEquals(2, service.countOf(requirement));
    }

    @Test
    void submissionUnitCountsOnlyCurrentSubmissions() {
        submission(21, "k1", "CURRENT", List.of(1L));
        submission(22, "k2", "SUPERSEDED", List.of(1L));
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_SUBMISSION, 1);
        when(materialMapper.selectByEntity(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE))
                .thenReturn(List.of());
        when(submissionMapper.selectByRequirement(10L)).thenReturn(requirementSubmissions);

        assertEquals(1, service.countOf(requirement));
    }

    @Test
    void submitReplacesCurrentSubmissionAndSatisfiesRequirement() {
        material(1, 100, 1);
        DeliverySubmissionDO superseded = submission(21, "k-old", "CURRENT", List.of(1L));
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        stubEntityState();
        when(submissionMapper.selectByRequestKey("k-new")).thenReturn(java.util.Optional.empty());
        when(requirementMapper.selectById(10L)).thenReturn(requirement);
        when(submissionMapper.insert(any(DeliverySubmissionDO.class))).thenAnswer(invocation -> {
            requirementSubmissions.add(invocation.getArgument(0));
            return 1;
        });

        SubmissionOutcome outcome = service.submit(10L, "k-new", List.of(1L));

        assertFalse(outcome.replay());
        assertEquals(DeliveryRequirementDO.STATUS_SATISFIED, requirement.getStatus());
        ArgumentCaptor<DeliverySubmissionDO> updated = ArgumentCaptor.forClass(DeliverySubmissionDO.class);
        verify(submissionMapper).updateById(updated.capture());
        assertEquals(DeliverySubmissionDO.STATUS_SUPERSEDED, updated.getValue().getStatus());
        assertEquals(superseded.getId(), updated.getValue().getId());
        ArgumentCaptor<DeliverySubmissionDO> inserted = ArgumentCaptor.forClass(DeliverySubmissionDO.class);
        verify(submissionMapper).insert(inserted.capture());
        assertEquals(DeliverySubmissionDO.STATUS_CURRENT, inserted.getValue().getStatus());
        assertEquals("k-new", inserted.getValue().getRequestKey());
        // SUBMITTED + 数量满足触发的 STATUS_REFRESHED 两条事件留痕。
        verify(outbox, org.mockito.Mockito.times(2)).append(eq("DeliveryRequirement"), eq("10"),
                any(PlatformCommandExecutionApi.BusinessEvent.class));
    }

    @Test
    void submitReplaysByIdempotencyKeyWithoutInserting() {
        DeliverySubmissionDO existing = submission(21, "k-old", "CURRENT", List.of(1L));
        DeliveryRequirementDO requirement = requirement(10L, "SATISFIED",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        when(submissionMapper.selectByRequestKey("k-old")).thenReturn(java.util.Optional.of(existing));
        when(requirementMapper.selectById(10L)).thenReturn(requirement);
        when(materialMapper.selectByEntity(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE))
                .thenReturn(List.of());

        SubmissionOutcome outcome = service.submit(99L, "k-old", List.of(1L));

        assertTrue(outcome.replay());
        assertEquals(existing.getId(), outcome.submission().getId());
        verify(submissionMapper, never()).insert(any(DeliverySubmissionDO.class));
        verify(outbox, never()).append(anyString(), anyString(), any());
    }

    @Test
    void submitRejectsMaterialOfAnotherEntity() {
        DeliveryMaterialDO foreign = material(1, 100, 1);
        foreign.setEntityId(999L);
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        stubEntityState();
        when(submissionMapper.selectByRequestKey("k1")).thenReturn(java.util.Optional.empty());
        when(requirementMapper.selectById(10L)).thenReturn(requirement);

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.submit(10L, "k1", List.of(1L)));
        assertEquals("DELIVERY_MATERIAL_OWNER_MISMATCH", ex.getErrorCode());
        verify(submissionMapper, never()).insert(any(DeliverySubmissionDO.class));
    }

    @Test
    void submitRejectsDuplicateRequestKeyOnRaceConflict() {
        material(1, 100, 1);
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        stubEntityState();
        when(submissionMapper.selectByRequestKey("k-race")).thenReturn(java.util.Optional.empty());
        when(requirementMapper.selectById(10L)).thenReturn(requirement);
        when(submissionMapper.insert(any(DeliverySubmissionDO.class)))
                .thenThrow(new DuplicateKeyException("uk"));

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.submit(10L, "k-race", List.of(1L)));
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", ex.getErrorCode());
    }

    @Test
    void withdrawSubmissionRevertsConfirmedRequirementToOpen() {
        material(1, 100, 1);
        submission(21, "k1", "CURRENT", List.of(1L));
        DeliveryRequirementDO requirement = requirement(10L, "CONFIRMED",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        when(submissionMapper.selectById(21L)).thenReturn(requirementSubmissions.get(0));
        when(requirementMapper.selectById(10L)).thenReturn(requirement);
        when(submissionMapper.selectByRequirement(10L)).thenReturn(requirementSubmissions);
        when(materialMapper.selectByEntity(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE))
                .thenReturn(new ArrayList<>(entityMaterials));

        SubmissionOutcome outcome = service.withdrawSubmission(21L);

        assertEquals(DeliverySubmissionDO.STATUS_WITHDRAWN, outcome.submission().getStatus());
        assertEquals(DeliveryRequirementDO.STATUS_OPEN, requirement.getStatus());
        assertNull(requirement.getConfirmedBy());
        verify(requirementMapper).updateById(requirement);
    }

    @Test
    void withdrawRejectsNonCurrentSubmission() {
        when(submissionMapper.selectById(22L))
                .thenReturn(submission(22L, "k2", "SUPERSEDED", List.of(1L)));

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.withdrawSubmission(22L));
        assertEquals("DELIVERY_SUBMISSION_NOT_CURRENT", ex.getErrorCode());
    }

    @Test
    void evidenceInvalidationDuringSubmitFailsExplicitly() {
        material(1, 100, 1);
        DeliveryRequirementDO requirement = requirement(10L, "OPEN",
                DeliveryTypeDO.COUNTING_MATERIAL, 1);
        when(materialMapper.selectByMaterialIds(any()))
                .thenAnswer(invocation -> new ArrayList<>(entityMaterials));
        when(materialService.revalidateActive(any()))
                .thenThrow(new BusinessContractException("DELIVERY_FILE_UNAVAILABLE", "失效"));
        when(submissionMapper.selectByRequestKey("k-x")).thenReturn(java.util.Optional.empty());
        when(requirementMapper.selectById(10L)).thenReturn(requirement);

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.submit(10L, "k-x", List.of(1L)));
        assertEquals("DELIVERY_FILE_UNAVAILABLE", ex.getErrorCode());
        verify(submissionMapper, never()).insert(any(DeliverySubmissionDO.class));
    }

    @Test
    void registrationValidatesDocumentOwnerBeforeFreezingEvidence() {
        FileEvidenceApi.Document foreign = new FileEvidenceApi.Document(5001L, "PLT",
                "DELIVERY_MATERIAL", "demo:ticket:999", TYPE_CODE, "ref-key", 100L, 1, "sha-1",
                "receipt.pdf", true);
        DeliveryMaterialService materialSvc = new DeliveryMaterialService(materialMapper,
                catalogService, new FileEvidenceApi() {
                    @Override
                    public FileEvidenceApi.Document inspectDocument(Long tenantId, Long referenceId) {
                        return foreign;
                    }

                    @Override
                    public FileEvidenceApi.Fact lockAndRevalidate(Query query) {
                        throw new AssertionError("登记路径不应触发锁重验");
                    }
                }, eventPublisher());
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> materialSvc.register(OWNER_MODULE, ENTITY_TYPE, ENTITY_ID, TYPE_CODE,
                        5001L, "签收单", null));
        assertEquals("DELIVERY_FILE_OWNER_MISMATCH", ex.getErrorCode());
        verify(materialMapper, never()).insert(any(DeliveryMaterialDO.class));
    }

    private DeliveryEventPublisher eventPublisher() {
        return new DeliveryEventPublisher(outbox);
    }
}
