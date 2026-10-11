package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable wakeup in the contact command transaction; the PROJ state machine reads current facts after commit. */
@Service @RequiredArgsConstructor
public class ProjectContactRuleWakeup {
    private final PlatformBusinessEventApi outbox;
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(Long tenantId, Long projectId, Long actorId) {
        var event = ProjectRuleReevaluationRequested.create(tenantId, projectId, actorId, "project-contact:" + projectId);
        outbox.append("Project", projectId.toString(), new BusinessEvent(event.eventId(), event.EVENT_TYPE, JsonUtils.toJsonString(event)));
    }
}
