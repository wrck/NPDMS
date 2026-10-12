package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsRoleSourceContractTest {
    @Test void realSourceColumnValuesProduceTheApprovedThirtyOneBindingsAndTwentyEightRoles() throws Exception {
        try (var input = getClass().getResourceAsStream("/sync/dppms-active-role-source-fixture.json")) {
            assertNotNull(input);
            var fixture = JsonUtils.getObjectMapper().readValue(input, new TypeReference<Map<String, List<Map<String, Object>>>>() {});
            var definition = DppmsRoleSyncTemplate.create(55L);
            var sources = definition.sources().stream().map(source -> new MysqlSyncReader.SourceRows(source.object(), source.sourceObject(), fixture.get(source.object()))).toList();
            var snapshot = new MysqlSyncReader.Snapshot(LocalDateTime.of(2026, 10, 9, 11, 0), sources, 31);
            var rows = new SyncFieldMapper().transform(definition, snapshot, List.of());
            assertEquals(31, rows.size());
            var roles = mock(SystemRoleMigrationApi.class);
            var tasks = mock(SyncTaskService.class);
            var bindings = mock(SyncBindingMapper.class);
            var task = new SyncTaskDO().setSourceSystem("DPPMS").setAdapter("DPPMS_ROLES").setConnectionId(55L);
            task.setId(12L); task.setTenantId(1L);
            when(tasks.required(12L)).thenReturn(task);
            // A synthetic, isolated binding exercises identity resolution; it is not a live adoption decision.
            when(bindings.selectIdentitySources(any())).thenReturn(List.of(new SyncBindingDO().setSourceKey("81").setTargetId(1001L)));
            when(roles.preview(any())).thenReturn(List.of());
            TenantContextHolder.setTenantId(1L);
            try {
                new DppmsRoleSyncAdapter(roles, tasks, bindings).preview(new DataSyncAdapter.Batch("integration:1:12", rows, List.of(), true, "RETAIN", true, "UPSERT", false));
            } finally { TenantContextHolder.clear(); }
            var request = ArgumentCaptor.forClass(SystemRoleMigrationApi.Request.class);
            verify(roles).preview(request.capture());
            var entries = request.getValue().entries();
            assertEquals(28, entries.stream().map(e -> e.definition().code()).distinct().count());
            assertEquals(25, entries.stream().filter(e -> !e.referenceOnly()).count());
            assertTrue(entries.stream().noneMatch(e -> e.definition().code().startsWith("ROLE_") || e.definition().code().matches("[0-9]+")));
            assertTrue(entries.stream().noneMatch(e -> e.object().equals("FND_ROLE") && e.sourceKey().equals("15")));
            var bySource = entries.stream().collect(Collectors.toMap(e -> e.object() + ":" + e.sourceKey(), e -> e));
            assertEquals("super_admin", bySource.get("T_ROLE:1").definition().code());
            assertEquals("PROJECT_MANAGER", bySource.get("T_ROLE:7").definition().code());
            assertEquals("PROJECT_MANAGER", bySource.get("FND_ROLE:12").definition().code());
            assertEquals("SERVICE_MANAGER", bySource.get("FND_ROLE:11").definition().code());
            assertEquals("projectViewer", bySource.get("FND_ROLE:6").definition().code());
            assertEquals("componentAdmin", bySource.get("FND_ROLE:22").definition().code());
            assertEquals("1001", bySource.get("FND_ROLE:1").definition().creator());
            assertNull(bySource.get("FND_ROLE:17").definition().updater());
            assertEquals(bySource.get("FND_ROLE:17").definition().createTime(), bySource.get("FND_ROLE:17").definition().updateTime());
            assertEquals("普通用户", bySource.get("T_ROLE:2").definition().name());
            verify(roles, never()).apply(any());
        }
    }
}
