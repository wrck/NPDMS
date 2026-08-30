package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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
    @Mock SatisfactionResponseReservationService reservationService;
    @Mock FileArtifactApi fileArtifactApi;
    @InjectMocks SatisfactionResponseSubmissionService service;

    @Test
    void persistsImmutableResponseFilesAndMovesTaskToDecision() {
        arrangeActive();
        when(responseMapper.selectNextResponseNo(7L, 11L)).thenReturn(1);
        when(responseMapper.insert((SatisfactionResponseDO) any())).thenReturn(1);
        when(responseFileMapper.insert((SatisfactionResponseFileDO) any())).thenReturn(1);
        when(grantMapper.consumeIfActive(any())).thenReturn(1);
        when(taskMapper.moveToPendingDecision(any())).thenReturn(1);
        when(reservationService.requireReserved(anyLong(), any(), any(), any(), anyString(), anyLong()))
                .thenReturn(new SatisfactionResponseReservationService.Reservation(50L, 1L, 1, 11L, 9L, true));
        when(fileArtifactApi.lockAndRevalidateBusinessGrantFiles(any())).thenReturn(List.of(
                new BusinessGrantFileFact("SATISFACTION_SIGNATURE", "slot-1", 1,
                        new FileArtifactVersionFact(100L, 1, "slot-1", "SATISFACTION_SIGNATURE",
                                "sign.png", 3L, "image/png", "a".repeat(64), "AVAILABLE", "ACTIVE",
                                new FileFactVersion(1, 1, 1), 3L))));

        var result = service.submit(command());

        assertFalse(result.replayed());
        assertEquals(new BigDecimal("5.0"), result.score());
        assertTrue(result.passed());
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

    @Test
    void replayWithDifferentFileSetConflictsWithoutWrite() {
        arrangeActive();
        SatisfactionResponseDO existing = existingResponse(1L);
        when(responseMapper.selectByIdentityForUpdate(any())).thenReturn(existing);
        when(responseFileMapper.selectListByResponse(any())).thenReturn(List.of(existingFile(100L)));
        var differentFile = command().files().getFirst();
        var changed = new SatisfactionResponseSubmissionService.FileFact(differentFile.role(),
                differentFile.fileSlotKey(), differentFile.sequence(), 999L, differentFile.versionNo(),
                differentFile.referenceKey(), differentFile.artifactVersion(), differentFile.referenceVersion(),
                differentFile.availabilityVersion(), differentFile.scopeVersion(), differentFile.sha256());
        var command = new SatisfactionResponseSubmissionService.Command(7L, "token", "req-1", 50L,
                "PUBLIC_LINK", "customer-1", null,
                "{\"answers\":[{\"questionCode\":\"Q1\",\"value\":\"YES\"}]}",
                List.of(changed), "grant:1");

        assertThrows(IllegalStateException.class, () -> service.submit(command));

        verify(responseMapper, never()).insert((SatisfactionResponseDO) any());
        verify(responseFileMapper, never()).insert((SatisfactionResponseFileDO) any());
    }

    @Test
    void differentGrantCannotClaimExistingResponse() {
        arrangeActive();
        SatisfactionAccessGrantDO otherGrant = activeGrant(2L);
        when(grantMapper.selectByDigestForUpdate(any())).thenReturn(otherGrant);
        when(responseMapper.selectByIdentityForUpdate(any())).thenReturn(existingResponse(1L));

        assertThrows(IllegalStateException.class, () -> service.submit(command()));

        verify(responseFileMapper, never()).selectListByResponse(any());
        verify(responseMapper, never()).insert((SatisfactionResponseDO) any());
    }

    private void arrangeActive() {
        SatisfactionAccessGrantDO grant = activeGrant(1L);
        SatisfactionQuestionnaireDO questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setId(11L); questionnaire.setTenantId(7L); questionnaire.setCollectionTaskId(10L);
        questionnaire.setQuestionnaireStatus("ACTIVE");
        questionnaire.setFrozenQuestionJson(config());
        questionnaire.setFrozenThreshold(new BigDecimal("4.00"));
        questionnaire.setRuleVersion("RULE-1");
        SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
        task.setId(10L); task.setTenantId(7L); task.setTaskStatus("PENDING_COLLECTION"); task.setVersion(0);
        when(grantMapper.selectByDigestForUpdate(any())).thenReturn(grant);
        when(questionnaireMapper.selectById(11L)).thenReturn(questionnaire);
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire);
    }

    private SatisfactionAccessGrantDO activeGrant(long grantId) {
        SatisfactionAccessGrantDO grant = new SatisfactionAccessGrantDO();
        grant.setId(grantId); grant.setTenantId(7L); grant.setQuestionnaireId(11L); grant.setGrantStatus("ACTIVE");
        grant.setEffectiveFrom(LocalDateTime.now().minusMinutes(1)); grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        grant.setVersion(0); grant.setGrantVersion(1); grant.setCreator("9");
        return grant;
    }

    private SatisfactionResponseDO existingResponse(long grantId) {
        SatisfactionResponseDO existing = new SatisfactionResponseDO();
        existing.setId(50L); existing.setQuestionnaireId(11L);
        existing.setAnswerSnapshot("{\"answers\":[{\"questionCode\":\"Q1\",\"value\":\"YES\"}]}");
        existing.setSubmitChannel("PUBLIC_LINK"); existing.setCustomerContactRef("customer-1");
        existing.setCreator("BUSINESS_GRANT:" + grantId);
        return existing;
    }

    private SatisfactionResponseFileDO existingFile(long artifactId) {
        SatisfactionResponseFileDO file = new SatisfactionResponseFileDO();
        file.setId(70L); file.setTenantId(7L); file.setResponseId(50L); file.setFileRole("SIGNATURE");
        file.setFileSequence(1); file.setArtifactId(artifactId); file.setVersionNo(1);
        file.setReferenceKey("slot-1"); file.setArtifactVersion(1); file.setReferenceVersion(1);
        file.setAvailabilityVersion(1); file.setScopeVersion(3L); file.setFileHash("a".repeat(64));
        return file;
    }

    private SatisfactionResponseSubmissionService.Command command() {
        return new SatisfactionResponseSubmissionService.Command(7L, "token", "req-1", 50L, "PUBLIC_LINK",
                "customer-1", null, "{\"answers\":[{\"questionCode\":\"Q1\",\"value\":\"YES\"}]}", List.of(new SatisfactionResponseSubmissionService.FileFact(
                "SIGNATURE", "slot-1", 1, 100L, 1, "slot-1", 1, 1, 1, 3L,
                "a".repeat(64))), "grant:1");
    }

    private String config() {
        return "{\"schemaVersion\":1,\"questions\":[{\"code\":\"Q1\",\"title\":\"满意\","
                + "\"type\":\"SINGLE_CHOICE\",\"required\":true,\"options\":["
                + "{\"code\":\"YES\",\"label\":\"是\",\"score\":\"5.00\"},"
                + "{\"code\":\"NO\",\"label\":\"否\",\"score\":\"0.00\"}]}],"
                + "\"scoring\":{\"ruleVersion\":\"RULE-1\",\"strategy\":\"SUM_V1\","
                + "\"scoreMin\":\"0.00\",\"scoreMax\":\"5.00\",\"precision\":1,"
                + "\"roundingMode\":\"HALF_UP\",\"threshold\":\"4.00\"}}";
    }
}
