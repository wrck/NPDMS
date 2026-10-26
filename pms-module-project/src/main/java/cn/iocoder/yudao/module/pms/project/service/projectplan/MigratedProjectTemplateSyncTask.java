package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectTemplateInitializationMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A base-sync consumer of the existing project-owned matching and initialization commands. */
@Component
@RequiredArgsConstructor
public class MigratedProjectTemplateSyncTask
        extends DefaultSyncTask<MigratedProjectTemplateSyncTask.Row, MigratedProjectTemplateSyncTask.Parameters> {
    private final ProjectScopeApi scopes;
    private final ProjectPlanDraftService access;
    private final ProjectTemplateInitializationMapper initializationRows;
    private final ProjectTemplateService templates;
    private final ProjectRuleFields fields;
    private final ProjectTemplateInitializationService initialization;

    @Override public String taskCode() { return "BASE_P38"; }
    @Override public String taskName() { return "迁移项目模板分配"; }
    @Override public Set<String> requiredPermissions() { return Set.of(ProjectPlanDraftService.MANAGE_PERMISSION); }
    @Override protected Parameters createParameters(SyncContext<?> context) { return new Parameters(); }

    @Override public void work(SyncContext<Parameters> context) {
        var authorized = Objects.requireNonNull(scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                context.tenantId(), context.actorId(), ProjectScopeApi.ACTION_EDIT)), "项目权限范围不可用");
        if (authorized.isEmpty()) {
            context.runState().warning("PROJECT_TEMPLATE_NO_AUTHORIZED_PROJECTS");
            return;
        }
        // The scope provider owns scope enumeration. Only one bounded subset enters each SQL statement.
        var projectIds = authorized.stream().sorted().toList();
        try {
            for (int start = 0; start < projectIds.size(); start += context.batchSize()) {
                context.parameters().projectIds = projectIds.subList(start,
                        Math.min(start + context.batchSize(), projectIds.size()));
                syncData(context.forData("MigratedProjects", false));
                if (context.runState().counts().read() > context.maxRows())
                    throw new IllegalArgumentException("源数据超过本次读取上限");
            }
            context.parameters().skipped.forEach((reason, count) ->
                    context.runState().warning("PROJECT_TEMPLATE_" + reason + ":" + count));
        } finally {
            context.parameters().projectIds = List.of();
        }
    }

    @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> context, ResultHandler<Row> handler) {
        syncDataQueryBySql(context.target(), new MappedCall<>(context,
                targetMapperNamespace() + ".selectMigratedProjects",
                new SourceQuery<>(context.maxRows() + 1, context.tenantId(), context.preview(), context.parameters())), handler);
    }

    @Override protected void syncDataInsertBefore(SyncBatch<Row, Parameters> batch) {
        for (var row : batch.rows()) {
            if (!Objects.equals(row.getTenantId(), batch.context().tenantId())
                    || !"MIGRATION".equals(row.getSourceType()) || Boolean.TRUE.equals(row.getDeleted())
                    || !batch.context().parameters().projectIds.contains(row.getId()))
                throw new IllegalArgumentException("迁移项目读取范围不一致");
            row.action = SyncWriteAction.UNCHANGED;
            if (row.getLifecycleTemplateId() != null || row.getLifecycleTemplateRevisionId() != null
                    || row.getLifecycleTemplateRevisionNo() != null || row.getActivePlanVersionId() != null) {
                skip(batch, Reason.ALREADY_INITIALIZED);
                continue;
            }
            if (!"ACTIVE".equals(row.getLifecycleStatus())) {
                skip(batch, Reason.NOT_ACTIVE);
                continue;
            }
            var scope = access.authorize(row.getId(), batch.context().actorId());
            if (initializationRows.hasRuntime(scope)) {
                skip(batch, Reason.EXISTING_RUNTIME);
                continue;
            }
            var match = templates.matchPreview(fields.creationFacts(row));
            if (match.getOutcome() != TemplateMatchResult.Outcome.MATCHED) {
                skip(batch, match.getOutcome() == TemplateMatchResult.Outcome.MULTI_MATCH
                        ? Reason.MULTI_MATCH : Reason.NO_MATCH);
                continue;
            }
            row.matchedRevisionId = Objects.requireNonNull(match.getMatched().getTemplateRevisionId());
            row.action = SyncWriteAction.UPDATE;
        }
    }

    @Override protected SyncWriteAction syncDataAction(Row row) { return row.action; }

    @Override protected void syncDataInsert(SyncBatch<Row, Parameters> batch) {
        for (var row : batch.rows()) {
            if (row.action != SyncWriteAction.UPDATE) continue;
            initialization.initialize(new ProjectTemplateInitializationService.Initialize(
                            row.getId(), row.getVersion(), row.matchedRevisionId), batch.context().actorId(),
                    taskCode() + ":" + row.getId() + ":" + row.getVersion() + ":" + row.matchedRevisionId);
        }
    }

    private void skip(SyncBatch<Row, Parameters> batch, Reason reason) {
        batch.context().parameters().skipped.merge(reason, 1L, Long::sum);
    }

    enum Reason { ALREADY_INITIALIZED, NOT_ACTIVE, EXISTING_RUNTIME, NO_MATCH, MULTI_MATCH }

    public static final class Parameters {
        @Getter private List<Long> projectIds = List.of();
        private final EnumMap<Reason, Long> skipped = new EnumMap<>(Reason.class);
    }

    @Getter @Setter
    public static final class Row extends ProjectMasterDO {
        private SyncWriteAction action = SyncWriteAction.UNCHANGED;
        private Long matchedRevisionId;
    }

    @Mapper
    public interface TargetMapper {
        List<Row> selectMigratedProjects(SourceQuery<Parameters> query);
    }
}
