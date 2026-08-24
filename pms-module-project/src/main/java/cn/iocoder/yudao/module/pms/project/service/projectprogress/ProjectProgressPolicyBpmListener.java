package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectProgressPolicyBpmListener extends BpmProcessInstanceStatusEventListener {
    private final ProjectProgressPolicyService policyService;
    private final ProjectProgressProperties properties;

    @Override
    protected String getProcessDefinitionKey() {
        return properties.getProcessDefinitionKey();
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        policyService.onApprovalResult(event.getId(), event.getStatus(), event.getReason());
    }
}
