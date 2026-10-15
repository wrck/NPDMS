package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider;
import cn.iocoder.yudao.module.pms.service.dal.mysql.customerreference.ServiceProjectCustomerReferenceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionFeedbackRow;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionStateCount;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real migrated MySQL contracts, not published-rule or end-to-end business acceptance. All writes roll back. */
@EnabledIfSystemProperty(named = "inspection.mysql", matches = "true")
class InspectionMysqlContractTest {
    private static final String JDBC_PREFIX = "jdbc:mysql://127.0.0.1:24306/npdms_inspection_check_20261015_a13c35b?";
    private static final long TASK = 9701015001L;
    private static final long OTHER_TASK = 9701015002L;
    private static final long PROJECT = 9701015099L;
    private static final long ATTEMPT = 9701015201L;
    private static final long RESULT = 9701015501L;
    private static final long REPORT_V1 = 9701015601L;
    private static final long REPORT_V2 = 9701015602L;
    private static final long FILE_V1 = 9701015701L;
    private static final long FALSE_POSITIVE = 9701015802L;
    private static final long CONFIRMATION_V1 = 9701015901L;
    private static final long EXPORT = 9701016001L;
    private static final LocalDateTime DETECTED_AT = LocalDateTime.of(2026, 10, 15, 8, 2);
    private Connection connection;
    private SqlSession session;

