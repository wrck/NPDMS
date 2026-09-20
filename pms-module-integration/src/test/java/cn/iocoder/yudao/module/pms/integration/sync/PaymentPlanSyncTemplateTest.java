package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectPaymentAcceptanceApi;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentPlanSyncTemplateTest {
    @Test void explicitLookupDoesNotGuessUnknownEvents() {
        var definition = PaymentPlanSyncTemplate.create(1L);
        var source = definition.sources().getFirst();
        source.mappings().stream().filter(m -> "nodeType".equals(m.target())).findFirst().orElseThrow().values(Map.of("Final", "STAGE"));
        source.mappings().stream().filter(m -> "nodeCode".equals(m.target())).findFirst().orElseThrow().values(Map.of("Final", "S5"));
        var rows = List.<Map<String, Object>>of(
                Map.of("id", 1, "contractNo", "C1", "referenceEventName", "Final", "validReferenceEvent", "Final", "eventPlanHappenDate", "2026-12-31 12:00:00"),
                Map.of("id", 2, "contractNo", "C1", "referenceEventName", "Unknown", "validReferenceEvent", "Unknown", "eventPlanHappenDate", "2026-12-31 12:00:00"));
        var mapped = new SyncFieldMapper().transform(definition, new MysqlSyncReader.Snapshot(LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("ACCEPTANCE", "pm_pb_plan_from_sms", rows)), 100), List.of());
        assertEquals("S5", mapped.getFirst().fields().get("nodeCode"));
        assertNull(mapped.getLast().fields().get("nodeCode"));
        assertNull(mapped.getLast().fields().get("nodeType"));
        var adapter = new PaymentPlanAcceptanceAdapter(mock(ProjectPaymentAcceptanceApi.class));
        assertDoesNotThrow(() -> new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
    @Test void adapterDelegatesWritesToProjectOwnerAndPreservesIssue() {
        TenantContextHolder.setTenantId(1L);
        try {
            var project = mock(ProjectPaymentAcceptanceApi.class);
            when(project.refresh(any())).thenReturn(List.of(new ProjectPaymentAcceptanceApi.Result("1", null, "ISSUE", null, null, "missing mapping")));
            var row = new DataSyncAdapter.Row("ACCEPTANCE", "1", Map.of("contractNo", "C1", "referenceEvent", "Final", "acceptanceTime", "2026-12-31T12:00:00"), null);
            var result = new PaymentPlanAcceptanceAdapter(project).apply(new DataSyncAdapter.Batch("integration:1:9", List.of(row), List.of(), true, "RETAIN", false, "UPSERT", false));
            assertEquals("ISSUE", result.getFirst().action()); assertNull(result.getFirst().targetId());
            verify(project).refresh(argThat(r -> r.tenantId() == 1L && r.entries().getFirst().nodeCode() == null));
        } finally { TenantContextHolder.clear(); }
    }
}
