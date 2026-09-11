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
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationErrors;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Validation;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NO_DRAFT_REVISION;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_PUBLISH_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_STATUS_INVALID;

/**
 * Primary PM-03 implementation after the V2 runtime rewrite.
 *
 * <p>The legacy service remains available as a reader/validator for historical rows. All normal
 * injections of {@link ProjectTemplateService} resolve to this class and therefore use the
 * Designer -> Compiler -> ExecutionSnapshot chain.</p>
 */
@Service
@Primary
public class ProjectTemplateV2ServiceImpl extends ProjectTemplateServiceImpl {

    @Resource
    private ProjectTemplateMapper v2TemplateMapper;
    @Resource
    private ProjectTemplateRevisionMapper v2RevisionMapper;
    @Resource
    private TemplateDefinitionReferenceAssembler v2LegacyAssembler;
    @Resource
    private TemplateCompiler templateCompiler;
    @Resource
    private DeliveryConfigurationCommands v2ConfigurationCommands;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectTemplateDesigner(Long templateId, TemplateDesignerDocument designer) {
        ProjectTemplateDO template = lockV2Template(templateId);
        ProjectTemplateRevisionDO draft = requireDraft(templateId);
        if (!TemplateRules.canEditDraft(template.getStatus(), draft.getStatus())) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }
        if (designer == null) throw new IllegalArgumentException("designer不能为空");
        if (designer.getSchemaVersion() == null) designer.setSchemaVersion(TemplateDesignerDocument.SCHEMA_VERSION);
        if (!Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(designer.getSchemaVersion())) {
            throw new IllegalArgumentException("仅支持模板设计schema v2");
        }

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
        v2RevisionMapper.updateById(update);
        incrementV2Version(templateId);
    }

    @Override
    public TemplateDesignerDocument getDraftDesigner(Long templateId) {
        ProjectTemplateRevisionDO draft = requireDraft(templateId);
        if (hasText(draft.getDesignerDocument())) {
            return JsonUtils.parseObject(draft.getDesignerDocument(), TemplateDesignerDocument.class);
        }
        TemplateDefinitionContent legacy = super.getDraftContent(templateId);
        // Legacy drafts did not persist their closure. Resolve the exact configuration only in-memory.
        v2LegacyAssembler.resolve(legacy, false);
        return TemplateDesignerDocument.fromResolvedLegacy(legacy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectTemplateDraftContent(Long templateId, TemplateDefinitionContent content) {
        Objects.requireNonNull(content, "template content");
        // Compatibility callers still post exact DefinitionRevision ids. Resolve once at the
        // authoring boundary, then persist only the V2 Designer as the new truth.
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
            return JsonUtils.parseObject(revision.getExecutionSnapshot(), TemplateExecutionSnapshot.class).toRuntimeContent();
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
        if (hasText(revision.getExecutionSnapshot())) {
            return JsonUtils.parseObject(revision.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        }

        // Legacy reader: historical rows stay untouched. The adapter uses the already-frozen
        // publication closure and never writes the V2 artifact back into history.
        TemplateDefinitionContent legacy = super.getRevisionContent(templateId, revisionNo);
        TemplateDesignerDocument designer = TemplateDesignerDocument.fromResolvedLegacy(legacy);
        TemplateCompiler.Compilation compilation = templateCompiler.compile(designer);
        if (!compilation.valid()) {
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, summarize(compilation.issues()));
        }
        return compilation.snapshot();
    }

    @Override
    public Validation validateProjectTemplate(Long id) {
        TemplateDesignerDocument designer;
        try {
            designer = getDraftDesigner(id);
        } catch (RuntimeException ex) {
            return Validation.of(List.of(new Issue("designer", "IMPORT_INVALID", safeMessage(ex))));
        }
        List<Issue> issues = new ArrayList<>(templateCompiler.compile(designer).issues());

        // Imported exact-definition drafts continue to receive the mature Owner/DynamicForm/Gate
        // read-only validation. Pure V2 documents are validated by the compiler and their explicit
        // frozen binding facts instead of re-resolving DefinitionRevision at runtime.
        if (designer.getSourceEvidence() != null) {
            try {
                issues.addAll(super.validateProjectTemplate(id).issues());
            } catch (RuntimeException ex) {
                issues.add(new Issue("legacyValidation", "OWNER_VALIDATION_FAILED", safeMessage(ex)));
            }
        }
        return Validation.of(dedupe(issues));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishProjectTemplate(Long id) {
        ProjectTemplateDO template = lockV2Template(id);
        ProjectTemplateRevisionDO draft = requireDraft(id);
        if (!TemplateRules.canPublish(template.getStatus(), true)) {
            throw exception(PROJECT_TEMPLATE_STATUS_INVALID);
        }

        TemplateDesignerDocument designer = getDraftDesigner(id);
        TemplateCompiler.Compilation compilation = templateCompiler.compile(designer);
        List<Issue> issues = new ArrayList<>(compilation.issues());
        if (designer.getSourceEvidence() != null) {
            try {
                issues.addAll(super.validateProjectTemplate(id).issues());
            } catch (RuntimeException ex) {
                issues.add(new Issue("legacyValidation", "OWNER_VALIDATION_FAILED", safeMessage(ex)));
            }
        }
        issues = dedupe(issues);
        if (!issues.isEmpty()) {
            rememberValidation(draft, summarize(issues));
            throw exception(PROJECT_TEMPLATE_PUBLISH_INVALID, summarize(issues));
        }

        TemplateExecutionSnapshot snapshot = compilation.snapshot();
        int nextRevisionNo = nextV2RevisionNo(id);
        ProjectTemplateRevisionDO published = new ProjectTemplateRevisionDO();
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
        published.setExecutionSchemaVersion(TemplateExecutionSnapshot.SCHEMA_VERSION);
        published.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        published.setCompilerVersion(TemplateCompiler.COMPILER_VERSION);
        published.setSnapshotHash(compilation.snapshotHash());
        published.setValidationSummary("V2编译发布校验通过");
        published.setPublishedBy(String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        published.setPublishedTime(LocalDateTime.now());
        v2RevisionMapper.insert(published);

        ProjectTemplateDO status = new ProjectTemplateDO();
        status.setId(id);
        status.setStatus(TemplateRules.STATUS_ACTIVE);
        v2TemplateMapper.updateById(status);
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
        if (revision == null) throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        if (hasText(revision.getDesignerDocument()))
            return JsonUtils.parseObject(revision.getDesignerDocument(), TemplateDesignerDocument.class);
        TemplateDefinitionContent legacy = super.getRevisionContent(templateId, revisionNo);
        return TemplateDesignerDocument.fromResolvedLegacy(legacy);
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

    private int nextV2RevisionNo(Long templateId) {
        return v2RevisionMapper.selectPublishedListByTemplateId(templateId).stream()
                .map(ProjectTemplateRevisionDO::getRevisionNo).filter(no -> no != null && no > 0)
                .max(Integer::compareTo).map(no -> no + 1).orElse(1);
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
        v2RevisionMapper.updateById(update);
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
