package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsWarrantyRenewalSyncAdapterTest {
    DeviceWarrantyRenewalMigrationApi owner = mock(DeviceWarrantyRenewalMigrationApi.class);
    SyncTaskService tasks = mock(SyncTaskService.class);
    SyncRunMapper runs = mock(SyncRunMapper.class);
    cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi eligibility=mock(cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.class);
    DppmsWarrantyRenewalSyncAdapter adapter = new DppmsWarrantyRenewalSyncAdapter(owner, tasks, runs,eligibility);
    SyncTaskDO task;
    SyncRunDO run;
    Map<String, Object> original;
    static final String KEY = "fb_service:id-1:SN";
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(eligibility.resolve(any())).thenReturn(List.of(new cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.Result("SN",true,null)));
        task = new SyncTaskDO();task.setId(20L);task.setTenantId(1L);task.setVersion(2);
        task.setAdapter("DPPMS_WARRANTY_RENEWAL");task.setSourceSystem("DPPMS");task.setActiveRunId(31L);
        task.setDefinition(JsonUtils.toJsonString(DppmsLocalWarrantyRenewalSyncTemplate.create(11L)));
        when(tasks.required(20L)).thenReturn(task);
        run = new SyncRunDO();run.setId(31L);run.setTenantId(1L);run.setTaskId(20L);run.setConfigVersion(2);
        run.setPreview(false);run.setStatus("APPLYING");run.setCreator("7");when(runs.selectScoped(any())).thenReturn(run);
        original = new LinkedHashMap<>();DeviceWarrantyRenewalMigrationApi.ORIGINAL_COLUMNS.forEach(k -> original.put(k, null));
        original.put("id", "id-1");original.put("barcode", "SN");original.put("warranty", "1");
    }
    @AfterEach void close() { TenantContextHolder.clear(); }
    Batch batch(String key, Long target, List<Binding> bindings) {
        return new Batch("integration:1:20", List.of(new Row("fb_service", key, original, target)), bindings, true, "RETAIN", false, "UPSERT", false);
    }
    @Test void fullEditableTemplateDoesNotInventModificationWatermarkOrPilotIds() {
        var definition = DppmsLocalWarrantyRenewalSyncTemplate.create(11L);var source = definition.sources().getFirst();
        assertNull(source.updatedAt());assertFalse(source.parameters().containsKey("projectIds"));
        assertFalse(source.parameters().containsKey("projectTypes"));assertFalse(source.sql().contains("fb_shipment"));assertFalse(source.sql().contains("pm_project"));
        assertEquals(10, source.mappings().size());MysqlSyncReader.compile(source);
        source.sql("SELECT id,barcode FROM fb_service");assertEquals("SELECT id,barcode FROM fb_service", source.sql());
    }
    @Test void callsOwnerWithAllTenOriginalFieldsAndReturnsOfficialBinding() {
        when(owner.apply(any())).thenAnswer(call -> {
            var request = (DeviceWarrantyRenewalMigrationApi.Request) call.getArgument(0);
            assertEquals(7L, request.actorId());var entry = request.entries().getFirst();
            assertEquals("1", entry.source().years());assertNull(entry.source().latest());
            return List.of(new DeviceWarrantyRenewalMigrationApi.Result(KEY, 99L, "CREATED", Map.of("warrantyMonths", 12), "proof"));
        });
        var change = adapter.apply(batch(KEY, null, List.of())).getFirst();
        assertEquals(99L, change.targetId());assertEquals("proof", change.after().get("fingerprint"));
    }
    @Test void missingRawColumnWrongCompositeKeyAndStaleRunNeverReachOwner() {
        original.remove("remark");assertThrows(IllegalArgumentException.class, () -> adapter.apply(batch(KEY, null, List.of())));
        original.put("remark", null);assertThrows(IllegalArgumentException.class, () -> adapter.apply(batch("id-1", null, List.of())));
        run.setConfigVersion(1);assertThrows(IllegalArgumentException.class, () -> adapter.apply(batch(KEY, null, List.of())));
        verifyNoInteractions(owner);
    }
    @Test void refusesMissingFingerprintAndSourceReset() {
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(batch(KEY, 99L, List.of(new Binding("fb_service", KEY, 99L, Map.of())))));
        var definition = DppmsLocalWarrantyRenewalSyncTemplate.create(11L);definition.resetMappingsBeforeLoad(true);
        task.setDefinition(JsonUtils.toJsonString(definition));
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(batch(KEY, null, List.of())));verifyNoInteractions(owner);
    }
    @Test void missingLocalParentsAreIssuesAndProvenIneligibleRowsNeverReachWriter() {
        when(eligibility.resolve(any())).thenReturn(List.of(new cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.Result("SN",false,"WARRANTY_LOCAL_PARENT_MISSING")));
        assertEquals("ISSUE",adapter.apply(batch(KEY,null,List.of())).getFirst().action());
        when(eligibility.resolve(any())).thenReturn(List.of(new cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.Result("SN",false,null)));
        assertEquals("SKIPPED",adapter.apply(batch(KEY,null,List.of())).getFirst().action());verifyNoInteractions(owner);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(KEY,99L,List.of(new Binding("fb_service",KEY,99L,Map.of("fingerprint","before"))))));
    }
}
