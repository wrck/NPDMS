package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Set;

@Service @RequiredArgsConstructor
public class FileEvidenceService implements FileEvidenceApi {
    private final FileArtifactMapper artifacts;
    private final FileVersionMapper versions;
    private final FileReferenceMapper references;

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Document inspectDocument(Long tenantId, Long referenceId) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()) || referenceId == null)
            throw new IllegalArgumentException("FILE_DOCUMENT_QUERY_INVALID");
        var reference = references.selectIdentity(new FileReferenceMapper.IdentityQuery(tenantId, referenceId));
        if (reference == null) return null;
        var artifact = artifacts.selectOne(new FileArtifactLockQuery(tenantId, reference.getArtifactId()));
        var version = versions.selectOne(new FileVersionLockQuery(tenantId, reference.getArtifactId(), reference.getFileVersionNo()));
        if (artifact == null || version == null) return null;
        boolean available = !Boolean.TRUE.equals(artifact.getDeleted()) && "ACTIVE".equals(artifact.getLifecycleStatusCode())
                && "AVAILABLE".equals(version.getAvailabilityStatusCode())
                && Set.of("ACTIVE", "ARCHIVED").contains(reference.getStatusCode());
        return new Document(referenceId, reference.getOwnerContext(), reference.getObjectType(), reference.getObjectId(),
                reference.getPurposeCode(), reference.getReferenceKey(), reference.getArtifactId(), reference.getFileVersionNo(),
                version.getSha256(), artifact.getName(), available);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Document inspectReference(Reference query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || java.util.stream.Stream.of(query.ownerContext(),query.objectType(),query.objectId(),query.purposeCode(),query.referenceKey()).anyMatch(value->value==null||value.isBlank()))
            throw new IllegalArgumentException("FILE_DOCUMENT_QUERY_INVALID");
        var reference=references.selectExact(new ExactFileReferenceQuery(query.tenantId(),query.ownerContext(),query.objectType(),query.objectId(),query.purposeCode(),query.referenceKey()));
        return reference==null?null:inspectDocument(query.tenantId(),reference.getId());
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Document inspectDocumentByArtifact(Long tenantId, Long artifactId, Integer versionNo) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()) || artifactId == null || versionNo == null)
            throw new IllegalArgumentException("FILE_DOCUMENT_QUERY_INVALID");
        // 同一工件版本可能被多个引用槽锚定（如既有版本附加），取最早引用作为代表身份。
        var reference = references.selectByArtifactForUpdate(new FileArtifactReferenceQuery(tenantId, artifactId))
                .stream().filter(row -> versionNo.equals(row.getFileVersionNo()))
                .sorted(java.util.Comparator.comparing(FileReferenceDO::getId)).findFirst().orElse(null);
        if (reference == null) return null;
        return inspectDocument(tenantId, reference.getId());
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Fact lockAndRevalidate(Query query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())
                || query.artifactId() == null || query.artifactId() <= 0 || query.versionNo() == null || query.versionNo() <= 0
                || query.sha256() == null || !query.sha256().matches("[a-fA-F0-9]{64}")
                || java.util.stream.Stream.of(query.ownerContext(), query.objectType(), query.objectId(), query.purposeCode(), query.referenceKey())
                .anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("FILE_EVIDENCE_QUERY_INVALID");
        var artifact = artifacts.selectForUpdate(new FileArtifactLockQuery(query.tenantId(), query.artifactId()));
        var version = versions.selectForUpdate(new FileVersionLockQuery(query.tenantId(), query.artifactId(), query.versionNo()));
        var reference = references.selectForUpdate(new FileReferenceLockQuery(query.tenantId(), query.ownerContext(),
                query.objectType(), query.objectId(), query.purposeCode(), query.referenceKey()));
        boolean valid = artifact != null && version != null && reference != null
                && Objects.equals(query.tenantId(), artifact.getTenantId()) && !Boolean.TRUE.equals(artifact.getDeleted())
                && Objects.equals(query.tenantId(), version.getTenantId()) && Objects.equals(query.tenantId(), reference.getTenantId())
                && query.ownerContext().equals(artifact.getOwnerContext()) && "ACTIVE".equals(artifact.getLifecycleStatusCode())
                && "AVAILABLE".equals(version.getAvailabilityStatusCode()) && query.sha256().equals(version.getSha256())
                && Set.of("ACTIVE", "ARCHIVED").contains(reference.getStatusCode())
                && query.artifactId().equals(reference.getArtifactId()) && query.versionNo().equals(reference.getFileVersionNo());
        return new Fact(valid, valid ? "FILE_EVIDENCE_VALID" : "FILE_EVIDENCE_UNAVAILABLE",
                artifact == null ? null : artifact.getVersion(), version == null ? null : version.getAvailabilityVersion(),
                reference == null ? null : reference.getVersion());
    }
}
