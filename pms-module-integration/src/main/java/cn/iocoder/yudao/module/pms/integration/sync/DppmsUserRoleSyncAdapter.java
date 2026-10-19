package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.system.api.migration.SystemUserRoleMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DppmsUserRoleSyncAdapter implements DataSyncAdapter {
    private final SystemUserRoleMigrationApi memberships;
    private final SyncTaskService tasks;
    private final SyncBindingMapper bindings;
    @Override public Descriptor descriptor() {
        return new Descriptor("DPPMS_USER_ROLES","DPPMS正式用户角色关系",DppmsUserRoleSyncTemplate.COLUMNS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(group->new ObjectDescriptor(group.getKey(),group.getKey(),group.getValue().stream().map(field->new Field(field,field,"STRING",false)).toList(),
                        "SYSTEM","UserRole","system_user_role",false)).toList(),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public List<Change> preview(Batch batch){return execute(new DppmsAccountFoundationContext.Execution(batch,false,null));}
    @Override public List<Change> apply(Batch batch){return execute(new DppmsAccountFoundationContext.Execution(batch,true,null));}
    List<Change> afterFoundation(DppmsAccountFoundationContext.Execution execution){return execute(execution);}
    @Override public void refreshCaches(){memberships.refreshCaches();}
    private record Assignment(Row source,String sourceKey,String userTable,String userKey,String roleTable,String roleKey,String roleCode){}
    private List<Change> execute(DppmsAccountFoundationContext.Execution execution) {
        var batch=execution.batch();boolean apply=execution.apply();
        if(execution.preceding()!=null)execution.preceding().requireExecution(execution);
        if (!batch.full()||batch.clearBeforeLoad()||!"UPSERT".equals(batch.loadingMode())||!"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("角色关系仅支持完整来源核对与追加承接，不删除或替换现有权限");
        long tenant=TenantContextHolder.getRequiredTenantId();
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3||!"integration".equals(owner[0])||!Long.toString(tenant).equals(owner[1]))throw new IllegalArgumentException("角色关系迁移所有权无效");
        var task=tasks.required(Long.parseLong(owner[2]));
        if(!"DPPMS".equals(task.getSourceSystem())||!DppmsConsolidation.accepts(task,"DPPMS_USER_ROLES"))throw new IllegalArgumentException("角色关系来源任务无效");
        List<Assignment> assignments=new ArrayList<>();List<Change> changes=new ArrayList<>();Set<String> identities=new HashSet<>();
        for(Row row:batch.rows()) {
            if(!DppmsUserRoleSyncTemplate.COLUMNS.containsKey(row.object())||!row.fields().keySet().containsAll(DppmsUserRoleSyncTemplate.COLUMNS.get(row.object()))
                    ||!Objects.equals(row.sourceKey(),text(row,"id"))||!identities.add(row.object()+":"+row.sourceKey()))
                throw new IllegalArgumentException("角色关系原字段或来源身份缺失/重复");
            if(!DppmsUserMigrationScope.includes(text(row,"username"))) {
                changes.add(new Change(row.object(),row.sourceKey(),null,"SKIPPED",Map.of(),Map.of(),"来源账号不在用户明确迁移范围，不授予角色或范围"));continue;
            }
            boolean fnd="FND_USER_ROLE".equals(row.object());
            Set<String> roleKeys=fnd?roleKeys(text(row,"roleIds")):Set.of(Objects.requireNonNull(text(row,"role_id"),"原角色外键缺失"));
            for(String roleKey:roleKeys) {
                String compound=fnd?row.sourceKey()+":"+roleKey:row.sourceKey();
                if(fnd&&"15".equals(roleKey)) {
                    changes.add(new Change(row.object(),compound,null,"SKIPPED",Map.of(),Map.of(),"用户明确停用角色不迁入；原fnd_roles15及其关联按裁决排除"));continue;
                }
                String sourceCode=fnd?Objects.toString(DppmsRoleCodes.FND_CODES.get(roleKey),null):DppmsRoleCodes.T_SOURCE_CODES.get(roleKey);
                if(sourceCode==null)throw new IllegalArgumentException("原角色ID无已裁决业务编码，不生成猜测角色或忽略授权");
                String roleCode=fnd?DppmsRoleCodes.targetFnd(sourceCode):Objects.toString(DppmsRoleCodes.T_CODES.get(sourceCode),null);
                if(roleCode==null)throw new IllegalArgumentException("原角色ID无已裁决业务编码，不生成猜测角色或忽略授权");
                assignments.add(new Assignment(row,compound,fnd?"fnd_user_info":"t_user",fnd?row.sourceKey():text(row,"user_id"),
                        fnd?"fnd_roles":"t_role",roleKey,roleCode));
            }
        }
        Map<String,Long> userIds=execution.preceding()==null?resolve(assignments,true,tenant,task.getConnectionId()):new HashMap<>();
        Map<String,Long> roleIds=execution.preceding()==null?resolve(assignments,false,tenant,task.getConnectionId()):new HashMap<>();
        if(execution.preceding()!=null)for(var assignment:assignments){
            String user=assignment.userTable()+":"+assignment.userKey(), role=assignment.roleTable()+":"+assignment.roleKey();
            userIds.put(user,execution.preceding().user(user,true).id());
            var reference=execution.preceding().role(role);
            if(!Objects.equals(assignment.roleCode(),reference.fields().get("code")))throw new IllegalArgumentException("同轮角色编码与批准映射不同");
            roleIds.put(role,reference.id());
        }
        Map<String,Binding> previous=new HashMap<>();batch.bindings().forEach(binding->{
            if(previous.put(binding.object()+":"+binding.sourceKey(),binding)!=null)throw new IllegalArgumentException("角色关系绑定重复");
        });
        List<SystemUserRoleMigrationApi.Entry> entries=new ArrayList<>();Map<String,Assignment> byIdentity=new HashMap<>();
        for(Assignment assignment:assignments) {
            String key=assignment.source().object()+":"+assignment.sourceKey();
            if(byIdentity.put(key,assignment)!=null)throw new IllegalArgumentException("展开后的来源角色关系身份重复");
            Binding bound=previous.get(key);
            entries.add(new SystemUserRoleMigrationApi.Entry(assignment.source().object(),assignment.sourceKey(),userIds.get(assignment.userTable()+":"+assignment.userKey()),
                    roleIds.get(assignment.roleTable()+":"+assignment.roleKey()),assignment.roleCode(),bound==null?null:bound.targetId()));
        }
        var request=new SystemUserRoleMigrationApi.Request(tenant,batch.owner(),entries);
        var results=apply?memberships.apply(request):execution.preceding()==null?memberships.preview(request)
                :memberships.previewAfterFoundation(new SystemUserRoleMigrationApi.FoundationPreview(request,execution.preceding().preview()));
        for(var result:results) {
            Assignment source=byIdentity.get(result.object()+":"+result.sourceKey());
            if(source==null)throw new IllegalStateException("角色关系Owner返回未知来源身份");
            Map<String,Object> after=fields(result.after());after.put("roleCode",source.roleCode());
            // Formal company authorization is a separate, explicitly adjudicated scope entity.
            after.put("sourceCompanyId",source.source().fields().get("comp_id"));
            changes.add(new Change(result.object(),result.sourceKey(),result.after().id(),result.action(),fields(result.before()),after,
                    "承接正式用户角色；既有角色、菜单和公司部门范围不在此关系写入中替换"));
        }
        return List.copyOf(changes);
    }
    private Map<String,Long> resolve(List<Assignment> assignments,boolean user,long tenant,Long connection) {
        Map<String,Set<String>> keys=new HashMap<>();
        for(Assignment assignment:assignments)keys.computeIfAbsent(user?assignment.userTable():assignment.roleTable(),ignored->new HashSet<>()).add(user?assignment.userKey():assignment.roleKey());
        Map<String,Long> result=new HashMap<>();
        for(var group:keys.entrySet()) {
            for(var binding:bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant,"DPPMS",connection,user?"DPPMS_USERS":"DPPMS_ROLES",group.getKey(),List.copyOf(group.getValue())))) {
                if(binding.getTargetId()==null||user&&Boolean.TRUE.equals(JsonUtils.parseMap(binding.getFieldsJson()).get("_auditOnly")))
                    throw new IllegalArgumentException("角色关联不能引用未迁入账号或审计专用管理员绑定");
                String key=group.getKey()+":"+binding.getSourceKey();Long previous=result.putIfAbsent(key,binding.getTargetId());
                if(previous!=null&&!previous.equals(binding.getTargetId()))throw new IllegalArgumentException("来源用户或角色存在多个目标身份");
            }
            for(String key:group.getValue())if(!result.containsKey(group.getKey()+":"+key))throw new IllegalArgumentException("请先完成正式用户、角色及其来源绑定迁移，再承接角色关系");
        }
        return result;
    }
    static Set<String> roleKeys(String value) {
        if(value==null||value.isBlank())return Set.of();
        Set<String> keys=new TreeSet<>();
        // The original UI stores multiple wrapped IDs as ';11;,;12;'. Single ';3;' also occurs.
        for(String part:value.split("[;,]",-1)) {
            if(part.isEmpty())continue;
            if(!part.matches("[1-9][0-9]*"))throw new IllegalArgumentException("原roleIds字符串格式未知，不忽略或猜角色");
            keys.add(part);
        }
        return Collections.unmodifiableSet(keys);
    }
    private static Map<String,Object> fields(SystemUserRoleMigrationApi.Relation relation) {
        Map<String,Object> result=new LinkedHashMap<>();if(relation!=null){result.put("userId",relation.userId());result.put("roleId",relation.roleId());}return result;
    }
    private static String text(Row row,String field){return Objects.toString(row.fields().get(field),null);}
}
