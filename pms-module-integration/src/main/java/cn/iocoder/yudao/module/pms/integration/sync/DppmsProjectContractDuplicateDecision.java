package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import java.time.LocalDateTime;
import java.util.*;

/** Complete same-fact groups use the earliest original date; no source IDs are business rules. */
final class DppmsProjectContractDuplicateDecision {
    private DppmsProjectContractDuplicateDecision() {}
    record Canonical(String sourceKey, LocalDateTime createdAt, boolean identical) {}
    static Map<String,Canonical> resolve(List<Row> rows) {
        var streamed=new HashMap<String,Canonical>();
        var canonicalFacts=new HashMap<String,Row>();
        for(var row:rows) if(row.fields().containsKey("canonicalOriginalId")) {
            var f=row.fields();long count=positive(f.get(DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE));
            long id=positive(f.get("id")),canonical=positive(f.get("canonicalOriginalId"));
            var created=SyncFieldMapper.sourceTime(f.get("createTime"));
            var earliest=SyncFieldMapper.sourceTime(f.get("canonicalCreatedAt"));
            String flag=text(f.get("duplicateIdentical"));
            if(!Set.of("0","1","false","true").contains(flag==null?"":flag)) fail("完整分组的同值标记缺失");
            boolean identical="1".equals(flag)||"true".equals(flag);
            if(created==null || earliest==null || earliest.isAfter(created) || identical && !earliest.equals(created)
                    || count==1 && (id!=canonical || !earliest.equals(created) || !identical)) fail("完整原表最早记录元数据不一致");
            String canonicalKey=canonical+":"+text(f.get("member_id"));
            if(count>1 && f.get("reviewedBefore")!=null) fail("通用合并规则不授权接管现存正式关系");
            var first=canonicalFacts.putIfAbsent(canonicalKey,row);
            if(first!=null) {
                var names=new ArrayList<>(DppmsProjectContractRelationSyncTemplate.columns());
                names.addAll(List.of("contractId","expectedProjectVersion",DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE));
                for(String name:names) if(!List.of("id","createTime").contains(name)
                        && !Objects.equals(text(first.fields().get(name)),text(f.get(name)))) fail("同一规范来源的桥关系、业务或完整组数量不同");
                if(!Objects.equals(streamed.get(first.sourceKey()),new Canonical(canonicalKey,earliest,identical))) fail("规范来源元数据发生变化");
            }
            streamed.put(row.sourceKey(),new Canonical(canonicalKey,earliest,identical));
        }
        var counted=rows.stream().filter(row->!row.fields().containsKey("canonicalOriginalId")
                && row.fields().containsKey(DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE)).toList();
        var uncounted=new HashSet<List<String>>();
        for(var row:rows) {
            if(row.fields().containsKey(DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE)) continue;
            var f=row.fields();
            var key=Arrays.asList(text(f.get("contractNo")),text(f.get("projectGroupCode")),text(f.get("member_id")),
                    text(f.get("createBy")),text(f.get("updateTime")),text(f.get("updateBy")));
            if(!uncounted.add(key)) fail("重复组缺少完整原表分组数量，不能用局部结果合并");
        }
        streamed.putAll(resolveCompleteGroups(counted));
        return Map.copyOf(streamed);
    }
    static String orderedSourceKey(Map<String,Object> fields) {
        long canonical=positive(fields.get("canonicalOriginalId")),id=positive(fields.get("id"));
        String member=text(fields.get("member_id"));long memberId=member==null?0:positive(member);
        return String.format(Locale.ROOT,"%020d:%020d:%d:%020d",canonical,memberId,id==canonical?0:1,id);
    }
    static String canonicalBindingKey(Row row) {
        var f=new HashMap<>(row.fields());f.put("id",positive(f.get("canonicalOriginalId")));
        return orderedSourceKey(f);
    }
    private static long positive(Object value) { return SyncFieldMapper.primaryKey(text(value)); }
    private static Map<String,Canonical> resolveCompleteGroups(List<Row> rows) {
        var buckets=new LinkedHashMap<List<String>,List<Row>>();
        for(var row:rows) {
            var f=row.fields();
            var key=Arrays.asList(text(f.get("contractNo")),text(f.get("projectGroupCode")),text(f.get("member_id")),
                    text(f.get("createBy")),text(f.get("updateTime")),text(f.get("updateBy")));
            buckets.computeIfAbsent(key,k->new ArrayList<>()).add(row);
        }
        var result=new HashMap<String,Canonical>();
        for(var members:buckets.values()) {
            var first=members.getFirst();var ids=new HashSet<String>();
            String expected=text(first.fields().get(DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE));
            if(expected==null || !expected.matches("[1-9][0-9]*") || Long.parseLong(expected)!=members.size())
                fail("完整原表分组与本批数量不一致，禁止部分记录选取最早时间");
            for(var row:members) {
                if(!ids.add(text(row.fields().get("id"))) || !expected.equals(text(row.fields().get(DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE))))
                    fail("同组原身份重复或完整分组数量不同");
                for(String name:DppmsProjectContractRelationSyncTemplate.columns()) {
                    if(List.of("id","createTime").contains(name)) continue;
                    if(!Objects.equals(text(first.fields().get(name)),text(row.fields().get(name)))) fail("原五字段、项目或完整桥关系不一致");
                }
                for(String name:List.of("contractId","expectedProjectVersion"))
                    if(!Objects.equals(text(first.fields().get(name)),text(row.fields().get(name)))) fail("正式合同或项目版本不一致");
                if(members.size()>1 && row.fields().get("reviewedBefore")!=null) fail("通用合并规则不授权接管现存正式关系");
                if(row.fields().get("createTime")==null) fail("原创建日期缺失，不能选取最早历史日期");
            }
            if(members.size()==1) continue;
            var canonical=members.stream().min(Comparator.<Row,LocalDateTime>comparing(row->SyncFieldMapper.sourceTime(row.fields().get("createTime")))
                    .thenComparingLong(row->Long.parseLong(text(row.fields().get("id"))))).orElseThrow();
            var date=SyncFieldMapper.sourceTime(canonical.fields().get("createTime"));
            boolean identical=members.stream().allMatch(row->date.equals(SyncFieldMapper.sourceTime(row.fields().get("createTime"))));
            var decision=new Canonical(canonical.sourceKey(),date,identical);
            members.forEach(row->result.put(row.sourceKey(),decision));
        }
        return result;
    }
    private static String text(Object value) { return value==null?null:value.toString(); }
    private static void fail(String message) { throw new IllegalArgumentException("DPPMS合同重复裁决："+message); }
}
