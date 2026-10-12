package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractIdLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectScopeMigrationContextApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractMigrationIdentityQuery;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Approved current and historical relationships, without performing current business actions. */
@Service @RequiredArgsConstructor
public class ProjectContractRelationMigrationService implements ProjectContractRelationMigrationApi {
    private final ProjectContractRelationMigrationMapper relations;
    private final ContractMapper contracts;
    private final ProjectScopeMigrationContextApi projects;
    private final OrganizationScopeApi organization;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final SystemUserMigrationApi historicalUsers;
    private final ProjectMasterMigrationApi projectIdentities;

    @Override @Transactional(readOnly=true)
    public List<ResolvedReference> resolveReferences(ReferenceQuery query) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(query==null || !Objects.equals(query.tenantId(),tenant) || query.actorId()==null || query.actorId()<=0
                || query.owner()==null || !query.owner().matches("integration:"+tenant+":[0-9]+")
                || query.references()==null || query.references().size()>500) fail("目标引用解析范围无效");
        users.validateUser(query.actorId());
        if(!permissions.hasAnyPermissions(query.actorId(),"pms:integration:execute")
                || !permissions.hasAnyPermissions(query.actorId(),"pms:commerce:contract:relate")) fail("缺少迁移执行或合同关联权限");
        var scopes=organization.getActiveScopes(query.actorId());
        if(scopes==null || scopes.isEmpty()) fail("合同公司授权范围为空");
        var sources=new HashSet<String>();
        var expected=new TreeMap<Long,ProjectMasterMigrationApi.IdentityTarget>();
        for(var ref:query.references()) {
            if(ref==null || ref.sourceKey()==null || !ref.sourceKey().matches("[0-9:]+") || !sources.add(ref.sourceKey())
                    || ref.projectId()==null || ref.projectId()<=0 || ref.projectType()==null || ref.projectCode()==null
                    || ref.contractNo()==null || ref.contractNo().isBlank()) fail("精确项目或合同引用无效");
            var identity=new ProjectMasterMigrationApi.IdentityTarget(ref.projectId(),ref.projectType(),ref.projectCode());
            var prior=expected.putIfAbsent(ref.projectId(),identity);
            if(prior!=null && !prior.equals(identity)) fail("同一正式项目的类型或编码引用不一致");
        }
        var current=projectIdentities.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(tenant,query.actorId(),List.copyOf(expected.values())));
        if(current==null || current.size()!=expected.size()) fail("正式项目引用数量不一致");
        var projectsById=new HashMap<Long,ProjectMasterMigrationApi.Facts>();
        for(var project:current) {
            var identity=project==null?null:expected.get(project.id());
            if(identity==null || !Objects.equals(project.tenantId(),tenant) || project.version()==null || project.version()<0
                    || !Objects.equals(identity.projectCode(),project.projectCode()) || !Objects.equals(identity.projectType(),project.projectType())
                    || project.companyCode()==null || project.companyCode().isBlank() || projectsById.put(project.id(),project)!=null)
                fail("正式项目身份、版本或公司未核实");
        }
        var result=new ArrayList<ResolvedReference>();
        for(var ref:query.references()) {
            var project=projectsById.get(ref.projectId());
            if(scopes.stream().filter(Objects::nonNull).noneMatch(scope->scope.getId()!=null && scope.getVersion()!=null
                    && Objects.equals(scope.getCompanyCode(),project.companyCode()))) fail("合同公司授权范围不匹配");
            var candidates=contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(tenant,project.companyCode(),ref.contractNo()));
            if(candidates==null || candidates.size()!=1) fail("正式公司和合同号未唯一匹配");
            var contract=candidates.getFirst();
            if(contract==null || contract.getId()==null || contract.getId()<=0 || Boolean.TRUE.equals(contract.getDeleted())
                    || !Objects.equals(contract.getTenantId(),tenant) || !Objects.equals(contract.getCompanyCode(),project.companyCode())
                    || !Objects.equals(contract.getContractNo(),ref.contractNo())) fail("正式合同身份不一致");
            result.add(new ResolvedReference(ref.sourceKey(),project.id(),project.version(),contract.getId()));
        }
        return List.copyOf(result);
    }

    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Result> preview(Request request) { return execute(request,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Request request) { return execute(request,true); }

    private List<Result> execute(Request request,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(request==null || !Objects.equals(request.tenantId(),tenant) || request.actorId()==null || request.actorId()<=0
                || request.owner()==null || !request.owner().matches("integration:"+tenant+":[0-9]+")
                || request.entries()==null || request.entries().size()>500) fail("运行范围无效");
        users.validateUser(request.actorId());
        if(!permissions.hasAnyPermissions(request.actorId(),"pms:integration:execute")
                || !permissions.hasAnyPermissions(request.actorId(),"pms:commerce:contract:relate")) fail("缺少迁移执行或合同关联权限");
        var scopes=organization.getActiveScopes(request.actorId());
        if(scopes==null || scopes.isEmpty()) fail("合同公司授权范围为空");
        var keys=new HashSet<String>();var identities=new HashSet<String>();var actorIds=new HashSet<Long>();
        for(var entry:request.entries()) {
            if(entry==null || entry.sourceKey()==null || !entry.sourceKey().matches("[0-9]+:[0-9]+")
                    || !keys.add(entry.sourceKey()) || entry.sourceProjectKey()==null || entry.sourceProjectCode()==null
                    || entry.expectedProjectVersion()==null || entry.contractNo()==null || entry.contractNo().isBlank()) fail("原关系或项目桥身份缺失/重复");
            var row=entry.incoming();
            if(row==null || row.id()!=null || !Objects.equals(row.tenantId(),tenant) || row.projectId()==null || row.projectId()<=0
                    || row.contractId()==null || row.contractId()<=0 || !"RELATED".equals(row.relationRole())
                    || !Set.of("ACTIVE","HISTORICAL").contains(row.status()==null?"":row.status()) || row.effectiveTo()!=null || row.effectiveFrom()==null
                    || !row.effectiveFrom().equals(row.createTime()) || row.createTime()==null || row.updateTime()==null
                    || !Boolean.FALSE.equals(row.deleted()) || !"DPPMS".equals(row.sourceSystem())
                    || !"pm_project_contract".equals(row.sourceTable()) || !entry.sourceKey().equals(row.sourceRecordKey())
                    || !Integer.valueOf(0).equals(row.version()) || !identities.add(row.projectId()+":"+row.contractId())) fail("原关系字段不完整、重复或含未批准状态");
            if("HISTORICAL".equals(row.status()) && (entry.reviewedBefore()!=null || request.adoptExisting()))
                fail("历史关系不授权接管既有关系或转换当前关联");
            for(String actor:Arrays.asList(row.creator(),row.updater())) if(actor!=null) {
                if(UNLINKED_AUDIT_CODES.contains(actor)) continue;
                if(!actor.matches("[0-9]{1,19}")) fail("历史人员必须精确关联平台用户");
                actorIds.add(Long.valueOf(actor));
            }
            if(entry.reviewedBefore()!=null && (!request.adoptExisting() || entry.lastApplied()!=null)) fail("存量限定纠正未批准或已被绑定");
        }
        if(!actorIds.isEmpty()) {
            var actual=historicalUsers.getHistoricalIdentities(tenant,actorIds);
            if(actual==null || !new HashSet<>(actual.stream().map(SystemUserMigrationApi.HistoricalIdentity::id).toList()).equals(actorIds)) fail("历史审计用户不存在或跨租户");
        }
        var result=new ArrayList<Result>();
        for(var entry:request.entries()) {
            var incoming=entry.incoming();
            var query=new ProjectScopeMigrationContextApi.Query(tenant,request.actorId(),incoming.projectId(),
                    entry.expectedProjectVersion(),entry.sourceProjectKey(),entry.sourceConnectionId());
            var project=apply?projects.lockAndRevalidate(query):projects.resolve(query);
            if(project==null || !Objects.equals(project.projectCode(),entry.sourceProjectCode())) fail("正式项目编码与原项目不一致");
            // Same exact contract-company qualification as the normal relationship Owner.
            var contract=contracts.selectByIdForUpdate(new ContractIdLockQuery(tenant,incoming.contractId()));
            if(contract==null || Boolean.TRUE.equals(contract.getDeleted()) || !Objects.equals(contract.getTenantId(),tenant)
                    || !Objects.equals(contract.getContractNo(),entry.contractNo()) || contract.getCompanyCode()==null
                    || !Objects.equals(contract.getCompanyCode(),project.companyCode())
                    || scopes.stream().filter(Objects::nonNull).noneMatch(scope->scope.getId()!=null && scope.getVersion()!=null
                    && Objects.equals(scope.getCompanyCode(),contract.getCompanyCode()))) fail("合同号、公司或授权不一致");
            var identity=new ProjectContractRelationMigrationMapper.IdentityQuery(tenant,incoming.projectId(),incoming.contractId());
            var before=apply?relations.selectIdentityForUpdate(identity):relations.selectIdentity(identity);
            if(before!=null && (!Boolean.FALSE.equals(before.deleted()) || !incoming.status().equals(before.status())
                    || before.effectiveTo()!=null || before.version()==null)) fail("现存关系已删除、失效或版本缺失，禁止复活");
            if(entry.targetId()!=null && (before==null || !entry.targetId().equals(before.id()))) fail("来源绑定的目标已改变");
            if(entry.lastApplied()!=null && !entry.lastApplied().equals(before)) fail("全部17列与上次迁入结果不同，禁止覆盖");
            if(entry.reviewedBefore()!=null && (!entry.reviewedBefore().equals(before)
                    || !Objects.equals(entry.targetId(),before.id()))) fail("限定纠正的全部17列已有后续变化");
            if(before!=null && entry.lastApplied()==null && entry.reviewedBefore()==null) fail("现存关系未批准接管");
            if(before==null && (entry.reviewedBefore()!=null || entry.lastApplied()!=null)) fail("被核实的现存关系已不存在");
            var after=canonical(incoming,before,before==null?null:before.id(),before==null?0:before.version());
            String action=before==null?"CREATED":before.equals(after)?"UNCHANGED":"UPDATED";
            if("UPDATED".equals(action)) {
                if(entry.reviewedBefore()==null) fail("普通重复迁入不得自动纠正现存历史审计");
                after=canonical(incoming,before,before.id(),Math.addExact(before.version(),1));
            }
            result.add(new Result(entry.sourceKey(),action,before,after));
        }
        // Validate the complete batch before its first write; INT commits bindings in the same transaction.
        if(apply) for(var item:result) {
            if("UNCHANGED".equals(item.action())) continue;
            var after=item.after();
            if("CREATED".equals(item.action())) after=canonical(after,null,IdWorker.getId(),0);
            int count=item.before()==null?relations.insertHistorical(after):relations.correctHistorical(
                    new ProjectContractRelationMigrationMapper.HistoricalWrite(item.before(),after));
            if(count!=1) throw new IllegalStateException("PROJECT_CONTRACT_RELATION_WRITE_CONFLICT");
            int index=result.indexOf(item);result.set(index,new Result(item.sourceKey(),item.action(),item.before(),after));
        }
        return List.copyOf(result);
    }
    private static Relation canonical(Relation incoming,Relation before,Long id,Integer version) {
        return new Relation(id,incoming.tenantId(),incoming.projectId(),incoming.contractId(),"RELATED",
                before==null?incoming.sourceSystem():before.sourceSystem(),before==null?incoming.sourceTable():before.sourceTable(),
                before==null?incoming.sourceRecordKey():before.sourceRecordKey(),incoming.effectiveFrom(),null,incoming.status(),version,
                incoming.creator(),incoming.createTime(),incoming.updater(),incoming.updateTime(),false);
    }
    private static void fail(String message) { throw new IllegalArgumentException("项目合同关系："+message); }
}
