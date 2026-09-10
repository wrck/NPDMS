package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationErrors.*;
import static cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;

/** PM-03: resolves exact immutable revision closure; never returns unknown dependencies as available. */
@Service
@RequiredArgsConstructor
public class DeliveryDefinitionResolver {
    private final DeliveryDefinitionRevisionMapper revisions;
    private final DeliveryDefinitionReferenceMapper references;
    private final BusinessViewQueryApi businessViews;
    private final ProjectStageGateProviderRegistry gateProviders;
    private final ProjectStageGateProcessOwnerApi processes;
    private final TaskBusinessProviderRegistry taskBusinessProviders;

    public Map<Long, Snapshot> resolve(List<DeliveryDefinitionReference> roots, Long forbiddenOwner, boolean lock) {
        return resolve(roots, forbiddenOwner, lock, null);
    }

    public Map<Long, Snapshot> resolveDefinition(Revision definition, boolean lock) {
        return resolve(definition.references(), definition.id(), lock, definition);
    }

    private Map<Long, Snapshot> resolve(List<DeliveryDefinitionReference> roots, Long forbiddenOwner, boolean lock, Revision owner) {
        if (roots == null) throw exception(REFERENCE_INVALID, "references required");
        Map<Long, Revision> closure = new TreeMap<>();
        Set<String> slots = new HashSet<>();
        for (DeliveryDefinitionReference root : roots) {
            if (root == null || !DeliveryDefinitionPayloadValidator.code(root.referenceKey()) || !slots.add(root.referenceKey()))
                throw exception(REFERENCE_INVALID, "duplicate/invalid slot");
            visit(root.targetRevisionId(), forbiddenOwner, new HashSet<>(), closure);
        }
        // Published payload/reference edges cannot change. Lock every discovered row by stable ID,
        // then recheck revocation; no recursive discovery while holding PLT locks.
        if (lock) for (Long id : closure.keySet()) {
            DeliveryDefinitionRevisionDO row = revisions.lockRevision(new DeliveryDefinitionByIdQuery(tenant(), id));
            requirePublished(row);
            Revision original = closure.get(id);
            if (!Objects.equals(original.version(), row.getVersion())) throw exception(VERSION_CONFLICT);
        }
        Map<Long, Snapshot> result = new TreeMap<>();
        closure.forEach((id, revision) -> result.put(id, new Snapshot(revision, null)));
        for (Revision revision : closure.values()) validateLocal(revision, result);
        List<Revision> toValidate = new ArrayList<>(closure.values());
        if (owner != null) { validateLocal(owner, result); toValidate.add(owner); }
        Map<Long, BusinessViewRevision> views = inspectViews(toValidate, lock);
        closure.forEach((id, revision) -> result.put(id, new Snapshot(revision,
                revision.definitionKind() == DeliveryDefinitionKind.WORK_BINDING && revision.payload().hasNonNull("businessViewRevisionId")
                        ? views.get(revision.payload().path("businessViewRevisionId").asLong()) : null)));
        return Collections.unmodifiableMap(result);
    }

    public Revision require(Map<Long, Snapshot> closure, Long id, DeliveryDefinitionKind kind) {
        Snapshot snapshot = id == null ? null : closure.get(id);
        if (snapshot == null || snapshot.definition().definitionKind() != kind)
            throw exception(REFERENCE_INVALID, kind + ": " + id);
        return snapshot.definition();
    }

    private void visit(Long id, Long forbiddenOwner, Set<Long> path, Map<Long, Revision> closure) {
        if (id == null || id <= 0 || Objects.equals(id, forbiddenOwner) || !path.add(id))
            throw exception(REFERENCE_INVALID, "missing/self/cyclic reference");
        if (!closure.containsKey(id)) {
            DeliveryDefinitionRevisionDO row = revisions.selectById(id); requirePublished(row);
            List<DeliveryDefinitionReference> refs = references.selectReferences(new DeliveryDefinitionReferencesQuery(tenant(), id))
                    .stream().map(ref -> new DeliveryDefinitionReference(ref.getReferenceKey(), ref.getTargetRevisionId())).toList();
            Revision revision = new Revision(id, DeliveryDefinitionKind.valueOf(row.getDefinitionKind()), row.getDefinitionCode(),
                    row.getRevisionNo(), row.getRevisionState(), row.getSchemaVersion(), JsonUtils.parseObject(row.getPayload(), JsonNode.class),
                    refs, row.getPublishedAt(), row.getDisabledAt(), row.getVersion());
            for (DeliveryDefinitionReference ref : refs) visit(ref.targetRevisionId(), forbiddenOwner, path, closure);
            closure.put(id, revision);
        }
        path.remove(id);
    }

