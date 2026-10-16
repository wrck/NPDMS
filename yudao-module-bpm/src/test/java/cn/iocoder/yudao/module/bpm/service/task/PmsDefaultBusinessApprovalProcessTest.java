package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalProcessApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual native BPM creation and Flowable persistence on a fresh H2 database. */
class PmsDefaultBusinessApprovalProcessTest {
    private BpmProcessDefinitionService definitions;
    private DefaultBusinessApprovalApi associations;
    private PmsDefaultBusinessApprovalProcess adapter;
    private BpmProcessInstanceServiceImpl processes;
    private BpmProcessDefinitionInfoDO info;
    private Context context;
    @BeforeAll static void startEngine(){PmsNodeApprovalProcessOwnerTest.engine();}
    @AfterAll static void closeEngine(){PmsNodeApprovalProcessOwnerTest.stop();}
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(7L);
        definitions=mock(BpmProcessDefinitionService.class);info=new BpmProcessDefinitionInfoDO();info.setFormType(10);info.setVisible(true);
        when(definitions.getProcessDefinitionInfo(anyString())).thenReturn(info);when(definitions.canUserStartProcessDefinition(info,1L)).thenReturn(true);
        processes=spy(new BpmProcessInstanceServiceImpl());
        ReflectionTestUtils.setField(processes,"runtimeService",PmsNodeApprovalProcessOwnerTest.engine.getRuntimeService());
        ReflectionTestUtils.setField(processes,"processDefinitionService",definitions);
        doAnswer(ignored->new BpmApprovalDetailRespVO().setActivityNodes(new ArrayList<>())).when(processes).getApprovalDetail(eq(1L),any());
        associations=mock(DefaultBusinessApprovalApi.class);
        @SuppressWarnings("unchecked") var provider=(ObjectProvider<DefaultBusinessApprovalApi>)mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(associations);
        adapter=new PmsDefaultBusinessApprovalProcess(processes,definitions,PmsNodeApprovalProcessOwnerTest.engine.getRepositoryService(),
                PmsNodeApprovalProcessOwnerTest.engine.getHistoryService(),mock(BpmCommentService.class),provider);
        context=new Context(new EntityRef(7L,"IMP","arrival",PmsNodeApprovalProcessOwnerTest.ids.incrementAndGet()),20L,3L,UUID.randomUUID().toString(),1L);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private Command command(){return new Command(context,PmsNodeApprovalProcessOwnerTest.pinned,Map.of("quantity",2,"receiver","原提交人"),Map.of("reason","审批事由"),Map.of());}
    private Result start(){return PmsNodeApprovalProcessOwnerTest.tx.execute(ignored->adapter.start(command()));}

