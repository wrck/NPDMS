package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineeringRuleReevaluationEventsTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RequirementAnalysis", "SiteSurvey"})
    void emitsOnlyScopedWakeupWithoutBusinessBodyOrCompletionClaim(String aggregateType) {
        var outbox = mock(PlatformBusinessEventApi.class);
        TenantContextHolder.setTenantId(7L);
        try {
            new EngineeringRuleReevaluationEvents(outbox, org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)).changed(9L, aggregateType, 11L, 3L, "owner-change");
            var event = ArgumentCaptor.forClass(BusinessEvent.class);
            verify(outbox).append(eq(aggregateType), eq("11"), event.capture());
            var payload = JsonUtils.parseObject(event.getValue().eventPayload(), ProjectRuleReevaluationRequested.class);
            assertEquals(ProjectRuleReevaluationRequested.EVENT_TYPE, event.getValue().eventType());
            assertEquals(event.getValue().eventId(), payload.eventId());
            assertEquals(7L, payload.tenantId()); assertEquals(9L, payload.projectId());
            assertTrue(payload.eventId().length() <= 64);
            assertFalse(event.getValue().eventPayload().contains("completed"));
        } finally { TenantContextHolder.clear(); }
    }
}
