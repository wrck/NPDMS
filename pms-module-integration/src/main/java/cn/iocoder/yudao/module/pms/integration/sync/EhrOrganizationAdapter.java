package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class EhrOrganizationAdapter implements DataSyncAdapter {
    private final ManagedOrganizationApi organization;

    public Descriptor descriptor() {
        List<Field> common=List.of(new Field("code","编码","STRING",true),new Field("name","名称","STRING",true),
                new Field("status","状态（0启用/1停用）","LONG",true));
        List<Field> department=new ArrayList<>(common);
        department.addAll(List.of(new Field("parentKey","父部门源主键","REFERENCE",false),
                new Field("companyKey","所属公司源主键","REFERENCE",false),new Field("sort","排序","LONG",true)));
        return new Descriptor("EHR_ORGANIZATION","EHR 公司与部门",
                List.of(new ObjectDescriptor("COMPANY","公司",common,"SYSTEM","Company","system_company",true),
                        new ObjectDescriptor("DEPARTMENT","部门",department,"SYSTEM","Department","system_dept",true)),
                List.of("RETAIN","DISABLE"),List.of("UPSERT","INSERT_ONLY","INSERT_IGNORE"),true);
    }
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    public List<Change> apply(Batch batch) { return execute(batch,true); }
    public void refreshCaches() { organization.refreshCaches(); }

    private List<Change> execute(Batch batch,boolean apply) {
        Map<String,Row> rows=new LinkedHashMap<>();
        Map<String,Binding> bindings=new HashMap<>();
        batch.bindings().forEach(b->bindings.put(key(b.object(),b.sourceKey()),b));
        // Preserve other rows for incremental parent/company resolution and for disappearance handling.
        batch.bindings().forEach(b->rows.put(key(b.object(),b.sourceKey()),
                new Row(b.object(),b.sourceKey(),b.lastFields(),b.targetId())));
        Set<String> seen=new HashSet<>();
        for(Row r:batch.rows()) {
            String key=key(r.object(),r.sourceKey());
            if(!seen.add(key)) throw new IllegalArgumentException("组织来源身份重复");
            rows.put(key,r);
        }
        if(batch.full() && "DISABLE".equals(batch.missingPolicy())) {
            for(var pair:new ArrayList<>(rows.entrySet())) if(!seen.contains(pair.getKey())) {
                Row r=pair.getValue();Map<String,Object> fields=new LinkedHashMap<>(r.fields());fields.put("status",1);
                rows.put(pair.getKey(),new Row(r.object(),r.sourceKey(),fields,r.targetId()));
            }
        }
        List<ManagedOrganizationApi.Entry> entries=new ArrayList<>();
        Set<String> assignedIdKeys=new HashSet<>();
        for(Row r:rows.values()) {
            if(!Set.of("COMPANY","DEPARTMENT").contains(r.object())) throw new IllegalArgumentException("未知组织对象");
            String company=reference(r.fields().get("companyKey"));
            if(company!=null&&!rows.containsKey(key("COMPANY",company))) throw new IllegalArgumentException("所属公司来源未映射");
            String parent=reference(r.fields().get("parentKey"));
            String parentKey=parent==null?null:key("DEPARTMENT",parent);
            if(parentKey!=null&&!rows.containsKey(parentKey)) throw new IllegalArgumentException("父部门来源未映射");
            if(batch.full()&&seen.contains(key(r.object(),r.sourceKey())) && parentKey!=null&&!seen.contains(parentKey))
                throw new IllegalArgumentException("完整来源快照中父部门缺失");
            if(batch.full()&&seen.contains(key(r.object(),r.sourceKey())) && company!=null&&!seen.contains(key("COMPANY",company)))
                throw new IllegalArgumentException("完整来源快照中所属公司缺失");
            ManagedOrganizationApi.Node entity;
            Long targetId=r.targetId();
            Object requested=r.fields().get("_sourcePrimaryKey");
            if(requested!=null) {
                long requestedId=SyncFieldMapper.primaryKey(requested.toString());
                if(targetId!=null && targetId!=requestedId) throw new IllegalArgumentException("既有映射主键与来源主键不一致");
                if(targetId==null) assignedIdKeys.add(key(r.object(),r.sourceKey()));
                targetId=requestedId;
            }
            if("COMPANY".equals(r.object())) {
                var companyEntity=new cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO()
                        .setId(targetId).setCode(text(r,"code")).setName(text(r,"name")).setStatus(integer(r,"status"));
                entity=new ManagedOrganizationApi.Node(companyEntity,null,null);
            } else {
                var department=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO()
                        .setId(targetId).setCode(text(r,"code")).setName(text(r,"name"))
                        .setStatus(integer(r,"status")).setSort(integer(r,"sort"));
                entity=new ManagedOrganizationApi.Node(null,department,null);
            }
            entries.add(new ManagedOrganizationApi.Entry(key(r.object(),r.sourceKey()),parentKey,entity));
        }
        var command=new ManagedOrganizationApi.Command(batch.owner(),entries,batch.adoptExisting(),assignedIdKeys,batch.loadingMode(),seen);
        var results=batch.clearBeforeLoad()
                ?(apply?organization.replaceAll(command):organization.previewReplacement(command))
                :(apply?organization.apply(command):organization.preview(command));
        Map<String,Long> targetIds=new HashMap<>();
        results.forEach(result->{if(result.after().id()!=null)targetIds.put(result.key(),result.after().id());});
        List<Change> changes=new ArrayList<>();
        for(var result:results) {
            if("CLEARED".equals(result.action())) {
                var node=result.before();Object entity=node.company()!=null?node.company():node.department();
                changes.add(new Change(node.type(),"_cleared_"+node.id(),node.id(),"CLEARED",
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseMap(
                                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(entity)),Map.of(),"加载前清空当前租户目标记录"));
                continue;
            }
            Row row=rows.get(result.key());
            Binding previous=bindings.get(result.key());
            String action=result.action();
            if(!seen.contains(result.key()) && batch.full() && "RETAIN".equals(batch.missingPolicy())) action="SKIPPED";
            Map<String,Object> before=previous==null?Map.of():previous.lastFields();
            if(previous==null && result.before()!=null) {
                Object entity=result.before().company()!=null?result.before().company():result.before().department();
                before=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseMap(
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(entity));
            }
            Map<String,Object> after=new LinkedHashMap<>("SKIPPED".equals(action)&&previous!=null?previous.lastFields():row.fields());
            if("DEPARTMENT".equals(row.object())) {
                String companyKey=reference(row.fields().get("companyKey"));
                String parentKey=reference(row.fields().get("parentKey"));
                after.put("_companyTargetId",companyKey==null?null:targetIds.get(key("COMPANY",companyKey)));
                after.put("_parentTargetId",parentKey==null?null:targetIds.get(key("DEPARTMENT",parentKey)));
            }
            changes.add(new Change(row.object(),row.sourceKey(),result.after().id(),action,
                    before,after,
                    "CONFLICT".equals(action)?"目标已存在、由其他来源管理或接管字段不一致":null));
        }
        return changes;
    }
    private static String key(String object,String sourceKey) { return object+":"+sourceKey; }
    private static String reference(Object value) {
        if(value==null || value.toString().isBlank() || "0".equals(value.toString())) return null;
        return value.toString();
    }
    private static String text(Row r,String field) {
        Object value=r.fields().get(field);
        if(value==null||value.toString().isBlank()) throw new IllegalArgumentException("组织必填字段缺失: "+field);
        return value.toString();
    }
    private static int integer(Row r,String field) { return new java.math.BigDecimal(text(r,field)).intValueExact(); }
}