    @Test void createsARealPinnedBpmProcessWithTheOriginalBusinessSnapshot(){
        var result=start();assertEquals("PENDING",result.status());assertEquals(command().definitionId(),result.definitionId());
        var instance=PmsNodeApprovalProcessOwnerTest.engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(result.instanceId()).includeProcessVariables().singleResult();
        assertEquals("1",instance.getStartUserId());assertTrue(instance.getBusinessKey().startsWith("pms-default-business:"));
        assertEquals(3L,instance.getProcessVariables().get("pmsBusinessVersion"));
        var detail=adapter.detail(context,result.instanceId());assertEquals(2,detail.submittedValues().get("quantity"));assertEquals("原提交人",detail.submittedValues().get("receiver"));
        verify(processes).createProcessInstance0(eq(1L),any(),anyMap(),anyString(),eq(Map.of()));
    }
    @Test void nativeStartAuthorizationAndReservedIdentityBothRemainEnforced(){
        long before=PmsNodeApprovalProcessOwnerTest.engine.getHistoryService().createHistoricProcessInstanceQuery().count();
        when(definitions.canUserStartProcessDefinition(info,1L)).thenReturn(false);
        assertThrows(RuntimeException.class,this::start);
        when(definitions.canUserStartProcessDefinition(info,1L)).thenReturn(true);
        var forged=new Command(context,command().definitionId(),Map.of(),Map.of("pmsTaskId",55L),Map.of());
        assertThrows(IllegalArgumentException.class,()->PmsNodeApprovalProcessOwnerTest.tx.execute(ignored->adapter.start(forged)));
        assertEquals(before,PmsNodeApprovalProcessOwnerTest.engine.getHistoryService().createHistoricProcessInstanceQuery().count());
    }
    @Test void otherBusinessOrAttemptCannotReadTheProcessSnapshot(){
        var result=start();var foreign=new Context(context.entity(),context.projectId(),4L,context.attemptId(),context.actorId());
        assertThrows(IllegalArgumentException.class,()->adapter.detail(foreign,result.instanceId()));
        TenantContextHolder.setTenantId(8L);assertThrows(IllegalArgumentException.class,()->adapter.detail(context,result.instanceId()));
    }
    @Test void aFailedOuterBusinessTransactionRollsBackTheWorkflow(){
        long before=PmsNodeApprovalProcessOwnerTest.engine.getHistoryService().createHistoricProcessInstanceQuery().count();
        assertThrows(IllegalStateException.class,()->PmsNodeApprovalProcessOwnerTest.tx.executeWithoutResult(ignored->{adapter.start(command());throw new IllegalStateException("later failure");}));
        assertEquals(before,PmsNodeApprovalProcessOwnerTest.engine.getHistoryService().createHistoricProcessInstanceQuery().count());
    }
    @Test void nativeDecisionEndpointCannotModifyTheSnapshotOrItsBusinessIdentity(){
        var result=start();var engine=PmsNodeApprovalProcessOwnerTest.engine;
        var task=engine.getTaskService().createTaskQuery().processInstanceId(result.instanceId()).singleResult();
        var target=mock(BpmTaskServiceImpl.class);var factory=new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new PmsDefaultBusinessApprovalGuard(engine.getTaskService(),engine.getRuntimeService()));
        BpmTaskServiceImpl proxy=factory.getProxy();
        var request=new BpmTaskApproveReqVO().setId(task.getId()).setVariables(Map.of("pmsBusinessSnapshot","forged"));
        assertThrows(IllegalArgumentException.class,()->proxy.approveTask(1L,request));verifyNoInteractions(target);
        proxy.approveTask(1L,new BpmTaskApproveReqVO().setId(task.getId()).setVariables(Map.of("reviewNote","同意")));
        verify(target).approveTask(eq(1L),any());assertEquals("原提交人",adapter.detail(context,result.instanceId()).submittedValues().get("receiver"));
    }
    @Test void completedCallbackRequiresEndedHistoryAndRecordsTheOriginalIdentity(){
        var result=start();var engine=PmsNodeApprovalProcessOwnerTest.engine;
        var event=new BpmProcessInstanceStatusEvent(this);event.setId(result.instanceId());event.setStatus(2);
        event.setBusinessKey(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(result.instanceId()).singleResult().getBusinessKey());
        assertThrows(IllegalArgumentException.class,()->adapter.completed(event));verifyNoInteractions(associations);
        PmsNodeApprovalProcessOwnerTest.tx.executeWithoutResult(ignored->{
            engine.getRuntimeService().setVariable(result.instanceId(),"PROCESS_STATUS",2);
            engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(result.instanceId()).singleResult().getId());
            adapter.completed(event);
        });
        verify(associations).record(new Result(context,result.definitionId(),result.instanceId(),"APPROVED",null));
    }
    @Test void specializedBusinessFormsKeepTheirOriginalEntryAndStartRestrictions(){
        info.setFormType(20);info.setFormCustomCreatePath("/pms/existing-business/new");
        assertTrue(adapter.definitions(1L).stream().noneMatch(Definition::available));
        assertThrows(IllegalArgumentException.class,this::start);
    }
}
