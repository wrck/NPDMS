package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializePolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResponseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionResponseFilePolicyProviderTest {
    @Mock SatisfactionAccessGrantMapper grantMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock SatisfactionResponseMapper responseMapper;
    @Mock SatisfactionResponseReservationService reservationService;
    @Mock SatisfactionAssistedResponseReservationService assistedReservationService;
    @Mock ProjectScopeApi projectScopeApi;

    @Test
    void returnsGrantCreatorAsAuditActorAfterOwnerAndScopeLock() {
        SatisfactionAccessGrantDO grant = new SatisfactionAccessGrantDO();
        grant.setId(1L); grant.setTenantId(7L); grant.setQuestionnaireId(11L); grant.setGrantVersion(2);
        grant.setGrantStatus("ACTIVE"); grant.setEffectiveFrom(LocalDateTime.now().minusMinutes(1));
        grant.setExpiresAt(LocalDateTime.now().plusMinutes(10)); grant.setCreator("9"); grant.setUpdater("99");
        SatisfactionQuestionnaireDO questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setId(11L); questionnaire.setTenantId(7L); questionnaire.setCollectionTaskId(10L);
        questionnaire.setQuestionnaireStatus("ACTIVE"); questionnaire.setAccessScopeVersion(3L);
        SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
        task.setId(10L); task.setTenantId(7L); task.setProjectId(20L); task.setTaskStatus("PENDING_COLLECTION");
        when(grantMapper.selectByIdForUpdate(7L, 1L)).thenReturn(grant);
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire);
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(reservationService.requireReserved(7L, grant, questionnaire, task, "req-1", 50L))
                .thenReturn(new SatisfactionResponseReservationService.Reservation(50L, 1L, 2, 11L, 9L, true));
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 3L,
                Set.of(20L), Set.of()));
        var provider = new SatisfactionResponseFilePolicyProvider(grantMapper, questionnaireMapper, taskMapper,
                responseMapper, reservationService, assistedReservationService, projectScopeApi);

        var fact = provider.initializeBusinessGrantUploadPolicy(new BusinessGrantUploadInitializePolicyQuery(
                7L, 1L, 2, 11L, "req-1", 50L, "SATISFACTION_SIGNATURE", "slot-1", 1));

        assertEquals(9L, fact.grantIssuerUserId());
        assertEquals(3L, fact.scopeVersion());
        assertEquals(Set.of("SATISFACTION_SIGNATURE"), fact.filePolicy().allowedCategoryCodes());
    }
}
