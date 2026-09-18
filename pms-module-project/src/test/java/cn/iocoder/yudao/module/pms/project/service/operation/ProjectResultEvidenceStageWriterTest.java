package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.service.projectplan.*;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.ProjectStageApprovalService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 原Stage完成Service消费真实扫描/Guard，范围外仓储、规则执行和计时器使用替身。 */
class ProjectResultEvidenceStageWriterTest {
    private ResultEvidenceScanFixture f;
    private ProjectStageCompletionService writer;
    private final ProjectStageInstanceMapper stages=mock(ProjectStageInstanceMapper.class);
    private final ProjectRuntimeGraphMapper graph=mock(ProjectRuntimeGraphMapper.class);
    private final ProjectRuleEvaluationService rules=mock(ProjectRuleEvaluationService.class);
    private final OperationAuditApi audit=mock(OperationAuditApi.class);
    private final ProjectTaskBusinessService business=mock(ProjectTaskBusinessService.class);
    @BeforeEach void before() throws Exception {
        f=new ResultEvidenceScanFixture();f.recovery.round.setRoundNo(1);f.recovery.round.setVersion(0);
        var projects=mock(ProjectTaskRuntimeMapper.class);when(projects.selectProjectForCommandForUpdate(any())).thenReturn(f.recovery.project);
        when(f.recovery.plans.selectEffective(any())).thenReturn(f.recovery.plan);
        var stage=new ProjectStageInstanceDO().setId(4L).setProjectId(3L).setCode("S1").setStatus("ACTIVE").setVersion(1);
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));when(graph.selectTasksForUpdate(any())).thenReturn(List.of());when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        when(stages.updateStatusIfMatch(any())).thenReturn(1);when(f.recovery.rounds.finishIfActive(any())).thenReturn(1);
        when(rules.evaluate(anyString(),any(),any())).thenAnswer(call->new RuleEvaluation(call.getArgument(0),RuleEvaluation.Outcome.MATCHED,null,List.of(),List.of(),List.of()));
        var service=new ProjectStageCompletionService(projects,f.recovery.plans,f.recovery.rounds,graph,stages,mock(ProjectGateReferenceInstanceMapper.class),
                rules,new ProjectRuleCompiler(),mock(ProjectRuntimeRuleEvaluator.class),audit,mock(ProjectNodeExecutionApi.class),business,
                mock(ProjectStageGateProcessOwnerApi.class),mock(ProjectStageApprovalService.class));
        ReflectionTestUtils.setField(service,"timers",mock(ProjectRuleTimerScheduler.class));ReflectionTestUtils.setField(service,"currentStages",mock(ProjectCurrentStageService.class));
        var guard=f.recovery.proxy(new ProjectResultEvidenceGuard(f.recovery.subscriptions,f.evidence,new ProjectResultEvidenceConsistency(f.recovery.sources,f.recovery.journal),f.recovery.outbox));
        @SuppressWarnings("unchecked") ObjectProvider<ProjectResultEvidenceGuard> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(guard);
        ReflectionTestUtils.setField(service,"resultEvidence",provider);writer=f.recovery.proxy(service);
    }
    @AfterEach void after(){f.close();}
    @Test void satisfiedRulesCannotCompleteAPureSubscriptionWithoutItsEvidence() {
        assertEquals(0,writer.completeStage(3L,4L,0L,"evidence").completed());verifyNoInteractions(stages,audit,business);
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        assertEquals(1,writer.completeStage(3L,4L,0L,"evidence").completed());
        verify(f.recovery.rounds).finishIfActive(argThat(command->{
            var receipt=JsonUtils.parseObject(command.resultSnapshot(),StageCompletionEvidence.class);
            return receipt.subscriptionEvidence().size()==1 && receipt.subscriptionEvidence().getFirst().scanId().equals(f.scan().getId());
        }));verifyNoInteractions(business);
    }
    @Test void anOwnerChangeBetweenScanAndFormalCompletionPreventsTheWrite() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();f.recovery.committed=8;
        assertEquals(0,writer.completeStage(3L,4L,0L,"changed").completed());verifyNoInteractions(stages,audit,business);
    }
    @Test void evidenceDoesNotBypassStageRulesOrUnfinishedStartedTasks() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        when(rules.evaluate(anyString(),any(),any())).thenAnswer(call->new RuleEvaluation(call.getArgument(0),RuleEvaluation.Outcome.NOT_MATCHED,"RULE_FALSE",List.of(),List.of(),List.of()));
        assertEquals(0,writer.completeStage(3L,4L,0L,"rule").completed());
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setId(6L).setStageCode("S1").setStatus("IN_PROGRESS")));
        assertEquals(0,writer.completeStage(3L,4L,0L,"children").completed());verifyNoInteractions(stages,audit,business);
    }
    @Test void failedFormalCompareAndSetCannotBeReportedAsCompleted() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();when(f.recovery.rounds.finishIfActive(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->writer.completeStage(3L,4L,0L,"stale"));verifyNoInteractions(audit);
    }
}
