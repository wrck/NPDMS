package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Form schema compatibility and read-only access to imported legacy form references. */
@Component
@RequiredArgsConstructor
public class RequirementAnalysisFormPolicyProvider implements DynamicFormBusinessObjectPolicyProvider {
    private static final String REQUIRED_USAGE = "PRE_04_REQUIREMENT_ANALYSIS";
    private final RequirementAnalysisAccess access;
    private final RequirementAnalysisMapper mapper;

    @Override public DynamicFormProviderKey providerKey() {
        return new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
    }

    @Override
    public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query) {
        boolean actionAllowed = query != null && Set.of(DynamicFormBusinessAction.REVISION_BINDING_PUBLISH,
                DynamicFormBusinessAction.REVISION_FROZEN_USE).contains(query.action());
        boolean compatible = actionAllowed && REQUIRED_USAGE.equals(query.requiredUsage())
                && compatibleFields(query.fields());
        return new DynamicFormPolicyFact(query == null ? null : query.action(), compatible,
                compatible ? null : "PRE04_SCHEMA_INCOMPATIBLE",
                query == null || query.revisionFactVersion() == null ? null
                        : query.revisionFactVersion().longValue(),
                compatible ? "PRE04_SCHEMA_COMPATIBLE" : "PRE04_SCHEMA_INCOMPATIBLE");
    }

    private boolean compatibleFields(List<DynamicFormFieldDescriptor> fields) {
        if (fields == null) return false;
        Set<String> keys = new HashSet<>();
        for (DynamicFormFieldDescriptor field : fields) {
            if (field == null || field.fieldKey() == null || !keys.add(field.fieldKey())) return false;
        }
        for (var businessField : RequirementAnalysisEntityProvider.FIELDS.fields()) {
            String core = cn.hutool.core.util.StrUtil.toUnderlineCase(businessField.code()).toUpperCase(Locale.ROOT);
            DynamicFormFieldDescriptor text = field(fields, core);
            DynamicFormFieldDescriptor attachment = field(fields, core + "__ATTACHMENTS");
            if (text == null || text.controlledFile() || !"Editor".equals(text.componentType())
                    || text.required() != businessField.required()
                    || attachment == null || !attachment.controlledFile()
                    || attachment.required()
                    || !"PmsFileArtifact".equals(attachment.componentType())) return false;
        }
        return true;
    }

    private DynamicFormFieldDescriptor field(List<DynamicFormFieldDescriptor> fields, String key) {
        return fields.stream().filter(field -> key.equals(field.fieldKey())).findFirst().orElse(null);
    }

    @Override public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query) {
        if (query == null || query.action() == null
                || !Set.of(DynamicFormBusinessAction.READ, DynamicFormBusinessAction.FILE_READ).contains(query.action())
                || query.ownerKey() == null || !"SOL".equals(query.ownerKey().ownerContext())
                || !"REQUIREMENT_ANALYSIS".equals(query.ownerKey().objectType()))
            return denied(query == null ? null : query.action());
        try {
            var row = access.read(Long.valueOf(query.ownerKey().objectId()), new EntityActor(query.tenantId(), query.actorUserId(), null));
            return new DynamicFormPolicyFact(query.action(), true, null, row.getVersion().longValue(),
                    row.getId() + ":" + row.getRevisionState() + ":" + row.getVersion());
        } catch (RuntimeException unavailable) { return denied(query.action()); }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query) {
        if (query == null || query.expectedFact() == null) return denied(null);
        var lookup = new DynamicFormInstancePolicyQuery(query.tenantId(), query.actorUserId(), query.providerKey(),
                query.ownerKey(), query.instanceId(), query.expectedFact().action());
        var observed = inspectInstanceOwnerPolicy(lookup);
        if (!observed.allowed()) return observed;
        mapper.lockRevision(new RequirementRevisionQuery(query.tenantId(), Long.valueOf(query.ownerKey().objectId())));
        var locked = inspectInstanceOwnerPolicy(lookup);
        return Objects.equals(locked, query.expectedFact()) ? locked : denied(lookup.action());
    }

    private DynamicFormPolicyFact denied(DynamicFormBusinessAction action) {
        return new DynamicFormPolicyFact(action, false, "LEGACY_FORM_READ_ONLY", null, "LEGACY_FORM_READ_ONLY");
    }
}
