package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange.Channel;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateCompiler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real checkpoint Mapper and transaction proxy; project repositories and journal boundary are external test doubles. */
class ProjectResultSubscriptionInstallerTest {
    static final Type TYPE=new Type("TEST","RESULT","FORMED");
    private final ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    private final ProjectPlanVersionMapper plans=mock(ProjectPlanVersionMapper.class);
    private final ProjectNodeExecutionMapper rounds=mock(ProjectNodeExecutionMapper.class);
    private final ProjectBusinessResultSources sources=mock(ProjectBusinessResultSources.class);
    private final ProjectBusinessResultJournal journal=mock(ProjectBusinessResultJournal.class);
    private final PlatformBusinessEventApi outbox=mock(PlatformBusinessEventApi.class);
    private final ProjectMasterDO project=new ProjectMasterDO();
    private final ProjectPlanVersionDO plan=new ProjectPlanVersionDO();
    private final ProjectNodeExecutionDO round=new ProjectNodeExecutionDO();
    private TemplateExecutionSnapshot snapshot;
    private ResultSubscriptionMapper mapper;
    private ProjectResultSubscriptionInstaller installer;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;

    @BeforeEach void before() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var ds=new DriverManagerDataSource("jdbc:h2:mem:subscriptions_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE proj_result_subscription(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,plan_version_id BIGINT,execution_id BIGINT,node_kind VARCHAR(16),node_id BIGINT,node_key VARCHAR(128),contract_id BIGINT,subscription_key VARCHAR(128),channel_id BIGINT,configuration CLOB,baseline_sequence BIGINT,phase VARCHAR(20),inventory_cursor VARCHAR(128),processed_sequence BIGINT,through_sequence BIGINT,version INT,deleted TINYINT DEFAULT 0,UNIQUE(tenant_id,plan_version_id,execution_id,subscription_key))");
        var cfg=new Configuration(new Environment("subscription-test",new SpringManagedTransactionFactory(),ds));cfg.setMapUnderscoreToCamelCase(true);
        String path="mapper/businessresult/ResultSubscriptionMapper.xml";
        try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,cfg,path,cfg.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(cfg)).getMapper(ResultSubscriptionMapper.class);
        var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        var proxy=new ProxyFactory(new ProjectResultSubscriptionInstaller(projects,plans,rounds,mapper,sources,journal,outbox));proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));installer=(ProjectResultSubscriptionInstaller)proxy.getProxy();
        snapshot=snapshot();
        project.setId(3L);project.setTenantId(1L);project.setActivePlanVersionId(10L);project.setLifecycleStatus("ACTIVE");
        plan.setId(10L);plan.setTenantId(1L);plan.setProjectId(3L);plan.setStatus("EFFECTIVE");plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        round.setId(20L);round.setTenantId(1L);round.setProjectId(3L);round.setPlanVersionId(10L);round.setNodeKind("STAGE");
        round.setNodeInstanceId(4L);round.setNodeKey("stage");round.setContractId(30L);round.setCurrentMarker(1);round.setStatus("PENDING");
        when(projects.selectByIdForUpdate(3L)).thenReturn(project);when(plans.selectEffective(any())).thenReturn(plan);when(rounds.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(sources.descriptor(TYPE)).thenReturn(new Descriptor(TYPE,true,true,true));when(sources.changeSupported(TYPE)).thenReturn(true);when(sources.inventorySupported(TYPE)).thenReturn(true);
        when(journal.capture(1L,3L,TYPE)).thenReturn(new ProjectBusinessResultJournal.Boundary(new Channel(1L,1L,3L,TYPE),7));
    }
    @AfterEach void after(){TenantContextHolder.clear();jdbc.execute("SHUTDOWN");}

    @Test void installsPureSubscriptionWithoutAnOperationPageOrVirtualTask() {
        install();var row=mapper.selectIdentityForUpdate(new Identity(1L,3L,10L,20L,"source"));
        assertNotNull(row);assertEquals(7,row.getBaselineSequence());assertEquals(7,row.getProcessedSequence());assertEquals("INVENTORY",row.getPhase());
        assertEquals(30L,row.getContractId());assertEquals("STAGE",row.getNodeKind());assertEquals(0,row.getVersion());
        assertNull(snapshot.getStages().getFirst().getBinding());
        var event=ArgumentCaptor.forClass(BusinessEvent.class);verify(outbox).append(eq("ResultSubscription"),eq(row.getId().toString()),event.capture());
        assertTrue(JsonUtils.parseObject(event.getValue().eventPayload(),ResultSubscriptionWakeup.class).matches(row));
        verify(sources,never()).inspect(any());verify(sources,never()).inventory(any());
    }
    @Test void retryKeepsItsOriginalBoundaryAndDoesNotDuplicateWakeups() {
        install();when(journal.capture(any(),any(),any())).thenThrow(new AssertionError("must not recapture"));install();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription",Integer.class));verify(outbox,times(1)).append(anyString(),anyString(),any());
    }
    @Test void samePublishedPlanCannotOverwriteConfigurationOrRoundIdentity() {
        install();((tools.jackson.databind.node.ObjectNode)snapshot.getStages().getFirst().getExecution().path("subscriptions").get(0).path("policy")).put("selection","ANY_MATCHING");
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertEquals("SUBSCRIPTION_VERSION_IMMUTABLE",assertThrows(IllegalStateException.class,this::install).getMessage());
        assertEquals("EXACT_ONE",ResultSubscriptionContract.read(mapper.selectIdentityForUpdate(new Identity(1L,3L,10L,20L,"source")).getConfiguration()).policy().selection());
    }
    @Test void explicitPlanRebasePreservesTheExistingRoundFormationBoundary() {
        install();project.setActivePlanVersionId(11L);plan.setId(11L);round.setPlanVersionId(11L);round.setContractId(31L);round.setStartedAt(LocalDateTime.now());
        when(journal.capture(1L,3L,TYPE)).thenReturn(new ProjectBusinessResultJournal.Boundary(new Channel(1L,1L,3L,TYPE),20));
        install();var rebased=mapper.selectIdentityForUpdate(new Identity(1L,3L,11L,20L,"source"));
        assertEquals(7,rebased.getBaselineSequence());assertEquals(7,rebased.getProcessedSequence());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription",Integer.class));
    }
    @Test void repeatedPlanChangesDoNotMoveTheOriginalRoundBaseline() {
        install();
        for (long id = 11; id < 16; id++) {
            project.setActivePlanVersionId(id); plan.setId(id); round.setPlanVersionId(id); round.setContractId(id + 20);
            when(journal.capture(1L,3L,TYPE)).thenReturn(new ProjectBusinessResultJournal.Boundary(new Channel(1L,1L,3L,TYPE),id * 10));
            install();
            assertEquals(7, mapper.selectIdentityForUpdate(new Identity(1L,3L,id,20L,"source")).getBaselineSequence());
        }
    }
    @Test void completedHistoricalNodeKeepsItsSubscriptionButWrongScopeCannotHideAsHistory() {
        install(); project.setActivePlanVersionId(11L); plan.setId(11L); round.setEndedAt(LocalDateTime.now());
        install();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription",Integer.class));
        round.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,this::install);
    }
    @Test void aNewReworkRoundDoesNotBorrowTheOldRoundBoundary() {
        install();round.setId(21L);round.setContractId(31L);
        when(journal.capture(1L,3L,TYPE)).thenReturn(new ProjectBusinessResultJournal.Boundary(new Channel(1L,1L,3L,TYPE),20));install();
        assertEquals(20,mapper.selectIdentityForUpdate(new Identity(1L,3L,10L,21L,"source")).getBaselineSequence());
    }
    @Test void sourceChangesOnTheSameRoundCannotQuietlyAcquireANewerBoundary() {
        install();project.setActivePlanVersionId(11L);plan.setId(11L);round.setPlanVersionId(11L);
        when(journal.capture(1L,3L,TYPE)).thenReturn(new ProjectBusinessResultJournal.Boundary(new Channel(2L,1L,3L,TYPE),20));
        assertThrows(IllegalStateException.class,this::install);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription",Integer.class));
    }
    @Test void installAndWakeupRollbackWithThePlanTransaction() {
        doThrow(new IllegalStateException("OUTBOX_FAILED")).when(outbox).append(anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,this::install);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription",Integer.class));
        assertThrows(IllegalTransactionStateException.class,()->installer.synchronize(3L,10L,snapshot));
    }
    @ParameterizedTest @ValueSource(strings={"project-tenant","plan-tenant","plan-id","contract","round-tenant","current","missing","started","snapshot","source"})
    void incompatibleIdentityOrUnavailableSourceStopsBeforeAnySubscriptionIsPublished(String damage) {
        switch(damage){
            case "project-tenant"->project.setTenantId(2L);case "plan-tenant"->plan.setTenantId(2L);
            case "plan-id"->project.setActivePlanVersionId(11L);case "contract"->round.setContractId(null);
            case "round-tenant"->round.setTenantId(2L);case "current"->round.setCurrentMarker(null);
            case "missing"->when(rounds.selectCurrentForUpdate(any())).thenReturn(List.of());
            case "started"->round.setStartedAt(LocalDateTime.now());case "snapshot"->snapshot.getStages().getFirst().setName("tampered");
            case "source"->when(sources.inventorySupported(TYPE)).thenReturn(false);default->throw new AssertionError(damage);}
        assertThrows(RuntimeException.class,this::install);verifyNoInteractions(outbox);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription",Integer.class));
    }
    static TemplateExecutionSnapshot snapshot() {
        var designer=new TemplateDesignerDocument();var stage=new TemplateDesignerDocument.StageNode();
        stage.setNodeKey("stage");stage.setCode("S1");stage.setName("订阅阶段");stage.setLifecycleStage("S1");stage.setStart(true);stage.setTerminal(true);
        var rule=new TemplateDesignerDocument.RuleSpec();rule.setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));stage.setCompletionRule(rule);designer.getStages().add(stage);
        var compiled=new TemplateCompiler().compile(designer);assertTrue(compiled.valid(),()->compiled.issues().toString());
        var snapshot=compiled.snapshot();snapshot.setExecutionSchemaVersion(3);
        snapshot.getStages().getFirst().setExecution(JsonUtils.parseTree("{\"subscriptions\":[{\"key\":\"source\",\"ownerContext\":\"TEST\",\"entityType\":\"RESULT\",\"resultType\":\"FORMED\",\"scope\":{\"mode\":\"PROJECT\"},\"policy\":{\"acquisition\":\"REUSE_EXISTING\",\"validity\":\"CURRENT_VALID\",\"selection\":\"EXACT_ONE\"}}]}"));
        return snapshot;
    }
    private void install(){tx.executeWithoutResult(status->installer.synchronize(3L,plan.getId(),snapshot));}
}
