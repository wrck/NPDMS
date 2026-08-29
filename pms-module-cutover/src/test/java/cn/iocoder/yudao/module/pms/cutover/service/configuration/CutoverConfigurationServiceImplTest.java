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
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void copyShouldPreserveCategoryAndBindingRequiredResult() {
        when(revisionMapper.selectById(10L)).thenReturn(draft(10L));
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
        org.junit.jupiter.api.Assertions.assertTrue(result.getErrors().stream().anyMatch(error ->
                "planTemplateSections[0].cutoverTypeCodes".equals(error.getLocation())
                        && error.getMessage().contains("字典数据不存在")));
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
    void updateShouldRejectStableItemKeyReusedAsAnotherTypeBeforeWrite() {
        when(revisionMapper.selectById(10L)).thenReturn(draft(10L));
        CutoverChecklistItemDefinitionRevisionDO history = new CutoverChecklistItemDefinitionRevisionDO();
        history.setStableItemKey("ITEM-1");
        history.setItemTypeCode("RISK");
        when(itemMapper.selectHistoryByStableKeys(any())).thenReturn(List.of(history));
        CutoverConfigurationSaveReqVO request = new CutoverConfigurationSaveReqVO();
        request.setConfigurationCode("CUTOVER_DEFAULT");
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
        when(itemMapper.selectListByRevision(any())).thenReturn(List.of());
        when(ruleMapper.selectListByRevision(any())).thenReturn(List.of());
        DictDataRespDTO dictionaryValue = new DictDataRespDTO();
        dictionaryValue.setValue("A");
        dictionaryValue.setLabel("割接类型A");
        dictionaryValue.setStatus(0);
        when(dictDataApi.getDictDataList(anyString())).thenReturn(List.of(dictionaryValue));
        when(revisionMapper.selectLatestByCode(any())).thenReturn(null);
        when(revisionMapper.updateById(any(CutoverConfigurationRevisionDO.class))).thenReturn(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
            service.publish(10L, 0);
        }

        ArgumentCaptor<CutoverConfigurationRevisionDO> update =
                ArgumentCaptor.forClass(CutoverConfigurationRevisionDO.class);
        verify(revisionMapper).updateById(update.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
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
}
