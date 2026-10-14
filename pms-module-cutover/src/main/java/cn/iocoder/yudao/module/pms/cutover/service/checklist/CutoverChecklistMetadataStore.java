package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistStorageMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverMetadataScalar;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** P3 的输入、命中候选和设备上下文从属于原清单修订。 */
@Service
@RequiredArgsConstructor
public class CutoverChecklistMetadataStore {
    private final CutoverConfigurationMetadataStore configuration;
    private final CutoverChecklistInputDimensionMapper inputs;
    private final CutoverChecklistMatchCandidateMapper candidates;
    private final CutoverChecklistDeviceContextMapper devices;
    private final CutoverAssessmentDeviceMapper assessmentDevices;
    private final CutoverChecklistStorageMapper drafts;

    @Builder
    public record Write(CutoverChecklistDO checklist, CutoverFrozenConfiguration configuration,
                        Map<String, List<String>> dimensions, List<Long> matchedRuleIds, Set<Long> selectedRuleIds,
                        Long actorId, LocalDateTime time) { }

    public record RootWrite(CutoverChecklistDO checklist, CutoverTaskDO task, boolean gap) { }

    public static void applyRoot(RootWrite command) {
        var root = command.checklist(); var task = command.task();
        root.setSourceTaskVersion(task.getVersion()); root.setProjectScopeVersion(task.getProjectScopeVersion());
        root.setConfigurationRevisionId(task.getConfigurationRevisionId()); root.setConfigurationCode(task.getConfigurationCode());
        root.setConfigurationRevisionNo(task.getConfigurationRevisionNo()); root.setConfigGapReasonCode(command.gap() ? "NO_APPLICABLE_RULE" : null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(Write command) {
        var root = command.checklist();
        if (!"DRAFT".equals(root.getStatusCode()) || !Objects.equals(root.getConfigurationRevisionId(), command.configuration().revisionId())) {
            throw new IllegalArgumentException("只能替换同一配置修订下的清单草稿匹配事实");
        }
        var query = new CutoverChecklistRowQuery(root.getTenantId(), root.getCutoverTaskId(), root.getId());
        drafts.deleteDraftMatchCandidates(query); drafts.deleteDraftInputDimensions(query);
        Map<String, CutoverConfigurationDimensionDO> definitions = configuration.dimensions(root.getConfigurationRevisionId()).stream()
                .collect(Collectors.toMap(CutoverConfigurationDimensionDO::getDimensionCode, value -> value));
        for (var entry : command.dimensions().entrySet()) {
            var definition = definitions.get(entry.getKey());
            if (definition == null || !Boolean.TRUE.equals(definition.getEnabledFlag())) throw new IllegalArgumentException("匹配维度未在精确配置中启用");
            for (int i = 0; i < entry.getValue().size(); i++) {
                var value = CutoverMetadataScalar.from(entry.getValue().get(i));
                inputs.insert(CutoverChecklistInputDimensionDO.builder().tenantId(root.getTenantId()).checklistId(root.getId())
                        .configurationDimensionId(definition.getId()).dimensionCode(entry.getKey()).valueNo(i)
                        .valueTypeCode(value.type()).valueText(value.text()).valueNumber(value.number()).valueBoolean(value.bool()).valueDatetime(value.datetime())
                        .createUserId(command.actorId()).createTime(command.time()).build());
            }
        }
        var items = command.configuration().items().stream().collect(Collectors.toMap(CutoverFrozenConfiguration.ItemDefinition::id, value -> value));
        Set<Long> matched = Set.copyOf(command.matchedRuleIds());
        if (!matched.containsAll(command.selectedRuleIds())) throw new IllegalArgumentException("不能采用未命中的配置规则");
        for (var rule : command.configuration().rules()) {
            if (!matched.contains(rule.id())) continue;
            var item = items.get(rule.itemDefinitionId());
            var row = CutoverChecklistMatchCandidateDO.builder().checklistId(root.getId()).stableItemKey(item.stableItemKey())
                    .itemDefinitionRevisionId(item.id()).bindingRuleRevisionId(rule.id()).bindingRuleVersion(rule.version().longValue())
                    .priority(rule.priority()).requiredResultFlag(rule.requiredResult()).selectedFlag(command.selectedRuleIds().contains(rule.id())).build();
            row.setTenantId(root.getTenantId()); row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
            row.setCreateTime(command.time()); row.setUpdateTime(command.time()); row.setDeleted(false); candidates.insert(row);
        }
        Set<Long> existing = devices.selectListByParents(CutoverChecklistDeviceContextListQuery.builder()
                .tenantId(root.getTenantId()).checklistIds(Set.of(root.getId())).build()).stream()
                .map(CutoverChecklistDeviceContextDO::getAssessmentDeviceId).collect(Collectors.toSet());
        for (var device : assessmentDevices.selectListByParents(CutoverAssessmentDeviceListQuery.builder()
                .tenantId(root.getTenantId()).assessmentIds(Set.of(root.getAssessmentId())).build())) {
            if (existing.contains(device.getId())) continue;
            var row = CutoverChecklistDeviceContextDO.builder().checklistId(root.getId()).assessmentDeviceId(device.getId())
                    .deviceId(device.getDeviceId()).sortOrder(device.getSortOrder()).build();
            row.setTenantId(root.getTenantId()); row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
            row.setCreateTime(command.time()); row.setUpdateTime(command.time()); row.setDeleted(false); devices.insert(row);
        }
    }

    public Map<String, List<String>> dimensions(CutoverChecklistDO root) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var row : inputs.selectListByParents(CutoverChecklistInputDimensionListQuery.builder()
                .tenantId(root.getTenantId()).checklistIds(Set.of(root.getId())).build())) {
            Object value = new CutoverMetadataScalar(row.getValueTypeCode(), row.getValueText(), row.getValueNumber(), row.getValueBoolean(), row.getValueDatetime()).value();
            result.computeIfAbsent(row.getDimensionCode(), ignored -> new ArrayList<>()).add(String.valueOf(value));
        }
        return result;
    }

    public String matchTrace(CutoverChecklistDO root) {
        var rows = candidates.selectListByParents(CutoverChecklistMatchCandidateListQuery.builder()
                .tenantId(root.getTenantId()).checklistIds(Set.of(root.getId())).build());
        return JsonUtils.toJsonString(Map.of("candidates", rows.stream().map(row -> Map.of(
                "stableItemKey", row.getStableItemKey(), "itemDefinitionId", row.getItemDefinitionRevisionId(),
                "matchedRuleId", row.getBindingRuleRevisionId(), "priority", row.getPriority(),
                "required", row.getRequiredResultFlag(), "selected", row.getSelectedFlag())).toList(),
                "gap", root.getConfigGapReasonCode() != null));
    }
    public String gap(CutoverChecklistDO root) {
        return root.getConfigGapReasonCode() == null ? null : JsonUtils.toJsonString(Map.of("reason", root.getConfigGapReasonCode()));
    }
}
