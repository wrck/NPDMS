package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange.Channel;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.InventoryPage;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateCompiler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实生产Mapper XML、事务代理和Outbox事务替身；Owner数据、项目仓储及日志边界使用外围Mock。 */
final class ResultSubscriptionRecoveryFixture implements AutoCloseable {
    static final Type TYPE = new Type("TEST", "RESULT", "FORMED");
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper rounds = mock(ProjectNodeExecutionMapper.class);
    final ProjectBusinessResultSources sources = mock(ProjectBusinessResultSources.class);
    final ProjectBusinessResultJournal journal = mock(ProjectBusinessResultJournal.class);
    final ProjectMasterDO project = new ProjectMasterDO();
    final ProjectPlanVersionDO plan = new ProjectPlanVersionDO();
    final ProjectNodeExecutionDO round = new ProjectNodeExecutionDO();
    final Channel channel = new Channel(1L,1L,3L,TYPE);
    final List<BusinessResultChange> changes = new ArrayList<>();
    final JdbcTemplate jdbc;
    final DataSourceTransactionManager transactions;
    final ResultSubscriptionMapper subscriptions;
    final ResultSubscriptionCandidateMapper candidates;
    final PlatformBusinessEventApi outbox;
    final ProjectResultSubscriptionContext contexts;
    final ProjectResultSubscriptionWorker worker;
    final ProjectResultSubscriptionFanout fanout;
    final ProjectResultSubscriptionDelivery delivery;
    final TemplateExecutionSnapshot snapshot;
    boolean failOutbox;
    long committed = 7;

