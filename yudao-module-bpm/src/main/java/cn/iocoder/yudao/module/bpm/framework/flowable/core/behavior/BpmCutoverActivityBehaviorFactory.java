package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;

/** 仅割接新增角色节点使用原生共享候选任务，其余任务沿用平台行为。 */
public class BpmCutoverActivityBehaviorFactory extends BpmActivityBehaviorFactory {
    @Override
    public UserTaskActivityBehavior createUserTaskActivityBehavior(UserTask userTask) {
        if (userTask.getParentContainer() instanceof org.flowable.bpmn.model.Process process
                && CutoverWorkflowApi.DEFINITION_KEY.equals(process.getId())
                && userTask.getExtensionElements().containsKey("cutoverRoleGroup"))
            return new CutoverRoleTaskActivityBehavior(userTask);
        return super.createUserTaskActivityBehavior(userTask);
    }
}
