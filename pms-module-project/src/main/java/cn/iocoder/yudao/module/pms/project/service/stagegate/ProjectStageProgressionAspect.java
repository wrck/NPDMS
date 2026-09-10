package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.*;
import cn.iocoder.yudao.module.pms.project.service.projectmember.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class ProjectStageProgressionAspect {
    private final ProjectStageProgressionTrigger trigger;
    private final ProjectTaskInstanceMapper tasks;

    @AfterReturning(pointcut = "execution(* cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService.create(..))", returning = "result")
    public void created(ManualProjectCreateResult result) {
        trigger.afterChange(result.id());
    }

    @AfterReturning("execution(* cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManagerAssignmentApplicationService.assign(..))")
    public void serviceManagerChanged(JoinPoint call) {
        trigger.afterChange(((AssignServiceManagerCommand) call.getArgs()[0]).projectId());
    }

    @AfterReturning("execution(* cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectManagerMemberApplicationService.update(..))")
    public void managersChanged(JoinPoint call) {
        trigger.afterChange(((ProjectManagerMemberCommand) call.getArgs()[0]).projectId());
    }

    @AfterReturning("execution(* cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectMemberUpdateApplicationService.update(..))")
    public void membersChanged(JoinPoint call) {
        trigger.afterChange(((ProjectMemberUpdateCommand) call.getArgs()[0]).projectId());
    }

    @AfterReturning("execution(* cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationServiceImpl.updateProject(..))")
    public void projectChanged(JoinPoint call) {
        trigger.afterChange(((ProjectMasterDO) call.getArgs()[0]).getId());
    }

    @AfterReturning("execution(* cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService.act(..))")
    public void taskChanged(JoinPoint call) {
        var command = (TaskActionCommand) call.getArgs()[0];
        var task = tasks.selectById(command.taskId());
        if (task != null) trigger.afterChange(task.getProjectId());
    }
}
