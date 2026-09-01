package cn.iocoder.yudao.module.pms.cutover.service.plan.domain;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanControlledPorts;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverPlanContentCodecTest {
    private final CutoverPlanContentCodec codec = new CutoverPlanContentCodec();

    @Test
    void assemblesAbcStandardContentAndKeepsChildrenOutsideRootSnapshot() {
        for (String grade : List.of("A", "B", "C")) {
            var decoded = codec.decodeWritable(JsonUtils.parseTree(standardJson()), source(grade));
            assertThat(decoded.rootSnapshot().propertyNames())
                    .containsExactly("editMode", "overview", "riskMitigations");
            assertThat(decoded.steps()).extracting(CutoverPlanContentCodec.PlanStep::sectionCode)
                    .containsExactlyElementsOf(CutoverPlanRules.STANDARD_SECTIONS);
            assertThat(decoded.supportArrangements()).extracting(CutoverPlanContentCodec.SupportArrangement::roleCode)
                    .containsExactlyElementsOf(CutoverPlanRules.SUPPORT_ROLES);
            assertThat(decoded.rootSnapshot().path("overview").path("deviceSummary").get(0).path("serialNumber").asText())
                    .isEqualTo("Sn-001");
            assertThat(decoded.rootSnapshot().path("riskMitigations").get(0).path("riskFact").path("itemName").asText())
                    .isEqualTo("风险");
            assertThat(decoded.rootSnapshot().toString()).doesNotContain("supportArrangements").doesNotContain("steps");
        }
    }

    @Test
    void assemblesSimpleDWithOnlyOperationAndRollback() {
        var decoded = codec.decodeWritable(JsonUtils.parseTree("""
                {"editMode":"ONLINE_TEMPLATE_SIMPLE_D","steps":[
                {"sectionCode":"ROLLBACK","stepNo":1,"content":"回退"},
                {"sectionCode":"OPERATION","stepNo":1,"content":"操作"}]}
                """), source("D"));
        assertThat(decoded.steps()).extracting(CutoverPlanContentCodec.PlanStep::sectionCode)
                .containsExactly("OPERATION", "ROLLBACK");
        assertThat(decoded.rootSnapshot().propertyNames()).containsExactly("editMode");
    }

    @Test
    void freezesCompleteUploadedFileFact() {
        var decoded = codec.decodeWritable(JsonUtils.parseTree("""
                {"editMode":"FULL_FILE_UPLOAD","fileArtifactFact":{"artifactId":91,"versionNo":2,
                "referenceKey":"CUT/PLAN/91","fileFactVersion":{"artifactVersion":3,"referenceVersion":4,"availabilityVersion":5},
                "scopeVersion":6,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"ownershipConfirmed":true}
                """), source("D"));
        var portFact = new CutoverPlanFilePort.FileFact(91L, 2, "CUT/PLAN/91",
                new CutoverPlanFilePort.FileFactVersion(3, 4, 5), 6L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(decoded.fileFact()).isEqualTo(portFact);
        var controlledPort = new CutoverPlanControlledPorts.FilePort(portFact);
        assertThat(controlledPort.inspect(1L, 2L, 40L, portFact.handle())).isEqualTo(portFact);
        assertThat(controlledPort.inspections).isEqualTo(1);
    }

    @Test
    void acceptsWireLongStringsAndAssemblesLegacyReadOnlyProjection() {
        var decoded = codec.decodeWritable(JsonUtils.parseTree("""
                {"editMode":"FULL_FILE_UPLOAD","fileArtifactFact":{"artifactId":"9007199254740991","versionNo":2,
                "referenceKey":"CUT/PLAN/SAFE","fileFactVersion":{"artifactVersion":3,"referenceVersion":4,"availabilityVersion":5},
                "scopeVersion":"9007199254740992","sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},"ownershipConfirmed":true}
                """), source("D"));
        assertThat(decoded.fileFact().artifactId()).isEqualTo(9_007_199_254_740_991L);
        assertThat(decoded.fileFact().scopeVersion()).isEqualTo(9_007_199_254_740_992L);
        var legacy = codec.assembleLegacy(List.of(
                new CutoverPlanContentCodec.PlanStep("ROLLBACK", 1, "回退"),
                new CutoverPlanContentCodec.PlanStep("PRE_OPERATION", 1, "检查")));
        assertThat(legacy.path("editMode").asText()).isEqualTo("LEGACY_READ_ONLY");
        assertThat(legacy.path("steps").get(0).path("sectionCode").asText()).isEqualTo("PRE_OPERATION");
    }

    @Test
    void rejectsGradeModeMismatchAndIncompleteFailedRiskFacts() {
        assertThatThrownBy(() -> codec.decodeWritable(JsonUtils.parseTree("""
                {"editMode":"ONLINE_TEMPLATE_SIMPLE_D","steps":[
                {"sectionCode":"OPERATION","stepNo":1,"content":"操作"},
                {"sectionCode":"ROLLBACK","stepNo":1,"content":"回退"}]}
                """), source("A"))).isInstanceOf(IllegalArgumentException.class);
        var missingRisk = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(standardJson());
        missingRisk.set("riskMitigations", JsonUtils.getObjectMapper().createArrayNode());
        assertThatThrownBy(() -> codec.decodeWritable(missingRisk, source("A")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CutoverPlanSourcePort.SourceFacts source(String grade) {
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> sections = ("D".equals(grade)
                ? List.of(section("OPERATION", 1, grade), section("ROLLBACK", 2, grade))
                : CutoverPlanRules.STANDARD_SECTIONS.stream().map(code -> section(code,
                        CutoverPlanRules.STANDARD_SECTIONS.indexOf(code) + 1, grade)).toList());
        var snapshot = new CutoverPlanSourcePort.SourceSnapshot(1, 10L, 2, 20L, 1, grade,
                "D".equals(grade) ? null : 30L, "D".equals(grade) ? null : 1,
                40L, 3, 4L, List.of(new CutoverPlanSourcePort.DeviceSnapshot(
                50L, "Sn-001", 6L, "ROUTER", "ast-v1")), 60L, "DEFAULT", 1, sections);
        List<CutoverPlanSourcePort.RiskFactSnapshot> risks = "D".equals(grade) ? List.of()
                : List.of(new CutoverPlanSourcePort.RiskFactSnapshot(71L, "risk-1", 1,
                "风险", "FAILED", "描述"));
        var facts = new CutoverPlanSourcePort.SourceFacts(snapshot, risks);
        var controlledPort = new CutoverPlanControlledPorts.SourcePort(facts);
        return controlledPort.inspect(1L, 2L, 10L);
    }

    private static CutoverPlanSourcePort.TemplateSectionSnapshot section(String code, int order, String grade) {
        return new CutoverPlanSourcePort.TemplateSectionSnapshot(code, code, order,
                List.of("NETWORK"), List.of(grade), true);
    }

    private static String standardJson() {
        String steps = String.join(",", CutoverPlanRules.STANDARD_SECTIONS.stream()
                .map(code -> "{\"sectionCode\":\"" + code + "\",\"stepNo\":1,\"content\":\"" + code + "内容\"}").toList());
        return """
                {"editMode":"ONLINE_TEMPLATE_STANDARD","overview":{"projectDescription":"项目说明",
                "scheduleTable":[{"sequenceNo":1,"plannedAt":1000,"content":"计划"}],
                "preTopologyFile":null,"postTopologyFile":null,
                "deviceSummary":[{"deviceId":50,"serialNumber":"sn-001","projectAssignmentVersion":6,
                "deviceTypeCode":"ROUTER","deviceTypeSourceVersion":"ast-v1"}],"networkConfigurationFile":null},
                "steps":[%s],"riskMitigations":[{"riskFact":{"checklistItemId":71,"stableItemKey":"risk-1",
                "itemResultVersion":1,"itemName":"风险","resultCode":"FAILED","factDescription":"描述"},"mitigation":"措施"}],
                "supportArrangements":[
                {"arrangementId":null,"roleCode":"DP_RND","personName":"丁","dutyDescription":"研发","phone":"104","arrivalTime":1004},
                {"arrangementId":null,"roleCode":"CUSTOMER","personName":"甲","dutyDescription":"客户","phone":"101","arrivalTime":1001},
                {"arrangementId":null,"roleCode":"DP_SECOND_LINE","personName":"丙","dutyDescription":"二线","phone":"103","arrivalTime":1003},
                {"arrangementId":null,"roleCode":"DP_FIRST_LINE","personName":"乙","dutyDescription":"一线","phone":"102","arrivalTime":1002}]}
                """.formatted(steps);
    }
}
