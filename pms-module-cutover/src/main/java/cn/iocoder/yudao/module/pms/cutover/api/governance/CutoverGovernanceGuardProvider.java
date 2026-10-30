package cn.iocoder.yudao.module.pms.cutover.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverGovernanceGuardQuery;
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
    private static final String FACT_VERSION = "CUTOVER_TASK_CURRENT";

    private final CutoverTaskMapper taskMapper;

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
        List<CutoverTaskDO> tasks = taskMapper.selectListForGovernanceGuard(
                new CutoverGovernanceGuardQuery(query.tenantId(), query.projectIds()));
        validateScope(query, tasks);
        List<String> facts = tasks.stream().map(CutoverGovernanceGuardProvider::canonicalFact).sorted().toList();
        List<ProjectGovernanceBlocker> blockers = tasks.stream()
                .filter(task -> !"ARCHIVED".equals(task.getTaskStatus()))
                .sorted(Comparator.comparing(CutoverTaskDO::getProjectId).thenComparing(CutoverTaskDO::getId))
                .map(CutoverGovernanceGuardProvider::toBlocker).toList();
        return fact(facts, blockers);
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static void validateScope(ProjectGovernanceGuardQuery query, List<CutoverTaskDO> tasks) {
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

    private static String canonicalFact(CutoverTaskDO task) {
        return value(task.getProjectId()) + "|" + value(task.getId()) + "|" + value(task.getTaskStatus())
                + "|" + value(task.getVersion()) + "|" + value(task.getUpdateTime());
    }

    private static String watermarkPart(String fact) {
        String[] fields = fact.split("\\|", -1);
        return fields[0] + "|" + fields[1] + "|" + fields[3] + "|" + fields[4];
    }

    private static ProjectGovernanceBlocker toBlocker(CutoverTaskDO task) {
        return new ProjectGovernanceBlocker("CUTOVER_TASK", String.valueOf(task.getId()),
                task.getTaskStatus() == null ? "UNKNOWN" : task.getTaskStatus(),
                "NON_TERMINAL_CUTOVER_TASK", "割接任务阻断");
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
