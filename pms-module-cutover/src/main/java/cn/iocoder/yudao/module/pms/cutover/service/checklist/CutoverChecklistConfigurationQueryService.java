package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistBindingRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverFrozenConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.FROZEN_CONFIGURATION_INVALID;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.FROZEN_CONFIGURATION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.INVALID_REQUEST;

@Service
@RequiredArgsConstructor
public class CutoverChecklistConfigurationQueryService {

    private static final List<String> CONSUMABLE_STATUSES = List.of("PUBLISHED", "DISABLED");

    private final CutoverConfigurationRevisionMapper revisionMapper;
    private final CutoverChecklistItemDefinitionRevisionMapper itemMapper;
    private final CutoverChecklistBindingRuleRevisionMapper ruleMapper;
    private final CutoverConfigurationMetadataStore metadata;
    private final CutoverFieldDefinitionStore fields;

    public CutoverFrozenConfiguration resolveFrozen(CutoverFrozenConfigurationQuery query) {
        validate(query);
        CutoverConfigurationRevisionDO revision = revisionMapper.selectFrozen(query);
        if (revision == null) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_NOT_FOUND, "任务冻结的割接配置修订不存在");
        }
        if (!CONSUMABLE_STATUSES.contains(revision.getStatusCode())) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "任务冻结的割接配置修订不可消费");
        }
        CutoverConfigurationChildrenQuery children = new CutoverConfigurationChildrenQuery(revision.getId());
        List<CutoverChecklistItemDefinitionRevisionDO> itemRows = itemMapper.selectListByRevision(children).stream()
                .filter(row -> "ENABLED".equals(row.getStatusCode()))
                .toList();
        Map<Long, CutoverChecklistItemDefinitionRevisionDO> itemsById = itemRows.stream()
                .collect(Collectors.toMap(CutoverChecklistItemDefinitionRevisionDO::getId, Function.identity()));
        List<CutoverChecklistBindingRuleRevisionDO> ruleRows = ruleMapper.selectListByRevision(children).stream()
                .filter(row -> "ENABLED".equals(row.getStatusCode()))
                .toList();
        boolean brokenReference = ruleRows.stream().anyMatch(rule -> {
            CutoverChecklistItemDefinitionRevisionDO item = itemsById.get(rule.getItemDefinitionId());
            return item == null || !item.getItemDefinitionVersion().equals(rule.getItemDefinitionVersion());
        });
        if (brokenReference) {
            throw new CutoverChecklistException(FROZEN_CONFIGURATION_INVALID, "冻结配置规则引用的采集项定义不完整");
        }
        var schemas = fields.definitions(itemsById.keySet());
        var conditions = metadata.conditionViews(new CutoverConfigurationMetadataStore.ConditionRead(revision.getId(),
                ruleRows.stream().map(CutoverChecklistBindingRuleRevisionDO::getId).collect(Collectors.toSet())));
        return new CutoverFrozenConfiguration(revision.getId(), revision.getConfigurationCode(),
                revision.getRevisionNo(), revision.getStatusCode(), JsonUtils.toJsonString(metadata.dictionaryView(revision.getId())),
                JsonUtils.toJsonString(metadata.dimensionViews(revision.getId())), itemRows.stream().map(row ->
                new CutoverFrozenConfiguration.ItemDefinition(row.getId(), row.getStableItemKey(),
                        row.getItemDefinitionVersion(), row.getItemTypeCode(), row.getItemName(), row.getItemDescription(),
                        row.getInterfaceFormatCode(), JsonUtils.toJsonString(CutoverFieldSchemaCodec.decode(schemas.get(row.getId()))),
                        row.getWorkModeCode(), Boolean.TRUE.equals(row.getRequiredFlag()), row.getSortOrder())).toList(),
                ruleRows.stream().map(row -> new CutoverFrozenConfiguration.BindingRule(row.getId(), row.getStableRuleKey(),
                        row.getItemDefinitionId(), row.getItemDefinitionVersion(), JsonUtils.toJsonString(conditions.get(row.getId())),
                        row.getPriority(), Boolean.TRUE.equals(row.getRequiredResult()), row.getVersion())).toList());
    }

    private void validate(CutoverFrozenConfigurationQuery query) {
        if (query == null || query.tenantId() == null || query.tenantId() < 0
                || query.configurationRevisionId() == null || query.configurationRevisionId() <= 0
                || query.configurationCode() == null || query.configurationCode().isBlank()
                || !query.configurationCode().equals(query.configurationCode().trim())
                || query.configurationRevisionNo() == null || query.configurationRevisionNo() <= 0
                || TenantContextHolder.isIgnore() || !java.util.Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())) {
            throw new CutoverChecklistException(INVALID_REQUEST, "冻结配置身份不完整");
        }
    }

}
