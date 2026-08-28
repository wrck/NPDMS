package cn.iocoder.yudao.module.pms.integration.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class BpmGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {

    public static final String PROVIDER_CODE = "BPM_APPROVAL";
    public static final String PROJECT_ID_VARIABLE = "projectId";
    private static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";
    private static final String FACT_VERSION = "BPM_APPROVAL_V1";

    private final RuntimeService runtimeService;
    private final List<String> knownProcessDefinitionKeys;
    private final boolean tenantEnabled;

    public BpmGovernanceGuardProvider(RuntimeService runtimeService,
                                      @Value("${pms.project.progress-policy.process-definition-key:}")
                                      String progressPolicyProcessDefinitionKey,
                                      @Value("${pms.sol.duration-change.process-definition-key:}")
                                      String durationChangeProcessDefinitionKey,
                                      @Value("${yudao.tenant.enable:true}") boolean tenantEnabled) {
        this.runtimeService = runtimeService;
        this.knownProcessDefinitionKeys = knownKeys(
                progressPolicyProcessDefinitionKey, durationChangeProcessDefinitionKey);
        this.tenantEnabled = tenantEnabled;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProjectGovernanceProviderFact inspect(ProjectGovernanceGuardQuery query) {
        validateTenant(query);
        if (query.projectIds().isEmpty()) {
            return fact(List.of(), List.of());
        }
        if (knownProcessDefinitionKeys == null) {
            return unavailable("PROCESS_DEFINITION_UNCONFIGURED");
        }
        try {
            List<String> facts = new ArrayList<>();
            List<ProjectGovernanceBlocker> blockers = new ArrayList<>();
            for (String processDefinitionKey : knownProcessDefinitionKeys) {
                ProcessInstanceQuery instanceQuery = runtimeService.createProcessInstanceQuery()
                        .processDefinitionKey(processDefinitionKey);
                instanceQuery = tenantEnabled
                        ? instanceQuery.processInstanceTenantId(String.valueOf(query.tenantId()))
                        : instanceQuery.processInstanceWithoutTenantId();
                List<ProcessInstance> instances = instanceQuery.active().includeProcessVariables().list();
                if (instances == null) {
                    return unavailable("QUERY_RESULT_UNKNOWN");
                }
                inspectInstances(query, processDefinitionKey, instances, facts, blockers, tenantEnabled);
            }
            blockers.sort(Comparator.comparing(ProjectGovernanceBlocker::objectId)
                    .thenComparing(ProjectGovernanceBlocker::code));
            return fact(facts, blockers);
        } catch (RuntimeException ex) {
            return unavailable("QUERY_FAILED");
        }
    }

    private static void inspectInstances(ProjectGovernanceGuardQuery query, String processDefinitionKey,
                                         List<ProcessInstance> instances, List<String> facts,
                                         List<ProjectGovernanceBlocker> blockers,
                                         boolean tenantEnabled) {
        instances.stream().sorted(Comparator.comparing(ProcessInstance::getId)).forEach(instance -> {
            Object rawProjectId = variables(instance).get(PROJECT_ID_VARIABLE);
            boolean trustedTenant = tenantEnabled
                    ? Objects.equals(instance.getTenantId(), String.valueOf(query.tenantId()))
                    : instance.getTenantId() == null || instance.getTenantId().isBlank();
            if (!trustedTenant || !(rawProjectId instanceof Long projectId) || projectId <= 0) {
                facts.add(canonicalFact(processDefinitionKey, instance, "ASSOCIATION_UNKNOWN"));
                blockers.add(new ProjectGovernanceBlocker("BPM_APPROVAL", "UNKNOWN", "UNKNOWN",
                        "BPM_ASSOCIATION_UNKNOWN", "审批关联不完整"));
                return;
            }
            if (!query.projectIds().contains(projectId)) {
                return;
            }
            facts.add(canonicalFact(processDefinitionKey, instance, String.valueOf(projectId)));
            blockers.add(new ProjectGovernanceBlocker("BPM_APPROVAL", instance.getId(), "RUNNING",
                    "ACTIVE_BPM_APPROVAL", "项目审批进行中"));
        });
    }

    private static Map<String, Object> variables(ProcessInstance instance) {
        return instance.getProcessVariables() == null ? Map.of() : instance.getProcessVariables();
    }

    private static String canonicalFact(String processDefinitionKey, ProcessInstance instance,
                                        String association) {
        return processDefinitionKey + "|" + value(instance.getId()) + "|" + value(instance.getProcessDefinitionId())
                + "|" + value(instance.getProcessDefinitionVersion()) + "|"
                + (instance.getStartTime() == null ? "" : instance.getStartTime().getTime())
                + "|" + value(instance.getBusinessStatus()) + "|"
                + value(variables(instance).get(PROCESS_STATUS_VARIABLE)) + "|" + association;
    }

    private static ProjectGovernanceProviderFact unavailable(String reason) {
        return fact(List.of("UNAVAILABLE|" + reason), List.of(new ProjectGovernanceBlocker(
                "BPM_APPROVAL", "PROVIDER", "UNAVAILABLE", "PROVIDER_UNAVAILABLE", "审批守卫不可用")));
    }

    private static ProjectGovernanceProviderFact fact(List<String> facts,
                                                       List<ProjectGovernanceBlocker> blockers) {
        List<String> orderedFacts = facts.stream().sorted().toList();
        String watermark = orderedFacts.isEmpty() ? "EMPTY" : digest(orderedFacts);
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, FACT_VERSION, watermark,
                digest(orderedFacts), blockers);
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> knownKeys(String... values) {
        List<String> normalized = java.util.Arrays.stream(values).map(BpmGovernanceGuardProvider::normalize).toList();
        if (normalized.stream().anyMatch(Objects::isNull)) return null;
        return Set.copyOf(normalized).stream().sorted().toList();
    }

    private static String digest(List<String> facts) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", facts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
