package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionResultDecisionServiceTest {
    @Mock SatisfactionCollectionTaskMapper taskMapper;
    @Mock SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock SatisfactionResponseMapper responseMapper;
    @Mock SatisfactionResultMapper resultMapper;
    @Mock SatisfactionResultFileMapper resultFileMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock FileArtifactApi fileArtifactApi;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    SatisfactionResultDecisionService service;

    @BeforeEach
    void setUp() {
        service = new SatisfactionResultDecisionService(taskMapper, questionnaireMapper, responseMapper,
                resultMapper, resultFileMapper, projectScopeApi, fileArtifactApi, commandExecutionApi);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object result = operation.get();
            facts.apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
        when(taskMapper.selectByIdForUpdate(7L, 10L)).thenReturn(task());
        when(questionnaireMapper.selectByIdForUpdate(7L, 11L)).thenReturn(questionnaire());
        when(responseMapper.selectByIdForUpdate(7L, 12L)).thenReturn(response());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
    }

    @Test
    void createsResultDocumentResultAndTaskTransition() {
        when(fileArtifactApi.createGeneratedBusinessFile(any())).thenReturn(file());
        when(resultMapper.insert(any(SatisfactionResultDO.class))).thenReturn(1);
        when(resultFileMapper.insert(any(SatisfactionResultFileDO.class))).thenReturn(1);
        when(taskMapper.completeDecision(any())).thenReturn(1);

        var result = service.decide(command());

        assertEquals(12L, result.resultId());
        assertEquals(new BigDecimal("5.0"), result.score());
        assertTrue(result.passed());
        verify(resultMapper).insert(any(SatisfactionResultDO.class));
        verify(resultFileMapper).insert(any(SatisfactionResultFileDO.class));
        verify(taskMapper).completeDecision(any());
    }

    @Test
    void generatedFileFailureLeavesResultWritesAtZero() {
        when(fileArtifactApi.createGeneratedBusinessFile(any()))
                .thenThrow(new IllegalStateException("FILE_STORAGE_UNAVAILABLE"));

        assertThrows(IllegalStateException.class, () -> service.decide(command()));

        verify(resultMapper, never()).insert(any(SatisfactionResultDO.class));
        verify(resultFileMapper, never()).insert(any(SatisfactionResultFileDO.class));
        verify(taskMapper, never()).completeDecision(any());
    }

    private SatisfactionResultDecisionService.Command command() {
        return new SatisfactionResultDecisionService.Command(7L, 10L, 11L, 12L, 99L, "decision-12");
    }

    private SatisfactionCollectionTaskDO task() {
        SatisfactionCollectionTaskDO row = new SatisfactionCollectionTaskDO();
        row.setId(10L); row.setTenantId(7L); row.setProjectId(20L); row.setProjectTaskId(21L);
        row.setQuestionnaireId(11L); row.setAssignedToUserId(99L); row.setTaskStatus("PENDING_DECISION");
        row.setCollectionKey("SAT-10"); row.setTaskRevisionNo(1); row.setVersion(1);
        row.setSourceOwnerContext("ACC"); row.setSourceObjectType("AcceptanceActivity");
        row.setSourceObjectId("100"); row.setSourceObjectVersion(1L);
        return row;
    }

    private SatisfactionQuestionnaireDO questionnaire() {
        SatisfactionQuestionnaireDO row = new SatisfactionQuestionnaireDO();
        row.setId(11L); row.setTenantId(7L); row.setCollectionTaskId(10L); row.setTemplateRevisionId(30L);
        row.setFrozenQuestionJson(config()); row.setFrozenThreshold(new BigDecimal("4.00"));
        row.setRuleVersion("RULE-1"); row.setAccessScopeVersion(3L); row.setVersion(0);
        return row;
    }

    private SatisfactionResponseDO response() {
        SatisfactionResponseDO row = new SatisfactionResponseDO();
        row.setId(12L); row.setTenantId(7L); row.setQuestionnaireId(11L);
        row.setAnswerSnapshot("{\"answers\":[{\"questionCode\":\"Q1\",\"value\":\"YES\"}]}");
        return row;
    }

    private FileArtifactVersionFact file() {
        return new FileArtifactVersionFact(100L, 1, "satisfaction-result-12", "SATISFACTION_RESULT_DOCUMENT",
                "satisfaction-result-12.pdf", 100L, "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE",
                new FileFactVersion(1, 0, 0), 3L);
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
