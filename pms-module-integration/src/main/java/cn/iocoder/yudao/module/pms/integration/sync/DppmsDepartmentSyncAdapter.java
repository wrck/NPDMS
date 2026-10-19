package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.*;

@Component
@RequiredArgsConstructor
public class DppmsDepartmentSyncAdapter implements DataSyncAdapter {
    private final SystemDepartmentMigrationApi departments;
    private final SyncTaskService tasks;
    private static final Map<String,String> EHR_DISABLED_EXCEPTIONS=Map.of("160700002","541","310500001","391");
    @Override public Descriptor descriptor() {
        List<String> columns=new ArrayList<>(DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS);columns.addAll(DppmsDepartmentSyncTemplate.REFERENCES);
        return new Descriptor("DPPMS_DEPARTMENTS","DPPMS正式部门补齐",List.of(new ObjectDescriptor("fnd_department","原部门目录",
                columns.stream().map(column->new Field(column,column,"STRING",false)).toList(),"SYSTEM","Department","system_dept",false)),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    private record Execution(Batch batch,boolean apply,DppmsConsolidation.OrganizationPreviewBatch preview) {}
    @Override public List<Change> preview(Batch batch){return execute(new Execution(batch,false,null));}
    List<Change> previewAfterOrganization(DppmsConsolidation.OrganizationPreviewBatch preview){return execute(new Execution(preview.batch(),false,preview));}
    @Override public List<Change> apply(Batch batch){return execute(new Execution(batch,true,null));}
    List<Change> executeCanonical(DppmsOrganizationCanonical.ComponentBatch input){
        return execute(new Execution(input.batch(),input.apply(),new DppmsConsolidation.OrganizationPreviewBatch(input.batch(),input.preceding(),input.winners())));
    }
    @Override public void refreshCaches(){departments.refreshCaches();}
    private List<Change> execute(Execution execution) {
        var batch=execution.batch();boolean apply=execution.apply();
        if(!batch.full() || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("部门承接必须完整预览，不清空、不删除来源缺失部门");
        long tenant=TenantContextHolder.getRequiredTenantId();String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))throw new IllegalArgumentException("部门承接所有权无效");
        var task=tasks.required(Long.parseLong(owner[2]));
        if(!"DPPMS".equals(task.getSourceSystem()) || !DppmsConsolidation.accepts(task,"DPPMS_DEPARTMENTS"))throw new IllegalArgumentException("部门承接不能混用来源任务");
        Map<String,Binding> prior=new HashMap<>();
        for(var binding:batch.bindings())if(prior.put(binding.object()+":"+binding.sourceKey(),binding)!=null)throw new IllegalArgumentException("部门来源绑定重复");
        Set<String> codes=new HashSet<>();departments.getCatalogue().forEach(department->codes.add(department.code()));
        List<Entry> entries=new ArrayList<>();
        for(var row:batch.rows()) {
            if(!"fnd_department".equals(row.object()) || !row.fields().keySet().containsAll(DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS)
                    || !row.sourceKey().equals(text(row,"id")))throw new IllegalArgumentException("原部门字段或提取键缺失/不同");
            String code=text(row,"departmentNum");String normalized=DppmsDepartmentCodeRules.normalize(code);
            if(!Objects.equals(code,normalized))throw new IllegalArgumentException("目录原码不是批准补建编码；转换应关联已有部门，不伪造原目录记录");
            int status=switch(text(row,"status")){case "1"->0;case "0"->1;default->throw new IllegalArgumentException("原部门状态未知，须裁决");};
            String expected=EHR_DISABLED_EXCEPTIONS.get(code);
            if(expected!=null){
                if(!expected.equals(text(row,"migrationEhrDepartmentId")) || !"1".equals(text(row,"migrationEhrDisabled")))
                    throw new IllegalArgumentException("已裁决的EHR停用冲突来源事实变化，须重新核对");
                status=1;
            }
            String parent=codes.contains(code)?null:DppmsDepartmentCodeRules.parentForNew(code);
            var created=SyncFieldMapper.sourceTime(row.fields().get("createTime"));
            var updated=row.fields().get("updateTime")==null?created:SyncFieldMapper.sourceTime(row.fields().get("updateTime"));
            Binding previous=prior.get(row.object()+":"+row.sourceKey());
            Department last=previous==null?null:JsonUtils.getObjectMapper().convertValue(previous.lastFields().get("department"),Department.class);
            entries.add(new Entry(row.object(),row.sourceKey(),code,text(row,"departmentName"),parent,status,created,updated,row.targetId(),last,
                    previous!=null && Boolean.TRUE.equals(previous.lastFields().get("_referenceOnly"))));
        }
        if(execution.preview()!=null && execution.preview().canonicalWinners()!=null)
            entries.removeIf(entry->!execution.preview().canonicalWinners().contains(entry.object()+":"+entry.sourceKey()));
        var request=new Request(tenant,batch.owner(),entries);
        var results=apply?departments.apply(request):execution.preview()==null?departments.preview(request):
                departments.previewAfterOrganization(new OrganizationPreview(request,execution.preview().preceding()));
        return results.stream().map(result->new Change(result.object(),result.sourceKey(),result.after().id(),result.action(),fields(result.before(),false),fields(result.after(),"REFERENCED".equals(result.action())),
                "复用正式部门16列；isparam/有效期按用户裁决不迁入；已有定义、负责人、联系方式与审计不覆盖"
                        +(result.after().parentId()==null?"；父部门来自同批已校验的拟建组织，目标ID待正式迁入生成":""))).toList();
    }
    private static Map<String,Object> fields(Department value,boolean reference) {
        Map<String,Object> result=new LinkedHashMap<>();if(value!=null)result.put("department",value);result.put("_referenceOnly",reference);return result;
    }
    private static String text(Row row,String name){return Objects.toString(row.fields().get(name),null);}
}
