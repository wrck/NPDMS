package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SatisfactionResponseSubmissionServiceTest {
    @Mock SatisfactionAccessGrantMapper grantMapper;
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionResponseMapper responseMapper;
    @Mock SatisfactionResponseFileMapper responseFileMapper;
    @InjectMocks SatisfactionResponseSubmissionService service;

    @Test
    void persistsImmutableResponseFilesAndMovesTaskToDecision() {
        arrangeActive();
        when(responseMapper.selectNextResponseNo(7L, 11L)).thenReturn(1);
        when(responseMapper.insert((SatisfactionResponseDO) any())).thenReturn(1);
        when(responseFileMapper.insert((SatisfactionResponseFileDO) any())).thenReturn(1);
        when(grantMapper.consumeIfActive(any())).thenReturn(1);
        when(taskMapper.moveToPendingDecision(any())).thenReturn(1);

        var result = service.submit(command());

        assertFalse(result.replayed());
        verify(responseMapper).insert((SatisfactionResponseDO) any());
        verify(responseFileMapper).insert((SatisfactionResponseFileDO) any());
        verify(grantMapper).consumeIfActive(any());
        verify(taskMapper).moveToPendingDecision(any());
    }

    @Test
    void missingSignatureRejectedBeforeAnyWrite() {
        var invalid = new SatisfactionResponseSubmissionService.Command(7L, "token", "req-1",
                "PUBLIC_LINK", "customer-1", null, "{}", List.of(), "grant:1");
        assertThrows(IllegalArgumentException.class, () -> service.submit(invalid));
        verifyNoInteractions(grantMapper, taskMapper, questionnaireMapper, responseMapper, responseFileMapper);
    }

    @Test
    void sameRequestWithDifferentPayloadConflictsWithoutWrite() {
        arrangeActive();
        SatisfactionResponseDO existing = new SatisfactionResponseDO();
        existing.setId(50L); existing.setQuestionnaireId(11L); existing.setAnswerSnapshot("{\"score\":1}");
        existing.setSubmitChannel("PUBLIC_LINK"); existing.setCustomerContactRef("customer-1");
        when(responseMapper.selectByIdentityForUpdate(any())).thenReturn(existing);
        assertThrows(IllegalStateException.class, () -> service.submit(command()));
        verify(responseMapper, never()).insert((SatisfactionResponseDO) any());
        verify(responseFileMapper, never()).insert((SatisfactionResponseFileDO) any());
    }

    private void arrangeActive() {
        SatisfactionAccessGrantDO grant = new SatisfactionAccessGrantDO();
        grant.setId(1L); grant.setTenantId(7L); grant.setQuestionnaireId(11L); grant.setGrantStatus("ACTIVE");
        grant.setEffectiveFrom(LocalDateTime.now().minusMinutes(1)); grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        grant.setVersion(0);
        SatisfactionQuestionnaireDO questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setId(11L); questionnaire.setTenantId(7L); questionnaire.setCollectionTaskId(10L);
        questionnaire.setQuestionnaireStatus("ACTIVE");
        SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
        task.setId(10L); task.setTenantId(7L); task.setTaskStatus("PENDING_COLLECTION"); task.setVersion(0);
        when(grantMapper.selectByDigestForUpdate(any())).thenReturn(grant);
        when(questionnaireMapper.selectById(11L)).thenReturn(questionnaire);
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire);
    }

    private SatisfactionResponseSubmissionService.Command command() {
        return new SatisfactionResponseSubmissionService.Command(7L, "token", "req-1", "PUBLIC_LINK",
                "customer-1", null, "{\"score\":5}", List.of(new SatisfactionResponseSubmissionService.FileFact(
                "SIGNATURE", 1, 100L, 1, "signature-1", 1, 1, 1, 3L, "a".repeat(64))), "grant:1");
    }
}
