package cn.iocoder.yudao.module.pms.cutover.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.cutover.domain.CutTaskStatusRules;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
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
public class CutoverGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {

    public static final String PROVIDER_CODE = "CUTOVER";
    private static final String FACT_VERSION = "CUTOVER_TASK_V1";

    private final CutTaskMapper taskMapper;

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
        List<CutTaskDO> tasks = taskMapper.selectListForGovernanceGuard(
                new CutoverGovernanceGuardQuery(query.tenantId(), query.projectIds()));
        validateScope(query, tasks);
        List<String> facts = tasks.stream().map(CutoverGovernanceGuardProvider::canonicalFact).sorted().toList();
        List<ProjectGovernanceBlocker> blockers = tasks.stream()
                .filter(task -> !CutTaskStatusRules.isTerminal(task.getStatus()))
                .sorted(Comparator.comparing(CutTaskDO::getProjectId).thenComparing(CutTaskDO::getId))
                .map(CutoverGovernanceGuardProvider::toBlocker).toList();
        return fact(facts, blockers);
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static void validateScope(ProjectGovernanceGuardQuery query, List<CutTaskDO> tasks) {
        if (tasks.stream().anyMatch(task -> !Objects.equals(task.getTenantId(), query.tenantId())
                || !query.projectIds().contains(task.getProjectId()))) {
            throw new IllegalStateException("cutover guard query returned out-of-scope fact");
        }
    }

    private static ProjectGovernanceProviderFact fact(List<String> facts,
                                                       List<ProjectGovernanceBlocker> blockers) {
        String watermark = facts.isEmpty() ? "EMPTY" : digest(facts.stream()
                .map(CutoverGovernanceGuardProvider::watermarkPart).toList());
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, FACT_VERSION, watermark, digest(facts), blockers);
    }

    private static String canonicalFact(CutTaskDO task) {
        return value(task.getProjectId()) + "|" + value(task.getId()) + "|" + value(task.getStatus())
                + "|" + value(task.getVersion()) + "|" + value(task.getUpdateTime());
    }

    private static String watermarkPart(String fact) {
        String[] fields = fact.split("\\|", -1);
        return fields[0] + "|" + fields[1] + "|" + fields[3] + "|" + fields[4];
    }

    private static ProjectGovernanceBlocker toBlocker(CutTaskDO task) {
        return new ProjectGovernanceBlocker("CUTOVER_TASK", String.valueOf(task.getId()),
                statusName(task.getStatus()), "NON_TERMINAL_CUTOVER_TASK", "割接任务阻断");
    }

    private static String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case 0 -> "DRAFT";
            case 1 -> "PREPARING";
            case 2 -> "PENDING_REVIEW";
            case 3 -> "CLOSURE_IN_PROGRESS";
            case 6 -> "COMPLETED";
            case 7 -> "ROLLED_BACK";
            case 8 -> "TERMINATED";
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
