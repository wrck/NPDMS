package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxBusinessEventPortTest {
    @Test void persistedProjectChangeWritesTheExistingRuntimeWakeupWithoutRunningAnyTransition() {
        var writer=mock(PlatformTransactionalOutboxWriter.class);var port=new OutboxBusinessEventPort(writer);
        port.append(new BusinessEventRecord("change-1",new EntityRef(7L,"IT","note",11L),BusinessEventKind.CHANGED,
                "3","intent-1",Map.of("operation","save"),0L,new BusinessEventRecord.ProjectChange(20L,42L)));
        var events=ArgumentCaptor.forClass(BusinessEvent.class);
        verify(writer,times(2)).write(eq(7L),events.capture(),anyString(),anyString(),any());
        var wake=events.getAllValues().stream().filter(event->ProjectRuleReevaluationRequested.EVENT_TYPE.equals(event.eventType())).findFirst().orElseThrow();
        var fact=JsonUtils.parseObject(wake.eventPayload(),ProjectRuleReevaluationRequested.class);
        assertEquals(20L,fact.projectId());assertEquals(7L,fact.tenantId());assertEquals(42L,fact.actorId());
        assertEquals("change-1",fact.correlationId());assertEquals(wake.eventId(),fact.eventId());
    }
    @Test void nonProjectEventAndUntrustedPayloadDoNotInventAProjectWakeup() {
        var writer=mock(PlatformTransactionalOutboxWriter.class);
        new OutboxBusinessEventPort(writer).append(new BusinessEventRecord("change-2",new EntityRef(7L,"IT","note",11L),BusinessEventKind.CHANGED,
                "3","intent",Map.of("projectId",999L),0L));
        verify(writer).write(eq(7L),any(),eq("note"),eq("11"),any());verifyNoMoreInteractions(writer);
    }
}
