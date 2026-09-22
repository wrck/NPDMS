package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectManualSatisfactionFreeze;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProjectManualSatisfactionApiImplTest {
    @Mock ProjectMasterMapper projects;
    @Mock ProjectWorkBindingFactMapper facts;
    @Mock ProjectWorkBindingFactApi bindings;
    @Mock ProjectScopeApi scopes;
    @Mock SatisfactionQuestionnaireTemplateApi templates;
    ProjectManualSatisfactionApiImpl api;
    final ProjectManualSatisfactionApi.Selection selection = new ProjectManualSatisfactionApi.Selection(10L,20L,30L,31L,1L);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        api = new ProjectManualSatisfactionApiImpl(projects,facts,bindings,scopes,templates);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    ProjectTaskInstanceDO prepare() {
        var scope = new ProjectScopeResult(10L,1L,Set.of(10L),Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope);
        var project = new ProjectMasterDO(); project.setId(10L);project.setTenantId(1L);project.setLifecycleStatus("ACTIVE");
        when(projects.selectByIdForUpdate(10L)).thenReturn(project);
        var task = new ProjectTaskInstanceDO();task.setId(20L);task.setVersion(2);
        when(facts.selectProjectTaskForUpdate(any())).thenReturn(task);
        return task;
    }
    @Test void freezesSelectedPublishedRevisionWithoutChangingTaskLifecycle() {
        var task = prepare();
        var startedAt = LocalDateTime.now();
        task.setActualStartTime(startedAt);
        when(templates.inspectPublished(30L,31L)).thenReturn(new SatisfactionTemplateFact("FOUND",30L,31L,1,"R1",new BigDecimal("80")));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(facts.freezeManualSatisfaction(any())).thenReturn(1);
        api.freeze(selection);
        var update=ArgumentCaptor.forClass(ProjectManualSatisfactionFreeze.class);
        verify(facts).freezeManualSatisfaction(update.capture());
        assertEquals(31L,update.getValue().revisionId());assertEquals(2,update.getValue().expectedVersion());
        assertEquals(startedAt,task.getActualStartTime());
        verify(bindings).lockCurrentSatisfactionTaskByProject(any());
    }
    @Test void rejectsCompletedTaskWithoutOverwritingItsHistory() {
        prepare().setActualEndTime(LocalDateTime.now());
        assertThrows(RuntimeException.class,()->api.freeze(selection));
        verifyNoInteractions(templates);verify(facts,never()).freezeManualSatisfaction(any());
    }
    @Test void rejectsOutsideScopeBeforeReadingOrWritingProject() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,()->api.freeze(selection));
        verifyNoInteractions(projects,facts,templates);
    }
    @Test void rejectsUnpublishedSelectionWithoutWrites() {
        prepare();when(templates.inspectPublished(30L,31L)).thenThrow(new IllegalStateException("unpublished"));
        assertThrows(IllegalStateException.class,()->api.freeze(selection));
        verify(facts,never()).freezeManualSatisfaction(any());
    }
}
