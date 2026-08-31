package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

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
    void decodesExactListQueryWithLockedEnums() {
        var query = new LinkedMultiValueMap<String, String>();
        query.add("projectId", "31");
        query.add("taskStatus", "GRADE_CONFIRMING");
        query.add("currentStage", "P2");
        query.add("pageNo", "2");
        query.add("pageSize", "50");

        assertThat(codec.listQuery(query)).isEqualTo(
                new CutoverTaskRequestCodec.ListQuery(31L, "GRADE_CONFIRMING", "P2", 2, 50));
    }
}
