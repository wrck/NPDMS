package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateClosurePolicyPersistenceTest {
    private static final String JSON = """
            {"closureType":"NORMAL","ruleRevision":1,"requireTerminalStage":true,
            "requireAllTasksDone":true,"revalidateBusinessFacts":true,
            "processDefinitionKey":"PMS_MINIMAL_NORMAL_CLOSURE","reviewerUserId":"9007199254740993"}
            """;
    private final ProjectTemplateServiceImpl service = new ProjectTemplateServiceImpl();
    private final ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
    private final ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
    private final BpmNormalClosureApi bpm = mock(BpmNormalClosureApi.class);
    private final ExplicitPermissionApi permissions = mock(ExplicitPermissionApi.class);
    private ProjectTemplateRevisionDO draft;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service, "revisionMapper", revisions);
        ReflectionTestUtils.setField(service, "projectTemplateMapper", templates);
        ReflectionTestUtils.setField(service, "bpmNormalClosureApi", bpm);
        ReflectionTestUtils.setField(service, "explicitPermissionApi", permissions);
        ReflectionTestUtils.setField(service, "definitionReferenceAssembler", mock(TemplateDefinitionReferenceAssembler.class));
        ReflectionTestUtils.setField(service, "stageDefinitionMapper", mock(ProjectTemplateStageDefinitionMapper.class));
        ReflectionTestUtils.setField(service, "taskDefinitionMapper", mock(ProjectTemplateTaskDefinitionMapper.class));
        ReflectionTestUtils.setField(service, "milestoneDefinitionMapper", mock(ProjectTemplateMilestoneDefinitionMapper.class));
        ReflectionTestUtils.setField(service, "deliverableDefinitionMapper", mock(ProjectTemplateDeliverableDefinitionMapper.class));
        ReflectionTestUtils.setField(service, "gateDefinitionMapper", mock(ProjectTemplateGateDefinitionMapper.class));
        ReflectionTestUtils.setField(service, "gateReferenceMapper", mock(ProjectTemplateGateReferenceMapper.class));
        ReflectionTestUtils.setField(service, "transitionDefinitionMapper", mock(ProjectTemplateTransitionDefinitionMapper.class));
        var template = new ProjectTemplateDO(); template.setId(10L); template.setTenantId(7L); template.setStatus("DRAFT");
        when(templates.lockTemplate(any())).thenReturn(template);
        when(templates.incrementVersion(any())).thenReturn(1);
        draft = new ProjectTemplateRevisionDO(); draft.setId(11L); draft.setTemplateId(10L); draft.setStatus("DRAFT"); draft.setRevisionNo(0);
        when(revisions.selectDraftByTemplateId(10L)).thenReturn(draft);
        when(revisions.selectById(11L)).thenReturn(draft);
    }

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void savesAndReadsPolicyInDedicatedColumnWithoutTouchingDefinitionSnapshot() {
        var content = content();
        service.updateProjectTemplateDraftContent(10L, content);
        var captor = ArgumentCaptor.forClass(ProjectTemplateRevisionDO.class);
        verify(revisions).updateById(captor.capture());
        assertEquals(JsonUtils.parseTree(JSON), JsonUtils.parseTree(captor.getValue().getClosurePolicy()));
        assertNull(captor.getValue().getDefinitionSnapshot());
        draft.setClosurePolicy(captor.getValue().getClosurePolicy());
        assertEquals(JsonUtils.parseTree(JSON), service.getDraftContent(10L).getClosurePolicy().toJson());
        verifyNoInteractions(bpm, permissions);
    }

    @Test
    void disablingClearsPolicyRatherThanTreatingMissingConditionsAsPassed() throws Exception {
        draft.setClosurePolicy(JSON);
        service.updateProjectTemplateDraftContent(10L, new TemplateDefinitionContent());
        var captor = ArgumentCaptor.forClass(ProjectTemplateRevisionDO.class);
        verify(revisions).updateById(captor.capture());
        assertNull(captor.getValue().getClosurePolicy());
        assertEquals(com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS,
                ProjectTemplateRevisionDO.class.getDeclaredField("closurePolicy")
                        .getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class).updateStrategy());
        draft.setClosurePolicy(null);
        assertNull(service.getDraftContent(10L).getClosurePolicy());
    }

    @Test
    void publishedRevisionCopiesExactPolicyColumnAndLeavesDraftUntouched() {
        draft.setClosurePolicy(JSON);
        var stages = mock(ProjectTemplateStageDefinitionMapper.class);
        var stage = new ProjectTemplateStageDefinitionDO();
        stage.setStageCode("S0"); stage.setName("立项"); stage.setSortOrder(0);
        stage.setStart(true); stage.setTerminal(true); stage.setDefinitionRevisionId(1L);
        stage.setWorkBindingRevisionId(2L); stage.setPermissionPolicyRevisionId(3L); stage.setCompletionRuleRevisionId(4L);
        when(stages.selectListByRevisionId(11L)).thenReturn(List.of(stage));
        ReflectionTestUtils.setField(service, "stageDefinitionMapper", stages);
        availableBpm();
        when(permissions.lockAndCheck(anyLong(), anyLong(), anyString())).thenReturn(true);
        doAnswer(call -> { call.<ProjectTemplateRevisionDO>getArgument(0).setId(12L); return 1; })
                .when(revisions).insert(any(ProjectTemplateRevisionDO.class));
        service.publishProjectTemplate(10L);
        var captor = ArgumentCaptor.forClass(ProjectTemplateRevisionDO.class);
        verify(revisions).insert(captor.capture());
        assertEquals("PUBLISHED", captor.getValue().getStatus());
        assertEquals(JSON, captor.getValue().getClosurePolicy());
        assertEquals(JSON, draft.getClosurePolicy());
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
    }

    @Test
    void noPolicySkipsClosureOwnersForLegacyTemplates() {
        assertTrue(service.validateClosurePolicyOwners(new TemplateDefinitionContent(), 7L, true).isEmpty());
        verifyNoInteractions(bpm, permissions);
    }

    @Test
    void publicationLocksExplicitQualificationAndInspectDoesNotLock() {
        availableBpm();
        when(permissions.lockAndCheck(7L, 9007199254740993L, "pms:acc-project-closure:audit")).thenReturn(true);
        assertTrue(service.validateClosurePolicyOwners(content(), 7L, true).isEmpty());
        verify(permissions).lockAndCheck(7L, 9007199254740993L, "pms:acc-project-closure:audit");
        when(permissions.hasExplicitPermission(7L, 9007199254740993L, "pms:acc-project-closure:audit")).thenReturn(true);
        assertTrue(service.validateClosurePolicyOwners(content(), 7L, false).isEmpty());
        verify(permissions).hasExplicitPermission(7L, 9007199254740993L, "pms:acc-project-closure:audit");
        verify(bpm, never()).start(any());
    }

    @Test
    void absentExplicitGrantFailsEvenWithAvailableDefinition() {
        availableBpm();
        assertTrue(service.validateClosurePolicyOwners(content(), 7L, true).stream().anyMatch(message -> message.contains("显式材料审核资格")));
    }

    @Test
    void missingBpmAndQualificationOwnerFailuresFailClosed() {
        when(bpm.inspectDefinition(anyLong(), anyString())).thenThrow(new IllegalStateException("unavailable"));
        when(permissions.lockAndCheck(anyLong(), anyLong(), anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(2, service.validateClosurePolicyOwners(content(), 7L, true).size());
        verify(revisions, never()).insert(any(ProjectTemplateRevisionDO.class));
    }

    @Test
    void rejectsWrongDefinitionAndSingleNode() {
        when(bpm.inspectDefinition(anyLong(), anyString())).thenReturn(new BpmNormalClosureApi.Definition(
                "actual", "OTHER", List.of(new BpmNormalClosureApi.Node("review", "审核", "MATERIAL"))));
        when(permissions.lockAndCheck(anyLong(), anyLong(), anyString())).thenReturn(true);
        assertEquals(1, service.validateClosurePolicyOwners(content(), 7L, true).size());
    }

    @Test
    void rejectsReversedManualNodesEvenWithCorrectDefinitionKey() {
        when(bpm.inspectDefinition(anyLong(), anyString())).thenReturn(new BpmNormalClosureApi.Definition(
                "actual", "PMS_MINIMAL_NORMAL_CLOSURE", List.of(
                new BpmNormalClosureApi.Node("materialReview", "材料审核", "MATERIAL_REVIEWER"),
                new BpmNormalClosureApi.Node("serviceManagerReview", "服务经理", "SERVICE_MANAGER"))));
        when(permissions.lockAndCheck(anyLong(), anyLong(), anyString())).thenReturn(true);
        assertEquals(1, service.validateClosurePolicyOwners(content(), 7L, true).size());
    }

    private void availableBpm() {
        when(bpm.inspectDefinition(7L, "PMS_MINIMAL_NORMAL_CLOSURE")).thenReturn(new BpmNormalClosureApi.Definition(
                "PMS_MINIMAL_NORMAL_CLOSURE:1:actual", "PMS_MINIMAL_NORMAL_CLOSURE", List.of(
                new BpmNormalClosureApi.Node("serviceManagerReview", "服务经理", "SERVICE_MANAGER"),
                new BpmNormalClosureApi.Node("materialReview", "材料审核", "MATERIAL_REVIEWER"))));
    }
    private TemplateDefinitionContent content() {
        var content = new TemplateDefinitionContent();
        content.setClosurePolicy(new TemplateDefinitionContent.ClosurePolicy(JsonUtils.parseTree(JSON)));
        return content;
    }
}
