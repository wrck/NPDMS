package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SatisfactionIndependentRecollectTest {
    @Test void failedDirectCollectionReusesFrozenQuestionnaireInANewRoundAndPreservesPriorEvidence() {
        var tasks=mock(SatisfactionCollectionTaskMapper.class); var questionnaires=mock(SatisfactionQuestionnaireMapper.class);
        var results=mock(SatisfactionResultMapper.class); var remediations=mock(SatisfactionRemediationFactMapper.class);
        var scope=mock(ProjectScopeApi.class); var bindings=mock(ProjectWorkBindingFactApi.class);
        var service=new SatisfactionTaskManagementService(tasks,questionnaires,results,remediations,
                mock(ProjectParticipantFactApi.class),scope,bindings,mock(PlatformCommandExecutionApi.class));
        var independent=mock(IndependentSatisfactionService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"independent",independent);
        var prior=new SatisfactionCollectionTaskDO(); prior.setId(10L); prior.setTenantId(7L); prior.setProjectId(80L);
        prior.setOriginKind("DIRECT"); prior.setOriginKey("19:create"); prior.setOriginSnapshot("{\"actorId\":19}");
        prior.setResultId(12L); prior.setQuestionnaireId(11L); prior.setTaskRevisionNo(1); prior.setCollectionKey("SAT-10"); prior.setAssignedToUserId(19L);
        var failed=new SatisfactionResultDO(); failed.setId(12L); failed.setCollectionTaskId(10L); failed.setPassed(false); failed.setResultStatus("FAILED");
        var questionnaire=new SatisfactionQuestionnaireDO(); questionnaire.setId(11L); questionnaire.setTemplateId(20L);
        questionnaire.setTemplateRevisionId(21L); questionnaire.setTemplateVersion(1); questionnaire.setFrozenQuestionJson("frozen-original");
        questionnaire.setFrozenThreshold(new BigDecimal("80")); questionnaire.setRuleVersion("SUM_V1"); questionnaire.setAccessScopeVersion(3L);
        when(tasks.selectById(10L)).thenReturn(prior); when(tasks.selectByIdForUpdate(7L,10L)).thenReturn(prior);
        when(results.selectByIdForUpdate(7L,12L)).thenReturn(failed); when(questionnaires.selectByIdForUpdate(7L,11L)).thenReturn(questionnaire);
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(80L,3L,Set.of(80L),Set.of()));
        when(remediations.insert(any(SatisfactionRemediationFactDO.class))).thenReturn(1);
        when(tasks.insert(any(SatisfactionCollectionTaskDO.class))).thenReturn(1);
        when(questionnaires.insert(any(SatisfactionQuestionnaireDO.class))).thenReturn(1);
        var command=new SatisfactionTaskManagementService.Recollect(12L,"remediate-1","整改完成",null);
        var created=service.recollectOnce(7L,19L,10L,command);
        assertEquals(2,created.revisionNo()); assertEquals(10L,created.priorTaskId()); assertNull(created.projectTaskVersion());
        var next=org.mockito.ArgumentCaptor.forClass(SatisfactionCollectionTaskDO.class);
        var frozen=org.mockito.ArgumentCaptor.forClass(SatisfactionQuestionnaireDO.class);
        verify(tasks).insert(next.capture()); verify(questionnaires).insert(frozen.capture());
        assertEquals("DIRECT",next.getValue().getOriginKind()); assertNull(next.getValue().getProjectTaskId()); assertNull(next.getValue().getDeliverableId());
        assertNotEquals(prior.getOriginKey(),next.getValue().getOriginKey()); assertEquals(prior.getOriginSnapshot(),next.getValue().getOriginSnapshot());
        assertEquals("frozen-original",frozen.getValue().getFrozenQuestionJson()); assertEquals(21L,frozen.getValue().getTemplateRevisionId());
        assertEquals(12L,prior.getResultId()); assertEquals("FAILED",failed.getResultStatus());
        verify(tasks,never()).updateById(any(SatisfactionCollectionTaskDO.class)); verifyNoInteractions(bindings);
        failed.setPassed(true); failed.setResultStatus("EFFECTIVE");
        assertThrows(IllegalStateException.class,()->service.recollectOnce(7L,19L,10L,command));
    }
}
