package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.model.ProjectBusinessModelContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import java.util.*;

/** Current project extensions reuse project authorization and version locking; no revision workflow is created. */
@Component @RequiredArgsConstructor
public class ProjectEntityFieldProvider implements EntityFieldProvider {
    private final ProjectManualCreationService access;
    private final ProjectMasterMapper projects;
    private final ProjectBusinessModelContributor model;
    @Override public String ownerModule(){return "PRJ";}
    @Override public String entityType(){return "project";}
    @Override public boolean usesValidatedExtensionPatch(){return true;}
    @Override public List<EntityField> fields() {
        return model.declarations().stream().filter(d->entityType().equals(d.descriptor().entityType())).findFirst().orElseThrow()
                .descriptor().fields().stream().map(f->new EntityField(f.code(),f.type(),f.required())).toList();
    }
    @Override public Map<String,EntityFieldValue> read(EntityDataRef target,EntityActor actor) {
        var row=authorized(target,actor,false);var bean=new BeanWrapperImpl(row);var result=new LinkedHashMap<String,EntityFieldValue>();
        for(var field:fields())result.put(field.code(),EntityFieldValue.known(bean.getPropertyValue(field.code())));
        return Collections.unmodifiableMap(result);
    }
    @Override public void requireReadable(EntityDataRef target,EntityActor actor){authorized(target,actor,false);}
    @Override public Long concurrencyBasis(EntityDataRef target,EntityActor actor){return authorized(target,actor,false).getVersion();}
    @Override public void lockForWrite(EntityDataRef target,EntityActor actor,Long expectedVersion) {
        authorized(target,actor,true);var row=projects.selectByIdForUpdate(target.entity().entityId());
        if(row==null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(),actor.tenantId())
                || !Objects.equals(row.getVersion(),expectedVersion))throw new IllegalArgumentException("PROJECT_EXTENSION_VERSION_CHANGED");
    }
    private ProjectMasterDO authorized(EntityDataRef target,EntityActor actor,boolean write) {
        actor.requireTenant(target.entity());
        if(!Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()) || target.isRevision()
                || !ownerModule().equals(target.entity().ownerModule()) || !entityType().equals(target.entity().entityType())
                || actor.isSystemObserver())throw new IllegalArgumentException("PROJECT_EXTENSION_IDENTITY_INVALID");
        var subject=new ProjectManualCreationService.ProjectAccessActor(actor.tenantId(),actor.userId());
        return write?access.getProjectForManage(target.entity().entityId(),subject):access.getProject(target.entity().entityId(),subject);
    }
}
