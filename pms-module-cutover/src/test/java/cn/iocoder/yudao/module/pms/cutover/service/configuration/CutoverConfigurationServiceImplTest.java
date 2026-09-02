package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationValidationRespVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistBindingRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverRiskMatrixRules;
import cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverSurveyMatrixRules;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CutoverConfigurationServiceImplTest {

    @Mock
    private CutoverConfigurationRevisionMapper revisionMapper;
    @Mock
    private CutoverChecklistItemDefinitionRevisionMapper itemMapper;
    @Mock
    private CutoverChecklistBindingRuleRevisionMapper ruleMapper;
    @Mock
    private DictDataApi dictDataApi;
    @InjectMocks
    private CutoverConfigurationServiceImpl service;

    @Test
    void copyShouldPreserveAllMatrixChildren() {
        CutoverConfigurationRevisionDO source = draft(10L);
        source.setNavigationRuleSnapshot("{\"target\":\"TASK_OVERVIEW\"}");
        when(revisionMapper.selectById(10L)).thenReturn(source);
        CutoverChecklistItemDefinitionRevisionDO item = new CutoverChecklistItemDefinitionRevisionDO();
        item.setId(20L);
        item.setStableItemKey("RISK-SYSTEM-LOG");
        item.setItemDefinitionVersion(1);
        item.setItemTypeCode("RISK");
        item.setBusinessCategoryCode("SYSTEM_LOG");
        item.setItemName("系统日志检查");
        item.setInterfaceFormatCode("TEXT");
        item.setInterfaceSchema("{}");
        item.setFeedbackFormatCode("TEXT");
        item.setRequiredFlag(false);
        item.setWorkModeCode("MANUAL");
        item.setStatusCode("ENABLED");
        item.setSortOrder(10);
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of(item));
        CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
        rule.setStableRuleKey("RULE-RISK-SYSTEM-LOG");
        rule.setItemDefinitionId(20L);
        rule.setDimensionConditionSnapshot("{}");
        rule.setPriority(10);
        rule.setRequiredResult(true);
        rule.setStatusCode("ENABLED");
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of(rule));
        when(revisionMapper.selectLatestByCode(any())).thenReturn(draft(10L));
        when(revisionMapper.insert(any(CutoverConfigurationRevisionDO.class))).thenAnswer(invocation -> {
            invocation.<CutoverConfigurationRevisionDO>getArgument(0).setId(11L);
            return 1;
        });

        service.copyRevision(10L, 0);

        ArgumentCaptor<CutoverChecklistItemDefinitionRevisionDO> copiedItem =
                ArgumentCaptor.forClass(CutoverChecklistItemDefinitionRevisionDO.class);
        verify(itemMapper).insert(copiedItem.capture());
        org.junit.jupiter.api.Assertions.assertEquals("SYSTEM_LOG", copiedItem.getValue().getBusinessCategoryCode());
        ArgumentCaptor<CutoverChecklistBindingRuleRevisionDO> copiedRule =
                ArgumentCaptor.forClass(CutoverChecklistBindingRuleRevisionDO.class);
        verify(ruleMapper).insert(copiedRule.capture());
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(copiedRule.getValue().getRequiredResult()));
        ArgumentCaptor<CutoverConfigurationRevisionDO> copiedRoot =
                ArgumentCaptor.forClass(CutoverConfigurationRevisionDO.class);
        verify(revisionMapper).insert(copiedRoot.capture());
        org.junit.jupiter.api.Assertions.assertEquals("{\"target\":\"TASK_OVERVIEW\"}",
                copiedRoot.getValue().getNavigationRuleSnapshot());
    }

    @Test
    void validateShouldReturnLocationWhenSectionReferencesInvalidDictValue() {
        when(revisionMapper.selectById(10L)).thenReturn(draft(10L));
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of());
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
        when(dictDataApi.getDictDataList(anyString())).thenReturn(List.of(new DictDataRespDTO()));
        doThrow(new ServiceException(1, "字典数据不存在"))
                .when(dictDataApi).validateDictDataList(eq("pms_cutover_type"),
                        argThat(values -> values.contains("UNKNOWN")));

        CutoverConfigurationValidationRespVO result = service.validate(10L);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error ->
                "base.planTemplateSections[0].cutoverTypeCodes".equals(error.getLocation())
                        && error.getMessage().contains("字典数据不存在")));
    }

    @Test
    void validateShouldPrefixRiskAndSurveyLocations() {
        CutoverConfigurationRevisionDO incomplete = draft(10L);
        incomplete.setPlanTemplateSectionSnapshot("[]");
        when(revisionMapper.selectById(10L)).thenReturn(incomplete);
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of());
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
        stubEnabledDictionaries();

        CutoverConfigurationValidationRespVO result = service.validate(10L);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.getLocation().startsWith("risk.")));
        assertTrue(result.getErrors().stream().anyMatch(error -> error.getLocation().startsWith("survey.")));
    }

    @Test
    void publishShouldHaveNoWriteWhenDictionaryValidationFails() {
        when(revisionMapper.selectById(10L)).thenReturn(draft(10L));
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of());
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
        when(dictDataApi.getDictDataList(anyString())).thenReturn(List.of(new DictDataRespDTO()));
        doThrow(new ServiceException(1, "字典数据不存在"))
                .when(dictDataApi).validateDictDataList(eq("pms_cutover_type"),
                        argThat(values -> values.contains("UNKNOWN")));

        assertThrows(ServiceException.class, () -> service.publish(10L, 0));

        verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
    }

    @Test
    void publishShouldHaveNoWriteWhenRiskOrSurveyValidationFails() {
        CutoverConfigurationRevisionDO incomplete = draft(10L);
        incomplete.setPlanTemplateSectionSnapshot("[]");
        when(revisionMapper.selectById(10L)).thenReturn(incomplete);
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of());
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
        when(dictDataApi.getDictDataList(anyString())).thenReturn(List.of(enabledDict("A")));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.publish(10L, 0));

        assertTrue(exception.getMessage().contains("risk."));
        verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
        verify(itemMapper, never()).hardDeleteByRevisionId(any());
        verify(ruleMapper, never()).hardDeleteByRevisionId(any());
    }

    @Test
    void publishShouldKeepCurrentPublishedWhenMatrixValidationFails() {
        CutoverConfigurationRevisionDO incomplete = draft(10L);
        incomplete.setPlanTemplateSectionSnapshot("[]");
        when(revisionMapper.selectById(10L)).thenReturn(incomplete);
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of());
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
        stubEnabledDictionaries();

        assertThrows(ServiceException.class, () -> service.publish(10L, 0));

        verify(revisionMapper, never()).selectLatestByCode(argThat(query -> "PUBLISHED".equals(query.statusCode())));
        verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
    }

    @Test
    void updateShouldRejectPublishedRevisionBeforeAnyWrite() {
        CutoverConfigurationRevisionDO published = draft(10L);
        published.setStatusCode("PUBLISHED");
        when(revisionMapper.selectById(10L)).thenReturn(published);

        assertThrows(ServiceException.class, () -> service.update(10L, 0, new CutoverConfigurationSaveReqVO()));

        verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
        verify(itemMapper, never()).hardDeleteByRevisionId(any());
        verify(ruleMapper, never()).hardDeleteByRevisionId(any());
    }

    @Test
    void updateShouldRejectStaleIfMatchBeforeReplacingChildren() {
        CutoverConfigurationRevisionDO current = draft(10L);
        current.setVersion(3);
        when(revisionMapper.selectById(10L)).thenReturn(current);

        assertThrows(ServiceException.class,
                () -> service.update(10L, 2, new CutoverConfigurationSaveReqVO()));

        verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
        verify(itemMapper, never()).hardDeleteByRevisionId(any());
        verify(ruleMapper, never()).hardDeleteByRevisionId(any());
    }

    @Test
    void updateShouldRejectStableItemKeyReusedAsAnotherTypeBeforeWrite() {
        when(revisionMapper.selectById(10L)).thenReturn(draft(10L));
        CutoverChecklistItemDefinitionRevisionDO history = new CutoverChecklistItemDefinitionRevisionDO();
        history.setStableItemKey("ITEM-1");
        history.setItemTypeCode("RISK");
        when(itemMapper.selectHistoryByStableKeys(any())).thenReturn(List.of(history));
        CutoverConfigurationSaveReqVO request = new CutoverConfigurationSaveReqVO();
        request.setConfigurationCode("CUTOVER_DEFAULT");
        request.setNavigationRule(null);
        CutoverConfigurationSaveReqVO.ItemVO item = new CutoverConfigurationSaveReqVO.ItemVO();
        item.setStableItemKey("ITEM-1");
        item.setItemType("BUSINESS_SURVEY");
        request.setItems(List.of(item));

        assertThrows(ServiceException.class, () -> service.update(10L, 0, request));

        verify(revisionMapper, never()).updateById(any(CutoverConfigurationRevisionDO.class));
    }

    @Test
    void publishShouldFreezeAuthoritativeDictionaryLabels() {
        CutoverConfigurationRevisionDO valid = draft(10L);
        valid.setPlanTemplateSectionSnapshot("[]");
        when(revisionMapper.selectById(10L)).thenReturn(valid);
        List<CutoverChecklistItemDefinitionRevisionDO> items = completeMatrixItems();
        when(itemMapper.selectListByRevision(any())).thenReturn(items);
        when(ruleMapper.selectListByRevision(any())).thenReturn(completeMatrixRules(items));
        stubEnabledDictionaries();
        when(revisionMapper.selectLatestByCode(any())).thenReturn(null);
        when(revisionMapper.updateById(any(CutoverConfigurationRevisionDO.class))).thenReturn(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
            service.publish(10L, 0);
        }

        ArgumentCaptor<CutoverConfigurationRevisionDO> update =
                ArgumentCaptor.forClass(CutoverConfigurationRevisionDO.class);
        verify(revisionMapper).updateById(update.capture());
        assertTrue(
                update.getValue().getDictionarySnapshot().contains("割接类型A"));
    }

    private CutoverConfigurationRevisionDO draft(Long id) {
        CutoverConfigurationRevisionDO row = new CutoverConfigurationRevisionDO();
        row.setId(id);
        row.setConfigurationCode("CUTOVER_DEFAULT");
        row.setConfigurationName("割接默认配置");
        row.setRevisionNo(2);
        row.setStatusCode("DRAFT");
        row.setVersion(0);
        row.setDictionarySnapshot("{}");
        row.setDimensionDefinitionSnapshot("""
                [
                  {"code":"CUTOVER_TYPE","name":"割接类型","dataType":"STRING","valueSource":"DICT:pms_cutover_type","owner":"CUT","contextPath":"task.cutoverType","enabled":true},
                  {"code":"NETWORK_MODE","name":"组网模式","dataType":"STRING","valueSource":"DICT:pms_network_mode","owner":"CUT","contextPath":"task.networkMode","enabled":true},
                  {"code":"DEVICE_TYPE","name":"设备类型","dataType":"STRING","valueSource":"DICT:pms_device_type","owner":"SYSTEM","contextPath":"task.deviceType","enabled":true},
                  {"code":"CUTOVER_LEVEL","name":"割接等级","dataType":"STRING","valueSource":"DICT:pms_risk_level","owner":"CUT","contextPath":"assessment.level","enabled":true}
                ]
                """);
        row.setPlanTemplateSectionSnapshot("""
                [{"stableSectionKey":"OVERVIEW","title":"割接概述","sortOrder":10,
                  "cutoverTypeCodes":["UNKNOWN"],"levelCodes":[],"required":true}]
                """);
        return row;
    }

    private DictDataRespDTO enabledDict(String value) {
        DictDataRespDTO dictionaryValue = new DictDataRespDTO();
        dictionaryValue.setValue(value);
        dictionaryValue.setLabel(value);
        dictionaryValue.setStatus(0);
        return dictionaryValue;
    }

    private void stubEnabledDictionaries() {
        when(dictDataApi.getDictDataList("pms_cutover_type")).thenReturn(List.of(
                enabledDict("VERSION_UPGRADE", "割接类型A"), enabledDict("CONFIGURATION_CHANGE")));
        when(dictDataApi.getDictDataList("pms_network_mode")).thenReturn(
                CutoverRiskMatrixRules.DUAL_COUNTS.keySet().stream().map(this::enabledDict).toList());
        when(dictDataApi.getDictDataList("pms_device_type")).thenReturn(List.of(
                enabledDict("FW"), enabledDict("SW"), enabledDict("ADX")));
        when(dictDataApi.getDictDataList("pms_risk_level")).thenReturn(List.of(
                enabledDict("A"), enabledDict("B"), enabledDict("C"), enabledDict("D")));
    }

    private DictDataRespDTO enabledDict(String value, String label) {
        DictDataRespDTO dictionaryValue = enabledDict(value);
        dictionaryValue.setLabel(label);
        return dictionaryValue;
    }

    private List<CutoverChecklistItemDefinitionRevisionDO> completeMatrixItems() {
        List<CutoverChecklistItemDefinitionRevisionDO> items = new ArrayList<>();
        long id = 1L;
        for (String category : CutoverRiskMatrixRules.REQUIRED_RISK_CATEGORIES) {
            items.add(item(id++, "RISK_" + category, "RISK", category, null, "{}"));
        }
        for (Map.Entry<String, Integer> entry : CutoverRiskMatrixRules.DUAL_COUNTS.entrySet()) {
            for (int index = 1; index <= entry.getValue(); index++) {
                String key = "DUAL_" + entry.getKey() + "_" + "%03d".formatted(index);
                items.add(item(id++, key, "DUAL_MACHINE_CHECK", entry.getKey(), entry.getKey(), "{}"));
            }
        }
        for (String category : CutoverSurveyMatrixRules.CORE_SURVEY_CATEGORIES) {
            String schema = "CUTOVER_BACKGROUND".equals(category) ? backgroundSchema() : "{}";
            items.add(item(id++, "SURVEY_" + category, "BUSINESS_SURVEY", category, null, schema));
        }
        return items;
    }

    private CutoverChecklistItemDefinitionRevisionDO item(long id, String key, String type, String category,
                                                           String subtable, String schema) {
        CutoverChecklistItemDefinitionRevisionDO item = new CutoverChecklistItemDefinitionRevisionDO();
        item.setId(id);
        item.setStableItemKey(key);
        item.setItemDefinitionVersion(1);
        item.setItemTypeCode(type);
        item.setBusinessCategoryCode(category);
        item.setItemName(key);
        item.setInterfaceFormatCode("TABLE");
        item.setInterfaceSchema(schema);
        item.setFeedbackFormatCode("BOOLEAN_REMARK");
        item.setRequiredFlag(true);
        item.setWorkModeCode("MANUAL");
        item.setSubtableCode(subtable);
        item.setStatusCode("ENABLED");
        item.setSortOrder((int) id);
        return item;
    }

    private List<CutoverChecklistBindingRuleRevisionDO> completeMatrixRules(
            List<CutoverChecklistItemDefinitionRevisionDO> items) {
        Map<String, CutoverChecklistItemDefinitionRevisionDO> itemsByKey = items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CutoverChecklistItemDefinitionRevisionDO::getStableItemKey, item -> item));
        List<CutoverChecklistBindingRuleRevisionDO> rules = new ArrayList<>();
        for (String category : CutoverRiskMatrixRules.ALL_SITUATION_REQUIRED) {
            rules.add(rule(itemsByKey, "RISK_" + category,
                    "{\"CUTOVER_TYPE\":[\"VERSION_UPGRADE\",\"CONFIGURATION_CHANGE\"],"
                            + "\"DEVICE_TYPE\":[\"FW\",\"SW\",\"ADX\"],"
                            + "\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}", true));
        }
        rules.add(rule(itemsByKey, "RISK_TARGET_VERSION_BULLETIN",
                "{\"CUTOVER_TYPE\":[\"VERSION_UPGRADE\"]}", true));
        for (String category : CutoverRiskMatrixRules.REQUIRED_RISK_CATEGORIES) {
            String key = "RISK_" + category;
            if (rules.stream().noneMatch(rule -> rule.getItemDefinitionId().equals(itemsByKey.get(key).getId()))) {
                rules.add(rule(itemsByKey, key, "{\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}", false));
            }
        }
        items.stream().filter(item -> "DUAL_MACHINE_CHECK".equals(item.getItemTypeCode()))
                .forEach(item -> rules.add(rule(itemsByKey, item.getStableItemKey(),
                        "{\"NETWORK_MODE\":[\"" + item.getSubtableCode() + "\"]}", true)));
        items.stream().filter(item -> "BUSINESS_SURVEY".equals(item.getItemTypeCode()))
                .forEach(item -> rules.add(rule(itemsByKey, item.getStableItemKey(),
                        "{\"CUTOVER_LEVEL\":[\"A\",\"B\",\"C\"]}", true)));
        return rules;
    }

    private CutoverChecklistBindingRuleRevisionDO rule(
            Map<String, CutoverChecklistItemDefinitionRevisionDO> itemsByKey, String itemKey,
            String conditions, boolean required) {
        CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
        rule.setStableRuleKey("RULE_" + itemKey);
        rule.setItemDefinitionId(itemsByKey.get(itemKey).getId());
        rule.setDimensionConditionSnapshot(conditions);
        rule.setPriority(10);
        rule.setRequiredResult(required);
        rule.setStatusCode("ENABLED");
        return rule;
    }

    private String backgroundSchema() {
        return """
                {"fields":[
                  {"code":"solvesOnlineIssue"},
                  {"code":"issueTicketNo","visibleWhen":{"field":"solvesOnlineIssue","equals":true}},
                  {"code":"issueHandler","visibleWhen":{"field":"solvesOnlineIssue","equals":true}},
                  {"code":"repeatCutover"},
                  {"code":"firstCutoverOwner","visibleWhen":{"field":"repeatCutover","equals":true}},
                  {"code":"backgroundDescription"}
                ]}
                """;
    }
}
