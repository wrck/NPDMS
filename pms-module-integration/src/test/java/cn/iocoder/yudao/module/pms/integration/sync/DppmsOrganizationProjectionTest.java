package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsOrganizationProjectionTest {
    final SyncDefinition definition=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P01",18L));
    private Map<String,Object> company(String id,String name){return Map.of("table","ehr_company","id",id,
            "rawFields",Map.of("compID",id,"compCode"," 01 ","compName",name,"isDisabled",0));}
    private Map<String,Object> group(String winner){return Map.of("objectName","COMPANY","businessKey","3031","canonicalFields",
            Map.of("sourceId",winner,"sourceTable","ehr_company","code","01","name","first","status",0),
            "sourceAliases",List.of(company("7","second"),company("8","first")));}
    private List<MysqlSyncReader.SourceRows> expand(Map<String,Object> group){return SyncSourceProjection.expand(
            new SyncSourceProjection.Input(definition,definition.sources().getFirst(),List.of(group)));}
    @Test void sqlSelectedWholeRowWinsIndependentlyOfAliasArrayOrderAndCompanyMappingStaysTrim(){
        var expanded=expand(group("8"));var companies=expanded.getFirst().rows();
        assertEquals(2,companies.size());assertEquals("8",companies.getFirst().get("_organizationWinnerKey"));
        assertEquals(" 01 ",companies.getFirst().get("compCode"));
        var mapped=new SyncFieldMapper().transform(definition,new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),expanded,0),List.of());
        assertEquals("01",mapped.getFirst().fields().get("code"));
        var batch=new Batch("integration:1:12",mapped,List.of(),true,"RETAIN",false,"UPSERT",false);
        var canonical=DppmsOrganizationCanonical.from(batch);assertNotNull(canonical);
        var winners=canonical.ehrBatch(batch);assertEquals(1,winners.rows().size());assertEquals("8",winners.rows().getFirst().sourceKey());
        assertEquals("first",winners.rows().getFirst().fields().get("name"));
        assertFalse(winners.rows().getFirst().fields().containsKey("_sourcePrimaryKey"));
    }
    @Test void missingWinningAliasWrongBinaryCodeAndDuplicateOriginalIdentityBlock(){
        assertThrows(IllegalArgumentException.class,()->expand(group("9")));
        var wrong=new LinkedHashMap<>(group("8"));wrong.put("businessKey","303031");assertThrows(IllegalArgumentException.class,()->expand(wrong));
        var duplicate=new LinkedHashMap<>(group("8"));duplicate.put("sourceAliases",List.of(company("8","first"),company("8","first")));
        assertThrows(IllegalArgumentException.class,()->expand(duplicate));
    }
    @Test void referenceNormalizationDoesNotRewriteOriginalCompanyForeignKeyEvidence(){
        var raw=new LinkedHashMap<String,Object>();DppmsEhrDepartmentCompletionTemplate.ORIGINAL_COLUMNS.forEach(field->raw.put(field,null));
        raw.putAll(Map.of("depID","10","depCode","160000","depName","原部门","isDisabled",0,"compID","7","adminID",0,"depGrade","1"));
        var department=Map.<String,Object>of("objectName","DEPARTMENT","businessKey","313630303030","canonicalFields",
                Map.of("sourceId","10","sourceTable","ehr_department","code","160000","name","原部门","status",0),
                "sourceAliases",List.of(Map.of("table","ehr_department","id","10","component","EHR_BASE","rawFields",raw)));
        var expanded=SyncSourceProjection.expand(new SyncSourceProjection.Input(definition,definition.sources().getFirst(),List.of(group("8"),department)));
        var original=expanded.stream().filter(source->"DEPARTMENT".equals(source.object())).findFirst().orElseThrow().rows().getFirst();
        assertEquals("7",original.get("compID"));assertEquals("8",original.get("_organizationCompanyKey"));
        var mapped=new SyncFieldMapper().transform(definition,new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),expanded,0),List.of());
        var batch=new Batch("integration:1:12",mapped,List.of(),true,"RETAIN",false,"UPSERT",false);
        var ownerRows=DppmsOrganizationCanonical.from(batch).ehrBatch(batch).rows();
        assertEquals("8",ownerRows.stream().filter(row->"DEPARTMENT".equals(row.object())).findFirst().orElseThrow().fields().get("companyKey"));
        assertEquals("7",original.get("compID"));
    }
    private Map<String,Object> metadata(String winnerObject,String winnerKey){var fields=new LinkedHashMap<String,Object>();DppmsOrganizationProjection.METADATA.forEach(field->fields.put(field,null));
        fields.put("_organizationWinnerObject",winnerObject);fields.put("_organizationWinnerKey",winnerKey);fields.put("_organizationBusinessCode","167010");return fields;}
    private Batch departments(Long oldTarget){
        var base=metadata("DEPARTMENT","1");base.put("code","167010");base.put("name","EHR主定义");
        var completion=metadata("DEPARTMENT","1");completion.put("depCode","167010");completion.put("director",293);
        return new Batch("integration:1:12",List.of(new Row("DEPARTMENT","1",base,77L),new Row("ehr_department","1",completion,oldTarget)),
                List.of(),true,"RETAIN",false,"UPSERT",false);
    }
    @Test void aliasesUseOwnersActualIdentityAndKeepPendingDirectorWithoutAllocatingIds(){
        var ownerNode=new ManagedOrganizationApi.Node(null,new DeptDO().setId(77L).setCode("167010").setName("EHR主定义").setStatus(0).setSort(1).setParentId(0L),"integration:1:12");
        var receipt=new Change("DEPARTMENT","1",77L,"UNCHANGED",Map.of(),Map.of("code","167010"),null);
        var changes=DppmsOrganizationCanonical.from(departments(null)).aliases(new DppmsOrganizationCanonical.Receipts(List.of(receipt),List.of(new ManagedOrganizationApi.Result("DEPARTMENT:1",ownerNode,ownerNode,"UNCHANGED"))));
        assertEquals(2,changes.size());assertEquals(77L,changes.getLast().targetId());assertEquals("REFERENCED",changes.getLast().action());
        assertEquals(293,changes.getLast().after().get("_directorSourceEmployeeId"));assertEquals(true,changes.getLast().after().get("_referenceOnly"));
        assertThrows(IllegalArgumentException.class,()->DppmsOrganizationCanonical.from(departments(78L)).aliases(new DppmsOrganizationCanonical.Receipts(List.of(receipt),List.of())));
        var previewNode=new ManagedOrganizationApi.Node(null,new DeptDO().setCode("167010").setName("EHR主定义").setStatus(0).setSort(1).setParentId(0L),"integration:1:12");
        var batch=departments(null);var rows=batch.rows().stream().map(row->new Row(row.object(),row.sourceKey(),row.fields(),null)).toList();
        var previewBatch=new Batch(batch.owner(),rows,List.of(),true,"RETAIN",false,"UPSERT",false);
        var preview=DppmsOrganizationCanonical.from(previewBatch).aliases(new DppmsOrganizationCanonical.Receipts(List.of(new Change("DEPARTMENT","1",null,"CREATED",Map.of(),Map.of(),null)),
                List.of(new ManagedOrganizationApi.Result("DEPARTMENT:1",null,previewNode,"CREATED"))));
        assertTrue(preview.stream().allMatch(change->change.targetId()==null));
    }
    @Test void aliasesCannotHideAnEditedHistoricallyCreatedDepartment(){
        var batch=departments(77L);
        var historical=new cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.Department(77L,"167010","旧创建定义",0L,0,null,null,null,0,0,null,null,null,null);
        var bound=new Batch(batch.owner(),batch.rows(),List.of(new Binding("ehr_department","1",77L,Map.of("department",historical,"_referenceOnly",false))),true,"RETAIN",false,"UPSERT",false);
        var current=new ManagedOrganizationApi.Node(null,new DeptDO().setId(77L).setCode("167010").setName("后来维护定义").setStatus(0).setSort(1).setParentId(0L),"integration:1:12");
        assertThrows(IllegalArgumentException.class,()->DppmsOrganizationCanonical.from(bound).aliases(new DppmsOrganizationCanonical.Receipts(
                List.of(new Change("DEPARTMENT","1",77L,"UNCHANGED",Map.of(),Map.of(),null)),
                List.of(new ManagedOrganizationApi.Result("DEPARTMENT:1",current,current,"UNCHANGED")))));
    }
}
