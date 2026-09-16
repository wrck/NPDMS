package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectControlledOperationExecutorTest {
    private static final String CODE = "SOL.SITE_SURVEY.CONFIRM";
    private final ProjectOperationContextResolver contexts = mock(ProjectOperationContextResolver.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectBusinessOperationRegistry registry = mock(ProjectBusinessOperationRegistry.class);
    private final ProjectOperationAdapters adapters = mock(ProjectOperationAdapters.class);
    private final ProjectBusinessOperationCommandAdapter adapter = mock(ProjectBusinessOperationCommandAdapter.class);
    private final ProjectBusinessOperationAccessProvider access = mock(ProjectBusinessOperationAccessProvider.class);
    private final ProjectOperationRuleEvaluator rules = mock(ProjectOperationRuleEvaluator.class);
    private final PlatformCommandExecutionApi idempotency = mock(PlatformCommandExecutionApi.class);
    private final ProjectOperationResultSink sink = mock(ProjectOperationResultSink.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<ProjectOperationResultSink> sinks = mock(ObjectProvider.class);
    private MockedStatic<TenantContextHolder> tenant;
    private MockedStatic<SecurityFrameworkUtils> security;
    private ProjectControlledOperationExecutor executor;
    private ProjectOperationCommand command;
    private ProjectOperationContextResolver.Context context;
    private final ProjectOperationResult result = new ProjectOperationResult("SOL", "SITE_SURVEY", "11", null, 3,
            "SOL:SITE_SURVEY:11:3:1", "SURVEY_CONFIRMED", JsonUtils.parseTree("{}"), false);

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        tenant = mockStatic(TenantContextHolder.class); tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
        security = mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        var selection = new ProjectBusinessExecutionSelection(null, new ProjectStageExecutionContext(9L, 1, 10L, 1, 20L, 1, 30L, 40L, 1, 1, true));
        command = new ProjectOperationCommand(9L, "STAGE", 10L, selection, "11", 2, "owner:2", JsonUtils.parseTree("{}"), "request-1");
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L);
        var round = new ProjectNodeExecutionDO(); round.setId(40L); round.setPlanVersionId(30L);
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setTargetContextCode("SOL"); binding.setTargetObjectType("SITE_SURVEY");
        binding.setOperationContract(JsonUtils.parseTree("{\"version\":1,\"operations\":[{\"operationCode\":\"" + CODE
                + "\",\"operationVersion\":1,\"pre\":{\"mode\":\"NONE\"},\"post\":{\"mode\":\"NONE\"}}],\"programs\":{}}"));
        context = new ProjectOperationContextResolver.Context(1L,7L,project,
                new ProjectOperationCapabilities.Node(9L,"STAGE",10L,"stage-A","A","ACTIVE"),round,binding,selection,true,null);
        when(contexts.resolve(eq(9L),eq("STAGE"),eq(10L),nullable(ProjectBusinessExecutionSelection.class))).thenReturn(context);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project); when(projects.selectById(9L)).thenReturn(project);
        when(executions.beginStageHandling(selection.stage(),7L)).thenReturn(selection.stage());
        when(registry.find(CODE,1)).thenReturn(new ProjectBusinessOperationDescriptor(CODE,1,"SOL","SITE_SURVEY","Confirm","CONFIRM",Set.of("PRE","POST"),getClass(),"setup"));
        when(registry.runtimeAvailable(CODE,1)).thenReturn(true); when(adapters.require(CODE,1)).thenReturn(adapter);
        when(access.ownerContext()).thenReturn("SOL"); when(access.objectType()).thenReturn("SITE_SURVEY");
        when(access.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(CODE),"owner:2"));
        when(rules.evaluate(anyString(),any(),any(),any())).thenReturn(new ProjectOperationRuleEvaluator.Evaluation("MATCHED",null));
        when(sinks.getObject()).thenReturn(sink);
        when(idempotency.execute(any(),anyString(),eq(ProjectOperationResult.class),any(),any())).thenAnswer(invocation ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<ProjectOperationResult>) invocation.getArgument(3)).get()));
        executor = new ProjectControlledOperationExecutor(contexts,executions,projects,registry,adapters,List.of(access),rules,idempotency,sinks);
    }
    @AfterEach void cleanup() { security.close(); tenant.close(); assertNull(ProjectVerifiedOperationScope.current()); }
    @Test void permittedOperationInvokesOwnerOnceAndAppendsAfterPost() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(result, executor.execute(CODE,1,command,current -> {
            calls.incrementAndGet(); assertEquals(current.execution(), ProjectVerifiedOperationScope.current().execution()); return result;
        }));
        assertEquals(1,calls.get()); verify(sink).append(eq(CODE),eq(1),any(),eq(result),eq(1L),eq(7L),anyString());
    }
    @Test void preFailureHasNoOwnerEffectOrSuccessEvent() {
        when(rules.evaluate(contains(":PRE"),any(),any(),any())).thenReturn(new ProjectOperationRuleEvaluator.Evaluation("NOT_MATCHED",null));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RuntimeException.class,() -> executor.execute(CODE,1,command,c -> { calls.incrementAndGet(); return result; }));
        assertEquals(0,calls.get()); verifyNoInteractions(sink);
    }
    @Test void postFailureProducesNoSuccessEventAndClearsScope() {
        when(rules.evaluate(contains(":POST"),any(),any(),any())).thenReturn(new ProjectOperationRuleEvaluator.Evaluation("NOT_MATCHED",null));
        assertThrows(RuntimeException.class,() -> executor.execute(CODE,1,command,c -> result)); verifyNoInteractions(sink);
    }
    @Test void ownerFailureProducesNoSuccessEvent() {
        assertThrows(IllegalStateException.class,() -> executor.execute(CODE,1,command,c -> { throw new IllegalStateException("failure"); }));
        verifyNoInteractions(sink);
    }
    @Test void ownerPermissionIsNotGrantedByProject() {
        when(access.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(),"owner:2"));
        assertThrows(RuntimeException.class,() -> executor.execute(CODE,1,command,c -> fail("Owner must not run")));
    }
    @Test void staleBusinessFactCannotUseEarlierCapabilityResponse() {
        when(access.inspect(any())).thenReturn(new ProjectBusinessOperationAccessProvider.Access(Set.of(CODE),"owner:3"));
        assertThrows(RuntimeException.class,() -> executor.execute(CODE,1,command,c -> fail("stale write")));
    }
    @Test void explicitCommandCannotOmitOrMixExecutionIdentity() {
        var bad = new ProjectOperationCommand(9L,"TASK",10L,command.execution(),"11",2,"owner:2",command.input(),"key");
        assertThrows(RuntimeException.class,() -> ProjectControlledOperationExecutor.validate(bad));
    }
    @Test void replayReauthorizesButDoesNotRerunRulesOrOwner() {
        when(idempotency.execute(any(),anyString(),eq(ProjectOperationResult.class),any(),any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,result));
        assertTrue(executor.execute(CODE,1,command,c -> fail("duplicate effect")).replayed());
        verify(adapter).authorizeReplay(CODE,command); verifyNoInteractions(rules,sink);
    }
    @Test void missingPipelineCannotActivateOrExecuteNewContracts() {
        when(registry.runtimeAvailable(CODE,1)).thenReturn(false);
        assertThrows(RuntimeException.class,() -> executor.execute(CODE,1,command,c -> fail("pipeline unavailable")));
    }
    @Test void postReadsProjectAgainAfterOwnerWrite() {
        executor.execute(CODE,1,command,c -> result);
        verify(projects).selectById(9L); verify(rules).evaluate(contains(":POST"),any(),any(),any());
    }
    @Test void postFailureRollsBackActualJdbcTransaction() {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:controlled_post_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("create table operation_test (id integer primary key)");
        var advice = new org.springframework.transaction.interceptor.TransactionInterceptor();
        advice.setTransactionManager(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        advice.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        var factory = new org.springframework.aop.framework.ProxyFactory(executor); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        var transactional = (ProjectControlledOperationExecutor) factory.getProxy();
        when(rules.evaluate(contains(":POST"),any(),any(),any())).thenReturn(new ProjectOperationRuleEvaluator.Evaluation("NOT_MATCHED",null));
        assertThrows(RuntimeException.class,() -> transactional.execute(CODE,1,command,c -> {
            jdbc.update("insert into operation_test(id) values (1)"); return result;
        }));
        assertEquals(0,jdbc.queryForObject("select count(*) from operation_test",Integer.class));
        verifyNoInteractions(sink);
    }
    @Test void stageStartVersionIsPassedToOwnerWithoutChangingRetryDigestInput() {
        var started = new ProjectStageExecutionContext(9L,1,10L,1,20L,1,30L,40L,2,1,true);
        when(executions.beginStageHandling(command.execution().stage(),7L)).thenReturn(started);
        executor.execute(CODE,1,command,c -> { assertEquals(started,c.execution().stage()); return result; });
        assertEquals(1,command.execution().stage().executionVersion());
    }
}
