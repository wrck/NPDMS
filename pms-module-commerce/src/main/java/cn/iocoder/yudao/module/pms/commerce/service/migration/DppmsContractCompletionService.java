package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractCompletionApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class DppmsContractCompletionService implements DppmsContractCompletionApi {
    private final DppmsContractCompletionMapper mapper;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final DeviceOrganizationProjectionApi deviceOrganizations;
    private final ManagedOrganizationApi organizations;

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Result complete(Command command) {
        if(command==null || command.current()==null || !valid(command.current().contractIds())
                || !valid(command.current().shipmentContractIds()) || !valid(command.current().packageIds())
                || !valid(command.shipmentContractIds()) || !valid(command.receivableIds())
                || !command.shipmentContractIds().containsAll(command.current().shipmentContractIds()))
            throw new IllegalArgumentException("DPPMS_CONTRACT_COMPLETION_INVALID");
        authorize(new Actor(command.tenantId(),command.actorId()));
        var current=command.current();
        if(current.contractIds().isEmpty() && current.shipmentContractIds().isEmpty() && current.packageIds().isEmpty()
                && command.shipmentContractIds().isEmpty() && command.receivableIds().isEmpty())return new Result(0);
        var rows=new ArrayList<DppmsContractCompletionMapper.ScopeRow>();
        current.contractIds().forEach(id->rows.add(new DppmsContractCompletionMapper.ScopeRow("CONTRACT",id)));
        current.shipmentContractIds().forEach(id->rows.add(new DppmsContractCompletionMapper.ScopeRow("CURRENT_SHIPMENT",id)));
        current.packageIds().forEach(id->rows.add(new DppmsContractCompletionMapper.ScopeRow("PACKAGE",id)));
        command.shipmentContractIds().forEach(id->rows.add(new DppmsContractCompletionMapper.ScopeRow("SHIPMENT",id)));
        command.receivableIds().forEach(id->rows.add(new DppmsContractCompletionMapper.ScopeRow("RECEIVABLE",id)));
        var query=new DppmsContractCompletionMapper.Query(command.tenantId());
        Throwable failure=null;
        try {
            mapper.createScope();
            for(int offset=0;offset<rows.size();offset+=500)
                mapper.insertScope(new DppmsContractCompletionMapper.ScopeBatch(command.tenantId(),rows.subList(offset,Math.min(offset+500,rows.size()))));
            mapper.selectContractsForUpdate(query);
            if(mapper.countValidInputs(query)!=rows.size())
                throw new IllegalArgumentException("DPPMS_CONTRACT_COMPLETION_INPUT_CHANGED");
            mapper.createCompanies();mapper.createDepartments();
            var organizationRows=current.contractIds().isEmpty() && current.shipmentContractIds().isEmpty()
                    ?List.<DppmsContractCompletionMapper.OrganizationRow>of():organizationRows(command.tenantId());
            for(String kind:List.of("COMPANY","DEPARTMENT")) {
                var selected=organizationRows.stream().filter(row->kind.equals(row.kind())).toList();
                for(int offset=0;offset<selected.size();offset+=500) {
                    var batch=new DppmsContractCompletionMapper.OrganizationBatch(command.tenantId(),selected.subList(offset,Math.min(offset+500,selected.size())));
                    if("COMPANY".equals(kind))mapper.insertCompanies(batch);else mapper.insertDepartments(batch);
                }
            }
            mapper.createUniqueContracts(query);
            var changedContracts=new LinkedHashSet<>(mapper.selectCompanyChangeNumbers(query));
            int companyChanges=0;long shipmentChanges,packageChanges;
            if(command.preview()) {
                shipmentChanges=mapper.countShipmentChanges(query);packageChanges=mapper.countPackageChanges(query);
            } else {
                companyChanges=mapper.updateContractCompanies(query);
                shipmentChanges=mapper.updateShipmentReferences(query);packageChanges=mapper.updatePackageReferences(query);
            }
            // Both complete local inputs are aggregated exactly once after reference completion.
            mapper.createShipmentFields(query);mapper.createReceivableFields(query);
            changedContracts.addAll(mapper.selectFieldChangeNumbers(query));
            int fieldChanges=command.preview()?0:mapper.updateContractFields(query);
            if(!command.preview() && companyChanges+fieldChanges+shipmentChanges+packageChanges>0) {
                var numbers=new LinkedHashSet<String>();
                for(var number:mapper.selectAffectedContractNumbers(query))if(number!=null && !number.isBlank())numbers.add(number);
                if(!numbers.isEmpty())deviceOrganizations.refresh(
                        new DeviceOrganizationProjectionApi.Refresh(command.tenantId(),Set.of(),Set.of(),numbers));
            }
            return new Result(changedContracts.size(),shipmentChanges,packageChanges);
        } catch(RuntimeException|Error ex) {
            failure=ex;throw ex;
        } finally {
            RuntimeException cleanupFailure=null;
            for(Runnable cleanup:List.<Runnable>of(mapper::dropReceivableFields,mapper::dropShipmentFields,
                    mapper::dropUniqueContracts,mapper::dropDepartments,mapper::dropCompanies,mapper::dropScope)) {
                try { cleanup.run(); } catch(RuntimeException ex) {
                    if(failure!=null)failure.addSuppressed(ex);
                    else if(cleanupFailure==null)cleanupFailure=ex;
                    else cleanupFailure.addSuppressed(ex);
                }
            }
            if(failure==null && cleanupFailure!=null)throw cleanupFailure;
        }
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.MANDATORY)
    public List<Projection> readProjection(ProjectionQuery query) {
        if(query==null || query.target()==null || !valid(query.ids()) || query.ids().size()>500)
            throw new IllegalArgumentException("DPPMS_CONTRACT_PROJECTION_INVALID");
        authorize(new Actor(query.tenantId(),query.actorId()));
        if(query.ids().isEmpty())return List.of();
        var projections=mapper.selectProjection(query);var ids=new HashSet<Long>();
        for(var projection:projections)if(!query.ids().contains(projection.getId()) || !ids.add(projection.getId()))
            throw new IllegalArgumentException("DPPMS_CONTRACT_PROJECTION_AMBIGUOUS");
        if(ids.size()!=query.ids().size())throw new IllegalArgumentException("DPPMS_CONTRACT_PROJECTION_INPUT_CHANGED");
        return List.copyOf(projections);
    }
    private record Actor(Long tenantId,Long actorId) {}
    private void authorize(Actor actor) {
        if(!Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()) || actor.actorId()==null || actor.actorId()<=0)
            throw new IllegalArgumentException("DPPMS_CONTRACT_COMPLETION_INVALID");
        users.validateUser(actor.actorId());
        if(!permissions.hasAnyPermissions(actor.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
    }
    private record OrganizationKey(String kind,String code) {}
    private List<DppmsContractCompletionMapper.OrganizationRow> organizationRows(Long tenantId) {
        var grouped=new LinkedHashMap<OrganizationKey,List<ManagedOrganizationApi.Node>>();
        for(var node:organizations.list()) {
            if(node==null || (node.company()==null)==(node.department()==null) || node.id()==null || node.id()<=0
                    || !Objects.equals(tenantId,node.company()!=null?node.company().getTenantId():node.department().getTenantId()))
                throw new IllegalArgumentException("DPPMS_CONTRACT_ORGANIZATION_SCOPE_INVALID");
            if(node.code()==null || node.code().isBlank())continue;
            grouped.computeIfAbsent(new OrganizationKey(node.type(),node.code()),ignored->new ArrayList<>()).add(node);
        }
        var rows=new ArrayList<DppmsContractCompletionMapper.OrganizationRow>();
        for(var group:grouped.values()) {
            if(group.stream().map(ManagedOrganizationApi.Node::id).distinct().count()!=1
                    || group.stream().map(ManagedOrganizationApi.Node::name).distinct().count()!=1)continue;
            var node=group.getFirst();
            rows.add(new DppmsContractCompletionMapper.OrganizationRow(node.type(),node.id(),node.code(),node.name()));
        }
        return List.copyOf(rows);
    }
    private static boolean valid(Set<Long> ids) {
        return ids!=null && ids.stream().allMatch(id->id!=null && id>0);
    }
}
