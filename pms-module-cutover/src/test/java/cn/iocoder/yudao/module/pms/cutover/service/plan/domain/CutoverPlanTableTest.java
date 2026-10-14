package cn.iocoder.yudao.module.pms.cutover.service.plan.domain;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanStepDO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverPlanTableTest {
    private final CutoverPlanContentCodec codec = new CutoverPlanContentCodec();

    @Test
    void supportRowsAllowRepeatedRolesAndPreserveIndependentCustomerReferences() {
        var body = standardBody();
        var rows = body.putArray("supportArrangements");
        for (int index = 0; index < 5; index++) {
            var row = rows.addObject().putNull("arrangementId").put("roleCode", "CUSTOMER")
                    .put("personName", "联系人" + index).put("dutyDescription", "")
                    .put("phone", "").put("arrivalTime", 0);
            row.putObject("customerContactReference").put("contactId", 100 + index).put("contactVersion", index);
        }
        var decoded = codec.decodeWritable(body, source("C"));
        assertThat(decoded.supportArrangements()).hasSize(5);
        var derived = codec.rebaseDerivedDraft(decoded, source("C"));
        assertThat(derived.supportArrangements()).isEqualTo(decoded.supportArrangements());
    }

    @Test
    void duplicateRowIdentityAndUnrecognizedRoleAreRejected() {
        var body = standardBody();
        var rows = body.putArray("supportArrangements");
        var row = rows.addObject().put("arrangementId", 101).put("roleCode", "DP_FIRST_LINE")
                .put("personName", "样例甲").put("dutyDescription", "职责")
                .put("phone", "00000000000").put("arrivalTime", 1);
        rows.add(row.deepCopy());
        assertThatThrownBy(() -> codec.decodeWritable(body, source("C")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("support identity");
        rows.remove(1);
        row.put("roleCode", "UNDEFINED_ROLE");
        assertThatThrownBy(() -> codec.decodeWritable(body, source("C")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("roleCode");
    }

    @Test
    void standardTableRetainsSeparateOperatorAndTestResult() {
        var facts = source("C");
        var body = standardBody();
        var decoded = codec.decodeWritable(body, facts);
        assertThat(decoded.steps().get(0).details().operator())
                .isEqualTo("现场工程师");
        assertThat(decoded.steps().get(0).details().testResult())
                .isEqualTo("备份验证通过");
        assertThat(decoded.steps().get(0).content()).isEqualTo("备份现网配置");
    }

    @Test
    void reopenAndDerivedRevisionKeepFieldsWithoutChangingOriginalSnapshot() {
        var body = standardBody(); body.put("rollbackDescription", "恢复原链路并复核业务");
        var decoded = codec.decodeWritable(body, source("C"));
        var original = decoded.rootSnapshot().deepCopy();
        var stored = new CutoverPlanStepDO(); stored.setSectionCode("PRE_OPERATION"); stored.setStepNo(1);
        stored.setContent("备份现网配置");
        CutoverPlanContentCodec.applyDetails(stored, decoded.steps().get(0).details());
        var reopened = codec.assembleStored(new CutoverPlanContentCodec.StoredPlanContent((ObjectNode) decoded.rootSnapshot(), List.of(stored)));
        reopened.putArray("supportArrangements");
        assertThat(reopened.has("stepDetails")).isFalse();
        var content = codec.decodeWritable(reopened, source("C"));
        var revision = codec.rebaseDerivedDraft(content, source("C"));
        assertThat(content.steps().get(0).details()).isEqualTo(decoded.steps().get(0).details());
        assertThat(revision.steps()).isEqualTo(decoded.steps());
        assertThat(revision.rootSnapshot().path("rollbackDescription").asText()).isEqualTo("恢复原链路并复核业务");
        assertThat(decoded.rootSnapshot()).isEqualTo(original);
        assertThat(stored.getContent()).isEqualTo("备份现网配置");
    }

    @Test
    void simplePlanAndBlankRequiredContentCannotUseDetailsToBypassRules() {
        var body = standardBody();
        ((ObjectNode) body.path("steps").get(0)).put("content", "");
        assertThatThrownBy(() -> codec.decodeWritable(body, source("C"))).isInstanceOf(IllegalArgumentException.class);
        var simple = JsonUtils.getObjectMapper().createObjectNode(); simple.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D");
        simple.set("steps", standardBody().get("steps"));
        assertThatThrownBy(() -> codec.decodeWritable(simple, source("D"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnexpectedDetailFieldsAndOrphanedStoredMetadata() {
        var body = standardBody();
        ((ObjectNode) body.path("steps").get(0).path("details")).put("ownerUserId", 1);
        assertThatThrownBy(() -> codec.decodeWritable(body, source("C"))).isInstanceOf(IllegalArgumentException.class);
        var decoded = codec.decodeWritable(standardBody(), source("C"));
        var incomplete = new CutoverPlanStepDO(); incomplete.setSectionCode("PRE_OPERATION"); incomplete.setStepNo(1);
        incomplete.setContent("备份现网配置"); incomplete.setOperatorName("现场工程师");
        assertThatThrownBy(() -> codec.assembleStored(new CutoverPlanContentCodec.StoredPlanContent((ObjectNode) decoded.rootSnapshot(), List.of(incomplete))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stored step details");
    }

    @Test
    void previousPlainStepsRemainPlainAndDoNotGainMetadata() {
        var body = standardBody(); ((ObjectNode) body.path("steps").get(0)).remove("details");
        var decoded = codec.decodeWritable(body, source("C"));
        assertThat(decoded.rootSnapshot().has("stepDetails")).isFalse();
        assertThat(decoded.steps().get(0).details()).isNull();
        assertThat(decoded.steps().get(0).content()).isEqualTo("备份现网配置");
    }

    @Test
    void equipmentDetailsRemainInsideFrozenDeviceScope() {
        var body = standardBody();
        ((ObjectNode) body.get("overview")).set("deviceDetails", JsonUtils.parseTree("""
                [{"deviceId":50,"productModel":"测试型号","currentVersion":"R1","targetVersion":"R2","remark":"计划升级"}]
                """));
        var content = codec.decodeWritable(body, source("C"));
        assertThat(content.rootSnapshot().path("overview").path("deviceDetails").get(0).path("targetVersion").asText()).isEqualTo("R2");
        assertThat(codec.rebaseDerivedDraft(content, source("C")).rootSnapshot().path("overview").path("deviceDetails"))
                .isEqualTo(content.rootSnapshot().path("overview").path("deviceDetails"));
        ((ObjectNode) body.path("overview").path("deviceDetails").get(0)).put("deviceId", 51);
        assertThatThrownBy(() -> codec.decodeWritable(body, source("C"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scope");
    }

    private ObjectNode standardBody() {
        var body = (ObjectNode) codec.createInitialOnlineDraft("ONLINE_TEMPLATE_STANDARD", source("C")).rootSnapshot().deepCopy();
        body.putArray("supportArrangements");
        body.set("steps", JsonUtils.parseTree("""
                [{"sectionCode":"PRE_OPERATION","stepNo":1,"content":"备份现网配置",
                "details":{"stepName":"","testResult":"备份验证通过","businessImpactTime":"",
                "operator":"现场工程师","operationTime":""}}]
                """));
        return body;
    }

    private static CutoverPlanSourcePort.SourceFacts source(String grade) {
        var sections = ("D".equals(grade) ? CutoverPlanRules.SIMPLE_SECTIONS : CutoverPlanRules.STANDARD_SECTIONS)
                .stream().map(code -> new CutoverPlanSourcePort.TemplateSectionSnapshot(
                        code, code, CutoverPlanRules.STANDARD_SECTIONS.indexOf(code) + 1,
                        List.of("NETWORK"), List.of(grade), true)).toList();
        var snapshot = new CutoverPlanSourcePort.SourceSnapshot(1, 10L, 2L, 20L, 1L, grade,
                "D".equals(grade) ? null : 30L, "D".equals(grade) ? null : 1L,
                40L, 3, 4L, List.of(new CutoverPlanSourcePort.DeviceSnapshot(
                50L, "Sn-001", 6L, "ROUTER", "ast-v1")), 60L, "DEFAULT", 1, sections, List.of());
        return new CutoverPlanSourcePort.SourceFacts(snapshot, List.of());
    }
}
