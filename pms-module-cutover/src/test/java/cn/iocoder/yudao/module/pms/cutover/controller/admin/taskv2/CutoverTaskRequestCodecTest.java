package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverTaskRequestCodecTest {

    private final CutoverTaskRequestCodec codec = new CutoverTaskRequestCodec(JsonUtils.getObjectMapper());

    @Test
    void decodesExactResolveRequest() {
        var request = codec.resolveCreateContext(JsonUtils.parseTree("""
                {"serialNumbers":["SN-001","SN-002"]}
                """));

        assertThat(request.serialNumbers()).containsExactly("SN-001", "SN-002");
    }

    @Test
    void rejectsMissingAndExtraKeysBeforeRecordBinding() {
        assertThatThrownBy(() -> codec.resolveCreateContext(JsonUtils.parseTree("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.resolveCreateContext(JsonUtils.parseTree("""
                {"serialNumbers":["SN-001"],"projectId":31}
                """))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.saveAssessment(JsonUtils.parseTree("""
                {"answers":{"businessImportanceLevel":"HIGH","operationComplexityLevel":"HIGH",
                "hiddenRiskLevel":"HIGH"},"manualGrade":"A"}
                """))).isInstanceOf(IllegalArgumentException.class);
    }
}
