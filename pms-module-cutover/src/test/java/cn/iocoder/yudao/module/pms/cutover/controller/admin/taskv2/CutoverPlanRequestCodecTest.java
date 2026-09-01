package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverPlanRequestCodecTest {
    private final CutoverPlanRequestCodec codec = new CutoverPlanRequestCodec();

    @Test
    void parsesWireLongDateTimeAndExactRequestKeys() {
        var upload = codec.createDraft(JsonUtils.parseObject("""
                {"editMode":"FULL_FILE_UPLOAD","fileArtifactFact":{"artifactId":"9007199254740992",
                "versionNo":1,"referenceKey":"ref-1","fileFactVersion":{"artifactVersion":1,
                "referenceVersion":2,"availabilityVersion":3},"scopeVersion":4,
                "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                "ownershipConfirmed":true}
                """, tools.jackson.databind.JsonNode.class));
        assertThat(upload.fileFact().artifactId()).isEqualTo(9_007_199_254_740_992L);

        var patch = codec.patchContact(JsonUtils.parseObject(
                "{\"personName\":\"张三\",\"phone\":\"13800000000\",\"arrivalTime\":1788220800000}",
                tools.jackson.databind.JsonNode.class));
        assertThat(patch.personName()).isEqualTo("张三");
        assertThat(patch.arrivalTime()).isNotNull();
    }

    @Test
    void rejectsMissingExtraAndInvalidHeaderFields() {
        assertThatThrownBy(() -> codec.revise(JsonUtils.parseObject(
                "{\"sourcePlanRevisionId\":1}", tools.jackson.databind.JsonNode.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.createDraft(JsonUtils.parseObject(
                "{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\",\"extra\":1}", tools.jackson.databind.JsonNode.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.version(" 1", "If-Match")).isInstanceOf(IllegalArgumentException.class);
    }
}
