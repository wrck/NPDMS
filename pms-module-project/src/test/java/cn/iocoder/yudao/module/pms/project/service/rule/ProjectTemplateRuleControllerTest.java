package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.ProjectTemplateRuleController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_RULE_SIMULATION_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTemplateRuleControllerTest {
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void malformedRuleErrorsDoNotEscapeWithOriginalLiteralsOrCause() throws Exception {
        TenantContextHolder.setTenantId(7L);
        var simulation = mock(ProjectRuleSimulationService.class);
        when(simulation.simulate(7L, List.of(), "bad", Map.of()))
                .thenThrow(new IllegalArgumentException("DMN contained sensitive-trial-value"));
        var controller = new ProjectTemplateRuleController(simulation);
        var failure = assertThrows(ServiceException.class, () -> controller.simulate(
                new ProjectTemplateRuleController.SimulationRequest(List.of(), "bad", Map.of())));
        assertEquals(PROJECT_TEMPLATE_RULE_SIMULATION_INVALID.getCode(), failure.getCode());
        assertFalse(failure.getMessage().contains("sensitive-trial-value"));
        assertNull(failure.getCause());
        var accessLog = ProjectTemplateRuleController.class.getMethod("simulate", ProjectTemplateRuleController.SimulationRequest.class)
                .getAnnotation(ApiAccessLog.class);
        assertFalse(accessLog.requestEnable());
        assertFalse(accessLog.responseEnable());
    }
}
