package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.completeList;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.positive;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.text;

/** 迁移预演命令：对同一来源身份集合做只读分类，不写入任何证据表。 */
public record MigrationPreviewCommand(
        Long tenantId,
        String sourceSystem,
        String sourceTable,
        List<MigrationPreviewRecordRequest> records) {

    public MigrationPreviewCommand {
        tenantId = positive(tenantId, "tenantId");
        sourceSystem = text(sourceSystem, 32, "sourceSystem");
        sourceTable = text(sourceTable, 64, "sourceTable");
        records = List.copyOf(completeList(records, "records"));
        if (records.isEmpty() || records.size() > 1000) {
            throw MigrationEvidenceContractRules.invalid("records size must be 1..1000");
        }
        // 允许同一 sourcePk 携带不同 sourceChecksum 重复出现：这正是校验和冲突的探测方式。
    }
}
