package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApiImpl;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskExecutionLookupQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Read-only acceptance against an explicitly selected, browser-created fixture; never creates or deletes data. */
@EnabledIfSystemProperty(named = "pms.execution.fixture.projectId", matches = "[1-9][0-9]*")
@SpringBootTest(classes = ProjectWorkBindingFactMapperTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskExecutionMapperAcceptanceTest {
    @Resource ProjectTaskExecutionMapper executions;
    @Resource ProjectWorkBindingFactMapper bindings;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (!"npdms_test".equals(System.getenv("NPDMS_DB_NAME"))
                || !"23316".equals(System.getenv("NPDMS_MYSQL_PORT")))
            throw new IllegalStateException("Acceptance requires the fixed isolated test database");
        ProjectWorkBindingFactMapperTest.mysqlProperties(registry);
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void readsTheRealBindingAndActiveRoundWithoutLegacyAssetIds() {
        Long tenant = Long.getLong("pms.execution.fixture.tenantId");
        Long project = Long.getLong("pms.execution.fixture.projectId");
        Long task = Long.getLong("pms.execution.fixture.taskId");
        Long contract = Long.getLong("pms.execution.fixture.contractId");
        assertNotNull(tenant); assertNotNull(task); assertNotNull(contract);
        TenantContextHolder.setTenantId(tenant);
        var projects = mock(ProjectMasterMapper.class); // inspect methods use only their joined read models.
        var binding = new ProjectWorkBindingFactApiImpl(projects, bindings,
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class)).inspect(
                new ProjectWorkBindingFactQuery(project, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
        assertEquals(task, binding.projectTaskId());
        assertEquals(contract, binding.executionContractId());
        assertNotNull(binding.projectTemplateId());
        assertNotNull(binding.dynamicFormTemplateRevisionId());
        var context = new ProjectNodeExecutionApiImpl(projects, executions,
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.class)).inspect(
                new ProjectTaskExecutionQuery(project, task, contract));
        assertTrue(context.writable());
        assertNotNull(context.planVersionId()); assertNotNull(context.executionId());
        assertNotNull(context.startedAt());
        assertNull(executions.selectCurrent(new ProjectTaskExecutionLookupQuery(tenant, project, task, contract+1)));
        TenantContextHolder.setTenantId(tenant+1);
        assertNull(executions.selectCurrent(new ProjectTaskExecutionLookupQuery(tenant+1, project, task, contract)));
    }
}
