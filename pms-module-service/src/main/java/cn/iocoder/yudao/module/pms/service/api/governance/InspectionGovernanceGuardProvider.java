package cn.iocoder.yudao.module.pms.service.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask.SrvTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask.query.InspectionGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.service.domain.SrvTaskStatusRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class InspectionGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {

    public static final String PROVIDER_CODE = "INSPECTION";
    private static final String FACT_VERSION = "INSPECTION_TASK_V1";

    private final SrvTaskMapper taskMapper;

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
        List<SrvTaskDO> tasks = taskMapper.selectListForGovernanceGuard(
                new InspectionGovernanceGuardQuery(query.tenantId(), query.projectIds()));
        validateScope(query, tasks);
        List<String> facts = tasks.stream().map(InspectionGovernanceGuardProvider::canonicalFact).sorted().toList();
        List<ProjectGovernanceBlocker> blockers = tasks.stream()
                .filter(task -> !SrvTaskStatusRules.isTerminal(task.getStatus()))
                .sorted(Comparator.comparing(SrvTaskDO::getProjectId).thenComparing(SrvTaskDO::getId))
                .map(InspectionGovernanceGuardProvider::toBlocker).toList();
        return fact(facts, blockers);
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static void validateScope(ProjectGovernanceGuardQuery query, List<SrvTaskDO> tasks) {
        if (tasks.stream().anyMatch(task -> !Objects.equals(task.getTenantId(), query.tenantId())
                || !query.projectIds().contains(task.getProjectId()))) {
            throw new IllegalStateException("inspection guard query returned out-of-scope fact");
        }
    }

    private static ProjectGovernanceProviderFact fact(List<String> facts,
                                                       List<ProjectGovernanceBlocker> blockers) {
        String watermark = facts.isEmpty() ? "EMPTY" : digest(facts.stream()
                .map(InspectionGovernanceGuardProvider::watermarkPart).toList());
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, FACT_VERSION, watermark, digest(facts), blockers);
    }

    private static String canonicalFact(SrvTaskDO task) {
        return value(task.getProjectId()) + "|" + value(task.getId()) + "|" + value(task.getStatus())
                + "|" + value(task.getVersion()) + "|" + value(task.getUpdateTime());
    }

    private static String watermarkPart(String fact) {
        String[] fields = fact.split("\\|", -1);
        return fields[0] + "|" + fields[1] + "|" + fields[3] + "|" + fields[4];
    }

    private static ProjectGovernanceBlocker toBlocker(SrvTaskDO task) {
        return new ProjectGovernanceBlocker("INSPECTION_TASK", String.valueOf(task.getId()),
                statusName(task.getStatus()), "NON_TERMINAL_INSPECTION_TASK", "巡检任务阻断");
    }

    private static String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case 0 -> "DRAFT";
            case 1 -> "PENDING";
            case 2 -> "EXECUTING";
            case 3 -> "PENDING_CONFIRM";
            case 4 -> "COMPLETED";
            case 5 -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }

    private static String digest(List<String> facts) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", facts.stream().sorted().toList()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
