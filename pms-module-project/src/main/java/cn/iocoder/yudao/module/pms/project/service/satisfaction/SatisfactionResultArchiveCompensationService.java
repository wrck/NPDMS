package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultFileMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultArchiveProjectionUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultFilesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SatisfactionResultArchiveCompensationService {
    private final AccProjectDeliverableMapper deliverableMapper;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final ProjectDeliverableSourceAttachmentMapper attachmentMapper;
    private final SatisfactionResultMapper resultMapper;
    private final SatisfactionResultFileMapper resultFileMapper;
    private final FileArtifactApi fileArtifactApi;

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long tenantId, Long sourceVersionId) {
        ProjectDeliverableSourceVersionDO snapshot = sourceMapper.selectById(sourceVersionId);
        if (!satisfactionSource(snapshot, tenantId)) throw new IllegalStateException("archive source unavailable");
        AccProjectDeliverableDO root = deliverableMapper.selectByIdForUpdate(
                new ProjectDeliverableIdLockQuery(tenantId, snapshot.getDeliverableId()));
        ProjectDeliverableSourceVersionDO source = sourceMapper.selectByIdForUpdate(
                new DeliverableSourceIdLockQuery(tenantId, sourceVersionId));
        if (root == null || !satisfactionSource(source, tenantId)
                || !Objects.equals(source.getDeliverableId(), root.getId())) {
            throw new IllegalStateException("archive source identity conflict");
        }
        if ("ARCHIVED".equals(source.getArchiveStatus())) return;
        if (!List.of("CURRENT", "SUPERSEDED", "REVOKED").contains(source.getRelationStatus())
                || !"PENDING_COMPENSATION".equals(source.getArchiveStatus())) {
            throw new IllegalStateException("archive source state conflict");
        }
        SatisfactionResultDO result = resultMapper.selectByIdForUpdate(tenantId, source.getSourceObjectId());
        if (result == null || !Objects.equals(result.getTenantId(), tenantId)
                || result.getArchiveActorUserId() == null || result.getArchiveActorUserId() <= 0) {
            throw new IllegalStateException("archive actor unavailable");
        }
        List<ProjectDeliverableSourceAttachmentDO> rows = attachmentMapper.selectBySourceVersion(source.getId());
        if (rows.isEmpty()) throw new IllegalStateException("archive files missing");
        List<FileArtifactVersionFact> facts = rows.stream().map(this::toFact).toList();
        Long scopeVersion = facts.getFirst().scopeVersion();
        if (facts.stream().anyMatch(fact -> !Objects.equals(scopeVersion, fact.scopeVersion()))) {
            throw new IllegalStateException("archive file scope conflict");
        }
        FileReferenceSetKey archiveKey = new FileReferenceSetKey("ACC", "SATISFACTION_RESULT",
                String.valueOf(result.getId()), "SATISFACTION_ARCHIVE");
        List<SatisfactionResultFileDO> resultFiles = resultFileMapper.selectListByResult(
                new SatisfactionResultFilesQuery(tenantId, result.getId()));
        if (!samePublicFacts(rows, resultFiles)) throw new IllegalStateException("archive source file conflict");
        Map<String, List<SatisfactionResultFileDO>> groups = new LinkedHashMap<>();
        groups.put("SATISFACTION_RESULT_DOCUMENT", new java.util.ArrayList<>());
        groups.put("SATISFACTION_SIGNATURE", new java.util.ArrayList<>());
        groups.put("SATISFACTION_ATTACHMENT", new java.util.ArrayList<>());
        resultFiles.forEach(row -> groups.computeIfAbsent(sourcePurpose(row.getFileRole()),
                ignored -> new java.util.ArrayList<>()).add(row));
        for (Map.Entry<String, List<SatisfactionResultFileDO>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            String purpose = group.getKey();
            String objectType = "SATISFACTION_RESULT_DOCUMENT".equals(purpose)
                    ? "SATISFACTION_RESULT" : "SATISFACTION_RESPONSE";
            Long objectId = "SATISFACTION_RESULT".equals(objectType) ? result.getId() : result.getResponseId();
            FileReferenceSetKey activeKey = new FileReferenceSetKey("ACC", objectType,
                    String.valueOf(objectId), purpose);
            List<FileArtifactVersionFact> groupFacts = group.getValue().stream().map(this::toFact).toList();
            fileArtifactApi.archiveReferenceSets(new ArchiveFileReferenceSetsCommand(
                    "ACC-SAT-ARCHIVE:" + source.getId() + ":" + purpose,
                    "ACC-SAT-ARCHIVE:" + source.getId(), "ACC-SATISFACTION:" + result.getId(),
                    result.getArchiveActorUserId(), activeKey, archiveKey, scopeVersion, groupFacts));
        }

        source.setArchiveStatus("ARCHIVED"); source.setArchiveFailureCode(null);
        source.setArchiveTime(LocalDateTime.now()); source.setUpdater(String.valueOf(result.getArchiveActorUserId()));
        if (sourceMapper.updateById(source) != 1) throw new IllegalStateException("archive source update failed");
        if (resultMapper.updateArchiveProjection(new SatisfactionResultArchiveProjectionUpdate(
                tenantId, result.getId(), result.getVersion(), source.getId(), "ARCHIVED", null,
                result.getArchiveRetryCount(), String.valueOf(result.getArchiveActorUserId()))) != 1) {
            throw new IllegalStateException("archive result update failed");
        }
        if (Objects.equals(root.getCurrentSourceVersionId(), source.getId())) {
            root.setArchiveStatus("ARCHIVED"); root.setVersion(root.getVersion() + 1);
            root.setUpdater(String.valueOf(result.getArchiveActorUserId()));
            if (deliverableMapper.updateById(root) != 1) {
                throw new IllegalStateException("archive root update failed");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long sourceVersionId, String failureCode) {
        ProjectDeliverableSourceVersionDO snapshot = sourceMapper.selectById(sourceVersionId);
        if (!satisfactionSource(snapshot, tenantId)) return;
        deliverableMapper.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(tenantId, snapshot.getDeliverableId()));
        ProjectDeliverableSourceVersionDO source = sourceMapper.selectByIdForUpdate(
                new DeliverableSourceIdLockQuery(tenantId, sourceVersionId));
        if (source == null || !"PENDING_COMPENSATION".equals(source.getArchiveStatus())) return;
        SatisfactionResultDO result = resultMapper.selectByIdForUpdate(tenantId, source.getSourceObjectId());
        if (result == null) return;
        int retryCount = source.getArchiveRetryCount() == null ? 1 : source.getArchiveRetryCount() + 1;
        source.setArchiveFailureCode(failureCode); source.setArchiveRetryCount(retryCount);
        if (sourceMapper.updateById(source) != 1) throw new IllegalStateException("archive failure update failed");
        if (resultMapper.updateArchiveProjection(new SatisfactionResultArchiveProjectionUpdate(
                tenantId, result.getId(), result.getVersion(), source.getId(), "PENDING_COMPENSATION",
                failureCode, retryCount, String.valueOf(result.getArchiveActorUserId()))) != 1) {
            throw new IllegalStateException("archive result failure update failed");
        }
    }

    private boolean satisfactionSource(ProjectDeliverableSourceVersionDO source, Long tenantId) {
        return source != null && Objects.equals(source.getTenantId(), tenantId)
                && "SatisfactionResult".equals(source.getSourceObjectType());
    }

    private FileArtifactVersionFact toFact(ProjectDeliverableSourceAttachmentDO row) {
        return new FileArtifactVersionFact(row.getFileArtifactId(), row.getFileVersionNo(), row.getReferenceKey(),
                null, null, null, null, row.getFileHash(), "AVAILABLE", "ACTIVE",
                new FileFactVersion(row.getArtifactVersion(), row.getReferenceVersion(), row.getAvailabilityVersion()),
                row.getScopeVersion());
    }

    private FileArtifactVersionFact toFact(SatisfactionResultFileDO row) {
        return new FileArtifactVersionFact(row.getArtifactId(), row.getVersionNo(), row.getReferenceKey(),
                null, null, null, null, row.getFileHash(), "AVAILABLE", "ACTIVE",
                new FileFactVersion(row.getArtifactVersion(), row.getReferenceVersion(), row.getAvailabilityVersion()),
                row.getScopeVersion());
    }

    private String sourcePurpose(String role) {
        return switch (role) {
            case "RESULT_DOCUMENT" -> "SATISFACTION_RESULT_DOCUMENT";
            case "SIGNATURE" -> "SATISFACTION_SIGNATURE";
            case "ATTACHMENT" -> "SATISFACTION_ATTACHMENT";
            default -> throw new IllegalStateException("archive file role invalid");
        };
    }

    private boolean samePublicFacts(List<ProjectDeliverableSourceAttachmentDO> sources,
                                    List<SatisfactionResultFileDO> files) {
        if (sources.size() != files.size() || files.isEmpty()) return false;
        java.util.Set<String> sourceKeys = sources.stream().map(row -> publicKey(row.getFileArtifactId(),
                row.getFileVersionNo(), row.getReferenceKey(), row.getArtifactVersion(), row.getReferenceVersion(),
                row.getAvailabilityVersion(), row.getScopeVersion(), row.getFileHash()))
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> resultKeys = files.stream().map(row -> publicKey(row.getArtifactId(), row.getVersionNo(),
                row.getReferenceKey(), row.getArtifactVersion(), row.getReferenceVersion(),
                row.getAvailabilityVersion(), row.getScopeVersion(), row.getFileHash()))
                .collect(java.util.stream.Collectors.toSet());
        return sourceKeys.size() == sources.size() && resultKeys.size() == files.size() && sourceKeys.equals(resultKeys);
    }

    private String publicKey(Long artifactId, Integer versionNo, String referenceKey, Integer artifactVersion,
                             Integer referenceVersion, Integer availabilityVersion, Long scopeVersion, String hash) {
        return artifactId + "|" + versionNo + "|" + referenceKey + "|" + artifactVersion + "|"
                + referenceVersion + "|" + availabilityVersion + "|" + scopeVersion + "|" + hash;
    }
}
