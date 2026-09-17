package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCopyReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateIdentityQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatcher;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationErrors;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Validation;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NO_DRAFT_REVISION;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_PUBLISH_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_STATUS_INVALID;

/** Primary PM-03 implementation after the V2 runtime rewrite. */
@Service
@Primary
public class ProjectTemplateV2ServiceImpl extends ProjectTemplateServiceImpl {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectTemplate(ProjectTemplateDO template) {
        Long id = super.createProjectTemplate(template);
        ProjectTemplateRevisionDO draft = requireWritableDraft(id);
        ProjectTemplateRevisionDO initial = new ProjectTemplateRevisionDO();
        initial.setId(draft.getId());
        initial.setDesignerSchemaVersion(TemplateDesignerDocument.SCHEMA_VERSION);
        initial.setDesignerDocument(JsonUtils.toJsonString(new TemplateDesignerDocument()));
        requireOne(v2RevisionMapper.updateById(initial));
        return id;
    }

    @Resource
    private ProjectTemplateMapper v2TemplateMapper;
    @Resource
    private ProjectTemplateRevisionMapper v2RevisionMapper;
    @Resource
    private TemplateDefinitionReferenceAssembler v2LegacyAssembler;
    @Resource
    private TemplateCompiler templateCompiler;
    @Resource
    private TemplateDesignerDependencyValidator dependencyValidator;
    @Resource
    private cn.iocoder.yudao.module.pms.project.service.rule.ProjectRulePublicationValidator rulePublicationValidator;
    @Resource
    private DeliveryConfigurationCommands v2ConfigurationCommands;
    @Resource
    private ProjectTemplateMatchRuleEvaluator matchRuleEvaluator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectTemplateDesigner(Long templateId, TemplateDesignerDocument submitted) {
        ProjectTemplateDO template = lockV2Template(templateId);
        ProjectTemplateRevisionDO draft = requireWritableDraft(templateId);
        if (!TemplateRules.canEditDraft(template.getStatus(), draft.getStatus())) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        if (submitted == null) throw new IllegalArgumentException("designer不能为空");
        if (submitted.getSchemaVersion() == null) submitted.setSchemaVersion(TemplateDesignerDocument.SCHEMA_VERSION);
        if (!Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(submitted.getSchemaVersion())) {
            throw new IllegalArgumentException("仅支持模板设计schema v2");
        }

        TemplateDesignerDocument designer = cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.forEditing(submitted);

        ProjectTemplateRevisionDO update = new ProjectTemplateRevisionDO();
        update.setId(draft.getId());
        update.setDesignerSchemaVersion(designer.getSchemaVersion());
        update.setDesignerDocument(JsonUtils.toJsonString(designer));
        if (designer.getMatch() != null) {
            update.setSigningMethod(designer.getMatch().getSigningMethod());
            update.setProjectCategory(designer.getMatch().getProjectCategory());
            update.setImplementationMethod(designer.getMatch().getImplementationMethod());
            update.setMajorProjectLevel(designer.getMatch().getMajorProjectLevel());
        }
        update.setProcessDefinitionKey(designer.getProcessDefinitionKey());
        update.setProcessDefinitionVersion("");
        update.setClosurePolicy(designer.getClosurePolicy() == null ? null : JsonUtils.toJsonString(designer.getClosurePolicy()));
        update.setValidationSummary(null);
        requireOne(v2RevisionMapper.updateById(update));
        incrementV2Version(templateId);
    }

