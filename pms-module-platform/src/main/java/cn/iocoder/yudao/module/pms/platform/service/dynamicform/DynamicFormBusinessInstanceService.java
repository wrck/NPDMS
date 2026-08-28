package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class DynamicFormBusinessInstanceService {

    private static final String FILE_OWNER_CONTEXT = "PLATFORM";
    private static final String FILE_OBJECT_TYPE = "DYNAMIC_FORM_INSTANCE";

    private final DynamicFormBusinessObjectPolicyProviderRegistry policyRegistry;
    private final DynamicFormTemplateMapper templateMapper;
    private final DynamicFormTemplateRevisionMapper revisionMapper;
    private final PlatformDynamicFormInstanceMapper instanceMapper;
    private final DynamicFormSchemaService schemaService;
    private final FileArtifactApi fileArtifactApi;

    public DynamicFormRevisionFact inspectRevisionForUsage(DynamicFormRevisionUsageQuery query) {
        requireRevisionAction(query == null ? null : query.action());
        requireActor(query.tenantId(), query.actorUserId());
        DynamicFormTemplateRevisionDO revision = requireRevision(query.tenantId(), query.templateRevisionId());
        DynamicFormTemplateDO template = requireTemplate(query.tenantId(), revision.getTemplateId());
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        requireRevisionState(query, template, revision);
        DynamicFormPolicyFact policy = policyRegistry.inspectRevision(new DynamicFormRevisionPolicyQuery(
                query.tenantId(), query.actorUserId(), query.providerKey(), revision.getTemplateId(), revision.getId(),
                revision.getRevisionNo(), revision.getVersion(), requireText(query.requiredUsage()), query.action(),
                schema.descriptors()));
        return revisionFact(query, revision, schema, policy);
    }

    public DynamicFormRevisionFact lockAndRevalidateRevisionForUsage(DynamicFormRevisionRevalidationQuery query) {
        DynamicFormRevisionFact expected = query == null ? null : query.expectedFact();
        requireRevisionAction(expected == null ? null : expected.action());
        requireActor(expected.tenantId(), query.actorUserId());
        DynamicFormRevisionPolicyQuery policyQuery = new DynamicFormRevisionPolicyQuery(
                expected.tenantId(), query.actorUserId(), expected.providerKey(), expected.templateId(),
                expected.templateRevisionId(), expected.revisionNo(), expected.revisionFactVersion(),
                expected.requiredUsage(), expected.action(), expected.fields());
        DynamicFormPolicyFact policy = policyRegistry.revalidateRevision(policyQuery, expected.policyFact());
        DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(
                expected.tenantId(), expected.templateId(), expected.templateRevisionId()));
        if (revision == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        DynamicFormTemplateDO template = requireTemplate(expected.tenantId(), expected.templateId());
        DynamicFormRevisionUsageQuery inspection = new DynamicFormRevisionUsageQuery(expected.tenantId(),
                query.actorUserId(), expected.providerKey(), expected.templateRevisionId(), expected.requiredUsage(),
                expected.action(), expected.revisionFactVersion());
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        requireRevisionState(inspection, template, revision);
        DynamicFormRevisionFact actual = revisionFact(inspection, revision, schema, policy);
        if (!expected.equals(actual)) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        return actual;
    }

    public DynamicFormInstanceFact createBusinessInstance(DynamicFormInstanceCreateCommand command) {
        if (command == null || command.action() != DynamicFormBusinessAction.CREATE) {
            throw new IllegalArgumentException("CREATE action is required");
        }
        requireActor(command.tenantId(), command.actorUserId());
        requireProviderOwner(command.providerKey(), command.ownerKey());
        requireId(command.preallocatedInstanceId());
        DynamicFormPolicyFact inspected = policyRegistry.inspectInstance(new DynamicFormInstancePolicyQuery(
                command.tenantId(), command.actorUserId(), command.providerKey(), command.ownerKey(),
                command.preallocatedInstanceId(), DynamicFormBusinessAction.CREATE));
        DynamicFormPolicyFact policy = policyRegistry.lockAndRevalidate(new DynamicFormPolicyRevalidationQuery(
                command.tenantId(), command.actorUserId(), command.providerKey(), command.ownerKey(),
                command.preallocatedInstanceId(), inspected));
        DynamicFormTemplateRevisionDO inspectedRevision = requireRevision(command.tenantId(),
                command.templateRevisionId());
        DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(
                command.tenantId(), inspectedRevision.getTemplateId(), inspectedRevision.getId()));
        requireFrozenRevision(revision, command.expectedRevisionFactVersion());
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        Map<String, Object> values = normalizedValues(command.initialValues());
        requireOrdinaryFields(values.keySet(), schema);
        PlatformDynamicFormInstanceDO existing = instanceMapper.selectByOwner(new DynamicFormInstanceOwnerQuery(
                command.tenantId(), command.ownerKey().ownerContext(), command.ownerKey().objectType(),
                command.ownerKey().objectId()));
        if (existing != null) {
            requireCreateReplay(existing, command, revision);
            return toFact(existing, revision, schema, DynamicFormBusinessAction.CREATE, policy,
                    emptyFiles(existing, schema, policy.scopeVersion()));
        }
        PlatformDynamicFormInstanceDO row = newInstance(command, revision, values);
        try {
            if (instanceMapper.insert(row) != 1) throw new IllegalStateException("DYNAMIC_FORM_INSTANCE_INSERT_FAILED");
        } catch (DuplicateKeyException conflict) {
            throw exception(DYNAMIC_FORM_OWNER_CONFLICT);
        }
        return toFact(row, revision, schema, DynamicFormBusinessAction.CREATE, policy,
                emptyFiles(row, schema, policy.scopeVersion()));
    }

    public DynamicFormInstanceFact inspectInstance(DynamicFormInstanceQuery query) {
        requireInspectAction(query == null ? null : query.action());
        requireActor(query.tenantId(), query.actorUserId());
        requireProviderOwner(query.providerKey(), query.ownerKey());
        DynamicFormPolicyFact policy = policyRegistry.inspectInstance(new DynamicFormInstancePolicyQuery(
                query.tenantId(), query.actorUserId(), query.providerKey(), query.ownerKey(), query.instanceId(),
                query.action()));
        PlatformDynamicFormInstanceDO row = requireInstance(query.tenantId(), query.instanceId(), query.ownerKey());
        DynamicFormTemplateRevisionDO revision = requireRevision(query.tenantId(), row.getTemplateRevisionId());
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        return toFact(row, revision, schema, query.action(), policy, inspectFiles(row, schema));
    }

    public DynamicFormInstanceFact patchInstanceValues(DynamicFormInstancePatchCommand command) {
        if (command == null || command.action() != DynamicFormBusinessAction.PATCH) {
            throw new IllegalArgumentException("PATCH action is required");
        }
        requireActor(command.tenantId(), command.actorUserId());
        requireProviderOwner(command.providerKey(), command.ownerKey());
        DynamicFormPolicyFact inspected = policyRegistry.inspectInstance(new DynamicFormInstancePolicyQuery(
                command.tenantId(), command.actorUserId(), command.providerKey(), command.ownerKey(),
                command.instanceId(), DynamicFormBusinessAction.PATCH));
        DynamicFormPolicyFact policy = policyRegistry.lockAndRevalidate(new DynamicFormPolicyRevalidationQuery(
                command.tenantId(), command.actorUserId(), command.providerKey(), command.ownerKey(),
                command.instanceId(), inspected));
        PlatformDynamicFormInstanceDO inspectedRow = requireInstance(command.tenantId(), command.instanceId(),
                command.ownerKey());
        if (!schema(requireRevision(command.tenantId(), inspectedRow.getTemplateRevisionId())).fileFieldKeys().isEmpty()) {
            prevalidateFilePolicy(command.tenantId(), command.actorUserId(), command.providerKey(), command.ownerKey(),
                    command.instanceId(), DynamicFormBusinessAction.FILE_READ, policy.scopeVersion());
        }
        PlatformDynamicFormInstanceDO row = lockInstance(command.tenantId(), command.instanceId(), command.ownerKey());
        if (!Objects.equals(row.getVersion(), command.expectedInstanceVersion())) {
            throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        }
        DynamicFormTemplateRevisionDO revision = requireRevision(command.tenantId(), row.getTemplateRevisionId());
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        requireOrdinaryFields(command.partialValues().keySet(), schema);
        Map<String, Object> merged = values(row);
        merged.putAll(command.partialValues());
        String json = valuesJson(merged);
        if (instanceMapper.updateValueIfMatch(new DynamicFormInstanceValueUpdate(command.tenantId(), row.getId(),
                command.expectedInstanceVersion(), json, String.valueOf(command.actorUserId()))) != 1) {
            throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        }
        row.setValueJson(json);
        row.setVersion(row.getVersion() + 1);
        return toFact(row, revision, schema, DynamicFormBusinessAction.PATCH, policy, inspectFiles(row, schema));
    }

    public DynamicFormInstanceFact lockAndRevalidateInstance(DynamicFormInstanceRevalidationQuery query) {
        DynamicFormInstanceFact expected = query == null ? null : query.expectedFact();
        requireInspectAction(expected == null ? null : expected.action());
        requireActor(expected.tenantId(), query.actorUserId());
        DynamicFormPolicyFact policy = policyRegistry.lockAndRevalidate(new DynamicFormPolicyRevalidationQuery(
                expected.tenantId(), query.actorUserId(), expected.providerKey(), expected.ownerKey(),
                expected.instanceId(), expected.policyFact()));
        if (!expected.controlledFileFacts().isEmpty()) {
            prevalidateFilePolicy(expected.tenantId(), query.actorUserId(), expected.providerKey(), expected.ownerKey(),
                    expected.instanceId(), DynamicFormBusinessAction.FILE_READ,
                    expectedFileScope(expected.controlledFileFacts()));
        }
        PlatformDynamicFormInstanceDO row = lockInstance(expected.tenantId(), expected.instanceId(), expected.ownerKey());
        DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(
                expected.tenantId(), row.getTemplateId(), row.getTemplateRevisionId()));
        if (revision == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        List<FileReferenceSetFact> files = lockFiles(expected.controlledFileFacts());
        DynamicFormInstanceFact actual = toFact(row, revision, schema, expected.action(), policy, files);
        if (!expected.equals(actual)) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        return actual;
    }

    public DynamicFormInstanceFact cloneBusinessInstance(DynamicFormInstanceCloneCommand command) {
        if (command == null || command.sourceFact() == null
                || command.sourceFact().action() != DynamicFormBusinessAction.CLONE_SOURCE
                || command.targetPolicyFact() == null
                || command.targetPolicyFact().action() != DynamicFormBusinessAction.CLONE_TARGET) {
            throw new IllegalArgumentException("CLONE_SOURCE and CLONE_TARGET facts are required");
        }
        requireActor(command.tenantId(), command.actorUserId());
        requireProviderOwner(command.providerKey(), command.targetOwnerKey());
        DynamicFormInstanceFact source = command.sourceFact();
        if (!Objects.equals(command.tenantId(), source.tenantId())
                || !Objects.equals(command.providerKey(), source.providerKey())) {
            throw new IllegalArgumentException("source fact does not belong to the clone command");
        }
        DynamicFormPolicyFact sourcePolicy = policyRegistry.lockAndRevalidate(new DynamicFormPolicyRevalidationQuery(
                command.tenantId(), command.actorUserId(), command.providerKey(), source.ownerKey(),
                source.instanceId(), source.policyFact()));
        DynamicFormPolicyFact targetPolicy = policyRegistry.lockAndRevalidate(new DynamicFormPolicyRevalidationQuery(
                command.tenantId(), command.actorUserId(), command.providerKey(), command.targetOwnerKey(),
                command.preallocatedTargetInstanceId(), command.targetPolicyFact()));
        if (!source.controlledFileFacts().isEmpty()) {
            prevalidateFilePolicy(command.tenantId(), command.actorUserId(), command.providerKey(), source.ownerKey(),
                    source.instanceId(), DynamicFormBusinessAction.FILE_READ,
                    expectedFileScope(source.controlledFileFacts()));
            prevalidateFilePolicy(command.tenantId(), command.actorUserId(), command.providerKey(),
                    command.targetOwnerKey(), command.preallocatedTargetInstanceId(),
                    DynamicFormBusinessAction.FILE_WRITE, targetPolicy.scopeVersion());
            prevalidateFilePolicy(command.tenantId(), command.actorUserId(), command.providerKey(),
                    command.targetOwnerKey(), command.preallocatedTargetInstanceId(),
                    DynamicFormBusinessAction.FILE_READ, targetPolicy.scopeVersion());
        }
        PlatformDynamicFormInstanceDO sourceRow = lockInstance(command.tenantId(), source.instanceId(), source.ownerKey());
        DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(
                command.tenantId(), sourceRow.getTemplateId(), sourceRow.getTemplateRevisionId()));
        DynamicFormSchemaService.SchemaFields schema = schema(revision);
        List<FileReferenceSetFact> lockedSourceFiles = lockFiles(source.controlledFileFacts());
        DynamicFormInstanceFact currentSource = toFact(sourceRow, revision, schema, DynamicFormBusinessAction.CLONE_SOURCE,
                sourcePolicy, lockedSourceFiles);
        if (!source.equals(currentSource)) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        DynamicFormInstanceCreateCommand create = new DynamicFormInstanceCreateCommand(command.tenantId(),
                command.actorUserId(), command.providerKey(), DynamicFormBusinessAction.CREATE,
                command.preallocatedTargetInstanceId(), command.targetOwnerKey(), revision.getId(),
                revision.getVersion(), values(sourceRow));
        PlatformDynamicFormInstanceDO targetRow = newInstance(create, revision, values(sourceRow));
        PlatformDynamicFormInstanceDO existing = instanceMapper.selectByOwner(new DynamicFormInstanceOwnerQuery(
                command.tenantId(), command.targetOwnerKey().ownerContext(), command.targetOwnerKey().objectType(),
                command.targetOwnerKey().objectId()));
        if (existing == null) {
            try {
                if (instanceMapper.insert(targetRow) != 1) {
                    throw new IllegalStateException("DYNAMIC_FORM_INSTANCE_INSERT_FAILED");
                }
            } catch (DuplicateKeyException conflict) {
                throw exception(DYNAMIC_FORM_OWNER_CONFLICT);
            }
        } else {
            requireCreateReplay(existing, create, revision);
            targetRow = existing;
        }
        List<AttachExistingFileVersionItem> attachments = attachmentItems(source, command, targetPolicy);
        if (!attachments.isEmpty()) {
            fileArtifactApi.attachExistingVersions(new AttachExistingFileVersionsCommand(
                    requireText(command.operationId()), attachments));
        }
        return toFact(targetRow, revision, schema, DynamicFormBusinessAction.CLONE_TARGET, targetPolicy,
                inspectFiles(targetRow, schema));
    }

    private List<AttachExistingFileVersionItem> attachmentItems(DynamicFormInstanceFact source,
                                                                 DynamicFormInstanceCloneCommand command,
                                                                 DynamicFormPolicyFact targetPolicy) {
        List<AttachExistingFileVersionItem> result = new ArrayList<>();
        for (FileReferenceSetFact set : source.controlledFileFacts()) {
            for (FileArtifactVersionFact fact : set.activeFacts()) {
                result.add(new AttachExistingFileVersionItem(new FileArtifactVersionRevalidationQuery(
                        fact.artifactId(), fact.versionNo(), set.key().ownerContext(), set.key().objectType(),
                        set.key().objectId(), set.key().purposeCode(), fact.referenceKey(), FileActionCodes.READ,
                        fact.fileFactVersion(), set.scopeVersion()), new ExistingFileReferenceTarget(
                        FILE_OWNER_CONTEXT, FILE_OBJECT_TYPE, String.valueOf(command.preallocatedTargetInstanceId()),
                        set.key().purposeCode(), fact.referenceKey(), targetPolicy.scopeVersion())));
            }
        }
        return List.copyOf(result);
    }

    private List<FileReferenceSetFact> inspectFiles(PlatformDynamicFormInstanceDO row,
                                                     DynamicFormSchemaService.SchemaFields schema) {
        if (schema.fileFieldKeys().isEmpty()) return List.of();
        return fileArtifactApi.inspectReferenceSets(new FileReferenceSetCollectionQuery(fileKeys(row, schema),
                FileActionCodes.READ));
    }

    private List<FileReferenceSetFact> emptyFiles(PlatformDynamicFormInstanceDO row,
                                                   DynamicFormSchemaService.SchemaFields schema,
                                                   Long scopeVersion) {
        return fileKeys(row, schema).stream().map(key -> new FileReferenceSetFact(key, scopeVersion, List.of()))
                .toList();
    }

    private List<FileReferenceSetFact> lockFiles(List<FileReferenceSetFact> expected) {
        if (expected.isEmpty()) return List.of();
        return fileArtifactApi.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(
                expected.stream().map(set -> new FileReferenceSetExpectation(set.key(), set.scopeVersion(),
                        set.activeFacts())).toList(), FileActionCodes.READ));
    }

    private DynamicFormPolicyFact prevalidateFilePolicy(Long tenantId, Long actorUserId,
                                                         DynamicFormProviderKey providerKey,
                                                         DynamicFormOwnerKey ownerKey, Long instanceId,
                                                         DynamicFormBusinessAction action,
                                                         Long expectedScopeVersion) {
        DynamicFormPolicyFact inspected = policyRegistry.inspectInstance(new DynamicFormInstancePolicyQuery(
                tenantId, actorUserId, providerKey, ownerKey, instanceId, action));
        if (!Objects.equals(expectedScopeVersion, inspected.scopeVersion())) {
            throw exception(DYNAMIC_FORM_SCOPE_VERSION_CONFLICT);
        }
        return policyRegistry.lockAndRevalidate(new DynamicFormPolicyRevalidationQuery(
                tenantId, actorUserId, providerKey, ownerKey, instanceId, inspected));
    }

    private Long expectedFileScope(List<FileReferenceSetFact> facts) {
        Long scopeVersion = facts.getFirst().scopeVersion();
        if (facts.stream().anyMatch(fact -> !Objects.equals(scopeVersion, fact.scopeVersion()))) {
            throw exception(DYNAMIC_FORM_SCOPE_VERSION_CONFLICT);
        }
        return scopeVersion;
    }

    private List<FileReferenceSetKey> fileKeys(PlatformDynamicFormInstanceDO row,
                                               DynamicFormSchemaService.SchemaFields schema) {
        return schema.fileFieldKeys().stream().sorted().map(field -> new FileReferenceSetKey(FILE_OWNER_CONTEXT,
                FILE_OBJECT_TYPE, String.valueOf(row.getId()), DynamicFormSchemaService.FILE_PURPOSE_PREFIX + field))
                .toList();
    }

    private DynamicFormInstanceFact toFact(PlatformDynamicFormInstanceDO row, DynamicFormTemplateRevisionDO revision,
                                           DynamicFormSchemaService.SchemaFields schema,
                                           DynamicFormBusinessAction action, DynamicFormPolicyFact policy,
                                           List<FileReferenceSetFact> files) {
        Map<String, Object> values = values(row);
        return new DynamicFormInstanceFact(row.getTenantId(), rowOwner(row).providerKey(), rowOwner(row), row.getId(),
                row.getTemplateId(), row.getTemplateRevisionId(), row.getTemplateRevisionNo(), revision.getVersion(),
                row.getEngineCode(), row.getDesignerVersion(), row.getRendererVersion(), revision.getFormConfJson(),
                revision.getFormRulesJson(), schema.descriptors(), values, validate(schema, values, files), files,
                row.getVersion(), action, policy);
    }

    private DynamicFormValidationFact validate(DynamicFormSchemaService.SchemaFields schema,
                                               Map<String, Object> values, List<FileReferenceSetFact> files) {
        List<String> blockers = new ArrayList<>();
        for (DynamicFormFieldDescriptor field : schema.descriptors()) {
            if (field.controlledFile()) {
                String purposeCode = DynamicFormSchemaService.FILE_PURPOSE_PREFIX + field.fieldKey();
                boolean unavailable = files.stream()
                        .filter(set -> purposeCode.equals(set.key().purposeCode()))
                        .flatMap(set -> set.activeFacts().stream())
                        .anyMatch(fact -> !"AVAILABLE".equals(fact.availabilityStatus()));
                if (unavailable) blockers.add("CONTROLLED_FILE_INVALID:" + field.fieldKey());
                continue;
            }
            Object value = values.get(field.fieldKey());
            if (field.required() && missingRequiredValue(field, value)) {
                blockers.add("REQUIRED_VALUE_MISSING:" + field.fieldKey());
            }
            if (value != null && !matchesValueType(value, field.valueType())) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
                continue;
            }
            if (value instanceof String text && field.minLength() != null && text.length() < field.minLength()) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
            }
            if (value instanceof String text && field.maxLength() != null && text.length() > field.maxLength()) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
            }
            if (value instanceof String text && field.pattern() != null && !text.matches(field.pattern())) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
            }
            if (value instanceof Number number && field.minLength() != null
                    && decimal(number).compareTo(BigDecimal.valueOf(field.minLength())) < 0) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
            }
            if (value instanceof Number number && field.maxLength() != null
                    && decimal(number).compareTo(BigDecimal.valueOf(field.maxLength())) > 0) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
            }
            if (value != null && !field.allowedValues().isEmpty()
                    && !field.allowedValues().contains(String.valueOf(value))) {
                blockers.add("FORM_VALUE_INVALID:" + field.fieldKey());
            }
        }
        return new DynamicFormValidationFact(blockers.isEmpty() ? "VALID" : "INVALID", blockers);
    }

    private boolean missingRequiredValue(DynamicFormFieldDescriptor field, Object value) {
        if (value == null) return true;
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (!(value instanceof String text)) return false;
        if (!"Editor".equalsIgnoreCase(field.componentType())) return text.isBlank();
        String visible = HtmlUtils.htmlUnescape(text.replaceAll("<[^>]*>", ""));
        return visible.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint) || Character.getType(codePoint) == Character.FORMAT);
    }

    private boolean matchesValueType(Object value, String valueType) {
        return switch (valueType) {
            case "boolean" -> value instanceof Boolean;
            case "number" -> value instanceof Number;
            case "array" -> value instanceof Collection<?>;
            default -> true;
        };
    }

    private BigDecimal decimal(Number value) {
        return new BigDecimal(value.toString());
    }

    private PlatformDynamicFormInstanceDO newInstance(DynamicFormInstanceCreateCommand command,
                                                       DynamicFormTemplateRevisionDO revision,
                                                       Map<String, Object> values) {
        PlatformDynamicFormInstanceDO row = new PlatformDynamicFormInstanceDO();
        row.setId(command.preallocatedInstanceId());
        row.setInstanceCode("BUS-" + command.preallocatedInstanceId());
        row.setInstanceName(command.ownerKey().ownerContext() + "/" + command.ownerKey().objectType()
                + "/" + command.ownerKey().objectId());
        row.setOwnerContext(command.ownerKey().ownerContext());
        row.setObjectType(command.ownerKey().objectType());
        row.setObjectId(command.ownerKey().objectId());
        row.setTemplateId(revision.getTemplateId());
        row.setTemplateRevisionId(revision.getId());
        row.setTemplateRevisionNo(revision.getRevisionNo());
        row.setEngineCode(revision.getEngineCode());
        row.setDesignerVersion(revision.getDesignerVersion());
        row.setRendererVersion(revision.getRendererVersion());
        row.setValueJson(valuesJson(values));
        row.setCreatedBy(command.actorUserId());
        row.setVersion(1);
        row.setCreator(String.valueOf(command.actorUserId()));
        row.setUpdater(String.valueOf(command.actorUserId()));
        row.setTenantId(command.tenantId());
        return row;
    }

    private DynamicFormRevisionFact revisionFact(DynamicFormRevisionUsageQuery query,
                                                  DynamicFormTemplateRevisionDO revision,
                                                  DynamicFormSchemaService.SchemaFields schema,
                                                  DynamicFormPolicyFact policy) {
        return new DynamicFormRevisionFact(query.tenantId(), query.providerKey(), revision.getTemplateId(),
                revision.getId(), revision.getRevisionNo(), revision.getVersion(), query.requiredUsage(),
                query.action(), revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion(),
                revision.getFormConfJson(), revision.getFormRulesJson(), schema.descriptors(), policy);
    }

    private void requireRevisionState(DynamicFormRevisionUsageQuery query, DynamicFormTemplateDO template,
                                      DynamicFormTemplateRevisionDO revision) {
        requireFrozenRevision(revision, query.expectedRevisionFactVersion());
        if (query.action() == DynamicFormBusinessAction.REVISION_BINDING_PUBLISH
                && (!"ENABLED".equals(template.getAvailabilityCode())
                || !Objects.equals(template.getCurrentPublishedRevisionId(), revision.getId()))) {
            throw exception(DYNAMIC_FORM_CURRENT_REVISION_CHANGED);
        }
    }

    private void requireFrozenRevision(DynamicFormTemplateRevisionDO revision, Integer expectedVersion) {
        if (revision == null || !"PUBLISHED".equals(revision.getStatusCode())) {
            throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        }
        if (!Objects.equals(revision.getVersion(), expectedVersion)) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
    }

    private void requireCreateReplay(PlatformDynamicFormInstanceDO existing,
                                     DynamicFormInstanceCreateCommand command,
                                     DynamicFormTemplateRevisionDO revision) {
        if (!Objects.equals(existing.getId(), command.preallocatedInstanceId())
                || !Objects.equals(existing.getTemplateRevisionId(), revision.getId())
                || !Objects.equals(existing.getValueJson(), valuesJson(command.initialValues()))) {
            throw exception(DYNAMIC_FORM_OWNER_CONFLICT);
        }
    }

    private DynamicFormTemplateDO requireTemplate(Long tenantId, Long templateId) {
        DynamicFormTemplateDO row = templateMapper.selectByRow(new DynamicFormTemplateRowQuery(tenantId, templateId));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        return row;
    }

    private DynamicFormTemplateRevisionDO requireRevision(Long tenantId, Long revisionId) {
        DynamicFormTemplateRevisionDO row = revisionMapper.selectByRow(new DynamicFormRevisionRowQuery(tenantId,
                revisionId));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        return row;
    }

    private PlatformDynamicFormInstanceDO requireInstance(Long tenantId, Long instanceId, DynamicFormOwnerKey owner) {
        PlatformDynamicFormInstanceDO row = instanceMapper.selectByRow(new DynamicFormInstanceRowQuery(tenantId,
                instanceId));
        requireOwner(row, owner);
        return row;
    }

    private PlatformDynamicFormInstanceDO lockInstance(Long tenantId, Long instanceId, DynamicFormOwnerKey owner) {
        PlatformDynamicFormInstanceDO row = instanceMapper.selectForUpdate(new DynamicFormInstanceLockQuery(tenantId,
                instanceId));
        requireOwner(row, owner);
        return row;
    }

    private void requireOwner(PlatformDynamicFormInstanceDO row, DynamicFormOwnerKey owner) {
        if (row == null) throw exception(DYNAMIC_FORM_INSTANCE_NOT_FOUND);
        if (!rowOwner(row).equals(owner)) throw exception(DYNAMIC_FORM_SCOPE_FORBIDDEN);
    }

    private DynamicFormOwnerKey rowOwner(PlatformDynamicFormInstanceDO row) {
        return new DynamicFormOwnerKey(row.getOwnerContext(), row.getObjectType(), row.getObjectId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> values(PlatformDynamicFormInstanceDO row) {
        Map<String, Object> value = JsonUtils.parseObject(row.getValueJson(), Map.class);
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private Map<String, Object> normalizedValues(Map<String, Object> input) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        if (input != null) sorted.putAll(input);
        return new LinkedHashMap<>(sorted);
    }

    private String valuesJson(Map<String, Object> values) {
        tools.jackson.databind.node.ObjectNode node = JsonUtils.getObjectMapper().createObjectNode();
        normalizedValues(values).forEach((key, value) -> node.set(key,
                JsonUtils.getObjectMapper().valueToTree(value)));
        return node.toString();
    }

    private void requireOrdinaryFields(Collection<String> keys, DynamicFormSchemaService.SchemaFields schema) {
        for (String key : keys) {
            if (schema.fileFieldKeys().contains(key)) throw exception(DYNAMIC_FORM_FILE_FIELD_REQUIRES_FILE_API);
            if (!schema.ordinaryFieldKeys().contains(key)) throw exception(DYNAMIC_FORM_INSTANCE_FIELD_UNKNOWN);
        }
    }

    private DynamicFormSchemaService.SchemaFields schema(DynamicFormTemplateRevisionDO revision) {
        return schemaService.parseAndValidate(revision.getFormConfJson(), revision.getFormRulesJson(),
                revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion());
    }

    private void requireProviderOwner(DynamicFormProviderKey provider, DynamicFormOwnerKey owner) {
        if (provider == null || owner == null || !provider.equals(owner.providerKey())) {
            throw new IllegalArgumentException("provider and owner must match");
        }
    }

    private void requireActor(Long tenantId, Long actorUserId) {
        if (tenantId == null || tenantId < 0 || actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException("invalid trusted actor");
        }
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("invalid id");
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("required text is missing");
        return value.trim();
    }

    private void requireRevisionAction(DynamicFormBusinessAction action) {
        if (action != DynamicFormBusinessAction.REVISION_BINDING_PUBLISH
                && action != DynamicFormBusinessAction.REVISION_FROZEN_USE) {
            throw new IllegalArgumentException("invalid revision action");
        }
    }

    private void requireInspectAction(DynamicFormBusinessAction action) {
        if (!EnumSet.of(DynamicFormBusinessAction.READ, DynamicFormBusinessAction.PATCH,
                DynamicFormBusinessAction.COMPLETE, DynamicFormBusinessAction.CLONE_SOURCE,
                DynamicFormBusinessAction.FILE_READ, DynamicFormBusinessAction.FILE_WRITE).contains(action)) {
            throw new IllegalArgumentException("invalid instance inspection action");
        }
    }
}
