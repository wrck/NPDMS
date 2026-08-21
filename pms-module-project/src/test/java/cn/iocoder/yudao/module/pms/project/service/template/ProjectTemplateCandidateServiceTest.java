package cn.iocoder.yudao.module.pms.project.service.template;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateTaskDefinitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateApplicability;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateTaskDefinitionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateCandidateResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCriteria;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTemplateCandidateServiceTest {

    private ProjectTemplateMapper templateMapper;
    private ProjectTemplateTaskDefinitionMapper taskMapper;
    private ProjectTemplateCandidateService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(ProjectTemplateMapper.class);
        taskMapper = mock(ProjectTemplateTaskDefinitionMapper.class);
        service = new ProjectTemplateCandidateServiceImpl(templateMapper, taskMapper);
    }

    @Test
    void findCandidates_matchesFourDimensionsIndependentlyAndExcludesOtherTenant() {
        ProjectTemplateDO matched = revision(101L, 1L, true, 10);
        ProjectTemplateDO wrongDimension = revision(102L, 1L, true, 10);
        wrongDimension.getApplicabilitySnapshot().setImplementationModeCodes(Set.of("PARTNER"));
        ProjectTemplateDO otherTenant = revision(103L, 2L, true, 10);
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(List.of(otherTenant, wrongDimension, matched));

        TemplateCandidateResult result = service.findCandidates(1L, 9L, criteria());

        assertEquals(List.of(101L), result.candidates().stream().map(candidate -> candidate.revisionId()).toList());
        assertEquals(64, result.candidateWatermark().length());
    }

    @Test
    void resolveForCreate_rejectsTwoEqualPriorityDefaults() {
        List<ProjectTemplateDO> candidates = List.of(revision(101L, 1L, true, 10), revision(102L, 1L, true, 10));
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(candidates);
        String watermark = service.findCandidates(1L, 9L, criteria()).candidateWatermark();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveForCreate(1L, 9L, null, criteria(), watermark));

        assertEquals(1_014_023_006, error.getCode());
    }

    @Test
    void resolveForCreate_usesUniqueHighestPriorityDefault() {
        ProjectTemplateDO selected = revision(101L, 1L, true, 20);
        List<ProjectTemplateDO> candidates = List.of(revision(102L, 1L, true, 10), selected);
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(candidates);
        when(taskMapper.selectByRevisionId(1L, 101L)).thenReturn(List.of(nativeTask(101L)));
        String watermark = service.findCandidates(1L, 9L, criteria()).candidateWatermark();

        assertEquals(101L, service.resolveForCreate(1L, 9L, null, criteria(), watermark).revisionId());
    }

    @Test
    void resolveForCreate_rejectsChangedCandidateWatermark() {
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(List.of(revision(101L, 1L, true, 10)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveForCreate(1L, 9L, 101L, criteria(), "stale"));

        assertEquals(1_014_023_007, error.getCode());
    }

    @Test
    void resolveForCreate_rejectsMissingDefault() {
        List<ProjectTemplateDO> candidates = List.of(revision(101L, 1L, false, 10));
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(candidates);
        String watermark = service.findCandidates(1L, 9L, criteria()).candidateWatermark();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveForCreate(1L, 9L, null, criteria(), watermark));

        assertEquals(1_014_023_005, error.getCode());
    }

    @Test
    void resolveForCreate_revalidatesExplicitRevisionAgainstCurrentCandidates() {
        List<ProjectTemplateDO> candidates = List.of(revision(101L, 1L, false, 10));
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(candidates);
        String watermark = service.findCandidates(1L, 9L, criteria()).candidateWatermark();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveForCreate(1L, 9L, 999L, criteria(), watermark));

        assertEquals(1_014_023_003, error.getCode());
    }

    @Test
    void getPreview_returnsOnlyValidatedStageTaskAndCreationSummaries() {
        ProjectTemplateDO revision = revision(101L, 1L, true, 10);
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(List.of(revision));
        when(taskMapper.selectByRevisionId(1L, 101L)).thenReturn(List.of(nativeTask(101L)));

        var preview = service.getPreview(1L, 9L, 101L, criteria());

        assertEquals("S0", preview.stages().getFirst().stageCode());
        assertEquals("TASK-1", preview.stages().getFirst().tasks().getFirst().taskDefinitionKey());
        assertEquals(1, preview.milestones().size());
        assertEquals(1, preview.deliverables().size());
        assertEquals(1, preview.gates().size());
    }

    @Test
    void getPreview_rejectsInvalidNativeBinding() {
        ProjectTemplateDO revision = revision(101L, 1L, true, 10);
        ProjectTemplateTaskDefinitionDO task = nativeTask(101L);
        task.setTargetContextCode("ACC");
        when(templateMapper.selectPublishedCandidates(eq(1L), eq("MANUAL_CREATE"), any(LocalDateTime.class)))
                .thenReturn(List.of(revision));
        when(taskMapper.selectByRevisionId(1L, 101L)).thenReturn(List.of(task));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getPreview(1L, 9L, 101L, criteria()));

        assertEquals(1_014_023_004, error.getCode());
    }

    private TemplateMatchCriteria criteria() {
        return new TemplateMatchCriteria("DIRECT", "ENGINEERING", "VENDOR_DIRECT", null,
                "MANUAL_CREATE", 11L, 12L, 13L);
    }

    private ProjectTemplateDO revision(long id, long tenantId, boolean defaultFlag, int priority) {
        ProjectTemplateDO revision = new ProjectTemplateDO();
        revision.setId(id);
        revision.setTenantId(tenantId);
        revision.setTemplateId(501L);
        revision.setCode("TPL-1");
        revision.setRevisionNo(1);
        revision.setName("标准项目模板");
        revision.setMatchPriority(priority);
        revision.setDefaultFlag(defaultFlag);
        revision.setContentSha256("a".repeat(64));
        revision.setWorkflowDefinitionKey("PROJECT_STANDARD");
        revision.setWorkflowDefinitionVersion(1);
        TemplateApplicability applicability = new TemplateApplicability();
        applicability.setSchemaVersion(1);
        applicability.setSigningMethodCodes(Set.of("DIRECT"));
        applicability.setProjectCategoryCodes(Set.of("ENGINEERING"));
        applicability.setImplementationModeCodes(Set.of("VENDOR_DIRECT"));
        applicability.setMajorProjectLevelCodes(Set.of("NOT_APPLICABLE"));
        revision.setApplicabilitySnapshot(applicability);
        revision.setSnapshotJson(snapshot());
        return revision;
    }

    private TemplateSnapshot snapshot() {
        TemplateSnapshot snapshot = new TemplateSnapshot();
        TemplateSnapshot.StageDef stage = new TemplateSnapshot.StageDef();
        stage.setStageCode("S0");
        stage.setStageName("待开始");
        stage.setSortOrder(0);
        TemplateSnapshot.MilestoneDef milestone = new TemplateSnapshot.MilestoneDef();
        milestone.setMilestoneKey("M1");
        milestone.setMilestoneName("项目启动");
        milestone.setStageCode("S0");
        TemplateSnapshot.DeliverableDef deliverable = new TemplateSnapshot.DeliverableDef();
        deliverable.setRequirementKey("D1");
        deliverable.setDeliverableName("启动材料");
        deliverable.setStageCode("S0");
        deliverable.setRequired(true);
        TemplateSnapshot.GateDef gate = new TemplateSnapshot.GateDef();
        gate.setGateKey("G1");
        gate.setGateName("启动门禁");
        gate.setStageCode("S0");
        snapshot.setStages(List.of(stage));
        snapshot.setMilestones(List.of(milestone));
        snapshot.setDeliverables(List.of(deliverable));
        snapshot.setGates(List.of(gate));
        return snapshot;
    }

    private ProjectTemplateTaskDefinitionDO nativeTask(long revisionId) {
        ProjectTemplateTaskDefinitionDO task = new ProjectTemplateTaskDefinitionDO();
        task.setId(201L);
        task.setTenantId(1L);
        task.setTemplateRevisionId(revisionId);
        task.setStageDefinitionKey("S0");
        task.setTaskDefinitionKey("TASK-1");
        task.setName("确认项目范围");
        task.setSortOrder(1);
        task.setWorkBindingTypeCode("TASK_NATIVE");
        task.setBindingConfig(JsonNodeFactory.instance.objectNode());
        task.setPermissionPolicyRef("PROJECT_MEMBER");
        task.setCompletionRuleTypeCode("TASK_STATUS");
        task.setCompletionRuleConfig(JsonNodeFactory.instance.objectNode());
        task.setGateRef("G1");
        task.setDefinitionVersion(1);
        return task;
    }
}
