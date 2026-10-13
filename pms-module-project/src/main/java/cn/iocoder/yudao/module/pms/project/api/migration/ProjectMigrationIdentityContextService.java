package cn.iocoder.yudao.module.pms.project.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Reuses the established project authorization and provenance checks without a consumer-specific office dependency. */
@Service @RequiredArgsConstructor
public class ProjectMigrationIdentityContextService implements ProjectMigrationIdentityContextApi {
    private final ProjectManualCreationService access;
    private final ProjectMasterMapper projects;
    private final ProjectLegacyDetailMapper sources;
    private final DppmsProjectBindingApi bindings;
    @Override public Context resolve(Query query) { validate(query);return context(query,projects.selectById(query.projectId())); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Context lockAndRevalidate(Query query) { validate(query);return context(query,projects.selectByIdForUpdate(query.projectId())); }
    private void validate(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.actorId()<=0 || query.projectId()==null || query.projectId()<=0
                || query.expectedProjectVersion()==null || query.sourceProjectKey()==null || query.sourceProjectKey().isBlank())
            throw new IllegalArgumentException("DPPMS_PROJECT_MIGRATION_IDENTITY_CONTEXT_INVALID");
        access.getProjectForManage(query.projectId(),new ProjectAccessActor(query.tenantId(),query.actorId()));
        if(query.sourceConnectionId()==null) {
            var source=sources.selectSource(new ProjectLegacyDetailMapper.SourceIdentityQuery(query.tenantId(),query.sourceProjectKey()));
            if(source==null || !Objects.equals(source.getProjectId(),query.projectId()))
                throw new IllegalArgumentException("DPPMS_PROJECT_MIGRATION_IDENTITY_BINDING_INVALID");
        }
    }
    private Context context(Query query,ProjectMasterDO project) {
        if(project==null || Boolean.TRUE.equals(project.getDeleted()) || !Objects.equals(project.getTenantId(),query.tenantId())
                || !Objects.equals(project.getVersion(),query.expectedProjectVersion()))
            throw new IllegalArgumentException("DPPMS_PROJECT_MIGRATION_IDENTITY_VERSION_CHANGED");
        if(query.sourceConnectionId()!=null) {
            var binding=bindings.resolve(new DppmsProjectBindingApi.Query(query.tenantId(),query.sourceConnectionId(),query.sourceProjectKey()));
            if(binding==null || !Objects.equals(binding.projectId(),project.getId()) || !Objects.equals(binding.projectCode(),project.getProjectCode())
                    || !Objects.equals(binding.projectType(),project.getProjectType()))
                throw new IllegalArgumentException("DPPMS_PROJECT_MIGRATION_IDENTITY_BINDING_INVALID");
        }
        return new Context(project.getId(),project.getVersion(),project.getProjectCode(),project.getProjectType(),project.getCompanyCode(),project.getCompanyName());
    }
}
