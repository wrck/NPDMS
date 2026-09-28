package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultFileMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionResultArchiveProjectionUpdate;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionResultFilesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 满意度投影材料归档补偿（P06R I2）：材料行是补偿主体，投影来源经 材料 → 提交台账 →
 * satisfaction-result:{成果ID}:{版本} 反查；分组与文件事实沿用成果文件表（业务侧权威冻结元组），
 * 材料与成果文件以（工件、版本、摘要）集合互证。撤销链的材料保持 PENDING 照常归档。
 */
@Service
@RequiredArgsConstructor
public class SatisfactionResultArchiveCompensationService {
    private final PlatformDeliveryRequirementApi platform;
    private final SatisfactionResultMapper resultMapper;
    private final SatisfactionResultFileMapper resultFileMapper;
    private final FileArtifactApi fileArtifactApi;

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long tenantId, Long materialId) {
        List<TemplateFrozenMaterialView> locked = platform.lockMaterials(List.of(materialId));
        if (locked.isEmpty()) throw new IllegalStateException("archive source unavailable");
        TemplateFrozenMaterialView material = locked.getFirst();
        if (PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED.equals(material.archiveStatus())) return;
        if (!PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION.equals(material.archiveStatus())
                || !PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE.equals(material.materialKind())) {
            throw new IllegalStateException("archive source state conflict");
        }
        TemplateFrozenSubmissionView submission = requireSatisfactionProjection(material);
        long resultId = resultIdOf(submission.requestKey());
        SatisfactionResultDO result = resultMapper.selectByIdForUpdate(tenantId, resultId);
        if (result == null || !Objects.equals(result.getTenantId(), tenantId)
                || result.getArchiveActorUserId() == null || result.getArchiveActorUserId() <= 0) {
            throw new IllegalStateException("archive actor unavailable");
        }
        List<TemplateFrozenMaterialView> materials = platform.lockMaterials(submission.materialIds());
        if (materials.isEmpty()) throw new IllegalStateException("archive files missing");
        List<SatisfactionResultFileDO> resultFiles = resultFileMapper.selectListByResult(
                new SatisfactionResultFilesQuery(tenantId, result.getId()));
        if (!sameFiles(materials, resultFiles)) throw new IllegalStateException("archive source file conflict");
        Long scopeVersion = resultFiles.getFirst().getScopeVersion();
        if (resultFiles.stream().anyMatch(row -> !Objects.equals(scopeVersion, row.getScopeVersion()))) {
            throw new IllegalStateException("archive file scope conflict");
        }
        Map<String, List<SatisfactionResultFileDO>> groups = new LinkedHashMap<>();
        groups.put("SATISFACTION_RESULT_DOCUMENT", new ArrayList<>());
        groups.put("SATISFACTION_SIGNATURE", new ArrayList<>());
        groups.put("SATISFACTION_ATTACHMENT", new ArrayList<>());
        resultFiles.forEach(row -> groups.computeIfAbsent(sourcePurpose(row.getFileRole()),
                ignored -> new ArrayList<>()).add(row));
        for (Map.Entry<String, List<SatisfactionResultFileDO>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            String purpose = group.getKey();
            String objectType = "SATISFACTION_RESULT_DOCUMENT".equals(purpose)
                    ? "SATISFACTION_RESULT" : "SATISFACTION_RESPONSE";
            Long objectId = "SATISFACTION_RESULT".equals(objectType) ? result.getId() : result.getResponseId();
            FileReferenceSetKey activeKey = new FileReferenceSetKey("ACC", objectType,
                    String.valueOf(objectId), purpose);
            FileReferenceSetKey archiveKey = new FileReferenceSetKey("ACC", "SATISFACTION_RESULT",
                    String.valueOf(result.getId()), "SATISFACTION_ARCHIVE");
            List<FileArtifactVersionFact> groupFacts = group.getValue().stream().map(this::toFact).toList();
            fileArtifactApi.archiveReferenceSets(new ArchiveFileReferenceSetsCommand(
                    "ACC-SAT-ARCHIVE:" + submission.id() + ":" + purpose,
                    "ACC-SAT-ARCHIVE:" + submission.id(), "ACC-SATISFACTION:" + result.getId(),
                    result.getArchiveActorUserId(), activeKey, archiveKey, scopeVersion, groupFacts));
        }
        LocalDateTime now = LocalDateTime.now();
        String actor = String.valueOf(result.getArchiveActorUserId());
        for (TemplateFrozenMaterialView row : materials) {
            platform.markMaterialArchiveState(row.id(), PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED, null, now, actor);
        }
        if (resultMapper.updateArchiveProjection(new SatisfactionResultArchiveProjectionUpdate(
                tenantId, result.getId(), result.getVersion(), submission.id(), "ARCHIVED", null,
                result.getArchiveRetryCount(), actor)) != 1) {
            throw new IllegalStateException("archive result update failed");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long materialId, String failureCode) {
        int retryCount = platform.bumpMaterialArchiveRetry(materialId, failureCode);
        if (retryCount == 0) return;
        var submission = platform.findSubmissionIdByMaterial(materialId).flatMap(platform::findSubmissionById);
        if (submission.isEmpty()) return;
        long resultId = resultIdOf(submission.get().requestKey());
        SatisfactionResultDO result = resultMapper.selectByIdForUpdate(tenantId, resultId);
        if (result == null) return;
        resultMapper.updateArchiveProjection(new SatisfactionResultArchiveProjectionUpdate(
                tenantId, result.getId(), result.getVersion(), submission.get().id(),
                PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION, failureCode, retryCount,
                String.valueOf(result.getArchiveActorUserId())));
    }

    private TemplateFrozenSubmissionView requireSatisfactionProjection(TemplateFrozenMaterialView material) {
        TemplateFrozenSubmissionView submission = platform
                .findSubmissionIdByMaterial(material.id()).flatMap(platform::findSubmissionById)
                .orElseThrow(() -> new IllegalStateException("archive source identity conflict"));
        if (!PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(submission.sourceType())
                || submission.requestKey() == null
                || !submission.requestKey().startsWith("satisfaction-result:")) {
            throw new IllegalStateException("archive source identity conflict");
        }
        return submission;
    }

    static long resultIdOf(String requestKey) {
        String[] parts = requestKey.substring("satisfaction-result:".length()).split(":");
        if (parts.length != 2 || !parts[0].matches("[1-9][0-9]*")) {
            throw new IllegalStateException("archive source identity conflict");
        }
        return Long.parseLong(parts[0]);
    }

    private boolean sameFiles(List<TemplateFrozenMaterialView> materials, List<SatisfactionResultFileDO> files) {
        if (materials.size() != files.size() || files.isEmpty()) return false;
        Set<String> materialKeys = materials.stream().map(row -> row.fileArtifactId() + "|" + row.fileVersionNo()
                + "|" + row.fileSha256()).collect(Collectors.toSet());
        Set<String> resultKeys = files.stream().map(row -> row.getArtifactId() + "|" + row.getVersionNo()
                + "|" + row.getFileHash()).collect(Collectors.toSet());
        return materialKeys.size() == materials.size() && resultKeys.size() == files.size()
                && materialKeys.equals(resultKeys);
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
}
