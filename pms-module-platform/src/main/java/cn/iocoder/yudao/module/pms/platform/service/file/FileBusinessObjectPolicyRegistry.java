package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFilePolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadCompletePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedFileRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadCompletePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadInitializePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadPolicyFact;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_VERSION_CONFLICT;

@Component
public class FileBusinessObjectPolicyRegistry {

    private static final String BUSINESS_GRANT_OWNER = "ACC";
    private static final String BUSINESS_GRANT_OBJECT = "SATISFACTION_RESPONSE";

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

    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                query.key().ownerContext(), query.key().objectType());
        FileBusinessObjectPolicyFact fact;
        try {
            fact = provider.inspectReferenceSet(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return requireUsableFact(fact);
    }

    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                query.key().ownerContext(), query.key().objectType());
        FileBusinessObjectPolicyFact fact;
        try {
            fact = provider.lockAndRevalidateReferenceSet(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        fact = requireUsableFact(fact);
        if (!query.expectedScopeVersion().equals(fact.scopeVersion())) {
            throw exception(FILE_SCOPE_VERSION_CONFLICT);
        }
        return fact;
    }

    public FileBusinessObjectPolicyFact lockAndRevalidateGeneratedBusinessFile(
            GeneratedBusinessFilePolicyRevalidationQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                query.ownerContext(), query.objectType());
        FileBusinessObjectPolicyFact fact;
        try {
            fact = provider.lockAndRevalidateGeneratedBusinessFile(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        fact = requireUsableFact(fact);
        if (!query.expectedScopeVersion().equals(fact.scopeVersion())) {
            throw exception(FILE_SCOPE_VERSION_CONFLICT);
        }
        return fact;
    }

    public BusinessGrantUploadPolicyFact initializeBusinessGrantUploadPolicy(
            BusinessGrantUploadInitializePolicyQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                BUSINESS_GRANT_OWNER, BUSINESS_GRANT_OBJECT);
        BusinessGrantUploadPolicyFact fact;
        try {
            fact = provider.initializeBusinessGrantUploadPolicy(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return requireBusinessGrantFact(fact, query.grantId(), query.grantVersion(),
                query.questionnaireId(), query.requestId(), query.responseId(), query.policyKey(),
                query.fileSlotKey(), query.fileSequence(), null);
    }

    public BusinessGrantUploadPolicyFact lockAndRevalidateBusinessGrantUpload(
            BusinessGrantUploadCompletePolicyQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                BUSINESS_GRANT_OWNER, BUSINESS_GRANT_OBJECT);
        BusinessGrantUploadPolicyFact fact;
        try {
            fact = provider.lockAndRevalidateBusinessGrantUpload(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return requireBusinessGrantFact(fact, query.grantId(), query.grantVersion(),
                query.questionnaireId(), query.requestId(), query.responseId(), query.policyKey(),
                query.fileSlotKey(), query.fileSequence(), query.expectedScopeVersion());
    }

    public BusinessGrantUploadPolicyFact lockAndRevalidateBusinessGrantFiles(
            BusinessGrantFileRevalidationQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(
                BUSINESS_GRANT_OWNER, BUSINESS_GRANT_OBJECT);
        BusinessGrantUploadPolicyFact fact;
        try {
            fact = provider.lockAndRevalidateBusinessGrantFiles(query);
        } catch (RuntimeException ex) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        return requireBusinessGrantFact(fact, query.grantId(), query.grantVersion(),
                query.questionnaireId(), query.requestId(), query.responseId(), null,
                null, null, null);
    }

    public AuthenticatedAssistedUploadPolicyFact initializeAuthenticatedAssistedUploadPolicy(
            AuthenticatedAssistedUploadInitializePolicyQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(BUSINESS_GRANT_OWNER, BUSINESS_GRANT_OBJECT);
        AuthenticatedAssistedUploadPolicyFact fact;
        try { fact = provider.initializeAuthenticatedAssistedUploadPolicy(query); }
        catch (RuntimeException ex) { throw exception(FILE_PROVIDER_UNAVAILABLE); }
        return requireAuthenticatedAssistedFact(fact, query.actorUserId(), query.taskId(), query.questionnaireId(),
                query.requestId(), query.responseId(), query.policyKey(), query.fileSlotKey(), query.fileSequence(), null);
    }

    public AuthenticatedAssistedUploadPolicyFact lockAndRevalidateAuthenticatedAssistedUpload(
            AuthenticatedAssistedUploadCompletePolicyQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(BUSINESS_GRANT_OWNER, BUSINESS_GRANT_OBJECT);
        AuthenticatedAssistedUploadPolicyFact fact;
        try { fact = provider.lockAndRevalidateAuthenticatedAssistedUpload(query); }
        catch (RuntimeException ex) { throw exception(FILE_PROVIDER_UNAVAILABLE); }
        return requireAuthenticatedAssistedFact(fact, query.actorUserId(), query.taskId(), query.questionnaireId(),
                query.requestId(), query.responseId(), query.policyKey(), query.fileSlotKey(), query.fileSequence(),
                query.expectedScopeVersion());
    }

    public AuthenticatedAssistedUploadPolicyFact lockAndRevalidateAuthenticatedAssistedFiles(
            AuthenticatedAssistedFileRevalidationQuery query) {
        FileBusinessObjectPolicyProvider provider = requireUniqueProvider(BUSINESS_GRANT_OWNER, BUSINESS_GRANT_OBJECT);
        AuthenticatedAssistedUploadPolicyFact fact;
        try { fact = provider.lockAndRevalidateAuthenticatedAssistedFiles(query); }
        catch (RuntimeException ex) { throw exception(FILE_PROVIDER_UNAVAILABLE); }
        return requireAuthenticatedAssistedFact(fact, query.actorUserId(), query.taskId(), query.questionnaireId(),
                query.requestId(), query.responseId(), null, null, null, null);
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

    private BusinessGrantUploadPolicyFact requireBusinessGrantFact(
            BusinessGrantUploadPolicyFact fact, Long grantId, Integer grantVersion,
            Long questionnaireId, String requestId, Long responseId, String policyKey,
            String fileSlotKey, Integer fileSequence, Long expectedScopeVersion) {
        if (fact == null || !java.util.Objects.equals(grantId, fact.grantId())
                || !java.util.Objects.equals(grantVersion, fact.grantVersion())
                || !java.util.Objects.equals(questionnaireId, fact.questionnaireId())
                || !java.util.Objects.equals(requestId, fact.requestId())
                || !java.util.Objects.equals(responseId, fact.responseId())
                || (policyKey != null && !java.util.Objects.equals(policyKey, fact.policyKey()))
                || (fileSlotKey != null && !java.util.Objects.equals(fileSlotKey, fact.fileSlotKey()))
                || (fileSequence != null && !java.util.Objects.equals(fileSequence, fact.fileSequence()))
                || fact.grantIssuerUserId() == null || fact.grantIssuerUserId() <= 0
                || fact.scopeVersion() == null || fact.scopeVersion() < 0
                || (expectedScopeVersion != null
                && !java.util.Objects.equals(expectedScopeVersion, fact.scopeVersion()))) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        requireUsableFact(fact.filePolicy());
        return fact;
    }

    private AuthenticatedAssistedUploadPolicyFact requireAuthenticatedAssistedFact(
            AuthenticatedAssistedUploadPolicyFact fact, Long actorUserId, Long taskId, Long questionnaireId,
            String requestId, Long responseId, String policyKey, String fileSlotKey,
            Integer fileSequence, Long expectedScopeVersion) {
        if (fact == null || !java.util.Objects.equals(actorUserId, fact.actorUserId())
                || !java.util.Objects.equals(taskId, fact.taskId())
                || !java.util.Objects.equals(questionnaireId, fact.questionnaireId())
                || !java.util.Objects.equals(requestId, fact.requestId())
                || !java.util.Objects.equals(responseId, fact.responseId())
                || (policyKey != null && !java.util.Objects.equals(policyKey, fact.policyKey()))
                || (fileSlotKey != null && !java.util.Objects.equals(fileSlotKey, fact.fileSlotKey()))
                || (fileSequence != null && !java.util.Objects.equals(fileSequence, fact.fileSequence()))
                || fact.actorUserId() == null || fact.actorUserId() <= 0
                || fact.scopeVersion() == null || fact.scopeVersion() < 0
                || (expectedScopeVersion != null && !java.util.Objects.equals(expectedScopeVersion, fact.scopeVersion()))) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        requireUsableFact(fact.filePolicy());
        return fact;
    }

    private BusinessGrantUploadPolicyFact requireBusinessGrantFact(
            BusinessGrantUploadPolicyFact fact, Long grantId, Integer grantVersion,
            Long questionnaireId, String requestId, Long responseId, String policyKey,
            String fileSlotKey, Integer fileSequence, Long expectedScopeVersion) {
        if (fact == null || !java.util.Objects.equals(grantId, fact.grantId())
                || !java.util.Objects.equals(grantVersion, fact.grantVersion())
                || !java.util.Objects.equals(questionnaireId, fact.questionnaireId())
                || !java.util.Objects.equals(requestId, fact.requestId())
                || !java.util.Objects.equals(responseId, fact.responseId())
                || (policyKey != null && !java.util.Objects.equals(policyKey, fact.policyKey()))
                || (fileSlotKey != null && !java.util.Objects.equals(fileSlotKey, fact.fileSlotKey()))
                || (fileSequence != null && !java.util.Objects.equals(fileSequence, fact.fileSequence()))
                || fact.grantIssuerUserId() == null || fact.grantIssuerUserId() <= 0
                || fact.scopeVersion() == null || fact.scopeVersion() < 0
                || (expectedScopeVersion != null
                && !java.util.Objects.equals(expectedScopeVersion, fact.scopeVersion()))) {
            throw exception(FILE_PROVIDER_UNAVAILABLE);
        }
        requireUsableFact(fact.filePolicy());
        return fact;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
