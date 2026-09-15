package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectChildCreationServiceTest {
    private final ProjectManualCreationService projects = mock(ProjectManualCreationService.class);
    private final DeptApi departments = mock(DeptApi.class);
    private final ProjectTemplateSelectionService templates = mock(ProjectTemplateSelectionService.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final ProjectChildCreationService service = new ProjectChildCreationService(projects, new ProjectChildDraftFactory(departments), templates, audit);

    @Test void createsUsingChildSelectionAndAuditsOverrideWithoutChangingParent() {
        var parent = parent(); var item = item(); var revision = new ProjectTemplateRevisionDO(); revision.setId(201L);
        when(templates.select(argThat(child -> "现场工勘".equals(child.getProjectName()) && Long.valueOf(100L).equals(child.getParentId())), eq(201L), eq("独立交付"), eq(9L))).thenReturn(new ProjectTemplateSelectionService.Selection(revision, true));
        when(projects.createProject(any(), isNull(), isNull(), eq(201L), isNull(), isNull()))
                .thenAnswer(call -> { ProjectMasterDO child = call.getArgument(0); child.setId(200L); return child; });
        var child = service.create(parent, item, 1L, 20L, 9L, "corr");
        assertEquals(100L, child.getParentId()); assertEquals(200L, child.getId());
        assertEquals(101L, parent.getLifecycleTemplateRevisionId());
        verify(audit).record(eq(1L), eq(9L), eq("corr"), eq("PROJECT_CHILD_TEMPLATE_SELECT"), eq(200L), eq("SUCCESS"),
                argThat(facts -> facts.get("templateRevisionId").equals(201L) && facts.get("override").equals(true)
                        && facts.get("reason").equals("独立交付")));
    }

    @Test void rejectedSelectionAndForeignParentCannotCreateOrAudit() {
        var parent = parent(); var item = item();
        assertThrows(IllegalArgumentException.class, () -> service.create(parent, item, 2L, 20L, 9L, "corr"));
        when(templates.select(argThat(child -> "现场工勘".equals(child.getProjectName()) && Long.valueOf(100L).equals(child.getParentId())), eq(201L), eq("独立交付"), eq(9L))).thenThrow(new IllegalArgumentException("unavailable"));
        assertThrows(IllegalArgumentException.class, () -> service.create(parent, item, 1L, 20L, 9L, "corr"));
        verifyNoInteractions(projects, audit);
    }

    @Test void auditFailureRollsBackChildCreation() {
        // Unique H2 ledger only; does not load application configuration or access project databases.
        var database = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(database);
            jdbc.execute("CREATE TABLE child_ledger(id INT PRIMARY KEY)");
            var parent = parent(); var item = item(); var revision = new ProjectTemplateRevisionDO(); revision.setId(201L);
            when(templates.select(argThat(child -> "现场工勘".equals(child.getProjectName()) && Long.valueOf(100L).equals(child.getParentId())), eq(201L), eq("独立交付"), eq(9L))).thenReturn(new ProjectTemplateSelectionService.Selection(revision, true));
            when(projects.createProject(any(), isNull(), isNull(), eq(201L), isNull(), isNull())).thenAnswer(call -> {
                jdbc.update("INSERT INTO child_ledger VALUES(200)"); var child = new ProjectMasterDO(); child.setId(200L); return child;
            });
            doThrow(new IllegalStateException("audit unavailable")).when(audit).record(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyMap());
            var proxy = new org.springframework.aop.framework.ProxyFactory(service);
            proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(database),
                    new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var transactional = (ProjectChildCreationService) proxy.getProxy();
            assertThrows(IllegalStateException.class, () -> transactional.create(parent, item, 1L, 20L, 9L, "corr"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM child_ledger", Integer.class));
        } finally { database.shutdown(); }
    }

    private ProjectMasterDO parent() {
        var parent = new ProjectMasterDO(); parent.setId(100L); parent.setTenantId(1L);
        parent.setLifecycleTemplateRevisionId(101L); return parent;
    }
    private ProjectSplitItemDO item() {
        var item = new ProjectSplitItemDO(); item.setTemplateRevisionId(201L); item.setTemplateSelectionReason("独立交付");
        item.setProjectName("现场工勘"); return item;
    }
}
