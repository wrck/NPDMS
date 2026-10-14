package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationValidationRespVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationStorageMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationStorageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverDraftFieldDeleteQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 配置修订下的关系化元数据。Map 只在既有 HTTP 展示契约边界组装。 */
@Service
@RequiredArgsConstructor
public class CutoverConfigurationMetadataStore {
    private final CutoverConfigurationDimensionMapper dimensions;
    private final CutoverConfigurationDictionaryValueMapper dictionaries;
    private final CutoverConfigurationPlanSectionMapper sections;
    private final CutoverConfigurationPlanSectionScopeMapper scopes;
    private final CutoverConfigurationValidationIssueMapper validationIssues;
    private final CutoverChecklistBindingConditionMapper conditions;
    private final CutoverConfigurationStorageMapper drafts;
    private final CutoverFieldDefinitionStore fields;

    public List<CutoverConfigurationDimensionDO> dimensions(Long revisionId) {
        return dimensions.selectListByParents(CutoverConfigurationDimensionListQuery.builder()
                .tenantId(tenant()).configurationRevisionIds(Set.of(revisionId)).build());
    }

    public List<CutoverConfigurationDictionaryValueDO> dictionaryValues(Long revisionId) {
        return dictionaries.selectListByParents(CutoverConfigurationDictionaryValueListQuery.builder()
                .tenantId(tenant()).configurationRevisionIds(Set.of(revisionId)).build());
    }

    public Map<String, Object> dictionaryView(Long revisionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var dimension : dimensions(revisionId)) {
            if (dimension.getDictionarySnapshotKey() != null) {
                result.put(dimension.getDictionarySnapshotKey(), dimension.getValueSource().substring(5));
            }
        }
        Map<String, List<Map<String, Object>>> values = new LinkedHashMap<>();
        for (var value : dictionaryValues(revisionId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", value.getValueCode());
            item.put("label", value.getLabel());
            values.computeIfAbsent(value.getDictType(), key -> new ArrayList<>()).add(item);
        }
        result.putAll(values);
        return result;
    }

    public List<CutoverConfigurationSaveReqVO.DimensionVO> dimensionViews(Long revisionId) {
        return dimensions(revisionId).stream().map(row -> {
            var value = new CutoverConfigurationSaveReqVO.DimensionVO();
            value.setCode(row.getDimensionCode()); value.setName(row.getDimensionName());
            value.setDataType(row.getDataTypeCode()); value.setValueSource(row.getValueSource());
            value.setOwner(row.getBusinessOwner()); value.setContextPath(row.getContextPath());
            value.setEnabled(row.getEnabledFlag());
            return value;
        }).toList();
    }

    public List<CutoverConfigurationSaveReqVO.PlanTemplateSectionVO> sectionViews(Long revisionId) {
        var rows = sections.selectListByParents(CutoverConfigurationPlanSectionListQuery.builder()
                .tenantId(tenant()).configurationRevisionIds(Set.of(revisionId)).build());
        var constraints = scopes.selectListByParents(CutoverConfigurationPlanSectionScopeListQuery.builder()
                .tenantId(tenant()).planSectionIds(rows.stream().map(CutoverConfigurationPlanSectionDO::getId)
                        .collect(Collectors.toSet())).build());
        return rows.stream().map(row -> {
            var value = new CutoverConfigurationSaveReqVO.PlanTemplateSectionVO();
            value.setStableSectionKey(row.getStableSectionKey()); value.setTitle(row.getTitle());
            value.setRequired(row.getRequiredFlag()); value.setSortOrder(row.getSortOrder());
            value.setCutoverTypeCodes(constraints.stream().filter(scope -> row.getId().equals(scope.getPlanSectionId())
                            && "CUTOVER_TYPE".equals(scope.getScopeAxisCode())).map(CutoverConfigurationPlanSectionScopeDO::getScopeValue).toList());
            value.setLevelCodes(constraints.stream().filter(scope -> row.getId().equals(scope.getPlanSectionId())
                            && "CUTOVER_LEVEL".equals(scope.getScopeAxisCode())).map(CutoverConfigurationPlanSectionScopeDO::getScopeValue).toList());
            return value;
        }).toList();
    }

