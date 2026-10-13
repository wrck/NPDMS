package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectContractRelationSyncAdapter.auditValue;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectContractRelationSyncAdapter.original;

/** Q018: complete source identities are aggregated before project filtering or streaming chunks. */
final class DppmsProjectContractFormalDecision {
    private DppmsProjectContractFormalDecision() {}
    static final List<String> FACTS=List.of("formalGroupSize","formalStatus","formalCreatedAt","formalCreator",
            "formalUpdatedAt","formalUpdater","formalCreateConflicts","formalUpdateConflicts");
    static List<String> columns() {
        var result=new ArrayList<>(FACTS);
        DppmsProjectContractRelationSyncTemplate.columns().forEach(name->result.add("canonical_"+name));
        result.add("projectCandidateCount");
        return List.copyOf(result);
    }
    record Selection(String sourceKey,LocalDateTime createdAt,LocalDateTime updatedAt,String creator,String updater,
                     String status,long count,Map<String,Object> canonical,Map<String,Object> proof) {}
    static boolean sameProof(Selection selection,Object saved) {
        if(!(saved instanceof Map<?,?> before) || !before.keySet().equals(selection.proof().keySet())) return false;
        return FACTS.stream().allMatch(name->Objects.equals(text(selection.proof().get(name)),text(before.get(name))));
    }
    static boolean present(Map<String,Object> fields) { return fields.containsKey("formalGroupSize"); }
    static String orderedSourceKey(Map<String,Object> fields) {
        long canonical=key(fields.get("canonical_id")),id=key(fields.get("id"));
        long member=optionalKey(fields.get("member_id")),canonicalMember=optionalKey(fields.get("canonical_member_id"));
        return String.format(Locale.ROOT,"%020d:%020d:%d:%020d:%020d",canonical,canonicalMember,
                id==canonical && member==canonicalMember?0:1,id,member);
    }
    static String canonicalBindingKey(Selection selection) {
        var fields=new HashMap<>(selection.canonical());
        fields.put("canonical_id",fields.get("id"));fields.put("canonical_member_id",fields.get("member_id"));
        return orderedSourceKey(fields);
    }
    static Map<String,Selection> resolve(List<Row> rows) {
        var result=new LinkedHashMap<String,Selection>();var groups=new HashMap<List<String>,Selection>();
        for(var row:rows) {
            var f=row.fields();
            if(!f.keySet().containsAll(columns()) || !Objects.equals(row.sourceKey(),orderedSourceKey(f))) fail("完整身份归并元数据或来源顺序缺失");
            long count=key(f.get("formalGroupSize"));
            if(!"0".equals(text(f.get("formalCreateConflicts"))) || !"0".equals(text(f.get("formalUpdateConflicts"))))
                fail("时间并列且人员不同，禁止按技术ID选择审计人员");
            var canonical=new LinkedHashMap<String,Object>();
            DppmsProjectContractRelationSyncTemplate.columns().forEach(name->canonical.put(name,f.get("canonical_"+name)));
            for(String name:List.of("originalProjectId","originalProjectCode","group_projectType","contractNo"))
                if(text(canonical.get(name))==null || !Objects.equals(text(canonical.get(name)),text(f.get(name)))) fail("规范来源不属于同一项目合同身份");
            String canonicalCode=text(canonical.get("member_projectCode")),code=text(f.get("member_projectCode"));
            if(canonicalCode==null || !canonicalCode.replace("-his","").equals(text(canonical.get("originalProjectCode")))
                    || !Objects.equals(text(canonical.get("projectGroupCode")),text(canonical.get("member_projectGroupCode")))
                    || !Objects.equals(text(canonical.get("projectGroupCode")),text(canonical.get("group_projectGroupCode")))) fail("规范来源的完整项目桥不一致");
            String status=text(f.get("formalStatus"));
            if(!Objects.equals(status,canonicalCode.endsWith("-his")?"HISTORICAL":"ACTIVE")
                    || code!=null && !code.endsWith("-his") && !"ACTIVE".equals(status)) fail("当前来源必须优先于历史来源");
            var created=time(f.get("formalCreatedAt"));var sourceCreated=time(auditValue(f,"createTime"));
            var updated=time(f.get("formalUpdatedAt"));var sourceUpdated=time(auditValue(f,"updateTime"));
            if(created==null || !created.equals(time(auditValue(canonical,"createTime")))
                    || !Objects.equals(text(f.get("formalCreator")),text(auditValue(canonical,"createBy")))) fail("最早来源及创建审计不一致");
            if(Objects.equals(status,code!=null && code.endsWith("-his")?"HISTORICAL":"ACTIVE")
                    && (sourceCreated==null || created.isAfter(sourceCreated)
                    || sourceUpdated!=null && (updated==null || updated.isBefore(sourceUpdated)))) fail("完整身份最早创建或最近非空更新时间不一致");
            String sourceKey=key(canonical.get("id"))+":"+key(canonical.get("member_id"));
            if(count==1 && (!sourceKey.equals(key(f.get("id"))+":"+key(f.get("member_id")))
                    || !original(canonical).equals(original(f)) || !Objects.equals(updated,sourceUpdated)
                    || !Objects.equals(text(f.get("formalUpdater")),text(auditValue(f,"updateBy"))))) fail("单条来源的规范事实不一致");
            if(count>1 && f.get("reviewedBefore")!=null) fail("身份归并不授权接管现存正式关系");
            var proof=new LinkedHashMap<String,Object>();
            for(String name:FACTS) {
                Object value=f.get(name);
                if(value!=null && name.endsWith("At")) value=time(value).toString();
                proof.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value.toString());
            }
            var selection=new Selection(sourceKey,created,updated==null?created:updated,text(f.get("formalCreator")),
                    text(f.get("formalUpdater")),status,count,original(canonical),Map.copyOf(proof));
            var identity=List.of(text(f.get("originalProjectId")),text(f.get("contractNo")));
            var prior=groups.putIfAbsent(identity,selection);
            if(prior!=null && !prior.equals(selection)) fail("同一项目合同的完整归并事实不一致");
            if(result.put(row.sourceKey(),selection)!=null) fail("原合同成员身份重复");
        }
        return Map.copyOf(result);
    }
    static Map<String,String> fieldDecisions(Map<String,Object> source,Selection selected) {
        var result=new LinkedHashMap<String,String>();
        String rawStatus=text(source.get("member_projectCode")).endsWith("-his")?"HISTORICAL":"ACTIVE";
        boolean differentStatus=!rawStatus.equals(selected.status());
        result.put("status",differentStatus?"不独立迁入：按裁决保留同一关系的当前事实":"正式关系状态承接");
        for(String name:List.of("createTime","createBy","updateTime","updateBy")) {
            Object chosen=switch(name) {
                case "createTime" -> selected.createdAt();case "createBy" -> selected.creator();
                case "updateTime" -> selected.updatedAt();default -> selected.updater();
            };
            for(String field:List.of(name,"member_"+name)) {
                Object raw=source.get(field);String value=text(raw);
                if(value!=null && !value.isEmpty() && name.endsWith("Time")) value=time(raw).toString();
                result.put(field,differentStatus?"不独立迁入：按裁决保留同一关系的当前事实":
                        value==null || value.isEmpty()?"原缺值，无独立审计事实":Objects.equals(value,text(chosen))?
                        "同义原值复用正式审计字段":"不独立迁入：同一关系按合同为主、成员补缺，采用最早创建和最近非空更新");
            }
        }
        return result;
    }
    private static long key(Object value) { return SyncFieldMapper.primaryKey(text(value)); }
    private static long optionalKey(Object value) { return text(value)==null?0:key(value); }
    private static LocalDateTime time(Object value) { return text(value)==null?null:SyncFieldMapper.sourceTime(value); }
    private static String text(Object value) { return value==null || value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value.toString(); }
    private static void fail(String message) { throw new IllegalArgumentException("DPPMS合同正式身份归并："+message); }
}
