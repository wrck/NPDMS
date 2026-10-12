package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectContractRelationSyncTemplate.*;

class DppmsProjectContractRelationSyncAdapterTest {
    private static SyncBindingDO projectBinding(String key,Long id,String type,String code) {
        var original=new LinkedHashMap<String,Object>();DppmsProjectMasterSyncTemplate.ORIGINAL_COLUMNS.forEach(name->original.put(name,tools.jackson.databind.node.NullNode.getInstance()));
        original.put("projectId",key);original.put("projectType",type);original.put("projectCode",code);
        return new SyncBindingDO().setSourceKey(key).setTargetId(id).setFieldsJson(JsonUtils.toJsonString(Map.of(
                "originalFields",original,"project",Map.of("id",id,"tenantId",1L,"projectType",type,"projectCode",code))));
    }
    final ProjectContractRelationMigrationApi relations=mock(ProjectContractRelationMigrationApi.class);
    final SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final DppmsProjectContractRelationSyncAdapter adapter=new DppmsProjectContractRelationSyncAdapter(relations,users,tasks,runs,bindings,new DppmsProjectBindingApiImpl(bindings));
    SyncRunDO run;
    SyncTaskDO task;
    final LocalDateTime old=LocalDateTime.parse("2022-12-01T10:05:40");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setAdapter(ADAPTER).setSourceSystem("DPPMS").setConnectionId(18L)
                .setDefinition(JsonUtils.toJsonString(create(18L).readStrategy("SNAPSHOT")));task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);run.setTenantId(1L);run.setCreator("1");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        doAnswer(call->{var query=(ReferenceQuery)call.getArgument(0);
            return query.references().stream().map(ref->new ResolvedReference(ref.sourceKey(),ref.projectId(),0L,11L)).toList();}).when(relations).resolveReferences(any());
        var binding=projectBinding("50370",10L,"10","P1");
        when(bindings.selectIdentitySources(argThat(q->q!=null && "DPPMS_PROJECT_MASTER".equals(q.adapter())))).thenReturn(List.of(binding));
        when(relations.apply(any())).thenAnswer(call->{Request request=call.getArgument(0);var e=request.entries().getFirst();var x=e.incoming();
            return List.of(new Result(e.sourceKey(),"CREATED",null,new Relation(30L,x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),
                    x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),x.effectiveFrom(),x.effectiveTo(),x.status(),0,x.creator(),x.createTime(),x.updater(),x.updateTime(),false)));});
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Row row() {
        var f=new LinkedHashMap<String,Object>();columns().forEach(name->f.put(name,null));
        f.put("id",100);f.put("member_id",200);f.put("group_id",300);f.put("projectGroupCode","g1");f.put("member_projectGroupCode","g1");
        f.put("group_projectGroupCode","g1");f.put("group_projectType","10");f.put("member_projectCode","P1");f.put("originalProjectCode","P1");f.put("originalProjectId",50370);
        f.put("contractNo","C1");f.put("contractId",11);f.put("createTime","2022-12-01 10:05:40");f.put("expectedProjectVersion",0);
        return new Row(OBJECT,"100:200",f,null);
    }
    Batch batch(Row row,List<Binding> prior,boolean adopt) { return new Batch("integration:1:12",List.of(row),prior,true,"RETAIN",adopt,"UPSERT",false); }
    void verifyNoOwnerMutation() {
        // Formal identity resolution is read-only; rejected input must never reach either mutation plan.
        verify(relations,never()).apply(any());verify(relations,never()).preview(any());
    }
    @Test void assignedProjectCodeComesFromBindingWhileOriginalContractBridgeKeepsOriginalCode() {
        var proof=projectBinding("50370",10L,"10","P1");var fields=JsonUtils.parseMap(proof.getFieldsJson());
        ((Map<String,Object>)fields.get("originalFields")).replaceAll((key,value)->value==null?tools.jackson.databind.node.NullNode.getInstance():value);
        ((Map<String,Object>)fields.get("project")).put("projectCode","P1-1");proof.setFieldsJson(JsonUtils.toJsonString(fields));
        when(bindings.selectIdentitySources(argThat(q->q!=null && "DPPMS_PROJECT_MASTER".equals(q.adapter())))).thenReturn(List.of(proof));
        var result=adapter.apply(batch(row(),List.of(),false)).getFirst();
        verify(relations).apply(argThat(request->"P1-1".equals(request.entries().getFirst().sourceProjectCode())
                && Long.valueOf(18L).equals(request.entries().getFirst().sourceConnectionId())));
        assertEquals("P1",((Map<?,?>)result.after().get("originalFields")).get("originalProjectCode"));
    }
    @Test void templateReadsEveryOriginalAndDoesNotSilentlyExcludeMissingOrHistoricalBridges() {
        var d=create(18L);assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(d));
        assertDoesNotThrow(()->MysqlSyncReader.compile(d.sources().getFirst()));
        assertTrue(d.sources().getFirst().sql().contains("LEFT JOIN pm_project_group_relationship"));
        assertEquals(26,columns().size());assertEquals("relationSourceKey",d.sources().getFirst().sourceKey());
        assertTrue(d.sources().getFirst().mappings().stream().noneMatch(mapping->Set.of("contractId","expectedProjectVersion").contains(mapping.target())));
    }
    @Test void wholeTaskResolvesFormalTargetsWithoutPerSourceContractOrVersionLookups() {
        var row=row();row.fields().remove("contractId");row.fields().remove("expectedProjectVersion");
        doReturn(List.of(new ResolvedReference(row.sourceKey(),10L,7L,11L))).when(relations).resolveReferences(any());
        adapter.apply(batch(row,List.of(),false));
        verify(relations).apply(argThat(request->{var x=request.entries().getFirst();return x.expectedProjectVersion()==7L
                && x.incoming().contractId()==11L && x.incoming().projectId()==10L;}));
    }
    @Test void resolvedTargetIdentityAndExplicitReferencesMustAgreeBeforeOwnerMutation() {
        for(var ref:List.of(new ResolvedReference("100:200",99L,0L,11L),new ResolvedReference("100:200",10L,1L,11L),
                new ResolvedReference("100:200",10L,0L,99L),new ResolvedReference("999:200",10L,0L,11L))) {
            doReturn(List.of(ref)).when(relations).resolveReferences(any());
            assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        }
        doReturn(List.of()).when(relations).resolveReferences(any());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        verify(relations,never()).apply(any());verify(relations,never()).preview(any());
    }
    @Test void repeatedHistoricalSuffixesFollowTheOldListJoinWithoutActivatingCurrentRelation() {
        var row=row();row.fields().put("member_projectCode","P1-his-his-his");
        var change=adapter.apply(batch(row,List.of(),false)).getFirst();
        assertEquals("HISTORICAL",((Map<?,?>)change.after().get("relation")).get("status"));
        assertEquals("P1-his-his-his",((Map<?,?>)change.after().get("originalFields")).get("member_projectCode"));
        verify(relations).apply(argThat(request->request.entries().getFirst().incoming().effectiveTo()==null));
    }
    @Test void preservesNullsRawBridgeValuesAndMapsZeroProjectVersion() {
        var result=adapter.apply(batch(row(),List.of(),false)).getFirst();
        assertEquals(17,((Map<?,?>)result.after().get("relation")).size());assertEquals(26,((Map<?,?>)result.after().get("originalFields")).size());
        verify(relations).apply(argThat(request->{var e=request.entries().getFirst();return e.expectedProjectVersion()==0 && e.incoming().creator()==null
                && e.incoming().updater()==null && e.incoming().createTime().equals(e.incoming().updateTime()) && e.incoming().projectId()==10L;}));
    }
    @Test void exactHistoricalActorIsUsedUnknownActorBlocksInsteadOfDefaultingToAdmin() {
        var row=row();row.fields().put("createBy","x01861");
        when(users.getHistoricalIdentitiesByUsername(1L,Set.of("x01861"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(21L,"x01861")));
        adapter.apply(batch(row,List.of(),false));verify(relations).apply(argThat(r->"21".equals(r.entries().getFirst().incoming().creator())));
        clearInvocations(relations);row.fields().put("createBy","unknown");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));verifyNoOwnerMutation();
    }
    @Test void contractAuditRemainsPrimaryAndDifferentMemberAuditsHaveExplicitDisposition() {
        var row=row();var f=row.fields();
        f.put("createBy","x01861");f.put("member_createBy","not-migrated");
        f.put("updateBy","x01861");f.put("member_updateBy","another-unselected-account");
        f.put("updateTime",old.plusDays(1));f.put("member_updateTime",old.plusDays(2));
        f.put("member_createTime",old.plusMinutes(1));f.put("member_mergeBranchMark","上海节点");
        when(users.getHistoricalIdentitiesByUsername(1L,Set.of("x01861")))
                .thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(21L,"x01861")));
        var change=adapter.apply(batch(row,List.of(),false)).getFirst();
        verify(relations).apply(argThat(request->{var x=request.entries().getFirst().incoming();
            return x.createTime().equals(old) && x.effectiveFrom().equals(old) && x.updateTime().equals(old.plusDays(1))
                    && "21".equals(x.creator()) && "21".equals(x.updater());}));
        var dispositions=(Map<?,?>)change.after().get("fieldDecisions");
        for(String name:List.of("createTime","createBy","updateTime","updateBy"))
            assertTrue(dispositions.get("member_"+name).toString().startsWith("不独立迁入"));
        assertTrue(dispositions.get("member_mergeBranchMark").toString().startsWith("不迁入"));
        assertEquals("上海节点",((Map<?,?>)change.after().get("originalFields")).get("member_mergeBranchMark"));
        assertEquals(17,((Map<?,?>)change.after().get("relation")).size());
    }
    @Test void missingContractAuditUsesMemberFactsWithoutInventingRelationshipEnd() {
        var row=row();var f=row.fields();f.put("member_createBy","x00270");f.put("member_updateBy","x01861");
        f.put("member_updateTime",old.plusDays(2));f.put("createBy","");
        when(users.getHistoricalIdentitiesByUsername(1L,Set.of("x01861","x00270")))
                .thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(21L,"x01861"),
                        new SystemUserMigrationApi.HistoricalIdentity(22L,"x00270")));
        var change=adapter.apply(batch(row,List.of(),false)).getFirst();
        verify(relations).apply(argThat(request->{var x=request.entries().getFirst().incoming();return
                "22".equals(x.creator()) && "21".equals(x.updater()) && old.plusDays(2).equals(x.updateTime()) && x.effectiveTo()==null;}));
        var dispositions=(Map<?,?>)change.after().get("fieldDecisions");
        for(String name:List.of("createBy","updateTime","updateBy"))
            assertEquals("补齐正式同名审计字段",dispositions.get("member_"+name));
    }
    @Test void unknownSelectedMemberActorStillBlocksBeforeOwnerInsteadOfAssigningAdmin() {
        var row=row();row.fields().put("member_createBy","unresolved-account");
        when(users.getHistoricalIdentitiesByUsername(anyLong(),anySet())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));
        verifyNoOwnerMutation();
    }
    @Test void approvedOriginalServiceAuditorsArePreservedAlongsideMemberFallback() {
        var row=row();row.fields().put("createBy","sys");row.fields().put("member_updateBy","异常数据处理");
        var change=adapter.apply(batch(row,List.of(),false)).getFirst();
        verify(relations).apply(argThat(request->{var x=request.entries().getFirst().incoming();
            return "sys".equals(x.creator()) && "异常数据处理".equals(x.updater());}));
        verifyNoInteractions(users);
        assertEquals("异常数据处理",((Map<?,?>)change.after().get("originalFields")).get("member_updateBy"));
    }
    @Test void missingFieldStaleRunAndHistoricalOrUnresolvedBridgeCannotWrite() {
        var missing=row();missing.fields().remove("group_updateBy");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(missing,List.of(),false)));
        var historical=row();historical.fields().put("member_projectCode","OTHER-his");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(historical,List.of(),false)));
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        verifyNoOwnerMutation();
    }
    @Test void repeatRequiresFullSourceEvidenceAndChangedRawAuditIsNotIgnored() {
        var first=adapter.apply(batch(row(),List.of(),false)).getFirst();var proof=JsonUtils.parseMap(JsonUtils.toJsonString(first.after()));
        var raw=row();var bound=new Row(OBJECT,raw.sourceKey(),raw.fields(),30L);
        adapter.apply(batch(bound,List.of(new Binding(OBJECT,raw.sourceKey(),30L,proof)),false));
        raw.fields().put("member_mergeBranchMark","新备注");clearInvocations(relations);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(bound,List.of(new Binding(OBJECT,raw.sourceKey(),30L,proof)),false)));
        verifyNoOwnerMutation();
    }
    @Test void ordinaryReplayUsesSuccessfulBindingInsteadOfOldAdoptionReview() {
        var first=adapter.apply(batch(row(),List.of(),false)).getFirst();
        var proof=JsonUtils.parseMap(JsonUtils.toJsonString(first.after()));
        var raw=row();raw.fields().put("reviewedBefore",proof.get("relation"));
        var bound=new Row(OBJECT,raw.sourceKey(),raw.fields(),30L);
        clearInvocations(relations);
        adapter.apply(batch(bound,List.of(new Binding(OBJECT,raw.sourceKey(),30L,proof)),false));
        verify(relations).apply(argThat(request->!request.adoptExisting()
                && request.entries().getFirst().reviewedBefore()==null && request.entries().getFirst().lastApplied()!=null));
        clearInvocations(relations);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of(),false)));
        verifyNoOwnerMutation();
        adapter.apply(batch(raw,List.of(),true));
        verify(relations).apply(argThat(request->request.adoptExisting() && request.entries().getFirst().reviewedBefore()!=null));
    }
    List<Row> approvedRows() {
        var rows=new ArrayList<Row>();
        for(int id:List.of(11034,11035)) {
            var f=new LinkedHashMap<>(row().fields());f.put("id",id);f.put("contractNo","31020151118I");
            for(String name:List.of("projectGroupCode","member_projectGroupCode","group_projectGroupCode")) f.put(name,"prj_gp8933");
            f.put("createBy","x01861");f.put("createTime",id==11034?"2016-01-05 18:12:21":"2016-01-05 18:12:22");
            f.put(DUPLICATE_GROUP_SIZE,2);
            rows.add(new Row(OBJECT,id+":200",f,null));
        }
        when(users.getHistoricalIdentitiesByUsername(1L,Set.of("x01861"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(21L,"x01861")));
        return rows;
    }
    Batch duplicates(List<Row> rows,List<Binding> prior,boolean adopt) {
        return new Batch("integration:1:12",rows,prior,true,"RETAIN",adopt,"UPSERT",false);
    }
    @Test void approvedDuplicatesCallOwnerOnceButKeepBothOriginalIdentitiesAndTimes() {
        var rows=approvedRows();Collections.reverse(rows);
        var result=adapter.apply(duplicates(rows,List.of(),false));
        assertTrue(adapter.sharesTargetAcrossSources());assertEquals(2,result.size());
        assertEquals(Set.of("11034:200","11035:200"),new HashSet<>(result.stream().map(Change::sourceKey).toList()));
        assertTrue(result.stream().allMatch(change->Objects.equals(change.targetId(),30L)));
        verify(relations).apply(argThat(request->request.entries().size()==1 && request.entries().getFirst().sourceKey().equals("11034:200")
                && request.entries().getFirst().incoming().createTime().equals(LocalDateTime.parse("2016-01-05T18:12:21"))));
        var alias=result.stream().filter(change->change.sourceKey().equals("11035:200")).findFirst().orElseThrow();
        assertEquals("2016-01-05T18:12:22",((Map<?,?>)alias.after().get("originalFields")).get("createTime"));
        assertTrue(alias.message().contains("createTime不独立迁入正式审计：重复登记时间，不构成不同关系事实"));
        assertEquals(26,((Map<?,?>)alias.after().get("originalFields")).size());
    }
    @Test void approvedDuplicatesReplayChecksEveryOriginalAndAllSharedTargetFields() {
        var rows=approvedRows();var first=adapter.apply(duplicates(rows,List.of(),false));
        var proof=first.stream().map(change->new Binding(OBJECT,change.sourceKey(),30L,JsonUtils.parseMap(JsonUtils.toJsonString(change.after())))).toList();
        var bound=rows.stream().map(row->new Row(OBJECT,row.sourceKey(),row.fields(),30L)).toList();
        clearInvocations(relations);adapter.apply(duplicates(bound,proof,false));
        verify(relations).apply(argThat(request->request.entries().size()==1 && request.entries().getFirst().lastApplied()!=null));
        clearInvocations(relations);var changed=new LinkedHashMap<>(proof.getLast().lastFields());
        var target=new LinkedHashMap<>((Map<String,Object>)changed.get("relation"));target.put("version",8);changed.put("relation",target);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(bound,List.of(proof.getFirst(),new Binding(OBJECT,proof.getLast().sourceKey(),30L,changed)),false)));
        verifyNoOwnerMutation();
        rows.getLast().fields().put("createTime","2016-01-05 18:12:23");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(bound,proof,false)));
        verifyNoOwnerMutation();
    }
    @Test void missingSiblingChangedOriginalNewIdAndChangedBridgeAllBlockBeforeOwner() {
        var rows=approvedRows();
        var single=List.of(rows.getFirst());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(single,List.of(),false)));
        for(String field:List.of("createBy","updateBy","member_smsProjectCode","contractId","expectedProjectVersion")) {
            rows=approvedRows();rows.getLast().fields().put(field,field.equals("createTime")?"2016-01-05 18:12:23":"changed");
            var bad=rows;assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(bad,List.of(),false)));
        }
        rows=approvedRows();var extra=new LinkedHashMap<>(rows.getLast().fields());extra.put("id",99999);
        rows.add(new Row(OBJECT,"99999:200",extra,null));var added=rows;
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(added,List.of(),false)));
        verifyNoOwnerMutation();
    }
    @Test void duplicateApprovalDoesNotAuthorizeAdoptingAnyExistingRelationship() {
        var rows=approvedRows();var first=adapter.apply(duplicates(rows,List.of(),false));
        rows.getFirst().fields().put("reviewedBefore",first.getFirst().after().get("relation"));
        clearInvocations(relations);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(rows,List.of(),true)));
        verifyNoOwnerMutation();
    }
    Row streamingRow(long id) {
        var fields=new LinkedHashMap<>(row().fields());fields.put("id",id);
        fields.put("canonicalOriginalId",100L);fields.put("canonicalCreatedAt",old.toString());
        fields.put("duplicateIdentical",false);fields.put(DUPLICATE_GROUP_SIZE,2);
        fields.put("projectCandidateCount",1);
        fields.put("createTime",id==100?old.toString():old.plusSeconds(10).toString());
        return new Row(OBJECT,DppmsProjectContractDuplicateDecision.orderedSourceKey(fields),fields,null);
    }
    @Test void fullTableStreamingKeepsCanonicalBeforeAliasAcrossCommittedChunksWithoutAdoptingExistingData() {
        task.setDefinition(JsonUtils.toJsonString(create(18L)));
        doAnswer(call->{Request request=call.getArgument(0);var entry=request.entries().getFirst();var x=entry.incoming();
            var after=entry.lastApplied()!=null?entry.lastApplied():new Relation(30L,x.tenantId(),x.projectId(),x.contractId(),
                    x.relationRole(),x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),x.effectiveFrom(),x.effectiveTo(),x.status(),
                    x.version(),x.creator(),x.createTime(),x.updater(),x.updateTime(),x.deleted());
            return List.of(new Result(entry.sourceKey(),entry.lastApplied()==null?"CREATED":"UNCHANGED",entry.lastApplied(),after));
        }).when(relations).apply(any());
        var canonical=streamingRow(100);var alias=streamingRow(101);
        assertTrue(canonical.sourceKey().compareTo(alias.sourceKey())<0);
        var first=adapter.apply(duplicates(List.of(canonical),List.of(),false)).getFirst();
        var proof=new SyncBindingDO().setSourceKey(canonical.sourceKey()).setTargetId(first.targetId())
                .setFieldsJson(JsonUtils.toJsonString(first.after()));
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(proof));clearInvocations(relations);
        var second=adapter.apply(duplicates(List.of(alias),List.of(),false)).getFirst();
        assertEquals("UNCHANGED",second.action());assertEquals(first.targetId(),second.targetId());
        assertEquals(first.after().get("relation"),second.after().get("relation"));
        assertEquals(old.plusSeconds(10).toString(),((Map<?,?>)second.after().get("originalFields")).get("createTime"));
        verify(relations).apply(argThat(request->!request.adoptExisting() && request.entries().size()==1
                && request.entries().getFirst().targetId().equals(30L) && request.entries().getFirst().lastApplied()!=null));
        clearInvocations(relations);alias.fields().put("member_smsProjectCode","changed");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(List.of(alias),List.of(),false)));
        verifyNoOwnerMutation();
    }
    @Test void streamingRejectsMissingCanonicalFactsAndForgedOrderingBeforeOwner() {
        task.setDefinition(JsonUtils.toJsonString(create(18L)));
        var legacy=row();assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(legacy,List.of(),false)));
        var canonical=streamingRow(100);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(new Row(OBJECT,"101:200",canonical.fields(),null),List.of(),false)));
        canonical.fields().put("canonicalCreatedAt",old.plusDays(1).toString());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(canonical,List.of(),false)));
        Row ambiguous=streamingRow(100);ambiguous.fields().put("projectCandidateCount",2);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(ambiguous,List.of(),false)));
        verifyNoOwnerMutation();
    }
    List<Row> actualRows(String fixture) throws Exception {
        try(var input=getClass().getResourceAsStream("/migration/"+fixture)) {
            assertNotNull(input);
            var raw=JsonUtils.parseArray(new String(input.readAllBytes(),StandardCharsets.UTF_8),Map.class);
            Map<String,Object> decisions;
            try(var config=getClass().getResourceAsStream("/migration/approved-contract-task-decisions.json")) {
                assertNotNull(config);
                decisions=(Map<String,Object>)JsonUtils.parseMap(new String(config.readAllBytes(),StandardCharsets.UTF_8)).get("migrationDecisionLookup");
            }
            var rows=new ArrayList<Row>();var projectBindings=new LinkedHashMap<String,SyncBindingDO>();
            var contractIds=new LinkedHashMap<String,Long>();
            for(var source:raw) {
                var f=new LinkedHashMap<String,Object>();f.putAll(source);
                if(!f.containsKey(DUPLICATE_GROUP_SIZE)) f.put(DUPLICATE_GROUP_SIZE,raw.stream().filter(other -> List.of("contractNo","projectGroupCode","createBy","updateTime","updateBy")
                        .stream().allMatch(name -> Objects.equals(source.get(name),other.get(name))))
                        .map(other -> other.get("id").toString()).distinct().count());
                String key=source.get("id")+":"+(source.get("member_id")==null?0:source.get("member_id"));
                if(decisions.containsKey(key)) f.put("migrationDecision",decisions.get(key));
                if(source.get("originalProjectId")!=null) {
                    String project=source.get("originalProjectId").toString();
                    projectBindings.computeIfAbsent(project,k->projectBinding(k,1000L+projectBindings.size(),source.get("group_projectType").toString(),source.get("originalProjectCode").toString()));
                }
                long contract=contractIds.computeIfAbsent(source.get("contractNo").toString(),k->2000L+contractIds.size());
                f.put("contractId",contract);f.put("expectedProjectVersion",0);rows.add(new Row(OBJECT,key,f,null));
            }
            when(bindings.selectIdentitySources(argThat(q->q!=null && "DPPMS_PROJECT_MASTER".equals(q.adapter())))).thenAnswer(call->projectBindings.values().stream()
                    .filter(binding->((SyncQueries.IdentitySources)call.getArgument(0)).sourceKeys().contains(binding.getSourceKey())).toList());
            when(users.getHistoricalIdentitiesByUsername(eq(1L),anySet())).thenAnswer(call->List.of(
                    new SystemUserMigrationApi.HistoricalIdentity(21L,"x01861"),new SystemUserMigrationApi.HistoricalIdentity(22L,"x00270"),
                    new SystemUserMigrationApi.HistoricalIdentity(23L,"x05179")).stream()
                    .filter(identity->((Set<?>)call.getArgument(1)).contains(identity.username())).toList());
            doAnswer(call->{var query=(ReferenceQuery)call.getArgument(0);
                return query.references().stream().map(ref->new ResolvedReference(ref.sourceKey(),ref.projectId(),0L,
                        "C1".equals(ref.contractNo())?11L:contractIds.get(ref.contractNo()))).toList();}).when(relations).resolveReferences(any());
            return rows;
        }
    }
    @Test void actualCompleteBridgeWithUnselectedProjectCannotBypassSpecificAdjudication() throws Exception {
        var rows=actualRows("approved-contract-duplicate-bridges.json");
        assertEquals(40,rows.size());assertEquals(37,rows.stream().map(Row::sourceKey).distinct().count());
        var error=assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(rows,List.of(),false)));
        assertTrue(error.getMessage().contains("原身份重复") || error.getMessage().contains("数量不一致") || error.getMessage().contains("裁决原字段"));
        verifyNoOwnerMutation();
    }
    @Test void actualAdjudicatedBridgeCallsOwnerOncePerGroupAndRetainsEverySourceIdentity() throws Exception {
        var rows=actualRows("approved-contract-duplicate-selected-bridges.json");
        doAnswer(call->{Request request=call.getArgument(0);var receipt=new ArrayList<Result>();
            for(var e:request.entries()) { var x=e.incoming();receipt.add(new Result(e.sourceKey(),"CREATED",null,
                new Relation(3000L+receipt.size(),x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),
                        x.effectiveFrom(),x.effectiveTo(),x.status(),0,x.creator(),x.createTime(),x.updater(),x.updateTime(),false))); }
            return receipt;
        }).when(relations).apply(any());
        var changes=adapter.apply(duplicates(rows,List.of(),false));
        assertEquals(37,changes.size());assertEquals(14,changes.stream().map(Change::targetId).distinct().count());
        assertEquals(37,changes.stream().map(Change::sourceKey).distinct().count());
        assertEquals(23,changes.stream().filter(change->change.message().contains("createTime不独立迁入正式审计")).count());
        assertTrue(changes.stream().allMatch(change->((Map<?,?>)change.after().get("originalFields")).size()==26));
        verify(relations).apply(argThat(request->request.entries().size()==14));
    }
    @Test void actualIdenticalSourcesShare16RelationsKeep32BindingsAndReplayAll17TargetFields() throws Exception {
        var all=actualRows("approved-contract-identical-bridges.json");assertEquals(39,all.size());
        var unresolved=Set.of("41325","41326","41327","41328","42366","42367","42368");
        var rows=all.stream().filter(row->!unresolved.contains(row.fields().get("id").toString())).toList();assertEquals(32,rows.size());
        doAnswer(call->{Request request=call.getArgument(0);var receipt=new ArrayList<Result>();
            for(var e:request.entries()) { var x=e.incoming();var after=e.lastApplied()!=null?e.lastApplied():
                new Relation(3000L+receipt.size(),x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),
                    x.effectiveFrom(),x.effectiveTo(),x.status(),0,x.creator(),x.createTime(),x.updater(),x.updateTime(),false);
                receipt.add(new Result(e.sourceKey(),e.lastApplied()==null?"CREATED":"UNCHANGED",e.lastApplied(),after)); }
            return receipt;
        }).when(relations).apply(any());
        var changes=adapter.apply(duplicates(rows,List.of(),false));
        assertEquals(32,changes.size());assertEquals(16,changes.stream().map(Change::targetId).distinct().count());
        assertEquals(32,changes.stream().map(Change::sourceKey).distinct().count());
        assertTrue(changes.stream().allMatch(change->change.message().contains("Q-LEGACY-CONTRACT-DUPLICATE-POLICY-20261012-008")
                && !change.message().contains("不独立迁入")));
        for(int i=0;i<changes.size();i++) {
            var change=changes.get(i);var raw=(Map<?,?>)change.after().get("originalFields");var target=(Map<?,?>)change.after().get("relation");
            assertEquals(26,raw.size());assertEquals(17,target.size());
            assertEquals(SyncFieldMapper.sourceTime(rows.get(i).fields().get("createTime")).toString(),raw.get("createTime"));
            assertEquals(SyncFieldMapper.sourceTime(raw.get("createTime")),SyncFieldMapper.sourceTime(target.get("createTime")));
            assertEquals(target.get("createTime"),target.get("effectiveFrom"));assertEquals(target.get("createTime"),target.get("updateTime"));
        }
        verify(relations).apply(argThat(request->request.entries().size()==16));
        var proof=changes.stream().map(change->new Binding(OBJECT,change.sourceKey(),change.targetId(),JsonUtils.parseMap(JsonUtils.toJsonString(change.after())))).toList();
        var bound=new ArrayList<Row>();for(int i=0;i<rows.size();i++) bound.add(new Row(OBJECT,rows.get(i).sourceKey(),rows.get(i).fields(),changes.get(i).targetId()));
        clearInvocations(relations);var replay=adapter.apply(duplicates(bound,proof,false));
        assertTrue(replay.stream().allMatch(change->change.action().equals("UNCHANGED")));
        assertEquals(changes.stream().map(Change::after).toList(),replay.stream().map(Change::after).toList());
        verify(relations).apply(argThat(request->request.entries().size()==16 && request.entries().stream().allMatch(entry->entry.lastApplied()!=null)));
        clearInvocations(relations);
        var changedProof=new ArrayList<>(proof);var changed=new LinkedHashMap<>(proof.getFirst().lastFields());
        var changedTarget=new LinkedHashMap<>((Map<String,Object>)changed.get("relation"));changedTarget.put("updater","999");changed.put("relation",changedTarget);
        changedProof.set(0,new Binding(OBJECT,proof.getFirst().sourceKey(),proof.getFirst().targetId(),changed));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(bound,changedProof,false)));verifyNoOwnerMutation();
    }
    @Test void actualFourHistoricalOriginalsRemainPresentAndCannotAcquireCurrentRelationships() throws Exception {
        var all=actualRows("approved-contract-identical-bridges.json");
        assertEquals(7,all.stream().filter(row->row.fields().get("originalProjectId")==null).count());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(all,List.of(),false)));
        var historical=all.stream().filter(row->"16200220092717N".equals(row.fields().get("contractNo"))).toList();
        assertEquals(4,historical.size());assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(historical,List.of(),false)));
        verifyNoOwnerMutation();
    }
    @Test void exactlyThreeApprovedMissingGroupsAreExplicitlySkippedWithCompleteEvidenceAndNoOwnerWrite() throws Exception {
        var all=actualRows("approved-contract-identical-bridges.json");
        var missing=all.stream().filter(row->"16210120062918N-L".equals(row.fields().get("contractNo"))).toList();
        assertEquals(3,missing.size());var changes=adapter.apply(duplicates(missing,List.of(),false));
        assertEquals(Set.of("42366:0","42367:0","42368:0"),new HashSet<>(changes.stream().map(Change::sourceKey).toList()));
        assertTrue(changes.stream().allMatch(change->change.targetId()==null && change.action().equals("SKIPPED")
                && change.message().contains("原项目组及成员不存在") && ((Map<?,?>)change.after().get("originalFields")).size()==26));
        assertEquals(changes,adapter.apply(duplicates(missing,List.of(),false)));verifyNoOwnerMutation();
        var raw=missing.getFirst();var bound=new Row(OBJECT,raw.sourceKey(),raw.fields(),30L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(List.of(bound),List.of(),false)));
        for(String field:List.of("createTime","group_id","member_id","originalProjectId","reviewedBefore")) {
            var fields=new LinkedHashMap<>(raw.fields());fields.put(field,field.equals("createTime")?"2021-06-15 14:02:54":1);
            assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(List.of(new Row(OBJECT,raw.sourceKey(),fields,null)),List.of(),true)),field);
        }
        var fields=new LinkedHashMap<>(raw.fields());fields.put("id",99999);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(List.of(new Row(OBJECT,"99999:0",fields,null)),List.of(),false)));
        verifyNoOwnerMutation();
    }
    @Test void adjudicatedHistoricalProjectCallsOwnerOnceAndKeepsFourIndependentFullBindings() throws Exception {
        var all=actualRows("approved-contract-identical-selected-bridges.json");
        var historical=all.stream().filter(row->"16200220092717N".equals(row.fields().get("contractNo"))).toList();
        assertEquals(4,historical.size());
        assertTrue(historical.stream().allMatch(row->row.fields().get("originalProjectId").toString().equals("36645")
                && row.fields().get("member_projectCode").equals("16200220110601N-0-his")));
        doAnswer(call->{var e=((Request)call.getArgument(0)).entries().getFirst();var x=e.incoming();
            return List.of(new Result(e.sourceKey(),"CREATED",null,new Relation(3000L,x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),
                    x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),x.effectiveFrom(),null,x.status(),0,x.creator(),x.createTime(),null,x.updateTime(),false)));}).when(relations).apply(any());
        var changes=adapter.apply(duplicates(historical,List.of(),false));assertEquals(4,changes.size());
        assertEquals(1,changes.stream().map(Change::targetId).distinct().count());
        for(var change:changes) {
            var target=(Map<?,?>)change.after().get("relation");assertEquals(17,target.size());assertEquals("HISTORICAL",target.get("status"));
            var raw=(Map<?,?>)change.after().get("originalFields");assertEquals(26,raw.size());assertEquals("16200220110601N-0-his",raw.get("member_projectCode"));
        }
        verify(relations).apply(argThat(request->request.entries().size()==1 && request.entries().getFirst().sourceProjectKey().equals("36645")));
        clearInvocations(relations);historical.getFirst().fields().put("member_projectCode","16200220110601N-0");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(historical,List.of(),false)));verifyNoOwnerMutation();
        historical.getFirst().fields().put("member_projectCode","16200220110601N-0-his");
        var resolved=DppmsProjectContractDuplicateDecision.resolve(historical);assertEquals(4,resolved.size());
        assertEquals(Set.of("41325:37632"),new HashSet<>(resolved.values().stream().map(DppmsProjectContractDuplicateDecision.Canonical::sourceKey).toList()));
        historical.getLast().fields().put("originalProjectId",39370);
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractDuplicateDecision.resolve(historical));
    }
    @Test void missingGroupSkipsAndNormalOwnerResultRetainEverySourceInOriginalOrder() throws Exception {
        var all=actualRows("approved-contract-identical-bridges.json");
        var mixed=new ArrayList<Row>(all.stream().filter(row->"16210120062918N-L".equals(row.fields().get("contractNo"))).toList());
        mixed.add(1,row());
        doReturn(List.of(projectBinding("50370",10L,"10","P1"))).when(bindings).selectIdentitySources(argThat(q->q!=null && "DPPMS_PROJECT_MASTER".equals(q.adapter())));
        var changes=adapter.apply(duplicates(mixed,List.of(),false));assertEquals(4,changes.size());
        assertEquals(mixed.stream().map(Row::sourceKey).toList(),changes.stream().map(Change::sourceKey).toList());
        assertEquals(3,changes.stream().filter(change->change.action().equals("SKIPPED") && change.targetId()==null).count());
        assertEquals("CREATED",changes.get(1).action());assertEquals(30L,changes.get(1).targetId());
        verify(relations).apply(argThat(request->request.entries().size()==1 && request.entries().getFirst().sourceKey().equals("100:200")));
    }
    @Test void identicalApprovalStillBlocksMissingSiblingNewOriginalIdAndSourceOrBridgeChanges() {
        for(String change:List.of("missing","new-id","updateTime","group_projectGroupName","originalProjectId","reviewedBefore")) {
            var rows=approvedRows();
            for(int i=0;i<rows.size();i++) {
                var f=rows.get(i).fields();int id=9745+i;f.put("id",id);f.put("contractNo","31020151009H");f.put("createTime","2015-10-12 10:57:31");
                for(String name:List.of("projectGroupCode","member_projectGroupCode","group_projectGroupCode")) f.put(name,"prj_gp7379");
                rows.set(i,new Row(OBJECT,id+":200",f,null));
            }
            if(change.equals("missing")) rows.removeLast();
            else if(change.equals("new-id")) { var f=new LinkedHashMap<>(rows.getLast().fields());f.put("id",99999);rows.add(new Row(OBJECT,"99999:200",f,null)); }
            else rows.getLast().fields().put(change,change.endsWith("Time")?"2015-10-12 10:57:32":"changed");
            assertThrows(IllegalArgumentException.class,()->adapter.apply(duplicates(rows,List.of(),true)),change);
        }
        verifyNoOwnerMutation();
    }
    List<Row> approvedMissingProjectMembers() throws Exception {
        var rows=actualRows("approved-contract-missing-project-members.json");
        for(var row:rows) {
            var raw=new LinkedHashMap<String,Object>();columns().forEach(name->raw.put(name,row.fields().get(name)));
            row.fields().put("migrationDecision",Map.of("action","SKIP_MISSING_PROJECT_MEMBER","originalFields",raw));
            row.fields().put("projectCandidateCount",0);
        }
        return rows;
    }
    @Test void approvedMissingProjectMembersRetainEveryOriginalAndDoNotCallBusinessOwner() throws Exception {
        var rows=approvedMissingProjectMembers();
        // Member 9052 has no contract row; its source exclusion is registered separately, never fabricated here.
        assertEquals(9,rows.size());
        assertEquals(Set.of("23878","24017","24554","24601","24808","25469","25539","29161","36307"),
                new HashSet<>(rows.stream().map(row->row.fields().get("member_id").toString()).toList()));
        var result=adapter.apply(duplicates(rows,List.of(),false));
        assertEquals(rows.stream().map(Row::sourceKey).toList(),result.stream().map(Change::sourceKey).toList());
        for(var change:result) {
            assertEquals("SKIPPED",change.action());assertNull(change.targetId());
            assertEquals(26,((Map<?,?>)change.after().get("originalFields")).size());
            assertTrue(change.message().contains("原项目身份不存在，不能挂靠其他类型或同名项目"));
        }
        assertEquals(result,adapter.apply(duplicates(rows,List.of(),false)));
        verifyNoInteractions(relations,users,bindings);
    }
    @Test void missingProjectDecisionRejectsChangedRawFieldsRestoredAssociationAndExistingTargets() throws Exception {
        for(String change:List.of("member_smsProjectCode","member_updateBy","group_projectType","originalProjectId",
                "originalProjectCode","projectCandidateCount","reviewedBefore","target","binding","no-decision")) {
            var row=approvedMissingProjectMembers().getFirst();var f=row.fields();
            List<Binding> prior=List.of();
            switch(change) {
                case "target" -> row=new Row(OBJECT,row.sourceKey(),f,30L);
                case "binding" -> prior=List.of(new Binding(OBJECT,row.sourceKey(),30L,Map.of()));
                case "no-decision" -> f.remove("migrationDecision");
                case "projectCandidateCount" -> f.put(change,1);
                default -> f.put(change,"changed");
            }
            var selected=row;var previous=prior;
            assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(selected,previous,false)),change);
        }
        verifyNoInteractions(relations,users,bindings);
    }
    @Test void approvedMemberExclusionDoesNotSkipAnotherMissingSourceAndPreservesMixedOrder() throws Exception {
        var excluded=approvedMissingProjectMembers().getFirst();
        var missing=row();missing.fields().put("originalProjectId",null);missing.fields().put("originalProjectCode",null);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(missing,List.of(),false)));
        verifyNoOwnerMutation();
        doReturn(List.of(projectBinding("50370",10L,"10","P1"))).when(bindings)
                .selectIdentitySources(argThat(q->q!=null && "DPPMS_PROJECT_MASTER".equals(q.adapter())));
        var rows=List.of(excluded,row());var result=adapter.apply(duplicates(rows,List.of(),false));
        assertEquals(rows.stream().map(Row::sourceKey).toList(),result.stream().map(Change::sourceKey).toList());
        assertEquals("SKIPPED",result.getFirst().action());assertEquals("CREATED",result.getLast().action());
        verify(relations).apply(argThat(request->request.entries().size()==1));
    }
    @Test void countedGeneralSourcesPreserve76IndependentReceiptsAnd31FormalRelationshipsWithProtectedReplay() throws Exception {
        var all=actualRows("approved-contract-general-duplicate-bridges.json");assertEquals(81,all.size());
        var outOfScope=Set.of("42247","45177","44210","45535","45538");
        var rows=all.stream().filter(row->!outOfScope.contains(row.fields().get("id").toString())).toList();
        doAnswer(call->{var request=(Request)call.getArgument(0);var result=new ArrayList<Result>();
            for(var e:request.entries()) { var x=e.incoming();var after=e.lastApplied()!=null?e.lastApplied():
                    new Relation(3000L+result.size(),x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),
                            x.effectiveFrom(),x.effectiveTo(),x.status(),0,x.creator(),x.createTime(),x.updater(),x.updateTime(),false);
                result.add(new Result(e.sourceKey(),e.lastApplied()==null?"CREATED":"UNCHANGED",e.lastApplied(),after)); }
            return result;
        }).when(relations).apply(any());
        var changes=adapter.apply(duplicates(rows,List.of(),false));assertEquals(76,changes.size());
        assertEquals(3,changes.stream().filter(change->change.action().equals("SKIPPED")).count());
        assertEquals(31,changes.stream().filter(change->change.targetId()!=null).map(Change::targetId).distinct().count());
        assertTrue(changes.stream().allMatch(change->((Map<?,?>)change.after().get("originalFields")).size()==26));
        assertEquals(4,changes.stream().filter(change->change.targetId()!=null && "HISTORICAL".equals(((Map<?,?>)change.after().get("relation")).get("status"))).count());
        var proofs=new ArrayList<Binding>();var bound=new ArrayList<Row>();
        for(int i=0;i<rows.size();i++) {
            var change=changes.get(i);var row=rows.get(i);bound.add(new Row(OBJECT,row.sourceKey(),row.fields(),change.targetId()));
            if(change.targetId()!=null) proofs.add(new Binding(OBJECT,row.sourceKey(),change.targetId(),JsonUtils.parseMap(JsonUtils.toJsonString(change.after()))));
        }
        clearInvocations(relations);var replay=adapter.apply(duplicates(bound,proofs,false));
        assertEquals(73,replay.stream().filter(change->change.action().equals("UNCHANGED")).count());
        assertEquals(changes.stream().map(Change::after).toList(),replay.stream().map(Change::after).toList());
        verify(relations).apply(argThat(request->request.entries().size()==31));
    }
}
