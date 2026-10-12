package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.*;

/** Eight adjudicated directory entries only; excluded EHR fields never become attributes or permissions. */
@Component
@RequiredArgsConstructor
public class DppmsEhrDepartmentCompletionAdapter implements DataSyncAdapter {
    private final SystemDepartmentMigrationApi departments;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    @Override public Descriptor descriptor() {
        return new Descriptor(DppmsEhrDepartmentCompletionTemplate.ADAPTER,"DPPMS八项缺失正式部门补齐",List.of(new ObjectDescriptor("ehr_department","EHR完整部门原字段",
                DppmsEhrDepartmentCompletionTemplate.columns().stream().map(c->new Field(c,c,"STRING",false)).toList(),"SYSTEM","Department","system_dept",false)),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override public List<Change> apply(Batch batch){return execute(batch,true);}
    @Override public void refreshCaches(){departments.refreshCaches();}
    private List<Change> execute(Batch batch,boolean apply) {
        if(!batch.full() || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("缺失目录补齐必须完整核对，不清空、不删除或自动扩展部门");
        long tenant=TenantContextHolder.getRequiredTenantId();String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))throw new IllegalArgumentException("目录补齐运行归属无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        if(!DppmsEhrDepartmentCompletionTemplate.ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || task.getActiveRunId()==null)
            throw new IllegalArgumentException("缺失目录补齐任务身份无效");
        var run=runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId()) || !Objects.equals(run.getConfigVersion(),task.getVersion())
                || !Objects.equals(run.getPreview(),!apply) || !(apply?"APPLYING":"VALIDATING").equals(run.getStatus()) || run.getStartedAt()==null)
            throw new IllegalArgumentException("缺失目录补齐运行版本、阶段或审计身份无效");
        String actor=Long.toString(SyncFieldMapper.primaryKey(run.getCreator()));
        LocalDateTime migrationTime=run.getStartedAt().withNano(0);
        Map<String,Binding> prior=new HashMap<>();for(var b:batch.bindings())if(prior.put(b.object()+":"+b.sourceKey(),b)!=null)throw new IllegalArgumentException("目录来源绑定重复");
        var entries=new ArrayList<Entry>();var pending=new HashMap<String,String>();var seen=new HashSet<String>();
        for(var row:batch.rows()) {
            if(!"ehr_department".equals(row.object()) || !row.fields().keySet().containsAll(DppmsEhrDepartmentCompletionTemplate.columns()) || !Objects.equals(row.sourceKey(),text(row,"depID")))
                throw new IllegalArgumentException("EHR完整原字段、FND参考或来源主键缺失");
            String code=text(row,"depCode");if(!DppmsEhrDepartmentCompletionTemplate.CODES.contains(code) || !seen.add(code))throw new IllegalArgumentException("未获准的部门或重复原编码，须裁决");
            for(String empty:List.of("depProperty","depNum","xOrder","remark","depCustom1","depCustom3","depCustom5"))
                if(row.fields().get(empty)!=null)throw new IllegalArgumentException("已按全空排除的原字段出现业务值，须重新裁决: "+empty);
            boolean fnd=row.fields().get("migrationFnd_id")!=null;
            if(fnd && !Set.of("167010","167020","167050").contains(code) || !fnd && Set.of("167010","167020","167050").contains(code))
                throw new IllegalArgumentException("已裁决FND/EHR主来源变化，须核对");
            String name=fnd?text(row,"migrationFnd_departmentName"):text(row,"depName");
            int status=fnd?switch(text(row,"migrationFnd_status")){case "1"->0;case "0"->1;default->throw new IllegalArgumentException("FND部门状态未知");}:
                    row.fields().get("isDisabled")==null?0:switch(text(row,"isDisabled")){case "0"->0;case "1"->1;default->throw new IllegalArgumentException("EHR部门状态未知");};
            String parent=fnd?"160000":text(row,"migrationParentCode");if(parent==null)throw new IllegalArgumentException("已裁决原父部门缺失，不默认根级");
            var previous=prior.get(row.object()+":"+row.sourceKey());
            Department last=previous==null?null:JsonUtils.getObjectMapper().convertValue(previous.lastFields().get("department"),Department.class);
            LocalDateTime created=fnd?SyncFieldMapper.sourceTime(row.fields().get("migrationFnd_createTime")):last==null?migrationTime:last.createTime();
            LocalDateTime updated=fnd?(row.fields().get("migrationFnd_updateTime")==null?created:SyncFieldMapper.sourceTime(row.fields().get("migrationFnd_updateTime"))):migrationTime;
            Long leader=resolveLeader(row,tenant,task.getConnectionId());
            if(leader==null && row.fields().get("director")!=null)pending.put(row.sourceKey(),"主管原EHR员工"+text(row,"director")+"尚无唯一已迁入用户绑定，保持待关联");
            entries.add(new Entry(row.object(),row.sourceKey(),code,name,parent,status,created,updated,row.targetId(),last,
                    previous!=null && Boolean.TRUE.equals(previous.lastFields().get("_referenceOnly")),fnd?null:actor,fnd?null:actor,leader));
        }
        if(seen.size()!=DppmsEhrDepartmentCompletionTemplate.CODES.size())throw new IllegalArgumentException("已批准八项原部门缺项，未写入目录");
        var request=new Request(tenant,batch.owner(),entries);
        return (apply?departments.apply(request):departments.preview(request)).stream().map(r->new Change(r.object(),r.sourceKey(),r.after().id(),r.action(),fields(r.before(),false),
                fields(r.after(),"REFERENCED".equals(r.action())),"现有16列正式承接；11缺项及7全空字段按用户裁决不迁入；"+pending.getOrDefault(r.sourceKey(),"主管已精确关联或原空"))).toList();
    }
    private Long resolveLeader(Row row,long tenant,Long connection) {
        if(row.fields().get("director")==null)return null;
        if(!"1".equals(text(row,"migrationDirectorLoginCount")))return null;
        String object,id;String fndCount=text(row,"migrationDirectorFndCount");
        if(fndCount!=null && !"0".equals(fndCount)) {
            if(!"1".equals(fndCount))return null;
            object="fnd_user_info";id=text(row,"migrationDirectorFndId");
        } else {
            if(!"1".equals(text(row,"migrationDirectorTCount")))return null;
            object="t_user";id=text(row,"migrationDirectorTId");
        }
        if(id==null)return null;
        var matches=bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant,"DPPMS",connection,"DPPMS_USERS",object,List.of(id)));
        var ids=matches.stream().map(b->b.getTargetId()).filter(Objects::nonNull).distinct().toList();
        if(ids.size()>1)throw new IllegalArgumentException("主管已迁入用户绑定冲突，须裁决");
        return ids.isEmpty()?null:ids.getFirst();
    }
    private static Map<String,Object> fields(Department value,boolean reference){var fields=new LinkedHashMap<String,Object>();if(value!=null)fields.put("department",value);fields.put("_referenceOnly",reference);return fields;}
    private static String text(Row row,String name){return Objects.toString(row.fields().get(name),null);}
}
