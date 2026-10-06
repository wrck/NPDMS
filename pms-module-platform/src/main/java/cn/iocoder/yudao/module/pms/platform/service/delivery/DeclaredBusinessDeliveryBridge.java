package cn.iocoder.yudao.module.pms.platform.service.delivery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.util.*;
/** Existing delivery contracts connected to explicit project ownership and safe declared operations. */
@org.springframework.stereotype.Component @RequiredArgsConstructor
public class DeclaredBusinessDeliveryBridge {
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessOperationDispatcher operations;
    private final BusinessEntityAccessPort entities;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi contexts;
    public boolean supports(String owner,String type) {
        var declaration=persistence.find(owner,type).orElse(null);
        if(declaration==null || declaration.descriptor().scopeBinding()==null) return false;
        return true; // Explicitly scoped declarations never fall through to legacy projectId guesses.
    }
    public Long projectId(Long tenant,String owner,String type,Long id) {
        var declaration=persistence.require(owner,type);var binding=declaration.descriptor().scopeBinding();
        if(binding==null || !"project".equals(binding.policyRef())) throw DeliveryOwnerAccess.denied();
        var row=persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(id);
        if(row==null || !tenant.equals(row.getTenantId())) throw DeliveryOwnerAccess.denied();
        var values=BusinessModelIntrospector.readValues(row,BusinessModelIntrospector.businessFields(declaration.entityClass()));
        try {long project=new BigDecimal(Objects.requireNonNull(values.get(binding.ownershipFieldCode())).toString()).longValueExact();if(project<=0)throw new IllegalArgumentException();return project;}
        catch(RuntimeException invalid){throw DeliveryOwnerAccess.denied();}
    }
    public Long require(Long tenant,Long actor,String owner,String type,Long id,boolean write,boolean lock,Long expectedScope) {
        var declaration=persistence.require(owner,type);var model=declaration.descriptor();
        if(!model.capabilities().stream().anyMatch(cap->cap.type()==BusinessCapabilityType.DELIVERY && cap.enabled())) throw DeliveryOwnerAccess.denied();
        var service=operations.capabilityService(owner,type);
        var target=EntityDataRef.current(new EntityRef(tenant,owner,type,id));var user=new EntityActor(tenant,actor,"DELIVERY_ACCESS");
        if(write) {
            service.requireCapabilityWrite(target,user);
            if(lock) {
                var row=persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(id);
                service.lockCapabilityTarget(target,user,row.getVersion());
            }
        } else if(!entities.read(target,user,"delivery").available()) throw DeliveryOwnerAccess.denied();
        Long project=projectId(tenant,owner,type,id);String action=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;
        var observed=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,project,action));
        if(observed==null || observed.treeVersion()==null || observed.fullProjectIds()==null || !observed.fullProjectIds().contains(project)
            || expectedScope!=null && !expectedScope.equals(observed.treeVersion())) throw DeliveryOwnerAccess.denied();
        if(lock) {
            var current=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,project,action,observed.treeVersion()));
            if(current==null || !Objects.equals(observed.rootProjectId(),current.rootProjectId()) || !Objects.equals(observed.treeVersion(),current.treeVersion())
                || current.fullProjectIds()==null || !current.fullProjectIds().contains(project)) throw DeliveryOwnerAccess.denied();
        }
        if(write) {
            var query=new ProjectAcceptanceContextApi.Query(tenant,project,actor);var initial=contexts.inspect(query);
            if(initial==null || !Objects.equals(project,initial.projectId())) throw DeliveryOwnerAccess.denied();
            var current=lock?contexts.lock(query,initial.projectVersion(),observed.treeVersion()):initial;
            if(current==null || !Objects.equals(project,current.projectId()) || !Objects.equals(observed.treeVersion(),current.treeVersion()) || !"ACTIVE".equals(current.lifecycleStatus())) throw DeliveryOwnerAccess.denied();
        }
        return observed.treeVersion();
    }
}
