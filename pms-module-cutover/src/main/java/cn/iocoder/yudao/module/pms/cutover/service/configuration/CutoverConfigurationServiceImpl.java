package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationValidationRespVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistBindingRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationByCodeQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationPageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationItemHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverConfigurationRules;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_CONFIG_CODE_CHANGED;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_CONFIG_NOT_EDITABLE;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_CONFIG_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_CONFIG_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_CONFIG_VALIDATION_FAILED;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.CUTOVER_CONFIG_VERSION_CONFLICT;

@Service
@Validated
public class CutoverConfigurationServiceImpl implements CutoverConfigurationService {

    @Resource
    private CutoverConfigurationRevisionMapper revisionMapper;
    @Resource
    private CutoverChecklistItemDefinitionRevisionMapper itemMapper;
    @Resource
    private CutoverChecklistBindingRuleRevisionMapper ruleMapper;
    @Resource
    private DictDataApi dictDataApi;

    @Override
    public PageResult<CutoverConfigurationRespVO> getPage(CutoverConfigurationPageReqVO request) {
        CutoverConfigurationPageQuery query = BeanUtils.toBean(request, CutoverConfigurationPageQuery.class);
        PageResult<CutoverConfigurationRevisionDO> page = revisionMapper.selectPage(query);
        return new PageResult<>(page.getList().stream().map(this::toSummary).toList(), page.getTotal());
    }

