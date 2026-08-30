package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFilePolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SatisfactionResultFilePolicyProviderTest {
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionResponseMapper responseMapper;
    @Mock SatisfactionResultMapper resultMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @InjectMocks SatisfactionResultFilePolicyProvider provider;

    @Test
    void validatesOwnerChainAndReturnsExactTreeVersion() {
        arrangeOwnerChain();
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 9L, Set.of(20L), Set.of()));
        var fact = provider.lockAndRevalidateGeneratedBusinessFile(query());
        assertTrue(fact.allowed());
        assertEquals(9L, fact.scopeVersion());
        verify(resultMapper).selectByIdForUpdate(7L, 40L);
    }

    @Test
    void wrongAssigneeFailsBeforeScopeLookup() {
        SatisfactionCollectionTaskDO task = task();
        task.setAssignedToUserId(99L);
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        assertThrows(IllegalStateException.class,
                () -> provider.lockAndRevalidateGeneratedBusinessFile(query()));
        verifyNoInteractions(questionnaireMapper, responseMapper, resultMapper, projectScopeApi);
    }

    @Test
    void occupiedResultFailsBeforeProjectScope() {
        arrangeOwnerChain();
        when(resultMapper.selectByIdForUpdate(7L, 40L)).thenReturn(new SatisfactionResultDO());
        assertThrows(IllegalStateException.class,
                () -> provider.lockAndRevalidateGeneratedBusinessFile(query()));
        verifyNoInteractions(projectScopeApi);
    }

    private void arrangeOwnerChain() {
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task());
        SatisfactionQuestionnaireDO q = new SatisfactionQuestionnaireDO();
        q.setId(11L); q.setCollectionTaskId(10L);
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(q);
        SatisfactionResponseDO response = new SatisfactionResponseDO();
        response.setId(12L); response.setQuestionnaireId(11L);
        when(responseMapper.selectByIdForUpdate(7L, 12L)).thenReturn(response);
    }

    private SatisfactionCollectionTaskDO task() {
        SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
        task.setId(10L); task.setProjectId(20L); task.setQuestionnaireId(11L); task.setAssignedToUserId(30L);
        task.setTaskStatus("PENDING_DECISION"); task.setVersion(4);
        return task;
    }

    private GeneratedBusinessFilePolicyRevalidationQuery query() {
        return new GeneratedBusinessFilePolicyRevalidationQuery(7L, 30L, 40L, 10L, 11L, 12L, 4,
                "ACC", "SATISFACTION_RESULT", "SATISFACTION_RESULT_DOCUMENT",
                "satisfaction-result-40", FileActionCodes.UPLOAD, 9L);
    }
}
