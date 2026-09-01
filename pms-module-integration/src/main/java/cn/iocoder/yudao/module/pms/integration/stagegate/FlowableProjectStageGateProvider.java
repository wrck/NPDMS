package cn.iocoder.yudao.module.pms.integration.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionSelectionQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartCommand;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartFact;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Flowable原生定义和实例事实的阶段Gate适配器。 */
@Component
public class FlowableProjectStageGateProvider
        implements ProjectStageGateFactProviderApi, ProjectStageGateProcessOwnerApi {

    private static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";
    private static final String CANDIDATE_STRATEGY = "candidateStrategy";
    private static final String START_USER_SELECT = "35";
    private static final String PROCESS_STATUS = "PROCESS_STATUS";
    private static final String PROCESS_START_USER_ID = "PROCESS_START_USER_ID";
    private static final String SKIP_EXPRESSION_ENABLED = "_FLOWABLE_SKIP_EXPRESSION_ENABLED";
    private static final String VAR_TENANT_ID = "pmsGateTenantId";
    private static final String VAR_PROJECT_ID = "pmsGateProjectId";
    private static final String VAR_STAGE_CODE = "pmsGateStageCode";
    private static final String VAR_GATE_ID = "pmsGateId";
    private static final String VAR_GATE_REFERENCE_ID = "pmsGateReferenceId";
    private static final String VAR_REF_TYPE = "pmsGateRefType";
    private static final String VAR_REF_CODE = "pmsGateRefCode";
    private static final String VAR_ACTOR_USER_ID = "pmsGateActorUserId";
    private static final String VAR_DEFINITION_ID = "pmsGateProcessDefinitionId";
    private static final String VAR_OPERATION_ID = "pmsGateOperationId";
    private static final String VAR_REQUEST_DIGEST = "pmsGateRequestDigest";
    private static final Set<String> RESERVED_VARIABLES = Set.of(
            PROCESS_STATUS, PROCESS_START_USER_ID, SKIP_EXPRESSION_ENABLED,
            VAR_TENANT_ID, VAR_PROJECT_ID, VAR_STAGE_CODE, VAR_GATE_ID,
            VAR_GATE_REFERENCE_ID, VAR_REF_TYPE, VAR_REF_CODE, VAR_ACTOR_USER_ID,
            VAR_DEFINITION_ID, VAR_OPERATION_ID, VAR_REQUEST_DIGEST);

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final boolean tenantEnabled;

    public FlowableProjectStageGateProvider(RepositoryService repositoryService,
                                            RuntimeService runtimeService,
                                            HistoryService historyService,
                                            @Value("${yudao.tenant.enable:true}") boolean tenantEnabled) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.tenantEnabled = tenantEnabled;
    }

    @Override
    public Set<String> providerKeys() {
        return Set.of(PROVIDER_BPM_APPROVAL, PROVIDER_BPM_PROCESS);
    }

    @Override
    public ProjectStageGateProcessDefinitionFact inspectDefinitionKey(
            ProjectStageGateProcessDefinitionQuery query) {
        validateTenant(query == null ? null : query.tenantId());
        ProcessDefinition definition = latestDefinition(requireText(query.processDefinitionKey(),
                "processDefinitionKey"));
        if (definition == null) {
            throw new IllegalArgumentException("active process definition not found: " + query.processDefinitionKey());
        }
        requireSelectable(definition);
        return definitionFact(definition);
    }

    @Override
    public List<ProjectStageGateProcessDefinitionFact> listSelectableDefinitions(
            ProjectStageGateProcessDefinitionSelectionQuery query) {
        validateTenant(query == null ? null : query.tenantId());
        requirePositive(query.projectId(), "projectId");
        requirePositive(query.gateReferenceId(), "gateReferenceId");
        ProcessDefinitionQuery definitionQuery = tenantDefinitions(repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(requireText(query.processDefinitionKey(), "processDefinitionKey")))
                .active();
        List<ProcessDefinition> definitions = definitionQuery.orderByProcessDefinitionVersion().desc().list();
        if (definitions == null) {
            throw new IllegalStateException("process definition query returned null");
        }
        return definitions.stream().filter(this::isSelectable).map(this::definitionFact).toList();
    }

    @Override
    public ProjectStageGateProcessStartFact startProcess(ProjectStageGateProcessStartCommand command) {
        validateStartCommand(command);
        String businessKey = command.businessKey();
        HistoricProcessInstance replay = tenantHistory(command.tenantId(),
                historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(businessKey))
                .includeProcessVariables().list().stream()
                .filter(instance -> Objects.equals(variable(instance, VAR_OPERATION_ID), command.operationId()))
                .findFirst().orElse(null);
        if (replay != null) {
            if (!matchesReplay(replay, command)) {
                throw new IllegalArgumentException("same operationId carries a different gate process request");
            }
            return new ProjectStageGateProcessStartFact(replay.getId(), replay.getProcessDefinitionId(),
                    replay.getProcessDefinitionKey(), businessKey, "REPLAYED");
        }

        ProcessDefinition definition = command.selectedProcessDefinitionId() == null
                ? latestDefinition(command.processDefinitionKey())
                : selectedDefinition(command.selectedProcessDefinitionId(), command.processDefinitionKey());
        if (definition == null) {
            throw new IllegalArgumentException("selectable process definition not found");
        }
        requireSelectable(definition);
        Map<String, Object> variables = buildVariables(command, definition.getId());
        Authentication.setAuthenticatedUserId(String.valueOf(command.actorUserId()));
        try {
            ProcessInstanceBuilder builder = runtimeService.createProcessInstanceBuilder()
                    .processDefinitionId(definition.getId())
                    .businessKey(businessKey)
                    .variables(variables);
            if (tenantEnabled) {
                builder.tenantId(String.valueOf(command.tenantId()));
            }
            ProcessInstance instance = builder.start();
            return new ProjectStageGateProcessStartFact(instance.getId(), instance.getProcessDefinitionId(),
                    instance.getProcessDefinitionKey(), businessKey, "STARTED");
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query) {
        validateFactQuery(query);
        String providerKey = providerKey(query.refType());
        try {
            List<HistoricProcessInstance> attempts = tenantHistory(query.tenantId(),
                    historyService.createHistoricProcessInstanceQuery()
                            .processInstanceBusinessKey(businessKey(query.gateReferenceId())))
                    .includeProcessVariables().list();
            if (attempts == null) {
                return unavailable(providerKey, query.refType(), "BPM_QUERY_UNKNOWN");
            }
            List<HistoricProcessInstance> trusted = attempts.stream()
                    .filter(instance -> matchesGate(instance, query)).toList();
            if (trusted.size() != attempts.size()) {
                return unavailable(providerKey, query.refType(), "BPM_INSTANCE_IDENTITY_MISMATCH");
            }
            long activeCount = trusted.stream().filter(instance -> instance.getEndTime() == null).count();
            if (activeCount > 1) {
                return unavailable(providerKey, query.refType(), "BPM_MULTIPLE_ACTIVE_INSTANCES");
            }
            HistoricProcessInstance latest = trusted.stream().max(Comparator
                    .comparing(HistoricProcessInstance::getStartTime)
                    .thenComparing(HistoricProcessInstance::getId)).orElse(null);
            if (latest == null) {
                return unsatisfied(providerKey, query.refType(), "BPM", query.refCode(), "0",
                        unmetPrefix(query.refType()) + "_NOT_STARTED");
            }
            Integer status = integerVariable(latest, PROCESS_STATUS);
            if (status == null || status < 1 || status > 4) {
                return unavailable(providerKey, query.refType(), "BPM_STATUS_UNKNOWN");
            }
            String time = Instant.ofEpochMilli((latest.getEndTime() == null
                    ? latest.getStartTime() : latest.getEndTime()).getTime()).toString();
            String factVersion = status + ":" + time;
            if (status == 2 && latest.getEndTime() != null) {
                return new ProjectStageGateFact(providerKey, query.refType(), latest.getId(),
                        latest.getProcessDefinitionId(), factVersion, ProjectStageGateOutcome.SATISFIED, null);
            }
            String code = switch (status) {
                case 1 -> "RUNNING";
                case 3 -> "REJECTED";
                case 4 -> "CANCELLED";
                default -> "NOT_COMPLETED";
            };
            return unsatisfied(providerKey, query.refType(), latest.getId(),
                    latest.getProcessDefinitionId(), factVersion, unmetPrefix(query.refType()) + "_" + code);
        } catch (RuntimeException ex) {
            return unavailable(providerKey, query.refType(), "BPM_PROVIDER_UNAVAILABLE");
        }
    }

    private ProcessDefinition latestDefinition(String definitionKey) {
        return tenantDefinitions(repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(definitionKey)).active().latestVersion().singleResult();
    }

    private ProcessDefinition selectedDefinition(String definitionId, String expectedKey) {
        ProcessDefinition definition = tenantDefinitions(repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId)).active().singleResult();
        return definition != null && Objects.equals(definition.getKey(), expectedKey) ? definition : null;
    }

    private ProcessDefinitionQuery tenantDefinitions(ProcessDefinitionQuery query) {
        return tenantEnabled
                ? query.processDefinitionTenantId(String.valueOf(TenantContextHolder.getRequiredTenantId()))
                : query.processDefinitionWithoutTenantId();
    }

    private org.flowable.engine.history.HistoricProcessInstanceQuery tenantHistory(
            Long tenantId, org.flowable.engine.history.HistoricProcessInstanceQuery query) {
        return tenantEnabled ? query.processInstanceTenantId(String.valueOf(tenantId)) : query;
    }

    private void requireSelectable(ProcessDefinition definition) {
        if (definition == null || definition.isSuspended() || !isSelectable(definition)) {
            throw new IllegalArgumentException("process definition is not selectable");
        }
    }

    private boolean isSelectable(ProcessDefinition definition) {
        BpmnModel model = repositoryService.getBpmnModel(definition.getId());
        if (model == null || model.getProcesses() == null || model.getProcesses().isEmpty()) {
            return false;
        }
        for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
            if (containsStartUserSelect(process.getFlowElements())) {
                return false;
            }
        }
        return true;
    }

    private boolean containsStartUserSelect(Iterable<FlowElement> elements) {
        for (FlowElement element : elements) {
            if (element instanceof UserTask && START_USER_SELECT.equals(candidateStrategy(element))) {
                return true;
            }
            if (element instanceof SubProcess subProcess && containsStartUserSelect(subProcess.getFlowElements())) {
                return true;
            }
        }
        return false;
    }

    private static String candidateStrategy(FlowElement element) {
        String strategy = element.getAttributeValue(FLOWABLE_NAMESPACE, CANDIDATE_STRATEGY);
        if (strategy != null && !strategy.isBlank()) {
            return strategy.trim();
        }
        List<ExtensionElement> extensions = element.getExtensionElements().get(CANDIDATE_STRATEGY);
        return extensions == null || extensions.isEmpty() ? null : extensions.get(0).getElementText();
    }

    private ProjectStageGateProcessDefinitionFact definitionFact(ProcessDefinition definition) {
        return new ProjectStageGateProcessDefinitionFact(definition.getId(), definition.getKey(),
                definition.getName(), true);
    }

    private static Map<String, Object> buildVariables(ProjectStageGateProcessStartCommand command,
                                                       String actualDefinitionId) {
        Map<String, Object> variables = new HashMap<>();
        if (command.variables() != null) {
            if (command.variables().keySet().stream().anyMatch(RESERVED_VARIABLES::contains)) {
                throw new IllegalArgumentException("variables occupies a reserved process variable");
            }
            variables.putAll(command.variables());
        }
        variables.put(PROCESS_START_USER_ID, command.actorUserId());
        variables.put(PROCESS_STATUS, 1);
        variables.put(SKIP_EXPRESSION_ENABLED, true);
        variables.put(VAR_TENANT_ID, command.tenantId());
        variables.put(VAR_PROJECT_ID, command.projectId());
        variables.put(VAR_STAGE_CODE, command.currentStageCode());
        variables.put(VAR_GATE_ID, command.gateId());
        variables.put(VAR_GATE_REFERENCE_ID, command.gateReferenceId());
        variables.put(VAR_REF_TYPE, command.refType());
        variables.put(VAR_REF_CODE, command.processDefinitionKey());
        variables.put(VAR_ACTOR_USER_ID, command.actorUserId());
        variables.put(VAR_DEFINITION_ID, actualDefinitionId);
        variables.put(VAR_OPERATION_ID, command.operationId());
        variables.put(VAR_REQUEST_DIGEST, command.requestDigest());
        return variables;
    }

    private static boolean matchesReplay(HistoricProcessInstance instance,
                                         ProjectStageGateProcessStartCommand command) {
        return Objects.equals(variable(instance, VAR_REQUEST_DIGEST), command.requestDigest())
                && Objects.equals(variable(instance, VAR_TENANT_ID), command.tenantId())
                && Objects.equals(variable(instance, VAR_PROJECT_ID), command.projectId())
                && Objects.equals(variable(instance, VAR_STAGE_CODE), command.currentStageCode())
                && Objects.equals(variable(instance, VAR_GATE_ID), command.gateId())
                && Objects.equals(variable(instance, VAR_GATE_REFERENCE_ID), command.gateReferenceId())
                && Objects.equals(variable(instance, VAR_REF_TYPE), command.refType())
                && Objects.equals(variable(instance, VAR_REF_CODE), command.processDefinitionKey())
                && Objects.equals(variable(instance, VAR_ACTOR_USER_ID), command.actorUserId())
                && Objects.equals(variable(instance, VAR_DEFINITION_ID), instance.getProcessDefinitionId())
                && (command.selectedProcessDefinitionId() == null
                || Objects.equals(command.selectedProcessDefinitionId(), instance.getProcessDefinitionId()));
    }

    private static boolean matchesGate(HistoricProcessInstance instance, ProjectStageGateFactQuery query) {
        return Objects.equals(variable(instance, VAR_TENANT_ID), query.tenantId())
                && Objects.equals(variable(instance, VAR_PROJECT_ID), query.projectId())
                && Objects.equals(variable(instance, VAR_STAGE_CODE), query.currentStageCode())
                && Objects.equals(variable(instance, VAR_GATE_ID), query.gateId())
                && Objects.equals(variable(instance, VAR_GATE_REFERENCE_ID), query.gateReferenceId())
                && Objects.equals(variable(instance, VAR_REF_TYPE), query.refType())
                && Objects.equals(variable(instance, VAR_REF_CODE), query.refCode())
                && Objects.equals(variable(instance, VAR_DEFINITION_ID), instance.getProcessDefinitionId())
                && Objects.equals(instance.getProcessDefinitionKey(), query.refCode());
    }

    private static Object variable(HistoricProcessInstance instance, String key) {
        Map<String, Object> variables = instance.getProcessVariables();
        return variables == null ? null : variables.get(key);
    }

    private static Integer integerVariable(HistoricProcessInstance instance, String key) {
        Object value = variable(instance, key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static ProjectStageGateFact unsatisfied(String providerKey, String refType,
                                                    String objectKey, String businessVersion,
                                                    String factVersion, String unmetCode) {
        return new ProjectStageGateFact(providerKey, refType, objectKey, businessVersion, factVersion,
                ProjectStageGateOutcome.UNSATISFIED, unmetCode);
    }

    private static ProjectStageGateFact unavailable(String providerKey, String refType, String unmetCode) {
        return new ProjectStageGateFact(providerKey, refType, "BPM", "UNKNOWN", "UNKNOWN",
                ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, unmetCode);
    }

    private static String providerKey(String refType) {
        return "APPROVAL".equals(refType) ? PROVIDER_BPM_APPROVAL : PROVIDER_BPM_PROCESS;
    }

    private static String unmetPrefix(String refType) {
        return "APPROVAL".equals(refType) ? "APPROVAL" : "PROCESS";
    }

    private static String businessKey(Long gateReferenceId) {
        return "PROJECT_STAGE_GATE:" + gateReferenceId;
    }

    private static void validateStartCommand(ProjectStageGateProcessStartCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        validateTenant(command.tenantId());
        requirePositive(command.actorUserId(), "actorUserId");
        requirePositive(command.projectId(), "projectId");
        requirePositive(command.gateId(), "gateId");
        requirePositive(command.gateReferenceId(), "gateReferenceId");
        requireText(command.currentStageCode(), "currentStageCode");
        requireText(command.refType(), "refType");
        requireText(command.processDefinitionKey(), "processDefinitionKey");
        if (!Objects.equals(command.businessKey(), businessKey(command.gateReferenceId()))) {
            throw new IllegalArgumentException("businessKey must match the gate reference identity");
        }
        requireText(command.operationId(), "operationId");
        requireText(command.requestDigest(), "requestDigest");
    }

    private static void validateFactQuery(ProjectStageGateFactQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query is required");
        }
        validateTenant(query.tenantId());
        requirePositive(query.projectId(), "projectId");
        requirePositive(query.gateId(), "gateId");
        requirePositive(query.gateReferenceId(), "gateReferenceId");
        requireText(query.currentStageCode(), "currentStageCode");
        requireText(query.refType(), "refType");
        requireText(query.refCode(), "refCode");
        if (!"APPROVAL".equals(query.refType()) && !"PROCESS".equals(query.refType())) {
            throw new IllegalArgumentException("unsupported BPM gate refType: " + query.refType());
        }
    }

    private static void validateTenant(Long tenantId) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("tenant must match trusted tenant context");
        }
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
