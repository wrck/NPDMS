package cn.iocoder.yudao.module.bpm.service.durationchange;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.durationchange.DurationChangeBpmApi.ApprovalOpinionQuery;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import org.flowable.bpmn.model.*;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class DurationChangeBpmServiceTest {
    private static final String KEY = "pms-sol-duration-change";
    private static EmbeddedDatabase database;
    private static ProcessEngine engine;
    private static TransactionTemplate transaction;
    private static DurationChangeBpmService service;
    private String instanceId;
    private String taskId;
    private String businessKey;

    @BeforeAll
    static void createEngine() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var manager = new DataSourceTransactionManager(database);
        transaction = new TransactionTemplate(manager);
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(database);
        configuration.setTransactionManager(manager);
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        engine = configuration.buildProcessEngine();
        service = new DurationChangeBpmService(engine.getHistoryService());
        var model = new BpmnModel();
        var process = new org.flowable.bpmn.model.Process();
        process.setId(KEY);
        process.setExecutable(true);
        model.addProcess(process);
        var start = new StartEvent(); start.setId("start");
        var approve = new UserTask(); approve.setId("serviceManagerApprove");
        approve.setName("服务经理审批"); approve.setAssignee("2");
        var end = new EndEvent(); end.setId("end");
        process.addFlowElement(start); process.addFlowElement(approve); process.addFlowElement(end);
        var first = new SequenceFlow("start", "serviceManagerApprove"); first.setId("start-approve");
        var last = new SequenceFlow("serviceManagerApprove", "end"); last.setId("approve-end");
        process.addFlowElement(first); process.addFlowElement(last);
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addBpmnModel(KEY + ".bpmn20.xml", model).deploy();
    }

    @AfterAll
    static void closeEngine() {
        try { if (engine != null) engine.close(); }
        finally { if (database != null) database.shutdown(); }
    }

    @BeforeEach
    void startChange() {
        TenantContextHolder.setTenantId(1L);
        businessKey = UUID.randomUUID().toString();
        instanceId = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId(KEY, businessKey,
                Map.of(PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.RUNNING.getStatus()), "1").getId();
        taskId = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult().getId();
    }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void readsTaskOpinionBeforeCommitEvenWhenProcessReasonIsAbsent() {
        var opinion = new AtomicReference<String>();
        transaction.executeWithoutResult(status -> {
            complete("同意延长一天");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    opinion.set(service.getApprovalOpinion(query()));
                }
            });
        });
        assertEquals("同意延长一天", opinion.get());
        assertNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instanceId)
                .includeProcessVariables().singleResult().getProcessVariables().get(PROCESS_INSTANCE_VARIABLE_REASON));
    }

    @Test
    void allowsApprovalWithoutOptionalOpinion() {
        transaction.executeWithoutResult(status -> complete(null));
        assertNull(service.getApprovalOpinion(query()));
    }

    @Test
    void rejectsWrongTenantAndBusinessAssociation() {
        transaction.executeWithoutResult(status -> complete("同意"));
        assertThrows(IllegalArgumentException.class, () -> service.getApprovalOpinion(
                new ApprovalOpinionQuery(2L, instanceId, KEY, businessKey, 2L)));
        assertThrows(IllegalStateException.class, () -> service.getApprovalOpinion(
                new ApprovalOpinionQuery(1L, instanceId, KEY, "other-change", 2L)));
        assertThrows(IllegalStateException.class, () -> service.getApprovalOpinion(
                new ApprovalOpinionQuery(1L, instanceId, "other-definition", businessKey, 2L)));
    }

    @Test
    void rejectsUnfinishedProcess() {
        assertThrows(IllegalStateException.class, () -> service.getApprovalOpinion(query()));
    }

    @Test
    void mismatchedApproverRollsBackOriginalApprovalTransaction() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            complete("不可错配");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    service.getApprovalOpinion(new ApprovalOpinionQuery(1L, instanceId, KEY, businessKey, 99L));
                }
            });
        }));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult());
        assertNull(engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult().getEndTime());
    }

    private ApprovalOpinionQuery query() {
        return new ApprovalOpinionQuery(1L, instanceId, KEY, businessKey, 2L);
    }

    private void complete(String opinion) {
        engine.getTaskService().setVariableLocal(taskId, TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
        if (opinion != null) engine.getTaskService().setVariableLocal(taskId, TASK_VARIABLE_REASON, opinion);
        engine.getRuntimeService().setVariable(instanceId, PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        engine.getTaskService().complete(taskId);
    }
}
