package cn.iocoder.yudao.module.pms.cutover.controller.business.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverPlanContentRequestTest {
    private final CutoverPlanRequestCodec codec = new CutoverPlanRequestCodec();

    @Test
    void acceptsStandardBrowserDraftWithRollbackDescriptionAndContactReference() {
        var body = standardDraft();
        body.put("rollbackDescription", "平台验收样例：保留失败记录并停止推进");
        body.withArray("supportArrangements").addObject().putObject("customerContactReference").put("contactId", "2109327965311381510")
                .put("contactVersion", 0);
        var original = body.deepCopy();

        assertThat(codec.draftContent(body)).isEqualTo(original).isNotSameAs(body);
        assertThat(body).isEqualTo(original);
    }

    @Test
    void stillRejectsUnknownStandardFields() {
        var body = standardDraft();
        body.put("rollbackDescription", "说明");
        body.put("ownerUserId", 1);
        assertThatThrownBy(() -> codec.draftContent(body)).isInstanceOf(CutoverPlanRequestException.class);
    }

    @Test
    void doesNotAdmitStandardFieldsIntoTheSimpleRoute() {
        var body = JsonUtils.getObjectMapper().createObjectNode();
        body.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D");
        body.putArray("steps");
        body.put("rollbackDescription", "说明");
        assertThatThrownBy(() -> codec.draftContent(body)).isInstanceOf(CutoverPlanRequestException.class);
    }

    private ObjectNode standardDraft() {
        var body = JsonUtils.getObjectMapper().createObjectNode();
        body.put("editMode", "ONLINE_TEMPLATE_STANDARD");
        body.putObject("overview");
        body.putArray("steps");
        body.putArray("riskMitigations");
        body.putArray("supportArrangements");
        return body;
    }
}
