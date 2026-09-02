package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import java.util.List;

public record CutoverFrozenConfiguration(Long revisionId,
                                         String configurationCode,
                                         Integer revisionNo,
                                         String statusCode,
                                         String dictionarySnapshot,
                                         String dimensionDefinitionSnapshot,
                                         List<ItemDefinition> items,
                                         List<BindingRule> rules) {

    public record ItemDefinition(Long id,
                                 String stableItemKey,
                                 Integer itemDefinitionVersion,
                                 String itemTypeCode,
                                 String itemName,
                                 String itemDescription,
                                 String interfaceFormatCode,
                                 String interfaceSchema,
                                 String workModeCode,
                                 boolean required,
                                 Integer sortOrder) {
    }

    public record BindingRule(Long id,
                              String stableRuleKey,
                              Long itemDefinitionId,
                              Integer itemDefinitionVersion,
                              String dimensionConditionSnapshot,
                              Integer priority,
                              boolean requiredResult,
                              Integer version) {
        public BindingRule(Long id, String stableRuleKey, Long itemDefinitionId,
                           Integer itemDefinitionVersion, String dimensionConditionSnapshot,
                           Integer priority, boolean requiredResult) {
            this(id, stableRuleKey, itemDefinitionId, itemDefinitionVersion,
                    dimensionConditionSnapshot, priority, requiredResult, 0);
        }
    }
}
