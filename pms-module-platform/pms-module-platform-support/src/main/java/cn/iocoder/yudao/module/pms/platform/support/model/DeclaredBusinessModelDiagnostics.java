package cn.iocoder.yudao.module.pms.platform.support.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;

import java.util.ArrayList;
import java.util.List;

/** Reports compatibility gaps without silently adding permissions or changing legacy domain behavior. */
public final class DeclaredBusinessModelDiagnostics {
    private DeclaredBusinessModelDiagnostics() {
    }

    public record Issue(String stableCode, String location, String errorCode, String impact) {
    }

    public static List<Issue> inspect(BusinessModelDeclaration declaration) {
        var model = declaration.descriptor();
        List<Issue> issues = new ArrayList<>();
        if (model.authorizationPolicyRef() == null || model.authorizationPolicyRef().isBlank()) {
            issues.add(new Issue(model.stableCode(), "read", "READ_PERMISSION_NOT_DECLARED",
                    "Default reads are rejected; an existing Owner permission policy may still provide authorization."));
        }
        if (model.scopeBinding() == null) {
            issues.add(new Issue(model.stableCode(), "scope", "SCOPE_POLICY_NOT_DECLARED",
                    "Default writes and reads without an Owner scope policy are rejected."));
        } else if (!"tenant".equals(model.scopeBinding().policyRef())) {
            String ownership = model.scopeBinding().ownershipFieldCode();
            if (ownership == null || BusinessModelIntrospector.businessFields(declaration.entityClass())
                    .stream().noneMatch(field -> field.code().equals(ownership))) {
                issues.add(new Issue(model.stableCode(), "scope", "SCOPE_MAPPING_INVALID",
                        "The declared ownership field does not exist in the persisted entity."));
            }
        }
        for (var operation : model.operations()) {
            if ((operation.kind() == BusinessOperationDescriptor.StandardOperationKind.CREATE
                    || operation.kind() == BusinessOperationDescriptor.StandardOperationKind.UPDATE)
                    && (operation.authorizationPolicyRef() == null || operation.authorizationPolicyRef().isBlank())) {
                issues.add(new Issue(model.stableCode(), "operation:" + operation.code(),
                        "OPERATION_PERMISSION_NOT_DECLARED",
                        "Default execution is rejected; an existing Owner operation policy may still authorize it."));
            }
        }
        var persisted = BusinessModelIntrospector.businessFields(declaration.entityClass());
        for (var field : model.fields()) {
            var property = persisted.stream().filter(candidate -> candidate.code().equals(field.code())).findFirst();
            if (property.isEmpty() || property.get().type() != field.type()) {
                issues.add(new Issue(model.stableCode(), "field:" + field.code(), "FIELD_DECLARATION_INVALID",
                        "The declared field has no matching persisted property and type."));
            }
        }
        return List.copyOf(issues);
    }
}
