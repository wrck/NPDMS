package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsProjectContractDuplicateDecisionTest {
    @Test void all39IdenticalOriginalsKeepSameAuditAndStableTechnicalIdentityRegardlessOfOrder() throws Exception {
        try(var input=getClass().getResourceAsStream("/migration/approved-contract-identical-originals.json")) {
            assertNotNull(input);
            var raw=JsonUtils.parseArray(new String(input.readAllBytes(),StandardCharsets.UTF_8),Map.class);
            var rows=new ArrayList<Row>();var canonical=new HashMap<String,Long>();
            for(var source:raw) {
                var fields=new LinkedHashMap<String,Object>();DppmsProjectContractRelationSyncTemplate.columns().forEach(name->fields.put(name,null));
                fields.putAll(source);fields.put("member_id",200);
                rows.add(new Row("pm_project_contract",source.get("id")+":200",fields,null));
                canonical.merge(source.get("contractNo")+":"+source.get("projectGroupCode"),Long.parseLong(source.get("id").toString()),Math::min);
            }
            Collections.reverse(rows);
            var result=DppmsProjectContractDuplicateDecision.resolve(complete(rows));
            assertEquals(39,result.size());assertEquals(18,result.values().stream().map(DppmsProjectContractDuplicateDecision.Canonical::sourceKey).distinct().count());
            for(var row:rows) {
                var resolved=result.get(row.sourceKey());assertTrue(resolved.identical());
                assertEquals(canonical.get(row.fields().get("contractNo")+":"+row.fields().get("projectGroupCode"))+":200",resolved.sourceKey());
                assertEquals(SyncFieldMapper.sourceTime(row.fields().get("createTime")),resolved.createdAt());
            }
            var anotherProject=new ArrayList<Row>();
            for(var row:rows) if(Set.of("9745","9746").contains(row.fields().get("id").toString())) {
                var fields=new LinkedHashMap<>(row.fields());fields.put("member_id",201);fields.put("originalProjectId",5000);
                anotherProject.add(new Row("pm_project_contract",fields.get("id")+":201",fields,null));
            }
            rows.addAll(anotherProject);var expanded=DppmsProjectContractDuplicateDecision.resolve(complete(rows));
            assertEquals(41,expanded.size());assertEquals(19,expanded.values().stream().map(DppmsProjectContractDuplicateDecision.Canonical::sourceKey).distinct().count());
            assertEquals("9745:201",expanded.get("9746:201").sourceKey());assertEquals("9745:200",expanded.get("9746:200").sourceKey());
        }
    }
    @Test void all37ActualOriginalRecordsResolveToExactly14EarliestRecords() throws Exception {
        try(var input=getClass().getResourceAsStream("/migration/approved-contract-duplicate-originals.json")) {
            assertNotNull(input);
            var raw=JsonUtils.parseArray(new String(input.readAllBytes(),StandardCharsets.UTF_8),Map.class);
            var rows=new ArrayList<Row>();var earliest=new HashMap<String,LocalDateTime>();
            for(var source:raw) {
                var fields=new LinkedHashMap<String,Object>();DppmsProjectContractRelationSyncTemplate.columns().forEach(name->fields.put(name,null));
                fields.putAll(source);fields.put("member_id",200);
                rows.add(new Row("pm_project_contract",source.get("id")+":200",fields,null));
                String key=source.get("contractNo")+":"+source.get("projectGroupCode");
                var date=SyncFieldMapper.sourceTime(source.get("createTime"));
                earliest.merge(key,date,(left,right)->left.isBefore(right)?left:right);
            }
            var result=DppmsProjectContractDuplicateDecision.resolve(complete(rows));
            assertEquals(37,result.size());assertEquals(14,result.values().stream().map(DppmsProjectContractDuplicateDecision.Canonical::sourceKey).distinct().count());
            for(var row:rows) assertEquals(earliest.get(row.fields().get("contractNo")+":"+row.fields().get("projectGroupCode")),result.get(row.sourceKey()).createdAt());
        }
    }
    @Test void unsupportedPartialOldDefinitionsCannotInventAGeneralApproval() {
        var rows=new ArrayList<Row>();
        for(String id:List.of("44210","45535","90000","90001")) {
            var fields=new HashMap<String,Object>();fields.put("id",id);fields.put("contractNo",id.startsWith("9")?"C1":"16030021090911N");
            fields.put("projectGroupCode",id.startsWith("9")?"new-group":"prj_gp_afxx43568");
            fields.put("createTime","2021-09-13 10:20:19");fields.put("member_id",200);
            rows.add(new Row("pm_project_contract",id+":200",fields,null));
        }
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(rows));
    }

    List<Row> complete(List<Row> rows) {
        for(var row:rows) {
            var f=row.fields();
            if(f.containsKey("duplicateGroupSize")) continue;
            long count=rows.stream().filter(other -> List.of("contractNo","projectGroupCode","member_id","createBy","updateTime","updateBy")
                    .stream().allMatch(name -> Objects.equals(f.get(name),other.fields().get(name))))
                    .map(other -> other.fields().get("id").toString()).distinct().count();
            f.put("duplicateGroupSize",count);
        }
        return rows;
    }
    List<Row> generalRows() {
        var result=new ArrayList<Row>();
        for(int i=0;i<3;i++) {
            var fields=new LinkedHashMap<String,Object>();DppmsProjectContractRelationSyncTemplate.columns().forEach(name->fields.put(name,null));
            fields.put("id",90000+i);fields.put("contractNo","NEW-C");fields.put("projectGroupCode","new-group");fields.put("member_id",200);
            fields.put("createBy","creator");fields.put("createTime",i==1?"2019-02-01 09:00:00":"2021-11-01 09:00:00");
            fields.put(DppmsProjectContractRelationSyncTemplate.DUPLICATE_GROUP_SIZE,3);
            result.add(new Row("pm_project_contract",(90000+i)+":200",fields,null));
        }
        return result;
    }
    @Test void generalApprovalUsesEarliestDateRatherThanMinimumIdAndNeedsCompleteOriginalGroup() {
        var rows=generalRows();Collections.reverse(rows);var result=DppmsProjectContractDuplicateDecision.resolve(rows);
        assertEquals(3,result.size());assertEquals(Set.of("90001:200"),new HashSet<>(result.values().stream().map(DppmsProjectContractDuplicateDecision.Canonical::sourceKey).toList()));
        assertTrue(result.values().stream().allMatch(c->c.createdAt().equals(LocalDateTime.parse("2019-02-01T09:00:00")) && !c.identical()));
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(rows.subList(0,2)));
        rows.getFirst().fields().put("member_projectCode","other-project");
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(rows));
    }
    @Test void differentOriginalActorsOrUpdateFactsAreNotTheSameCaseAndCannotBeSilentlyMerged() {
        for(String field:List.of("createBy","updateBy","updateTime","projectGroupCode","contractId","expectedProjectVersion","reviewedBefore")) {
            var rows=generalRows();rows.getFirst().fields().put(field,"changed");
            assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(rows),field);
        }
        var rows=generalRows();rows.getFirst().fields().put("createTime",null);
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(rows));
    }
    @Test void all81FreshOriginalsApplyTheUniversalRuleTo34CompleteGroupsIncludingCrossMonthDates() throws Exception {
        try(var input=getClass().getResourceAsStream("/migration/approved-contract-general-duplicate-bridges.json")) {
            assertNotNull(input);var raw=JsonUtils.parseArray(new String(input.readAllBytes(),StandardCharsets.UTF_8),Map.class);
            var rows=new ArrayList<Row>();
            for(var f:raw) rows.add(new Row("pm_project_contract",f.get("id")+":"+(f.get("member_id")==null?0:f.get("member_id")),f,null));
            assertEquals(81,rows.size());var resolved=DppmsProjectContractDuplicateDecision.resolve(rows);
            assertEquals(81,resolved.size());assertEquals(34,resolved.values().stream().map(DppmsProjectContractDuplicateDecision.Canonical::sourceKey).distinct().count());
            assertEquals("44210:43427",resolved.get("45538:43427").sourceKey());
            assertEquals(LocalDateTime.parse("2021-09-13T10:20:19"),resolved.get("45535:43427").createdAt());
            assertEquals("42247:41526",resolved.get("45177:41526").sourceKey());
            assertEquals(LocalDateTime.parse("2021-06-09T13:45:46"),resolved.get("45177:41526").createdAt());
            assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(rows.subList(1,rows.size())));
        }
    }
}
