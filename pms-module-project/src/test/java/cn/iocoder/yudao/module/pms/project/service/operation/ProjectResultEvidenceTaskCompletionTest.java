package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateCompiler;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectResultEvidenceTaskCompletionTest {
    private ResultEvidenceScanFixture f;
    private ProjectTaskPlanCompletionService completion;
    private ProjectTaskInstanceDO task;
    private ProjectTaskExecutionContractDO binding;
    private TransactionTemplate tx;
    private final ProjectTaskBusinessService business=mock(ProjectTaskBusinessService.class);
    @BeforeEach void before() throws Exception {
        f=new ResultEvidenceScanFixture();tx=new TransactionTemplate(f.recovery.transactions);
        var designer=new TemplateDesignerDocument();var stage=new TemplateDesignerDocument.StageNode();
        stage.setNodeKey("stage");stage.setCode("S1");stage.setName("stage");stage.setLifecycleStage("S1");stage.setStart(true);stage.setTerminal(true);stage.setCompletionRule(rule());designer.getStages().add(stage);
        var node=new TemplateDesignerDocument.TaskNode();node.setNodeKey("task");node.setCode("T1");node.setName("办理");node.setStageCode("S1");node.setCompletionRule(rule());
        var work=new TemplateDesignerDocument.WorkBindingSpec();work.setType("TASK_NATIVE");node.setWorkBinding(work);
        var permission=new TemplateDesignerDocument.PermissionRequirement();permission.setPolicyRef("PROJECT_TASK_NATIVE_DEFAULT");node.setPermission(permission);designer.getTasks().add(node);
        var compiled=new TemplateCompiler().compile(designer);assertTrue(compiled.valid(),()->compiled.issues().toString());
        var snapshot=compiled.snapshot();snapshot.setExecutionSchemaVersion(3);
        snapshot.getTasks().getFirst().setExecution(f.recovery.snapshot.getStages().getFirst().getExecution().deepCopy());
        f.recovery.plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));when(f.recovery.plans.selectEffective(any())).thenReturn(f.recovery.plan);
        f.recovery.round.setNodeKind("TASK");f.recovery.round.setNodeKey("task");f.recovery.round.setSubmittedAt(LocalDateTime.now());
        f.recovery.jdbc.update("UPDATE proj_result_subscription SET node_kind='TASK',node_key='task'");
        task=new ProjectTaskInstanceDO().setId(4L).setProjectId(3L).setCode("T1").setStageCode("S1").setName("办理").setStatus("PENDING_ACCEPT");
        binding=new ProjectTaskExecutionContractDO();binding.setId(30L);binding.setTenantId(1L);binding.setProjectTaskId(4L);binding.setSourceNodeKey("task");binding.setContractVersion(1);binding.setWorkBindingTypeCode("TASK_NATIVE");
        var graph=mock(ProjectRuntimeGraphMapper.class);when(graph.selectStagesForUpdate(any())).thenReturn(List.of(new ProjectStageInstanceDO().setId(5L).setCode("S1").setStatus("ACTIVE")));
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(task));when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        var rules=mock(ProjectRuleEvaluationService.class);when(rules.evaluate(anyString(),any(),any())).thenAnswer(call->new RuleEvaluation(call.getArgument(0),RuleEvaluation.Outcome.MATCHED,null,List.of(),List.of(),List.of()));
        var service=new ProjectTaskPlanCompletionService(f.recovery.plans,f.recovery.rounds,graph,mock(ProjectGateReferenceInstanceMapper.class),mock(ProjectRuntimeRuleEvaluator.class),rules,new ProjectRuleCompiler(),business,mock(ProjectNodeExecutionApi.class),mock(ProjectGateRuleService.class));
        var guard=f.recovery.proxy(new ProjectResultEvidenceGuard(f.recovery.subscriptions,f.evidence,new ProjectResultEvidenceConsistency(f.recovery.sources,f.recovery.journal),f.recovery.outbox));
        @SuppressWarnings("unchecked") ObjectProvider<ProjectResultEvidenceGuard> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(guard);ReflectionTestUtils.setField(service,"resultEvidence",provider);
        completion=f.recovery.proxy(service);
    }
    @AfterEach void after(){f.close();}
    @Test void automaticCompletionUsesTheSameEvidenceGateAsManualCompletion() {
        assertFalse(automatic().matched());assertFalse(manual().matched());f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        assertTrue(automatic().matched());assertTrue(manual().matched());
        var refs=(List<?>)automatic().evidence().get("subscriptionEvidence");assertEquals(1,refs.size());
        assertEquals(f.scan().getId(),((ResultEvidenceReceipt)refs.getFirst()).scanId());verifyNoInteractions(business);
    }
    @Test void aResultSubscriptionDoesNotSilentlyRemoveNativeSubmissionRequirements() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();f.recovery.round.setSubmittedAt(null);
        assertFalse(automatic().matched());assertTrue(automatic().unmet().contains("CURRENT_ROUND_SUBMISSION_REQUIRED"));
    }
    @Test void changedSourceAfterTheScanStopsBothCompletionChannels() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();f.recovery.committed=8;
        assertFalse(automatic().matched());assertFalse(manual().matched());
        assertTrue(automatic().unmet().contains("RESULT_EVIDENCE_CHANGED"));
    }
    @Test void legacyAcceptanceCompletionCannotGainASubscriptionWithoutItsEvidenceConsumer() {
        var snapshot=TemplateExecutionSnapshotReader.read(f.recovery.plan.getExecutionSnapshot());
        var target=snapshot.getTasks().getFirst();
        target.getBinding().setTargetContextCode("ACC");target.getBinding().setTargetObjectType("AcceptanceActivity");
        target.getBinding().setTargetObjectKey("native-legacy-activity");
        assertTrue(assertThrows(IllegalArgumentException.class,()->TemplateVersionSnapshot.validate(snapshot)).getMessage()
                .contains("LEGACY_ACCEPTANCE_SUBSCRIPTION_UNSUPPORTED"));
        target.setExecution(null);
        assertDoesNotThrow(()->TemplateVersionSnapshot.validate(snapshot));
        // C0报告结果采用独立的ACCEPTANCE类型，不属于旧专用完成通道。
        target.getBinding().setTargetObjectType("ACCEPTANCE");target.setExecution(f.recovery.snapshot.getStages().getFirst().getExecution());
        assertDoesNotThrow(()->TemplateVersionSnapshot.validate(snapshot));
    }
    private ProjectTaskPlanCompletionService.Result automatic(){return tx.execute(status->completion.evaluateAutomatically(f.recovery.project,task,binding));}
    private ProjectTaskPlanCompletionService.Result manual(){return tx.execute(status->completion.evaluate(new TaskActionCommand(4L,1,"COMPLETE",null,30L,1,null,null,null,null,null,"key","digest"),f.recovery.project,task,binding,new TaskWorkbenchActor(1L,2L,"test")));}
    private TemplateDesignerDocument.RuleSpec rule(){var rule=new TemplateDesignerDocument.RuleSpec();rule.setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));return rule;}
}
