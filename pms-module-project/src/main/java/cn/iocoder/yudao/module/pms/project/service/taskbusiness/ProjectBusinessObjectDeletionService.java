package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.ProjectBusinessObjectDeletionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.DeletedBusinessObjectLinksQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.TaskBusinessUnlinkUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class ProjectBusinessObjectDeletionService implements ProjectBusinessObjectDeletionApi {
    private final ProjectMasterMapper projects;
    private final ProjectScopeApi scopes;
    private final ProjectTaskBusinessLinkMapper links;
    private final OperationAuditApi audit;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void unlinkDeletedObject(DeletedObject object) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var user = SecurityFrameworkUtils.getLoginUser();
        Long actor = user == null ? null : user.getId();
        if (actor == null || !Objects.equals(tenant, user.getTenantId())
                || object == null || object.projectId() == null || object.projectId() <= 0
                || object.ownerContext() == null || object.ownerContext().isBlank()
                || object.objectType() == null || object.objectType().isBlank()
                || object.objectId() == null || object.objectId().isBlank()) throw exception(FORBIDDEN);
        // The Owner already holds the project-first write lock; retain that serialization
        // against automatic association and plan changes until the deletion commits.
        var project = projects.selectByIdForUpdate(object.projectId());
        if (project == null || !Objects.equals(tenant, project.getTenantId())) throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(tenant).subjectUserId(actor)
                .anchorProjectId(object.projectId()).actionCode(ProjectScopeApi.ACTION_MANAGE).build());
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(object.projectId()))
            throw exception(FORBIDDEN);
        var current = links.selectDeletedObjectLiveLinksForUpdate(new DeletedBusinessObjectLinksQuery(tenant,
                object.projectId(), object.ownerContext(), object.objectType(), object.objectId()));
        var now = LocalDateTime.now();
        for (var link : current) {
            if (!Objects.equals(link.getTenantId(), tenant) || !Objects.equals(link.getProjectId(), object.projectId())
                    || !Objects.equals(link.getOwnerContext(), object.ownerContext())
                    || !Objects.equals(link.getObjectType(), object.objectType())
                    || !Objects.equals(link.getObjectId(), object.objectId())) throw exception(FORBIDDEN);
            if (links.unlinkIfMatch(new TaskBusinessUnlinkUpdate(tenant, object.projectId(), link.getTaskId(),
                    link.getId(), link.getVersion(), actor, now, link.getStageId())) != 1)
                throw new IllegalStateException("DELETED_OBJECT_REFERENCE_CONFLICT");
        }
        if (!current.isEmpty()) audit.record(tenant, actor, UUID.randomUUID().toString(),
                "PROJECT_DELETED_BUSINESS_OBJECT_UNLINK", object.objectType(), object.objectId(), "SUCCESS",
                Map.of("projectId", object.projectId(), "ownerContext", object.ownerContext(),
                        "closedLinks", current.stream().map(link -> link.getId()).toList()));
    }
}
