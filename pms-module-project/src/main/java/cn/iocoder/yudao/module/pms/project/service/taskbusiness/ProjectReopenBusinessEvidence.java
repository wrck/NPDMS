package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.projectgovernance.ProjectReopenScopeApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Only an approved template reopen changes evidence eligibility; ordinary projects retain their rules. */
@Component
@RequiredArgsConstructor
public class ProjectReopenBusinessEvidence {
    private final ProjectReopenScopeApi reopens;

    private boolean isProjectObject(TaskBusinessObjectProvider provider) {
        return ("PROJ".equals(provider.ownerContext()) && "PROJECT_ASSIGNMENT".equals(provider.objectType()))
                || ("IMP".equals(provider.ownerContext()) && "INSTALLATION".equals(provider.objectType()));
    }

    public boolean mayAssociate(TaskBusinessObjectProvider provider,
                                TaskBusinessObjectProvider.AssociationContext context, String objectId) {
        var round = reopens.findTemplateReopen(new ProjectReopenScopeApi.Query(context.tenantId(), context.projectId()));
        if (round.isEmpty() || isProjectObject(provider)) return true;
        var createdAt = provider.handlingCreatedAt(context, objectId);
        return createdAt != null && createdAt.isAfter(round.get().openedAt());
    }

    public boolean mayReuseProjectObject(TaskBusinessObjectProvider provider,
                                TaskBusinessObjectProvider.AssociationContext context) {
        return isProjectObject(provider) && reopens.findTemplateReopen(
                new ProjectReopenScopeApi.Query(context.tenantId(), context.projectId())).isPresent();
    }

    public boolean mayComplete(TaskBusinessObjectProvider provider,
                               TaskBusinessObjectProvider.AssociationContext context,
                               ProjectTaskBusinessLinkDO link) {
        var round = reopens.findTemplateReopen(new ProjectReopenScopeApi.Query(context.tenantId(), context.projectId()));
        if (round.isEmpty()) return true;
        if (isProjectObject(provider)) return true;
        var createdAt = provider.handlingCreatedAt(context, link.getObjectId());
        return createdAt != null && createdAt.isAfter(round.get().openedAt());
    }
}
