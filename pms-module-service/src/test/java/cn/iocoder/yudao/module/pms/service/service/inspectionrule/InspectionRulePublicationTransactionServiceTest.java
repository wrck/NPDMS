package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleCommandRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleSecurityReviewDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleCommandRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleSecurityReviewMapper;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleContentDigestService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleSecurityReviewPermissionGuard;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleProductTypeNameUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRulePublishUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class InspectionRulePublicationTransactionServiceTest {

    private static final long TENANT_ID = 7L;
    private static final long RULE_ID = 10L;
    private static final long TARGET_ID = 20L;
    private static final long CURRENT_ID = 21L;
    private static final long ACTOR_ID = 9L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 9, 2, 17, 30);

    @Mock
    private InspectionRuleMapper ruleMapper;
    @Mock
    private InspectionRuleRevisionMapper revisionMapper;
    @Mock
    private InspectionRuleCommandRevisionMapper commandMapper;
    @Mock
    private InspectionRuleProductTypeRevisionMapper productTypeMapper;
    @Mock
    private InspectionRuleSecurityReviewMapper securityReviewMapper;
    @Mock
    private InspectionAssetProductTypeApi assetProductTypeApi;
    @Mock
    private DictDataApi dictDataApi;

    private InspectionRulePublicationTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionRulePublicationTransactionService(
                ruleMapper,
                revisionMapper,
                commandMapper,
                productTypeMapper,
                securityReviewMapper,
                assetProductTypeApi,
                dictDataApi,
                new InspectionRuleContentDigestService());
    }

    @Test
    void shouldRefreshSnapshotsAndAtomicallyReplaceCurrentPublishedRevision() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", CURRENT_ID, 1, 4));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(
                productType("OLD-A", "A"), productType("OLD-B", "B")));
        when(productTypeMapper.updateNameSnapshot(any())).thenReturn(1);
        when(revisionMapper.disablePublishedIfMatch(any())).thenReturn(1);
        when(revisionMapper.publishDraftIfMatch(any())).thenReturn(1);

        InspectionRulePublicationTransactionService.PublishResult result = service.publishVerified(
                command(Map.of("A", "权威A", "B", "权威B")));

        assertEquals(TARGET_ID, result.revisionId());
        assertEquals(4, result.version());
        assertEquals(CURRENT_ID, result.disabledRevisionId());
        InOrder writes = inOrder(productTypeMapper, revisionMapper);
        writes.verify(productTypeMapper).updateNameSnapshot(new InspectionRuleProductTypeNameUpdate(
                TENANT_ID, TARGET_ID, "A", "权威A"));
        writes.verify(productTypeMapper).updateNameSnapshot(new InspectionRuleProductTypeNameUpdate(
                TENANT_ID, TARGET_ID, "B", "权威B"));
        writes.verify(revisionMapper).disablePublishedIfMatch(new InspectionRuleDisableUpdate(
                TENANT_ID, CURRENT_ID, 4, ACTOR_ID, PUBLISHED_AT));
        writes.verify(revisionMapper).publishDraftIfMatch(new InspectionRulePublishUpdate(
                TENANT_ID, TARGET_ID, 3, "权威分类", "权威严重度", ACTOR_ID, PUBLISHED_AT));
    }

    @Test
    void shouldPublishWithoutDisablingWhenNoCurrentRevisionExists() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", null, null, null));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(
                productType("OLD-A", "A"), productType("OLD-B", "B")));
        when(productTypeMapper.updateNameSnapshot(any())).thenReturn(1);
        when(revisionMapper.publishDraftIfMatch(any())).thenReturn(1);

        InspectionRulePublicationTransactionService.PublishCommand command =
                new InspectionRulePublicationTransactionService.PublishCommand(
                        TENANT_ID, TARGET_ID, 3, null, "权威分类", "权威严重度",
                        new LinkedHashMap<>(Map.of("A", "权威A", "B", "权威B")), ACTOR_ID, PUBLISHED_AT);

        InspectionRulePublicationTransactionService.PublishResult result = service.publishVerified(command);

        assertEquals(null, result.disabledRevisionId());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper).publishDraftIfMatch(any());
    }

    @Test
    void shouldRejectSnapshotCodeMismatchBeforeLifecycleWrites() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", CURRENT_ID, 1, 4));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(
                productType("OLD-A", "A"), productType("OLD-B", "B")));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishVerified(command(Map.of("A", "权威A"))));

        assertEquals(1_013_002_006, failure.getCode());
        verify(productTypeMapper, never()).updateNameSnapshot(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    @Test
    void shouldRejectWhenCurrentPublishedRevisionChangedAfterVerification() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", 99L, 2, 1));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishVerified(command(Map.of("A", "权威A", "B", "权威B"))));

        assertEquals(1_013_002_007, failure.getCode());
        verify(productTypeMapper, never()).selectListByRevisionIds(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    @Test
    void shouldRejectStaleVersionBeforeSnapshotOrLifecycleWrites() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 4));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishVerified(command(Map.of("A", "权威A", "B", "权威B"))));

        assertEquals(1_013_002_007, failure.getCode());
        verify(revisionMapper, never()).selectPublicationLockForUpdate(any());
        verify(productTypeMapper, never()).updateNameSnapshot(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    @Test
    void shouldAppendSecurityReviewFactForDraftUnderAggregateLock() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", CURRENT_ID, 1, 4));
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(commandRow()));
        when(securityReviewMapper.insert(any(InspectionRuleSecurityReviewDO.class))).thenReturn(1);

        InspectionRulePublicationTransactionService.SecurityReviewResult result =
                service.recordSecurityReview(new InspectionRulePublicationTransactionService.SecurityReviewCommand(
                        TENANT_ID, TARGET_ID, 3, "PASSED",
                        new InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization(
                                ACTOR_ID, "pms:inspection-rule:security-review", "RBAC_PERMISSION", null),
                        PUBLISHED_AT));

        assertEquals(TARGET_ID, result.revisionId());
        assertEquals("PASSED", result.conclusionCode());
        assertEquals(64, result.contentDigest().length());
        ArgumentCaptor<InspectionRuleSecurityReviewDO> review =
                ArgumentCaptor.forClass(InspectionRuleSecurityReviewDO.class);
        verify(securityReviewMapper).insert(review.capture());
        assertEquals(ACTOR_ID, review.getValue().getReviewedBy());
        assertEquals("RBAC_PERMISSION", review.getValue().getAuthorizationType());
        assertEquals(null, review.getValue().getAuthorizationSourceId());
    }

    @Test
    void shouldPublishOnlyWhenLatestExactDigestReviewPassed() {
        prepareApprovedPublication("PASSED");
        when(productTypeMapper.updateNameSnapshot(any())).thenReturn(1);
        when(revisionMapper.disablePublishedIfMatch(any())).thenReturn(1);
        when(revisionMapper.publishDraftIfMatch(any())).thenReturn(1);

        InspectionRulePublicationTransactionService.ApprovedPublishResult result =
                service.publishApproved(approvedCommand());

        assertEquals(TARGET_ID, result.revisionId());
        assertEquals(CURRENT_ID, result.disabledRevisionId());
        assertEquals("review-latest", result.reviewReference());
        assertEquals(64, result.contentDigest().length());
        verify(assetProductTypeApi).getByCodes(any());
        verify(securityReviewMapper).selectLatestByRevisionAndDigest(any());
        verify(revisionMapper).disablePublishedIfMatch(any());
        verify(revisionMapper).publishDraftIfMatch(any());
    }

    @Test
    void shouldKeepOldPublishedRevisionWhenLatestExactDigestReviewRejected() {
        prepareApprovedPublication("REJECTED");

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishApproved(approvedCommand()));

        assertEquals(1_013_002_006, failure.getCode());
        verify(productTypeMapper, never()).updateNameSnapshot(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    @Test
    void shouldFailClosedBeforeReviewLookupWhenAssetContractIsUnavailable() {
        prepareDraftForPublication();
        when(assetProductTypeApi.getByCodes(any())).thenThrow(new IllegalStateException("AST unavailable"));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishApproved(approvedCommand()));

        assertEquals(1_013_002_006, failure.getCode());
        verify(securityReviewMapper, never()).selectLatestByRevisionAndDigest(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
    }

    private void prepareApprovedPublication(String conclusion) {
        prepareDraftForPublication();
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(new ProductTypeCodeResult(
                "A", true, true, "权威A", "CRM", "v1", "SYNCED", PUBLISHED_AT.minusDays(1), false)));
        InspectionRuleSecurityReviewDO latest = new InspectionRuleSecurityReviewDO();
        latest.setReviewReference("review-latest");
        latest.setConclusionCode(conclusion);
        when(securityReviewMapper.selectLatestByRevisionAndDigest(any())).thenReturn(latest);
    }

    private void prepareDraftForPublication() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(completeRevision());
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", CURRENT_ID, 1, 4));
        when(ruleMapper.selectById(RULE_ID)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(commandRow()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(productType("草稿A", "A")));
        when(dictDataApi.getDictDataList("pms_inspection_rule_category"))
                .thenReturn(List.of(dictData("pms_inspection_rule_category", "BASIC", "权威分类")));
        when(dictDataApi.getDictDataList("pms_inspection_rule_severity"))
                .thenReturn(List.of(dictData("pms_inspection_rule_severity", "GENERAL", "权威严重度")));
    }

    private static InspectionRulePublicationTransactionService.ApprovedPublishCommand approvedCommand() {
        return new InspectionRulePublicationTransactionService.ApprovedPublishCommand(
                TENANT_ID, TARGET_ID, 3, CURRENT_ID, ACTOR_ID, PUBLISHED_AT);
    }

    private static InspectionRulePublicationTransactionService.PublishCommand command(
            Map<String, String> productTypeNames) {
        return new InspectionRulePublicationTransactionService.PublishCommand(
                TENANT_ID,
                TARGET_ID,
                3,
                CURRENT_ID,
                "权威分类",
                "权威严重度",
                new LinkedHashMap<>(productTypeNames),
                ACTOR_ID,
                PUBLISHED_AT);
    }

    private static InspectionRuleRevisionDO revision(String status, int version) {
        InspectionRuleRevisionDO revision = new InspectionRuleRevisionDO();
        revision.setId(TARGET_ID);
        revision.setTenantId(TENANT_ID);
        revision.setRuleId(RULE_ID);
        revision.setStatusCode(status);
        revision.setVersion(version);
        return revision;
    }

    private static InspectionRuleRevisionDO completeRevision() {
        InspectionRuleRevisionDO revision = revision("DRAFT", 3);
        revision.setRuleNameSnapshot("核心状态检查");
        revision.setInspectionItem("运行状态");
        revision.setDescription("检查核心设备运行状态");
        revision.setCategoryCode("BASIC");
        revision.setSeverityCode("GENERAL");
        revision.setSortOrder(1);
        revision.setExpectedResultRegex("^UP$");
        revision.setThresholdDataType("NUMBER");
        revision.setThresholdOperator("=");
        revision.setThresholdValue(BigDecimal.ONE);
        revision.setThresholdUnit("count");
        return revision;
    }

    private static InspectionRuleDO stableRule() {
        InspectionRuleDO rule = new InspectionRuleDO();
        rule.setId(RULE_ID);
        rule.setTenantId(TENANT_ID);
        rule.setDetectionId("DET-001");
        rule.setRuleName("核心状态检查");
        rule.setVersion(0);
        return rule;
    }

    private static InspectionRuleCommandRevisionDO commandRow() {
        InspectionRuleCommandRevisionDO command = new InspectionRuleCommandRevisionDO();
        command.setTenantId(TENANT_ID);
        command.setRevisionId(TARGET_ID);
        command.setStableCommandKey("status");
        command.setCommandContent("show status");
        command.setExecutionOrder(1);
        command.setTimeoutSeconds(30);
        command.setContinueOnTimeout(false);
        return command;
    }

    private static DictDataRespDTO dictData(String dictType, String value, String label) {
        DictDataRespDTO data = new DictDataRespDTO();
        data.setDictType(dictType);
        data.setValue(value);
        data.setLabel(label);
        data.setStatus(0);
        return data;
    }

    private static InspectionRuleProductTypeRevisionDO productType(String name, String code) {
        InspectionRuleProductTypeRevisionDO productType = new InspectionRuleProductTypeRevisionDO();
        productType.setTenantId(TENANT_ID);
        productType.setRevisionId(TARGET_ID);
        productType.setProductTypeCode(code);
        productType.setProductTypeNameSnapshot(name);
        return productType;
    }
}
