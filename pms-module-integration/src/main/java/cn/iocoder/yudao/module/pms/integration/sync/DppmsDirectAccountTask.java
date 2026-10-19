package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import org.springframework.stereotype.Component;
import java.util.*;

/** P04 direct task. Reuses approved source interpretation with isolated direct SQL writers. */
@Component
public class DppmsDirectAccountTask extends AbstractSynchronizeTask<DppmsDirectAccountTask.Execution,DataSyncAdapter.Batch,List<DataSyncAdapter.Change>> implements DataSyncAdapter {
    record Execution(Batch batch, boolean apply) {}
    private final DppmsUserSyncAdapter accounts;
    private final DppmsRoleSyncAdapter roles;
    private final DppmsUserRoleSyncAdapter memberships;
    private final DppmsUserScopeSyncAdapter scopes;
    private final SyncTaskService tasks;
    private final SyncBindingMapper bindings;
    private final cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi organizations;
    @org.springframework.beans.factory.annotation.Autowired
    DppmsDirectAccountTask(DirectAccountPersistence persistence, SyncTaskService tasks, SyncBindingMapper bindings,
            cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi organizations,
            cn.iocoder.yudao.module.system.api.company.CompanyApi companies,
            cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi departments,
            cn.iocoder.yudao.module.system.api.dept.PostApi posts, jakarta.validation.Validator validator) {
        this.tasks=tasks;this.bindings=bindings;this.organizations=organizations;
        this.accounts=new DppmsUserSyncAdapter(persistence.users(),tasks,companies,departments,posts,validator);
        this.roles=new DppmsRoleSyncAdapter(persistence.roles(),tasks,bindings);
        this.memberships=new DppmsUserRoleSyncAdapter(persistence.memberships(),tasks,bindings);
        this.scopes=new DppmsUserScopeSyncAdapter(persistence.scopes(),departments,companies,tasks,bindings);
    }
    @Override public Descriptor descriptor() {
        Map<String,ObjectDescriptor> objects=new LinkedHashMap<>();
        for(var adapter:List.of(accounts,roles,memberships,scopes))for(var object:adapter.descriptor().objects()) {
            var previous=objects.putIfAbsent(object.name(),object);
            if(previous!=null && !previous.fields().equals(object.fields()))throw new IllegalStateException("账号基础输入字段契约不一致");
        }
        return new Descriptor(DppmsDirectAccountTemplate.ADAPTER,"DPPMS账号、角色、关系与公司部门范围（直写）",List.copyOf(objects.values()),
                List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public List<Change> preview(Batch batch){return syncData(new Execution(batch,false));}
    @Override public List<Change> apply(Batch batch){return syncData(new Execution(batch,true));}
    @Override public void refreshCaches(){accounts.refreshCaches();roles.refreshCaches();memberships.refreshCaches();scopes.refreshCaches();}
    @Override protected void syncDataBefore(Execution execution) {
        Batch input=execution.batch();
        long tenant=TenantContextHolder.getRequiredTenantId();
        String[] owner=input.owner()==null?new String[0]:input.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("账号基础任务所有权无效");
        var task=tasks.required(Long.parseLong(owner[2]));
        if(!DppmsDirectAccountTemplate.ADAPTER.equals(task.getAdapter()) || !DppmsConsolidation.accepts(task,"DPPMS_USERS")
                || !input.full() || input.clearBeforeLoad() || !"RETAIN".equals(input.missingPolicy()) || !"UPSERT".equals(input.loadingMode()))
            throw new IllegalArgumentException("账号基础只允许完整读取、保留现存数据与直写顺序承接");
    }
    @Override protected Batch syncDataQuery(Execution execution) {
        Batch input=execution.batch();long tenant=TenantContextHolder.getRequiredTenantId();
        var task=tasks.required(Long.parseLong(input.owner().split(":")[2]));
        return prepare(new Preparation(input,tenant,task.getConnectionId()));
    }
    @Override protected List<Change> syncDataInsertWrapper(Execution execution, Batch batch) {
        boolean apply=execution.apply();
        var context=new DppmsAccountFoundationContext(batch,apply);List<Change> result=new ArrayList<>();
        var accountChanges=apply?accounts.apply(select(new Selection(batch,accounts,false))):accounts.preview(select(new Selection(batch,accounts,false)));
        accountChanges=verifiedEmployees(new AccountReceipts(batch,accountChanges));
        result.addAll(accountChanges);if(blocked(accountChanges))return List.copyOf(result);context.accounts(accountChanges);
        var roleChanges=roles.afterAccounts(new DppmsAccountFoundationContext.Execution(select(new Selection(batch,roles,false)),apply,context));
        result.addAll(roleChanges);if(blocked(roleChanges))return List.copyOf(result);context.roles(roleChanges);
        var relationChanges=memberships.afterFoundation(new DppmsAccountFoundationContext.Execution(select(new Selection(batch,memberships,true)),apply,context));
        result.addAll(relationChanges);if(blocked(relationChanges))return List.copyOf(result);context.memberships(relationChanges);
        var scopeChanges=scopes.afterFoundation(new DppmsAccountFoundationContext.Execution(select(new Selection(batch,scopes,true)),apply,context));
        // Raw account/role rows are scope inputs, not another independent write or duplicate evidence item.
        Set<String> emitted=new HashSet<>();result.forEach(change->emitted.add(change.object()+":"+change.sourceKey()));
        for(var change:scopeChanges)if(!"SKIPPED".equals(change.action()) || !emitted.contains(change.object()+":"+change.sourceKey()))result.add(change);
        return List.copyOf(result);
    }
    private record AccountReceipts(Batch batch,List<Change> changes) {}
    private static List<Change> verifiedEmployees(AccountReceipts receipts) {
        var batch=receipts.batch();var changes=receipts.changes();
        Map<String,Row> originals=new HashMap<>();batch.rows().forEach(row->originals.put(row.object()+":"+row.sourceKey(),row));
        List<Change> result=new ArrayList<>();
        for(var change:changes){
            var row=originals.get(change.object()+":"+change.sourceKey());var after=new LinkedHashMap<>(change.after());
            if(row!=null && Set.of("t_user","fnd_user_info").contains(row.object()) && after.containsKey("profile")
                    && !Boolean.TRUE.equals(after.get("_auditOnly")) && "1".equals(Objects.toString(row.fields().get("migrationLoginCount"),null))) {
                Object employee=row.fields().get("migrationEmployeeId");
                if(employee!=null && Objects.equals(employee,row.fields().get("migrationLoginEmployeeId")))
                    after.put("_verifiedEhrEmployeeId",employee.toString());
            }
            result.add(new Change(change.object(),change.sourceKey(),change.targetId(),change.action(),change.before(),after,change.message()));
        }
        return List.copyOf(result);
    }
    private static boolean blocked(List<Change> changes){return changes.stream().anyMatch(change->Set.of("ISSUE","CONFLICT").contains(change.action()));}
    @Override protected void syncDataSuccess(Execution execution, List<Change> changes) {
        if(execution.apply() && blocked(changes))throw new IllegalArgumentException("P04直写存在未决问题，整批回滚");
    }
    private record Selection(Batch batch,DataSyncAdapter adapter,boolean compoundBindings) {}
    private static Batch select(Selection selection) {
        var batch=selection.batch();Set<String> objects=new HashSet<>();selection.adapter().descriptor().objects().forEach(object->objects.add(object.name()));
        List<Row> rows=batch.rows().stream().filter(row->objects.contains(row.object())).toList();
        Set<String> identities=new HashSet<>();rows.forEach(row->identities.add(row.object()+":"+row.sourceKey()));
        List<Binding> prior=batch.bindings().stream().filter(binding->objects.contains(binding.object())
                && (selection.compoundBindings()?binding.sourceKey().contains(":"):identities.contains(binding.object()+":"+binding.sourceKey()))).toList();
        // T_USER_ROLE has a simple relation identity, while FND expands roleIds into compound identities.
        if(selection.adapter() instanceof DppmsUserRoleSyncAdapter)prior=batch.bindings().stream().filter(binding->objects.contains(binding.object())).toList();
        return new Batch(batch.owner(),rows,prior,true,batch.missingPolicy(),batch.adoptExisting(),batch.loadingMode(),false);
    }
    private record Preparation(Batch batch,long tenant,Long connection) {}
    private Batch prepare(Preparation preparation) {
        Batch batch=preparation.batch();Map<String,Row> tAccounts=new HashMap<>();Map<String,List<Row>> names=new HashMap<>();
        Set<String> companyKeys=new HashSet<>(),departmentKeys=new HashSet<>();
        batch.rows().stream().filter(row->"t_user".equals(row.object())).forEach(row->tAccounts.put(row.sourceKey(),row));
        for(var row:batch.rows()) {
            if(Set.of("t_user","fnd_user_info").contains(row.object()))names.computeIfAbsent(row.object()+":"+
                    Objects.toString(row.fields().get("t_user".equals(row.object())?"user_name":"username"),null),ignored->new ArrayList<>()).add(row);
            if("t_user_info".equals(row.object()) && qualifies(row,tAccounts)){
                collect(companyKeys,row.fields().get("compID"));collect(departmentKeys,row.fields().get("depID"));
            }
            if(Set.of("T_USER_ROLE","t_user_role").contains(row.object()) && qualifies(row,tAccounts))collect(companyKeys,row.fields().get("comp_id"));
        }
        Map<String,cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Node> catalogue=new HashMap<>();
        if(!companyKeys.isEmpty() || !departmentKeys.isEmpty())for(var node:organizations.list())catalogue.put(node.type()+":"+node.id(),node);
        Map<String,String> companies=directory(new DirectoryQuery(preparation,"t_company",companyKeys,catalogue));
        Map<String,String> departments=directory(new DirectoryQuery(preparation,"ehr_department",departmentKeys,catalogue));
        List<Row> rows=new ArrayList<>();
        for(var row:batch.rows()) {
            var fields=new LinkedHashMap<>(row.fields());
            if("t_user_info".equals(row.object()) && qualifies(row,tAccounts)){
                fields.put("migrationCompanyCode",lookup(companies,fields.get("compID")));
                fields.put("migrationDepartmentCode",lookup(departments,fields.get("depID")));
            }
            if(Set.of("T_USER_ROLE","t_user_role").contains(row.object())){
                var account=tAccounts.get(Objects.toString(fields.get("user_id"),null));
                fields.put("username",account==null?null:account.fields().get("user_name"));
                if(qualifies(row,tAccounts))fields.put("migrationCompanyCode",lookup(companies,fields.get("comp_id")));
            }
            if(Set.of("T_ROLE","FND_ROLE").contains(row.object()))for(String kind:List.of("creator","updater")){
                String name=Objects.toString(fields.get(kind+"Name"),null);if(name==null || name.isEmpty())continue;
                String table="FND_ROLE".equals(row.object())?"fnd_user_info":"t_user";
                var matches=names.getOrDefault(table+":"+name,List.of());
                Row matched=matches.size()==1?matches.getFirst():"fnd_user_info".equals(table)?DppmsUserSyncAdapter.primaryFnd(name,matches):null;
                if(matched==null)throw new IllegalArgumentException("角色历史操作者原账号不唯一或不存在");
                fields.put(kind+"SourceKey",matched.sourceKey());
            }
            rows.add(new Row(row.object(),row.sourceKey(),Collections.unmodifiableMap(fields),row.targetId()));
        }
        return new Batch(batch.owner(),List.copyOf(rows),batch.bindings(),batch.full(),batch.missingPolicy(),batch.adoptExisting(),batch.loadingMode(),batch.clearBeforeLoad());
    }
    private static boolean qualifies(Row row,Map<String,Row> accounts){
        if(DppmsUserMigrationScope.excludesAdjudicatedOrphan(row))return false;
        var account=accounts.get(Objects.toString(row.fields().get("user_id"),null));
        return account!=null && DppmsUserMigrationScope.includes(Objects.toString(account.fields().get("user_name"),null));
    }
    private record DirectoryQuery(Preparation preparation,String sourceObject,Set<String> keys,
            Map<String,cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Node> catalogue) {}
    private Map<String,String> directory(DirectoryQuery query) {
        Map<String,String> result=new HashMap<>();Map<String,Long> targets=new HashMap<>();
        if(query.keys().isEmpty())return result;
        var p=query.preparation();
        for(var binding:bindings.selectIdentitySources(new SyncQueries.IdentitySources(p.tenant(),"DPPMS",p.connection(),
                "DPPMS_ORGANIZATION",query.sourceObject(),List.copyOf(query.keys())))){
            Map<String,Object> fields=JsonUtils.parseMap(binding.getFieldsJson());
            Object code="t_company".equals(query.sourceObject())?fields.get("sourceCompanyCode"):fields.get("code");
            if(code==null && fields.get("department") instanceof Map<?,?> department)code=department.get("code");
            if(binding.getTargetId()==null || code==null || code.toString().isBlank())throw new IllegalArgumentException("P01来源别名没有正式公司或部门身份");
            boolean company="t_company".equals(query.sourceObject());
            var formal=query.catalogue().get((company?"COMPANY:":"DEPARTMENT:")+binding.getTargetId());
            String expected=company?cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter.fromSource(code.toString()):code.toString();
            if(formal==null || (formal.company()!=null)!=company || !Objects.equals(expected,formal.code()))
                throw new IllegalArgumentException("P01来源别名对应的正式公司或部门已删除或变化");
            String previous=result.putIfAbsent(binding.getSourceKey(),code.toString());Long target=targets.putIfAbsent(binding.getSourceKey(),binding.getTargetId());
            if(previous!=null && (!previous.equals(code.toString()) || !Objects.equals(target,binding.getTargetId())))
                throw new IllegalArgumentException("P01来源公司或部门别名冲突，不能任选");
        }
        return result;
    }
    private static void collect(Set<String> keys,Object value){if(value!=null && !value.toString().isBlank() && !"0".equals(value.toString()))keys.add(value.toString());}
    private static String lookup(Map<String,String> values,Object raw){
        if(raw==null || raw.toString().isBlank() || "0".equals(raw.toString()))return null;
        String result=values.get(raw.toString());if(result==null)throw new IllegalArgumentException("P01本地来源公司/部门别名未完成，不能回源猜配");return result;
    }
}
