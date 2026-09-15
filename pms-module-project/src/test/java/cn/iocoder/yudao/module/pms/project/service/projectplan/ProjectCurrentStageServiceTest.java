package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCurrentStageServiceTest {
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectPlanProjectionMapper projections = mock(ProjectPlanProjectionMapper.class);
    final ProjectCurrentStageService service = new ProjectCurrentStageService(projects, plans, graph, projections);
    final ProjectMasterDO project = new ProjectMasterDO();
    final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
    final List<ProjectStageInstanceDO> stages = new ArrayList<>();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        project.setId(9L); project.setTenantId(7L); project.setVersion(3);
        project.setLifecycleStatus("ACTIVE"); project.setCurrentStage("S0"); project.setActivePlanVersionId(21L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        when(projects.selectById(9L)).thenReturn(project);
        var plan = new ProjectPlanVersionDO(); plan.setId(21L);
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); return plan; });
        when(graph.selectStages(any())).thenReturn(stages);
        when(projections.updateCurrentStage(any())).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void earliestActiveBindingWinsRegardlessOfCanvasOrderAndAllowsLaterParallelBusiness() {
        add("DEPLOY", "S4", "ACTIVE"); add("PREP_WORK", "S1", "ACTIVE"); add("OTHER_PREP", "S1", "ACTIVE");
        assertEquals(4, service.synchronize(9L));
        verify(projections).updateCurrentStage(argThat(q -> q.currentStage().equals("S1") && q.expectedVersion() == 3 && q.planVersionId() == 21L));
        assertTrue(service.isActive(project, "S4"));
        assertFalse(service.isActive(project, "S2"));
    }
    @Test void endingEarliestStageMovesToTheNextActiveBindingAndReworkCanMoveBack() {
        add("PREP_WORK", "S1", "DONE"); add("DEPLOY", "S4", "ACTIVE");
        project.setCurrentStage("S1"); service.synchronize(9L);
        verify(projections).updateCurrentStage(argThat(q -> q.currentStage().equals("S4")));
        project.setCurrentStage("S4"); stages.getFirst().setStatus("ACTIVE"); service.synchronize(9L);
        verify(projections).updateCurrentStage(argThat(q -> q.currentStage().equals("S1")));
    }
    @Test void pendingDoneTerminatedOrNoStagesKeepLastValueWithoutVersionChurn() {
        add("A", "S1", "PENDING"); add("B", "S2", "DONE"); add("C", "S3", "TERMINATED");
        assertEquals(3, service.synchronize(9L));
        stages.clear(); assertEquals(3, service.synchronize(9L));
        verifyNoInteractions(projections);
    }
    @Test void duplicateStateEventsDoNotWriteAnUnchangedSummary() {
        add("A", "S1", "ACTIVE"); project.setCurrentStage("S1");
        service.synchronize(9L); service.synchronize(9L); verifyNoInteractions(projections);
    }
    @Test void missingFrozenBindingAndStalePlanDoNotInventClassification() {
        add("CUSTOM", null, "ACTIVE");
        assertThrows(IllegalStateException.class, () -> service.synchronize(9L));
        when(plans.selectEffective(any())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.synchronize(9L));
        verifyNoInteractions(projections);
    }
    @Test void foreignTenantAndVersionConflictCannotUpdateCurrentStage() {
        add("A", "S1", "ACTIVE");
        when(projections.updateCurrentStage(any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.synchronize(9L));
        clearInvocations(projections); TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, () -> service.synchronize(9L));
        verifyNoInteractions(projections);
    }
    @Test void closureRetainsHistoryAndReadFactChecksVersionWithoutWrites() {
        add("A", "S4", "ACTIVE");
        assertTrue(service.isActive(new ProjectCurrentStageService.Query(9L, 3, "S4")));
        assertThrows(IllegalStateException.class, () -> service.isActive(new ProjectCurrentStageService.Query(9L, 2, "S4")));
        project.setLifecycleStatus("NORMAL_CLOSED"); assertEquals(3, service.synchronize(9L));
        verifyNoInteractions(projections);
    }

    @Test void summaryJoinsTheStageTransactionAndRollsBackWithIt() {
        // Isolated H2 transaction ledger: validates transaction participation, not production mapper SQL.
        var database = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder()
                .generateUniqueName(true).setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(database);
            jdbc.execute("CREATE TABLE stage_summary (id INT PRIMARY KEY, stage_status VARCHAR(20), current_stage VARCHAR(2))");
            jdbc.update("INSERT INTO stage_summary VALUES (1,'PENDING','S0')");
            add("PREP_WORK", "S1", "ACTIVE");
            when(projections.updateCurrentStage(any())).thenAnswer(call -> jdbc.update(
                    "UPDATE stage_summary SET current_stage=? WHERE id=1", call.getArgument(0,
                            ProjectPlanProjectionMapper.CurrentStageUpdate.class).currentStage()));
            var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(database);
            var proxy = new org.springframework.aop.framework.ProxyFactory(service);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                    new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var transactional = (ProjectCurrentStageService) proxy.getProxy();
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                    () -> transactional.synchronize(9L));
            var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE stage_summary SET stage_status='ACTIVE' WHERE id=1");
                transactional.synchronize(9L);
                throw new IllegalStateException("later stage command failed");
            }));
            assertEquals("PENDING", jdbc.queryForObject("SELECT stage_status FROM stage_summary WHERE id=1", String.class));
            assertEquals("S0", jdbc.queryForObject("SELECT current_stage FROM stage_summary WHERE id=1", String.class));
            transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE stage_summary SET stage_status='ACTIVE' WHERE id=1");
                transactional.synchronize(9L);
            });
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT stage_status FROM stage_summary WHERE id=1", String.class));
            assertEquals("S1", jdbc.queryForObject("SELECT current_stage FROM stage_summary WHERE id=1", String.class));
        } finally { database.shutdown(); }
    }
    void add(String code, String binding, String status) {
        var node = new TemplateExecutionSnapshot.StageContract(); node.setCode(code); node.setLifecycleStage(binding);
        snapshot.getStages().add(node);
        stages.add(new ProjectStageInstanceDO().setCode(code).setStatus(status));
    }
}
