package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SatisfactionResponseReservationServiceTest {
    @Mock SatisfactionAccessGrantMapper grantMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock PlatformCommandExecutionApi commandExecutionApi;

    @Test
    void reservesWithGrantIssuerAndStableScope() {
        SatisfactionAccessGrantDO grant = grant();
        SatisfactionQuestionnaireDO questionnaire = questionnaire();
        SatisfactionCollectionTaskDO task = task();
        when(grantMapper.selectByDigestForUpdate(any())).thenReturn(grant);
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire);
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(commandExecutionApi.execute(any(), anyString(), eq(SatisfactionResponseReservationService.Reservation.class),
                any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") java.util.function.Supplier<SatisfactionResponseReservationService.Reservation> op =
                    invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, op.get());
        });
        var service = new SatisfactionResponseReservationService(grantMapper, questionnaireMapper, taskMapper,
                commandExecutionApi);

        var result = service.reserveFromToken(7L, "secret", "request-1");

        assertEquals(9L, result.grantIssuerUserId());
        assertEquals(11L, result.questionnaireId());
        ArgumentCaptor<PlatformCommandExecutionApi.IdempotencyScope> scope =
                ArgumentCaptor.forClass(PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), anyString(), any(), any(), any());
        assertEquals(SatisfactionResponseReservationService.SCOPE, scope.getValue().scopeCode());
        assertEquals("request-1", scope.getValue().key());
        assertEquals(9L, scope.getValue().actorId());
    }

    @Test
    void rejectsNonUserGrantCreatorBeforeIdempotencyWrite() {
        SatisfactionAccessGrantDO grant = grant();
        grant.setCreator("anonymous");
        when(grantMapper.selectByDigestForUpdate(any())).thenReturn(grant);
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire());
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task());
        var service = new SatisfactionResponseReservationService(grantMapper, questionnaireMapper, taskMapper,
                commandExecutionApi);

        assertThrows(IllegalArgumentException.class,
                () -> service.reserveFromToken(7L, "secret", "request-1"));
        verifyNoInteractions(commandExecutionApi);
    }

    private SatisfactionAccessGrantDO grant() {
        SatisfactionAccessGrantDO row = new SatisfactionAccessGrantDO();
        row.setId(1L); row.setTenantId(7L); row.setQuestionnaireId(11L); row.setGrantVersion(2);
        row.setGrantStatus("ACTIVE"); row.setEffectiveFrom(LocalDateTime.now().minusMinutes(1));
        row.setExpiresAt(LocalDateTime.now().plusMinutes(10)); row.setCreator("9");
        return row;
    }

    private SatisfactionQuestionnaireDO questionnaire() {
        SatisfactionQuestionnaireDO row = new SatisfactionQuestionnaireDO();
        row.setId(11L); row.setTenantId(7L); row.setCollectionTaskId(10L);
        row.setQuestionnaireStatus("ACTIVE");
        return row;
    }

    private SatisfactionCollectionTaskDO task() {
        SatisfactionCollectionTaskDO row = new SatisfactionCollectionTaskDO();
        row.setId(10L); row.setTenantId(7L); row.setTaskStatus("PENDING_COLLECTION");
        return row;
    }
}
