package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectContractRelationSyncAdapter.auditValue;
/** The source emits complete business groups. Compute the existing Q018 audit decision once, before chunking originals. */
final class DppmsProjectContractGroupProjection {
    static final String PROJECTION="projectContractGroups";
    static boolean supports(SyncSourceProjection.Input input) {
        return DppmsProjectContractRelationSyncTemplate.ADAPTER.equals(input.definition().adapter()) && PROJECTION.equals(input.source().rowProjection());
    }
    static List<MysqlSyncReader.SourceRows> expand(SyncSourceProjection.Input input) {
        var result=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();
        for(var group:input.rows()) {
            if(group.get("originalProjectId")==null || group.get("originalProjectCode")==null || group.get("migrationKey")==null)fail("已发布项目身份缺失");
            Object raw=group.get("sourceRelations");
            var tree=JsonUtils.parseTree(raw instanceof String value?value:JsonUtils.toJsonString(raw));
            if(!tree.isArray() || tree.isEmpty())fail("完整来源组缺失");
            var rows=new ArrayList<Map<String,Object>>();
            for(var node:tree) {
                if(!node.isObject())fail("原字段缺失");
                Map<String,Object> row=JsonUtils.parseMap(node.toString());
                var expected=new HashSet<>(DppmsProjectContractRelationSyncTemplate.columns());expected.remove("originalProjectId");expected.remove("originalProjectCode");
                if(!row.keySet().equals(expected) || !Objects.equals(text(row.get("contractNo")),text(group.get("contractNo")))
                        || !Objects.equals(text(row.get("group_projectType")),text(group.get("projectType")))
                        || text(row.get("member_projectCode"))==null
                        || !text(row.get("member_projectCode")).replace("-his","").equals(text(group.get("originalProjectCode"))))fail("组与原身份不一致");
                row.put("originalProjectId",group.get("originalProjectId"));row.put("originalProjectCode",group.get("originalProjectCode"));rows.add(row);
            }
            boolean current=rows.stream().anyMatch(row->!historical(row));
            var eligible=rows.stream().filter(row->historical(row)!=current).toList();
            var created=eligible.stream().map(row->time(auditValue(row,"createTime"))).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElseThrow(()->new IllegalArgumentException("DPPMS合同组创建时间缺失"));
            var first=eligible.stream().filter(row->created.equals(time(auditValue(row,"createTime")))).toList();
            if(first.stream().map(row->text(auditValue(row,"createBy"))).distinct().count()!=1)fail("创建同刻人员冲突");
            var canonical=first.stream().min(Comparator.<Map<String,Object>>comparingLong(row->id(row.get("id"))).thenComparingLong(row->id(row.get("member_id")))).orElseThrow();
            var updated=eligible.stream().map(row->time(auditValue(row,"updateTime"))).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            var latest=updated==null?List.<Map<String,Object>>of():eligible.stream().filter(row->updated.equals(time(auditValue(row,"updateTime")))).toList();
            if(latest.stream().map(row->text(auditValue(row,"updateBy"))).distinct().count()>1)fail("更新同刻人员冲突");
            Object updater=latest.isEmpty()?null:auditValue(latest.getFirst(),"updateBy");
            var expanded=new ArrayList<Row>();
            for(var original:rows) {
                var row=new LinkedHashMap<>(original);
                DppmsProjectContractRelationSyncTemplate.columns().forEach(name->row.put("canonical_"+name,canonical.get(name)));
                row.put("formalGroupSize",rows.size());row.put("formalStatus",current?"ACTIVE":"HISTORICAL");
                row.put("formalCreatedAt",created);row.put("formalCreator",auditValue(canonical,"createBy"));
                row.put("formalUpdatedAt",updated);row.put("formalUpdater",updater);row.put("formalCreateConflicts",0);row.put("formalUpdateConflicts",0);row.put("projectCandidateCount",1);
                String key=DppmsProjectContractFormalDecision.orderedSourceKey(row);
                if(!keys.add(key))fail("原身份重复");row.put(input.source().sourceKey(),key);
                expanded.add(new Row(input.source().object(),key,row,null));
            }
            // Reuse all existing completeness, canonical bridge, status and source-audit checks.
            DppmsProjectContractFormalDecision.resolve(expanded);
            expanded.sort(Comparator.comparing(Row::sourceKey));expanded.forEach(row->result.add(Collections.unmodifiableMap(row.fields())));
            if(input.definition().maxRows()>0 && result.size()>input.definition().maxRows())fail("完整来源组超过容量限制");
        }
        return List.of(new MysqlSyncReader.SourceRows(input.source().object(),input.source().sourceObject(),List.copyOf(result)));
    }
    private static boolean historical(Map<String,Object> row){return text(row.get("member_projectCode")).endsWith("-his");}
    private static String text(Object value){return value==null?null:value.toString();}
    private static long id(Object value){return value==null?0:SyncFieldMapper.primaryKey(text(value));}
    private static LocalDateTime time(Object value){return value==null?null:SyncFieldMapper.sourceTime(value);}
    private static void fail(String message){throw new IllegalArgumentException("DPPMS合同组："+message);}
    private DppmsProjectContractGroupProjection() {}
}
