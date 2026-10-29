package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverCreationProjectResolveControllerTest {
    final CutoverTaskCreationQueryService queries = mock(CutoverTaskCreationQueryService.class);
    final CutoverTaskContext actor = new CutoverTaskContext(1L, 9L, "project-create", true, true, false, false, false);
    final CutoverTaskRequestCodec codec = new CutoverTaskRequestCodec(JsonUtils.getObjectMapper());
    final CutoverTaskController controller = new CutoverTaskController(mock(CutoverTaskApplicationService.class),
            mock(CutoverTaskQueryService.class), () -> actor, codec, mock(CutoverTaskListService.class),
            mock(cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService.class),
            cn.iocoder.yudao.module.pms.cutover.support.CutoverTestContexts::dashboard);

    @Test void projectBoundCreationAcceptsItsProjectWithoutRelaxingTheLegacyActionCodec() {
        ReflectionTestUtils.setField(controller, "creationQueries", queries);
        when(queries.resolve(any())).thenReturn(new CutoverTaskCreationQueryService.ResolvedDevices(
                new CutoverTaskViews.CreateContextData(List.of(), false, List.of(), true), List.of()));
        var body = JsonUtils.parseTree("""
                {"projectId":"101","serialNumbers":["SN-401"]}
                """);

        assertNotNull(assertDoesNotThrow(() -> controller.resolveCreationDevices(body)).getData());
        verify(queries).resolve(argThat(query -> query.context().equals(actor) && query.projectId().equals(101L)
                && query.serialNumbers().equals(List.of("SN-401"))));
        assertThrows(IllegalArgumentException.class, () -> codec.resolveCreateContext(body));
    }

    @Test void unboundCreationKeepsAnAbsentProjectAndRejectsClientAuthorizationFields() {
        ReflectionTestUtils.setField(controller, "creationQueries", queries);
        when(queries.resolve(any())).thenReturn(new CutoverTaskCreationQueryService.ResolvedDevices(
                new CutoverTaskViews.CreateContextData(List.of(), false, List.of(), true), List.of()));

        controller.resolveCreationDevices(JsonUtils.parseTree("{\"serialNumbers\":[\"SN-401\"]}"));

        verify(queries).resolve(argThat(query -> query.projectId() == null && query.context().equals(actor)));
        assertThrows(IllegalArgumentException.class, () -> controller.resolveCreationDevices(JsonUtils.parseTree(
                "{\"serialNumbers\":[\"SN-401\"],\"projectId\":101,\"actorId\":999}")));
    }
}
