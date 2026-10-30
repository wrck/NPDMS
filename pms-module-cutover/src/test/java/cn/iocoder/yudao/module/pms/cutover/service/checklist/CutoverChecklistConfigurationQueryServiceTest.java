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
    @org.junit.jupiter.api.BeforeEach
    void setTenant() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L); }
    @org.junit.jupiter.api.AfterEach
    void clearTenant() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }


    @Test
    void resolvesDisabledRevisionByExactFrozenIdentity() {
        CutoverConfigurationRevisionMapper revisions = mock(CutoverConfigurationRevisionMapper.class);
        CutoverChecklistItemDefinitionRevisionMapper items = mock(CutoverChecklistItemDefinitionRevisionMapper.class);
        CutoverChecklistBindingRuleRevisionMapper rules = mock(CutoverChecklistBindingRuleRevisionMapper.class);
        var metadata = mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore.class);
        var fields = mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore.class);
        CutoverChecklistConfigurationQueryService service =
                new CutoverChecklistConfigurationQueryService(revisions, items, rules, metadata, fields);
        CutoverFrozenConfigurationQuery query = new CutoverFrozenConfigurationQuery(1L, 10L, "CUTOVER_MAIN", 2);

        CutoverConfigurationRevisionDO revision = new CutoverConfigurationRevisionDO();
        revision.setId(10L);
        revision.setConfigurationCode("CUTOVER_MAIN");
        revision.setRevisionNo(2);
        revision.setStatusCode("DISABLED");
        when(revisions.selectFrozen(query)).thenReturn(revision);

        CutoverChecklistItemDefinitionRevisionDO item = new CutoverChecklistItemDefinitionRevisionDO();
        item.setId(20L);
        item.setStableItemKey("SURVEY_BACKGROUND");
        item.setItemDefinitionVersion(1);
        item.setItemTypeCode("BUSINESS_SURVEY");
        item.setItemName("割接背景");
        item.setInterfaceFormatCode("FORM");
        item.setWorkModeCode("MANUAL");
        item.setRequiredFlag(true);
        item.setStatusCode("ENABLED");
        item.setSortOrder(10);

        CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
        rule.setId(30L);
        rule.setStableRuleKey("RULE_BACKGROUND");
        rule.setItemDefinitionId(20L);
        rule.setItemDefinitionVersion(1);
        rule.setPriority(10);
        rule.setRequiredResult(true);
        rule.setStatusCode("ENABLED");
        CutoverConfigurationChildrenQuery children = new CutoverConfigurationChildrenQuery(10L);
        when(items.selectListByRevision(children)).thenReturn(List.of(item));
        when(rules.selectListByRevision(children)).thenReturn(List.of(rule));

        var schema = java.util.Map.<String, Object>of("fields", List.of(java.util.Map.of(
                "code", "backgroundDescription", "type", "TEXT", "required", true)));
        var definition = cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec.encode(
                cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec.Write.builder()
                        .tenantId(1L).itemDefinitionRevisionId(20L).businessCategoryCode("CUTOVER_BACKGROUND")
                        .itemTypeCode("BUSINESS_SURVEY").schema(schema).build());
        when(fields.definitions(java.util.Set.of(20L))).thenReturn(java.util.Map.of(20L, definition));
        when(metadata.dictionaryView(10L)).thenReturn(java.util.Map.of());
        when(metadata.dimensionViews(10L)).thenReturn(List.of());
        when(metadata.conditionViews(new cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore.ConditionRead(
                10L, java.util.Set.of(30L)))).thenReturn(java.util.Map.of(30L, java.util.Map.of("CUTOVER_LEVEL", List.of("A"))));

        CutoverFrozenConfiguration resolved = service.resolveFrozen(query);
        assertEquals(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(schema)),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(resolved.items().getFirst().interfaceSchema()));
        assertEquals("A", cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                resolved.rules().getFirst().dimensionConditionSnapshot()).path("CUTOVER_LEVEL").get(0).asText());
        assertEquals(10L, resolved.revisionId());
        assertEquals("CUTOVER_MAIN", resolved.configurationCode());
        assertEquals(2, resolved.revisionNo());

        assertEquals("DISABLED", resolved.statusCode());
        assertEquals(List.of("SURVEY_BACKGROUND"), resolved.items().stream()
                .map(CutoverFrozenConfiguration.ItemDefinition::stableItemKey).toList());
        assertEquals(List.of(30L), resolved.rules().stream()
                .map(CutoverFrozenConfiguration.BindingRule::id).toList());
    }
}
