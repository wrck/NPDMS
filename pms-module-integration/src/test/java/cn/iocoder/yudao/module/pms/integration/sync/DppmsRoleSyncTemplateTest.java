package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsRoleSyncTemplateTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    private static final LocalDateTime HISTORY = LocalDateTime.of(2017, 2, 7, 15, 23, 38);
    private final SystemRoleMigrationApi roles = mock(SystemRoleMigrationApi.class);
    private final SyncTaskService tasks = mock(SyncTaskService.class);
    private final SyncBindingMapper bindings = mock(SyncBindingMapper.class);
    private final DppmsRoleSyncAdapter adapter = new DppmsRoleSyncAdapter(roles, tasks, bindings);
    private DataSyncAdapter.Batch batch(String sourceId, String code) {
        TenantContextHolder.setTenantId(1L);
        var task = new SyncTaskDO().setSourceSystem("DPPMS").setAdapter("DPPMS_ROLES").setConnectionId(55L);
        task.setTenantId(1L); task.setId(12L);
        when(tasks.required(12L)).thenReturn(task);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceRoleId", sourceId); fields.put("sourceCode", code); fields.put("name", "原角色");
        fields.put("sort", 117); fields.put("status", 0); fields.put("createTime", HISTORY);
        fields.put("updateTime", null);
        return new DataSyncAdapter.Batch("integration:1:12", List.of(new DataSyncAdapter.Row("FND_ROLE", sourceId, fields, null)),
                List.of(), true, "RETAIN", true, "UPSERT", false);
    }
    @Test void unknownRoleIdsAndIntegerConstantNamesCannotBecomeBusinessCodes() {
        var unknown = batch("99", "role99");
        assertThrows(IllegalArgumentException.class, () -> adapter.preview(unknown));
        assertThrows(IllegalArgumentException.class, () -> adapter.preview(batch("6", "ROLE_PROJECT_VIEWER")));
        assertThrows(IllegalArgumentException.class, () -> adapter.preview(batch("15", "salesPeople")));
        verifyNoInteractions(roles);
    }
    @Test void semanticCodesReuseTheAdjudicatedPlatformRolesWithoutModifyingTheirDefinitions() {
        when(roles.preview(any())).thenReturn(List.of());
        adapter.preview(batch("11", "serviceManager"));
        var request = ArgumentCaptor.forClass(SystemRoleMigrationApi.Request.class);
        verify(roles).preview(request.capture());
        var entry = request.getValue().entries().getFirst();
        assertEquals("SERVICE_MANAGER", entry.definition().code()); assertTrue(entry.referenceOnly());
        assertNull(entry.definition().creator());
    }
    @Test void missingUpdateTimestampUsesOnlyApprovedHistoricalCreationTime() {
        when(roles.preview(any())).thenReturn(List.of());
        adapter.preview(batch("17", "presalesStaff"));
        var request = ArgumentCaptor.forClass(SystemRoleMigrationApi.Request.class);
        verify(roles).preview(request.capture());
        assertEquals(HISTORY, request.getValue().entries().getFirst().definition().updateTime());
        assertEquals("presalesStaff", request.getValue().entries().getFirst().definition().code());
    }
    @Test void roleAuditActorCannotBeGuessedFromAnUnmigratedSameName() {
        var value = batch("1", "admin");
        value.rows().getFirst().fields().put("creatorName", "admin");
        value.rows().getFirst().fields().put("creatorSourceKey", "81");
        when(bindings.selectIdentitySources(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(value));
        verifyNoInteractions(roles);
    }
    @Test void templateUsesRealOriginalColumnsAndKeepsEveryRoleColumnInSourceEvidence() {
        var definition = DppmsRoleSyncTemplate.create(55L);
        assertDoesNotThrow(() -> new SyncDefinitionValidator(List.of(adapter)).validate(definition));
        for (var source : definition.sources()) {
            String sql = MysqlSyncReader.compile(source).sql();
            assertTrue(sql.contains("r.*")); assertTrue(sql.contains("COALESCE"));
            assertFalse(sql.contains("password")); assertFalse(source.syncPrimaryKey());
            assertEquals("migrationVersion", source.updatedAt());
        }
        var fnd = definition.sources().getLast();
        assertEquals("roleName", fnd.mappings().stream().filter(m -> "name".equals(m.target())).findFirst().orElseThrow().source());
        assertEquals("roleRemark", fnd.mappings().stream().filter(m -> "remark".equals(m.target())).findFirst().orElseThrow().source());
        var codeMapping = fnd.mappings().stream().filter(m -> "sourceCode".equals(m.target())).findFirst().orElseThrow();
        var one = definition.toBuilder().sources(List.of(fnd.toBuilder().updatedAt(null).mappings(List.of(codeMapping)).build())).build();
        var sourceRows = new MysqlSyncReader.Snapshot(HISTORY, List.of(new MysqlSyncReader.SourceRows("FND_ROLE", "fnd_roles", List.of(Map.of("id", 6)))), 1);
        assertEquals("projectViewer", new SyncFieldMapper().transform(one, sourceRows, List.of()).getFirst().fields().get("sourceCode"));
        var unknownRows = new MysqlSyncReader.Snapshot(HISTORY, List.of(new MysqlSyncReader.SourceRows("FND_ROLE", "fnd_roles", List.of(Map.of("id", 99)))), 1);
        assertThrows(IllegalArgumentException.class, () -> new SyncFieldMapper().transform(one, unknownRows, List.of()));
    }
    @Test void destructivePoliciesAndForgedTenantOwnerAreRejectedBeforeAnyOwnerWrite() {
        var good = batch("17", "presalesStaff");
        var destructive = new DataSyncAdapter.Batch(good.owner(), good.rows(), good.bindings(), true, "RETAIN", true, "UPSERT", true);
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(destructive));
        var foreign = new DataSyncAdapter.Batch("integration:2:12", good.rows(), good.bindings(), true, "RETAIN", true, "UPSERT", false);
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(foreign));
        verifyNoInteractions(roles);
    }
}
