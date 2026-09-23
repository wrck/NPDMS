package cn.iocoder.yudao.module.pms.project.api.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@Service @RequiredArgsConstructor
public class ProjectFieldRuleApiImpl implements ProjectFieldRuleApi {
    private final ProjectMasterMapper projects;
    // Template publication validation discovers SOL providers; resolve its reader only during an operation.
    private final org.springframework.beans.factory.ObjectProvider<ProjectTemplateService> templates;
    private final ProjectScopeApi scopes;
    private final ProjectRuleFields fields;
    private final ProjectRuleEvaluationService evaluator;

    @Override public Evaluation evaluate(Query query) { return evaluate(query, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Evaluation lockAndEvaluate(Query query) { return evaluate(query, true); }

    private Evaluation evaluate(Query query, boolean lock) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (query == null || !Objects.equals(tenant, query.tenantId()) || query.actorId() == null
                || !Objects.equals(query.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || query.projectId() == null || query.ruleKeys() == null || query.ruleKeys().isEmpty()
                || query.ruleKeys().stream().anyMatch(key -> key == null || key.isBlank())
                || new HashSet<>(query.ruleKeys()).size() != query.ruleKeys().size()) throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, query.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(FORBIDDEN);
        if (lock) {
            var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, query.actorId(), query.projectId(),
                    ProjectScopeApi.ACTION_VIEW, scope.treeVersion()));
            if (locked == null || !Objects.equals(locked.treeVersion(), scope.treeVersion())
                    || !locked.fullProjectIds().contains(query.projectId())) throw exception(FORBIDDEN);
        }
        var project = lock ? projects.selectByIdForUpdate(query.projectId()) : projects.selectById(query.projectId());
        if (project == null || !Objects.equals(tenant, project.getTenantId()) || !Objects.equals(query.projectId(), project.getId())) throw exception(FORBIDDEN);
        // Imported projects without a template have no new review policy; keep their original SOL behavior.
        if (project.getLifecycleTemplateId() == null && project.getLifecycleTemplateRevisionId() == null
                && project.getLifecycleTemplateRevisionNo() == null) return new Evaluation(false, Map.of(), null);
        var reader = templates.getObject();
        var revision = reader.getRevisionById(project.getLifecycleTemplateRevisionId());
        if (revision == null || !Objects.equals(tenant, revision.getTenantId()) || !"PUBLISHED".equals(revision.getStatus())
                || !Objects.equals(revision.getId(), project.getLifecycleTemplateRevisionId())
                || !Objects.equals(revision.getTemplateId(), project.getLifecycleTemplateId())
                || !Objects.equals(revision.getRevisionNo(), project.getLifecycleTemplateRevisionNo()))
            throw new IllegalArgumentException("项目冻结模板修订不可用");
        var snapshot = reader.getExecutionSnapshot(revision.getTemplateId(), revision.getRevisionNo());
        if (snapshot == null || snapshot.getRulePrograms() == null) throw new IllegalArgumentException("项目冻结规则不可用");
        if (query.ruleKeys().stream().noneMatch(snapshot.getRulePrograms()::containsKey))
            return new Evaluation(false, Map.of(), null);
        Map<String, RuleProgram> programs = new LinkedHashMap<>();
        Map<String, RuleFact> facts = new LinkedHashMap<>();
        Map<String, String> outcomes = new LinkedHashMap<>();
        for (String key : query.ruleKeys()) {
            var program = snapshot.getRulePrograms().get(key);
            if (program == null || program.kind() != VersionRule.Kind.CONDITION
                    || program.leaves().stream().anyMatch(leaf -> !Set.of("FIELD", "CONSTANT").contains(leaf.predicate())))
                throw new IllegalArgumentException("项目审核适用规则缺失或包含非字段条件");
            programs.put(key, program);
            var result = evaluator.evaluate("template:" + revision.getId() + ":" + key, program, leaf -> {
                String code = leaf.parameters().path("fieldCode").asText();
                return facts.computeIfAbsent(code, value -> fields.read(project, value));
            });
            outcomes.put(key, result.outcome().name());
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tenantId", tenant); evidence.put("projectId", project.getId()); evidence.put("projectVersion", project.getVersion());
        evidence.put("templateId", revision.getTemplateId()); evidence.put("templateRevisionId", revision.getId());
        evidence.put("templateRevisionNo", revision.getRevisionNo()); evidence.put("programs", programs);
        evidence.put("facts", facts); evidence.put("outcomes", outcomes);
        return new Evaluation(true, Map.copyOf(outcomes), JsonUtils.toJsonString(evidence));
    }
}
