package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyCutoverPlanRowConverterTest {

    @Test
    void convertsQualifiedFrozenSourceIntoRootSnapshotAndFourSteps() {
        var source = source(81L, 71L, legacyPayload(false));

        var converted = new LegacyCutoverPlanRowConverter().convert(1L, source);

        assertThat(converted.legacyPlanId()).isEqualTo(91L);
        assertThat(converted.legacyTaskId()).isEqualTo(41L);
        assertThat(converted.legacyStatus()).isEqualTo(2);
        assertThat(converted.legacyVersion()).isEqualTo(6);
        assertThat(converted.steps()).extracting(LegacyCutoverPlanRowConverter.LegacyStep::sectionCode)
                .containsExactly("PRE_OPERATION", "OPERATION", "POST_BUSINESS_TEST", "ROLLBACK");
        assertThat(converted.steps()).extracting(LegacyCutoverPlanRowConverter.LegacyStep::content)
                .containsExactly("割接前检查", "正式操作", "业务验证", "回退操作");
        JsonNode snapshot = JsonUtils.parseObject(converted.sourceSnapshot(), JsonNode.class);
        assertThat(snapshot.propertyNames()).containsExactlyInAnyOrder("sourceTable", "sourceId", "sourceTenantId",
                "sourceTaskId", "sourceVersion", "sourceStatusRaw", "mappingVersion", "code", "name", "level",
                "remark");
        assertThat(snapshot.path("mappingVersion").asText()).isEqualTo("FCUT004_LEGACY_V1");
        assertThat(snapshot.has("approvedBy")).isFalse();
    }

    @Test
    void keepsDeletedSourceAsRetainedCandidateWithoutCreatingStepsRequirement() {
        var source = source(82L, 71L, legacyPayload(true)
                .replace("\"割接前检查\"", "\" \"")
                .replace("\"正式操作\"", "\" \"")
                .replace("\"业务验证\"", "\" \"")
                .replace("\"回退操作\"", "\" \""));

        var converted = new LegacyCutoverPlanRowConverter().convert(1L, source);

        assertThat(converted.deleted()).isTrue();
        assertThat(converted.steps()).isEmpty();
    }

    static MigrationSourceRecordFact source(Long sourceRecordId, Long batchId, String payload) {
        return new MigrationSourceRecordFact(sourceRecordId, 1L, batchId, "NPDMS_LEGACY", "pms_cut_plan",
                "91", "PLAN-91", payload, "0".repeat(64), LocalDateTime.of(2026, 9, 1, 10, 0), null);
    }

    static String legacyPayload(boolean deleted) {
        return """
                {"id":91,"task_id":41,"code":" PLAN-91 ","name":" 历史割接方案 ",
                 "pre_check":" 割接前检查 ","procedure":" 正式操作 ","verification":" 业务验证 ",
                 "rollback":" 回退操作 ","level":" b ","status":2,"approved_by":1001,
                 "approved_time":1788228000000,"approval_opinion":"历史审批意见","baseline_version":3,
                 "remark":" 历史说明 ","version":6,"creator":"10","create_time":1788228000000,
                 "updater":"11","update_time":1788314400000,"deleted":%s,"tenant_id":1}
                """.formatted(deleted);
    }
}
