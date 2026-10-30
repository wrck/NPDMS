package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectTemplateInitializationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MigratedProjectTemplateSyncTaskTest {
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final ProjectPlanDraftService access = mock(ProjectPlanDraftService.class);
    final ProjectTemplateInitializationMapper runtime = mock(ProjectTemplateInitializationMapper.class);
    final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    final ProjectRuleFields fields = mock(ProjectRuleFields.class);
    final ProjectTemplateInitializationService initialization = mock(ProjectTemplateInitializationService.class);
    final SyncSource externalSource = mock(SyncSource.class);
    final MigratedProjectTemplateSyncTask task = new MigratedProjectTemplateSyncTask(
            scopes, access, runtime, templates, fields, initialization);
    JdbcTemplate jdbc;
    SqlSessionTemplate sql;
    TransactionTemplate transaction;

    @BeforeEach void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:template_sync_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE proj_project (id BIGINT PRIMARY KEY,tenant_id BIGINT,source_type VARCHAR(30),"
                + "deleted BOOLEAN DEFAULT FALSE,lifecycle_status VARCHAR(30),version BIGINT DEFAULT 0,"
                + "lifecycle_template_id BIGINT,lifecycle_template_revision_id BIGINT,"
                + "lifecycle_template_revision_no INT,active_plan_version_id BIGINT)");
        var config = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/projectplan/MigratedProjectTemplateSyncTaskMapper.xml";
        try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
        }
        sql = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(9L));
        when(access.authorize(anyLong(), eq(1L))).thenAnswer(inv -> new ProjectPlanScopeQuery(7L, inv.getArgument(0)));
        when(fields.creationFacts(any())).thenReturn(new TemplateMatchFacts(Map.of()));
        var candidate = new TemplateMatchCandidate();
        candidate.setTemplateId(8L); candidate.setTemplateRevisionId(80L);
        when(templates.matchPreview(any())).thenReturn(TemplateMatchResult.matched(candidate));
        when(initialization.initialize(any(), eq(1L), anyString())).thenAnswer(inv -> {
            ProjectTemplateInitializationService.Initialize command = inv.getArgument(0);
            jdbc.update("UPDATE proj_project SET lifecycle_template_id=8,lifecycle_template_revision_id=?,"
                    + "lifecycle_template_revision_no=2,active_plan_version_id=100,version=version+1 WHERE id=?",
                    command.expectedTemplateRevisionId(), command.projectId());
            return new ProjectTemplateInitializationService.Initialized(command.projectId(), 100L, 8L, 80L);
        });
    }

    private void project(long id, long tenant, String source, String state) {
        jdbc.update("INSERT INTO proj_project(id,tenant_id,source_type,lifecycle_status) VALUES(?,?,?,?)",
                id, tenant, source, state);
    }

    private SyncContext<Void> context(boolean preview, int batchSize, int maxRows) {
        return new SyncContext<>(7, 1, 3, preview, batchSize, maxRows, LocalDateTime.now(), externalSource,
                mock(SyncIdentityStore.class), mock(SyncReferences.class), task.taskCode(), sql, false, true);
    }

    @Test void previewUsesExistingMatcherWithoutWritingAndOnlyReadsAuthorizedMigratedTenantRows() {
        project(9, 7, "MIGRATION", "ACTIVE"); project(10, 8, "MIGRATION", "ACTIVE");
        project(11, 7, "MANUAL", "ACTIVE"); project(12, 7, "MIGRATION", "ACTIVE");
        project(13, 7, "MIGRATION", "ACTIVE");
        jdbc.update("UPDATE proj_project SET deleted=TRUE WHERE id=13");
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(9L, 10L, 11L, 13L));
        var result = task.execute(context(true, 2, 100));
        assertEquals(new SyncCounts(1, 0, 1, 0, 0), result);
        verifyNoInteractions(initialization, externalSource);
        verify(fields).creationFacts(argThat(project -> project.getId() == 9 && "MIGRATION".equals(project.getSourceType())));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM proj_project WHERE lifecycle_template_id IS NOT NULL", Long.class));
        verify(scopes).resolveAllCurrent(argThat(query -> query.tenantId() == 7 && query.subjectUserId() == 1
                && ProjectScopeApi.ACTION_EDIT.equals(query.actionCode())));
    }

    @Test void executionDelegatesExpectedVersionsAndReplayPreservesFrozenBinding() {
        project(9, 7, "MIGRATION", "ACTIVE");
        var first = transaction.execute(status -> task.execute(context(false, 2, 100)));
        assertEquals(new SyncCounts(1, 0, 1, 0, 0), first);
        verify(initialization).initialize(new ProjectTemplateInitializationService.Initialize(9L, 0L, 80L),
                1L, "BASE_P38:9:0:80");
        var replay = context(false, 2, 100);
        assertEquals(new SyncCounts(1, 0, 0, 1, 0), transaction.execute(status -> task.execute(replay)));
        assertTrue(replay.runState().warnings().contains("PROJECT_TEMPLATE_ALREADY_INITIALIZED:1"));
        verify(initialization, times(1)).initialize(any(), anyLong(), anyString());
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM proj_project WHERE id=9", Long.class));
    }

    @Test void noMatchAndEqualPriorityConflictRemainUnchanged() {
        project(9, 7, "MIGRATION", "ACTIVE"); project(10, 7, "MIGRATION", "ACTIVE");
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(9L, 10L));
        when(templates.matchPreview(any())).thenReturn(TemplateMatchResult.noMatch("missing"),
                TemplateMatchResult.multiMatch(List.of("tie")));
        var context = context(false, 2, 100);
        assertEquals(new SyncCounts(2, 0, 0, 2, 0), task.execute(context));
        assertTrue(context.runState().warnings().containsAll(List.of("PROJECT_TEMPLATE_NO_MATCH:1", "PROJECT_TEMPLATE_MULTI_MATCH:1")));
        verifyNoInteractions(initialization);
    }

    @Test void closedPartialBindingAndExistingRuntimeAreNotReinitialized() {
        for (long id = 9; id <= 13; id++) project(id, 7, "MIGRATION", "ACTIVE");
        jdbc.update("UPDATE proj_project SET lifecycle_status='NORMAL_CLOSED' WHERE id=9");
        jdbc.update("UPDATE proj_project SET lifecycle_status='EXCEPTION_CLOSED' WHERE id=10");
        jdbc.update("UPDATE proj_project SET lifecycle_template_revision_id=80 WHERE id=11");
        jdbc.update("UPDATE proj_project SET active_plan_version_id=100 WHERE id=12");
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(9L, 10L, 11L, 12L, 13L));
        when(runtime.hasRuntime(new ProjectPlanScopeQuery(7L, 13L))).thenReturn(true);
        var context = context(false, 2, 100);
        assertEquals(new SyncCounts(5, 0, 0, 5, 0), task.execute(context));
        assertTrue(context.runState().warnings().containsAll(List.of("PROJECT_TEMPLATE_NOT_ACTIVE:2",
                "PROJECT_TEMPLATE_ALREADY_INITIALIZED:2", "PROJECT_TEMPLATE_EXISTING_RUNTIME:1")));
        verifyNoInteractions(templates, initialization);
    }

    @Test void emptyScopeDoesNotBroadenQueryAndNullScopeFailsClosed() {
        project(9, 7, "MIGRATION", "ACTIVE");
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of());
        assertEquals(SyncCounts.empty(), task.execute(context(false, 2, 100)));
        verifyNoInteractions(access, templates, initialization);
        var query = new AbstractSyncTask.SourceQuery<>(100, 7, true, new MigratedProjectTemplateSyncTask.Parameters());
        assertTrue(sql.getMapper(MigratedProjectTemplateSyncTask.TargetMapper.class).selectMigratedProjects(query).isEmpty());
        when(scopes.resolveAllCurrent(any())).thenReturn(null);
        assertThrows(NullPointerException.class, () -> task.execute(context(false, 2, 100)));
    }

    @Test void revokedProjectPermissionFailsBeforeMatchingOrWriting() {
        project(9, 7, "MIGRATION", "ACTIVE");
        when(access.authorize(9L, 1L)).thenThrow(new org.springframework.security.access.AccessDeniedException("revoked"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> task.execute(context(false, 2, 100)));
        verifyNoInteractions(templates, initialization);
    }

    @Test void laterInitializationFailureRollsBackEarlierProjectInOuterTransaction() {
        project(9, 7, "MIGRATION", "ACTIVE"); project(10, 7, "MIGRATION", "ACTIVE");
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(9L, 10L));
        when(initialization.initialize(argThat(command -> command.projectId() == 10), eq(1L), anyString()))
                .thenThrow(new IllegalStateException("publication changed"));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> task.execute(context(false, 1, 100))));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM proj_project WHERE lifecycle_template_id IS NOT NULL", Long.class));
    }

    @Test void totalReadLimitAcrossScopeChunksRollsBackAllWrites() {
        project(9, 7, "MIGRATION", "ACTIVE"); project(10, 7, "MIGRATION", "ACTIVE");
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(9L, 10L));
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(status -> task.execute(context(false, 1, 1))));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM proj_project WHERE lifecycle_template_id IS NOT NULL", Long.class));
    }

    @Test void configuredMaximumBatchUsesBoundParametersAndRetainsEmptyResultSemantics() {
        when(scopes.resolveAllCurrent(any())).thenReturn(LongStream.rangeClosed(1, 2000).boxed().collect(Collectors.toSet()));
        assertEquals(SyncCounts.empty(), task.execute(context(true, 2000, 5000)));
        verifyNoInteractions(initialization, externalSource);
    }
}
