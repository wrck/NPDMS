package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-03: explicit edges round-trip; missing historical graph is not manufactured or rejected on read. */
class ProjectTemplateGraphPersistenceTest {
    ProjectTemplateServiceImpl service = new ProjectTemplateServiceImpl();
    ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
    ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
    ProjectTemplateTransitionDefinitionMapper transitions = mock(ProjectTemplateTransitionDefinitionMapper.class);
    ProjectTemplateStageDefinitionMapper stages = mock(ProjectTemplateStageDefinitionMapper.class);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service,"projectTemplateMapper",templates);
        ReflectionTestUtils.setField(service,"revisionMapper",revisions);
        ReflectionTestUtils.setField(service,"transitionDefinitionMapper",transitions);
        ReflectionTestUtils.setField(service,"stageDefinitionMapper",stages);
        ReflectionTestUtils.setField(service,"taskDefinitionMapper",mock(ProjectTemplateTaskDefinitionMapper.class));
        ReflectionTestUtils.setField(service,"milestoneDefinitionMapper",mock(ProjectTemplateMilestoneDefinitionMapper.class));
        ReflectionTestUtils.setField(service,"deliverableDefinitionMapper",mock(ProjectTemplateDeliverableDefinitionMapper.class));
        ReflectionTestUtils.setField(service,"gateDefinitionMapper",mock(ProjectTemplateGateDefinitionMapper.class));
        ReflectionTestUtils.setField(service,"gateReferenceMapper",mock(ProjectTemplateGateReferenceMapper.class));
        var root = new ProjectTemplateDO(); root.setId(1L); root.setTenantId(7L); root.setStatus("DRAFT"); root.setVersion(0);
        when(templates.lockTemplate(any())).thenReturn(root); when(templates.incrementVersion(any())).thenReturn(1);
        var draft = new ProjectTemplateRevisionDO(); draft.setId(10L); draft.setTemplateId(1L); draft.setTenantId(7L);
        draft.setStatus("DRAFT"); draft.setRevisionNo(0);
        when(revisions.selectDraftByTemplateId(1L)).thenReturn(draft); when(revisions.selectById(10L)).thenReturn(draft);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void saveAndLoadKeepGraphFlagsReferencesAndDisplayOrderIndependent() {
        var content = new TemplateDefinitionContent();
        var stage = new TemplateDefinitionContent.StageDef(); stage.setStageCode("S0"); stage.setName("开始"); stage.setSortOrder(99);
        stage.setStart(true); stage.setTerminal(false); stage.setDefinitionRevisionId(100L); stage.setWorkBindingRevisionId(101L);
        stage.setPermissionPolicyRevisionId(102L); stage.setCompletionRuleRevisionId(103L); content.setStages(List.of(stage));
        var edge = new TemplateDefinitionContent.TransitionDef(); edge.setTransitionCode("S0_TO_S4");
        edge.setFromStageCode("S0"); edge.setToStageCode("S4"); edge.setDefaultBranch(true); edge.setPriority(10);
        edge.setRevisionNo(2L); content.setTransitions(List.of(edge));
        service.updateProjectTemplateDraftContent(1L,content);
        var edgeCaptor = ArgumentCaptor.forClass(ProjectTemplateTransitionDefinitionDO.class); verify(transitions).insert(edgeCaptor.capture());
        var stageCaptor = ArgumentCaptor.forClass(ProjectTemplateStageDefinitionDO.class); verify(stages).insert(stageCaptor.capture());
        assertEquals(10L,edgeCaptor.getValue().getTemplateRevisionId()); assertEquals("S4",edgeCaptor.getValue().getToStageCode());
        when(transitions.selectRows(any())).thenReturn(List.of(edgeCaptor.getValue()));
        when(stages.selectListByRevisionId(10L)).thenReturn(List.of(stageCaptor.getValue()));
        var loaded = service.getDraftContent(1L);
        assertEquals(true,loaded.getStages().getFirst().getStart()); assertEquals(101L,loaded.getStages().getFirst().getWorkBindingRevisionId());
        assertEquals(99,loaded.getStages().getFirst().getSortOrder()); assertEquals(2L,loaded.getTransitions().getFirst().getRevisionNo());
        assertEquals(true,loaded.getTransitions().getFirst().getDefaultBranch());
    }
    @Test void copyCreatesNewIdentityDraftWithSourcePriorityAndRejectsStaleVersion() {
        var commands = mock(cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands.class);
        ReflectionTestUtils.setField(service,"configurationCommands",commands);
        when(commands.execute(anyString(),anyString(),any(),eq(Long.class),any())).thenAnswer(call -> {
            java.util.function.Supplier<Long> operation = call.getArgument(4); return operation.get();
        });
        var source = new ProjectTemplateDO(); source.setId(1L); source.setTenantId(7L); source.setVersion(4);
        source.setMatchPriority(17); source.setStatus("ACTIVE"); source.setDescription("source description");
        var target = new ProjectTemplateDO(); target.setId(2L); target.setTenantId(7L); target.setVersion(0); target.setStatus("DRAFT");
        when(templates.lockTemplate(any())).thenAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateIdentityQuery)call.getArgument(0);
            return query.templateId() == 1L ? source : target;
        });
        var sourceRevision = new ProjectTemplateRevisionDO(); sourceRevision.setId(20L); sourceRevision.setTemplateId(1L);
        sourceRevision.setRevisionNo(2); sourceRevision.setStatus("PUBLISHED");
        when(revisions.selectByTemplateIdAndRevisionNo(1L,2)).thenReturn(sourceRevision);
        var draft = new ProjectTemplateRevisionDO(); draft.setId(30L); draft.setTemplateId(2L); draft.setStatus("DRAFT");
        when(revisions.selectDraftByTemplateId(2L)).thenReturn(draft); when(revisions.selectById(30L)).thenReturn(draft);
        when(templates.insert(any(ProjectTemplateDO.class))).thenAnswer(call -> {
            ProjectTemplateDO row = call.getArgument(0); assertEquals(17,row.getMatchPriority());
            assertEquals("DRAFT",row.getStatus()); assertEquals(false,row.getSystemReserved()); row.setId(2L); return 1;
        });
        var body = new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCopyReqVO();
        body.setCode("COPY"); body.setName("copy"); body.setSourceRevisionNo(2);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.copyProjectTemplate(1L,3,body,"stale"));
        assertEquals(2L,service.copyProjectTemplate(1L,4,body,"copy"));
        assertEquals("ACTIVE",source.getStatus()); assertEquals("PUBLISHED",sourceRevision.getStatus());
    }

    @Test void historicalPublishedReadDoesNotInventEdgesOrRewriteHistory() {
        var history = new ProjectTemplateRevisionDO(); history.setId(20L); history.setTemplateId(1L); history.setRevisionNo(1);
        history.setStatus("PUBLISHED"); history.setProcessDefinitionVersion("historical-only");
        when(revisions.selectByTemplateIdAndRevisionNo(1L,1)).thenReturn(history);
        var loaded = service.getRevisionContent(1L,1);
        assertTrue(loaded.getTransitions().isEmpty()); assertNull(loaded.getDefinitionSnapshot());
        assertEquals("historical-only",loaded.getProcessDefinitionVersion());
        verify(transitions,never()).insert(any(ProjectTemplateTransitionDefinitionDO.class)); verify(revisions,never()).updateById(any(ProjectTemplateRevisionDO.class));
    }
}
