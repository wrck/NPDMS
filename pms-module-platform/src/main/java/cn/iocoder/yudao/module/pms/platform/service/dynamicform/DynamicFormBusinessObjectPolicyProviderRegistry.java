package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionPolicyQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_SCOPE_VERSION_CONFLICT;

@Component
public class DynamicFormBusinessObjectPolicyProviderRegistry {

    private final List<DynamicFormBusinessObjectPolicyProvider> providers;
    private final Object transactionResourceKey = new Object();
    private final Object revisionTransactionResourceKey = new Object();

    public DynamicFormBusinessObjectPolicyProviderRegistry(List<DynamicFormBusinessObjectPolicyProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public DynamicFormPolicyFact inspectRevision(DynamicFormRevisionPolicyQuery query) {
        DynamicFormPolicyFact fact = usable(
                invoke(() -> provider(query.providerKey()).inspectRevisionCompatibility(query)), query.action());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            revisionPolicies().put(RevisionPolicyKey.from(query), fact);
        }
        return fact;
    }

    public DynamicFormPolicyFact revalidateRevision(DynamicFormRevisionPolicyQuery query,
                                                    DynamicFormPolicyFact expected) {
        DynamicFormPolicyFact fact = TransactionSynchronizationManager.hasResource(revisionTransactionResourceKey)
                ? revisionPolicies().get(RevisionPolicyKey.from(query)) : null;
        if (fact == null) {
            fact = usable(invoke(() -> provider(query.providerKey()).inspectRevisionCompatibility(query)),
                    query.action());
        }
        if (!Objects.equals(expected, fact)) throw exception(DYNAMIC_FORM_SCOPE_VERSION_CONFLICT);
        return fact;
    }

    public DynamicFormPolicyFact inspectInstance(DynamicFormInstancePolicyQuery query) {
        return usable(invoke(() -> provider(query.providerKey()).inspectInstanceOwnerPolicy(query)), query.action());
    }

    public DynamicFormPolicyFact lockAndRevalidate(DynamicFormPolicyRevalidationQuery query) {
        DynamicFormPolicyFact frozen = prevalidatedPolicy(query.tenantId(), query.actorUserId(), query.ownerKey(),
                query.instanceId(), query.expectedFact().action()).orElse(null);
        if (frozen != null) {
            if (!Objects.equals(query.expectedFact(), frozen)) {
                throw exception(DYNAMIC_FORM_SCOPE_VERSION_CONFLICT);
            }
            return frozen;
        }
        DynamicFormPolicyFact fact = usable(invoke(() -> provider(query.providerKey())
                .lockAndRevalidateInstanceOwnerPolicy(query)), query.expectedFact().action());
        if (!Objects.equals(query.expectedFact().scopeVersion(), fact.scopeVersion())
                || !Objects.equals(query.expectedFact().ownerStateSummary(), fact.ownerStateSummary())) {
            throw exception(DYNAMIC_FORM_SCOPE_VERSION_CONFLICT);
        }
        remember(query, fact);
        return fact;
    }

    public Optional<DynamicFormPolicyFact> prevalidatedFilePolicy(Long tenantId, Long actorUserId,
                                                                   cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormOwnerKey ownerKey,
                                                                   Long instanceId,
                                                                   cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction action,
                                                                   Long expectedScopeVersion) {
        DynamicFormPolicyFact fact = prevalidatedPolicy(tenantId, actorUserId, ownerKey, instanceId, action)
                .orElse(null);
        if (fact == null || expectedScopeVersion != null
                && !Objects.equals(expectedScopeVersion, fact.scopeVersion())) return Optional.empty();
        return Optional.of(fact);
    }

    private Optional<DynamicFormPolicyFact> prevalidatedPolicy(Long tenantId, Long actorUserId,
                                                                cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormOwnerKey ownerKey,
                                                                Long instanceId,
                                                                cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction action) {
        if (!TransactionSynchronizationManager.hasResource(transactionResourceKey)) return Optional.empty();
        return Optional.ofNullable(policies().get(new PolicyKey(tenantId, actorUserId, ownerKey, instanceId, action)));
    }

    private void remember(DynamicFormPolicyRevalidationQuery query, DynamicFormPolicyFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw exception(DYNAMIC_FORM_PROVIDER_UNAVAILABLE);
        }
        policies().put(new PolicyKey(query.tenantId(), query.actorUserId(), query.ownerKey(), query.instanceId(),
                fact.action()), fact);
    }

    @SuppressWarnings("unchecked")
    private Map<PolicyKey, DynamicFormPolicyFact> policies() {
        if (!TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            Map<PolicyKey, DynamicFormPolicyFact> policies = new HashMap<>();
            TransactionSynchronizationManager.bindResource(transactionResourceKey, policies);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(transactionResourceKey);
                }
            });
        }
        return (Map<PolicyKey, DynamicFormPolicyFact>) TransactionSynchronizationManager
                .getResource(transactionResourceKey);
    }

    private record PolicyKey(Long tenantId, Long actorUserId,
                             cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormOwnerKey ownerKey,
                             Long instanceId,
                             cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction action) {
    }

    private record RevisionPolicyKey(Long tenantId, Long actorUserId, DynamicFormProviderKey providerKey,
                                     Long templateId, Long revisionId, Integer revisionNo,
                                     Integer revisionFactVersion, String requiredUsage,
                                     cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction action) {
        private static RevisionPolicyKey from(DynamicFormRevisionPolicyQuery query) {
            return new RevisionPolicyKey(query.tenantId(), query.actorUserId(), query.providerKey(),
                    query.templateId(), query.templateRevisionId(), query.revisionNo(), query.revisionFactVersion(),
                    query.requiredUsage(), query.action());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<RevisionPolicyKey, DynamicFormPolicyFact> revisionPolicies() {
        if (!TransactionSynchronizationManager.hasResource(revisionTransactionResourceKey)) {
            Map<RevisionPolicyKey, DynamicFormPolicyFact> policies = new HashMap<>();
            TransactionSynchronizationManager.bindResource(revisionTransactionResourceKey, policies);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(revisionTransactionResourceKey);
                }
            });
        }
        return (Map<RevisionPolicyKey, DynamicFormPolicyFact>) TransactionSynchronizationManager
                .getResource(revisionTransactionResourceKey);
    }

    private DynamicFormBusinessObjectPolicyProvider provider(DynamicFormProviderKey key) {
        if (key == null) throw exception(DYNAMIC_FORM_PROVIDER_UNAVAILABLE);
        List<DynamicFormBusinessObjectPolicyProvider> matches = providers.stream()
                .filter(provider -> key.equals(provider.providerKey())).toList();
        if (matches.size() != 1) throw exception(DYNAMIC_FORM_PROVIDER_UNAVAILABLE);
        return matches.getFirst();
    }

    private DynamicFormPolicyFact usable(DynamicFormPolicyFact fact,
                                         cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction action) {
        if (fact == null || fact.action() != action || fact.scopeVersion() == null || fact.scopeVersion() < 0
                || fact.ownerStateSummary() == null || fact.ownerStateSummary().isBlank()) {
            throw exception(DYNAMIC_FORM_PROVIDER_UNAVAILABLE);
        }
        if (!fact.allowed()) throw exception(DYNAMIC_FORM_SCOPE_FORBIDDEN);
        return fact;
    }

    private DynamicFormPolicyFact invoke(java.util.function.Supplier<DynamicFormPolicyFact> call) {
        try {
            return call.get();
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException business) {
            throw business;
        } catch (RuntimeException unavailable) {
            throw exception(DYNAMIC_FORM_PROVIDER_UNAVAILABLE);
        }
    }
}
