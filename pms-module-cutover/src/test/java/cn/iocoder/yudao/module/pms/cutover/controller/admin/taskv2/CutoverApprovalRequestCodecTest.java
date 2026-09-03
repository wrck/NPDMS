package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverApprovalRequestCodecTest {
    private final CutoverApprovalRequestCodec codec = new CutoverApprovalRequestCodec();

    @Test
    void parsesApproveRejectReassignAndWireLong() {
        var approve = codec.approve(json(decision("APPROVE", "YES", null)));
        assertThat(approve.reviewItems()).extracting("itemCode").containsExactly(
                "PREPARATION", "BUSINESS_TEST", "EXECUTION", "ROLLBACK", "OTHER");
        assertThat(approve.reviewItems()).extracting("decision").containsOnly("YES");

        var reject = codec.reject(json(decision("REJECT", "NO", "需补充回退演练")));
        assertThat(reject.reviewItems()).extracting("unreasonableReason").containsOnly("需补充回退演练");

        var reassign = codec.reassign(json("""
                {"nodeNo":2,"newApproverUserId":"9007199254740992","reason":"当前审批人请假"}
                """));
        assertThat(reassign.newApproverUserId()).isEqualTo(9_007_199_254_740_992L);
        assertThat(codec.version("0", "If-Match")).isZero();
    }

    @Test
    void rejectsMissingExtraAndWrongDecisionShape() {
        assertThatThrownBy(() -> codec.reassign(json("""
                {"nodeNo":2,"newApproverUserId":9}
                """))).isInstanceOf(CutoverApprovalRequestException.class);
        assertThatThrownBy(() -> codec.reassign(json("""
                {"nodeNo":2,"newApproverUserId":9,"reason":"改派","extra":true}
                """))).isInstanceOf(CutoverApprovalRequestException.class);
        assertThatThrownBy(() -> codec.approve(json(decision("APPROVE", "NO", "不通过"))))
                .isInstanceOf(CutoverApprovalRequestException.class);
        assertThatThrownBy(() -> codec.version(" 1", "If-Match"))
                .isInstanceOf(CutoverApprovalRequestException.class);
    }

    private static String decision(String action, String decision, String reason) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return """
                {"action":"%s","reviewItems":[
                  {"itemCode":"PREPARATION","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"BUSINESS_TEST","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"EXECUTION","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"ROLLBACK","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"OTHER","decision":"%s","unreasonableReason":%s}
                ],"assessmentReview":null,"feedback":"已完成本节点评审"}
                """.formatted(action, decision, reasonJson, decision, reasonJson, decision, reasonJson,
                decision, reasonJson, decision, reasonJson);
    }

    private static JsonNode json(String value) {
        return JsonUtils.parseObject(value, JsonNode.class);
    }
}
