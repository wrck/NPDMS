package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistFieldDefinitionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverChecklistFieldOptionMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverFieldDefinitionStoreTest {
    private final CutoverChecklistFieldDefinitionMapper fields = mock(CutoverChecklistFieldDefinitionMapper.class);
    private final CutoverChecklistFieldOptionMapper options = mock(CutoverChecklistFieldOptionMapper.class);
    private final CutoverFieldDefinitionStore store = new CutoverFieldDefinitionStore(fields, options);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void mixedRevisionsAndCustomItemKeepExactSchemasWhileSystemItemsUseOneBatch() {
        var definitions = List.of(definition(10L, null, "old"), definition(11L, null, "new"), definition(null, 3L, "custom"));
        when(fields.selectListByParents(any())).thenAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistFieldDefinitionListQuery) call.getArgument(0);
            assertEquals(1L, query.tenantId());
            return definitions.stream().flatMap(def -> def.fields().stream()).filter(row ->
                    query.itemDefinitionRevisionIds() != null && row.getItemDefinitionRevisionId() != null && query.itemDefinitionRevisionIds().contains(row.getItemDefinitionRevisionId())
                    || query.customChecklistItemIds() != null && row.getCustomChecklistItemId() != null && query.customChecklistItemIds().contains(row.getCustomChecklistItemId())).toList();
        });
        when(options.selectListByParents(any())).thenAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistFieldOptionListQuery) call.getArgument(0);
            return definitions.stream().flatMap(def -> def.options().stream()).filter(row -> query.fieldDefinitionIds().contains(row.getFieldDefinitionId())).toList();
        });
        var items = List.of(item(1L, 10L), item(2L, 11L), item(3L, null), item(4L, 10L));
        var expected = items.stream().map(store::withSchema).map(CutoverChecklistItemDO::getInterfaceSchemaSnapshot).toList();
        clearInvocations(fields, options);
        var actual = store.withSchemas(items).stream().map(CutoverChecklistItemDO::getInterfaceSchemaSnapshot).toList();
        for (int i = 0; i < expected.size(); i++) assertEquals(JsonUtils.parseTree(expected.get(i)), JsonUtils.parseTree(actual.get(i)));
        verify(fields).selectListByParents(argThat(q -> Set.of(10L, 11L).equals(q.itemDefinitionRevisionIds())));
        verify(fields).selectListByParents(argThat(q -> Set.of(3L).equals(q.customChecklistItemIds())));
        verify(options, times(2)).selectListByParents(any());
        verifyNoMoreInteractions(fields, options);
    }

    @Test void foreignTenantOrIgnoredTenantCannotReadAnyDefinitions() {
        var item = item(1L, 10L); item.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> store.withSchemas(List.of(item)));
        item.setTenantId(1L); TenantContextHolder.setIgnore(true);
        assertThrows(IllegalArgumentException.class, () -> store.withSchemas(List.of(item)));
        verifyNoInteractions(fields, options);
    }

    @Test void emptyItemsNeverBecomeAnUnfilteredDefinitionQuery() {
        assertEquals(List.of(), store.withSchemas(List.of())); verifyNoInteractions(fields, options);
    }

    private static CutoverChecklistItemDO item(Long id, Long revision) {
        var item = new CutoverChecklistItemDO(); item.setId(id); item.setTenantId(1L); item.setItemDefinitionId(revision); return item;
    }
    private static CutoverFieldSchemaCodec.Definition definition(Long revision, Long custom, String label) {
        return CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder().tenantId(1L)
                .itemDefinitionRevisionId(revision).customChecklistItemId(custom).itemTypeCode("RISK")
                .schema(Map.of("type", "string", "label", label, "options", List.of(Map.of("label", label, "value", label)))).build());
    }
}