    private void requirePublished(DeliveryDefinitionRevisionDO row) {
        if (row == null || !Objects.equals(row.getTenantId(), tenant()) || Boolean.TRUE.equals(row.getDeleted())
                || !"PUBLISHED".equals(row.getRevisionState()) || row.getPublishedAt() == null || row.getDisabledAt() != null)
            throw exception(REFERENCE_INVALID, "same-tenant active published revision required");
    }

    private void validateLocal(Revision revision, Map<Long, Snapshot> closure) {
        try { DeliveryDefinitionPayloadValidator.validate(revision.definitionKind(), revision.schemaVersion(), revision.payload(), revision.references()); }
        catch (IllegalArgumentException ex) { throw exception(INVALID, ex.getMessage()); }
        JsonNode payload = revision.payload();
        switch (revision.definitionKind()) {
            case STAGE, TASK -> {
                Map<String, DeliveryDefinitionKind> types = Map.of("workBinding", DeliveryDefinitionKind.WORK_BINDING,
                        "permissionPolicy", DeliveryDefinitionKind.PERMISSION_POLICY, "completionRule", DeliveryDefinitionKind.COMPLETION_RULE);
                Map<String, Revision> targets = new HashMap<>();
                types.forEach((field, kind) -> {
                    String slot = payload.path(field).asText();
                    Long id = revision.references().stream().filter(ref -> slot.equals(ref.referenceKey()))
                            .map(DeliveryDefinitionReference::targetRevisionId).findFirst().orElse(null);
                    Revision target = require(closure, id, kind);
                    targets.put(field, target);
                    if (kind == DeliveryDefinitionKind.WORK_BINDING) {
                        String binding = target.payload().path("bindingType").asText();
                        if ((revision.definitionKind() == DeliveryDefinitionKind.STAGE && "TASK_NATIVE".equals(binding))
                                || (revision.definitionKind() == DeliveryDefinitionKind.TASK && "STAGE_NATIVE".equals(binding)))
                            throw exception(REFERENCE_INVALID, "binding owner kind mismatch");
                    }
                });
                validateBoundBusinessFacts(targets.get("completionRule").payload(), revision.definitionKind(),
                        targets.get("workBinding").payload());
            }
            case COMPLETION_RULE -> validateRuleProviders(payload);
            case DELIVERABLE -> validateRuleProviders(payload.path("confirmationRule"));
            case GATE -> { for (JsonNode ref : payload.path("references")) provider(ref.path("refType").asText(), ref.path("refCode").asText()); }
            default -> { }
        }
    }

