package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.business.DurationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.business.StagePlanBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.business.DurationBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchRespVO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.business.StagePlanBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.business.DurationBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.stageplan.*;
import cn.iocoder.yudao.module.pms.engineering.service.stageplan.business.StagePlanBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.ProjectBusinessScopeAccess;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi;
import cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Inherited writes use actual tables, journals and transactions in a fresh test-owned H2 database. */
@SuppressWarnings({"rawtypes","unchecked"})
class PlanDefaultBusinessTest {
    private final SiteSurveySpringPersistenceTest fixture=new SiteSurveySpringPersistenceTest();
    private DurationBusinessService duration;
    private StagePlanBusinessService stage;
    private ConstructionPlanQueryService legacyDuration;
    private StagePlanBatchServiceImpl legacyStage;
    private final LocalDate start=LocalDate.of(2026,10,12),end=start.plusDays(9);
    @BeforeEach void open() throws Exception {
        assertFalse(SiteSurveySpringPersistenceTest.mysqlOptIn());fixture.open();var ctx=fixture.ctx;
        var scopes=ctx.getBean(ProjectScopeApi.class);when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        BusinessCallerContext callers=()->{var caller=ctx.getBean(BusinessCallerContext.class).require();return new AbstractBusinessApplicationService.ResolvedCaller(caller.tenantId(),caller.userId(),null);};
        ctx.getBeanFactory().registerSingleton("planDefaults",new BusinessDefaults(callers,new BusinessPermissions(ctx.getBean(PermissionApi.class),callers),new ProjectBusinessScopeAccess(scopes),ctx.getBean(jakarta.validation.Validator.class),new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),ctx.getBean(OperationExecutionStore.class),ctx.getBean(OperationAuditApi.class),ctx.getBean(BusinessEventPort.class),List.of(),()->mock(DefaultBusinessDeliveryApi.class)));
        for(var type:List.of(DurationBusinessDO.class,ConstructionPlanRevisionDO.class,ConstructionPlanChangeDO.class,StagePlanBusinessDO.class,StagePlanItemDO.class))SiteSurveySpringPersistenceTest.Config.schema(fixture.jdbc,type);
        var sf=ctx.getBean(SqlSessionFactory.class);var sessions=new SqlSessionTemplate(sf);
        sf.getConfiguration().getInterceptors().stream()
                .filter(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class::isInstance)
                .map(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class::cast).findFirst().orElseThrow()
                .addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));
        for(var type:List.of(DurationBusinessMapper.class,StagePlanBusinessMapper.class,StagePlanBatchMapper.class,StagePlanItemMapper.class)){
            sf.getConfiguration().addMapper(type);ctx.getBeanFactory().registerSingleton(type.getSimpleName(),sessions.getMapper(type));
        }
        for(var name:List.of("constructionplan/ConstructionPlanMapper","constructionplan/ConstructionPlanRevisionMapper","constructionplan/ConstructionPlanChangeMapper","completion/StagePlanCompletionMapper","stageplan/business/StagePlanBusinessMapper")){
            var xml=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/mapper/"+name+".xml")).replace("b'0'","0").replace("b'1'","1");
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(new java.io.StringReader(xml),sf.getConfiguration(),name,sf.getConfiguration().getSqlFragments()).parse();
            Class type=Class.forName("cn.iocoder.yudao.module.pms.engineering.dal.mysql."+name.replace('/','.'));if(!ctx.containsBean(type.getSimpleName()))ctx.getBeanFactory().registerSingleton(type.getSimpleName(),sessions.getMapper(type));
        }
        registerMock(ConstructionPlanQueryService.class);registerMock(DurationChangeApplicationService.class);registerMock(ProjectParticipantFactApi.class);registerMock(StagePlanBatchService.class);
        var evidence=registerMock(cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionEvidenceApi.class);
        when(evidence.deliveries(any(),any(),eq(false))).thenReturn(new ProjectBusinessRuntimeApi.DeliveryFacts(Map.of(),"empty-deliveries"));
        var plans=registerMock(ProjectStagePlanApi.class);
        when(plans.listStages(1L,20L)).thenReturn(List.of(new ProjectStagePlanApi.StagePlanFact(31L,"S1","准备",1,start,end,null,null,"1",0)));
        when(plans.calculateSchedule(eq(1L),eq(20L),any(),any())).thenAnswer(call->new ProjectStagePlanApi.ScheduleCalculation(7L,call.getArgument(2),call.getArgument(3),List.of(new ProjectStagePlanApi.StagePlanDate(31L,call.getArgument(2),call.getArgument(3))),"frozen-input"));
        when(plans.listTaskPlans(1L,20L)).thenReturn(List.of(new ProjectStagePlanApi.TaskPlan(41L,null,"S1","工勘",0,start,end,end)));
        ctx.registerBean(StagePlanBusinessResultSource.class);ctx.registerBean(DurationBusinessService.class);ctx.registerBean(StagePlanBusinessService.class);
        duration=ctx.getBean(DurationBusinessService.class);stage=ctx.getBean(StagePlanBusinessService.class);
        legacyDuration=new ConstructionPlanQueryService(ctx.getBean(ConstructionPlanMapper.class),ctx.getBean(ConstructionPlanRevisionMapper.class),ctx.getBean(ConstructionPlanChangeMapper.class),ctx.getBean(PermissionApi.class),scopes,ctx.getBean(ProjectParticipantFactApi.class));
        when(ctx.getBean(ConstructionPlanQueryService.class).getById(anyLong(),any())).thenAnswer(call->legacyDuration.getById(call.getArgument(0),call.getArgument(1)));
        legacyStage=new StagePlanBatchServiceImpl();
        ReflectionTestUtils.setField(legacyStage,"batchMapper",ctx.getBean(StagePlanBatchMapper.class));
        ReflectionTestUtils.setField(legacyStage,"itemMapper",ctx.getBean(StagePlanItemMapper.class));
        ReflectionTestUtils.setField(legacyStage,"constructionPlanMapper",ctx.getBean(ConstructionPlanMapper.class));
        ReflectionTestUtils.setField(legacyStage,"constructionPlanRevisionMapper",ctx.getBean(ConstructionPlanRevisionMapper.class));
        ReflectionTestUtils.setField(legacyStage,"projectScopeApi",scopes);
        when(ctx.getBean(StagePlanBatchService.class).getBatch(anyLong())).thenAnswer(call->legacyStage.getBatch(call.getArgument(0)));
    }
    @AfterEach void close(){fixture.close();}
    private <T>T registerMock(Class<T> type){var value=mock(type,withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));fixture.ctx.getBeanFactory().registerSingleton(type.getSimpleName(),value);return value;}
    private Map<String,Object> initial(){return Map.of("projectId",20L,"calculationBasis","DURATION_FROM_START","startDate",start.toString(),"durationDays",10,"$business",Map.of("expectedProjectVersion",2L));}
    private long createDuration(){return duration.createForm(initial(),"duration-initial").entityRef().entityId();}
    private ConstructionPlanRespVO durationView(long id){
        DurationBusinessDO row=ReflectionTestUtils.invokeMethod(duration,"copy",duration.get(id));
        Map<String,Object> view=ReflectionTestUtils.invokeMethod(duration,"formContext",row);
        return (ConstructionPlanRespVO)view.get("duration");
    }
    private StagePlanBatchRespVO stageView(long id){
        StagePlanBusinessDO row=ReflectionTestUtils.invokeMethod(stage,"copy",stage.get(id));
        Map<String,Object> view=ReflectionTestUtils.invokeMethod(stage,"formContext",row);
        return (StagePlanBatchRespVO)view.get("plan");
    }
    @Test void authorizedDurationPresentationPreservesNativeFieldsWithoutRepeatingTheRead(){
        long id=createDuration();
        // Management denial must remove the action without denying an otherwise authorized read.
        when(fixture.ctx.getBean(PermissionApi.class).hasAnyPermissions(9L,ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(false);
        var expected=legacyDuration.getById(id,new ConstructionPlanQueryService.Actor(1L,9L));
        clearInvocations(fixture.ctx.getBean(ProjectScopeApi.class),fixture.ctx.getBean(ConstructionPlanQueryService.class));
        var actual=durationView(id);
        assertEquals(JsonUtils.parseTree(JsonUtils.toJsonString(expected)),JsonUtils.parseTree(JsonUtils.toJsonString(actual)));
        assertEquals(List.of(),actual.getAllowedActions());assertNotNull(actual.getCurrentRevision().getFrozenAt());
        verify(fixture.ctx.getBean(ProjectScopeApi.class),times(1)).resolveCurrent(any());
        verify(fixture.ctx.getBean(ConstructionPlanQueryService.class),never()).getById(anyLong(),any());
        when(fixture.ctx.getBean(ProjectScopeApi.class).resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(20L,2L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,()->durationView(id));
    }
    @Test void durationPresentationKeepsPendingCandidateAndFailsWhenHistoryIsMissing(){
        long id=createDuration();var root=duration.get(id);
        var candidate=new ConstructionPlanRevisionDO();candidate.setTenantId(1L);candidate.setPlanId(id);candidate.setRevisionNo(2);candidate.setCalculationBasisCode("DATE_RANGE");candidate.setStartDate(start);candidate.setEndDate(end.plusDays(2));candidate.setDurationDays(12);candidate.setCreatedBy(9L);candidate.setCreatedAt(LocalDateTime.now());candidate.setVersion(0);
        fixture.ctx.getBean(ConstructionPlanRevisionMapper.class).insert(candidate);
        var pending=new ConstructionPlanChangeDO();pending.setTenantId(1L);pending.setPlanId(id);pending.setBaseRevisionId(root.getCurrentDurationRevisionId());pending.setCandidateRevisionId(candidate.getId());pending.setStatusCode("PENDING_APPROVAL");pending.setReasonTypeCode("OTHER");pending.setReasonDetail("验收窗口调整");pending.setCustomerEvidenceRequired(false);pending.setProcessDefinitionKey("duration-change");pending.setProcessInstanceId("pending-process");pending.setApplicantUserId(9L);pending.setCreatedAt(LocalDateTime.now());pending.setVersion(0);
        fixture.ctx.getBean(ConstructionPlanChangeMapper.class).insert(pending);
        fixture.jdbc.update("UPDATE sol_construction_plan SET pending_change_id=? WHERE id=?",pending.getId(),id);
        var expected=legacyDuration.getById(id,new ConstructionPlanQueryService.Actor(1L,9L));var actual=durationView(id);
        assertEquals(JsonUtils.parseTree(JsonUtils.toJsonString(expected)),JsonUtils.parseTree(JsonUtils.toJsonString(actual)));
        assertFalse(actual.getPendingChangeSummary().getCandidateRevision().getCurrent());assertEquals(List.of(),actual.getAllowedActions());
        fixture.jdbc.update("DELETE FROM sol_construction_plan_revision WHERE id=?",candidate.getId());
        assertThrows(RuntimeException.class,()->durationView(id));
    }
    @Test void durationPresentationRechecksManageScopeAndParticipantForItsDisplayedAction(){
        long id=createDuration();var participants=fixture.ctx.getBean(ProjectParticipantFactApi.class);
        when(participants.inspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact(20L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"DIRECT","ACTIVE","S1",2L,2L));
        assertEquals(List.of("CREATE_CHANGE"),durationView(id).getAllowedActions());
        when(fixture.ctx.getBean(ProjectScopeApi.class).resolveCurrent(argThat(query->query!=null && ProjectScopeApi.ACTION_MANAGE.equals(query.actionCode()))))
                .thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(20L,2L,Set.of(),Set.of()));
        clearInvocations(participants);
        assertEquals(List.of(),durationView(id).getAllowedActions());verifyNoInteractions(participants);
        assertEquals(List.of(),legacyDuration.getById(id,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());
    }
    @Test void authorizedStagePresentationKeepsFrozenTasksAndHistoricalBaselineWithoutRepeatingTheRead(){
        long durationId=createDuration();long id=stage.createForm(Map.of("projectId",20L),"stage-create").entityRef().entityId();
        fixture.jdbc.update("UPDATE sol_stage_plan_batch SET status=2,bpm_process_instance_id='approved',effective_at=CURRENT_TIMESTAMP,calculated_start=NULL,calculated_end=NULL WHERE id=?",id);
        // A later current revision must not replace this approved batch's referenced baseline.
        fixture.jdbc.update("UPDATE sol_construction_plan SET current_duration_revision_id=999 WHERE id=?",durationId);
        var expected=legacyStage.getBatch(id);clearInvocations(fixture.ctx.getBean(StagePlanBatchService.class),fixture.ctx.getBean(ProjectScopeApi.class));
        var actual=stageView(id);
        assertEquals(JsonUtils.parseTree(JsonUtils.toJsonString(expected)),JsonUtils.parseTree(JsonUtils.toJsonString(actual)));
        assertEquals(start,actual.getBaselineStart());assertEquals(end,actual.getBaselineEnd());assertTrue(actual.getAdjustmentReasonRequired());assertEquals(1,actual.getTasks().size());assertEquals(1,actual.getItems().size());
        verify(fixture.ctx.getBean(StagePlanBatchService.class),never()).getBatch(anyLong());
        verify(fixture.ctx.getBean(ProjectScopeApi.class),times(1)).resolveCurrent(any());
        verify(fixture.ctx.getBean(ProjectScopeApi.class),never()).resolveAllCurrent(any());
        fixture.jdbc.update("DELETE FROM sol_construction_plan_revision WHERE id=?",stage.get(id).getDurationRevisionId());
        assertThrows(RuntimeException.class,()->stageView(id));
    }
    @Test void initialRevisionIsEffectiveIdempotentAndCannotBeOverwritten(){
        var first=duration.createForm(initial(),"duration-initial");var row=duration.get(first.entityRef().entityId());
        assertEquals(1L,first.newConcurrencyBasis());assertEquals(end,row.getEndDate());assertEquals(10,row.getDurationDays());assertEquals(first,duration.createForm(initial(),"duration-initial"));
        assertEquals(1,fixture.count("sol_construction_plan_revision"));
        assertThrows(RuntimeException.class,()->duration.updateForm(row.getId(),Map.of("endDate",end.plusDays(1).toString()),row.getVersion(),"rewrite-approved"));
        assertThrows(RuntimeException.class,()->duration.delete(row.getId(),row.getVersion(),"delete-history"));
        assertThrows(RuntimeException.class,()->duration.createForm(initial(),"duplicate-plan"));
        assertEquals(end,duration.get(row.getId()).getEndDate());assertEquals(1,fixture.count("sol_construction_plan_revision"));
        var fact=duration.runtimeObservation(new ProjectBusinessRuntimeApi.Query(1L,20L,new ProjectBusinessRuntimeApi.Type("SOL","constructionPlan"),row.getId()),false);
        assertTrue(fact.handlingCompleted());
    }
    @Test void deniedProjectQualificationRollsBackRootHistoryAndReceipt(){
        doThrow(new IllegalStateException("stale project")).when(fixture.ctx.getBean(ProjectParticipantFactApi.class)).lockAndRevalidate(any());
        assertThrows(RuntimeException.class,()->duration.createForm(initial(),"duration-denied"));
        assertEquals(0,fixture.count("sol_construction_plan"));assertEquals(0,fixture.count("sol_construction_plan_revision"));assertEquals(0,fixture.count("plt_idempotency_record"));
        verifyNoInteractions(fixture.ctx.getBean(ProjectEndDateApi.class));
    }
    @Test void stageDraftKeepsFrozenTaskIdentityAndRejectsForeignItems(){
        createDuration();var saved=stage.createForm(Map.of("projectId",20L),"stage-create");long id=saved.entityRef().entityId();var row=stage.get(id);
        assertEquals(1,row.getItems().size());assertEquals(start,row.getItems().getFirst().getPlanStart());
        assertThrows(RuntimeException.class,()->stage.updateForm(id,Map.of("$business",Map.of("items",List.of(Map.of("id",999L,"planStart",start.toString())))),saved.newConcurrencyBasis(),"foreign-item"));
        assertThrows(RuntimeException.class,()->stage.updateForm(id,Map.of("$business",Map.of("tasks",List.of())),saved.newConcurrencyBasis(),"delete-task"));
        var task=new ProjectStagePlanApi.TaskPlan(41L,null,"S1","伪造名称",0,start,end,end);
        assertThrows(RuntimeException.class,()->stage.updateForm(id,Map.of("$business",Map.of("tasks",List.of(task))),saved.newConcurrencyBasis(),"rewrite-task"));
        var update=new LinkedHashMap<String,Object>();update.put("id",row.getItems().getFirst().getId());update.put("planStart",null);update.put("planEnd",null);
        var changed=stage.updateForm(id,Map.of("$business",Map.of("items",List.of(update))),saved.newConcurrencyBasis(),"partial-draft");
        assertNull(stage.get(id).getItems().getFirst().getPlanStart());assertEquals(1L,changed.newConcurrencyBasis());
        assertThrows(RuntimeException.class,()->stage.updateForm(id,Map.of("remark","stale"),saved.newConcurrencyBasis(),"stale-draft"));
        assertThrows(RuntimeException.class,()->stage.createForm(Map.of("projectId",20L),"parallel-draft"));
    }
    @Test void approvalFactExpiresWhenCurrentDurationChangesWithoutChangingOldBatch(){
        long root=createDuration();var saved=stage.createForm(Map.of("projectId",20L),"stage-create");long id=saved.entityRef().entityId();
        // Retained native approval fact is fixture data; this does not simulate a BPM approval.
        fixture.jdbc.update("UPDATE sol_stage_plan_batch SET status=2,bpm_process_instance_id='test-approved',effective_at=CURRENT_TIMESTAMP,version=1 WHERE id=?",id);
        var query=new ProjectBusinessRuntimeApi.Query(1L,20L,new ProjectBusinessRuntimeApi.Type("SOL","stagePlanBatch"),id);
        var current=stage.runtimeObservation(query,false);assertTrue(current.handlingCompleted());
        fixture.jdbc.update("UPDATE sol_construction_plan SET current_duration_revision_id=999,version=version+1 WHERE id=?",root);
        var old=stage.runtimeObservation(query,false);assertFalse(old.handlingCompleted());assertFalse(old.facts().get("CONSTRUCTION_PLAN_APPROVED"));assertNotEquals(current.factVersion(),old.factVersion());
        assertThrows(RuntimeException.class,()->stage.updateForm(id,Map.of("remark","overwrite"),1L,"approved-edit"));
        assertEquals(2,stage.get(id).getStatus());assertEquals(1L,stage.get(id).getVersion());
    }
    @Test void initializationContainsNativePlanAndDurationWithoutReloadingLegacyOwners(){
        when(fixture.ctx.getBean(ProjectParticipantFactApi.class).inspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact(20L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"DIRECT","ACTIVE","S1",2L,2L));
        long durationId=createDuration();long id=stage.createForm(Map.of("projectId",20L),"stage-create").entityRef().entityId();
        fixture.jdbc.update("UPDATE sol_stage_plan_batch SET status=2,bpm_process_instance_id='approved',effective_at=CURRENT_TIMESTAMP WHERE id=?",id);
        var expected=legacyStage.getBatch(id);
        clearInvocations(fixture.ctx.getBean(StagePlanBatchService.class),fixture.ctx.getBean(ConstructionPlanQueryService.class));
        var request=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi.Request(20L,id,null,null);
        var initialized=stage.initializePage(request,List.of(id));
        assertEquals(1L,initialized.page().records().getTotal());
        var actual=(StagePlanBatchRespVO)initialized.page().forms().getFirst().context().get("plan");
        assertEquals(JsonUtils.parseTree(JsonUtils.toJsonString(expected)),JsonUtils.parseTree(JsonUtils.toJsonString(actual)));
        var durationPage=initialized.page().relatedPages().getFirst();
        assertEquals(durationId,durationPage.forms().getFirst().entity().ref().entityId());
        assertTrue(initialized.inspection().objects().getFirst().handlingCompleted());
        assertEquals(false,initialized.page().context().get("overdueFailed"));
        verify(fixture.ctx.getBean(StagePlanBatchService.class),never()).getBatch(anyLong());
        verify(fixture.ctx.getBean(StagePlanBatchService.class),never()).getOverdueStages(anyLong());
        verifyNoInteractions(fixture.ctx.getBean(ConstructionPlanQueryService.class));
        fixture.jdbc.update("DELETE FROM sol_construction_plan_revision WHERE id=?",duration.get(durationId).getCurrentDurationRevisionId());
        assertThrows(RuntimeException.class,()->stage.initializePage(request,List.of(id)));
    }
    @Test void initializationPreservesSeparateSelectedAndEffectiveVersions(){
        when(fixture.ctx.getBean(ProjectParticipantFactApi.class).inspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact(20L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"DIRECT","ACTIVE","S1",2L,2L));
        createDuration();long approved=stage.createForm(Map.of("projectId",20L),"stage-approved").entityRef().entityId();
        fixture.jdbc.update("UPDATE sol_stage_plan_batch SET status=2,bpm_process_instance_id='approved',effective_at=CURRENT_TIMESTAMP WHERE id=?",approved);
        long draft=stage.createForm(Map.of("projectId",20L),"stage-draft").entityRef().entityId();
        var request=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi.Request(20L,draft,null,null);
        var initialized=stage.initializePage(request,List.of(draft));
        assertEquals(Set.of(draft,approved),initialized.page().forms().stream().map(form->form.entity().ref().entityId()).collect(java.util.stream.Collectors.toSet()));
        assertFalse(initialized.inspection().objects().getFirst().handlingCompleted());
        when(fixture.ctx.getBean(ProjectScopeApi.class).resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(20L,2L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,()->stage.initializePage(request,List.of(draft)));
    }
    @Test void childDraftCommandsInvalidateRootBasisAndReplayOnlyOnce(){
        long id=createDuration();var nativeChanges=fixture.ctx.getBean(DurationChangeApplicationService.class);
        when(nativeChanges.createDraft(any(),any())).thenAnswer(call->{
            ConstructionPlanApplicationService.Actor actor=call.getArgument(1);
            assertEquals(1L,actor.tenantId());assertEquals(9L,actor.actorId());
            assertNotNull(actor.correlationId(),"A normal HTTP caller has no entry correlation id; the adapter must provide the native command audit id");
            assertFalse(actor.correlationId().isBlank());return null;
        });
        var content=new cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.DurationChangeCreateReqVO();content.setExpectedProjectVersion(2L);content.setCalculationBasis("DATE_RANGE");content.setStartDate(start);content.setEndDate(end);content.setReasonType("OTHER");content.setReasonDetail("现场窗口调整");
        var saved=duration.createChange(id,1L,"change-create",content);assertEquals(2L,saved.newConcurrencyBasis());assertEquals(saved,duration.createChange(id,1L,"change-create",content));
        verify(nativeChanges,times(1)).createDraft(any(),any());assertEquals(1,fixture.count("sol_construction_plan_revision"));
    }
}
