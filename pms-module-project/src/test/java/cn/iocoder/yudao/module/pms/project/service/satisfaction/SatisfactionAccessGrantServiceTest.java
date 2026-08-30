package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionAccessGrantServiceTest {
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionAccessGrantMapper grantMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @InjectMocks SatisfactionAccessGrantService service;

    @Test
    void createsOpaqueOneTimeTokenAndStoresOnlyDigest() {
        when(taskMapper.selectById(10L)).thenReturn(task());
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        when(grantMapper.selectNextVersion(7L, 11L)).thenReturn(1);
        when(grantMapper.insert((SatisfactionAccessGrantDO) any())).thenReturn(1);

        var result = service.create(7L, 30L, 10L, LocalDateTime.now().plusDays(1));

        assertTrue(result.token().length() >= 40);
        ArgumentCaptor<SatisfactionAccessGrantDO> row = ArgumentCaptor.forClass(SatisfactionAccessGrantDO.class);
        verify(grantMapper).insert(row.capture());
        assertEquals(64, row.getValue().getTokenDigest().length());
        assertTrue(!row.getValue().getTokenDigest().equals(result.token()));
    }

    @Test
    void projectScopeDenialProducesNoGrant() {
        when(taskMapper.selectById(10L)).thenReturn(task());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(99L, 3L, Set.of(99L), Set.of()));
        assertThrows(IllegalStateException.class,
                () -> service.create(7L, 30L, 10L, LocalDateTime.now().plusDays(1)));
        verify(grantMapper, never()).insert((SatisfactionAccessGrantDO) any());
    }

    private SatisfactionCollectionTaskDO task() {
        SatisfactionCollectionTaskDO row = new SatisfactionCollectionTaskDO();
        row.setId(10L); row.setTenantId(7L); row.setProjectId(20L); row.setQuestionnaireId(11L);
        row.setTaskStatus("PENDING_COLLECTION");
        return row;
    }

    private SatisfactionQuestionnaireDO questionnaire() {
        SatisfactionQuestionnaireDO row = new SatisfactionQuestionnaireDO();
        row.setId(11L); row.setTenantId(7L); row.setQuestionnaireStatus("ACTIVE"); row.setVersion(0);
        row.setFrozenQuestionJson("[]");
        return row;
    }
}
