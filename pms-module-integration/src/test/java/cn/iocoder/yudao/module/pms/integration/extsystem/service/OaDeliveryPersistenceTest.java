package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.module.pms.integration.extsystem.config.IntegrationProperties;
import cn.iocoder.yudao.module.pms.integration.extsystem.config.OaProperties;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import cn.iocoder.yudao.module.pms.integration.extsystem.mapper.IntegrationLogMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.oauth.OAuthTokenCache;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.impl.IntegrationLogServiceImpl;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.impl.OaIntegrationServiceImpl;
import cn.iocoder.yudao.module.pms.workflow.listener.OaTaskListener;
import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL mapper/transactions and loopback HTTP; only OAuth token acquisition is a test port.
 * The CI job requires all seven cases with no skips. This is not deployed OA/application E2E.
 */
class OaDeliveryPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions { }

    record Request(String method, String path, String authorization, String body) { }
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private IntegrationLogServiceImpl logs;
    private OaIntegrationServiceImpl oa;
    private OaTodoPort port;
    private OAuthTokenCache tokens;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("NPDMS_OA_TEST_JDBC_URL");
        if (Boolean.getBoolean("npdms.oa.require-mysql")) assertNotNull(url, "CI must provide the isolated MySQL database");
        Assumptions.assumeTrue(url != null, "Opt-in MySQL integration test; no embedded-database fallback");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:\\d+/npdms_migration_acceptance(?:\\?.*)?"),
                "Only the dedicated loopback acceptance database may be used");
        source = new DriverManagerDataSource(url, System.getenv("NPDMS_OA_TEST_DB_USER"), System.getenv("NPDMS_OA_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            System.out.println("OA acceptance database: " + connection.getMetaData().getDatabaseProductName()
                    + " " + connection.getMetaData().getDatabaseProductVersion());
        }
        jdbc.execute("DROP TABLE IF EXISTS int_log");
        // Use the actual tracked migration DDL, not an invented entity-shaped schema.
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("sql/migrations/V284__init_integration_extsystem_tables.sql"))) root = root.getParent();
        assertNotNull(root, "Repository migration not found");
        String migration = Files.readString(root.resolve("sql/migrations/V284__init_integration_extsystem_tables.sql"));
        int start = migration.indexOf("CREATE TABLE IF NOT EXISTS `int_log`");
        assertTrue(start >= 0);
        jdbc.execute(migration.substring(start, migration.indexOf(';', start)));
        jdbc.execute("CREATE TABLE IF NOT EXISTS acceptance_outer_marker (id BIGINT PRIMARY KEY)");
        jdbc.update("DELETE FROM acceptance_outer_marker");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            int status = responseStatus.get();
            byte[] body = "{\"fixture\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var properties = new OaProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        tokens = mock(OAuthTokenCache.class);
        when(tokens.getToken(eq("oa"), any())).thenReturn("fixture-token");
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var global = new GlobalConfig();
        global.setMetaObjectHandler(new DefaultDBFieldHandler());
        factory.setGlobalConfig(global);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IntegrationLogMapper.class);
        factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject());
        var manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean("transactionManager", DataSourceTransactionManager.class, () -> manager);
        context.registerBean(IntegrationLogMapper.class, () -> sessions.getMapper(IntegrationLogMapper.class));
        context.registerBean(IntegrationProperties.class, IntegrationProperties::new);
        context.registerBean(IntegrationLogServiceImpl.class);
        context.registerBean(OaProperties.class, () -> properties);
        context.registerBean(RestTemplate.class, RestTemplate::new);
        context.registerBean(ObjectMapper.class, ObjectMapper::new);
        context.registerBean(OAuthTokenCache.class, () -> tokens);
        context.registerBean(OaIntegrationServiceImpl.class);
        context.registerBean(OaTodoPortAdapter.class);
        context.refresh();
        logs = context.getBean(IntegrationLogServiceImpl.class);
        oa = context.getBean(OaIntegrationServiceImpl.class);
        port = context.getBean(OaTodoPort.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
        if (server != null) server.stop(0);
    }

    private OaTodoCommand command() {
        return OaTodoCommand.builder().title("审批").content("测试待办").handlerUserId("acceptance-user")
                .processInstanceId("acceptance-process").businessKey("acceptance-business")
                .processUrl("/workflow/acceptance-process").businessType("acceptance-definition").build();
    }
    private IntegrationLog onlyLog() {
        List<IntegrationLog> all = logs.list();
        assertEquals(1, all.size());
        return all.getFirst();
    }

    @Test
    void failedPushCommitsItsLogEvenWhenTheOuterBusinessTransactionRollsBack() {
        responseStatus.set(503);
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO acceptance_outer_marker VALUES (1)");
            assertThrows(IntegrationException.class, () -> port.pushTodo(command()));
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM acceptance_outer_marker", Integer.class));
        IntegrationLog log = onlyLog();
        assertEquals("FAILED", log.getResponseStatus());
        assertNotNull(log.getErrorMessage());
        assertNotNull(log.getNextRetryTime());
        assertEquals("acceptance-business", log.getBusinessId());
        assertEquals("Bearer fixture-token", requests.getFirst().authorization());
    }

    @Test
    void successfulRetryClearsPersistedErrorAndDeadlineWithoutCreatingAnotherLog() {
        responseStatus.set(503);
        assertThrows(IntegrationException.class, () -> port.pushTodo(command()));
        Long id = onlyLog().getId();
        responseStatus.set(200);
        IntegrationLog result = oa.retry(id);
        assertEquals("SUCCESS", result.getResponseStatus());
        assertNull(result.getErrorMessage());
        assertNull(result.getNextRetryTime());
        assertEquals(id, onlyLog().getId());
        assertEquals(2, requests.size());
        assertEquals(requests.get(0).body(), requests.get(1).body());
        assertEquals("POST", requests.get(1).method());
    }

    @Test
    void nonSuccessHttpResponseIsNeverRecordedAsSuccess() {
        responseStatus.set(302); // No Location: the real client receives the non-2xx status.
        assertThrows(IntegrationException.class, () -> port.pushTodo(command()));
        assertEquals("FAILED", onlyLog().getResponseStatus());
        assertNotNull(onlyLog().getNextRetryTime());
    }

    @Test
    void exhaustedRetryClearsThePreviouslyPersistedDeadline() {
        responseStatus.set(503);
        assertThrows(IntegrationException.class, () -> port.pushTodo(command()));
        Long id = onlyLog().getId();
        jdbc.update("UPDATE int_log SET retry_count=max_retry WHERE id=?", id);
        logs.markFailed(id, "exhausted");
        assertEquals("FAILED", onlyLog().getResponseStatus());
        assertNull(onlyLog().getNextRetryTime());
        assertTrue(logs.getPendingRetryLogs().isEmpty());
    }

    @Test
    void emptySuccessBodyExplicitlyClearsAnEarlierResponse() {
        port.pushTodo(command());
        Long id = onlyLog().getId();
        assertNotNull(onlyLog().getResponseBody());
        logs.markSuccess(id, null);
        assertNull(onlyLog().getResponseBody());
    }

    @Test
    void tokenFailurePersistsFailedLogWithoutSendingAnHttpRequest() {
        when(tokens.getToken(eq("oa"), any())).thenThrow(new IntegrationException("oa", "fixture token unavailable"));
        assertThrows(IntegrationException.class, () -> port.pushTodo(command()));
        assertEquals("FAILED", onlyLog().getResponseStatus());
        assertEquals("fixture token unavailable", onlyLog().getErrorMessage());
        assertTrue(requests.isEmpty());
    }

    @Test
    void realFlowableCreateReturnAndCompletePreserveVariablesWhileOaIsUnavailable() {
        responseStatus.set(503);
        var listener = new OaTaskListener(context.getBeanProvider(OaTodoPort.class));
        var engineConfiguration = new SpringProcessEngineConfiguration();
        engineConfiguration.setDataSource(source);
        engineConfiguration.setTransactionManager(context.getBean(DataSourceTransactionManager.class));
        engineConfiguration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        engineConfiguration.setAsyncExecutorActivate(false);
        engineConfiguration.setBeans(Map.of("oaTaskListener", listener));
        ProcessEngine engine = engineConfiguration.buildProcessEngine();
        try {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="acceptance">
                      <process id="oaRepairAcceptance" isExecutable="true">
                        <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                        <userTask id="review" name="Review" flowable:assignee="acceptance-user"><extensionElements>
                          <flowable:taskListener event="create" delegateExpression="${oaTaskListener}"/>
                          <flowable:taskListener event="complete" delegateExpression="${oaTaskListener}"/>
                        </extensionElements></userTask>
                        <sequenceFlow id="s2" sourceRef="review" targetRef="decision"/>
                        <exclusiveGateway id="decision" default="return"/>
                        <sequenceFlow id="accept" sourceRef="decision" targetRef="end"><conditionExpression xsi:type="tFormalExpression" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><![CDATA[${approved}]]></conditionExpression></sequenceFlow>
                        <sequenceFlow id="return" sourceRef="decision" targetRef="rework"/>
                        <userTask id="rework" name="Rework" flowable:assignee="acceptance-user"><extensionElements>
                          <flowable:taskListener event="create" delegateExpression="${oaTaskListener}"/>
                          <flowable:taskListener event="complete" delegateExpression="${oaTaskListener}"/>
                        </extensionElements></userTask>
                        <sequenceFlow id="s3" sourceRef="rework" targetRef="review"/><endEvent id="end"/>
                      </process>
                    </definitions>
                    """;
            var deployment = engine.getRepositoryService().createDeployment().addString("oaRepairAcceptance.bpmn20.xml", xml).deploy();
            var instance = engine.getRuntimeService().startProcessInstanceByKey("oaRepairAcceptance", "acceptance-business",
                    Map.of("businessKey", "acceptance-business", "approved", false, "note", "initial"));
            var tasks = engine.getTaskService();
            var review = tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult();
            assertEquals("review", review.getTaskDefinitionKey());
            tasks.complete(review.getId(), Map.of("approved", false, "note", "returned"));
            var rework = tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult();
            assertEquals("rework", rework.getTaskDefinitionKey());
            assertEquals("returned", tasks.getVariable(rework.getId(), "note"));
            tasks.complete(rework.getId(), Map.of("note", "corrected"));
            review = tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult();
            assertEquals("corrected", tasks.getVariable(review.getId(), "note"));
            tasks.complete(review.getId(), Map.of("approved", true));
            assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance.getId()).singleResult());
            assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instance.getId()).singleResult().getEndTime());
            assertEquals(6, logs.list().size());
            assertTrue(logs.list().stream().allMatch(log -> "FAILED".equals(log.getResponseStatus()) && "acceptance-business".equals(log.getBusinessId())));
            assertEquals(3, requests.stream().filter(request -> "POST".equals(request.method())).count());
            assertEquals(3, requests.stream().filter(request -> "PUT".equals(request.method())).count());
            engine.getRepositoryService().deleteDeployment(deployment.getId(), true);
        } finally {
            engine.close();
        }
    }
}