    ResultSubscriptionRecoveryFixture() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var ds = new DriverManagerDataSource("jdbc:h2:mem:recovery_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); transactions = new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE proj_result_subscription(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,plan_version_id BIGINT,execution_id BIGINT,node_kind VARCHAR(16),node_id BIGINT,node_key VARCHAR(128),contract_id BIGINT,subscription_key VARCHAR(128),channel_id BIGINT,configuration CLOB,baseline_sequence BIGINT,phase VARCHAR(20),inventory_cursor VARCHAR(128),processed_sequence BIGINT,through_sequence BIGINT,version INT,deleted TINYINT DEFAULT 0,UNIQUE(tenant_id,plan_version_id,execution_id,subscription_key))");
        jdbc.execute("CREATE TABLE proj_result_subscription_candidate(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,subscription_id BIGINT,object_id VARCHAR(128),result_id VARCHAR(128),formation_sequence BIGINT,observed_sequence BIGINT,observation CLOB,UNIQUE(tenant_id,subscription_id,object_id,result_id))");
        jdbc.execute("CREATE TABLE recovery_test_outbox(event_id VARCHAR(128) PRIMARY KEY,event_type VARCHAR(128),payload CLOB)");
        var configuration = new Configuration(new Environment("recovery", new SpringManagedTransactionFactory(), ds));
        configuration.setMapUnderscoreToCamelCase(true);
        for (var file : List.of("ResultSubscriptionMapper", "ResultSubscriptionCandidateMapper")) {
            String resource = "mapper/businessresult/" + file + ".xml";
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream); new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        subscriptions = session.getMapper(ResultSubscriptionMapper.class); candidates = session.getMapper(ResultSubscriptionCandidateMapper.class);
        outbox = new PlatformBusinessEventApi() {
            public void append(String kind, String key, BusinessEvent event) {
                if (failOutbox) throw new IllegalStateException("OUTBOX_UNAVAILABLE");
                var existing = jdbc.queryForList("SELECT payload FROM recovery_test_outbox WHERE event_id=?", String.class, event.eventId());
                if (existing.isEmpty()) jdbc.update("INSERT INTO recovery_test_outbox VALUES(?,?,?)", event.eventId(), event.eventType(), event.eventPayload());
                else assertEquals(existing.getFirst(), event.eventPayload());
            }
            public void appendAt(String kind, String key, BusinessEvent event, LocalDateTime time) { append(kind, key, event); }
        };
        snapshot = snapshot();
        project.setId(3L); project.setTenantId(1L); project.setActivePlanVersionId(10L); project.setLifecycleStatus("ACTIVE");
        plan.setId(10L); plan.setTenantId(1L); plan.setProjectId(3L); plan.setStatus("EFFECTIVE"); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        round.setId(20L); round.setTenantId(1L); round.setProjectId(3L); round.setPlanVersionId(10L); round.setNodeKind("STAGE");
        round.setNodeInstanceId(4L); round.setNodeKey("stage"); round.setContractId(30L); round.setCurrentMarker(1); round.setStatus("ACTIVE");
        when(projects.selectByIdForUpdate(3L)).thenReturn(project); when(plans.selectById(10L)).thenReturn(plan);
        when(rounds.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(sources.inventory(any())).thenReturn(new InventoryPage(null, true, List.of()));
        when(sources.inspect(any())).thenReturn(Observation.absent(Status.NOT_FOUND, "NOT_FOUND"));
        when(journal.capture(1L,3L,TYPE)).thenAnswer(call -> new ProjectBusinessResultJournal.Boundary(channel, committed));
        when(journal.read(any(), anyLong(), anyInt())).thenAnswer(call -> {
            var through = call.getArgument(0, ProjectBusinessResultJournal.Boundary.class); long after = call.getArgument(1); int limit = call.getArgument(2);
            var page = changes.stream().filter(change -> change.sequence() > after && change.sequence() <= through.sequence()).limit(limit).toList();
            if (page.size() != Math.min(limit, through.sequence()-after)) throw new IllegalStateException("RESULT_CHANGE_GAP");
            long next = after + page.size(); return new ProjectBusinessResultJournal.Page(through, next, next == through.sequence(), page);
        });
        contexts = new ProjectResultSubscriptionContext(projects,plans,rounds,subscriptions);
        worker = proxy(new ProjectResultSubscriptionWorker(contexts,sources,journal,new ProjectResultSubscriptionCandidates(candidates,sources),subscriptions,outbox));
        fanout = proxy(new ProjectResultSubscriptionFanout(journal,subscriptions,outbox));
        delivery = new ProjectResultSubscriptionDelivery(worker,fanout);
        subscriptions.insert(subscription(501,20));
    }
    ResultSubscriptionDO subscription(long id,long execution) {
        var row = new ResultSubscriptionDO(); row.setId(id);row.setTenantId(1L);row.setProjectId(3L);row.setPlanVersionId(10L);row.setExecutionId(execution);
        row.setNodeKind("STAGE");row.setNodeId(4L);row.setNodeKey("stage");row.setContractId(30L);row.setSubscriptionKey("source");
        row.setChannelId(1L);row.setConfiguration(snapshot.getStages().getFirst().getExecution().get("subscriptions").get(0).toString());
        row.setBaselineSequence(7L);row.setProcessedSequence(7L);row.setPhase("INVENTORY");row.setVersion(0);return row;
    }
    ResultSubscriptionDO row() { return subscriptions.selectByIdForUpdate(new ResultSubscriptionMapper.IdQuery(1L,3L,501L)); }
    void tick() { worker.process(ResultSubscriptionWakeup.create(row())); }
    List<ResultSubscriptionCandidateDO> found() { return candidates.selectPage(new ResultSubscriptionCandidateMapper.Page(1L,3L,501L,0,100)); }
    Observation result(String object, String result, Validity validity) {
        return Observation.available(new Result(1L,3L,TYPE,object,result,null,"v1",validity,LocalDateTime.of(2026,9,18,0,0)));
    }
    BusinessResultChange change(long sequence, Observation observation, boolean formation) {
        var source = new BusinessOperationResultEvent(UUID.randomUUID().toString(),1,1L,3L,"TEST","RESULT", "native-object",
                null,1,"v1","OBSERVED","TEST.RESULT.CHANGED","owner-command",0L,LocalDateTime.of(2026,9,18,0,0),"test");
        return new BusinessResultChange(UUID.randomUUID().toString(),1,channel,sequence,source,observation,formation);
    }
    <T> T proxy(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked") T result = (T) proxy.getProxy(); return result;
    }
    public void close() { TenantContextHolder.clear(); jdbc.execute("SHUTDOWN"); }
    static TemplateExecutionSnapshot snapshot() {
        var designer = new TemplateDesignerDocument(); var stage = new TemplateDesignerDocument.StageNode();
        stage.setNodeKey("stage");stage.setCode("S1");stage.setName("结果订阅");stage.setLifecycleStage("S1");stage.setStart(true);stage.setTerminal(true);
        var rule = new TemplateDesignerDocument.RuleSpec();rule.setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));
        stage.setCompletionRule(rule);designer.getStages().add(stage);var compiled = new TemplateCompiler().compile(designer);
        assertTrue(compiled.valid(), () -> compiled.issues().toString());var snapshot = compiled.snapshot();snapshot.setExecutionSchemaVersion(3);
        snapshot.getStages().getFirst().setExecution(JsonUtils.parseTree("{\"subscriptions\":[{\"key\":\"source\",\"ownerContext\":\"TEST\",\"entityType\":\"RESULT\",\"resultType\":\"FORMED\",\"scope\":{\"mode\":\"PROJECT\"},\"policy\":{\"acquisition\":\"REUSE_EXISTING\",\"validity\":\"CURRENT_VALID\",\"selection\":\"EXACT_ONE\"}}]}"));
        return snapshot;
    }
}
