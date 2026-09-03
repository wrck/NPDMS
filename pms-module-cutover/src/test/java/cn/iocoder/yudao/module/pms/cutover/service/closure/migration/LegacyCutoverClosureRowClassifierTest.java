package cn.iocoder.yudao.module.pms.cutover.service.closure.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyCutoverClosureRowClassifierTest {

    @Test
    void retainsStructurallyValidLegacyStepWithoutCreatingClosureFacts() {
        long tenantId = 91L;
        MigrationSourceRecordFact source = new MigrationSourceRecordFact(101L, tenantId, 201L,
                "NPDMS_LEGACY", "pms_cut_execution", "301", "STEP-301",
                payload(tenantId), "a".repeat(64), LocalDateTime.of(2026, 9, 2, 8, 0), null);

        assertEquals(LegacyCutoverClosureRowClassifier.Disposition.RETAINED,
                new LegacyCutoverClosureRowClassifier().classify(tenantId, source));
    }

    static String payload(long tenantId) {
        return """
                {"id":301,"task_id":41,"code":"STEP-301","step_name":"切换核心路由",\
                "operator_user_id":10,"operation_time":"2026-08-01T10:00:00",\
                "result":"执行完成","exception_record":null,"evidence_url":"https://legacy/evidence/301",\
                "status":2,"remark":null,"version":3,"creator":"10",\
                "create_time":"2026-08-01T09:00:00","updater":"10",\
                "update_time":"2026-08-01T10:00:00","deleted":false,"tenant_id":%d}
                """.formatted(tenantId).replace("\\\n", "");
    }
}
