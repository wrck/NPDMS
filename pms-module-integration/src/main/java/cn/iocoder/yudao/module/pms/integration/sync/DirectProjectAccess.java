package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import java.util.Objects;

/** Retains current project manage scope through the public scope contract. */
@RequiredArgsConstructor
final class DirectProjectAccess {
    record Actor(Long tenantId,Long actorId) {}
    private final DirectProjectMapper projects;
    private final ProjectScopeApi scope;
    void getProjectForManage(Long projectId,Actor actor) {
        if(actor==null || actor.tenantId()==null || actor.tenantId()<0 || actor.actorId()==null || actor.actorId()<=0)
            throw new IllegalArgumentException("DPPMS_PROJECT_MANAGE_SCOPE_INVALID");
        var row=projects.selectIdentity(new DirectProjectMapper.IdentityQuery(actor.tenantId(),projectId));
        if(row==null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(),actor.tenantId()))
            throw new IllegalArgumentException("DPPMS_PROJECT_TARGET_PROTECTED");
        var current=scope.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(),actor.actorId(),projectId,ProjectScopeApi.ACTION_MANAGE));
        if(current==null || !current.fullProjectIds().contains(projectId))
            throw new IllegalArgumentException("DPPMS_PROJECT_MANAGE_SCOPE_FORBIDDEN");
    }
}