    public List<CutoverConfigurationValidationRespVO.ValidationErrorVO> validationErrors(CutoverConfigurationRevisionDO root) {
        if (root.getCurrentValidationNo() == null) return List.of();
        return validationIssues.selectListByParents(CutoverConfigurationValidationIssueListQuery.builder()
                        .tenantId(tenant()).configurationRevisionIds(Set.of(root.getId())).build()).stream()
                .filter(row -> root.getCurrentValidationNo().equals(row.getValidationNo()))
                .map(row -> new CutoverConfigurationValidationRespVO.ValidationErrorVO(row.getLocation(), row.getMessage())).toList();
    }

    public Map<Long, Map<String, Object>> conditionViews(ConditionRead query) {
        Map<Long, String> codes = dimensions(query.configurationRevisionId()).stream()
                .collect(Collectors.toMap(CutoverConfigurationDimensionDO::getId, CutoverConfigurationDimensionDO::getDimensionCode));
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        query.bindingRuleRevisionIds().forEach(id -> result.put(id, new LinkedHashMap<>()));
        for (var row : conditions.selectListByParents(CutoverChecklistBindingConditionListQuery.builder()
                .tenantId(tenant()).bindingRuleRevisionIds(query.bindingRuleRevisionIds()).build())) {
            String code = codes.get(row.getConfigurationDimensionId());
            if (code == null) throw new IllegalStateException("绑定规则维度不属于当前配置修订");
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) result.get(row.getBindingRuleRevisionId()).computeIfAbsent(code, key -> new ArrayList<>());
            values.add(new CutoverMetadataScalar(row.getValueTypeCode(), row.getValueText(), row.getValueNumber(),
                    row.getValueBoolean(), row.getValueDatetime()).value());
        }
        return result;
    }

    public record ConditionRead(Long configurationRevisionId, Set<Long> bindingRuleRevisionIds) { }
    public record DraftReplacement(CutoverConfigurationRevisionDO root, Set<Long> previousItemIds,
                                   CutoverConfigurationSaveReqVO request) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceDraft(DraftReplacement command) {
        CutoverConfigurationRevisionDO root = command.root();
        if (!"DRAFT".equals(root.getStatusCode())) throw new IllegalArgumentException("只可替换配置草稿明细");
        var query = new CutoverConfigurationStorageQuery(tenant(), root.getId());
        var oldFields = fields.definitions(command.previousItemIds()).values().stream()
                .flatMap(definition -> definition.fields().stream())
                .sorted(Comparator.comparingInt((CutoverChecklistFieldDefinitionDO field) -> field.getNodePath().length()).reversed()).toList();
        drafts.deleteDraftOptions(query);
        oldFields.forEach(field -> drafts.deleteDraftField(new CutoverDraftFieldDeleteQuery(tenant(), root.getId(), field.getId())));
        drafts.deleteDraftMappings(query);
        drafts.deleteDraftConditions(query);
        drafts.deleteDraftDimensions(query);
        drafts.deleteDraftSectionScopes(query);
        drafts.deleteDraftSections(query);
        drafts.deleteDraftDictionaryValues(query);
        int order = 0;
        for (var value : command.request().getDimensions()) {
            var row = CutoverConfigurationDimensionDO.builder().configurationRevisionId(root.getId())
                    .dimensionCode(value.getCode()).dimensionName(value.getName()).dataTypeCode(value.getDataType())
                    .valueSource(value.getValueSource()).businessOwner(value.getOwner()).contextPath(value.getContextPath())
                    .enabledFlag(Boolean.TRUE.equals(value.getEnabled())).sortOrder(order++).build();
            Object oldReference = command.request().getDictionarySnapshot().get(value.getCode());
            if (oldReference instanceof String reference && ("DICT:" + reference).equals(value.getValueSource())) {
                row.setDictionarySnapshotKey(value.getCode());
            }
            stamp(row); dimensions.insert(row);
        }
        insertDictionary(new DictionaryWrite(root.getId(), command.request().getDictionarySnapshot()));
        for (var value : command.request().getPlanTemplateSections()) {
            var row = CutoverConfigurationPlanSectionDO.builder().configurationRevisionId(root.getId())
                    .stableSectionKey(value.getStableSectionKey()).title(value.getTitle())
                    .requiredFlag(Boolean.TRUE.equals(value.getRequired())).sortOrder(value.getSortOrder()).build();
            stamp(row); sections.insert(row);
            insertScopes(new SectionScopes(row, "CUTOVER_TYPE", value.getCutoverTypeCodes()));
            insertScopes(new SectionScopes(row, "CUTOVER_LEVEL", value.getLevelCodes()));
        }
    }

    private record SectionScopes(CutoverConfigurationPlanSectionDO section, String axis, List<String> values) { }

    private void insertScopes(SectionScopes command) {
        for (int i = 0; i < command.values().size(); i++) {
            var row = CutoverConfigurationPlanSectionScopeDO.builder().planSectionId(command.section().getId())
                    .scopeAxisCode(command.axis()).scopeValue(command.values().get(i)).sortOrder(i).build();
            stamp(row); scopes.insert(row);
        }
    }

    public record DictionaryWrite(Long configurationRevisionId, Map<String, Object> values) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void freezeDictionary(DictionaryWrite command) {
        drafts.deleteDraftDictionaryValues(new CutoverConfigurationStorageQuery(tenant(), command.configurationRevisionId()));
        insertDictionary(command);
    }

    private void insertDictionary(DictionaryWrite command) {
        for (var entry : command.values().entrySet()) {
            if (entry.getValue() instanceof String) continue; // 原引用型快照保存在对应维度的引用键。
            if (!(entry.getValue() instanceof List<?> values)) throw new IllegalArgumentException("字典快照值必须为列表");
            for (int i = 0; i < values.size(); i++) {
                if (!(values.get(i) instanceof Map<?, ?> item) || !(item.get("value") instanceof String value)) {
                    throw new IllegalArgumentException("字典值必须有明确编码");
                }
                var row = CutoverConfigurationDictionaryValueDO.builder().configurationRevisionId(command.configurationRevisionId())
                        .dictType(entry.getKey()).valueCode(value).label((String) item.get("label"))
                        .sortOrder(i).sourceStatusCode(item.get("status") == null ? null : String.valueOf(item.get("status"))).build();
                stamp(row); dictionaries.insert(row);
            }
        }
    }

    public record ConditionWrite(Long configurationRevisionId, Long bindingRuleRevisionId, Map<String, Object> values) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertConditions(ConditionWrite command) {
        Map<String, Long> byCode = dimensions(command.configurationRevisionId()).stream()
                .collect(Collectors.toMap(CutoverConfigurationDimensionDO::getDimensionCode, CutoverConfigurationDimensionDO::getId));
        for (var entry : command.values().entrySet()) {
            Long dimensionId = byCode.get(entry.getKey());
            if (dimensionId == null) throw new IllegalArgumentException("绑定规则引用未定义维度：" + entry.getKey());
            Collection<?> values = entry.getValue() instanceof Collection<?> collection ? collection : java.util.Collections.singletonList(entry.getValue());
            if (values.isEmpty()) throw new IllegalArgumentException("显式维度条件不能为空集合");
            int index = 0;
            for (Object value : values) {
                CutoverMetadataScalar scalar = CutoverMetadataScalar.from(value);
                var row = CutoverChecklistBindingConditionDO.builder().bindingRuleRevisionId(command.bindingRuleRevisionId())
                        .configurationDimensionId(dimensionId).valueNo(index++).valueTypeCode(scalar.type())
                        .valueText(scalar.text()).valueNumber(scalar.number()).valueBoolean(scalar.bool()).valueDatetime(scalar.datetime()).build();
                stamp(row); conditions.insert(row);
            }
        }
    }

    private static Long tenant() {
        if (TenantContextHolder.isIgnore()) throw new IllegalArgumentException("配置元数据不允许忽略租户范围");
        return TenantContextHolder.getRequiredTenantId();
    }

    private static void stamp(TenantBaseDO row) { row.setTenantId(tenant()); row.setDeleted(false); }
}
