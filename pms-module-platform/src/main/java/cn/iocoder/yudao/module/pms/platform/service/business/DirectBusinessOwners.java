package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.*;

/** Automatic service discovery only for cross-business delivery collection. Direct CRUD never calls this index. */
@Component @RequiredArgsConstructor
public class DirectBusinessOwners {
    private final ObjectProvider<ProjectBusinessService<?>> services;
    private final BusinessCallerContext callers;
    private final ProjectScopeApi projects;
    private final ProjectAcceptanceContextApi lifecycle;
    public record Access(Long projectId,Long scopeVersion) { }
    public Optional<ProjectBusinessService<?>> byCode(String code) {
        return unique(services.orderedStream().filter(service->service.definition().stableCode().equals(code)).toList());
    }
    public Optional<ProjectBusinessService<?>> byIdentity(String owner,String type) {
        return unique(services.orderedStream().filter(service->service.definition().ownerModule().equals(owner)
                && service.definition().entityType().equals(type)).toList());
    }
    private Optional<ProjectBusinessService<?>> unique(List<ProjectBusinessService<?>> matches) {
        if(matches.size()>1) throw denied("Duplicate direct business identity");
        return matches.stream().findFirst();
    }
    public List<BusinessModelDescriptor> definitions() { return services.orderedStream().map(ProjectBusinessService::definition).toList(); }
    public void requireReadableModel(String code) { byCode(code).orElseThrow(()->denied("Unknown direct business")).model(); }
    public Access require(Long tenant,Long user,String owner,String type,Long id,boolean write,boolean lock,Long expectedVersion) {
        var caller=callers.require();
        if(!Objects.equals(tenant,caller.tenantId()) || !Objects.equals(user,caller.userId())) throw denied("Actor differs from the trusted caller");
        var service=byIdentity(owner,type).orElseThrow(()->denied("Unknown direct business"));
        Long project=service.requireDeliveryAccess(id,write,lock);
        String action=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;
        var observed=projects.resolveCurrent(new ProjectCurrentScopeQuery(tenant,user,project,action));
        if(observed==null || observed.treeVersion()==null || observed.fullProjectIds()==null || !observed.fullProjectIds().contains(project)
                || expectedVersion!=null && !expectedVersion.equals(observed.treeVersion())) throw denied("Project scope changed");
        if(lock) {
            var current=projects.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,user,project,action,observed.treeVersion()));
            if(current==null || !Objects.equals(current.rootProjectId(),observed.rootProjectId()) || !Objects.equals(current.treeVersion(),observed.treeVersion())
                    || current.fullProjectIds()==null || !current.fullProjectIds().contains(project)) throw denied("Project scope changed");
        }
        if(write) {
            var query=new ProjectAcceptanceContextApi.Query(tenant,project,user);var initial=lifecycle.inspect(query);
            if(initial==null || !project.equals(initial.projectId())) throw denied("Project lifecycle is unavailable");
            var current=lock?lifecycle.lock(query,initial.projectVersion(),observed.treeVersion()):initial;
            if(current==null || !project.equals(current.projectId()) || !Objects.equals(current.treeVersion(),observed.treeVersion())
                    || !"ACTIVE".equals(current.lifecycleStatus())) throw denied("Project is not writable");
        }
        return new Access(project,observed.treeVersion());
    }
    private BusinessContractException denied(String message) { return new BusinessContractException("ACCESS_DENIED",message); }
}
