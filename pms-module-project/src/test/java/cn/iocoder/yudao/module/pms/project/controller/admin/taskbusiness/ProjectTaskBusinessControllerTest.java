package cn.iocoder.yudao.module.pms.project.controller.admin.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectTaskBusinessControllerTest {
    @AfterEach void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void contextSerializesOwnerActionsViewAndAggregateVersionWithoutDroppingThem() {
        var service = mock(ProjectTaskBusinessService.class);
        var controller = new ProjectTaskBusinessController(service,
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));
        var user = new LoginUser(); user.setId(9L); user.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        var view = new BusinessViewRevision(40L, "SITE_SURVEY", "survey", 1L, "SOL",
                BusinessViewComponentProvider.ViewSource.PAGE, "survey-list", "1", null,
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), "query", "command", "permission",
                null, null, 1, "PUBLISHED", Set.of());
        var context = new TaskBusinessContext(10L, 20L, 30L, 2, "SOL", "SITE_SURVEY", "survey-list", 40L,
                "REFERENCE_EXISTING", List.of(), Set.of("LINK"), null, "a".repeat(64), Set.of("QUERY", "CREATE"), view, true);
        when(service.getContext(eq(10L), eq(0L), eq(9L), anyString())).thenReturn(context);

        var response = JsonUtils.parseTree(JsonUtils.toJsonString(controller.context(10L)));

        assertEquals("a".repeat(64), response.path("data").path("factVersion").asText());
        assertEquals(40L, response.path("data").path("businessView").path("id").asLong());
        assertEquals(Set.of("QUERY", "CREATE"), controller.context(10L).getData().ownerActions());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test void relationshipCommandsPassTaskAndContractVersionsIndependently() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(9L); user.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        var service = mock(ProjectTaskBusinessService.class);
        var controller = new ProjectTaskBusinessController(service, new MockEnvironment());
        controller.link(10L, "\"2\"", "key", new ProjectTaskBusinessController.LinkRequest("survey-1", 3, 2));
        var command = ArgumentCaptor.forClass(ProjectTaskBusinessService.LinkCommand.class);
        verify(service).link(command.capture(), eq(1L), eq(9L), anyString());
        assertEquals(3, command.getValue().expectedTaskVersion());
        assertEquals(2, command.getValue().expectedContractVersion());
        assertThrows(RuntimeException.class, () -> controller.link(10L, "1", "key-2",
                new ProjectTaskBusinessController.LinkRequest("survey-1", 3, 2)));
        verifyNoMoreInteractions(service);
    }
}
