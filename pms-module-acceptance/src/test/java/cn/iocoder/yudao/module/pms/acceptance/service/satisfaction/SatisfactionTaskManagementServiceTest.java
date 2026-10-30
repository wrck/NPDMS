package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SatisfactionTaskManagementServiceTest {
    private final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    private final SatisfactionQuestionnaireMapper questionnaires = mock(SatisfactionQuestionnaireMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final SatisfactionTaskManagementService service = new SatisfactionTaskManagementService(
            tasks, questionnaires, null, null, null, scope, null, null);

    @Test
    void returnsFrozenQuestionsFromTaskQuestionnaire() {
        when(scope.resolveAllCurrent(any())).thenReturn(Set.of(10L));
        var task = new SatisfactionCollectionTaskDO();
        task.setId(20L); task.setProjectId(10L); task.setQuestionnaireId(30L);
        var questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setFrozenQuestionJson("{\"schemaVersion\":1,\"questions\":[{\"code\":\"frozen\"}]}");
        when(tasks.selectByScope(any())).thenReturn(List.of(task));
        when(questionnaires.selectById(30L)).thenReturn(questionnaire);
        assertEquals(questionnaire.getFrozenQuestionJson(), service.list(1L, 2L, null).getFirst().frozenQuestions());
    }

    @Test
    void emptyScopeDoesNotReadQuestions() {
        when(scope.resolveAllCurrent(any())).thenReturn(Set.of());
        assertTrue(service.list(1L, 2L, null).isEmpty());
        verifyNoInteractions(tasks, questionnaires);
    }

    @Test
    void crossTenantTaskDoesNotExposeQuestions() {
        var task = new SatisfactionCollectionTaskDO();
        task.setTenantId(9L); task.setAssignedToUserId(2L);
        when(tasks.selectById(20L)).thenReturn(task);
        assertThrows(IllegalStateException.class, () -> service.get(1L, 2L, 20L));
        verifyNoInteractions(questionnaires, scope);
    }
}
