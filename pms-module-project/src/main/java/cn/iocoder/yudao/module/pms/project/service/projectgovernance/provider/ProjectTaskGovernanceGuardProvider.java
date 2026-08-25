package cn.iocoder.yudao.module.pms.project.service.projectgovernance.provider;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectTaskGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProjectTaskGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {

    public static final String PROVIDER_CODE = "PROJECT_TASK";
    private static final String FACT_VERSION = "PROJECT_TASK_V1";

    private final ProjectTaskMapper taskMapper;

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
        List<ProjectTaskDO> tasks = taskMapper.selectListForGovernanceGuard(
                new ProjectTaskGovernanceGuardQuery(query.tenantId(), query.projectIds()));
        if (tasks.stream().anyMatch(task -> !Objects.equals(task.getTenantId(), query.tenantId())
                || !query.projectIds().contains(task.getProjectId()))) {
            throw new IllegalStateException("project task guard query returned out-of-scope fact");
        }
        List<String> facts = tasks.stream().map(ProjectTaskGovernanceGuardProvider::canonicalFact).toList();
        List<ProjectGovernanceBlocker> blockers = tasks.stream()
                .filter(ProjectTaskGovernanceGuardProvider::isBlocking)
                .sorted(Comparator.comparing(ProjectTaskDO::getProjectId).thenComparing(ProjectTaskDO::getId))
                .map(ProjectTaskGovernanceGuardProvider::toBlocker).toList();
        return fact(facts, blockers);
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static ProjectGovernanceProviderFact fact(List<String> facts,
                                                       List<ProjectGovernanceBlocker> blockers) {
        List<String> orderedFacts = new ArrayList<>(facts);
        orderedFacts.sort(String::compareTo);
        String watermark = orderedFacts.isEmpty() ? "EMPTY"
                : ProjectGovernanceFactDigest.digest(orderedFacts.stream()
                        .map(ProjectTaskGovernanceGuardProvider::watermarkPart).toList());
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, FACT_VERSION, watermark,
                ProjectGovernanceFactDigest.digest(orderedFacts), blockers);
    }

    private static String canonicalFact(ProjectTaskDO task) {
        return value(task.getProjectId()) + "|" + value(task.getId()) + "|" + value(task.getStatus())
                + "|" + value(task.getVersion()) + "|" + value(task.getUpdateTime());
    }

    private static String watermarkPart(String fact) {
        String[] fields = fact.split("\\|", -1);
        return fields[0] + "|" + fields[1] + "|" + fields[3] + "|" + fields[4];
    }

    private static boolean isBlocking(ProjectTaskDO task) {
        return task.getStatus() == null || task.getStatus() < 5 || task.getStatus() > 6;
    }

    private static ProjectGovernanceBlocker toBlocker(ProjectTaskDO task) {
        return new ProjectGovernanceBlocker("PROJECT_TASK", String.valueOf(task.getId()),
                statusName(task.getStatus()), "NON_TERMINAL_PROJECT_TASK",
                "项目任务阻断");
    }

    private static String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case 0 -> "DRAFT";
            case 1 -> "PENDING";
            case 2 -> "IN_PROGRESS";
            case 3 -> "BLOCKED";
            case 4 -> "PENDING_VERIFICATION";
            case 5 -> "COMPLETED";
            case 6 -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
