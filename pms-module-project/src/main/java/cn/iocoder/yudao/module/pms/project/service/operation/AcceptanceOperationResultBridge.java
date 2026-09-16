package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Synchronous native-event adaptation inside the original transaction, not an after-commit dual write. */
@Component
@RequiredArgsConstructor
public class AcceptanceOperationResultBridge {
    private final ObjectProvider<AcceptanceActivityMapper> activities;
    private final ObjectProvider<PlatformBusinessEventApi> outbox;
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAppended(PlatformOutboxAppended appended) {
        var message = appended.message();
        if (!"AcceptanceReportVersionChanged".equals(message.eventType())) return;
        var nativeEvent = JsonUtils.parseObject(message.payload(),AcceptanceReportVersionChangedMessage.class);
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (nativeEvent == null || !Objects.equals(tenant,message.tenantId()) || !Objects.equals(tenant,nativeEvent.tenantId())
                || !Objects.equals(message.eventId(),nativeEvent.eventId())) throw new IllegalArgumentException("ACCEPTANCE_RESULT_IDENTITY_INVALID");
        var frame = ProjectVerifiedOperationScope.current();
        if (frame != null && Objects.equals(frame.tenantId(),tenant) && "ACC".equals(frame.ownerContext())
                && "ACCEPTANCE".equals(frame.objectType()) && Objects.equals(frame.projectId(),nativeEvent.projectId())
                && Objects.equals(frame.objectId(),String.valueOf(nativeEvent.acceptanceId()))) return;
        var activity = activities.getObject().selectById(nativeEvent.acceptanceId());
        if (activity == null || !Objects.equals(activity.getTenantId(),tenant)
                || !Objects.equals(activity.getProjectId(),nativeEvent.projectId()) || activity.getVersion() == null)
            throw new IllegalArgumentException("ACCEPTANCE_RESULT_IDENTITY_INVALID");
        boolean revoked = "REVOKED".equals(nativeEvent.changeType());
        Long revision = revoked ? nativeEvent.previousReportVersionId() : nativeEvent.currentReportVersionId();
        var result = new ProjectOperationResult("ACC","ACCEPTANCE",activity.getId().toString(),
                revision == null ? null : revision.toString(),activity.getVersion(),
                "ACC:ACCEPTANCE:" + activity.getId() + ":" + activity.getVersion(),
                revoked ? "REPORT_VERSION_REVOKED" : "REPORT_VERSION_PUBLISHED",null,false);
        var event = BusinessOperationResultEvent.create(tenant,activity.getProjectId(),"OWNER.ACCEPTANCE_REPORT.CHANGED",
                message.eventId(),result,nativeEvent.publisherActorUserId(),message.eventId());
        outbox.getObject().append("ACCEPTANCE",result.objectId(),new BusinessEvent(event.eventId(),
                BusinessOperationResultEvent.EVENT_TYPE,JsonUtils.toJsonString(event)));
    }
}
