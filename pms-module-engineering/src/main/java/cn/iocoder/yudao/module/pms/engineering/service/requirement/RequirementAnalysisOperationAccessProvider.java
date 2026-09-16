package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementProjectQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationAccessProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Does not load a task-backed form merely to decide Owner permission or revision editability. */
@Component
@RequiredArgsConstructor
public class RequirementAnalysisOperationAccessProvider implements ProjectBusinessOperationAccessProvider {
    private final RequirementAnalysisAccess access;
    private final RequirementAnalysisMapper mapper;
    @Override public String ownerContext() { return "SOL"; }
    @Override public String objectType() { return "REQUIREMENT_ANALYSIS"; }

    @Override public Access inspect(Context context) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getRequiredTenantId())
                || context.actorId() == null || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId()))
            throw exception(FORBIDDEN);
        var actor = new EntityActor(context.tenantId(), context.actorId(), null);
        access.requireRead(context.projectId(), actor, false);
        boolean manager = access.isManager(context.projectId(), actor);
        var query = new RequirementProjectQuery(context.tenantId(), context.projectId());
        var draft = manager ? mapper.selectDraft(query) : null;
        Set<String> actions = new LinkedHashSet<>();
        if (manager && draft == null && mapper.selectLatest(query) == null) actions.add("SOL.REQUIREMENT_ANALYSIS.CREATE");
        if (context.objectId() == null) return new Access(actions, null);
        Long id;
        try { id = Long.valueOf(context.objectId()); } catch (NumberFormatException invalid) { throw exception(FORBIDDEN); }
        var revision = access.read(id, actor);
        if (!Objects.equals(revision.getProjectId(), context.projectId()) || revision.getVersion() == null)
            throw exception(FORBIDDEN);
        if (manager) {
            if ("DRAFT".equals(revision.getRevisionState()) && draft != null && id.equals(draft.getId()))
                actions.addAll(Set.of("SOL.REQUIREMENT_ANALYSIS.SAVE", "SOL.REQUIREMENT_ANALYSIS.COMPLETE"));
            if ("FROZEN".equals(revision.getRevisionState()) && draft == null)
                actions.add("SOL.REQUIREMENT_ANALYSIS.COPY");
        }
        return new Access(actions, "SOL:REQUIREMENT_ANALYSIS_REVISION:" + revision.getId() + ":" + revision.getVersion()
                + ":" + revision.getRevisionState());
    }
}