    private Map<Long, BusinessViewRevision> inspectViews(Collection<Revision> definitions, boolean lock) {
        Map<Long, BusinessViewRevision> views = new TreeMap<>();
        for (Revision revision : definitions) {
            if (revision.definitionKind() != DeliveryDefinitionKind.WORK_BINDING || !revision.payload().hasNonNull("businessViewRevisionId")) continue;
            long id = revision.payload().path("businessViewRevisionId").asLong();
            BusinessViewRevision view = views.computeIfAbsent(id, key -> businessViews.getRevision(
                    new BusinessViewQueryApi.Query(key, BusinessViewQueryApi.Purpose.NEW_REFERENCE)));
            if (view == null || !Objects.equals(view.id(), id) || view.publishedAt() == null || view.disabledAt() != null
                    || !Objects.equals(view.ownerContext(), revision.payload().path("targetContextCode").asText())
                    || !Objects.equals(view.entityType(), revision.payload().path("targetObjectType").asText()))
                throw exception(REFERENCE_INVALID, "business view owner/type unavailable");
            JsonNode mapping = revision.payload().path("contextMapping");
            JsonNode properties = view.contextSchema().path("properties");
            for (String key : mapping.propertyNames()) if (!properties.has(key)) throw exception(REFERENCE_INVALID, "unknown view context: " + key);
            for (JsonNode key : view.contextSchema().path("required")) if (!mapping.hasNonNull(key.asText()))
                throw exception(REFERENCE_INVALID, "missing view context: " + key.asText());
            if ("DYNAMIC_FORM".equals(revision.payload().path("bindingType").asText())
                    && view.viewSource() != BusinessViewComponentProvider.ViewSource.DYNAMIC_FORM)
                throw exception(REFERENCE_INVALID, "dynamic form view required");
        }
        if (lock && !views.isEmpty()) {
            List<BusinessViewRevision> locked = businessViews.lockAndRevalidateAll(views.entrySet().stream()
                    .map(entry -> new BusinessViewQueryApi.Query(entry.getKey(), BusinessViewQueryApi.Purpose.NEW_REFERENCE,
                            entry.getValue().version())).toList());
            if (locked == null || locked.size() != views.size()) throw exception(REFERENCE_INVALID, "business views unavailable");
            Set<Long> verified = new HashSet<>();
            for (BusinessViewRevision view : locked) {
                BusinessViewRevision expected = view == null ? null : views.get(view.id());
                if (expected == null || !verified.add(view.id()) || !Objects.equals(view.version(), expected.version())
                        || view.publishedAt() == null || view.disabledAt() != null)
                    throw exception(REFERENCE_INVALID, "business view changed");
            }
        }
        return views;
    }

    private void validateBoundBusinessFacts(JsonNode rule, DeliveryDefinitionKind kind, JsonNode binding) {
        if (rule.has("operator")) {
            for (JsonNode child : rule.path("rules")) validateBoundBusinessFacts(child, kind, binding);
            return;
        }
        if (!"BUSINESS_FACT".equals(rule.path("predicate").asText())) return;
        // Only the TASK business object/component host currently evaluates this predicate.
        if (kind != DeliveryDefinitionKind.TASK
                || !Set.of("BUSINESS_OBJECT", "BUSINESS_COMPONENT").contains(binding.path("bindingType").asText()))
            throw exception(REFERENCE_INVALID, "BUSINESS_FACT runtime unsupported for binding: " + kind);
        String owner = binding.path("targetContextCode").asText();
        String type = binding.path("targetObjectType").asText();
        String code = rule.path("parameters").path("factCode").asText();
        if (!taskBusinessProviders.supportsCompletionFact(owner, type, code))
            throw exception(REFERENCE_INVALID, "Owner completion fact unavailable: " + owner + "/" + type + "/" + code);
    }

    private void validateRuleProviders(JsonNode rule) {
        if (rule.has("operator")) { for (JsonNode child : rule.path("rules")) validateRuleProviders(child); return; }
        String predicate = rule.path("predicate").asText();
        if ("BUSINESS_FACT".equals(predicate)) {
            String code = rule.path("parameters").path("factCode").asText();
            if (!taskBusinessProviders.supportsCompletionFact(code))
                throw exception(REFERENCE_INVALID, "Owner completion fact unavailable: " + code);
        }
        else if ("TASK_NATIVE_STATUS".equals(predicate)) provider("TASK", null);
        else if ("STAGE_NATIVE_STATUS".equals(predicate)) provider("STATE", null);
        else provider(predicate, rule.path("parameters").path("refCode").asText());
    }
    private void provider(String type, String code) {
        String key = switch (type) {
            case "TASK" -> "PROJ_TASK"; case "MILESTONE" -> "PROJ_MILESTONE"; case "STATE" -> "PROJ_STATE";
            case "DELIVERABLE" -> "ACC_DELIVERABLE"; case "PROCESS" -> "BPM_PROCESS"; case "APPROVAL" -> "BPM_APPROVAL";
            default -> null;
        };
        if (key == null || !gateProviders.hasProvider(key)) throw exception(REFERENCE_INVALID, "Owner Provider unavailable: " + type);
        if ("PROCESS".equals(type) || "APPROVAL".equals(type))
            processes.inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(tenant(), code));
    }
    private Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
}
