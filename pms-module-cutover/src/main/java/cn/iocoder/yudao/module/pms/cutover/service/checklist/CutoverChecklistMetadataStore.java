package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
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

    public record RevisionWrite(CutoverChecklistDO source, CutoverChecklistDO target, Long actorId, LocalDateTime time) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void carryDeviceVersions(RevisionWrite command) {
        if (!"DRAFT".equals(command.target().getStatusCode())
                || !Objects.equals(command.source().getTenantId(), command.target().getTenantId())
                || !Objects.equals(command.source().getCutoverTaskId(), command.target().getCutoverTaskId())) {
            throw new IllegalArgumentException("软件版本只能沿用同任务的历史清单");
        }
        var prior = devices.selectListByParents(CutoverChecklistDeviceContextListQuery.builder()
                .tenantId(command.source().getTenantId()).checklistIds(Set.of(command.source().getId())).build()).stream()
                .collect(Collectors.toMap(CutoverChecklistDeviceContextDO::getDeviceId, value -> value));
        boolean upgrade = dimensions(command.target()).getOrDefault("CUTOVER_TYPE", List.of()).contains("VERSION_UPGRADE");
        for (var current : devices.selectListByParents(CutoverChecklistDeviceContextListQuery.builder()
                .tenantId(command.target().getTenantId()).checklistIds(Set.of(command.target().getId())).build())) {
            var source = prior.get(current.getDeviceId());
            if (source == null) continue;
            var update = CutoverChecklistDeviceContextDO.builder().id(current.getId()).productModel(source.getProductModel())
                    .currentSoftwareVersion(source.getCurrentSoftwareVersion())
                    .targetSoftwareVersion(upgrade ? source.getTargetSoftwareVersion() : null).build();
            update.setUpdater(String.valueOf(command.actorId())); update.setUpdateTime(command.time());
            if (devices.updateById(update) != 1) throw new IllegalStateException("修订设备版本上下文复制失败");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void copyToRevision(RevisionWrite command) {
        var source = command.source(); var target = command.target();
        if (!"DRAFT".equals(target.getStatusCode()) || !Objects.equals(source.getTenantId(), target.getTenantId())
                || !Objects.equals(source.getCutoverTaskId(), target.getCutoverTaskId())
                || !Objects.equals(source.getConfigurationRevisionId(), target.getConfigurationRevisionId())) {
            throw new IllegalArgumentException("清单修订只能复制同任务、同配置的来源事实");
        }
        for (var sourceRow : inputs.selectListByParents(CutoverChecklistInputDimensionListQuery.builder()
                .tenantId(source.getTenantId()).checklistIds(Set.of(source.getId())).build())) {
            var row = new CutoverChecklistInputDimensionDO(); org.springframework.beans.BeanUtils.copyProperties(sourceRow, row);
            row.setId(null); row.setChecklistId(target.getId()); row.setCreateUserId(command.actorId()); row.setCreateTime(command.time());
            inputs.insert(row);
        }
        for (var sourceRow : candidates.selectListByParents(CutoverChecklistMatchCandidateListQuery.builder()
                .tenantId(source.getTenantId()).checklistIds(Set.of(source.getId())).build())) {
            var row = new CutoverChecklistMatchCandidateDO(); org.springframework.beans.BeanUtils.copyProperties(sourceRow, row);
            row.setId(null); row.setChecklistId(target.getId()); row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId())); row.setCreateTime(command.time()); row.setUpdateTime(command.time());
            candidates.insert(row);
        }
        for (var sourceRow : devices.selectListByParents(CutoverChecklistDeviceContextListQuery.builder()
                .tenantId(source.getTenantId()).checklistIds(Set.of(source.getId())).build())) {
            var row = new CutoverChecklistDeviceContextDO(); org.springframework.beans.BeanUtils.copyProperties(sourceRow, row);
            row.setId(null); row.setChecklistId(target.getId()); row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId())); row.setCreateTime(command.time()); row.setUpdateTime(command.time());
            devices.insert(row);
        }
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