    @BeforeEach
    void connectOnlyToApprovedDisposableSchema() throws Exception {
        String url = System.getenv("INSPECTION_VERIFY_JDBC_URL");
        assertTrue(url != null && url.startsWith(JDBC_PREFIX), "只允许指定的巡检隔离 schema");
        String user = System.getenv("INSPECTION_VERIFY_DB_USER");
        String password = System.getenv("INSPECTION_VERIFY_DB_PASSWORD");
        assertNotNull(user, "缺少巡检验证数据库用户环境变量");
        assertNotNull(password, "缺少巡检验证数据库密码环境变量");
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        TenantContextHolder.setTenantId(1L);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        GlobalConfigUtils.getGlobalConfig(configuration).setMetaObjectHandler(new DefaultDBFieldHandler());
        configuration.setEnvironment(new Environment("inspection-contract", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);
        for (var mapper : List.of(InspectionTaskMapper.class, InspectionAttemptMapper.class,
                InspectionTaskDeviceMapper.class, InspectionTaskRuleSnapshotMapper.class, InspectionExecutionMapper.class,
                InspectionCollectionReceiptMapper.class, InspectionDetectionResultMapper.class,
                InspectionReportRevisionMapper.class, InspectionReportResultMapper.class, InspectionReportFileMapper.class,
                InspectionAnnotationMapper.class, ServiceIssueMapper.class, InspectionFeedbackConfirmationMapper.class,
                InspectionFeedbackExportMapper.class, InspectionFeedbackExportItemMapper.class, InspectionFeedbackQueryMapper.class)) {
            configuration.addMapper(mapper);
            String resource = "/mapper/inspection/" + mapper.getSimpleName() + ".xml";
            try (var input = getClass().getResourceAsStream(resource)) {
                if (input != null) new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        configuration.addMapper(ServiceProjectCustomerReferenceMapper.class);
        String customerReferenceResource = "/mapper/customerreference/ServiceProjectCustomerReferenceMapper.xml";
        try (var input = getClass().getResourceAsStream(customerReferenceResource)) {
            new XMLMapperBuilder(input, configuration, customerReferenceResource, configuration.getSqlFragments()).parse();
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(false);
    }

    @AfterEach
    void alwaysRollbackBeforeClosingTheSession() throws Exception {
        try {
            if (connection != null && !connection.isClosed()) connection.rollback();
        } finally {
            try {
                // JdbcTransaction may restore auto-commit on close; roll back fixture SQL BEFORE that happens.
                if (session != null) session.close();
            } finally {
                try { if (connection != null) connection.close(); }
                finally { TenantContextHolder.clear(); }
            }
        }
    }

    @Test
    void customerReferenceGuardRetainsCancelledAndDeletedHistoryWithinTenantAndProject() {
        insertWorkbenchFixtures(List.of(
                new WorkbenchFixture(9701017001L, 1L, PROJECT, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017002L, 1L, PROJECT, 2L, "2", "CANCELLED", true),
                new WorkbenchFixture(9701017003L, 1L, PROJECT + 1, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017004L, 2L, PROJECT, 2L, "2", "PREPARING", false)));
        var mapper = session.getMapper(ServiceProjectCustomerReferenceMapper.class);
        assertEquals(2, mapper.countReferences(new ProjectCustomerReferenceProvider.Query(1L, PROJECT)));
        assertEquals(1, mapper.countReferences(new ProjectCustomerReferenceProvider.Query(1L, PROJECT + 1)));
        assertEquals(0, mapper.countReferences(new ProjectCustomerReferenceProvider.Query(1L, PROJECT + 2)));
        assertEquals(0, mapper.countReferences(new ProjectCustomerReferenceProvider.Query(2L, PROJECT)),
                "Explicit tenant and tenant interceptor must agree");
        TenantContextHolder.setTenantId(2L); session.clearCache();
        assertEquals(1, mapper.countReferences(new ProjectCustomerReferenceProvider.Query(2L, PROJECT)));
    }

    @Test
    void taskMapperRoundTripsNullablePlanOptimisticVersionScopedPageAndTenantIsolation() {
        var mapper = session.getMapper(InspectionTaskMapper.class);
        assertEquals(1, mapper.insert(task(TASK)));
        session.clearCache();
        var first = mapper.selectById(TASK);
        assertEquals(1L, first.getVersion());
        assertNotNull(first.getCreateTime());
        assertEquals("inspection-mysql-contract", first.getCreator());
        var planned = LocalDateTime.of(2026, 10, 16, 9, 30, 0, 123_000_000);
        first.setPlannedAt(planned);
        assertEquals(1, mapper.updateById(first));
        session.clearCache();
        var reopened = mapper.selectById(TASK);
        assertEquals(planned, reopened.getPlannedAt());
        assertEquals(2L, reopened.getVersion());
        session.clearCache();
        var stale = mapper.selectById(TASK);
        reopened.setPlannedAt(null);
        assertEquals(1, mapper.updateById(reopened));
        stale.setTaskName("stale write must fail");
        assertEquals(0, mapper.updateById(stale));
        session.clearCache();
        var cleared = mapper.selectById(TASK);
        assertNull(cleared.getPlannedAt());
        assertEquals(3L, cleared.getVersion());
        assertEquals("巡检 SQL 契约测试", cleared.getTaskName());

        var query = taskPage();
        assertEquals(1, mapper.selectPageCount(query));
        assertEquals(List.of(TASK), mapper.selectPageList(query).stream().map(InspectionTaskDO::getId).toList());
        assertEquals(List.of(new InspectionStateCount("PREPARING", 1)), mapper.selectStateCounts(query));
        query.setVisibleProjectIds(Set.of());
        assertEquals(0, mapper.selectPageCount(query));
        assertTrue(mapper.selectPageList(query).isEmpty());
        assertTrue(mapper.selectStateCounts(query).isEmpty());
        TenantContextHolder.setTenantId(2L); session.clearCache();
        assertNull(mapper.selectById(TASK));
        assertTrue(mapper.selectPageList(taskPage()).isEmpty(), "Tenant interceptor must also constrain XML reads");
    }

    @Test
    void workbenchSectionsIntersectCreatorStatusOwnerAndManagerWithVisibleProjects() throws Exception {
        long managedProject = PROJECT + 1;
        long invisibleProject = PROJECT + 2;
        long ordinaryProject = PROJECT + 3;
        insertWorkbenchFixtures(List.of(
                new WorkbenchFixture(9701017001L, 1L, PROJECT, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017002L, 1L, managedProject, 3L, "2", "CLOSED", false),
                new WorkbenchFixture(9701017003L, 1L, ordinaryProject, 3L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017004L, 1L, invisibleProject, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017005L, 1L, PROJECT, 2L, "3", "PRECHECK_PENDING", false),
                new WorkbenchFixture(9701017006L, 1L, managedProject, 3L, "3", "EXECUTING", false),
                new WorkbenchFixture(9701017007L, 1L, PROJECT, 2L, "", "TRACKING", false),
                new WorkbenchFixture(9701017008L, 1L, ordinaryProject, 3L, "3", "PREPARING", false),
                new WorkbenchFixture(9701017009L, 1L, invisibleProject, 3L, "3", "PREPARING", false),
                new WorkbenchFixture(9701017010L, 1L, PROJECT, 2L, "3", "CLOSED", false),
                new WorkbenchFixture(9701017011L, 1L, managedProject, 3L, "3", "CANCELLED", false),
                new WorkbenchFixture(9701017012L, 1L, PROJECT, 2L, "3", "PREPARING", false),
                new WorkbenchFixture(9701017013L, 1L, PROJECT, 2L, "3", "PREPARING", true),
                new WorkbenchFixture(9701017014L, 1L, PROJECT, 2L, "2", "PREPARING", true),
                new WorkbenchFixture(9701017015L, 2L, PROJECT, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017016L, 2L, PROJECT, 2L, "3", "PREPARING", false),
                new WorkbenchFixture(9701017017L, 1L, PROJECT, 2L, "2", "CANCELLED", false)));
        // Give the archived fixture a same-task report and attempt, satisfying the real archive CHECK/FKs.
        execute("""
                INSERT INTO srv_inspection_attempt
                  (id,tenant_id,task_id,attempt_no,mode,status_code,started_by,started_at)
                VALUES (9701017201,1,9701017012,1,'ONLINE','COMPLETED',2,'2026-10-15 08:00:00')
                """);
        execute("""
                INSERT INTO srv_inspection_report_revision
                  (id,tenant_id,task_id,attempt_id,revision_no,source_code,status_code,input_digest,generated_by,generated_at)
                VALUES (9701017601,1,9701017012,9701017201,1,'MANUAL','AVAILABLE',REPEAT('e',64),2,'2026-10-15 08:05:00')
                """);
        execute("""
                UPDATE srv_inspection_task SET status_code='ARCHIVED',current_attempt_id=9701017201,
                  current_report_id=9701017601,archive_requested_by=2,archive_requested_at='2026-10-15 08:10:00',
                  archived_by=3,archived_at='2026-10-15 08:11:00',customer_archive_id=9701017999
                WHERE id=9701017012 AND tenant_id=1
                """);
        session.clearCache();
        var mapper = session.getMapper(InspectionTaskMapper.class);
        var query = taskPage();
        query.setPageSize(100);
        query.setVisibleProjectIds(Set.of(PROJECT, managedProject, ordinaryProject));
        // Even a manager grant outside the visible set cannot widen that set.
        query.setManagedProjectIds(Set.of(managedProject, invisibleProject));
        query.setWorkbenchSection(InspectionTaskPageQuery.WorkbenchSection.CREATED);
        assertEquals(3, mapper.selectPageCount(query));
        assertEquals(List.of(9701017017L, 9701017002L, 9701017001L),
                mapper.selectPageList(query).stream().map(InspectionTaskDO::getId).toList());
        assertEquals(List.of(new InspectionStateCount("CANCELLED", 1), new InspectionStateCount("CLOSED", 1),
                new InspectionStateCount("PREPARING", 1)), mapper.selectStateCounts(query));

        query.setWorkbenchSection(InspectionTaskPageQuery.WorkbenchSection.PENDING);
        assertEquals(3, mapper.selectPageCount(query));
        assertEquals(List.of(9701017007L, 9701017006L, 9701017005L),
                mapper.selectPageList(query).stream().map(InspectionTaskDO::getId).toList());
        assertEquals(List.of(new InspectionStateCount("EXECUTING", 1), new InspectionStateCount("PRECHECK_PENDING", 1),
                new InspectionStateCount("TRACKING", 1)), mapper.selectStateCounts(query));
    }

    @Test
    void workbenchSectionsFailClosedForEmptyScopeAndTenantDisagreement() {
        insertWorkbenchFixtures(List.of(
                new WorkbenchFixture(9701017001L, 1L, PROJECT, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017002L, 1L, PROJECT, 2L, "3", "PREPARING", false),
                new WorkbenchFixture(9701017003L, 2L, PROJECT, 2L, "2", "PREPARING", false),
                new WorkbenchFixture(9701017004L, 2L, PROJECT, 2L, "3", "PREPARING", false)));
        var mapper = session.getMapper(InspectionTaskMapper.class);
        for (var section : InspectionTaskPageQuery.WorkbenchSection.values()) {
            var query = taskPage(); query.setWorkbenchSection(section);
            assertEquals(1, mapper.selectPageCount(query));
            assertEquals(section == InspectionTaskPageQuery.WorkbenchSection.CREATED ? 9701017001L : 9701017002L,
                    mapper.selectPageList(query).getFirst().getId());
            query.setVisibleProjectIds(Set.of()); query.setManagedProjectIds(Set.of(PROJECT));
            assertEmptyWorkbench(mapper, query);
            query.setVisibleProjectIds(null);
            assertEmptyWorkbench(mapper, query);

            query.setVisibleProjectIds(Set.of(PROJECT)); query.setTenantId(2L);
            session.clearCache();
            assertEmptyWorkbench(mapper, query); // XML tenant=2 and interceptor tenant=1 must not match.
            query.setTenantId(1L); TenantContextHolder.setTenantId(2L); session.clearCache();
            assertEmptyWorkbench(mapper, query);
            TenantContextHolder.setTenantId(1L); session.clearCache();
        }
    }

    @Test
    void legalReportAnnotationAndFeedbackWritesPreserveOldIssuesFilesAndFrozenConfirmations() throws Exception {
        createReportFixture();
        addAnnotationRevisionsAndConfirmation();
        var reports = session.getMapper(InspectionReportRevisionMapper.class);
        var files = session.getMapper(InspectionReportFileMapper.class);
        var annotations = session.getMapper(InspectionAnnotationMapper.class);
        var issues = session.getMapper(ServiceIssueMapper.class);
        assertEquals("AVAILABLE", reports.selectById(REPORT_V1).getStatusCode());
        assertEquals(9701015781L, files.selectById(FILE_V1).getMaterialId());
        assertEquals(List.of("TRACK", "FALSE_POSITIVE"), annotations.selectForTask(taskRows()).stream().map(row -> row.getKind()).toList());
        assertEquals(9701015801L, issues.selectForTaskForUpdate(taskRows()).getFirst().getAnnotationId());
        assertEquals("PENDING", issues.selectForTaskForUpdate(taskRows()).getFirst().getStatusCode());

        var feedback = session.getMapper(InspectionFeedbackQueryMapper.class);
        var query = feedbackQuery();
        query.setFromAt(DETECTED_AT); query.setToAt(DETECTED_AT.plusSeconds(1));
        assertEquals(1, feedback.count(query));
        var row = feedback.selectPage(query).getFirst();
        assertFeedbackMapping(row);
        var statistic = feedback.statistics(query).getFirst();
        assertEquals(970485101L, statistic.getRuleRevisionId());
        assertEquals(970485001L, statistic.getRuleId());
        assertEquals(1, statistic.getRevisionNo());
        assertEquals("EXAMPLE-INS-BASIC", statistic.getDetectionId());
        assertEquals(1L, statistic.getDetectionCount());
        assertEquals(1L, statistic.getConfirmedFalsePositiveCount());

        query.setDetails(false);
        var redacted = feedback.selectPage(query).getFirst();
        assertNull(redacted.getDeviceSerial()); assertNull(redacted.getProductModel()); assertNull(redacted.getOutputSummary());
        assertEquals(FALSE_POSITIVE, redacted.getAnnotationId());
        query.setAdministrator(false); query.setActorId(3L);
        assertEquals(0, feedback.count(query));
        assertTrue(feedback.selectPage(query).isEmpty(), "Ordinary actors cannot read another annotator's feedback");
        query.setActorId(2L);
        assertEquals(1, feedback.count(query));
        query.setAdministrator(true);
        query.setToAt(DETECTED_AT);
        assertEquals(0, feedback.count(query), "Upper date boundary must be exclusive");
        query.setFromAt(DETECTED_AT.plusSeconds(1)); query.setToAt(DETECTED_AT.plusSeconds(2));
        assertEquals(0, feedback.count(query), "Lower date boundary must exclude earlier detections");
        query.setFromAt(null); query.setToAt(null); query.setVisibleProjectIds(Set.of());
        assertEquals(0, feedback.count(query));
        assertTrue(feedback.selectPage(query).isEmpty()); assertTrue(feedback.statistics(query).isEmpty());

        freezeExportThenReviseConfirmation();
        var exportItems = session.getMapper(InspectionFeedbackExportItemMapper.class);
        var frozen = exportItems.selectFrozenRows(new InspectionFeedbackExportRows(1L, EXPORT, true)).getFirst();
        assertEquals(CONFIRMATION_V1, frozen.getConfirmationId());
        assertEquals(1, frozen.getConfirmationVersion()); assertTrue(frozen.getValidFlag());
        assertEquals("旧确认依据", frozen.getConfirmationReason());
        var current = feedback.selectPage(feedbackQuery()).getFirst();
        assertEquals(2, current.getConfirmationVersion()); assertFalse(current.getValidFlag());
        assertEquals("新确认依据", current.getConfirmationReason());
        var hiddenFrozen = exportItems.selectFrozenRows(new InspectionFeedbackExportRows(1L, EXPORT, false)).getFirst();
        assertNull(hiddenFrozen.getDeviceSerial()); assertNull(hiddenFrozen.getProductModel()); assertNull(hiddenFrozen.getOutputSummary());

        createSecondReportVersion();
        session.clearCache();
        assertEquals(List.of(1, 2), reports.selectForTask(taskRows()).stream().map(report -> report.getRevisionNo()).toList());
        var historical = files.selectById(FILE_V1);
        assertEquals(REPORT_V1, historical.getReportId());
        assertEquals(9701015791L, historical.getArtifactId()); assertEquals(1, historical.getFileVersionNo());
        assertEquals("contract-report-v1", historical.getReferenceKey()); assertEquals(9701015781L, historical.getMaterialId());
        assertEquals("contract-v1.pdf", historical.getFileName()); assertEquals("b".repeat(64), historical.getSha256());
        assertEquals(REPORT_V2, session.getMapper(InspectionTaskMapper.class).selectById(TASK).getCurrentReportId());
        var stillFrozen = exportItems.selectFrozenRows(new InspectionFeedbackExportRows(1L, EXPORT, true)).getFirst();
        assertEquals(CONFIRMATION_V1, stillFrozen.getConfirmationId()); assertTrue(stillFrozen.getActiveFlag());
    }

    @Test
    void sameOwnerForeignKeysAndImmutableTriggersRejectMismatchedOrRewrittenEvidence() throws Exception {
        createReportFixture();
        addAnnotationRevisionsAndConfirmation();
        // Different task with an existing attempt, different task's existing device, and another tenant.
        rejectedSql(new RejectedSql("""
                INSERT INTO srv_inspection_execution
                  (id,tenant_id,task_id,attempt_id,device_id,purpose_code,status_code,submitted_by,submitted_at)
                VALUES (9701015409,1,9701015002,9701015201,9701015102,'COLLECT','PENDING',2,'2026-10-15 08:00:00')
                """, "23000", "fk_ins_execution_attempt"));
        rejectedSql(new RejectedSql("""
                INSERT INTO srv_inspection_execution
                  (id,tenant_id,task_id,attempt_id,device_id,purpose_code,status_code,submitted_by,submitted_at)
                VALUES (9701015409,1,9701015001,9701015201,9701015102,'COLLECT','PENDING',2,'2026-10-15 08:00:00')
                """, "23000", "fk_ins_execution_device"));
        rejectedSql(new RejectedSql("""
                INSERT INTO srv_inspection_attempt (id,tenant_id,task_id,attempt_no,mode,status_code,started_by,started_at)
                VALUES (9701015209,2,9701015001,9,'ONLINE','ACTIVE',2,'2026-10-15 08:00:00')
                """, "23000", "fk_ins_attempt_task"));
        rejectedSql(new RejectedSql("""
                INSERT INTO srv_inspection_detection_result
                  (id,tenant_id,task_id,attempt_id,execution_id,rule_snapshot_id,device_id,rule_revision_id,outcome,detail_code)
                VALUES (9701015509,1,9701015001,9701015201,9701015401,9701015302,9701015101,970485101,'ABNORMAL','CONTRACT')
                """, "23000", "fk_ins_result_snapshot"));
        for (String sql : List.of(
                "UPDATE srv_inspection_detection_result SET outcome='PASS' WHERE id=9701015501",
                "UPDATE srv_inspection_report_result SET version=version+1 WHERE id=9701015611",
                "UPDATE srv_inspection_report_file SET reference_key='overwrite' WHERE id=9701015701",
                "UPDATE srv_inspection_report_file SET material_id=9701015782 WHERE id=9701015701",
                "UPDATE srv_inspection_annotation SET kind='IGNORE' WHERE id=9701015801",
                "UPDATE srv_inspection_collection_receipt SET result_version=2 WHERE id=9701015411",
                "UPDATE srv_inspection_feedback_confirmation SET valid_flag=b'0' WHERE id=9701015901")) {
            rejectedSql(new RejectedSql(sql, "45000", "INS_IMMUTABLE_HISTORY"));
        }
        rejectedSql(new RejectedSql("""
                INSERT INTO srv_inspection_report_file
                  (id,tenant_id,task_id,report_id,format_code,artifact_id,file_version_no,reference_key,file_name,sha256)
                VALUES (9701015709,1,9701015001,9701015601,'XML',9701015799,1,'late-file','late.xml',REPEAT('d',64))
                """, "45000", "INS_REPORT_FROZEN"));
        session.clearCache();
        assertEquals("ABNORMAL", session.getMapper(InspectionDetectionResultMapper.class).selectById(RESULT).getOutcome());
        assertEquals("TRACK", session.getMapper(InspectionAnnotationMapper.class).selectById(9701015801L).getKind());
        assertEquals("contract-report-v1", session.getMapper(InspectionReportFileMapper.class).selectById(FILE_V1).getReferenceKey());
        assertEquals(1L, session.getMapper(InspectionCollectionReceiptMapper.class).selectById(9701015411L).getResultVersion());
    }

    private void createReportFixture() throws Exception {
        var tasks = session.getMapper(InspectionTaskMapper.class);
        assertEquals(1, tasks.insert(task(TASK))); assertEquals(1, tasks.insert(task(OTHER_TASK)));
        // This draft is an FK-only test anchor. No rule mutation, publishing, commands or business evaluation.
        try (var statement = connection.prepareStatement("SELECT status_code FROM srv_inspection_rule_revision WHERE tenant_id=1 AND id=970485101");
             var row = statement.executeQuery()) {
            assertTrue(row.next(), "缺少已迁移的 V485 外键草稿 fixture"); assertEquals("DRAFT", row.getString(1));
        }
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("inspection/mysql-contract-fixture.sql"));
        session.clearCache();
        var fileMapper = session.getMapper(InspectionReportFileMapper.class);
        var file = fileMapper.selectById(FILE_V1);
        assertNull(file.getMaterialId());
        file.setMaterialId(9701015781L);
        assertEquals(1, fileMapper.updateById(file));
        // Even while GENERATING, material completion is a one-time assignment, never a replacement.
        rejectedSql(new RejectedSql("UPDATE srv_inspection_report_file SET material_id=9701015782 WHERE id=9701015701",
                "45000", "INS_IMMUTABLE_HISTORY"));
        session.clearCache();
        var reportMapper = session.getMapper(InspectionReportRevisionMapper.class);
        var report = reportMapper.selectById(REPORT_V1);
        report.setStatusCode("AVAILABLE"); assertEquals(1, reportMapper.updateById(report));
        var task = tasks.selectById(TASK);
        task.setCurrentAttemptId(ATTEMPT); task.setCurrentReportId(REPORT_V1); task.setStatusCode("ANNOTATION_PENDING");
        assertEquals(1, tasks.updateById(task));
        session.clearCache();
    }

    private void addAnnotationRevisionsAndConfirmation() throws SQLException {
        execute("""
                INSERT INTO srv_inspection_annotation
                  (id,tenant_id,task_id,report_id,result_id,revision_no,kind,reason,responsible_user_id,due_at,annotated_by,annotated_at)
                VALUES (9701015801,1,9701015001,9701015601,9701015501,1,'TRACK','原跟踪记录',3,'2026-10-16 08:00:00',2,'2026-10-15 08:04:00')
                """);
        execute("""
                INSERT INTO srv_service_issue
                  (id,tenant_id,task_id,report_id,result_id,annotation_id,responsible_user_id,due_at,description,severity_code,status_code)
                VALUES (9701015811,1,9701015001,9701015601,9701015501,9701015801,3,'2026-10-16 08:00:00','原跟踪记录','GENERAL','PENDING')
                """);
        execute("""
                INSERT INTO srv_inspection_annotation
                  (id,tenant_id,task_id,report_id,result_id,revision_no,kind,reason,change_reason,annotated_by,annotated_at)
                VALUES (9701015802,1,9701015001,9701015601,9701015501,2,'FALSE_POSITIVE','误报原因','追加标注修订',2,'2026-10-15 08:05:00')
                """);
        execute("""
                INSERT INTO srv_inspection_feedback_confirmation
                  (id,tenant_id,task_id,annotation_id,revision_no,valid_flag,reason,confirmed_by,confirmed_at)
                VALUES (9701015901,1,9701015001,9701015802,1,b'1','旧确认依据',4,'2026-10-15 08:06:00')
                """);
        session.clearCache();
    }

    private void freezeExportThenReviseConfirmation() throws SQLException {
        execute("""
                INSERT INTO srv_inspection_feedback_export
                  (id,tenant_id,exported_by,format_code,status_code,data_as_of,current_only,include_details,result_count)
                VALUES (9701016001,1,4,'CSV','REQUESTED','2026-10-15 08:07:00',b'1',b'1',1)
                """);
        execute("""
                INSERT INTO srv_inspection_feedback_export_item
                  (id,tenant_id,export_id,project_id,annotation_id,confirmation_id,active_flag)
                VALUES (9701016011,1,9701016001,9701015099,9701015802,9701015901,b'1')
                """);
        execute("""
                INSERT INTO srv_inspection_feedback_confirmation
                  (id,tenant_id,task_id,annotation_id,revision_no,valid_flag,reason,confirmed_by,confirmed_at)
                VALUES (9701015902,1,9701015001,9701015802,2,b'0','新确认依据',4,'2026-10-15 08:08:00')
                """);
        session.clearCache();
    }

    private void createSecondReportVersion() throws SQLException {
        execute("""
                INSERT INTO srv_inspection_report_revision
                  (id,tenant_id,task_id,attempt_id,revision_no,source_code,status_code,input_digest,generated_by,generated_at)
                VALUES (9701015602,1,9701015001,9701015201,2,'MANUAL','GENERATING',REPEAT('c',64),2,'2026-10-15 08:09:00')
                """);
        execute("INSERT INTO srv_inspection_report_result (id,tenant_id,task_id,report_id,result_id) VALUES (9701015612,1,9701015001,9701015602,9701015501)");
        execute("""
                INSERT INTO srv_inspection_report_file
                  (id,tenant_id,task_id,report_id,format_code,artifact_id,file_version_no,reference_key,file_name,sha256)
                VALUES (9701015702,1,9701015001,9701015602,'PDF',9701015792,2,'contract-report-v2','contract-v2.pdf',REPEAT('c',64))
                """);
        execute("UPDATE srv_inspection_report_file SET material_id=9701015782 WHERE id=9701015702");
        execute("UPDATE srv_inspection_report_revision SET status_code='AVAILABLE' WHERE id=9701015602");
        execute("UPDATE srv_inspection_task SET current_report_id=9701015602 WHERE id=9701015001");
        session.clearCache();
    }

    private static void assertFeedbackMapping(InspectionFeedbackRow row) {
        assertEquals(FALSE_POSITIVE, row.getAnnotationId()); assertEquals(TASK, row.getTaskId()); assertEquals(PROJECT, row.getProjectId());
        assertEquals(RESULT, row.getResultId()); assertEquals(2, row.getAnnotationRevision());
        assertEquals(970485101L, row.getRuleRevisionId()); assertEquals(970485001L, row.getRuleId()); assertEquals(1, row.getRevisionNo());
        assertEquals("EXAMPLE-INS-BASIC", row.getDetectionId()); assertEquals("基础检测配置示例", row.getInspectionItem());
        assertEquals("BASIC", row.getCategoryCode()); assertEquals("基础", row.getCategoryName());
        assertNull(row.getSeverityCode()); assertNull(row.getSeverityName()); // The FK-only seed is deliberately incomplete.
        assertEquals("CONTRACT-SN-A", row.getDeviceSerial()); assertEquals("CONTRACT-MODEL-A", row.getProductModel());
        assertEquals("CONTRACT-OUTPUT-A", row.getOutputSummary()); assertEquals("误报原因", row.getReason());
        assertEquals(DETECTED_AT, row.getDetectedAt()); assertEquals(2L, row.getAnnotatedBy());
        assertEquals(CONFIRMATION_V1, row.getConfirmationId()); assertEquals(4L, row.getConfirmedBy()); assertTrue(row.getActiveFlag());
    }

    private static InspectionTaskDO task(long id) {
        var task = new InspectionTaskDO();
        task.setId(id); task.setTenantId(1L); task.setVersion(1L); task.setDeleted(false);
        task.setTaskCode("inspection-contract-" + id); task.setTaskName("巡检 SQL 契约测试");
        task.setProjectId(PROJECT); task.setProjectCode("contract-project"); task.setProjectName("契约测试项目");
        task.setCustomerId(9701015098L); task.setCustomerName("契约测试客户"); task.setOwnerUserId(2L);
        task.setBackground("仅同连接回滚的技术 fixture"); task.setMode("ONLINE"); task.setStatusCode("PREPARING");
        task.setCreator("inspection-mysql-contract"); task.setUpdater("inspection-mysql-contract");
        return task;
    }

    private record WorkbenchFixture(long id, Long tenantId, Long projectId, Long ownerId,
                                    String creator, String status, boolean deleted) { }

    private void insertWorkbenchFixtures(List<WorkbenchFixture> fixtures) {
        var mapper = session.getMapper(InspectionTaskMapper.class);
        for (var fixture : fixtures) {
            TenantContextHolder.setTenantId(fixture.tenantId());
            var row = task(fixture.id());
            row.setTenantId(fixture.tenantId()); row.setProjectId(fixture.projectId()); row.setOwnerUserId(fixture.ownerId());
            row.setCreator(fixture.creator()); row.setStatusCode(fixture.status()); row.setDeleted(fixture.deleted());
            assertEquals(1, mapper.insert(row));
        }
        TenantContextHolder.setTenantId(1L);
        session.clearCache();
    }

    private static void assertEmptyWorkbench(InspectionTaskMapper mapper, InspectionTaskPageQuery query) {
        assertEquals(0, mapper.selectPageCount(query));
        assertTrue(mapper.selectPageList(query).isEmpty());
        assertTrue(mapper.selectStateCounts(query).isEmpty());
    }

    private static InspectionTaskPageQuery taskPage() {
        var query = new InspectionTaskPageQuery();
        query.setTenantId(1L); query.setActorId(2L); query.setVisibleProjectIds(Set.of(PROJECT)); query.setManagedProjectIds(Set.of());
        return query;
    }

    private static InspectionFeedbackQuery feedbackQuery() {
        var query = new InspectionFeedbackQuery();
        query.setTenantId(1L); query.setActorId(2L); query.setVisibleProjectIds(Set.of(PROJECT));
        query.setRuleRevisionId(970485101L); query.setAdministrator(true); query.setDetails(true); query.setCurrentOnly(true);
        query.setDataAsOf(LocalDateTime.of(2026, 10, 15, 9, 0));
        return query;
    }

    private static InspectionTaskRows taskRows() { return new InspectionTaskRows(1L, TASK); }

    private void execute(String sql) throws SQLException {
        assertFalse(connection.getAutoCommit(), "Fixture must remain inside its rollback-only connection transaction");
        try (var statement = connection.createStatement()) { assertEquals(1, statement.executeUpdate(sql)); }
    }

    private record RejectedSql(String sql, String sqlState, String diagnostic) { }

    private void rejectedSql(RejectedSql expected) {
        var error = assertThrows(SQLException.class, () -> execute(expected.sql()));
        assertEquals(expected.sqlState(), error.getSQLState());
        assertTrue(error.getMessage().contains(expected.diagnostic()), "Must fail for the intended FK/trigger, not unrelated invalid SQL");
    }
}
