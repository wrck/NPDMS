package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionResultFactApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionResultFactQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitOutcome;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.event.SatisfactionResultVersionChangedMessage;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 满意度成果 → 统一交付件投影（P06R I2）：AUTO_PROJECTION 提交落平台台账，
 * requestKey=satisfaction-result:{成果ID}:{版本}；非本来源占据 CURRENT 时让位跳过，
 * 同版本并发冲突显式失败；撤销仅失效提交链、材料保持待归档。
 */
@ExtendWith(MockitoExtension.class)
class SatisfactionResultSourceProjectionServiceTest {

    @Mock SatisfactionResultFactApi resultFactApi;
    @Mock PlatformDeliveryRequirementApi platform;
    @Mock ProjectDeliverableRuleApi rules;
    SatisfactionResultSourceProjectionService service;

    static final TemplateFrozenView VIEW = new TemplateFrozenView(40L, 20L, "CUSTOM-REPORT", "满意度报告",
            "S5", "CUSTOM-RENAMED", null, null, false, 0, null, "OPEN", "{}", 0);

    @BeforeEach
    void setUp() {
        service = new SatisfactionResultSourceProjectionService(resultFactApi, platform, rules);
    }

    private SatisfactionResultFact fact(String outcome, String status, boolean passed, int factVersion) {
        return new SatisfactionResultFact(outcome, "SAT-10", 10L, 1, 11L, 12L, 12L, 1,
                31L, "RULE-1", new BigDecimal("4.00"), "ACC", "AcceptanceActivity", "100", 1L,
                passed, status, "PENDING_COMPENSATION", factVersion);
    }

    private SatisfactionResultVersionChangedMessage event(String changeType, String resultStatus, boolean passed) {
        return new SatisfactionResultVersionChangedMessage("evt-1", changeType, 7L, 20L, 21L, 7,
                40L, "SAT-10", 1, 10L, 11L, 12L, 12L, 1, 1, 31L,
                "RULE-1", new BigDecimal("4.00"), "ACC", "AcceptanceActivity", "100", 1L,
                passed, resultStatus, 99L, null, null, null, List.of(
                new SatisfactionResultVersionChangedMessage.FileFact("RESULT_DOCUMENT", 1, 1, 100L, 1,
                        "result-12", 1, 0, 0, 3L, "a".repeat(64))));
    }

    private TemplateFrozenSubmissionView current(String sourceType, String payloadJson) {
        return new TemplateFrozenSubmissionView(1002L, 40L, "other-key", sourceType, "CURRENT",
                payloadJson, null, List.of(7001L), null);
    }

