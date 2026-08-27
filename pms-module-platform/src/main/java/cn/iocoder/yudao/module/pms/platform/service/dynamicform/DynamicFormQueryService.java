package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstancePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplatePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateRowQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_INSTANCE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_TEMPLATE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class DynamicFormQueryService {

    static final String OWNER_CONTEXT = "PLATFORM";
    static final String OBJECT_TYPE = "DYNAMIC_FORM_INSTANCE";

    private final DynamicFormTemplateMapper templateMapper;
    private final DynamicFormTemplateRevisionMapper revisionMapper;
    private final PlatformDynamicFormInstanceMapper instanceMapper;
    private final DynamicFormSchemaService schemaService;
    private final DynamicFormActionProjection actionProjection;
    private final FileArtifactApi fileArtifactApi;

    public DynamicFormViews.Page<DynamicFormViews.Template> pageTemplates(
            DynamicFormCommands.Actor actor, int pageNo, int pageSize) {
        requireActor(actor);
        actionProjection.require(actor.userId(), DynamicFormActionProjection.TEMPLATE_QUERY);
        DynamicFormTemplatePageQuery query = new DynamicFormTemplatePageQuery(actor.tenantId(), null,
                null, null, false, offset(pageNo, pageSize), pageSize);
        long total = templateMapper.selectCountPage(query);
        if (total == 0) return new DynamicFormViews.Page<>(List.of(), 0);
        return new DynamicFormViews.Page<>(templateMapper.selectPage(query).stream()
                .map(row -> toTemplate(actor.userId(), row)).toList(), total);
    }

    public DynamicFormViews.Template getTemplate(DynamicFormCommands.Actor actor, Long templateId) {
        requireActor(actor);
        actionProjection.require(actor.userId(), DynamicFormActionProjection.TEMPLATE_QUERY);
        return toTemplate(actor.userId(), requireTemplate(actor.tenantId(), templateId));
    }

    public DynamicFormViews.Revision getRevision(DynamicFormCommands.Actor actor, Long revisionId) {
        requireActor(actor);
        actionProjection.require(actor.userId(), DynamicFormActionProjection.TEMPLATE_QUERY);
        DynamicFormTemplateRevisionDO revision = requireRevision(actor.tenantId(), revisionId);
        return toRevision(actor.userId(), revision);
    }

    public DynamicFormViews.Page<DynamicFormViews.Selection> pageSelection(
            DynamicFormCommands.Actor actor, int pageNo, int pageSize) {
        requireActor(actor);
        actionProjection.require(actor.userId(), DynamicFormActionProjection.INSTANCE_QUERY);
        DynamicFormTemplatePageQuery query = new DynamicFormTemplatePageQuery(actor.tenantId(), null,
                null, "ENABLED", true, offset(pageNo, pageSize), pageSize);
        long total = templateMapper.selectCountPage(query);
        if (total == 0) return new DynamicFormViews.Page<>(List.of(), 0);
        return new DynamicFormViews.Page<>(templateMapper.selectPage(query).stream()
                .map(row -> toSelection(actor.userId(), row)).toList(), total);
    }

    public DynamicFormViews.Page<DynamicFormViews.InstanceSummary> pageInstances(
            DynamicFormCommands.Actor actor, int pageNo, int pageSize) {
        requireActor(actor);
        actionProjection.require(actor.userId(), DynamicFormActionProjection.INSTANCE_QUERY);
        DynamicFormInstancePageQuery query = new DynamicFormInstancePageQuery(actor.tenantId(), null,
                null, offset(pageNo, pageSize), pageSize);
        long total = instanceMapper.selectCountPage(query);
        if (total == 0) return new DynamicFormViews.Page<>(List.of(), 0);
        return new DynamicFormViews.Page<>(instanceMapper.selectPage(query).stream()
                .map(row -> toInstanceSummary(actor, row)).toList(), total);
    }

    public DynamicFormViews.Instance getInstance(DynamicFormCommands.Actor actor, Long instanceId) {
        requireActor(actor);
        actionProjection.require(actor.userId(), DynamicFormActionProjection.INSTANCE_QUERY);
        PlatformDynamicFormInstanceDO instance = requireInstance(actor.tenantId(), instanceId);
        return toInstance(actor, instance);
    }

    DynamicFormViews.Instance toInstance(DynamicFormCommands.Actor actor, PlatformDynamicFormInstanceDO instance) {
        DynamicFormTemplateDO template = requireTemplate(actor.tenantId(), instance.getTemplateId());
        DynamicFormTemplateRevisionDO revision = requireRevision(actor.tenantId(), instance.getTemplateRevisionId());
        DynamicFormSchemaService.SchemaFields schema = schemaService.parseAndValidate(
                revision.getFormConfJson(), revision.getFormRulesJson(), revision.getEngineCode(),
                revision.getDesignerVersion(), revision.getRendererVersion());
        Map<String, List<DynamicFormViews.FileFact>> fileFacts = inspectFiles(actor, instance, schema.fileFieldKeys());
        return new DynamicFormViews.Instance(instance.getId(), instance.getInstanceCode(), instance.getInstanceName(),
                instance.getTemplateId(), template.getTemplateCode(), template.getTemplateName(),
                instance.getTemplateRevisionId(), instance.getTemplateRevisionNo(), instance.getEngineCode(),
                instance.getDesignerVersion(), instance.getRendererVersion(),
                JsonUtils.parseTree(revision.getFormConfJson()), JsonUtils.parseTree(revision.getFormRulesJson()),
                JsonUtils.parseTree(instance.getValueJson()), fileFacts, instance.getVersion(), instance.getCreatedBy(),
                actionProjection.instanceActions(actor.userId(), instance.getCreatedBy()),
                instance.getCreateTime(), instance.getUpdateTime());
    }

    DynamicFormViews.Template toTemplate(Long actorId, DynamicFormTemplateDO template) {
        DynamicFormViews.RevisionSummary draft = draftSummary(template);
        DynamicFormViews.RevisionSummary published = publishedSummary(template);
        return new DynamicFormViews.Template(template.getId(), template.getTemplateCode(), template.getTemplateName(),
                template.getCategoryCode(), template.getDescription(), template.getAvailabilityCode(),
                template.getVersion(), template.getCurrentPublishedRevisionId(), draft, published,
                actionProjection.templateActions(actorId, template.getAvailabilityCode(), draft != null,
                        published != null), template.getCreateTime(), template.getUpdateTime());
    }

    DynamicFormViews.Revision toRevision(Long actorId, DynamicFormTemplateRevisionDO revision) {
        return new DynamicFormViews.Revision(revision.getId(), revision.getTemplateId(), revision.getRevisionNo(),
                revision.getStatusCode(), revision.getSourceRevisionId(), JsonUtils.parseTree(revision.getFormConfJson()),
                JsonUtils.parseTree(revision.getFormRulesJson()), revision.getEngineCode(),
                revision.getDesignerVersion(), revision.getRendererVersion(), revision.getVersion(),
                revision.getPublishedBy(), revision.getPublishedAt(),
                actionProjection.revisionActions(actorId, revision.getStatusCode()));
    }

    private DynamicFormViews.Selection toSelection(Long actorId, DynamicFormTemplateDO template) {
        return new DynamicFormViews.Selection(template.getId(), template.getTemplateCode(), template.getTemplateName(),
                template.getCategoryCode(), template.getDescription(), template.getCurrentPublishedRevisionId(),
                template.getCurrentPublishedRevisionNo(), template.getCurrentPublishedEngineCode(),
                template.getCurrentPublishedDesignerVersion(), template.getCurrentPublishedRendererVersion(),
                template.getVersion(), actionProjection.selectionActions(actorId,
                template.getAvailabilityCode(), true));
    }

    private DynamicFormViews.InstanceSummary toInstanceSummary(DynamicFormCommands.Actor actor,
                                                                PlatformDynamicFormInstanceDO instance) {
        return new DynamicFormViews.InstanceSummary(instance.getId(), instance.getInstanceCode(),
                instance.getInstanceName(), instance.getTemplateId(), instance.getTemplateCode(),
                instance.getTemplateName(), instance.getTemplateRevisionId(), instance.getTemplateRevisionNo(),
                instance.getVersion(), instance.getCreatedBy(),
                actionProjection.instanceActions(actor.userId(), instance.getCreatedBy()),
                instance.getCreateTime(), instance.getUpdateTime());
    }

    private Map<String, List<DynamicFormViews.FileFact>> inspectFiles(
            DynamicFormCommands.Actor actor, PlatformDynamicFormInstanceDO instance, List<String> fields) {
        if (fields.isEmpty()) return Map.of();
        List<FileReferenceSetKey> keys = fields.stream().map(field -> new FileReferenceSetKey(
                OWNER_CONTEXT, OBJECT_TYPE, String.valueOf(instance.getId()),
                DynamicFormSchemaService.FILE_PURPOSE_PREFIX + field)).toList();
        List<FileReferenceSetFact> result;
        try {
            result = fileArtifactApi.inspectReferenceSets(
                    new FileReferenceSetCollectionQuery(keys, FileActionCodes.READ));
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        if (result == null || result.size() != keys.size()) throw exception(FILE_PROVIDER_UNAVAILABLE);
        Map<String, List<DynamicFormViews.FileFact>> byField = new LinkedHashMap<>();
        for (FileReferenceSetFact set : result) {
            if (set == null || !keys.contains(set.key())
                    || !Long.valueOf(instance.getTemplateRevisionId()).equals(set.scopeVersion())) {
                throw exception(FILE_PROVIDER_UNAVAILABLE);
            }
            String purpose = set.key().purposeCode();
            String field = purpose.startsWith(DynamicFormSchemaService.FILE_PURPOSE_PREFIX)
                    ? purpose.substring(DynamicFormSchemaService.FILE_PURPOSE_PREFIX.length()) : null;
            if (field == null || !fields.contains(field) || byField.containsKey(field)) {
                throw exception(FILE_PROVIDER_UNAVAILABLE);
            }
            byField.put(field, set.activeFacts().stream().map(fact -> new DynamicFormViews.FileFact(
                    fact.artifactId(), fact.versionNo(), fact.referenceKey(), fact.fileFactVersion(),
                    fact.scopeVersion(), fact.referenceStatus())).toList());
        }
        if (byField.size() != fields.size()) throw exception(FILE_PROVIDER_UNAVAILABLE);
        return Map.copyOf(byField);
    }

    private DynamicFormViews.RevisionSummary draftSummary(DynamicFormTemplateDO template) {
        if (template.getCurrentDraftRevisionId() == null) return null;
        return new DynamicFormViews.RevisionSummary(template.getCurrentDraftRevisionId(),
                template.getCurrentDraftRevisionNo(), "DRAFT", template.getCurrentDraftVersion(),
                template.getCurrentDraftSourceRevisionId(), template.getCurrentDraftEngineCode(),
                template.getCurrentDraftDesignerVersion(), template.getCurrentDraftRendererVersion(), null, null);
    }

    private DynamicFormViews.RevisionSummary publishedSummary(DynamicFormTemplateDO template) {
        if (template.getCurrentPublishedRevisionId() == null) return null;
        return new DynamicFormViews.RevisionSummary(template.getCurrentPublishedRevisionId(),
                template.getCurrentPublishedRevisionNo(), "PUBLISHED", template.getCurrentPublishedRevisionVersion(),
                template.getCurrentPublishedSourceRevisionId(), template.getCurrentPublishedEngineCode(),
                template.getCurrentPublishedDesignerVersion(), template.getCurrentPublishedRendererVersion(),
                template.getCurrentPublishedBy(), template.getCurrentPublishedAt());
    }

    private DynamicFormTemplateDO requireTemplate(Long tenantId, Long id) {
        if (id == null || id <= 0) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        DynamicFormTemplateDO row = templateMapper.selectByRow(new DynamicFormTemplateRowQuery(tenantId, id));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        return row;
    }

    private DynamicFormTemplateRevisionDO requireRevision(Long tenantId, Long id) {
        if (id == null || id <= 0) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        DynamicFormTemplateRevisionDO row = revisionMapper.selectByRow(new DynamicFormRevisionRowQuery(tenantId, id));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        return row;
    }

    private PlatformDynamicFormInstanceDO requireInstance(Long tenantId, Long id) {
        if (id == null || id <= 0) throw exception(DYNAMIC_FORM_INSTANCE_NOT_FOUND);
        PlatformDynamicFormInstanceDO row = instanceMapper.selectByRow(new DynamicFormInstanceRowQuery(tenantId, id));
        if (row == null) throw exception(DYNAMIC_FORM_INSTANCE_NOT_FOUND);
        return row;
    }

    private long offset(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("分页参数无效");
        return (long) (pageNo - 1) * pageSize;
    }

    private void requireActor(DynamicFormCommands.Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.userId() == null || actor.userId() <= 0) {
            throw new IllegalArgumentException("动态表单主体无效");
        }
    }
}
