package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual service proxy and production gate SQL, isolated to connection-local temporary tables. */
@EnabledIfSystemProperty(named="pms.gate.plan.mysql",matches="true")
class ProjectGateRuleTransactionMySqlTest {
    @Test void gateStatusAndAuditRollbackTogetherAndRetryDoesNotDuplicateThePass() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        TenantContextHolder.setTenantId(7L);
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database = new SingleConnectionDataSource(connection,true); var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_gate (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,gate_code VARCHAR(64),stage_code VARCHAR(32),gate_type VARCHAR(16),status VARCHAR(32),version INT,deleted BIT,updater VARCHAR(64),update_time DATETIME)");
            jdbc.execute("CREATE TEMPORARY TABLE gate_rule_test_audit (actor_id BIGINT NOT NULL,detail JSON)");
            jdbc.execute("INSERT INTO proj_project_gate VALUES (21,7,9,'READY','PREP','ENTRY','PENDING',3,0,'1',NULL),(22,8,9,'READY','PREP','ENTRY','PASSED',6,0,'2',NULL)");
            var configuration = new Configuration(new Environment("gate-rule-temp",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/projectmanual/ProjectGateInstanceMapper.xml")) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,"gates",configuration.getSqlFragments()).parse();
            }
            var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
            var gates = session.getMapper(ProjectGateInstanceMapper.class);
            var projects = mock(ProjectTaskRuntimeMapper.class); var plans = mock(ProjectPlanVersionMapper.class);
            var graph = mock(ProjectRuntimeGraphMapper.class); var references = mock(ProjectGateReferenceInstanceMapper.class);
            var rules = mock(ProjectRuntimeRuleEvaluator.class); var audit = mock(OperationAuditApi.class);
            var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(51L);
            when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
            when(graph.selectGatesForUpdate(any())).thenAnswer(call -> List.of(gates.selectByCodeForUpdate(new ProjectGateForUpdateQuery(7L,9L,"READY"))));
            var stage = new ProjectStageInstanceDO(); stage.setCode("PREP");
            when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
            var ref = new ProjectGateReferenceInstanceDO(); ref.setId(31L); ref.setTenantId(7L); ref.setGateId(21L); ref.setRefType("TASK"); ref.setRefCode("T1"); ref.setVersion(0);
            when(references.selectOrderedForUpdate(any())).thenReturn(List.of(ref));
            var snapshot = new TemplateExecutionSnapshot();
            var definition = new TemplateExecutionSnapshot.GateContract(); definition.setCode("READY"); definition.setStageCode("PREP"); definition.setGateType("ENTRY"); definition.setConditionRuleKey("$gate:ready");
            var frozenRef = new TemplateExecutionSnapshot.GateReference(); frozenRef.setRefType("TASK"); frozenRef.setRefCode("T1"); definition.getReferences().add(frozenRef);
            snapshot.getGates().add(definition);
            snapshot.getRulePrograms().put("$gate:ready",new ProjectRuleCompiler().compile(JsonUtils.parseTree("{\"predicate\":\"TASK\",\"parameters\":{\"refCode\":\"T1\"}}")));
            var plan = new ProjectPlanVersionDO(); plan.setId(51L); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
            when(plans.selectEffective(any())).thenReturn(plan);
            when(rules.evaluate(anyString(),any(),any())).thenReturn(new RuleEvaluation("plan:51:gate:21",RuleEvaluation.Outcome.MATCHED,null,List.of(),List.of(),List.of()));
            var failAudit = new AtomicBoolean(true);
            doAnswer(call -> {
                jdbc.update("INSERT INTO gate_rule_test_audit(actor_id,detail) VALUES (?,?)",call.getArgument(1),JsonUtils.toJsonString(call.getArgument(7)));
                if (failAudit.get()) throw new IllegalStateException("audit failure");
                return null;
            }).when(audit).record(any(),any(),any(),any(),anyString(),anyString(),anyString(),any());
            var target = new ProjectGateRuleService(projects,plans,graph,gates,references,rules,audit);
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(database),new AnnotationTransactionAttributeSource()));
            var service = (ProjectGateRuleService)proxy.getProxy();
            var before = jdbc.queryForList("SELECT * FROM proj_project_gate ORDER BY id");
            assertThrows(IllegalStateException.class,() -> service.evaluate(9L,"READY",null,"retry"));
            assertEquals(before,jdbc.queryForList("SELECT * FROM proj_project_gate ORDER BY id"));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM gate_rule_test_audit",Integer.class));
            failAudit.set(false);
            assertTrue(service.evaluate(9L,"READY",null,"retry").evaluation().matched());
            assertTrue(service.evaluate(9L,"READY",1L,"duplicate").evaluation().matched());
            assertEquals(4,jdbc.queryForObject("SELECT version FROM proj_project_gate WHERE id=21",Integer.class));
            assertEquals("PASSED",jdbc.queryForObject("SELECT status FROM proj_project_gate WHERE id=21",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM gate_rule_test_audit",Integer.class));
            assertEquals(0L,jdbc.queryForObject("SELECT actor_id FROM gate_rule_test_audit",Long.class));
            assertEquals(before.get(1),jdbc.queryForMap("SELECT * FROM proj_project_gate WHERE id=22"));
            when(rules.evaluate(anyString(),any(),any())).thenReturn(new RuleEvaluation("plan:51:gate:21",RuleEvaluation.Outcome.UNKNOWN,"OWNER_UNAVAILABLE",List.of(),List.of(),List.of()));
            assertEquals(RuleEvaluation.Outcome.UNKNOWN,service.evaluate(9L,"READY",1L,"unknown").evaluation().outcome());
            assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM proj_project_gate WHERE id=21",String.class));
            assertEquals(5,jdbc.queryForObject("SELECT version FROM proj_project_gate WHERE id=21",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM gate_rule_test_audit",Integer.class));
        } finally { TenantContextHolder.clear(); }
    }
}
