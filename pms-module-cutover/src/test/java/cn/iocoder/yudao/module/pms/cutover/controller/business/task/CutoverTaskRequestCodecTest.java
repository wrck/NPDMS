package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

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
    void acceptsCreationWithoutAClientTaskName() {
        var request = codec.create(JsonUtils.parseTree("""
                {"projectId":100,"configurationCode":"CFG","serialNumbers":["SN-1"],
                 "background":"背景","cutoverType":"TYPE","networkMode":null,"scheduledTime":null,
                 "expectedProjectContext":{"projectId":100,"projectVersion":1,"projectCode":"P100",
                   "projectName":"项目","customerId":null,"customerCode":null,"customerName":null,
                   "departmentId":null,"departmentCode":null,"departmentName":null},
                 "expectedProjectScopeVersion":1,
                 "expectedDeviceScopeWatermark":[{"deviceId":1,"serialNumber":"SN-1","projectAssignmentVersion":1}]}
                """));
        assertThat(request.taskName()).isNull();
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
                new cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery(cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery.ListKind.ALL, 31L, "GRADE_CONFIRMING", "P2", 2, 50));
    }
}
