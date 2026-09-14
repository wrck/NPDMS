package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class ProjectStageProgressionAspect {
    private final ProjectStageProgressionTrigger trigger;
    private final ProjectTaskInstanceMapper tasks;

    @AfterReturning("execution(* cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationServiceImpl.updateProject(..))")
    public void projectChanged(JoinPoint call) {
        trigger.afterChange(((ProjectMasterDO) call.getArgs()[0]).getId());
    }

    @AfterReturning(pointcut = "execution(* cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService.create(..))"
            + " || execution(* cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService.update(..))"
            + " || execution(* cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService.move(..))", returning = "result")
    public void taskMaintained(cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult result) {
        taskChanged(result.taskId());
    }

    private void taskChanged(Long taskId) {
        try {
            var task = tasks.selectById(taskId);
            if (task != null) trigger.afterChange(task.getProjectId());
        } catch (RuntimeException failure) {
            // 业务命令已成功，附加查询失败也不能导致调用方重复保存。
            log.warn("Stage recheck after task save failed: taskId={}", taskId, failure);
        }
    }
}