    @Test
    void recordedProjectsAsAutoProjectionWithFrozenMaterialsAndDecision() {
        when(platform.lockById(40L)).thenReturn(Optional.of(VIEW));
        when(platform.findSubmissionByRequestKey(40L, "satisfaction-result:12:1")).thenReturn(Optional.empty());
        when(resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 12L, 1)))
                .thenReturn(fact("FOUND", "EFFECTIVE", true, 1));
        when(platform.findCurrentSubmission(40L)).thenReturn(Optional.empty());
        when(platform.registerProjectionFile(eq(40L), any(), isNull(),
                eq(PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION))).thenReturn(7001L);
        when(platform.submitTemplateFrozen(any())).thenReturn(
                new TemplateFrozenSubmitOutcome(1001L, false, "SATISFIED", null));
        when(rules.evaluate(20L, "CUSTOM-REPORT")).thenReturn(
                new ProjectDeliverableRuleApi.Decision(true, "DELIVERABLE_RULE_SATISFIED", "{}"));

        service.project(event("RECORDED", "EFFECTIVE", true));

        var captor = ArgumentCaptor.forClass(TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(captor.capture());
        assertEquals("satisfaction-result:12:1", captor.getValue().requestKey());
        assertEquals(PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, captor.getValue().sourceType());
        assertEquals(List.of(7001L), captor.getValue().materialIds());
        assertTrue(captor.getValue().requestPayloadJson().contains("\"projectionKind\":\"SATISFACTION_RESULT\""));
        assertTrue(captor.getValue().requestPayloadJson().contains("\"resultId\":12"));
        assertTrue(captor.getValue().requestPayloadJson().contains("\"resultVersion\":1"));
        verify(platform).updateSubmissionDecision(eq(1001L), contains("DELIVERABLE_RULE_SATISFIED"));
    }

    @Test
    void replayedRequestKeyIsANoOp() {
        when(platform.lockById(40L)).thenReturn(Optional.of(VIEW));
        when(platform.findSubmissionByRequestKey(40L, "satisfaction-result:12:1"))
                .thenReturn(Optional.of(new TemplateFrozenSubmissionView(1001L, 40L, "satisfaction-result:12:1",
                        PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "CURRENT", "{}", null,
                        List.of(7001L), null)));

        service.project(event("RECORDED", "EFFECTIVE", true));

        verifyNoInteractions(resultFactApi, rules);
        verify(platform, never()).submitTemplateFrozen(any());
        verify(platform, never()).registerProjectionFile(any(), any(), any(), any());
    }

    @Test
    void nonCurrentOrFailedEvidenceNeverProjects() {
        when(platform.lockById(40L)).thenReturn(Optional.of(VIEW));
        when(platform.findSubmissionByRequestKey(40L, "satisfaction-result:12:1")).thenReturn(Optional.empty());
        when(resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 12L, 1)))
                .thenReturn(fact("VERSION_CONFLICT", null, false, 1));

        service.project(event("RECORDED", "EFFECTIVE", true));

        when(resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 12L, 1)))
                .thenReturn(fact("FOUND", "EFFECTIVE", false, 1));
        service.project(event("RECORDED", "EFFECTIVE", false));

        verify(platform, never()).submitTemplateFrozen(any());
        verify(platform, never()).registerProjectionFile(any(), any(), any(), any());
    }

    @Test
    void otherCurrentEvidenceDefersInsteadOfConflicting() {
        when(platform.lockById(40L)).thenReturn(Optional.of(VIEW));
        when(platform.findSubmissionByRequestKey(40L, "satisfaction-result:12:1")).thenReturn(Optional.empty());
        when(resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 12L, 1)))
                .thenReturn(fact("FOUND", "EFFECTIVE", true, 1));

        when(platform.findCurrentSubmission(40L)).thenReturn(Optional.of(current("UPLOAD", "{}")));
        service.project(event("RECORDED", "EFFECTIVE", true));

        when(platform.findCurrentSubmission(40L)).thenReturn(Optional.of(current(
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION,
                "{\"projectionKind\":\"ACCEPTANCE_REPORT\",\"reportVersionId\":55}")));
        service.project(event("RECORDED", "EFFECTIVE", true));

        when(platform.findCurrentSubmission(40L)).thenReturn(Optional.of(current(
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION,
                "{\"projectionKind\":\"SATISFACTION_RESULT\",\"resultVersion\":2}")));
        service.project(event("RECORDED", "EFFECTIVE", true));

        verify(platform, never()).submitTemplateFrozen(any());

        when(platform.findCurrentSubmission(40L)).thenReturn(Optional.of(current(
                PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION,
                "{\"projectionKind\":\"SATISFACTION_RESULT\",\"resultVersion\":1}")));
        assertEquals("SATISFACTION_SOURCE_VERSION_CONFLICT", assertThrows(IllegalStateException.class,
                () -> service.project(event("RECORDED", "EFFECTIVE", true))).getMessage());
    }

    @Test
    void frozenIdentityDoesNotAllowAnotherProjectOrMissingRoot() {
        when(platform.lockById(40L)).thenReturn(Optional.of(new TemplateFrozenView(40L, 999L, "OTHER",
                "其他", "S1", null, null, null, false, 0, null, "OPEN", "{}", 0)));
        assertThrows(IllegalStateException.class, () -> service.project(event("RECORDED", "EFFECTIVE", true)));
        when(platform.lockById(40L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.project(event("RECORDED", "EFFECTIVE", true)));
        verifyNoInteractions(resultFactApi);
    }

    @Test
    void invalidationRevokesOnlyTheRecordedProjection() {
        when(platform.lockById(40L)).thenReturn(Optional.of(VIEW));
        when(resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 12L, 1)))
                .thenReturn(fact("FOUND", "INVALIDATED", false, 1));
        when(platform.findSubmissionByRequestKey(40L, "satisfaction-result:12:1"))
                .thenReturn(Optional.of(new TemplateFrozenSubmissionView(1001L, 40L, "satisfaction-result:12:1",
                        PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "WITHDRAWN", "{}", null,
                        List.of(7001L), null)));

        service.project(event("INVALIDATED", "INVALIDATED", false));

        verify(platform).revokeProjectionSubmission(40L, "satisfaction-result:12:1", null);

        when(platform.findSubmissionByRequestKey(40L, "satisfaction-result:12:1")).thenReturn(Optional.empty());
        assertEquals("SATISFACTION_SOURCE_INVALIDATION_PENDING_RECORDED", assertThrows(IllegalStateException.class,
                () -> service.project(event("INVALIDATED", "INVALIDATED", false))).getMessage());

        when(resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 12L, 1)))
                .thenReturn(fact("FOUND", "EFFECTIVE", true, 1));
        assertEquals("SATISFACTION_RESULT_INVALIDATION_FACT_CONFLICT", assertThrows(IllegalStateException.class,
                () -> service.project(event("INVALIDATED", "INVALIDATED", false))).getMessage());
    }
}
