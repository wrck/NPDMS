package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistBindingRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverFrozenConfigurationQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CutoverChecklistConfigurationQueryServiceTest {

    @Test
    void resolvesDisabledRevisionByExactFrozenIdentity() {
        CutoverConfigurationRevisionMapper revisions = mock(CutoverConfigurationRevisionMapper.class);
        CutoverChecklistItemDefinitionRevisionMapper items = mock(CutoverChecklistItemDefinitionRevisionMapper.class);
        CutoverChecklistBindingRuleRevisionMapper rules = mock(CutoverChecklistBindingRuleRevisionMapper.class);
        CutoverChecklistConfigurationQueryService service =
                new CutoverChecklistConfigurationQueryService(revisions, items, rules);
        CutoverFrozenConfigurationQuery query = new CutoverFrozenConfigurationQuery(1L, 10L, "CUTOVER_MAIN", 2);

        CutoverConfigurationRevisionDO revision = new CutoverConfigurationRevisionDO();
        revision.setId(10L);
        revision.setConfigurationCode("CUTOVER_MAIN");
        revision.setRevisionNo(2);
        revision.setStatusCode("DISABLED");
        revision.setDictionarySnapshot("{}");
        revision.setDimensionDefinitionSnapshot("[]");
        when(revisions.selectFrozen(query)).thenReturn(revision);

        CutoverChecklistItemDefinitionRevisionDO item = new CutoverChecklistItemDefinitionRevisionDO();
        item.setId(20L);
        item.setStableItemKey("SURVEY_BACKGROUND");
        item.setItemDefinitionVersion(1);
        item.setItemTypeCode("BUSINESS_SURVEY");
        item.setItemName("割接背景");
        item.setInterfaceFormatCode("FORM");
        item.setInterfaceSchema("{}");
        item.setWorkModeCode("MANUAL");
        item.setRequiredFlag(true);
        item.setStatusCode("ENABLED");
        item.setSortOrder(10);

        CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
        rule.setId(30L);
        rule.setStableRuleKey("RULE_BACKGROUND");
        rule.setItemDefinitionId(20L);
        rule.setItemDefinitionVersion(1);
        rule.setDimensionConditionSnapshot("{\"CUTOVER_LEVEL\":[\"A\"]}");
        rule.setPriority(10);
        rule.setRequiredResult(true);
        rule.setStatusCode("ENABLED");
        CutoverConfigurationChildrenQuery children = new CutoverConfigurationChildrenQuery(10L);
        when(items.selectListByRevision(children)).thenReturn(List.of(item));
        when(rules.selectListByRevision(children)).thenReturn(List.of(rule));

        CutoverFrozenConfiguration resolved = service.resolveFrozen(query);

        assertEquals("DISABLED", resolved.statusCode());
        assertEquals(List.of("SURVEY_BACKGROUND"), resolved.items().stream()
                .map(CutoverFrozenConfiguration.ItemDefinition::stableItemKey).toList());
        assertEquals(List.of(30L), resolved.rules().stream()
                .map(CutoverFrozenConfiguration.BindingRule::id).toList());
    }
}
