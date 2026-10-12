package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsContractCollectionPlanSyncTemplate.*;

class DppmsContractCollectionPlanSyncAdapterTest {
    ContractCollectionPlanMigrationApi plans = mock(ContractCollectionPlanMigrationApi.class);
    SyncTaskService tasks = mock(SyncTaskService.class);
    SyncRunMapper runs = mock(SyncRunMapper.class);
    SyncBindingMapper bindings = mock(SyncBindingMapper.class);
    DppmsContractCollectionPlanSyncAdapter adapter = new DppmsContractCollectionPlanSyncAdapter(plans,tasks,runs,bindings);
    SyncTaskDO task;
    SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task = new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setConnectionId(18L)
                .setAdapter(ADAPTER).setSourceSystem("DPPMS").setDefinition(JsonUtils.toJsonString(create(18L)));
        task.setTenantId(1L);
        run = new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);
        run.setTenantId(1L);run.setCreator("7");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void officialTemplatePassesTaskCreationValidationAndOversizedCapacityIsRejected() {
        var validator = new SyncDefinitionValidator(List.of(adapter));
        var definition = create(18L);
        assertDoesNotThrow(() -> validator.validate(definition));
        assertDoesNotThrow(() -> MysqlSyncReader.compile(definition.sources().getFirst()));
        var oversized = JsonUtils.parseObject(JsonUtils.toJsonString(definition).replace("\"maxBytes\":67108864", "\"maxBytes\":268435456"), SyncDefinition.class);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(oversized));
    }
    private Row row() {
        Map<String,Object> fields = new LinkedHashMap<>(); ORIGINAL_COLUMNS.forEach(name -> fields.put(name,null));
        ACTOR_COLUMNS.forEach(name -> fields.put(name,null));
        fields.put("id",10);fields.put("contractNo"," C1 ");fields.put("batchCode","1");fields.put("basicDataName","终验款");
        fields.put("referenceEventName","终验");fields.put("afterDaysNum",30);fields.put("createBy","crmapp");
        fields.put("updateBy","CrmInterfaceUser");fields.put("createTime","2018-01-02 03:04:05.123");
        fields.put("updateTime","2019-01-02 03:04:05.456");fields.put("dataSource","CRM");
        return new Row(OBJECT,"10",fields,null);
    }
    private Batch batch(Row row,List<Binding> prior,boolean adopt) {
        return new Batch("integration:1:12",List.of(row),prior,false,"RETAIN",adopt,"UPSERT",false);
    }
    private Plan formal(Plan incoming) {
        return new Plan(40L,incoming.tenantId(),20L,incoming.contractNo(),incoming.batchCode(),incoming.collectionItemName(),
                incoming.referenceEventName(),incoming.eventPlannedAt(),incoming.afterDays(),incoming.eventActualFinishedAt(),
                incoming.marketingFeedback(),incoming.dataSource(),incoming.effectiveFrom(),incoming.effectiveTo(),incoming.creator(),
                incoming.createTime(),incoming.updater(),incoming.updateTime(),false);
    }
    @Test void carriesAllRawColumnsAndRecordsExactDatesInExistingBindings() {
        when(plans.apply(any())).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            assertEquals(7L,request.actorId());assertEquals("integration:1:12",request.owner());
            var incoming = request.entries().getFirst().incoming();
            assertEquals(" C1 ",incoming.contractNo());assertEquals("终验款",incoming.collectionItemName());
            assertEquals(30,incoming.afterDays());assertNull(incoming.eventPlannedAt());
            assertEquals("crmapp",incoming.creator());assertEquals("CrmInterfaceUser",incoming.updater());
            assertEquals(LocalDateTime.parse("2018-01-02T03:04:05.123"),incoming.createTime());
            return List.of(new Result("10","CREATED",null,formal(incoming),"保存"));
        });
        var change = adapter.apply(batch(row(),List.of(),false)).getFirst();
        assertEquals(40L,change.targetId());
        var raw = (Map<?,?>)change.after().get("originalFields");assertEquals(16,raw.size());assertTrue(raw.containsKey("marketingFeedback"));
        var plan = (Map<?,?>)change.after().get("plan");assertEquals("2019-01-02T03:04:05.456",plan.get("updateTime"));
    }
    @Test void explicitUnlinkedActorDecisionPreservesOnlyExactMissingOriginalCode() {
        var row=row();row.fields().put("createBy","l01663");
        row.fields().put("creatorFndMatchCount",0);row.fields().put("creatorTMatchCount",0);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));
        row.fields().put("creatorUnlinkedCode","l01663");
        when(plans.apply(any())).thenAnswer(invocation -> {
            Request request=invocation.getArgument(0);var incoming=request.entries().getFirst().incoming();
            assertEquals("l01663",incoming.creator());assertEquals("CrmInterfaceUser",incoming.updater());
            return List.of(new Result("10","CREATED",null,formal(incoming),"保存"));
        });
        assertEquals("CREATED",adapter.apply(batch(row,List.of(),false)).getFirst().action());
        row.fields().put("creatorUnlinkedCode","different");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));
        row.fields().put("creatorUnlinkedCode","l01663");row.fields().put("creatorFndMatchCount",2);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));
        row.fields().put("creatorFndMatchCount",1);row.fields().put("creatorFndSourceKey",99);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));
    }
    @Test void repeatUsesEstablishedTargetAndPreservesMillisecondsAfterBindingJsonRoundTrip() {
        when(plans.apply(any())).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);var entry = request.entries().getFirst();
            if (entry.targetId()!=null) assertEquals(LocalDateTime.parse("2019-01-02T03:04:05.456"),entry.lastApplied().updateTime());
            return List.of(new Result("10",entry.targetId()==null?"CREATED":"UNCHANGED",entry.lastApplied(),formal(entry.incoming()),"保存"));
        });
        var change = adapter.apply(batch(row(),List.of(),false)).getFirst();
        var persisted = JsonUtils.parseObject(JsonUtils.toJsonString(change.after()),Map.class);
        var raw = row();var bound = new Row(OBJECT,"10",raw.fields(),40L);
        var result = adapter.apply(batch(bound,List.of(new Binding(OBJECT,"10",40L,persisted)),false)).getFirst();
        assertEquals("UNCHANGED",result.action());assertEquals(40L,result.targetId());
    }
    @Test void changedRawIdContentDoesNotReachBusinessOwner() {
        when(plans.apply(any())).thenAnswer(invocation -> { Request request=invocation.getArgument(0);
            return List.of(new Result("10","CREATED",null,formal(request.entries().getFirst().incoming()),"保存")); });
        var saved = adapter.apply(batch(row(),List.of(),false)).getFirst();clearInvocations(plans);
        var changed = row();changed.fields().put("basicDataName","初验款");
        var bound = new Row(OBJECT,"10",changed.fields(),40L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(bound,List.of(new Binding(OBJECT,"10",40L,saved.after())),false)));
        verifyNoInteractions(plans);
    }
    @Test void scheduledConfigurationStaleActorAndIncompleteSourceAreRejected() {
        task.setDefinition(JsonUtils.toJsonString(create(18L).toBuilder().mode("SCHEDULED").build()));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        task.setDefinition(JsonUtils.toJsonString(create(18L)));run.setConfigVersion(2);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        run.setConfigVersion(1);var incomplete=row();incomplete.fields().remove("marketingFeedback");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(incomplete,List.of(),false)));
        verifyNoInteractions(plans);
    }
    @Test void auditNeedsMigratedUserBindingAndResolvesFndBeforeT() {
        var source=row();source.fields().put("createBy","l01663");source.fields().put("creatorFndSourceKey","123");
        source.fields().put("creatorTSourceKey","456");
        source.fields().put("creatorFndMatchCount",1);source.fields().put("creatorTMatchCount",1);
        when(bindings.selectIdentitySources(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(source,List.of(),false)));
        var binding=new SyncBindingDO().setSourceKey("123").setTargetId(11L);
        when(bindings.selectIdentitySources(any())).thenAnswer(invocation -> {
            SyncQueries.IdentitySources query=invocation.getArgument(0);assertEquals("fnd_user_info",query.sourceObject());
            assertEquals(18L,query.connectionId());return List.of(binding);
        });
        when(plans.preview(any())).thenAnswer(invocation -> { Request request=invocation.getArgument(0);
            assertEquals("11",request.entries().getFirst().incoming().creator());return List.of(); });
        run.setStatus("VALIDATING").setPreview(true);
        adapter.preview(batch(source,List.of(),false));verify(plans).preview(any());verify(plans,never()).apply(any());
    }
    @Test void fractionalDayCannotBeTruncatedAndOldTargetAdoptionMustBeExplicit() {
        var fractional=row();fractional.fields().put("afterDaysNum","1.5");
        assertThrows(NumberFormatException.class,()->adapter.apply(batch(fractional,List.of(),false)));
        var adopted=row();adopted.fields().put("existingPlanId",40L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(adopted,List.of(),false)));
        verifyNoInteractions(plans);
    }
    @Test void samePersonDuplicateActorsRequireEverySourceBindingToTheSameFormalUser() {
        var source=row();source.fields().put("createBy","c00733");
        source.fields().put("creatorFndMatchCount",2);source.fields().put("creatorTMatchCount",0);
        source.fields().put("creatorFndSourceKeys","233,408");
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(
                new SyncBindingDO().setSourceKey("233").setTargetId(11L),new SyncBindingDO().setSourceKey("408").setTargetId(11L)));
        when(plans.apply(any())).thenAnswer(invocation->{
            Request request=invocation.getArgument(0);var incoming=request.entries().getFirst().incoming();
            assertEquals("11",incoming.creator());return List.of(new Result("10","CREATED",null,formal(incoming),"保存"));
        });
        assertEquals("CREATED",adapter.apply(batch(source,List.of(),false)).getFirst().action());
        clearInvocations(plans);
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(
                new SyncBindingDO().setSourceKey("233").setTargetId(11L),new SyncBindingDO().setSourceKey("408").setTargetId(12L)));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(source,List.of(),false)));
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(new SyncBindingDO().setSourceKey("408").setTargetId(11L)));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(source,List.of(),false)));
        source.fields().put("creatorFndSourceKeys","408");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(source,List.of(),false)));
        verifyNoInteractions(plans);
    }
    @Test void existingFormalIdMappingIsExplicitAndRawIdCannotBecomeFormalIdentity() {
        assertFalse(adapter.descriptor().objects().getFirst().supportsSourcePrimaryKey());
        assertTrue(adapter.descriptor().objects().getFirst().fields().stream().anyMatch(field->"existingPlanId".equals(field.name())));
        var explicit=row();explicit.fields().put("existingPlanId",40L);
        when(plans.preview(any())).thenAnswer(call->{Request request=call.getArgument(0);
            assertEquals(40L,request.entries().getFirst().adjudicatedExistingId());assertTrue(request.adoptExisting());return List.of();});
        run.setStatus("VALIDATING").setPreview(true);
        adapter.preview(batch(explicit,List.of(),true));clearInvocations(plans);
        run.setStatus("APPLYING").setPreview(false);
        explicit.fields().put("_sourcePrimaryKey",10L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(explicit,List.of(),true)));verifyNoInteractions(plans);
    }
    @Test void ambiguousFndIdentityCannotFallBackToTIdentity() {
        var source=row();source.fields().put("createBy","unresolved");
        source.fields().put("creatorFndMatchCount",2);source.fields().put("creatorTMatchCount",1);
        source.fields().put("creatorTSourceKey","456");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(source,List.of(),false)));
        verifyNoInteractions(bindings,plans);
    }
    @Test void sourceAndSnapshotExecutionPhasesCannotMixPreviewAndWrites() {
        when(plans.preview(any())).thenReturn(List.of());when(plans.apply(any())).thenReturn(List.of());
        for(String phase:List.of("READING","VALIDATING")) {
            run.setStatus(phase).setPreview(true);assertDoesNotThrow(()->adapter.preview(batch(row(),List.of(),false)));
        }
        for(String phase:List.of("READING","APPLYING")) {
            run.setStatus(phase).setPreview(false);assertDoesNotThrow(()->adapter.apply(batch(row(),List.of(),false)));
        }
        clearInvocations(plans,bindings);
        for(String phase:List.of("QUEUED","SUCCESS","FAILED","PREVIEW_READY","RUNNING")) {
            run.setStatus(phase).setPreview(false);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        }
        run.setStatus("READING").setPreview(true);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        run.setStatus("READING").setPreview(false);
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(row(),List.of(),false)));
        verifyNoInteractions(plans,bindings);
    }
    @Test void snapshotPreviewStartsATransactionBeforeCallingTheMandatoryOwner() {
        run.setStatus("VALIDATING").setPreview(true);
        when(plans.preview(any())).thenAnswer(call->{
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return List.of();
        });
        var factory=new org.springframework.aop.framework.ProxyFactory(adapter);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                new org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager(),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        ((cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter)factory.getProxy()).preview(batch(row(),List.of(),false));
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        verify(plans).preview(any());verify(plans,never()).apply(any());
    }
}
