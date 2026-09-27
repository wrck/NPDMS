package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewClassification;import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewItem;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewTarget;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationSourceRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationMappingSourceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourceGlobalIdentityQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException.Code.TENANT_CONTEXT_MISMATCH;

/**
 * 迁移预演：对拟迁入的来源身份集合做只读分类，回答"这批数据迁入会发生什么"。
 * 只依据既有迁移证据（来源记录 + 外部键映射）判定，不做按名称猜测合并，不写任何表。
 */
@Service
@RequiredArgsConstructor
public class MigrationPreviewService {

    private final MigrationSourceRecordMapper sourceRecordMapper;
    private final ExternalKeyMappingMapper externalKeyMappingMapper;

    public MigrationPreviewResult preview(MigrationPreviewCommand command) {
        Long runtimeTenantId = TenantContextHolder.getRequiredTenantId();
        if (runtimeTenantId == null || !runtimeTenantId.equals(command.tenantId())) {
            throw new PlatformMigrationEvidenceException(TENANT_CONTEXT_MISMATCH,
                    "explicit tenant does not match trusted context");
        }

        List<String> keys = command.records().stream().map(row -> row.sourcePk()).distinct().toList();
        // 同一身份可能跨多个批次出现过：取最大 sourceRecordId 作为最新事实。
        Map<String, MigrationSourceRecordDO> latestByKey = new HashMap<>();
        for (MigrationSourceRecordDO row : sourceRecordMapper.selectListByGlobalIdentity(
                new MigrationSourceGlobalIdentityQuery(command.tenantId(), command.sourceSystem(),
                        command.sourceTable(), keys))) {
            latestByKey.merge(row.getSourceRecordKey(), row,
                    (existing, candidate) -> candidate.getId() > existing.getId() ? candidate : existing);
        }

        Set<Long> latestIds = new HashSet<>();
        latestByKey.values().forEach(row -> latestIds.add(row.getId()));
        Map<Long, List<ExternalKeyMappingDO>> mappingsBySourceRecordId = new HashMap<>();
        if (!latestIds.isEmpty()) {
            for (ExternalKeyMappingDO mapping : externalKeyMappingMapper.selectListBySourceRecordIds(
                    new MigrationMappingSourceQuery(command.tenantId(), new ArrayList<>(latestIds)))) {
                mappingsBySourceRecordId.computeIfAbsent(mapping.getSourceRecordId(), ignored -> new ArrayList<>())
                        .add(mapping);
            }
        }

        List<MigrationPreviewItem> items = new ArrayList<>(command.records().size());
        long noExistingSourceCount = 0L;
        long checksumConflictCount = 0L;
        long unmappedCount = 0L;
        long retainedCount = 0L;
        long mappedCount = 0L;
        long ambiguousCount = 0L;
        for (var request : command.records()) {
            MigrationSourceRecordDO latest = latestByKey.get(request.sourcePk());
            if (latest == null) {
                noExistingSourceCount++;
                items.add(new MigrationPreviewItem(request.sourcePk(),
                        MigrationPreviewClassification.NO_EXISTING_SOURCE, null, null, null, List.of()));
                continue;
            }
            if (request.sourceChecksum() != null && !request.sourceChecksum().equals(latest.getSourceChecksum())) {
                checksumConflictCount++;
                items.add(new MigrationPreviewItem(request.sourcePk(),
                        MigrationPreviewClassification.CHECKSUM_CONFLICT, latest.getId(), latest.getBatchId(),
                        latest.getSourceChecksum(), List.of()));
                continue;
            }
            List<ExternalKeyMappingDO> mappings = mappingsBySourceRecordId.getOrDefault(latest.getId(), List.of());
            MigrationPreviewClassification classification = classify(mappings);
            switch (classification) {
                case UNMAPPED -> unmappedCount++;
                case RETAINED -> retainedCount++;
                case AMBIGUOUS -> ambiguousCount++;
                default -> mappedCount++;
            }
            items.add(new MigrationPreviewItem(request.sourcePk(), classification, latest.getId(),
                    latest.getBatchId(), latest.getSourceChecksum(), toTargets(mappings)));
        }
        return new MigrationPreviewResult(command.sourceSystem(), command.sourceTable(), items,
                noExistingSourceCount, checksumConflictCount, unmappedCount, retainedCount, mappedCount,
                ambiguousCount);
    }

    private MigrationPreviewClassification classify(List<ExternalKeyMappingDO> mappings) {
        if (mappings.isEmpty()) {
            return MigrationPreviewClassification.UNMAPPED;
        }
        List<ExternalKeyMappingDO> mapped = mappings.stream()
                .filter(mapping -> "MAPPED".equals(mapping.getResultType()))
                .toList();
        if (mapped.isEmpty()) {
            return MigrationPreviewClassification.RETAINED;
        }
        // 按 targetRole 归并：同角色下出现多个不同目标即为歧义；跨角色的多目标（如主备）不算歧义。
        Map<String, Set<String>> targetsByRole = new LinkedHashMap<>();
        for (ExternalKeyMappingDO mapping : mapped) {
            targetsByRole.computeIfAbsent(mapping.getTargetRole(), ignored -> new HashSet<>())
                    .add(mapping.getTargetContext() + "/" + mapping.getTargetObjectType() + "/"
                            + mapping.getTargetTable() + "/" + mapping.getTargetId());
        }
        boolean ambiguous = targetsByRole.values().stream().anyMatch(targets -> targets.size() > 1);
        return ambiguous ? MigrationPreviewClassification.AMBIGUOUS : MigrationPreviewClassification.MAPPED;
    }

    private List<MigrationPreviewTarget> toTargets(List<ExternalKeyMappingDO> mappings) {
        return mappings.stream()
                .map(mapping -> new MigrationPreviewTarget(mapping.getResultType(), mapping.getTargetContext(),
                        mapping.getTargetObjectType(), mapping.getTargetTable(), mapping.getTargetId(),
                        mapping.getTargetRole(), mapping.getTargetSequence(), mapping.getResultKey()))
                .toList();
    }
}
