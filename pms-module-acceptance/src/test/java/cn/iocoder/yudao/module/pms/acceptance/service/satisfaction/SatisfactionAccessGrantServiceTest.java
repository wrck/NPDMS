package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
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
    @Mock CompletionSatisfactionBundleService completionBundles;
    @Mock cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResponseMapper submittedResponses;
    @Mock cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.CompletionSatisfactionSigningMapper submittedSignings;
    @InjectMocks SatisfactionAccessGrantService service;

    @Test void newJointGrantStoresTheExplicitPairingBeforeItIsIssued() {
        when(taskMapper.selectById(10L)).thenReturn(task());
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        when(completionBundles.bind(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(30L), any(), org.mockito.ArgumentMatchers.eq(40L))).thenReturn(50L);
        when(grantMapper.selectNextVersion(7L, 11L)).thenReturn(1);
        when(grantMapper.insert((SatisfactionAccessGrantDO) any())).thenReturn(1);
        service.create(7L, 30L, 10L, LocalDateTime.now().plusDays(1), 40L);
        ArgumentCaptor<SatisfactionAccessGrantDO> row = ArgumentCaptor.forClass(SatisfactionAccessGrantDO.class);
        verify(grantMapper).insert(row.capture());
        assertEquals(50L, row.getValue().getCompletionBundleId());
    }

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

    @Test void consumedJointLinkReturnsOnlyItsSavedAnswersAndSignatureEvenAfterSubmissionDeadline() {
        var grant=consumedGrant();var questionnaire=questionnaire();questionnaire.setFrozenQuestionJson("{\"questions\":[]}");
        when(grantMapper.selectByDigest(any())).thenReturn(grant);when(questionnaireMapper.selectById(11L)).thenReturn(questionnaire);
        var signing=new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.CompletionSatisfactionSigningDO();
        signing.setGrantId(60L);signing.setResponseId(70L);signing.setSignatureImageDataUrl("data:image/png;base64,canonical");
        when(submittedSignings.selectByQuestionnaire(any())).thenReturn(signing);
        var response=new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResponseDO();
        response.setTenantId(7L);response.setAnswerSnapshot("{\"answers\":[]}");when(submittedResponses.selectById(70L)).thenReturn(response);
        var viewed=service.inspect(7L,"opaque-token");
        assertTrue(viewed.submitted());assertEquals(response.getAnswerSnapshot(),viewed.answerSnapshot());
        assertEquals(signing.getSignatureImageDataUrl(),viewed.signatureImageDataUrl());
    }

    @Test void consumedLinkCannotReadAnotherGrantsSigning() {
        var grant=consumedGrant();when(grantMapper.selectByDigest(any())).thenReturn(grant);
        when(questionnaireMapper.selectById(11L)).thenReturn(questionnaire());
        var signing=new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.CompletionSatisfactionSigningDO();signing.setGrantId(61L);
        when(submittedSignings.selectByQuestionnaire(any())).thenReturn(signing);
        assertThrows(IllegalStateException.class,()->service.inspect(7L,"opaque-token"));
        org.mockito.Mockito.verifyNoInteractions(submittedResponses);
    }

    private SatisfactionAccessGrantDO consumedGrant(){
        var row=new SatisfactionAccessGrantDO();row.setId(60L);row.setTenantId(7L);row.setQuestionnaireId(11L);
        row.setCompletionBundleId(50L);row.setGrantStatus("CONSUMED");row.setEffectiveFrom(LocalDateTime.now().minusDays(3));
        row.setExpiresAt(LocalDateTime.now().minusDays(1));return row;
    }

    private SatisfactionQuestionnaireDO questionnaire() {
        SatisfactionQuestionnaireDO row = new SatisfactionQuestionnaireDO();
        row.setId(11L); row.setTenantId(7L); row.setQuestionnaireStatus("ACTIVE"); row.setVersion(0L);
        row.setFrozenQuestionJson("[]");
        return row;
    }
}
