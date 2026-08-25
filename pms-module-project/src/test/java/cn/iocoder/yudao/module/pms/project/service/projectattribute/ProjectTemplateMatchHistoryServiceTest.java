package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute.ProjectTemplateMatchHistoryDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.ProjectTemplateMatchHistoryMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeOwnerSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ImpactMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.InitialMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.MatchSourceMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProjectTemplateMatchHistoryServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 25, 10, 0);

    @Mock
    private ProjectTemplateMatchHistoryMapper historyMapper;
    @InjectMocks
    private ProjectTemplateMatchHistoryService service;

    @Test
    void initialHistoryFreezesDecisionAndTrimsReason() {
        service.appendInitial(initialCommand());

        ProjectTemplateMatchHistoryDO row = insertedRow();
        assertEquals("INITIAL_CREATE", row.getTriggerType());
        assertEquals("CREATE_DECISION", row.getRecordPurpose());
        assertEquals("AUTO_UNIQUE", row.getDecisionMode());
        assertEquals("NOT_APPLICABLE", row.getImpactResult());
        assertEquals("创建依据", row.getChangeReason());
        assertEquals("MANUAL", row.getSourceSystem());
        assertNull(row.getBeforeAttributeSnapshot());
        assertNotNull(row.getRecordedAt());
        assertEquals(11L, row.getFrozenTemplateRevisionId());
    }

    @Test
    void impactHistoryPreservesBeforeAfterAndDetectsChangedCandidate() {
        ImpactMatchHistoryCommand command = impactCommand(
                TemplateMatchDecisionRules.TRIGGER_MANUAL,
                ProjectTemplateMatchHistoryService.INPUT_MANUAL,
                new TemplateMatchDecision("UNIQUE", "digest-2", "v1", null, 2L, 22L, 1),
                null);

        service.appendImpact(command);

        ProjectTemplateMatchHistoryDO row = insertedRow();
        assertEquals("IMPACT_EVALUATION", row.getRecordPurpose());
        assertEquals("CANDIDATE_CHANGED", row.getImpactResult());
        assertNull(row.getDecisionMode());
        assertNotNull(row.getBeforeAttributeSnapshot());
        assertEquals(22L, row.getMatchedTemplateRevisionId());
    }

    @Test
    void sourceImpactRequiresCompleteSourceEvidence() {
        ImpactMatchHistoryCommand command = impactCommand(
                TemplateMatchDecisionRules.TRIGGER_SOURCE,
                ProjectTemplateMatchHistoryService.INPUT_SOURCE,
                new TemplateMatchDecision("NO_MATCH", "digest-2", "v1", null, null, null, null),
                new MatchSourceMetadata("CRM", "CRM", "CRM-1", null,
                        "v2", OCCURRED_AT, "digest", "map-v1"));

        assertThrows(IllegalArgumentException.class, () -> service.appendImpact(command));
        verifyNoInteractions(historyMapper);
    }

    @Test
    void manualTriggerCannotClaimSourceOrigin() {
        ImpactMatchHistoryCommand command = impactCommand(
                TemplateMatchDecisionRules.TRIGGER_MANUAL,
                ProjectTemplateMatchHistoryService.INPUT_SOURCE,
                new TemplateMatchDecision("NO_MATCH", "digest-2", "v1", null, null, null, null),
                sourceMetadata());

        assertThrows(IllegalArgumentException.class, () -> service.appendImpact(command));
        verifyNoInteractions(historyMapper);
    }

    @Test
    void postCreationConflictStoresNoMatchedTemplate() {
        service.appendImpact(impactCommand(
                TemplateMatchDecisionRules.TRIGGER_SOURCE,
                ProjectTemplateMatchHistoryService.INPUT_SOURCE,
                new TemplateMatchDecision("MULTIPLE_MATCHES", "digest-2", "v1", null,
                        null, null, null), sourceMetadata()));

        ProjectTemplateMatchHistoryDO row = insertedRow();
        assertEquals("MULTIPLE_MATCHES", row.getImpactResult());
        assertNull(row.getMatchedTemplateId());
        assertNull(row.getMatchedTemplateRevisionId());
        assertEquals("CRM-EVT-2", row.getSourceEventId());
    }

    private InitialMatchHistoryCommand initialCommand() {
        return new InitialMatchHistoryCommand(1L, 100L, attributes(),
                ProjectAttributeOwnerSnapshot.manualProject(),
                new TemplateMatchDecision("UNIQUE", "digest", "v1", "AUTO_UNIQUE", 1L, 11L, 1),
                11L, ProjectTemplateMatchHistoryService.INPUT_MANUAL, null, 7L, " 创建依据 ",
                OCCURRED_AT, "idem-1", "request-digest", "op-1", " ", null);
    }

    private ImpactMatchHistoryCommand impactCommand(String triggerType, String inputOrigin,
                                                    TemplateMatchDecision decision,
                                                    MatchSourceMetadata source) {
        return new ImpactMatchHistoryCommand(1L, 100L, triggerType, attributes(),
                new ProjectAttributeSnapshot("CHANNEL_SIGN", "GENERAL", "DIRECT_SERVICE", null),
                ProjectAttributeOwnerSnapshot.manualProject(), decision, 11L, inputOrigin, source,
                7L, "调整依据", OCCURRED_AT, "idem-2", "request-digest-2", "op-2", null, null);
    }

    private MatchSourceMetadata sourceMetadata() {
        return new MatchSourceMetadata("CRM", "CRM", "CRM-1", "CRM-EVT-2",
                "v2", OCCURRED_AT, "source-digest", "map-v1");
    }

    private ProjectAttributeSnapshot attributes() {
        return new ProjectAttributeSnapshot("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null);
    }

    private ProjectTemplateMatchHistoryDO insertedRow() {
        ArgumentCaptor<ProjectTemplateMatchHistoryDO> captor =
                ArgumentCaptor.forClass(ProjectTemplateMatchHistoryDO.class);
        verify(historyMapper).insert(captor.capture());
        return captor.getValue();
    }
}
