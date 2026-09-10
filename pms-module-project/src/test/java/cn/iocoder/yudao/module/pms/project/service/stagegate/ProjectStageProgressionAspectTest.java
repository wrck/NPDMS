package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProjectStageProgressionAspectTest {
    final ProjectStageProgressionTrigger trigger = mock(ProjectStageProgressionTrigger.class);
    final ProjectTaskInstanceMapper tasks = mock(ProjectTaskInstanceMapper.class);
    final ProjectStageProgressionAspect aspect = new ProjectStageProgressionAspect(trigger, tasks);

    @Test void taskSaveRechecksItsProject() {
        when(tasks.selectById(11L)).thenReturn(new ProjectTaskInstanceDO().setProjectId(9L));
        aspect.taskMaintained(new TaskCommandResult(11L, 2, 3L, "IN_PROGRESS", "NEW"));
        verify(trigger).afterChange(9L);
    }

    @Test void lookupFailureDoesNotTurnSuccessfulSaveIntoFailure() {
        when(tasks.selectById(11L)).thenThrow(new IllegalStateException("connection lost after commit"));
        assertDoesNotThrow(() -> aspect.taskMaintained(new TaskCommandResult(11L, 2, 3L, "IN_PROGRESS", "NEW")));
        verifyNoInteractions(trigger);
    }

    @Test void actualAdvicePreservesTheSuccessfulCommandResponse() {
        var target = mock(ProjectTaskCommandService.class);
        var saved = new TaskCommandResult(11L, 2, 3L, "PENDING_ASSIGN", "NEW");
        when(target.create(null, null)).thenReturn(saved);
        when(tasks.selectById(11L)).thenThrow(new IllegalStateException("lookup failed"));
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        ProjectTaskCommandService proxy = factory.getProxy();
        assertSame(saved, proxy.create(null, null));
        verify(tasks).selectById(11L);
    }

    @Test void failedBusinessCommandDoesNotTriggerProgression() {
        var target = mock(ProjectTaskCommandService.class);
        when(target.create(null, null)).thenThrow(new IllegalArgumentException("save rejected"));
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        ProjectTaskCommandService proxy = factory.getProxy();
        assertThrows(IllegalArgumentException.class, () -> proxy.create(null, null));
        verifyNoInteractions(tasks, trigger);
    }
}
