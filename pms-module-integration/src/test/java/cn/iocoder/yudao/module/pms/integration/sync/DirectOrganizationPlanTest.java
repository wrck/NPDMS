package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.Node;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DirectOrganizationPlanTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026,10,16,12,0), OLD = NOW.minusYears(10);
    static String json(Object value) { return new tools.jackson.databind.json.JsonMapper().writeValueAsString(value); }
    static Row row(String object,String id,String code,String name,String parent) {
        boolean company = "COMPANY".equals(object), fnd = "fnd_department".equals(object);
        boolean completion = "ehr_department".equals(object), history = fnd || completion && Set.of("167010","167020","167050").contains(code);
        Map<String,Object> raw = new LinkedHashMap<>();
        (company ? List.of("compID","compCode","compName","isDisabled") : fnd ? DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS : DppmsEhrDepartmentCompletionTemplate.ORIGINAL_COLUMNS).forEach(field -> raw.put(field,null));
        raw.put(company ? "compID" : fnd ? "id" : "depID",id);
        raw.put(company ? "compCode" : fnd ? "departmentNum" : "depCode",company ? " " + code + " " : code);
        raw.put(company ? "compName" : fnd ? "departmentName" : "depName",name);
        raw.put(company || !fnd ? "isDisabled" : "status",fnd ? 1 : 0);
        if (!company && !fnd) raw.put("depGrade","1");
        if (fnd) { raw.put("createTime",OLD.toString()); raw.put("updateTime",OLD.toString()); }
        var fields = new LinkedHashMap<String,Object>();
        fields.put("code",code); fields.put("name",name); fields.put("status",0); fields.put("sourceId",id);
        fields.put("sourceTable",company ? "ehr_company" : fnd ? "fnd_department" : "ehr_department");
        fields.put("parentCode",parent); fields.put("auditRule",history ? "FND_HISTORY" : "OWNER_INGEST_TIME");
        if (history) { fields.put("createTime",OLD.toString()); fields.put("updateTime",OLD.toString()); }
        var alias = new LinkedHashMap<String,Object>(); alias.put("id",id); alias.put("table",fields.get("sourceTable")); alias.put("rawFields",raw);
        if (!company && !fnd) alias.put("component",completion ? "EHR_COMPLETION" : "EHR_BASE");
        if (completion) {
            Map<String,Object> reference = new LinkedHashMap<>();
            DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS.forEach(field -> reference.put(field,null));
            if (history) { reference.put("id",id); reference.put("departmentNum",code); reference.put("departmentName",name); reference.put("status",1); reference.put("createTime",OLD.toString()); reference.put("updateTime",OLD.toString()); }
            alias.put("fndId",history ? id : null); alias.put("fndRawFields",reference);
        }
        int rank = company || "DEPARTMENT".equals(object) ? 0 : completion ? 1 : 2;
        return new Row(object,id,Map.of("objectName",company ? "COMPANY" : "DEPARTMENT","businessKey",HexFormat.of().withUpperCase().formatHex(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "sortRank",rank,"canonicalFields",json(fields),"sourceAlias",json(alias)),null);
    }
    static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(row("COMPANY","1","001","公司",null));
        rows.add(row("DEPARTMENT","10","160000","父部门",null));
        rows.add(row("fnd_department","20","163130","FND部门","160000"));
        int id = 30;
        for (String code : DppmsEhrDepartmentCompletionTemplate.CODES) rows.add(row("ehr_department",Integer.toString(id++),code,"补齐" + code,"160000"));
        rows.add(new Row("COMPANY_REFERENCE","90",Map.of("sourceCompanyId","90","sourceCompanyCode","01"),null));
        return rows;
    }
    static Batch batch(List<Row> rows,List<Binding> bindings) { return new Batch("integration:1:9",rows,bindings,true,"RETAIN",false,"UPSERT",false); }
    static DirectOrganizationPlan.Result plan(List<Row> rows,List<Node> catalogue,List<Binding> bindings,boolean apply) {
        AtomicLong ids = new AtomicLong(1000);
        return DirectOrganizationPlan.build(new DirectOrganizationPlan.Input(batch(rows,bindings),catalogue,Map.of(),"9",NOW,apply ? ids::incrementAndGet : null));
    }
    @Test void previewDoesNotAllocateAndApplyResolvesParentsAndHistoricalAudit() {
        var preview = plan(rows(),List.of(),List.of(),false);
        assertTrue(preview.changes().stream().allMatch(c -> c.targetId() == null));
        var result = plan(rows(),List.of(),List.of(),true);
        assertEquals(11,result.writes().size());
        var parent = result.writes().stream().map(DirectOrganizationPlan.Mutation::after).filter(n -> n.code().equals("160000")).findFirst().orElseThrow();
        var fnd = result.writes().stream().map(DirectOrganizationPlan.Mutation::after).filter(n -> n.code().equals("163130")).findFirst().orElseThrow();
        assertEquals(parent.id(),fnd.parentId()); assertEquals(OLD,fnd.createTime()); assertNull(fnd.creator());
        assertEquals("001",result.changes().getLast().after().get("companyCode"));
    }
    @Test void replayIsIdempotentAndEditedTargetIsRejected() {
        var first = plan(rows(),List.of(),List.of(),true);
        List<Node> catalogue = first.writes().stream().map(DirectOrganizationPlan.Mutation::after).toList();
        List<Binding> bindings = first.changes().stream().map(c -> new Binding(c.object(),c.sourceKey(),c.targetId(),c.after())).toList();
        assertTrue(plan(rows(),catalogue,bindings,true).writes().isEmpty());
        var edited = new ArrayList<>(catalogue); Node n = edited.getFirst();
        edited.set(0,new Node(n.id(),n.type(),n.code(),"人工维护",n.parentId(),n.sort(),n.leaderUserId(),n.phone(),n.email(),n.status(),n.version()+1,n.creator(),n.createTime(),n.updater(),n.updateTime()));
        assertThrows(IllegalArgumentException.class,() -> plan(rows(),edited,bindings,true));
    }
    @Test void existingDefinitionsAreReferencedAndAliasesCannotBeRebound() {
        Node formal = new Node(77L,"COMPANY","001","现有公司",null,null,null,null,null,1,7,"old",OLD,"old",OLD);
        var result = plan(rows(),List.of(formal),List.of(),true);
        assertEquals(10,result.writes().size()); assertEquals(77L,result.changes().getFirst().targetId());
        assertThrows(IllegalArgumentException.class,() -> plan(rows(),List.of(formal),List.of(new Binding("COMPANY","1",78L,Map.of())),true));
    }
    @Test void parentCyclesMissingParentsAndMissingApprovedRowsFailBeforeAnyWrites() {
        var missing = rows(); missing.removeIf(r -> r.object().equals("DEPARTMENT"));
        assertThrows(IllegalArgumentException.class,() -> plan(missing,List.of(),List.of(),true));
        var cycle = rows(); cycle.set(1,row("DEPARTMENT","10","160000","父部门","160000"));
        assertThrows(IllegalArgumentException.class,() -> plan(cycle,List.of(),List.of(),true));
        var incomplete = rows(); incomplete.removeIf(r -> r.object().equals("ehr_department") && r.sourceKey().equals("30"));
        assertThrows(IllegalArgumentException.class,() -> plan(incomplete,List.of(),List.of(),true));
    }
    @Test void sourceOrderDoesNotChangeBusinessPrecedenceAndAliasesShareOneTarget() {
        var rows = rows(); rows.add(row("DEPARTMENT","50","167010","EHR基础主定义","160000"));
        Collections.reverse(rows);
        var plan = plan(rows,List.of(),List.of(),true);
        var changes = plan.changes().stream().filter(c -> "167010".equals(c.after().get("code"))).toList();
        assertEquals(2,changes.size()); assertEquals(changes.getFirst().targetId(),changes.getLast().targetId());
        assertEquals(1,plan.writes().stream().filter(w -> "167010".equals(w.after().code())).count());
        assertEquals("EHR基础主定义",plan.writes().stream().filter(w -> "167010".equals(w.after().code())).findFirst().orElseThrow().after().name());
    }
    static Row director(Row row,String employee) {
        var fields = new LinkedHashMap<>(row.fields());
        var canonical = DirectOrganizationPlan.json(fields.get("canonicalFields")); canonical.put("rawDirector",employee);
        var alias = DirectOrganizationPlan.json(fields.get("sourceAlias")); var raw = DirectOrganizationPlan.json(alias.get("rawFields")); raw.put("director",employee); alias.put("rawFields",raw);
        fields.put("canonicalFields",json(canonical)); fields.put("sourceAlias",json(alias));
        return new Row(row.object(),row.sourceKey(),fields,row.targetId());
    }
    @Test void leaderCanBeCompletedOnReplayButNotAssignedToAnExistingReference() {
        var rows = rows();
        int index = java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).object().equals("ehr_department")).findFirst().orElseThrow();
        rows.set(index,director(rows.get(index),"293"));
        var first = plan(rows,List.of(),List.of(),true);
        var catalogue = first.writes().stream().map(DirectOrganizationPlan.Mutation::after).toList();
        var bindings = first.changes().stream().map(c -> new Binding(c.object(),c.sourceKey(),c.targetId(),c.after())).toList();
        var completed = DirectOrganizationPlan.build(new DirectOrganizationPlan.Input(batch(rows,bindings),catalogue,Map.of("293",88L),"9",NOW.plusHours(1),() -> 9999));
        assertEquals(1,completed.writes().size()); assertEquals(88L,completed.writes().getFirst().after().leaderUserId());
        assertEquals(completed.writes().getFirst().before().createTime(),completed.writes().getFirst().after().createTime());
        assertThrows(IllegalArgumentException.class,() -> DirectOrganizationPlan.build(new DirectOrganizationPlan.Input(batch(rows,List.of()),catalogue,Map.of("293",88L),"9",NOW,() -> 9999)));
    }
    @Test void excludedFieldsAndTamperedCanonicalDefinitionFailEvenOnNonWinningRows() {
        var rows = rows(); int index = 3; Row row = rows.get(index);
        var fields = new LinkedHashMap<>(row.fields());
        var alias = DirectOrganizationPlan.json(fields.get("sourceAlias")); var raw = DirectOrganizationPlan.json(alias.get("rawFields")); raw.put("depProperty","新增业务值"); alias.put("rawFields",raw);
        fields.put("sourceAlias",json(alias)); rows.set(index,new Row(row.object(),row.sourceKey(),fields,null));
        assertThrows(IllegalArgumentException.class,() -> plan(rows,List.of(),List.of(),true));
        var changed = rows(); var first = changed.getFirst(); fields = new LinkedHashMap<>(first.fields());
        var canonical = DirectOrganizationPlan.json(fields.get("canonicalFields")); canonical.put("name","未经映射的名称");fields.put("canonicalFields",json(canonical));
        changed.set(0,new Row(first.object(),first.sourceKey(),fields,null));
        assertThrows(IllegalArgumentException.class,() -> plan(changed,List.of(),List.of(),true));
    }
    @Test void templateUsesNativeIdsAndKeepsOriginalTemplateIndependent() {
        var template = DppmsDirectOrganizationTemplate.create(18L);
        var fnd=template.sources().stream().filter(s->"fnd_department".equals(s.object())).findFirst().orElseThrow();
        assertEquals(new HashSet<>(DppmsDirectOrganizationTemplate.FND_CODES),new HashSet<>(fnd.parameters().entrySet().stream()
                .filter(e->e.getKey().startsWith("P02_departmentCode")).map(e->e.getValue().toString()).toList()));
        assertEquals(29,DppmsDirectOrganizationTemplate.FND_CODES.size());
        for(int i=0;i<29;i++)assertTrue(fnd.sql().contains(":P02_departmentCode"+i));
        var adapter = new DppmsDirectOrganizationTask(null,null,null,null,null,null);
        assertDoesNotThrow(() -> new SyncDefinitionValidator(List.of(adapter)).validate(template));
        assertTrue(template.sources().stream().allMatch(s -> s.rowProjection() == null && s.queryRef() == null && s.updatedAt() == null));
        assertTrue(template.sources().stream().limit(4).allMatch(s -> s.sourceKey().equals("sourceId") && !s.sql().contains("GROUP BY")));
        assertEquals("DPPMS_ORGANIZATION",DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P01",18L)).adapter());
    }
    @Test void allReviewedFndDepartmentsKeepExactParentsAndEhrDisabledExceptions() {
        var all=rows();all.add(row("DEPARTMENT","900","310000","31父级",null));all.add(row("DEPARTMENT","901","280000","28父级",null));
        int id=100;
        for(String code:DppmsDepartmentSyncTemplate.APPROVED_MISSING_CODES) {
            Row original=row("fnd_department",Integer.toString(id++),code,"补齐"+code,DppmsDepartmentCodeRules.parentForNew(code));
            if(Set.of("160700002","310500001").contains(code)) {
                var fields=new LinkedHashMap<>(original.fields());var canonical=DirectOrganizationPlan.json(fields.get("canonicalFields"));
                canonical.put("status",1);canonical.put("ehrDepartmentId","160700002".equals(code)?541:391);canonical.put("ehrDisabled",1);
                fields.put("canonicalFields",json(canonical));original=new Row(original.object(),original.sourceKey(),fields,null);
            }
            all.add(original);
        }
        var result=plan(all,List.of(),List.of(),true);
        var nodes=new HashMap<String,Node>();result.writes().forEach(write->nodes.put(write.after().code(),write.after()));
        assertEquals(29,result.changes().stream().filter(change->"fnd_department".equals(change.object())).count());
        assertEquals(nodes.get("310000").id(),nodes.get("310500001001").parentId());
        assertEquals(nodes.get("280000").id(),nodes.get("280700").parentId());
        assertEquals(0L,nodes.get("999999").parentId());
        assertEquals(1,nodes.get("310500001").status());assertEquals(1,nodes.get("160700002").status());
        assertEquals(OLD,nodes.get("280700").createTime());assertNull(nodes.get("280700").creator());
        Row bad=all.stream().filter(r->"fnd_department".equals(r.object()) && "310500001".equals(DirectOrganizationPlan.text(DirectOrganizationPlan.json(r.fields().get("canonicalFields")),"code"))).findFirst().orElseThrow();
        var fields=new LinkedHashMap<>(bad.fields());var canonical=DirectOrganizationPlan.json(fields.get("canonicalFields"));canonical.put("ehrDepartmentId",392);fields.put("canonicalFields",json(canonical));
        var changed=new ArrayList<>(all);changed.set(changed.indexOf(bad),new Row(bad.object(),bad.sourceKey(),fields,null));
        assertThrows(IllegalArgumentException.class,()->plan(changed,List.of(),List.of(),true));
    }
}
