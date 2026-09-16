package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectOperationResultOutbox implements ProjectOperationResultSink {
    private final PlatformBusinessEventApi outbox;
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String code, int version, ProjectOperationCommand command, ProjectOperationResult result,
            Long tenant, Long actor, String correlation) {
        if (version != 1 || !Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("BUSINESS_RESULT_TENANT_INVALID");
        var event = BusinessOperationResultEvent.create(tenant,command.projectId(),code,command.idempotencyKey(),result,actor,correlation);
        outbox.append(result.objectType(),result.objectId(),new BusinessEvent(event.eventId(),BusinessOperationResultEvent.EVENT_TYPE,JsonUtils.toJsonString(event)));
    }
}