    @Override
    public TemplateDesignerDocument getDraftDesigner(Long templateId) {
        ProjectTemplateRevisionDO draft = requireDraft(templateId);
        if (hasText(draft.getDesignerDocument())) {
            return JsonUtils.parseObject(draft.getDesignerDocument(), TemplateDesignerDocument.class);
        }
        TemplateDefinitionContent legacy = super.getDraftContent(templateId);
        v2LegacyAssembler.resolve(legacy, false);
        return TemplateDesignerDocument.fromResolvedLegacy(legacy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectTemplateDraftContent(Long templateId, TemplateDefinitionContent content) {
        Objects.requireNonNull(content, "template content");
        v2LegacyAssembler.resolve(content, false);
        updateProjectTemplateDesigner(templateId, TemplateDesignerDocument.fromResolvedLegacy(content));
    }

    @Override
    public TemplateDefinitionContent getDraftContent(Long templateId) {
        ProjectTemplateRevisionDO draft = requireDraft(templateId);
        if (!hasText(draft.getDesignerDocument())) return super.getDraftContent(templateId);
        return TemplateDesignerLegacyAdapter.toLegacy(
                JsonUtils.parseObject(draft.getDesignerDocument(), TemplateDesignerDocument.class));
    }

    @Override
    public TemplateDefinitionContent getRevisionContent(Long templateId, Integer revisionNo) {
        ProjectTemplateRevisionDO revision = v2RevisionMapper.selectByTemplateIdAndRevisionNo(templateId, revisionNo);
        if (revision == null) throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        if (hasText(revision.getExecutionSnapshot())) {
            return verifiedExecutionSnapshot(revision, templateId, revisionNo).toRuntimeContent();
        }
        if (TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus()) && hasV2PublicationMetadata(revision)) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID,
                    "V2发布版本缺少完整ExecutionSnapshot，禁止退回Designer/Legacy解释路径");
        }
        if (hasText(revision.getDesignerDocument())) {
            return TemplateDesignerLegacyAdapter.toLegacy(
                    JsonUtils.parseObject(revision.getDesignerDocument(), TemplateDesignerDocument.class));
        }
        return super.getRevisionContent(templateId, revisionNo);
    }

    @Override
    public TemplateExecutionSnapshot getExecutionSnapshot(Long templateId, Integer revisionNo) {
        ProjectTemplateRevisionDO revision = v2RevisionMapper.selectByTemplateIdAndRevisionNo(templateId, revisionNo);
        if (revision == null || !TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus())) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        return verifiedExecutionSnapshot(revision, templateId, revisionNo);
    }

    @Override
    public TemplateMatchResult matchPreview(TemplateMatchFacts facts) {
        List<ProjectTemplateDO> activeTemplates =
                v2TemplateMapper.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE);
        List<TemplateMatchCandidate> candidates = new ArrayList<>();
        List<TemplateMatchResult.Evaluation> evaluations = new ArrayList<>();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        for (ProjectTemplateDO activeTemplate : activeTemplates) {
            List<ProjectTemplateRevisionDO> published =
                    v2RevisionMapper.selectPublishedListByTemplateId(activeTemplate.getId());
            if (published.isEmpty()) continue;
            ProjectTemplateRevisionDO latest = published.getFirst();
            if (!isV2RuntimeEligible(latest) && !TemplateVersionPublication.applies(latest)) continue;
            TemplateExecutionSnapshot snapshot;
            try {
                snapshot = verifiedExecutionSnapshot(latest, activeTemplate.getId(), latest.getRevisionNo());
            } catch (RuntimeException ex) {
                continue;
            }
            TemplateMatchCandidate candidate = new TemplateMatchCandidate();
            candidate.setTemplateId(activeTemplate.getId());
            candidate.setCode(activeTemplate.getCode());
            candidate.setName(activeTemplate.getName());
            candidate.setMatchPriority(activeTemplate.getMatchPriority());
            candidate.setLatestRevisionNo(latest.getRevisionNo());
            candidate.setTemplateRevisionId(latest.getId());
            var evaluation = matchRuleEvaluator.evaluate(tenantId, latest.getId(), snapshot, facts.values());
            String ruleName = matchRuleName(snapshot);
            candidate.setRuleName(ruleName);
            evaluations.add(new TemplateMatchResult.Evaluation(activeTemplate.getId(), latest.getId(), ruleName, evaluation));
            if (evaluation.matched()) candidates.add(candidate);
        }
        TemplateMatchResult result = TemplateMatcher.selectByPriority(candidates);
        result.setEvaluations(List.copyOf(evaluations));
        result.setCandidateWatermark(candidateWatermark(candidates, facts));
        return result;
    }

    private String matchRuleName(TemplateExecutionSnapshot snapshot) {
        String key = snapshot.getMatchRuleKey();
        if (key == null || key.isBlank()) return "不限";
        if (snapshot.getRules() == null) return key;
        return snapshot.getRules().stream().filter(rule -> Objects.equals(rule.key(), key))
                .map(rule -> rule.name() == null ? key : rule.name()).findFirst().orElse(key);
    }

    @Override
    public Validation validateProjectTemplate(Long id) {
        TemplateDesignerDocument designer;
        try {
            designer = getDraftDesigner(id);
        } catch (RuntimeException ex) {
            return Validation.of(List.of(new Issue("designer", "IMPORT_INVALID", safeMessage(ex))));
        }
        List<Issue> issues = new ArrayList<>(templateCompiler.compileVersioned(designer).issues());
        issues.addAll(dependencyValidator.validate(designer, false));
        issues.addAll(rulePublicationValidator.validate(designer));
        return Validation.of(dedupe(issues));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishProjectTemplate(Long id) {
        ProjectTemplateDO template = lockV2Template(id);
        ProjectTemplateRevisionDO draft = requireWritableDraft(id);
        if (!TemplateRules.canPublish(template.getStatus(), true)) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }

        TemplateDesignerDocument designer = getDraftDesigner(id);
        TemplateCompiler.Compilation compilation = templateCompiler.compileVersioned(designer);
        List<Issue> issues = new ArrayList<>(compilation.issues());
        issues.addAll(dependencyValidator.validate(designer, true));
        issues.addAll(rulePublicationValidator.validate(designer));
        issues = dedupe(issues);
        if (!issues.isEmpty()) {
            rememberValidation(draft, summarize(issues));
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, summarize(issues));
        }

        TemplateExecutionSnapshot snapshot = compilation.snapshot();
        int nextRevisionNo = nextV2RevisionNo(id);
        ProjectTemplateRevisionDO published = new ProjectTemplateRevisionDO();
        published.setTenantId(template.getTenantId());
        published.setTemplateId(id);
        published.setRevisionNo(nextRevisionNo);
        published.setStatus(TemplateRules.REVISION_STATUS_PUBLISHED);
        if (designer.getMatch() != null) {
            published.setSigningMethod(designer.getMatch().getSigningMethod());
            published.setProjectCategory(designer.getMatch().getProjectCategory());
            published.setImplementationMethod(designer.getMatch().getImplementationMethod());
            published.setMajorProjectLevel(designer.getMatch().getMajorProjectLevel());
        }
        published.setProcessDefinitionKey(designer.getProcessDefinitionKey());
        published.setProcessDefinitionVersion(null);
        published.setClosurePolicy(designer.getClosurePolicy() == null ? null : JsonUtils.toJsonString(designer.getClosurePolicy()));
        published.setDefinitionSnapshot(designer.getSourceEvidence() == null ? null : JsonUtils.toJsonString(designer.getSourceEvidence()));
        published.setDesignerSchemaVersion(TemplateDesignerDocument.SCHEMA_VERSION);
        published.setDesignerDocument(JsonUtils.toJsonString(designer));
        published.setExecutionSchemaVersion(snapshot.getExecutionSchemaVersion());
        published.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        published.setCompilerVersion(snapshot.getCompilerVersion());
        published.setSnapshotHash(compilation.snapshotHash());
        published.setValidationSummary("完整版本冻结发布校验通过");
        published.setPublishedBy(String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        published.setPublishedTime(LocalDateTime.now());
        requireOne(v2RevisionMapper.insert(published));

        ProjectTemplateDO status = new ProjectTemplateDO();
        status.setId(id);
        status.setStatus(TemplateRules.STATUS_ACTIVE);
        requireOne(v2TemplateMapper.updateById(status));
        incrementV2Version(id);
    }

    @Override
    public Long copyProjectTemplate(Long id, Integer expectedVersion, ProjectTemplateCopyReqVO body, String idempotencyKey) {
        return v2ConfigurationCommands.execute("PROJECT_TEMPLATE_COPY_V2", idempotencyKey,
                new CopyIntent(id, expectedVersion, body), Long.class, () -> {
                    ProjectTemplateDO source = lockV2Template(id);
                    if (!Objects.equals(source.getVersion(), expectedVersion))
                        throw exception(DeliveryConfigurationErrors.VERSION_CONFLICT);
                    int revisionNo = body.getSourceRevisionNo() == null ? 0 : body.getSourceRevisionNo();
                    TemplateDesignerDocument designer = revisionNo == 0
                            ? getDraftDesigner(id) : designerForRevision(id, revisionNo);
                    ProjectTemplateDO copy = new ProjectTemplateDO();
                    copy.setCode(body.getCode());
                    copy.setName(body.getName());
                    copy.setMatchPriority(source.getMatchPriority());
                    copy.setDescription(source.getDescription());
                    Long copyId = super.createProjectTemplate(copy);
                    updateProjectTemplateDesigner(copyId, designer);
                    return copyId;
                });
    }

    private TemplateDesignerDocument designerForRevision(Long templateId, int revisionNo) {
        ProjectTemplateRevisionDO revision = v2RevisionMapper.selectByTemplateIdAndRevisionNo(templateId, revisionNo);
        if (revision == null || !TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus())) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        if (TemplateVersionPublication.applies(revision)) {
            return readVersionedPublication(revision, templateId, revisionNo).designer();
        }
        // Copying a published V2 revision must not bypass the runtime integrity boundary.
        if (hasText(revision.getExecutionSnapshot()) || hasV2PublicationMetadata(revision)) {
            verifiedExecutionSnapshot(revision);
            if (!hasText(revision.getDesignerDocument())) {
                throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID,
                        "V2发布版本缺少冻结Designer，禁止退回Legacy复制路径");
            }
            TemplateDesignerDocument designer;
            try {
                JsonNode document = JsonUtils.parseObject(revision.getDesignerDocument(), JsonNode.class);
                JsonNode schema = document == null || !document.isObject() ? null : document.get("schemaVersion");
                if (!Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(revision.getDesignerSchemaVersion())
                        || schema == null || !schema.isIntegralNumber()
                        || !Integer.toString(TemplateDesignerDocument.SCHEMA_VERSION).equals(schema.asText())) {
                    throw new IllegalArgumentException("PUBLISHED_DESIGNER_SCHEMA_UNSUPPORTED");
                }
                designer = JsonUtils.parseObject(revision.getDesignerDocument(), TemplateDesignerDocument.class);
            } catch (RuntimeException ex) {
                throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2发布版本冻结Designer无法解析或schema不受支持");
            }
            if (designer == null || !Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION)
                    .equals(designer.getSchemaVersion())) {
                throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2发布版本冻结Designer的schema不受支持");
            }
            return designer;
        }
        TemplateDefinitionContent legacy = super.getRevisionContent(templateId, revisionNo);
        return TemplateDesignerDocument.fromResolvedLegacy(legacy);
    }

    private TemplateExecutionSnapshot verifiedExecutionSnapshot(ProjectTemplateRevisionDO revision,
                                                                  Long templateId, Integer revisionNo) {
        return TemplateVersionPublication.applies(revision)
                ? readVersionedPublication(revision, templateId, revisionNo).snapshot()
                : verifiedExecutionSnapshot(revision);
    }

    private TemplateVersionPublication.Frozen readVersionedPublication(ProjectTemplateRevisionDO revision,
                                                                        Long templateId, Integer revisionNo) {
        try {
            return TemplateVersionPublication.read(revision, TenantContextHolder.getRequiredTenantId(), templateId, revisionNo);
        } catch (RuntimeException invalid) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "版本冻结内容无效：" + safeMessage(invalid));
        }
    }

    private TemplateExecutionSnapshot verifiedExecutionSnapshot(ProjectTemplateRevisionDO revision) {
        if (!isV2RuntimeEligible(revision)) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID,
                    "历史发布版本缺少完整V2执行快照，禁止由当前编译器即时重解释；请显式复制为V2草稿并重新发布");
        }
        TemplateExecutionSnapshot snapshot;
        try {
            snapshot = TemplateExecutionSnapshotReader.read(revision.getExecutionSnapshot());
        } catch (RuntimeException ex) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2执行快照无法解析");
        }
        if (snapshot == null
                || !Objects.equals(revision.getExecutionSchemaVersion(), snapshot.getExecutionSchemaVersion())) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2执行快照schema与发布记录不一致");
        }
        if (!Objects.equals(revision.getCompilerVersion(), snapshot.getCompilerVersion())) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2执行快照compiler与发布记录不一致");
        }
        final String actualHash;
        try {
            actualHash = TemplateExecutionSnapshotHasher.hash(snapshot);
        } catch (RuntimeException ex) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2执行快照语义结构无效");
        }
        if (!Objects.equals(revision.getSnapshotHash(), actualHash)) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, "V2执行快照hash与发布记录不一致");
        }
        return snapshot;
    }

    private boolean isV2RuntimeEligible(ProjectTemplateRevisionDO revision) {
        return revision != null
                && TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus())
                && Integer.valueOf(TemplateExecutionSnapshot.SCHEMA_VERSION).equals(revision.getExecutionSchemaVersion())
                && hasText(revision.getExecutionSnapshot())
                && hasText(revision.getCompilerVersion())
                && hasText(revision.getSnapshotHash());
    }

    private boolean hasV2PublicationMetadata(ProjectTemplateRevisionDO revision) {
        return revision.getDesignerSchemaVersion() != null
                || hasText(revision.getDesignerDocument())
                || revision.getExecutionSchemaVersion() != null
                || hasText(revision.getCompilerVersion())
                || hasText(revision.getSnapshotHash());
    }

    private String candidateWatermark(List<TemplateMatchCandidate> candidates, TemplateMatchFacts facts) {
        StringBuilder canonical = new StringBuilder();
        facts.values().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            appendToken(canonical, entry.getKey());
            appendToken(canonical, entry.getValue().available());
            appendToken(canonical, JsonUtils.toJsonString(entry.getValue().value()));
            appendToken(canonical, entry.getValue().reasonCode());
        });
        candidates.stream().sorted(Comparator.comparing(TemplateMatchCandidate::getTemplateId)).forEach(candidate -> {
            appendToken(canonical, candidate.getTemplateId());
            appendToken(canonical, candidate.getTemplateRevisionId());
            appendToken(canonical, candidate.getLatestRevisionNo());
            appendToken(canonical, candidate.getMatchPriority());
            appendToken(canonical, candidate.getSigningMethod());
            appendToken(canonical, candidate.getProjectCategory());
            appendToken(canonical, candidate.getImplementationMethod());
            appendToken(canonical, candidate.getMajorProjectLevel());
        });
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private void appendToken(StringBuilder target, Object value) {
        String token = value == null ? "" : String.valueOf(value);
        target.append(token.length()).append(':').append(token).append(';');
    }

    private ProjectTemplateDO lockV2Template(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectTemplateDO row = v2TemplateMapper.lockTemplate(new TemplateIdentityQuery(tenantId, id));
        if (row == null || !Objects.equals(row.getTenantId(), tenantId)) throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        return row;
    }

    private ProjectTemplateRevisionDO requireDraft(Long templateId) {
        ProjectTemplateRevisionDO draft = v2RevisionMapper.selectDraftByTemplateId(templateId);
        if (draft == null) throw exception(PROJECT_TEMPLATE_NO_DRAFT_REVISION);
        return draft;
    }

    private ProjectTemplateRevisionDO requireWritableDraft(Long templateId) {
        ProjectTemplateRevisionDO draft = requireDraft(templateId);
        if (draft.getId() == null || draft.getId() <= 0
                || !Objects.equals(templateId, draft.getTemplateId())
                || !Objects.equals(TenantContextHolder.getRequiredTenantId(), draft.getTenantId())
                || !Integer.valueOf(TemplateRules.DRAFT_REVISION_NO).equals(draft.getRevisionNo())
                || !TemplateRules.REVISION_STATUS_DRAFT.equals(draft.getStatus())) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        return draft;
    }

    private void requireOne(int affected) {
        if (affected != 1) throw exception(DeliveryConfigurationErrors.VERSION_CONFLICT);
    }

    private int nextV2RevisionNo(Long templateId) {
        return v2RevisionMapper.selectPublishedListByTemplateId(templateId).stream()
                .map(ProjectTemplateRevisionDO::getRevisionNo).filter(no -> no != null && no > 0)
                .max(Integer::compareTo).map(Math::incrementExact).orElse(1);
    }

    private void incrementV2Version(Long templateId) {
        if (v2TemplateMapper.incrementVersion(new TemplateIdentityQuery(
                TenantContextHolder.getRequiredTenantId(), templateId)) != 1)
            throw exception(DeliveryConfigurationErrors.VERSION_CONFLICT);
    }

    private void rememberValidation(ProjectTemplateRevisionDO draft, String summary) {
        ProjectTemplateRevisionDO update = new ProjectTemplateRevisionDO();
        update.setId(draft.getId());
        update.setValidationSummary(summary.length() > 1000 ? summary.substring(0, 1000) : summary);
        update.setClosurePolicy(draft.getClosurePolicy());
        requireOne(v2RevisionMapper.updateById(update));
    }

    private List<Issue> dedupe(List<Issue> issues) {
        return issues.stream().filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        issue -> issue.field() + "\u0000" + issue.code() + "\u0000" + issue.message(),
                        issue -> issue, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream().toList();
    }

    private String summarize(List<Issue> issues) {
        return issues.stream().map(issue -> issue.field() + "[" + issue.code() + "]: " + issue.message())
                .limit(30).collect(java.util.stream.Collectors.joining("；"));
    }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private record CopyIntent(Long id, Integer expectedVersion, ProjectTemplateCopyReqVO body) { }
}
