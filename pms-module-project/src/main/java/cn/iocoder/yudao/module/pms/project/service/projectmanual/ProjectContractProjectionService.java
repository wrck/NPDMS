package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection.ProjectContractProjectionRow;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContractProjectionPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContractProjectionUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Rebuilds project contract text from current direct relations, in bounded pages. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectContractProjectionService {
    private static final int PAGE_SIZE = 500;
    private final ProjectMasterMapper projects;
    private final ProjectContractQueryApi contracts;

    public void refreshProject(Long projectId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new IllegalStateException("项目不存在或不可见");
        }
        var row = new ProjectContractProjectionRow();
        row.setId(project.getId());
        row.setVersion(project.getVersion());
        row.setContractNo(project.getContractNo());
        var result = reconcilePage(tenantId, LocalDateTime.now(), List.of(row));
        if (result.failed() != 0) throw new IllegalStateException("项目合同号同步失败: " + projectId);
    }

    public int reconcile() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime effectiveAt = LocalDateTime.now();
        long afterId = 0;
        int updated = 0;
        int failed = 0;
        while (true) {
            List<ProjectContractProjectionRow> page = projects.selectContractProjectionPage(
                    new ProjectContractProjectionPageQuery(tenantId, afterId, PAGE_SIZE));
            if (page.isEmpty()) {
                if (failed > 0) throw new IllegalStateException("项目合同号同步失败 " + failed + " 条；已更新 " + updated + " 条");
                return updated;
            }
            var result = reconcilePage(tenantId, effectiveAt, page);
            updated += result.updated();
            failed += result.failed();
            afterId = page.getLast().getId();
        }
    }

    Result reconcilePage(Long tenantId, LocalDateTime effectiveAt, List<ProjectContractProjectionRow> page) {
        var ids = page.stream().map(ProjectContractProjectionRow::getId).collect(Collectors.toSet());
        var facts = contracts.getProjectContractFacts(new ProjectContractQueryApi.ProjectContractFactsQuery(ids, effectiveAt));
        Map<Long, List<ProjectContractQueryApi.ProjectContractFact>> byProject = facts.stream()
                .collect(Collectors.groupingBy(ProjectContractQueryApi.ProjectContractFact::projectId));
        Map<Long, String> desired = new HashMap<>();
        int failed = 0;
        for (var row : page) {
            var projectFacts = byProject.get(row.getId());
            if (projectFacts == null) continue; // No relationship has ever been recorded; preserve manual input.
            var numbers = new TreeSet<String>();
            boolean invalid = false;
            for (var fact : projectFacts) {
                if (!fact.current()) continue;
                if (fact.contractNo() == null || fact.contractNo().isBlank()) {
                    invalid = true;
                    break;
                }
                numbers.add(fact.contractNo());
            }
            if (invalid) {
                log.warn("项目合同号同步跳过：有效关系缺少合同号，项目 {}", row.getId());
                failed++;
                continue;
            }
            desired.put(row.getId(), numbers.isEmpty() ? null : String.join(",", numbers));
        }
        int count = 0;
        for (var row : page) {
            if (!desired.containsKey(row.getId()) || Objects.equals(row.getContractNo(), desired.get(row.getId()))) continue;
            int changed = projects.updateContractProjectionIfMatch(new ProjectContractProjectionUpdate(
                    tenantId, row.getId(), row.getVersion(), row.getContractNo(), desired.get(row.getId())));
            if (changed != 1) {
                log.warn("项目合同号同步跳过：并发变更，项目 {}", row.getId());
                failed++;
                continue;
            }
            count++;
        }
        return new Result(count, failed);
    }

    record Result(int updated, int failed) { }
}
