package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectClosurePolicyFreezeTest {
    private static final String JSON = """
            {"closureType":"NORMAL","ruleRevision":1,"requireTerminalStage":true,
            "requireAllTasksDone":true,"revalidateBusinessFacts":true,
            "processDefinitionKey":"PMS_MINIMAL_NORMAL_CLOSURE","reviewerUserId":"9007199254740993"}
            """;
    private final ProjectManualCreationServiceImpl service = new ProjectManualCreationServiceImpl();
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    private final ProjectRuntimeGraphFreezer graph = mock(ProjectRuntimeGraphFreezer.class);
    private final ProjectDeliverableInitializationApplicationService deliverables = mock(ProjectDeliverableInitializationApplicationService.class);
    private final TemplateDefinitionContent content = new TemplateDefinitionContent();
    private final TemplateMatchDecision decision = new TemplateMatchDecision(TemplateMatchDecisionRules.MATCH_UNIQUE,
            "watermark", TemplateMatchDecisionRules.MATCHER_VERSION, TemplateMatchDecisionRules.DECISION_EXPLICIT, 9L, 1002L, 1);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "projectMasterMapper", projects);
        ReflectionTestUtils.setField(service, "projectTemplateService", templates);
        ReflectionTestUtils.setField(service, "runtimeGraphFreezer", graph);
        ReflectionTestUtils.setField(service, "stageInstanceMapper", mock(ProjectStageInstanceMapper.class));
        ReflectionTestUtils.setField(service, "taskInstanceMapper", mock(ProjectTaskInstanceMapper.class));
        ReflectionTestUtils.setField(service, "milestoneInstanceMapper", mock(ProjectMilestoneInstanceMapper.class));
        ReflectionTestUtils.setField(service, "gateInstanceMapper", mock(ProjectGateInstanceMapper.class));
        ReflectionTestUtils.setField(service, "taskTreePathMapper", mock(cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskTreePathMapper.class));
        ReflectionTestUtils.setField(service, "deliverableInitializationApplicationService", deliverables);
        var allocator = mock(ProjectCodeAllocator.class);
        when(allocator.allocateRootCode()).thenReturn("PJT2026000001");
        ReflectionTestUtils.setField(service, "projectCodeAllocator", allocator);
        var stateMachines = mock(TaskStateMachineMapper.class);
        var machine = new TaskStateMachineRevisionDO(); machine.setId(88L);
        when(stateMachines.selectCurrentPublished(any())).thenReturn(machine);
        ReflectionTestUtils.setField(service, "taskStateMachineMapper", stateMachines);
        var template = new ProjectTemplateDO(); template.setId(9L); template.setStatus("ACTIVE");
        var revision = new ProjectTemplateRevisionDO(); revision.setId(1002L); revision.setTemplateId(9L); revision.setRevisionNo(2); revision.setStatus("PUBLISHED");
        when(templates.getProjectTemplate(9L)).thenReturn(template);
        when(templates.getRevisionById(1002L)).thenReturn(revision);
        when(templates.getRevisionContent(9L, 2)).thenReturn(content);
        var stage = new TemplateDefinitionContent.StageDef(); stage.setStageCode("S0"); stage.setStart(true);
        content.setStages(List.of(stage));
        doAnswer(call -> { call.<ProjectMasterDO>getArgument(0).setId(100L); return 1; }).when(projects).insert(any(ProjectMasterDO.class));
    }

    @Test
    void freezesExactPublishedJsonBeforeRootInsertAndCannotFollowLaterTemplateChange() {
        content.setClosurePolicy(new TemplateDefinitionContent.ClosurePolicy(JsonUtils.parseTree(JSON)));
        doAnswer(call -> {
            var row = call.<ProjectMasterDO>getArgument(0);
            assertEquals(JsonUtils.parseTree(JSON), JsonUtils.parseTree(row.getClosurePolicySnapshot()));
            row.setId(100L); return 1;
        }).when(projects).insert(any(ProjectMasterDO.class));
        var created = create();
        content.setClosurePolicy(new TemplateDefinitionContent.ClosurePolicy(JsonUtils.parseTree(JSON.replace("9007199254740993", "27"))));
        assertEquals(JsonUtils.parseTree(JSON), JsonUtils.parseTree(created.getClosurePolicySnapshot()));
        assertEquals(100L, created.getRootId());
        assertEquals("S0", created.getCurrentStage());
        assertEquals("ACTIVE", created.getLifecycleStatus());
        verify(templates).getRevisionContent(9L, 2);
        verify(templates, never()).getDraftContent(anyLong());
    }

    @Test
    void absentPolicyOverridesUntrustedDraftWithNull() {
        assertNull(create().getClosurePolicySnapshot());
    }

    @Test
    void ordinaryProjectUpdateCannotReplaceFrozenPolicy() {
        var current = new ProjectMasterDO(); current.setId(100L); current.setRootId(100L); current.setTenantId(7L);
        current.setClosurePolicySnapshot(JSON);
        when(projects.selectById(100L)).thenReturn(current);
        var versions = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.class);
        var version = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO();
        version.setTreeVersion(1L);
        when(versions.selectLatestActive(100L)).thenReturn(version);
        ReflectionTestUtils.setField(service, "projectTreeVersionMapper", versions);
        var scopes = mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class);
        when(scopes.resolve(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.ProjectTreeScope(
                100L, 1L, java.util.Set.of(100L), java.util.Set.of(), java.util.Set.of()));
        ReflectionTestUtils.setField(service, "projectTreeScopeService", scopes);
        var update = new ProjectMasterDO(); update.setId(100L); update.setClosurePolicySnapshot("{}");
        service.updateProject(update, new ProjectManualCreationService.ProjectAccessActor(7L, 27L));
        assertEquals(JSON, update.getClosurePolicySnapshot());
        verify(projects).updateById(update);
        verify(templates, never()).getRevisionContent(anyLong(), anyInt());
    }

    @Test
    void initializationFailurePropagatesFromSameTransactionalCreation() throws Exception {
        content.setClosurePolicy(new TemplateDefinitionContent.ClosurePolicy(JsonUtils.parseTree(JSON)));
        doThrow(new IllegalStateException("initialization failed")).when(deliverables).initialize(any());
        assertThrows(IllegalStateException.class, this::create);
        var method = ProjectManualCreationServiceImpl.class.getMethod("createProject", ProjectMasterDO.class,
                String.class, String.class, TemplateMatchDecision.class, Long.class);
        assertTrue(List.of(method.getAnnotation(Transactional.class).rollbackFor()).contains(Exception.class));
        // Unit test proves propagation/transaction contract, not a real database rollback.
    }

    private ProjectMasterDO create() {
        var draft = new ProjectMasterDO(); draft.setTenantId(7L); draft.setProjectName("专用测试");
        draft.setCustomerCode("C"); draft.setCustomerName("客户"); draft.setCreationReason("测试");
        draft.setSigningMethod("DIRECT_SIGN"); draft.setProjectCategory("GENERAL"); draft.setImplementationMode("DIRECT_SERVICE");
        draft.setClosurePolicySnapshot("{\"untrusted\":true}");
        return service.createProject(draft, null, null, decision, null);
    }
}
