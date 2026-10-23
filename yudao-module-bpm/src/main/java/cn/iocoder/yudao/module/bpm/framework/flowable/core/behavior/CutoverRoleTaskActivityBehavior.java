package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.interceptor.CreateUserTaskBeforeContext;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;

/** 原生candidateGroups任务保持未领取；CUT业务校验后在同一事务领取并决定。 */
public class CutoverRoleTaskActivityBehavior extends UserTaskActivityBehavior {
    public CutoverRoleTaskActivityBehavior(UserTask userTask) { super(userTask); }

    @Override
    protected void handleCategory(CreateUserTaskBeforeContext beforeContext, ExpressionManager expressionManager,
                                  TaskEntity task, DelegateExecution execution) {
        ProcessDefinitionEntity definition = CommandContextUtil.getProcessDefinitionEntityManager()
                .findById(execution.getProcessDefinitionId());
        if (definition != null) task.setCategory(definition.getCategory());
    }
}
