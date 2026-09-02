package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleCommandRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleCommandRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDraftUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleIdentityLockQuery;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleManagePermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InspectionRuleRevisionServiceImplTest {

    private static final Long TENANT_ID = 7L;

    @Mock
    private InspectionRuleMapper ruleMapper;
    @Mock
    private InspectionRuleRevisionMapper revisionMapper;
    @Mock
    private InspectionRuleCommandRevisionMapper commandMapper;
    @Mock
    private InspectionRuleProductTypeRevisionMapper productTypeMapper;
    @Mock
    private InspectionAssetProductTypeApi assetProductTypeApi;
    @Mock
    private DictDataApi dictDataApi;
    @Mock
    private InspectionRuleManagePermissionGuard managePermissionGuard;
    @InjectMocks
    private InspectionRuleRevisionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        lenient().when(dictDataApi.getDictDataList("pms_inspection_rule_category"))
                .thenReturn(List.of(dictData("pms_inspection_rule_category", "BASIC", "基础检测")));
        lenient().when(dictDataApi.getDictDataList("pms_inspection_rule_severity"))
                .thenReturn(List.of(dictData("pms_inspection_rule_severity", "GENERAL", "一般")));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectAllDraftEntrypointsBeforeDataAccessWithoutManagePermission() {
        doThrow(new ServiceException(1_013_002_008, "forbidden"))
                .when(managePermissionGuard).check();

        assertThrows(ServiceException.class, () -> service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand("DET-001", "核心状态检查")));
        assertThrows(ServiceException.class, () -> service.saveDraft(
                new InspectionRuleRevisionService.SaveDraftCommand(
                        20L, 0, null, null, null, null, null, null, null,
                        null, null, null, null, null, List.of(), List.of())));
        assertThrows(ServiceException.class, () -> service.copyRevision(20L));
        assertThrows(ServiceException.class, () -> service.validateRevision(20L));

        verify(managePermissionGuard, org.mockito.Mockito.times(4)).check();
        verifyNoMoreInteractions(ruleMapper, revisionMapper, commandMapper, productTypeMapper,
                dictDataApi, assetProductTypeApi);
    }

    @Test
    void shouldCreateMinimalDraftForNewStableIdentity() {
        when(ruleMapper.insert(any(InspectionRuleDO.class))).thenReturn(1);
        when(revisionMapper.insert(any(InspectionRuleRevisionDO.class))).thenReturn(1);

        InspectionRuleRevisionService.DraftResult result = service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand(" DET-001 ", " 核心状态检查 "));

        ArgumentCaptor<InspectionRuleDO> rule = ArgumentCaptor.forClass(InspectionRuleDO.class);
        ArgumentCaptor<InspectionRuleRevisionDO> revision =
                ArgumentCaptor.forClass(InspectionRuleRevisionDO.class);
        verify(ruleMapper).insert(rule.capture());
        verify(revisionMapper).insert(revision.capture());
        assertNotNull(result.ruleId());
        assertNotNull(result.revisionId());
        assertEquals("DET-001", rule.getValue().getDetectionId());
        assertEquals("核心状态检查", rule.getValue().getRuleName());
        assertEquals(TENANT_ID, rule.getValue().getTenantId());
        assertEquals(1, revision.getValue().getRevisionNo());
        assertEquals("DRAFT", revision.getValue().getStatusCode());
        assertEquals("核心状态检查", revision.getValue().getRuleNameSnapshot());
        assertEquals(0, result.version());
    }

    @Test
    void shouldMapDetectionIdUniqueConstraintToStableBusinessError() {
        when(ruleMapper.insert(any(InspectionRuleDO.class))).thenThrow(
                new DuplicateKeyException("Duplicate entry for key 'uk_srv_inspection_rule_tenant_detection'"));

        ServiceException failure = assertThrows(ServiceException.class, () -> service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand("DET-001", "核心状态检查")));

        assertEquals(1_013_002_003, failure.getCode());
    }

    @Test
    void shouldMapRuleNameUniqueConstraintToStableBusinessError() {
        when(ruleMapper.insert(any(InspectionRuleDO.class))).thenThrow(
                new DuplicateKeyException("Duplicate entry for key 'uk_srv_inspection_rule_tenant_name'"));

        ServiceException failure = assertThrows(ServiceException.class, () -> service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand("DET-001", "核心状态检查")));

        assertEquals(1_013_002_004, failure.getCode());
    }

    @Test
    void shouldReplaceWholeDraftAndChildrenWithPartialFields() {
        InspectionRuleRevisionDO draft = new InspectionRuleRevisionDO();
        draft.setId(20L);
        draft.setRuleId(10L);
        draft.setTenantId(TENANT_ID);
        draft.setRevisionNo(1);
        draft.setStatusCode("DRAFT");
        draft.setRuleNameSnapshot("核心状态检查");
        draft.setVersion(2);
        when(revisionMapper.selectById(20L)).thenReturn(draft);
        when(revisionMapper.updateDraftIfMatch(any(InspectionRuleDraftUpdate.class))).thenReturn(1);
        when(commandMapper.insert(any(InspectionRuleCommandRevisionDO.class))).thenReturn(1);
        when(productTypeMapper.insert(any(InspectionRuleProductTypeRevisionDO.class))).thenReturn(1);

        InspectionRuleRevisionService.DraftResult result = service.saveDraft(
                new InspectionRuleRevisionService.SaveDraftCommand(
                        20L,
                        2,
                        "CPU利用率",
                        null,
                        "BASIC",
                        "基础检测",
                        "GENERAL",
                        "一般",
                        10,
                        "^CPU: [0-9]+$",
                        "NUMBER",
                        "≤",
                        new BigDecimal("80"),
                        "%",
                        List.of(new InspectionRuleRevisionService.CommandDraft(
                                "CMD-CPU", "show cpu", 1, 30, true)),
                        List.of(new InspectionRuleRevisionService.ProductTypeDraft("FW", "防火墙"))));

        verify(commandMapper).hardDeleteByRevisionIds(any());
        verify(productTypeMapper).hardDeleteByRevisionIds(any());
        verify(commandMapper).insert(any(InspectionRuleCommandRevisionDO.class));
        verify(productTypeMapper).insert(any(InspectionRuleProductTypeRevisionDO.class));
        assertEquals(20L, result.revisionId());
        assertEquals(3, result.version());
    }

    @Test
    void shouldRejectStaleDraftBeforeReplacingChildren() {
        InspectionRuleRevisionDO draft = new InspectionRuleRevisionDO();
        draft.setId(20L);
        draft.setRuleId(10L);
        draft.setTenantId(TENANT_ID);
        draft.setRevisionNo(1);
        draft.setStatusCode("DRAFT");
        draft.setVersion(2);
        when(revisionMapper.selectById(20L)).thenReturn(draft);
        when(revisionMapper.updateDraftIfMatch(any(InspectionRuleDraftUpdate.class))).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.saveDraft(
                new InspectionRuleRevisionService.SaveDraftCommand(
                        20L, 1, null, null, null, null, null, null, null,
                        null, null, null, null, null, List.of(), List.of())));

        verify(commandMapper, never()).hardDeleteByRevisionIds(any());
        verify(productTypeMapper, never()).hardDeleteByRevisionIds(any());
    }

    @Test
    void shouldLockStableIdentityAndCopyPublishedRevisionToNextDraft() {
        InspectionRuleRevisionDO source = completeRevision("PUBLISHED");
        InspectionRuleDO rule = stableRule();
        InspectionRuleCommandRevisionDO command = sourceCommand();
        InspectionRuleProductTypeRevisionDO productType = sourceProductType();
        when(revisionMapper.selectById(20L)).thenReturn(source);
        when(ruleMapper.selectByIdForUpdate(any(InspectionRuleIdentityLockQuery.class))).thenReturn(rule);
        when(revisionMapper.selectMaxRevisionNoByRule(any())).thenReturn(3);
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(command));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(productType));
        when(revisionMapper.insert(any(InspectionRuleRevisionDO.class))).thenReturn(1);
        when(commandMapper.insert(any(InspectionRuleCommandRevisionDO.class))).thenReturn(1);
        when(productTypeMapper.insert(any(InspectionRuleProductTypeRevisionDO.class))).thenReturn(1);

        InspectionRuleRevisionService.DraftResult result = service.copyRevision(20L);

        ArgumentCaptor<InspectionRuleRevisionDO> copied = ArgumentCaptor.forClass(InspectionRuleRevisionDO.class);
        verify(revisionMapper).insert(copied.capture());
        assertEquals(4, result.revisionNo());
        assertEquals("DRAFT", copied.getValue().getStatusCode());
        assertEquals(source.getInspectionItem(), copied.getValue().getInspectionItem());
        assertEquals(source.getRuleNameSnapshot(), copied.getValue().getRuleNameSnapshot());
        assertEquals(null, copied.getValue().getPublishedBy());
        assertEquals(null, copied.getValue().getPublishedAt());
        assertEquals(null, copied.getValue().getDisabledBy());
        assertEquals(null, copied.getValue().getDisabledAt());
        verify(commandMapper).insert(any(InspectionRuleCommandRevisionDO.class));
        verify(productTypeMapper).insert(any(InspectionRuleProductTypeRevisionDO.class));
    }

    @Test
    void shouldCopyDisabledRevisionWithoutLifecycleFacts() {
        InspectionRuleRevisionDO source = completeRevision("DISABLED");
        source.setPublishedBy(31L);
        source.setDisabledBy(32L);
        when(revisionMapper.selectById(20L)).thenReturn(source);
        when(ruleMapper.selectByIdForUpdate(any(InspectionRuleIdentityLockQuery.class))).thenReturn(stableRule());
        when(revisionMapper.selectMaxRevisionNoByRule(any())).thenReturn(3);
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        when(revisionMapper.insert(any(InspectionRuleRevisionDO.class))).thenReturn(1);
        when(commandMapper.insert(any(InspectionRuleCommandRevisionDO.class))).thenReturn(1);
        when(productTypeMapper.insert(any(InspectionRuleProductTypeRevisionDO.class))).thenReturn(1);

        service.copyRevision(20L);

        ArgumentCaptor<InspectionRuleRevisionDO> copied = ArgumentCaptor.forClass(InspectionRuleRevisionDO.class);
        verify(revisionMapper).insert(copied.capture());
        assertEquals("DRAFT", copied.getValue().getStatusCode());
        assertEquals(null, copied.getValue().getPublishedBy());
        assertEquals(null, copied.getValue().getDisabledBy());
    }

    @Test
    void shouldRejectCopyFromDraftBeforeLockingStableIdentity() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));

        assertThrows(RuntimeException.class, () -> service.copyRevision(20L));

        verify(ruleMapper, never()).selectByIdForUpdate(any());
        verify(revisionMapper, never()).selectMaxRevisionNoByRule(any());
        verify(revisionMapper, never()).insert(any(InspectionRuleRevisionDO.class));
    }

    @Test
    void shouldValidateCompleteDraftThroughAstWithoutWrites() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        when(dictDataApi.getDictDataList("pms_inspection_rule_category"))
                .thenReturn(List.of(dictData("pms_inspection_rule_category", "BASIC", "权威基础检测")));
        when(dictDataApi.getDictDataList("pms_inspection_rule_severity"))
                .thenReturn(List.of(dictData("pms_inspection_rule_severity", "GENERAL", "权威一般")));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(
                new ProductTypeCodeResult("FW", true, true, "下一代防火墙", "CRM", "v1", "FRESH",
                        LocalDateTime.of(2026, 9, 1, 10, 0), false)));

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of(), result.errors());
        assertEquals(List.of(
                new InspectionRuleRevisionService.DictionaryNameCandidate(
                        "categoryCode", "BASIC", "权威基础检测"),
                new InspectionRuleRevisionService.DictionaryNameCandidate(
                        "severityCode", "GENERAL", "权威一般")), result.dictionaryNameCandidates());
        assertEquals(List.of(new InspectionRuleRevisionService.ProductTypeNameCandidate(
                "FW", "下一代防火墙")), result.productTypeNameCandidates());
        verify(dictDataApi).validateDictDataList("pms_inspection_rule_category", List.of("BASIC"));
        verify(dictDataApi).validateDictDataList("pms_inspection_rule_severity", List.of("GENERAL"));
        verify(assetProductTypeApi).getByCodes(any());
        verify(revisionMapper, never()).insert(any(InspectionRuleRevisionDO.class));
        verify(revisionMapper, never()).updateDraftIfMatch(any());
        verify(commandMapper, never()).insert(any(InspectionRuleCommandRevisionDO.class));
        verify(commandMapper, never()).hardDeleteByRevisionIds(any());
        verify(productTypeMapper, never()).insert(any(InspectionRuleProductTypeRevisionDO.class));
        verify(productTypeMapper, never()).hardDeleteByRevisionIds(any());
    }

    @Test
    void shouldRejectDisabledCategoryDictionaryValue() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        doThrow(new ServiceException(400, "disabled")).when(dictDataApi)
                .validateDictDataList("pms_inspection_rule_category", List.of("BASIC"));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(
                new ProductTypeCodeResult("FW", true, true, "防火墙", "CRM", "v1", "FRESH",
                        LocalDateTime.of(2026, 9, 1, 10, 0), false)));

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of("categoryCode:UNSUPPORTED_VALUE"), result.errors().stream()
                .map(error -> error.location() + ":" + error.code())
                .toList());
    }

    @Test
    void shouldReturnStableTemporaryErrorWhenDictionaryCapabilityIsUnavailable() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        doThrow(new IllegalStateException("unavailable")).when(dictDataApi)
                .validateDictDataList("pms_inspection_rule_category", List.of("BASIC"));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(
                new ProductTypeCodeResult("FW", true, true, "防火墙", "CRM", "v1", "FRESH",
                        LocalDateTime.of(2026, 9, 1, 10, 0), false)));

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of("categoryCode:DEPENDENCY_TEMPORARY"), result.errors().stream()
                .map(error -> error.location() + ":" + error.code())
                .toList());
        verify(revisionMapper, never()).insert(any(InspectionRuleRevisionDO.class));
        verify(revisionMapper, never()).updateDraftIfMatch(any());
        verify(commandMapper, never()).insert(any(InspectionRuleCommandRevisionDO.class));
        verify(productTypeMapper, never()).insert(any(InspectionRuleProductTypeRevisionDO.class));
    }

    @Test
    void shouldRejectAstResultWithoutSourceProof() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(
                new ProductTypeCodeResult("FW", true, true, "防火墙", null, null, "FRESH", null, false)));

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of("productTypes[0]:DEPENDENCY_REJECTED"), result.errors().stream()
                .map(error -> error.location() + ":" + error.code())
                .toList());
        assertEquals(List.of(), result.productTypeNameCandidates());
    }

    @Test
    void shouldReturnStableTemporaryErrorWhenAstIsUnavailable() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        when(assetProductTypeApi.getByCodes(any())).thenThrow(new IllegalStateException("unavailable"));

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of("productTypes[0]:DEPENDENCY_TEMPORARY"), result.errors().stream()
                .map(error -> error.location() + ":" + error.code())
                .toList());
        verify(revisionMapper, never()).insert(any(InspectionRuleRevisionDO.class));
    }

    @Test
    void shouldReturnStableProductTypeErrorWhenAstRejectsCode() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(
                new ProductTypeCodeResult("FW", true, false, "防火墙", "CRM", "v1", "FRESH", null, false)));

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of("productTypes[0]:DEPENDENCY_REJECTED"), result.errors().stream()
                .map(error -> error.location() + ":" + error.code())
                .toList());
        verifyNoMoreInteractions(assetProductTypeApi);
        verify(revisionMapper, never()).insert(any(InspectionRuleRevisionDO.class));
    }

    @Test
    void shouldReturnCompletenessErrorsForEmptyDraftWithoutAstLookup() {
        InspectionRuleRevisionDO draft = new InspectionRuleRevisionDO();
        draft.setId(20L);
        draft.setRuleId(10L);
        draft.setTenantId(TENANT_ID);
        draft.setRevisionNo(1);
        draft.setStatusCode("DRAFT");
        draft.setRuleNameSnapshot("核心状态检查");
        draft.setVersion(0);
        when(revisionMapper.selectById(20L)).thenReturn(draft);
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of());
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of());

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of(), result.productTypeNameCandidates());
        assertEquals(List.of("inspectionItem:REQUIRED", "description:REQUIRED", "categoryCode:REQUIRED",
                "severityCode:REQUIRED", "sortOrder:REQUIRED", "commands:REQUIRED",
                "expectedResultRegex:REQUIRED", "threshold:REQUIRED", "productTypes:REQUIRED"),
                result.errors().stream().map(error -> error.location() + ":" + error.code()).toList());
        verify(assetProductTypeApi, never()).getByCodes(any());
    }

    @Test
    void shouldRejectValidationForNonDraftRevisionBeforeLoadingChildren() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("PUBLISHED"));

        ServiceException failure = assertThrows(ServiceException.class, () -> service.validateRevision(20L));

        assertEquals(1_013_002_006, failure.getCode());
        verify(ruleMapper, never()).selectById(any());
        verify(commandMapper, never()).selectListByRevisionIds(any());
        verify(productTypeMapper, never()).selectListByRevisionIds(any());
        verify(assetProductTypeApi, never()).getByCodes(any());
    }

    @Test
    void shouldRejectUnknownAstProductTypeWithoutWrites() {
        when(revisionMapper.selectById(20L)).thenReturn(completeRevision("DRAFT"));
        when(ruleMapper.selectById(10L)).thenReturn(stableRule());
        when(commandMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceCommand()));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(sourceProductType()));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of());

        InspectionRuleRevisionService.ValidationResult result = service.validateRevision(20L);

        assertEquals(List.of("productTypes[0]:DEPENDENCY_REJECTED"), result.errors().stream()
                .map(error -> error.location() + ":" + error.code())
                .toList());
        assertEquals(List.of(), result.productTypeNameCandidates());
        verify(revisionMapper, never()).insert(any(InspectionRuleRevisionDO.class));
        verify(revisionMapper, never()).updateDraftIfMatch(any());
        verify(commandMapper, never()).insert(any(InspectionRuleCommandRevisionDO.class));
        verify(commandMapper, never()).hardDeleteByRevisionIds(any());
        verify(productTypeMapper, never()).insert(any(InspectionRuleProductTypeRevisionDO.class));
        verify(productTypeMapper, never()).hardDeleteByRevisionIds(any());
    }
    private static DictDataRespDTO dictData(String dictType, String value, String label) {
        DictDataRespDTO data = new DictDataRespDTO();
        data.setDictType(dictType);
        data.setValue(value);
        data.setLabel(label);
        data.setStatus(0);
        return data;
    }

    private static InspectionRuleDO stableRule() {
        InspectionRuleDO rule = new InspectionRuleDO();
        rule.setId(10L);
        rule.setTenantId(TENANT_ID);
        rule.setDetectionId("DET-001");
        rule.setRuleName("核心状态检查");
        rule.setVersion(2);
        return rule;
    }

    private static InspectionRuleRevisionDO completeRevision(String status) {
        InspectionRuleRevisionDO revision = new InspectionRuleRevisionDO();
        revision.setId(20L);
        revision.setRuleId(10L);
        revision.setTenantId(TENANT_ID);
        revision.setRevisionNo(3);
        revision.setStatusCode(status);
        revision.setRuleNameSnapshot("核心状态检查");
        revision.setInspectionItem("CPU利用率");
        revision.setDescription("检查CPU利用率");
        revision.setCategoryCode("BASIC");
        revision.setCategoryNameSnapshot("基础检测");
        revision.setSeverityCode("GENERAL");
        revision.setSeverityNameSnapshot("一般");
        revision.setSortOrder(10);
        revision.setExpectedResultRegex("^CPU: [0-9]+$");
        revision.setThresholdDataType("NUMBER");
        revision.setThresholdOperator("≤");
        revision.setThresholdValue(new BigDecimal("80"));
        revision.setThresholdUnit("%");
        revision.setVersion(2);
        return revision;
    }

    private static InspectionRuleCommandRevisionDO sourceCommand() {
        InspectionRuleCommandRevisionDO command = new InspectionRuleCommandRevisionDO();
        command.setId(30L);
        command.setTenantId(TENANT_ID);
        command.setRevisionId(20L);
        command.setStableCommandKey("CMD-CPU");
        command.setCommandContent("show cpu");
        command.setExecutionOrder(1);
        command.setTimeoutSeconds(30);
        command.setContinueOnTimeout(true);
        command.setVersion(0);
        return command;
    }

    private static InspectionRuleProductTypeRevisionDO sourceProductType() {
        InspectionRuleProductTypeRevisionDO productType = new InspectionRuleProductTypeRevisionDO();
        productType.setId(40L);
        productType.setTenantId(TENANT_ID);
        productType.setRevisionId(20L);
        productType.setProductTypeCode("FW");
        productType.setProductTypeNameSnapshot("防火墙");
        productType.setVersion(0);
        return productType;
    }
}
