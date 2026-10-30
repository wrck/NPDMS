package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionReportRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionReportContentQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Mapper/transaction test in memory. JSON uses CLOB here; this does not certify a MySQL migration or browser flow. */
class InspectionUmcReportPersistenceTest {
    private Connection connection;
    private SqlSession session;
    private InspectionReportRevisionMapper reports;
    private final SqlCapture sqlCapture = new SqlCapture();

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    private static class SqlCapture implements Interceptor {
        private final List<String> statements = new ArrayList<>();
        @Override public Object intercept(Invocation invocation) throws Throwable {
            statements.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }
    }

    @BeforeEach void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:umc_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        connection.createStatement().execute("""
                CREATE TABLE srv_inspection_report_revision (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, version BIGINT NOT NULL,
                  task_id BIGINT NOT NULL, attempt_id BIGINT NOT NULL, revision_no INT NOT NULL,
                  source_code VARCHAR(24) NOT NULL, status_code VARCHAR(24) NOT NULL,
                  template_version VARCHAR(64), input_digest CHAR(64), explanation VARCHAR(2000), failure_message VARCHAR(2000),
                  generated_by BIGINT, generated_at TIMESTAMP,
                  creator VARCHAR(64), create_time TIMESTAMP, updater VARCHAR(64), update_time TIMESTAMP,
                  deleted BOOLEAN DEFAULT FALSE NOT NULL,
                  source_context_json CLOB, result_json CLOB, result_sha256 CHAR(64))
                """);
        connection.setAutoCommit(false); TenantContextHolder.setTenantId(7L);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        config.addInterceptor(sqlCapture);
        config.setEnvironment(new Environment("umc-memory", new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        GlobalConfigUtils.getGlobalConfig(config).setMetaObjectHandler(new DefaultDBFieldHandler());
        var interceptors = new MybatisPlusInterceptor();
        interceptors.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptors.addInnerInterceptor(new OptimisticLockerInnerInterceptor()); config.addInterceptor(interceptors);
        config.addMapper(InspectionReportRevisionMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false);
        reports = session.getMapper(InspectionReportRevisionMapper.class);
        var row = new InspectionReportRevisionDO(); row.setId(20L); row.setTenantId(7L); row.setVersion(1L);
        row.setTaskId(10L); row.setAttemptId(30L); row.setRevisionNo(1); row.setSourceCode("UMC"); row.setStatusCode("GENERATING");
        row.setTemplateVersion("umc-doc-v1");
        row.setInputDigest("a".repeat(64)); row.setGeneratedBy(9L); row.setGeneratedAt(LocalDateTime.now());
        row.setSourceContextJson("{\"deviceId\":40}"); row.setResultJson("{\"id\":\"690\",\"taskid\":\"75\",\"devlist\":[]}");
        row.setResultSha256("b".repeat(64)); assertEquals(1, reports.insert(row)); session.commit();
    }

    @AfterEach void close() throws Exception {
        if (session != null) { session.rollback(); session.close(); }
        if (connection != null) connection.close(); TenantContextHolder.clear();
    }

    @Test void executionResultQueryReadsStoredContentOnlyForTheAuthorizedTaskAndTenant() {
        var rows = reports.selectUmcExecutionResults(new InspectionTaskRows(7L, 10L));
        assertEquals(1, rows.size()); assertNotNull(rows.getFirst().getResultJson()); assertNotNull(rows.getFirst().getSourceContextJson());
        assertTrue(reports.selectUmcExecutionResults(new InspectionTaskRows(7L, 11L)).isEmpty());
        assertTrue(reports.selectUmcExecutionResults(new InspectionTaskRows(8L, 10L)).isEmpty());
    }

    @Test void largeExecutionSnapshotsAreOrderedWithoutDatabaseFilesort() {
        var content = reports.selectContent(new InspectionReportContentQuery(7L, 10L, 20L));
        content.setId(19L); content.setVersion(1L); content.setRevisionNo(2);
        content.setResultJson("{\"output\":\"" + "x".repeat(1_048_576) + "\"}");
        assertEquals(1, reports.insert(content)); session.commit(); session.clearCache();
        sqlCapture.statements.clear();
        var rows = reports.selectUmcExecutionResults(new InspectionTaskRows(7L, 10L));
        assertEquals(List.of(19L, 20L), rows.stream().map(InspectionReportRevisionDO::getId).toList());
        assertTrue(rows.getFirst().getResultJson().length() > 1_048_576);
        var sql = sqlCapture.statements.getFirst().toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("result_json"));
        assertFalse(sql.contains("order by"), "Large JSON snapshots must not enter a MySQL filesort buffer");
    }

    @Test void existingReportStoresContentWhileMetadataQueriesExcludeIt() {
        var content = reports.selectContent(new InspectionReportContentQuery(7L, 10L, 20L));
        assertNotNull(content.getResultJson()); assertNotNull(content.getSourceContextJson());
        var metadata = reports.selectForTask(new InspectionTaskRows(7L, 10L)).getFirst();
        assertNull(metadata.getResultJson()); assertNull(metadata.getSourceContextJson()); assertNull(metadata.getResultSha256());
        assertNotNull(reports.selectUmcSources(new InspectionTaskRows(7L, 10L)).getFirst().getSourceContextJson());
        assertNull(reports.selectContent(new InspectionReportContentQuery(7L, 11L, 20L)));
        assertNull(reports.selectContent(new InspectionReportContentQuery(8L, 10L, 20L)));
    }

    @Test void rollbackDoesNotPublishResultOrAvailableStatus() {
        var query = new InspectionReportContentQuery(7L, 10L, 20L); var row = reports.selectContent(query);
        row.setStatusCode("AVAILABLE"); row.setResultJson("{\"id\":\"changed\"}"); assertEquals(1, reports.updateById(row));
        session.rollback(); session.clearCache(); var previous = reports.selectContent(query);
        assertEquals("GENERATING", previous.getStatusCode()); assertTrue(previous.getResultJson().contains("690"));
    }

    @Test void successfulRecoveryClearsTheCurrentFailureInTheSameReportUpdate() {
        var query = new InspectionReportContentQuery(7L, 10L, 20L);
        var failed = reports.selectContent(query);
        failed.setFailureMessage("UMC处理或保存失败（NOT_CONFIGURED），可保留原记录重试");
        assertEquals(1, reports.updateById(failed)); session.commit(); session.clearCache();
        var recovered = reports.selectContent(query);
        String snapshot = recovered.getResultJson(); String digest = recovered.getResultSha256();
        assertNotNull(recovered.getFailureMessage());
        recovered.setStatusCode("AVAILABLE"); recovered.setFailureMessage(null);
        assertEquals(1, reports.updateById(recovered)); session.commit(); session.clearCache();
        var saved = reports.selectContent(query);
        assertEquals("AVAILABLE", saved.getStatusCode()); assertNull(saved.getFailureMessage());
        assertEquals(snapshot, saved.getResultJson()); assertEquals(digest, saved.getResultSha256());
    }

    @Test void staleVersionCannotReplaceAnExistingReport() {
        var query = new InspectionReportContentQuery(7L, 10L, 20L); var first = reports.selectContent(query);
        session.clearCache(); var stale = reports.selectContent(query);
        first.setFailureMessage("waiting to retry"); assertEquals(1, reports.updateById(first));
        stale.setStatusCode("AVAILABLE"); assertEquals(0, reports.updateById(stale));
        session.clearCache(); assertEquals("GENERATING", reports.selectContent(query).getStatusCode());
    }
}
