package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResponseDO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResponseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class SatisfactionResponseFilePolicyProviderTest {
    @Mock SatisfactionAccessGrantMapper grantMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock SatisfactionResponseMapper responseMapper;
    @Mock SatisfactionResponseReservationService reservationService;
    @Mock SatisfactionAssistedResponseReservationService assistedReservationService;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock PermissionApi permissionApi;
    @InjectMocks SatisfactionResponseFilePolicyProvider provider;

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
                responseMapper, reservationService, assistedReservationService, projectScopeApi, permissionApi);

        var fact = provider.initializeBusinessGrantUploadPolicy(new BusinessGrantUploadInitializePolicyQuery(
                7L, 1L, 2, 11L, "req-1", 50L, "SATISFACTION_SIGNATURE", "slot-1", 1));

        assertEquals(9L, fact.grantIssuerUserId());
        assertEquals(3L, fact.scopeVersion());
        assertEquals(Set.of("SATISFACTION_SIGNATURE"), fact.filePolicy().allowedCategoryCodes());
    }

    @Test
    void downloadsHistoricalSignatureAndAttachmentForAuthorizedProjectViewer() {
        arrangeReadChain(false);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        for (String purpose : Set.of("SATISFACTION_SIGNATURE", "SATISFACTION_ATTACHMENT")) {
            var fact = provider.inspect(downloadQuery(purpose));
            assertTrue(fact.allowed());
            assertEquals(3L, fact.scopeVersion());
            assertEquals(Set.of(purpose), fact.allowedCategoryCodes());
        }
    }

    @Test
    void refusesDownloadWithoutBusinessPermissionBeforeReadingObjects() {
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_SIGNATURE")).allowed());
        verifyNoInteractions(responseMapper, questionnaireMapper, taskMapper, projectScopeApi);
    }

    @Test
    void refusesForeignTenantResponse() {
        when(permissionApi.hasAnyPermissions(9L, "pms:acceptance:satisfaction:download")).thenReturn(true);
        var response = new SatisfactionResponseDO(); response.setId(50L); response.setTenantId(8L);
        when(responseMapper.selectById(50L)).thenReturn(response);
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_SIGNATURE")).allowed());
        verifyNoInteractions(questionnaireMapper, taskMapper, projectScopeApi);
    }

    @Test
    void refusesForeignTenantQuestionnaire() {
        when(permissionApi.hasAnyPermissions(9L, "pms:acceptance:satisfaction:download")).thenReturn(true);
        var response = new SatisfactionResponseDO(); response.setId(50L); response.setTenantId(7L); response.setQuestionnaireId(11L);
        var questionnaire = new SatisfactionQuestionnaireDO(); questionnaire.setId(11L); questionnaire.setTenantId(8L);
        when(responseMapper.selectById(50L)).thenReturn(response);
        when(questionnaireMapper.selectById(11L)).thenReturn(questionnaire);
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_SIGNATURE")).allowed());
        verifyNoInteractions(taskMapper, projectScopeApi);
    }

    @Test
    void refusesForeignTenantTask() {
        var task = arrangeReadChain(false); task.setTenantId(8L);
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_SIGNATURE")).allowed());
        verifyNoInteractions(projectScopeApi);
    }

    @Test
    void refusesEmptyOrOtherProjectScope() {
        arrangeReadChain(false);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(20L, 3L, Set.of(), Set.of()),
                new ProjectScopeResult(20L, 3L, Set.of(21L), Set.of()));
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_SIGNATURE")).allowed());
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_ATTACHMENT")).allowed());
    }

    @Test
    void locksOwnerChainAndRechecksCurrentScopeBeforeTicketConsumption() {
        arrangeReadChain(true);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        assertTrue(provider.lockAndRevalidate(downloadRevalidation()).allowed());
        verify(projectScopeApi).lockAndRevalidate(argThat(query -> query.subjectUserId().equals(9L)
                && query.anchorProjectId().equals(20L) && query.actionCode().equals(ProjectScopeApi.ACTION_VIEW)
                && query.expectedScopeVersion().equals(3L)));
    }

    @Test
    void rejectsScopeRemovalOrVersionChangeAfterTicketIssue() {
        arrangeReadChain(true);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(20L, 3L, Set.of(), Set.of()),
                new ProjectScopeResult(20L, 4L, Set.of(20L), Set.of()));
        assertFalse(provider.lockAndRevalidate(downloadRevalidation()).allowed());
        assertFalse(provider.lockAndRevalidate(downloadRevalidation()).allowed());
    }

    @Test
    void rejectsPermissionRemovalAfterTicketIssue() {
        assertFalse(provider.lockAndRevalidate(downloadRevalidation()).allowed());
        verifyNoInteractions(responseMapper, questionnaireMapper, taskMapper, projectScopeApi);
    }

    @Test
    void doesNotAuthorizeOtherFileCategoriesOrWriteActionsThroughReadPolicy() {
        assertFalse(provider.inspect(downloadQuery("SATISFACTION_RESULT_DOCUMENT")).allowed());
        assertFalse(provider.inspect(new FileBusinessObjectPolicyQuery(7L, 9L, "ACC", "SATISFACTION_RESPONSE",
                "50", "SATISFACTION_SIGNATURE", "signature-50", "UPLOAD")).allowed());
        verifyNoInteractions(permissionApi, responseMapper, questionnaireMapper, taskMapper, projectScopeApi);
    }

    private FileBusinessObjectPolicyQuery downloadQuery(String purpose) {
        return new FileBusinessObjectPolicyQuery(7L, 9L, "ACC", "SATISFACTION_RESPONSE", "50", purpose,
                "signature-50", "DOWNLOAD");
    }

    private FileBusinessObjectPolicyRevalidationQuery downloadRevalidation() {
        return new FileBusinessObjectPolicyRevalidationQuery(7L, 9L, "ACC", "SATISFACTION_RESPONSE", "50",
                "SATISFACTION_SIGNATURE", "signature-50", "DOWNLOAD", 3L);
    }

    private SatisfactionCollectionTaskDO arrangeReadChain(boolean locked) {
        when(permissionApi.hasAnyPermissions(9L, "pms:acceptance:satisfaction:download")).thenReturn(true);
        var response = new SatisfactionResponseDO(); response.setId(50L); response.setTenantId(7L); response.setQuestionnaireId(11L);
        var questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setId(11L); questionnaire.setTenantId(7L); questionnaire.setCollectionTaskId(10L);
        questionnaire.setQuestionnaireStatus("SUPERSEDED");
        var task = new SatisfactionCollectionTaskDO();
        task.setId(10L); task.setTenantId(7L); task.setProjectId(20L); task.setAssignedToUserId(99L); task.setTaskStatus("FAILED");
        if (locked) {
            when(responseMapper.selectByIdForUpdate(7L, 50L)).thenReturn(response);
            when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire);
            when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        } else {
            when(responseMapper.selectById(50L)).thenReturn(response);
            when(questionnaireMapper.selectById(11L)).thenReturn(questionnaire);
            when(taskMapper.selectById(10L)).thenReturn(task);
        }
        return task;
    }
}
