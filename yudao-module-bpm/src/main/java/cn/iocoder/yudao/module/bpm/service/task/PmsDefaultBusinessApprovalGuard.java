package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.Map;

/** Only new default-business instances reserve these fields; native task authorization remains unchanged. */
@Aspect @Component @RequiredArgsConstructor
public class PmsDefaultBusinessApprovalGuard {
    private final TaskService tasks;
    private final RuntimeService runtime;

    @Before("execution(* cn.iocoder.yudao.module.bpm.service.task.BpmTaskServiceImpl.approveTask(..)) && args(userId,request)")
    public void approving(Long userId,BpmTaskApproveReqVO request) {
        if(request.getVariables()==null || request.getVariables().keySet().stream().noneMatch(this::reserved))return;
        var task=tasks.createTaskQuery().taskTenantId(FlowableUtils.getTenantId()).taskId(request.getId()).singleResult();
        if(task!=null)requireUnchanged(task.getProcessInstanceId(),request.getVariables().keySet());
    }
    @Before("execution(* cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl.updateProcessInstanceVariables(..)) && args(id,variables)")
    public void updating(String id,Map<String,Object> variables){if(variables!=null)requireUnchanged(id,variables.keySet());}
    @Before("execution(* cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl.removeProcessInstanceVariables(..)) && args(id,names)")
    public void removing(String id,Collection<String> names){if(names!=null)requireUnchanged(id,names);}
    private void requireUnchanged(String id,Collection<String> names) {
        if(names.stream().noneMatch(this::reserved))return;
        var instance=runtime.createProcessInstanceQuery().processInstanceTenantId(FlowableUtils.getTenantId()).processInstanceId(id).singleResult();
        if(instance!=null && instance.getBusinessKey()!=null && instance.getBusinessKey().startsWith(PmsDefaultBusinessApprovalProcess.BUSINESS_KEY_PREFIX))
            throw new IllegalArgumentException("审批关联和提交时的业务快照不可修改");
    }
    private boolean reserved(String name){return name!=null && name.startsWith(PmsDefaultBusinessApprovalProcess.VARIABLE_PREFIX);}
}
