package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi.*;

/** Interprets approved source qualifications; all formal grants remain in the SYSTEM Owner. */
@Component
@RequiredArgsConstructor
public class DppmsUserScopeSyncAdapter implements DataSyncAdapter {
    private static final Set<String> NULL_COMPANY_ROLE_ONLY_ACCOUNTS=Set.of("z04214","w04218","y04223","w04261","z04264");
    private final SystemUserScopeMigrationApi scopes;
    private final SystemDepartmentMigrationApi departments;
    private final CompanyApi companies;
    private final SyncTaskService tasks;
    private final SyncBindingMapper bindings;
    @Override public Descriptor descriptor(){
        return new Descriptor("DPPMS_USER_SCOPES","DPPMS正式公司部门范围迁移",DppmsUserScopeSyncTemplate.columns().entrySet().stream()
                .map(entry->new ObjectDescriptor(entry.getKey(),entry.getKey(),entry.getValue().stream().map(column->new Field(column,column,"STRING",false)).toList(),
                        "SYSTEM","UserCompanyDepartmentScope","system_user_company_department_scope",false)).toList(),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public List<Change> preview(Batch batch){return execute(new DppmsAccountFoundationContext.Execution(batch,false,null));}
    @Override public List<Change> apply(Batch batch){return execute(new DppmsAccountFoundationContext.Execution(batch,true,null));}
    List<Change> afterFoundation(DppmsAccountFoundationContext.Execution execution){return execute(execution);}
    @Override public void refreshCaches(){}
    private List<Change> execute(DppmsAccountFoundationContext.Execution execution){
        var batch=execution.batch();boolean apply=execution.apply();
        if(execution.preceding()!=null)execution.preceding().requireExecution(execution);
        if(!batch.full() || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("范围迁移必须完整核对账号、公司角色和范围，不清空或删除来源缺失关系");
        long tenant=TenantContextHolder.getRequiredTenantId();String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))throw new IllegalArgumentException("范围承接所有权无效");
        var task=tasks.required(Long.parseLong(owner[2]));
        if(!"DPPMS".equals(task.getSourceSystem()) || !DppmsConsolidation.accepts(task,"DPPMS_USER_SCOPES"))throw new IllegalArgumentException("范围承接不能混用来源任务");
        ScopePlan plan=new ScopePlan(new Planning(batch,tenant,task.getConnectionId(),execution.preceding()));plan.build();
        var request=new Request(tenant,batch.owner(),plan.entries);var results=apply?scopes.apply(request):execution.preceding()==null?scopes.preview(request)
                :scopes.previewAfterFoundation(new FoundationPreview(request,execution.preceding().preview()));
        List<Change> changes=new ArrayList<>();
        for(var result:results)changes.add(new Change(result.object(),result.sourceKey(),result.after().id(),result.action(),fields(result.before()),fields(result.after()),
                "复用正式范围20列；历史日期与主/附加范围沿已批准规则，不生成办事处全部项目查看权"));
        for(var row:batch.rows())if(!plan.carried.contains(key(row)))changes.add(new Change(row.object(),row.sourceKey(),null,"SKIPPED",Map.of(),Map.of(),plan.skipReason(row)));
        return List.copyOf(changes);
    }
    private record Planning(Batch batch,long tenant,Long connection,DppmsAccountFoundationContext preceding) {}
    private final class ScopePlan {
        private final Batch batch;
        private final Map<String,Row> sources=new LinkedHashMap<>(),tAccounts=new HashMap<>(),fndAccounts=new HashMap<>();
        private final Map<String,List<Row>> details=new HashMap<>(),tRoles=new HashMap<>(),powers=new HashMap<>(),groups=new TreeMap<>();
        private final Map<String,Binding> prior=new HashMap<>();
        private final Map<String,Long> userTargets=new HashMap<>();
        private final Map<String,SystemDepartmentMigrationApi.Department> directory=new HashMap<>();
        private final Map<String,CompanyRespDTO> companyCache=new HashMap<>();
        private final List<Entry> entries=new ArrayList<>();
        private final Set<String> carried=new HashSet<>();
        ScopePlan(Planning planning){
            var batch=planning.batch();long tenant=planning.tenant();var connection=planning.connection();
            this.batch=batch;Map<String,List<String>> columns=DppmsUserScopeSyncTemplate.columns();
            for(var binding:batch.bindings())if(prior.put(binding.object()+":"+binding.sourceKey(),binding)!=null)throw new IllegalArgumentException("范围来源绑定重复");
            for(var row:batch.rows()){
                List<String> required=columns.get(row.object());
                if(required==null || !row.fields().keySet().containsAll(required) || sources.put(key(row),row)!=null
                        || !Objects.equals(row.sourceKey(),text(row,"t_user".equals(row.object())?"user_id":"id")))
                    throw new IllegalArgumentException("范围来源对象、全部原字段或提取键缺失/重复");
                switch(row.object()){
                    case "t_user"->{tAccounts.put(row.sourceKey(),row);group(row);}
                    case "fnd_user_info"->{fndAccounts.put(row.sourceKey(),row);group(row);}
                    case "t_user_info"->details.computeIfAbsent(text(row,"user_id"),ignored->new ArrayList<>()).add(row);
                    case "t_user_role"->tRoles.computeIfAbsent(text(row,"user_id"),ignored->new ArrayList<>()).add(row);
                    case "fnd_user_power"->powers.computeIfAbsent(text(row,"fndUserId"),ignored->new ArrayList<>()).add(row);
                    default->throw new IllegalArgumentException("未知范围来源");
                }
            }
            for(String object:List.of("t_user","fnd_user_info")){
                List<String> keys=sources.values().stream().filter(row->object.equals(row.object())).map(Row::sourceKey).toList();
                if(planning.preceding()!=null){
                    for(var key:keys){
                        var reference=planning.preceding().users().get(object+":"+key);
                        if(reference==null)continue;
                        var profile=(Map<?,?>)reference.fields().get("profile");
                        if(profile==null || !Objects.equals(accountName(sources.get(object+":"+key)),profile.get("username")))
                            throw new IllegalArgumentException("范围原账号与同轮Owner回执不符");
                        if(Boolean.TRUE.equals(reference.fields().get("_auditOnly")) && !"admin".equals(profile.get("username")))
                            throw new IllegalArgumentException("范围原账号混用审计专用关联");
                        userTargets.put(object+":"+key,reference.id());
                    }
                    continue;
                }
                var matched=bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant,"DPPMS",connection,"DPPMS_USERS",object,keys));
                for(var binding:matched){
                    String source=object+":"+binding.getSourceKey();Row row=sources.get(source);
                    Map<String,Object> saved=JsonUtils.parseMap(binding.getFieldsJson());
                    Map<?,?> profile=saved==null?null:(Map<?,?>)saved.get("profile");
                    if(row==null || binding.getTargetId()==null || profile==null || !Objects.equals(accountName(row),profile.get("username")))
                        throw new IllegalArgumentException("范围账号绑定未精确匹配原登录名，须先完成用户迁移");
                    if(Boolean.TRUE.equals(saved.get("_auditOnly")) && !"admin".equals(accountName(row)))throw new IllegalArgumentException("范围账号混用了未裁决审计专用关联");
                    Long previous=userTargets.putIfAbsent(source,binding.getTargetId());
                    if(previous!=null && !previous.equals(binding.getTargetId()))throw new IllegalArgumentException("范围来源账号关联多个正式用户");
                }
            }
            for(var department:departments.getCatalogue())if(department.code()!=null && !department.code().isBlank()
                    && directory.put(department.code(),department)!=null)throw new IllegalArgumentException("正式部门编码重复，不能任选关联");
        }
        private void group(Row row){String name=accountName(row);if(eligible(name))groups.computeIfAbsent(name,ignored->new ArrayList<>()).add(row);}
        void build(){
            for(var group:groups.entrySet()){
                List<Row> aliases=group.getValue();Row fnd=DppmsUserSyncAdapter.primaryFnd(group.getKey(),aliases);
                List<Row> legacy=aliases.stream().filter(row->"t_user".equals(row.object())).toList();
                if(legacy.size()>1)throw new IllegalArgumentException("同名t账号不唯一，不能合并范围");
                Row t=legacy.isEmpty()?null:legacy.getFirst();Set<String> companyCodes=new TreeSet<>();
                if(t!=null)for(var role:tRoles.getOrDefault(t.sourceKey(),List.of()))if(role.fields().get("comp_id")!=null)
                    companyCodes.add(DppmsCompanyCodeConverter.fromSource(text(role,"migrationCompanyCode")));
                if(t!=null && companyCodes.isEmpty() && NULL_COMPANY_ROLE_ONLY_ACCOUNTS.contains(group.getKey()))continue;
                if(fnd!=null){
                    if(t==null)companyCodes.addAll(List.of("001","002"));
                    else if(companyCodes.isEmpty())throw new IllegalArgumentException("跨来源账号没有明确公司范围，不能默认授予001、002");
                    for(var alias:aliases)if("fnd_user_info".equals(alias.object())){
                        String office=office(text(alias,"dpNo"));boolean primary=office!=null && Objects.equals(office,office(text(fnd,"dpNo")));
                        for(String role:fndRoles(alias))for(String company:companyCodes)
                            add(alias,"OWN",company,office,role,primary,timeOrCreated(alias,"effectiveFrom","createTime"),time(alias.fields().get("effectiveTo")),status(alias),audit(alias,true));
                        for(var power:powers.getOrDefault(alias.sourceKey(),List.of())){
                            // The FK determines the account; a redundant username is never a join key.
                            Set<String> additional=officeSet(text(power,"areapower"));
                            if(additional.isEmpty())continue;
                            Audit audit=powerAudit(power);LocalDateTime from=timeOrCreated(power,"effectiveFrom","createTime");
                            for(String role:fndRoles(alias))for(String company:companyCodes)for(String code:additional)
                                add(power,"EXTRA",company,code,role,false,from,time(power.fields().get("effectiveTo")),status(alias),audit);
                        }
                    }
                }
                if(t!=null)for(var info:details.getOrDefault(t.sourceKey(),List.of())){
                    String own=office(text(info,"custom3"));
                    boolean primary=own!=null && (fnd==null || Objects.equals(own,office(text(fnd,"dpNo"))));
                    for(var role:tRoles.getOrDefault(t.sourceKey(),List.of())){
                        if(role.fields().get("comp_id")==null)continue; // Approved role-only import, no company grant.
                        String company=DppmsCompanyCodeConverter.fromSource(text(role,"migrationCompanyCode"));String code=tRole(role);
                        LocalDateTime from=time(t.fields().get("create_time"));Audit audit=audit(t,false);
                        add(info,"OWN",company,own,code,primary,from,null,status(t),audit);
                        for(String extra:officeSet(text(info,"custom5")))add(info,"EXTRA",company,extra,code,false,from,null,status(t),audit);
                    }
                }
            }
        }
        private void add(Row source,String kind,String companyCode,String office,String role,boolean primary,LocalDateTime from,LocalDateTime to,int status,Audit audit){
            Row account=switch(source.object()){
                case "fnd_user_info"->source;
                case "fnd_user_power"->fndAccounts.get(text(source,"fndUserId"));
                case "t_user_info"->tAccounts.get(text(source,"user_id"));
                default->throw new IllegalArgumentException("范围没有明确来源账号");
            };
            Long user=userTargets.get(key(account));if(user==null)throw new IllegalArgumentException("请先迁移范围账号并保存精确来源绑定");
            CompanyRespDTO company=companyCache.computeIfAbsent(companyCode,companies::getCompanyByCode);
            if(company==null)throw new IllegalArgumentException("已转换公司编码尚未关联正式公司");
            var department=office==null?null:directory.get(office);
            if(office!=null && department==null)throw new IllegalArgumentException("范围部门尚未承接到正式目录："+office);
            Scope scope=new Scope(user,company.getId(),company.getCode(),company.getName(),department==null?null:department.id(),
                    department==null?null:department.code(),department==null?null:department.name(),role,primary,from,to,status);
            String sourceKey=source.sourceKey()+":"+kind+":"+companyCode+":"+(office==null?"COMPANY":office)+":"+role;
            Binding previous=prior.get(source.object()+":"+sourceKey);
            Relation last=previous==null?null:JsonUtils.getObjectMapper().convertValue(previous.lastFields().get("relation"),Relation.class);
            entries.add(new Entry(source.object(),sourceKey,scope,audit,previous==null?null:previous.targetId(),last));carried.add(key(source));
        }
        private Set<String> fndRoles(Row account){
            Set<String> result=new TreeSet<>();
            for(String key:DppmsUserRoleSyncAdapter.roleKeys(text(account,"roleIds"))){
                if("15".equals(key))continue;String source=Objects.toString(DppmsRoleCodes.FND_CODES.get(key),null);
                if(source==null)throw new IllegalArgumentException("范围角色编码未裁决");result.add(DppmsRoleCodes.targetFnd(source));
            }
            if(result.isEmpty())throw new IllegalArgumentException("来源范围无已裁决角色，不能擅自填user");return result;
        }
        private String tRole(Row role){String source=DppmsRoleCodes.T_SOURCE_CODES.get(text(role,"role_id"));
            String target=source==null?null:Objects.toString(DppmsRoleCodes.T_CODES.get(source),null);
            if(target==null)throw new IllegalArgumentException("范围t角色编码未裁决");return target;}
        private Audit audit(Row account,boolean fnd){
            String created=fnd?"createTime":"create_time",updated=fnd?"updateTime":"update_time";
            LocalDateTime start=time(account.fields().get(created)),end=time(account.fields().get(updated));
            return new Audit(actor(account.object(),text(account,fnd?"createBy":"create_by")),start,
                    actor(account.object(),text(account,fnd?"updateBy":"update_by")),end==null?start:end);
        }
        private Audit powerAudit(Row power){LocalDateTime created=time(power.fields().get("createTime")),updated=time(power.fields().get("updateTime"));
            return new Audit(actor("fnd_user_info",text(power,"createBy")),created,actor("fnd_user_info",text(power,"updateBy")),updated==null?created:updated);}
        private String actor(String object,String name){
            if(name==null || name.isEmpty())return name;
            List<Row> matches=sources.values().stream().filter(row->object.equals(row.object()) && name.equals(accountName(row))).toList();
            Row source=matches.size()==1?matches.getFirst():"fnd_user_info".equals(object)?DppmsUserSyncAdapter.primaryFnd(name,matches):null;
            Long id=source==null?null:userTargets.get(key(source));if(id==null)throw new IllegalArgumentException("范围历史操作者尚未精确迁移，不能凭名字猜配");return id.toString();
        }
        private String accountName(Row row){
            return switch(row.object()){
                case "t_user"->text(row,"user_name");case "fnd_user_info"->text(row,"username");
                case "t_user_info","t_user_role"->{Row account=tAccounts.get(text(row,"user_id"));yield account==null?null:text(account,"user_name");}
                case "fnd_user_power"->{Row account=fndAccounts.get(text(row,"fndUserId"));yield account==null?null:text(account,"username");}
                default->null;
            };
        }
        private String skipReason(Row row){
            String name=accountName(row);
            if(DppmsUserMigrationScope.excludesAdjudicatedOrphan(row))return "用户明确本条测试/管理员资料不迁入：无账号外键且不在已批准账号范围";
            if(name==null)return "来源人员或关系没有对应原账号；保持待关联，不伪造账号或范围";
            if(!DppmsUserMigrationScope.includes(name))return "原账号不在用户限定的首字母+工号、*_ss、*_supp迁移范围内，不生成授权";
            if("konglinghui_supp".equals(name))return "用户明确本账号范围不迁入；原角色为空，不擅自生成角色或范围";
            if(NULL_COMPANY_ROLE_ONLY_ACCOUNTS.contains(name))return "原角色公司为空，用户仅批准角色关系、不新增公司范围；没有其他明确公司范围时不默认授予001、002";
            if("t_user".equals(row.object()))return "账号原字段用于精确关联；账号资料由DPPMS_USERS承接，本对象不单独生成范围";
            if("t_user_role".equals(row.object()))return "原角色关系由DPPMS_USER_ROLES承接到system_user_role；本对象作为公司及角色关联依据，不另生成无部门的重复范围";
            if("fnd_user_power".equals(row.object()) && officeSet(text(row,"areapower")).isEmpty())return "用户明确不迁入：空范围没有附加授权事实；历史声明日期及操作者一并列明，不写入custom_info或生成授权";
            return "来源无独立可承接范围；未明事项保持待处理，不猜配公司、角色或办事处";
        }
    }
    private static boolean eligible(String username){return DppmsUserMigrationScope.includes(username) && !"konglinghui_supp".equals(username);}
    private static int status(Row account){return switch(text(account,"status")){case "1"->0;case "0"->1;case "2"->{
        if(!"t_user".equals(account.object()))throw new IllegalArgumentException("来源范围账号状态2未裁决");yield 1;}
        default->throw new IllegalArgumentException("范围账号状态未知");};}
    private static String office(String original){return original==null || original.isBlank()?null:DppmsDepartmentCodeRules.normalize(original);}
    private static Set<String> officeSet(String original){Set<String> result=new TreeSet<>();
        if(original!=null)for(String code:original.split("[;,]"))if(!code.isBlank())result.add(office(code.trim()));return result;}
    private static String text(Row row,String name){return Objects.toString(row.fields().get(name),null);}
    private static String key(Row row){return row.object()+":"+row.sourceKey();}
    private static LocalDateTime time(Object original){return original==null?null:SyncFieldMapper.sourceTime(original);}
    private static LocalDateTime timeOrCreated(Row row,String field,String created){LocalDateTime original=time(row.fields().get(field));return original==null?time(row.fields().get(created)):original;}
    private static Map<String,Object> fields(Relation value){return value==null?Map.of():Map.of("relation",value);}
}