    @Override
    public CutoverConfigurationRespVO get(Long revisionId) {
        return toDetail(requireRevision(revisionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CutoverConfigurationSaveReqVO request) {
        CutoverConfigurationRevisionDO latest = revisionMapper.selectLatestByCode(
                new CutoverConfigurationByCodeQuery(request.getConfigurationCode(), null));
        validateStableItemTypeHistory(-1L, request);
        CutoverConfigurationRevisionDO row = toDraftRoot(request);
        row.setRevisionNo(latest == null ? 1 : latest.getRevisionNo() + 1);
        revisionMapper.insert(row);
        replaceChildren(row.getId(), request);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long revisionId, Integer expectedVersion, CutoverConfigurationSaveReqVO request) {
        CutoverConfigurationRevisionDO existing = requireRevision(revisionId);
        requireExpectedVersion(existing, expectedVersion);
        requireDraft(existing);
        if (!Objects.equals(existing.getConfigurationCode(), request.getConfigurationCode())) {
            throw exception(CUTOVER_CONFIG_CODE_CHANGED);
        }
        validateStableItemTypeHistory(revisionId, request);
        CutoverConfigurationRevisionDO update = toDraftRoot(request);
        update.setId(revisionId);
        update.setVersion(expectedVersion);
        if (revisionMapper.updateById(update) != 1) {
            throw exception(CUTOVER_CONFIG_VERSION_CONFLICT);
        }
        replaceChildren(revisionId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyRevision(Long revisionId, Integer expectedVersion) {
        CutoverConfigurationRevisionDO source = requireRevision(revisionId);
        requireExpectedVersion(source, expectedVersion);
        CutoverConfigurationRespVO detail = toDetail(source);
        CutoverConfigurationSaveReqVO request = toSaveRequest(detail);
        request.setChangeSummary("基于修订" + source.getRevisionNo() + "创建新草稿");
        return create(request);
    }

    @Override
    public CutoverConfigurationValidationRespVO validate(Long revisionId) {
        CutoverConfigurationRespVO detail = get(revisionId);
        List<CutoverConfigurationRules.ValidationError> errors = new ArrayList<>(CutoverConfigurationRules.validate(
                toDomainDimensions(detail.getDimensions()), toDomainItems(detail.getItems()),
                toDomainRules(detail.getBindingRules()), toDomainSections(detail.getPlanTemplateSections())));
        validateDictionaryReferences(detail, errors);
        validateExternalSourceDefinitions(detail, errors);
        return new CutoverConfigurationValidationRespVO(errors.isEmpty(), errors.stream()
                .map(error -> new CutoverConfigurationValidationRespVO.ValidationErrorVO(
                        error.location(), error.message())).toList());
    }

    private void validateExternalSourceDefinitions(CutoverConfigurationRespVO detail,
                                                   List<CutoverConfigurationRules.ValidationError> errors) {
        for (int i = 0; i < detail.getItems().size(); i++) {
            CutoverConfigurationSaveReqVO.ItemVO item = detail.getItems().get(i);
            if (item.getInterfaceSchema() != null && item.getInterfaceSchema().containsKey("__INVALID_JSON__")) {
                errors.add(new CutoverConfigurationRules.ValidationError(
                        "items[" + i + "].interfaceSchema", "界面Schema必须是合法JSON对象"));
            }
            if (!Boolean.TRUE.equals(item.getEnabled()) || !"EXTERNAL".equals(item.getWorkMode())) {
                continue;
            }
            Map<String, Object> source = item.getExternalSourceConfig();
            boolean complete = source != null
                    && source.get("interfaceCode") instanceof String interfaceCode && !interfaceCode.isBlank()
                    && source.get("parameterMapping") instanceof Map<?, ?>
                    && source.get("returnMapping") instanceof Map<?, ?>
                    && source.get("manualFallback") instanceof Boolean;
            if (!complete) {
                errors.add(new CutoverConfigurationRules.ValidationError(
                        "items[" + i + "].externalSourceConfig",
                        "外部数据源必须包含接口标识、参数映射、返回映射和人工降级标识"));
            }
        }
    }

    private void validateDictionaryReferences(CutoverConfigurationRespVO detail,
                                              List<CutoverConfigurationRules.ValidationError> errors) {
        Map<String, String> dimensionDictTypes = new LinkedHashMap<>();
        for (int i = 0; i < detail.getDimensions().size(); i++) {
            CutoverConfigurationSaveReqVO.DimensionVO dimension = detail.getDimensions().get(i);
            if (!Boolean.TRUE.equals(dimension.getEnabled())) {
                continue;
            }
            String source = dimension.getValueSource();
            if (source == null || !source.startsWith("DICT:") || source.length() == 5) {
                errors.add(new CutoverConfigurationRules.ValidationError(
                        "dimensions[" + i + "].valueSource", "启用维度的允许值来源必须引用平台字典"));
                continue;
            }
            String dictType = source.substring(5);
            dimensionDictTypes.put(dimension.getCode(), dictType);
            if (dictDataApi.getDictDataList(dictType).isEmpty()) {
                errors.add(new CutoverConfigurationRules.ValidationError(
                        "dimensions[" + i + "].valueSource", "引用的字典不存在或没有启用值：" + dictType));
            }
        }

        for (int i = 0; i < detail.getBindingRules().size(); i++) {
            CutoverConfigurationSaveReqVO.BindingRuleVO rule = detail.getBindingRules().get(i);
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }
            for (Map.Entry<String, Object> condition : rule.getDimensionConditions().entrySet()) {
                String dictType = dimensionDictTypes.get(condition.getKey());
                String location = "bindingRules[" + i + "].dimensionConditions." + condition.getKey();
                if (dictType == null) {
                    errors.add(new CutoverConfigurationRules.ValidationError(
                            location, "绑定条件引用了不存在或未启用的维度"));
                    continue;
                }
                validateDictValues(dictType, stringValues(condition.getValue()), location, errors);
            }
        }

        for (int i = 0; i < detail.getItems().size(); i++) {
            CutoverConfigurationSaveReqVO.ItemVO item = detail.getItems().get(i);
            if (Boolean.TRUE.equals(item.getEnabled()) && item.getSubtableCode() != null) {
                validateDictValues("pms_network_mode", List.of(item.getSubtableCode()),
                        "items[" + i + "].subtableCode", errors);
            }
        }
        for (int i = 0; i < detail.getPlanTemplateSections().size(); i++) {
            CutoverConfigurationSaveReqVO.PlanTemplateSectionVO section = detail.getPlanTemplateSections().get(i);
            validateDictValues("pms_cutover_type", section.getCutoverTypeCodes(),
                    "planTemplateSections[" + i + "].cutoverTypeCodes", errors);
            validateDictValues("pms_risk_level", section.getLevelCodes(),
                    "planTemplateSections[" + i + "].levelCodes", errors);
        }
    }

    private void validateDictValues(String dictType, Collection<String> values, String location,
                                    List<CutoverConfigurationRules.ValidationError> errors) {
        try {
            dictDataApi.validateDictDataList(dictType, values);
        } catch (ServiceException exception) {
            errors.add(new CutoverConfigurationRules.ValidationError(location, exception.getMessage()));
        }
    }

    private Set<String> stringValues(Object value) {
        Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
        Set<String> result = new LinkedHashSet<>();
        for (Object current : values) {
            if (current instanceof String stringValue && !stringValue.isBlank()) {
                result.add(stringValue);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CutoverConfigurationRespVO publish(Long revisionId, Integer expectedVersion) {
        CutoverConfigurationRevisionDO draft = requireRevision(revisionId);
        requireExpectedVersion(draft, expectedVersion);
        requireDraft(draft);
        CutoverConfigurationValidationRespVO validation = validate(revisionId);
        if (!validation.isValid()) {
            throw exception(CUTOVER_CONFIG_VALIDATION_FAILED,
                    validation.getErrors().getFirst().getLocation());
        }
        LocalDateTime now = LocalDateTime.now();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        CutoverConfigurationRevisionDO current = revisionMapper.selectLatestByCode(
                new CutoverConfigurationByCodeQuery(draft.getConfigurationCode(),
                        CutoverConfigurationRules.STATUS_PUBLISHED));
        if (current != null && !Objects.equals(current.getId(), revisionId)) {
            CutoverConfigurationRevisionDO disabled = new CutoverConfigurationRevisionDO();
            disabled.setId(current.getId());
            disabled.setStatusCode(CutoverConfigurationRules.STATUS_DISABLED);
            disabled.setEffectiveTo(now);
            disabled.setDisabledBy(actorId);
            disabled.setDisabledAt(now);
            disabled.setVersion(current.getVersion());
            if (revisionMapper.updateById(disabled) != 1) {
                throw exception(CUTOVER_CONFIG_VERSION_CONFLICT);
            }
        }
        CutoverConfigurationRevisionDO published = new CutoverConfigurationRevisionDO();
        published.setId(revisionId);
        published.setStatusCode(CutoverConfigurationRules.STATUS_PUBLISHED);
        published.setEffectiveFrom(now);
        published.setPublishedBy(actorId);
        published.setPublishedAt(now);
        published.setDictionarySnapshot(JsonUtils.toJsonString(buildDictionarySnapshot(get(revisionId))));
        published.setValidationResultSnapshot(JsonUtils.toJsonString(validation.getErrors()));
        published.setVersion(expectedVersion);
        if (revisionMapper.updateById(published) != 1) {
            throw exception(CUTOVER_CONFIG_VERSION_CONFLICT);
        }
        return get(revisionId);
    }

    private Map<String, Object> buildDictionarySnapshot(CutoverConfigurationRespVO detail) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (CutoverConfigurationSaveReqVO.DimensionVO dimension : detail.getDimensions()) {
            if (!Boolean.TRUE.equals(dimension.getEnabled()) || !dimension.getValueSource().startsWith("DICT:")) {
                continue;
            }
            String dictType = dimension.getValueSource().substring(5);
            if (snapshot.containsKey(dictType)) {
                continue;
            }
            List<Map<String, String>> values = dictDataApi.getDictDataList(dictType).stream()
                    .filter(value -> CommonStatusEnum.ENABLE.getStatus().equals(value.getStatus()))
                    .map(this::toDictionarySnapshotValue)
                    .toList();
            snapshot.put(dictType, values);
        }
        return snapshot;
    }

    private Map<String, String> toDictionarySnapshotValue(DictDataRespDTO value) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("value", value.getValue());
        snapshot.put("label", value.getLabel());
        return snapshot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CutoverConfigurationRespVO disable(Long revisionId, Integer expectedVersion) {
        CutoverConfigurationRevisionDO current = requireRevision(revisionId);
        requireExpectedVersion(current, expectedVersion);
        if (!CutoverConfigurationRules.STATUS_PUBLISHED.equals(current.getStatusCode())) {
            throw exception(CUTOVER_CONFIG_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        CutoverConfigurationRevisionDO disabled = new CutoverConfigurationRevisionDO();
        disabled.setId(revisionId);
        disabled.setStatusCode(CutoverConfigurationRules.STATUS_DISABLED);
        disabled.setEffectiveTo(now);
        disabled.setDisabledBy(SecurityFrameworkUtils.getLoginUserId());
        disabled.setDisabledAt(now);
        disabled.setVersion(expectedVersion);
        if (revisionMapper.updateById(disabled) != 1) {
            throw exception(CUTOVER_CONFIG_VERSION_CONFLICT);
        }
        return get(revisionId);
    }

    private CutoverConfigurationRevisionDO toDraftRoot(CutoverConfigurationSaveReqVO request) {
        CutoverConfigurationRevisionDO row = new CutoverConfigurationRevisionDO();
        row.setConfigurationCode(request.getConfigurationCode());
        row.setConfigurationName(request.getConfigurationName());
        row.setStatusCode(CutoverConfigurationRules.STATUS_DRAFT);
        row.setDictionarySnapshot(JsonUtils.toJsonString(request.getDictionarySnapshot()));
        row.setDimensionDefinitionSnapshot(JsonUtils.toJsonString(request.getDimensions()));
        row.setPlanTemplateSectionSnapshot(JsonUtils.toJsonString(request.getPlanTemplateSections()));
        row.setChangeSummary(request.getChangeSummary());
        return row;
    }

    private void replaceChildren(Long revisionId, CutoverConfigurationSaveReqVO request) {
        CutoverConfigurationChildrenQuery query = new CutoverConfigurationChildrenQuery(revisionId);
        ruleMapper.hardDeleteByRevisionId(query);
        itemMapper.hardDeleteByRevisionId(query);
        Map<String, CutoverChecklistItemDefinitionRevisionDO> itemsByKey = new HashMap<>();
        for (CutoverConfigurationSaveReqVO.ItemVO value : request.getItems()) {
            CutoverChecklistItemDefinitionRevisionDO item = new CutoverChecklistItemDefinitionRevisionDO();
            item.setConfigurationRevisionId(revisionId);
            item.setStableItemKey(value.getStableItemKey());
            item.setItemDefinitionVersion(1);
            item.setItemTypeCode(value.getItemType());
            item.setBusinessCategoryCode(value.getBusinessCategoryCode());
            item.setItemName(value.getItemName());
            item.setItemDescription(value.getItemDescription());
            item.setInterfaceFormatCode(value.getInterfaceFormat());
            item.setInterfaceSchema(JsonUtils.toJsonString(value.getInterfaceSchema() == null
                    ? Map.of() : value.getInterfaceSchema()));
            item.setFeedbackFormatCode(value.getFeedbackFormat());
            item.setRequiredFlag(Boolean.TRUE.equals(value.getRequired()));
            item.setWorkModeCode(value.getWorkMode());
            item.setExternalSourceConfig(value.getExternalSourceConfig() == null ? null
                    : JsonUtils.toJsonString(value.getExternalSourceConfig()));
            item.setSubtableCode(value.getSubtableCode());
            item.setStatusCode(Boolean.FALSE.equals(value.getEnabled()) ? "DISABLED" : "ENABLED");
            item.setSortOrder(value.getSortOrder());
            itemMapper.insert(item);
            itemsByKey.put(item.getStableItemKey(), item);
        }
        for (CutoverConfigurationSaveReqVO.BindingRuleVO value : request.getBindingRules()) {
            CutoverChecklistItemDefinitionRevisionDO item = itemsByKey.get(value.getStableItemKey());
            if (item == null) {
                throw exception(CUTOVER_CONFIG_VALIDATION_FAILED, value.getStableRuleKey());
            }
            CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
            rule.setConfigurationRevisionId(revisionId);
            rule.setStableRuleKey(value.getStableRuleKey());
            rule.setItemDefinitionId(item.getId());
            rule.setItemDefinitionVersion(item.getItemDefinitionVersion());
            rule.setDimensionConditionSnapshot(JsonUtils.toJsonString(value.getDimensionConditions()));
            rule.setPriority(value.getPriority());
            rule.setRequiredResult(value.getRequiredResult());
            rule.setStatusCode(Boolean.FALSE.equals(value.getEnabled()) ? "DISABLED" : "ENABLED");
            ruleMapper.insert(rule);
        }
    }

    private void validateStableItemTypeHistory(Long revisionId, CutoverConfigurationSaveReqVO request) {
        if (request.getItems().isEmpty()) {
            return;
        }
        List<String> stableItemKeys = request.getItems().stream()
                .map(CutoverConfigurationSaveReqVO.ItemVO::getStableItemKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (stableItemKeys.isEmpty()) {
            return;
        }
        List<CutoverChecklistItemDefinitionRevisionDO> history = itemMapper.selectHistoryByStableKeys(
                new CutoverConfigurationItemHistoryQuery(request.getConfigurationCode(), revisionId, stableItemKeys));
        Map<String, String> historicalTypes = new HashMap<>();
        if (history != null) {
            history.forEach(item -> historicalTypes.putIfAbsent(item.getStableItemKey(), item.getItemTypeCode()));
        }
        for (CutoverConfigurationSaveReqVO.ItemVO item : request.getItems()) {
            String historicalType = historicalTypes.get(item.getStableItemKey());
            if (historicalType != null && !historicalType.equals(item.getItemType())) {
                throw exception(CUTOVER_CONFIG_VALIDATION_FAILED, item.getStableItemKey());
            }
        }
    }

    private CutoverConfigurationRevisionDO requireRevision(Long revisionId) {
        CutoverConfigurationRevisionDO row = revisionMapper.selectById(revisionId);
        if (row == null) {
            throw exception(CUTOVER_CONFIG_NOT_FOUND);
        }
        return row;
    }

    private void requireExpectedVersion(CutoverConfigurationRevisionDO row, Integer expectedVersion) {
        if (!Objects.equals(row.getVersion(), expectedVersion)) {
            throw exception(CUTOVER_CONFIG_VERSION_CONFLICT);
        }
    }

    private void requireDraft(CutoverConfigurationRevisionDO row) {
        if (!CutoverConfigurationRules.isEditable(row.getStatusCode())) {
            throw exception(CUTOVER_CONFIG_NOT_EDITABLE);
        }
    }

    private CutoverConfigurationRespVO toSummary(CutoverConfigurationRevisionDO row) {
        CutoverConfigurationRespVO response = new CutoverConfigurationRespVO();
        response.setId(row.getId());
        response.setConfigurationCode(row.getConfigurationCode());
        response.setConfigurationName(row.getConfigurationName());
        response.setRevisionNo(row.getRevisionNo());
        response.setStatusCode(row.getStatusCode());
        response.setEffectiveFrom(row.getEffectiveFrom());
        response.setEffectiveTo(row.getEffectiveTo());
        response.setChangeSummary(row.getChangeSummary());
        response.setPublishedBy(row.getPublishedBy());
        response.setPublishedAt(row.getPublishedAt());
        response.setDisabledBy(row.getDisabledBy());
        response.setDisabledAt(row.getDisabledAt());
        response.setVersion(row.getVersion());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        response.setDictionarySnapshot(JsonUtils.parseObject(row.getDictionarySnapshot(), Map.class));
        return response;
    }

    private CutoverConfigurationRespVO toDetail(CutoverConfigurationRevisionDO row) {
        CutoverConfigurationRespVO response = toSummary(row);
        response.setDimensions(JsonUtils.parseArray(row.getDimensionDefinitionSnapshot(),
                CutoverConfigurationSaveReqVO.DimensionVO.class));
        response.setPlanTemplateSections(JsonUtils.parseArray(row.getPlanTemplateSectionSnapshot(),
                CutoverConfigurationSaveReqVO.PlanTemplateSectionVO.class));
        List<CutoverChecklistItemDefinitionRevisionDO> items = itemMapper.selectListByRevision(
                new CutoverConfigurationChildrenQuery(row.getId()));
        Map<Long, String> itemKeys = new HashMap<>();
        List<CutoverConfigurationSaveReqVO.ItemVO> itemViews = new ArrayList<>();
        for (CutoverChecklistItemDefinitionRevisionDO item : items) {
            itemKeys.put(item.getId(), item.getStableItemKey());
            CutoverConfigurationSaveReqVO.ItemVO view = new CutoverConfigurationSaveReqVO.ItemVO();
            view.setStableItemKey(item.getStableItemKey());
            view.setItemType(item.getItemTypeCode());
            view.setBusinessCategoryCode(item.getBusinessCategoryCode());
            view.setItemName(item.getItemName());
            view.setItemDescription(item.getItemDescription());
            view.setInterfaceFormat(item.getInterfaceFormatCode());
            view.setInterfaceSchema(JsonUtils.parseObject(item.getInterfaceSchema(), Map.class));
            view.setFeedbackFormat(item.getFeedbackFormatCode());
            view.setRequired(item.getRequiredFlag());
            view.setWorkMode(item.getWorkModeCode());
            view.setExternalSourceConfig(item.getExternalSourceConfig() == null ? null
                    : JsonUtils.parseObject(item.getExternalSourceConfig(), Map.class));
            view.setSubtableCode(item.getSubtableCode());
            view.setEnabled("ENABLED".equals(item.getStatusCode()));
            view.setSortOrder(item.getSortOrder());
            itemViews.add(view);
        }
        response.setItems(itemViews);
        List<CutoverConfigurationSaveReqVO.BindingRuleVO> ruleViews = ruleMapper.selectListByRevision(
                new CutoverConfigurationChildrenQuery(row.getId())).stream().map(rule -> {
                    CutoverConfigurationSaveReqVO.BindingRuleVO view = new CutoverConfigurationSaveReqVO.BindingRuleVO();
                    view.setStableRuleKey(rule.getStableRuleKey());
                    view.setStableItemKey(itemKeys.get(rule.getItemDefinitionId()));
                    view.setDimensionConditions(JsonUtils.parseObject(rule.getDimensionConditionSnapshot(), Map.class));
                    view.setPriority(rule.getPriority());
                    view.setRequiredResult(rule.getRequiredResult());
                    view.setEnabled("ENABLED".equals(rule.getStatusCode()));
                    return view;
                }).toList();
        response.setBindingRules(ruleViews);
        if (row.getValidationResultSnapshot() != null) {
            response.setValidationErrors(JsonUtils.parseArray(row.getValidationResultSnapshot(),
                    CutoverConfigurationValidationRespVO.ValidationErrorVO.class));
        }
        return response;
    }

    private CutoverConfigurationSaveReqVO toSaveRequest(CutoverConfigurationRespVO response) {
        CutoverConfigurationSaveReqVO request = new CutoverConfigurationSaveReqVO();
        request.setConfigurationCode(response.getConfigurationCode());
        request.setConfigurationName(response.getConfigurationName());
        request.setDictionarySnapshot(response.getDictionarySnapshot());
        request.setDimensions(response.getDimensions());
        request.setPlanTemplateSections(response.getPlanTemplateSections());
        request.setItems(response.getItems());
        request.setBindingRules(response.getBindingRules());
        return request;
    }

    private List<CutoverConfigurationRules.DimensionDefinition> toDomainDimensions(
            List<CutoverConfigurationSaveReqVO.DimensionVO> values) {
        return values.stream().map(value -> new CutoverConfigurationRules.DimensionDefinition(value.getCode(),
                value.getName(), value.getDataType(), value.getValueSource(), value.getOwner(),
                value.getContextPath(), Boolean.TRUE.equals(value.getEnabled()))).toList();
    }

    private List<CutoverConfigurationRules.ItemDefinition> toDomainItems(
            List<CutoverConfigurationSaveReqVO.ItemVO> values) {
        return values.stream().map(value -> new CutoverConfigurationRules.ItemDefinition(value.getStableItemKey(),
                value.getItemType(), value.getBusinessCategoryCode(), value.getItemName(), value.getInterfaceFormat(),
                value.getInterfaceSchema(), value.getFeedbackFormat(), Boolean.TRUE.equals(value.getRequired()),
                value.getWorkMode(),
                value.getExternalSourceConfig() == null ? null : JsonUtils.toJsonString(value.getExternalSourceConfig()),
                value.getSubtableCode(), Boolean.TRUE.equals(value.getEnabled()))).toList();
    }

    private List<CutoverConfigurationRules.BindingRule> toDomainRules(
            List<CutoverConfigurationSaveReqVO.BindingRuleVO> values) {
        return values.stream().map(value -> new CutoverConfigurationRules.BindingRule(value.getStableRuleKey(),
                value.getStableItemKey(), canonicalJson(value.getDimensionConditions()),
                value.getPriority(), value.getRequiredResult(), Boolean.TRUE.equals(value.getEnabled()))).toList();
    }

    private String canonicalJson(Object value) {
        return JsonUtils.toJsonString(normalizeJson(value));
    }

    private Object normalizeJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((key, current) -> normalized.put(String.valueOf(key), normalizeJson(current)));
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeJson).toList();
        }
        return value;
    }

    private List<CutoverConfigurationRules.PlanTemplateSection> toDomainSections(
            List<CutoverConfigurationSaveReqVO.PlanTemplateSectionVO> values) {
        return values.stream().map(value -> new CutoverConfigurationRules.PlanTemplateSection(
                value.getStableSectionKey(), value.getTitle(), value.getSortOrder(), value.getCutoverTypeCodes(),
                value.getLevelCodes(), Boolean.TRUE.equals(value.getRequired()))).toList();
    }
}
