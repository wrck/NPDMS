package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultViewRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class SatisfactionResultExportBusinessDataProvider implements ExportBusinessDataProvider {
    private static final List<String> FIELDS = List.of("resultId", "projectId", "taskRevisionNo", "score",
            "threshold", "passed", "ruleVersion", "resultStatus", "archiveStatus", "effectiveFrom");
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final SatisfactionResultMapper resultMapper;

    @Override public String ownerContext() { return "ACC"; }
    @Override public String exportType() { return "SATISFACTION_RESULT"; }
    @Override public ExportBusinessDataSnapshot inspect(ExportBusinessDataQuery query) { return snapshot(query, false); }
    @Override public ExportBusinessDataSnapshot generate(ExportBusinessDataQuery query) { return snapshot(query, true); }

    private ExportBusinessDataSnapshot snapshot(ExportBusinessDataQuery query, boolean includeRows) {
        if (query == null || query.tenantId() == null || query.actorUserId() == null
                || !permissionApi.hasAnyPermissions(query.actorUserId(), "pms:acceptance:satisfaction:export")) {
            return unavailable();
        }
        Filter filter;
        try { filter = JsonUtils.parseObject(query.normalizedFilter(), Filter.class); }
        catch (RuntimeException invalid) { return unavailable(); }
        if (filter == null || filter.projectId() == null || filter.projectId() <= 0) return unavailable();
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.actorUserId(),
                filter.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(filter.projectId())
                || scope.treeVersion() == null || (query.expectedScopeVersion() != null
                && !query.expectedScopeVersion().equals(scope.treeVersion()))) return unavailable();
        List<String> requested = query.requestedFields().stream().distinct().toList();
        if (requested.isEmpty() || requested.stream().anyMatch(field -> !FIELDS.contains(field))) return unavailable();
        List<List<String>> rows = includeRows ? resultMapper.selectByScope(new SatisfactionResultScopeQuery(
                        query.tenantId(), Set.of(filter.projectId()), null)).stream()
                .map(row -> requested.stream().map(field -> value(row, field)).toList()).toList() : List.of();
        String normalized = JsonUtils.toJsonString(new Filter(filter.projectId()));
        return new ExportBusinessDataSnapshot("AVAILABLE", normalized,
                JsonUtils.toJsonString(Map.of("projectId", filter.projectId(), "treeVersion", scope.treeVersion())),
                requested, query.includeFiles(), scope.treeVersion(), rows);
    }

    private String value(SatisfactionResultViewRecord row, String field) {
        Object value = switch (field) {
            case "resultId" -> row.resultId(); case "projectId" -> row.projectId();
            case "taskRevisionNo" -> row.taskRevisionNo(); case "score" -> row.score();
            case "threshold" -> row.threshold(); case "passed" -> row.passed();
            case "ruleVersion" -> row.ruleVersion(); case "resultStatus" -> row.resultStatus();
            case "archiveStatus" -> row.archiveStatus(); case "effectiveFrom" -> row.effectiveFrom();
            default -> null;
        };
        return Objects.toString(value, "");
    }

    private ExportBusinessDataSnapshot unavailable() {
        return new ExportBusinessDataSnapshot("UNAVAILABLE", "{}", "{}", List.of(), false, null, List.of());
    }

    public record Filter(Long projectId) {}
}
