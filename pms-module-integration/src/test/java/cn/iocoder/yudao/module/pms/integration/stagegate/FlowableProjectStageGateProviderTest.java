package cn.iocoder.yudao.module.pms.integration.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartCommand;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class FlowableProjectStageGateProviderTest {

    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private HistoryService historyService;
    private ProcessDefinitionQuery definitionQuery;
    private HistoricProcessInstanceQuery historyQuery;
    private ProcessInstanceBuilder instanceBuilder;
    private FlowableProjectStageGateProvider provider;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        repositoryService = mock(RepositoryService.class);
        runtimeService = mock(RuntimeService.class);
        historyService = mock(HistoryService.class);
        definitionQuery = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        historyQuery = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        instanceBuilder = mock(ProcessInstanceBuilder.class, RETURNS_SELF);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(List.of());
        when(runtimeService.createProcessInstanceBuilder()).thenReturn(instanceBuilder);
        provider = new FlowableProjectStageGateProvider(repositoryService, runtimeService, historyService, true);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void startsLatestDefinitionByKey() {
        ProcessDefinition definition = definition("def-v3", "gate-approval");
        when(definitionQuery.singleResult()).thenReturn(definition);
        ProcessInstance instance = instance("pi-1", definition);
        when(instanceBuilder.start()).thenReturn(instance);

        var fact = provider.startProcess(command(null));

        assertEquals("def-v3", fact.processDefinitionId());
        assertEquals("STARTED", fact.outcome());
        verify(definitionQuery).latestVersion();
        verify(instanceBuilder).processDefinitionId("def-v3");
    }

    @Test
    void startsExplicitHistoricalDefinitionIdWithoutPmsVersion() {
        ProcessDefinition definition = definition("def-v1", "gate-approval");
        when(definitionQuery.singleResult()).thenReturn(definition);
        ProcessInstance instance = instance("pi-2", definition);
        when(instanceBuilder.start()).thenReturn(instance);

        var fact = provider.startProcess(command("def-v1"));

        assertEquals("def-v1", fact.processDefinitionId());
        verify(definitionQuery).processDefinitionId("def-v1");
        verify(instanceBuilder).processDefinitionId("def-v1");
    }

    @Test
    void replaysSameOperationWithoutStartingAnotherInstance() {
        HistoricProcessInstance replay = historic("pi-1", "def-v3", "gate-approval", null,
                Map.ofEntries(Map.entry("pmsGateTenantId", 7L), Map.entry("pmsGateProjectId", 9L),
                        Map.entry("pmsGateStageCode", "S0"), Map.entry("pmsGateId", 21L),
                        Map.entry("pmsGateReferenceId", 22L), Map.entry("pmsGateRefType", "APPROVAL"),
                        Map.entry("pmsGateRefCode", "gate-approval"), Map.entry("pmsGateActorUserId", 8L),
                        Map.entry("pmsGateProcessDefinitionId", "def-v3"), Map.entry("pmsGateOperationId", "op-1"),
                        Map.entry("pmsGateRequestDigest", "digest-1")));
        when(historyQuery.list()).thenReturn(List.of(replay));

        var fact = provider.startProcess(command(null));

        assertEquals("REPLAYED", fact.outcome());
        assertEquals("def-v3", fact.processDefinitionId());
        verify(runtimeService, never()).createProcessInstanceBuilder();
    }

    @Test
    void approvedEndedProcessSatisfiesGateFact() {
        HistoricProcessInstance completed = historic("pi-1", "def-v3", "gate-approval", new Date(2_000),
                Map.of("pmsGateTenantId", 7L, "pmsGateProjectId", 9L, "pmsGateStageCode", "S0",
                        "pmsGateId", 21L, "pmsGateReferenceId", 22L, "pmsGateRefType", "APPROVAL",
                        "pmsGateRefCode", "gate-approval", "pmsGateProcessDefinitionId", "def-v3",
                        "PROCESS_STATUS", 2));
        when(historyQuery.list()).thenReturn(List.of(completed));

        var fact = provider.lockAndRevalidate(new ProjectStageGateFactQuery(
                7L, 9L, "S0", 21L, "G-01", 0, 22L, 0, "APPROVAL", "gate-approval"));

        assertEquals(ProjectStageGateOutcome.SATISFIED, fact.outcome());
        assertEquals("def-v3", fact.ownerBusinessVersion());
    }

    @Test
    void completedProcessSatisfiesGateFact() {
        HistoricProcessInstance completed = historic("pi-2", "def-v2", "gate-process", new Date(3_000),
                Map.of("pmsGateTenantId", 7L, "pmsGateProjectId", 9L, "pmsGateStageCode", "S0",
                        "pmsGateId", 21L, "pmsGateReferenceId", 22L, "pmsGateRefType", "PROCESS",
                        "pmsGateRefCode", "gate-process", "pmsGateProcessDefinitionId", "def-v2",
                        "PROCESS_STATUS", 2));
        when(historyQuery.list()).thenReturn(List.of(completed));

        var fact = provider.lockAndRevalidate(new ProjectStageGateFactQuery(
                7L, 9L, "S0", 21L, "G-01", 0, 22L, 0, "PROCESS", "gate-process"));

        assertEquals(ProjectStageGateOutcome.SATISFIED, fact.outcome());
        assertEquals(ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS, fact.providerKey());
    }

    private ProcessDefinition definition(String id, String key) {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn(id);
        when(definition.getKey()).thenReturn(key);
        when(definition.getName()).thenReturn("阶段审批");
        when(definition.isSuspended()).thenReturn(false);
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.addFlowElement(new UserTask());
        model.addProcess(process);
        when(repositoryService.getBpmnModel(id)).thenReturn(model);
        return definition;
    }

    private static ProcessInstance instance(String id, ProcessDefinition definition) {
        ProcessInstance instance = mock(ProcessInstance.class);
        String definitionId = definition.getId();
        String definitionKey = definition.getKey();
        when(instance.getId()).thenReturn(id);
        when(instance.getProcessDefinitionId()).thenReturn(definitionId);
        when(instance.getProcessDefinitionKey()).thenReturn(definitionKey);
        return instance;
    }

    private static HistoricProcessInstance historic(String id, String definitionId, String definitionKey,
                                                      Date endTime, Map<String, Object> variables) {
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(instance.getId()).thenReturn(id);
        when(instance.getProcessDefinitionId()).thenReturn(definitionId);
        when(instance.getProcessDefinitionKey()).thenReturn(definitionKey);
        when(instance.getStartTime()).thenReturn(new Date(1_000));
        when(instance.getEndTime()).thenReturn(endTime);
        when(instance.getProcessVariables()).thenReturn(variables);
        return instance;
    }

    private static ProjectStageGateProcessStartCommand command(String selectedDefinitionId) {
        return new ProjectStageGateProcessStartCommand(7L, 8L, 9L, "S0", 21L, 22L,
                "APPROVAL", "gate-approval", selectedDefinitionId, "PROJECT_STAGE_GATE:22",
                "op-1", "digest-1", Map.of());
    }
}
