package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_VERSION_CONFLICT;

@Component
public class FileBusinessObjectPolicyRegistry {

    private final List<FileBusinessObjectPolicyProvider> providers;

    public FileBusinessObjectPolicyRegistry(List<FileBusinessObjectPolicyProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(query.ownerContext(), query.objectType());
        FileBusinessObjectPolicyFact fact;
        try {
            fact = provider.inspect(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return requireUsableFact(fact);
    }

    public FileBusinessObjectPolicyFact lockAndRevalidate(
            FileBusinessObjectPolicyRevalidationQuery query) {
        FileBusinessObjectPolicyQuery policyQuery = query.toInspectionQuery();
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                policyQuery.ownerContext(), policyQuery.objectType());
        FileBusinessObjectPolicyFact fact;
        try {
            fact = provider.lockAndRevalidate(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        fact = requireUsableFact(fact);
        if (!query.expectedScopeVersion().equals(fact.scopeVersion())) {
            throw exception(FILE_SCOPE_VERSION_CONFLICT);
        }
        return fact;
    }

    private FileBusinessObjectPolicyProvider requireUniqueProvider(String ownerContext, String objectType) {
        List<FileBusinessObjectPolicyProvider> matches;
        try {
            matches = providers.stream()
                    .filter(provider -> ownerContext.equals(normalize(provider.ownerContext()))
                            && objectType.equals(normalize(provider.objectType())))
                    .toList();
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        if (matches.size() != 1) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return matches.getFirst();
    }

    private FileBusinessObjectPolicyFact requireUsableFact(FileBusinessObjectPolicyFact fact) {
        if (fact == null) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        if (!fact.allowed()) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        if (fact.scopeVersion() == null || fact.scopeVersion() < 0
                || fact.referenceMutability() == null || fact.referenceMutability().isBlank()
                || fact.cardinality() == null || fact.cardinality().isBlank()
                || fact.allowedCategoryCodes().isEmpty() || fact.allowedMediaTypes().isEmpty()
                || fact.maxSizeBytes() == null || fact.maxSizeBytes() <= 0
                || fact.sensitivityCode() == null || fact.sensitivityCode().isBlank()) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return fact;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
