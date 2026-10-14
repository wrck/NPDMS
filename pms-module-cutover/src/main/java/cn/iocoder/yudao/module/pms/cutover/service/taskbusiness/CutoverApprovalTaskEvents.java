package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CutoverApprovalTaskEvents {
    private final PlatformBusinessEventApi outbox;

    @Transactional(propagation = Propagation.MANDATORY)
    public void approved(CutoverTaskDO task) {
        if (!java.util.Objects.equals(task.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || task.getProjectId() == null) throw new IllegalArgumentException("CUTOVER_TASK_EVENT_SCOPE_INVALID");
        String correlation = "cutover-approval:" + java.util.UUID.randomUUID();
        var event = new ProjectRuleReevaluationRequested(correlation, task.getTenantId(), task.getProjectId(),
                SecurityFrameworkUtils.getLoginUserId(), correlation);
        outbox.append("CutoverTask", task.getId().toString(), new BusinessEvent(event.eventId(),
                ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(event)));
    }
}
