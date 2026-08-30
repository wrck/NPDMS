package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionResultFactApi;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.event.SatisfactionResultVersionChangedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionResultSourceProjectionServiceTest {
    @Mock ProjectWorkBindingFactApi workBindingFactApi;
    @Mock SatisfactionResultFactApi resultFactApi;
    @Mock AccProjectDeliverableMapper deliverableMapper;
    @Mock ProjectDeliverableSourceVersionMapper sourceMapper;
    @Mock ProjectDeliverableSourceAttachmentMapper attachmentMapper;
    SatisfactionResultSourceProjectionService service;

    @BeforeEach
    void setUp() {
        service = new SatisfactionResultSourceProjectionService(workBindingFactApi, resultFactApi,
                deliverableMapper, sourceMapper, attachmentMapper);
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(new ProjectSatisfactionTaskFact(
                20L, 21L, "T-SAT-SURVEY", 7, "AFTER_INITIAL_ACCEPTANCE", 30L, 31L,
                1, "RULE-1", new BigDecimal("4.00"), 99L));
        when(deliverableMapper.selectByProjectAndCodeForUpdate(any())).thenReturn(root());
        when(sourceMapper.insert(any(ProjectDeliverableSourceVersionDO.class))).thenReturn(1);
        when(attachmentMapper.insert(any(ProjectDeliverableSourceAttachmentDO.class))).thenReturn(1);
    }

    @Test
    void recordedUsesFactVersionForOwnerAndBusinessVersionForCurrentSource() {
        when(resultFactApi.lockAndRevalidate(any())).thenReturn(resultFact("FOUND", "EFFECTIVE", true));
        when(deliverableMapper.updateById(any(AccProjectDeliverableDO.class))).thenReturn(1);

        service.project(event());

        ArgumentCaptor<ProjectDeliverableSourceVersionDO> source =
                ArgumentCaptor.forClass(ProjectDeliverableSourceVersionDO.class);
        verify(sourceMapper).insert(source.capture());
        assertEquals(1, source.getValue().getSourceVersion());
        assertEquals("CURRENT", source.getValue().getRelationStatus());
        verify(deliverableMapper).updateById(any(AccProjectDeliverableDO.class));
    }

    @Test
    void staleRecordedFactVersionOnlyCreatesNonCurrentHistory() {
        when(resultFactApi.lockAndRevalidate(any())).thenReturn(resultFact("VERSION_CONFLICT", null, false));

        service.project(event());

        ArgumentCaptor<ProjectDeliverableSourceVersionDO> source =
                ArgumentCaptor.forClass(ProjectDeliverableSourceVersionDO.class);
        verify(sourceMapper).insert(source.capture());
        assertEquals("SUPERSEDED", source.getValue().getRelationStatus());
        verify(deliverableMapper, never()).updateById(any(AccProjectDeliverableDO.class));
    }

    private AccProjectDeliverableDO root() {
        AccProjectDeliverableDO row = new AccProjectDeliverableDO();
        row.setId(40L); row.setProjectId(20L); row.setTaskCode("T-SAT-SURVEY");
        row.setDeliverableCode("D-SAT-REPORT"); row.setVersion(0); row.setTenantId(7L);
        return row;
    }

    private SatisfactionResultFact resultFact(String outcome, String status, boolean passed) {
        return new SatisfactionResultFact(outcome, "SAT-10", 10L, 1, 11L, 12L, 12L, 1,
                31L, "RULE-1", new BigDecimal("4.00"), "ACC", "AcceptanceActivity", "100", 1L,
                passed, status, "PENDING_COMPENSATION", 0);
    }

    private SatisfactionResultVersionChangedMessage event() {
        return new SatisfactionResultVersionChangedMessage("evt-1", "RECORDED", 7L, 20L, 21L, 7,
                "T-SAT-SURVEY", "SAT-10", 1, 10L, 11L, 12L, 12L, 1, 0, 31L,
                "RULE-1", new BigDecimal("4.00"), "ACC", "AcceptanceActivity", "100", 1L,
                true, "EFFECTIVE", 99L, null, null, null, List.of(
                new SatisfactionResultVersionChangedMessage.FileFact("RESULT_DOCUMENT", 1, 100L, 1,
                        "result-12", 1, 0, 0, 3L, "a".repeat(64))));
    }
}
