package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.DurationChangePatchReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.PatchDurationChangeCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DurationChangeControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jsonNullIsTrackedAsSubmittedWhileMissingFieldsStayAbsent() {
        DurationChangePatchReqVO request = JsonUtils.parseObject("""
                {"expectedProjectVersion":3,"reasonDetail":null}
                """, DurationChangePatchReqVO.class);

        assertEquals(Set.of("reasonDetail"), request.getSubmittedFields());
        assertNull(request.getReasonDetail());
    }

    @Test
    void patchPassesOnlyFieldsActuallyPresentInRequest() {
        login(9L);
        DurationChangeApplicationService changeService = mock(DurationChangeApplicationService.class);
        ConstructionPlanController controller = new ConstructionPlanController(
                mock(ConstructionPlanApplicationService.class), changeService,
                mock(ConstructionPlanQueryService.class),
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));
        DurationChangePatchReqVO request = JsonUtils.parseObject("""
                {"expectedProjectVersion":3,"durationDays":7}
                """, DurationChangePatchReqVO.class);

        controller.patchChange(501L, 801L, "0", request);

        ArgumentCaptor<PatchDurationChangeCommand> command =
                ArgumentCaptor.forClass(PatchDurationChangeCommand.class);
        verify(changeService).patchDraft(command.capture(), any());
        assertEquals(0, command.getValue().expectedChangeVersion());
        assertEquals(Set.of("durationDays"), command.getValue().patch().submittedFields());
        assertEquals(7, command.getValue().patch().durationDays());
    }

    private void login(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }
}
