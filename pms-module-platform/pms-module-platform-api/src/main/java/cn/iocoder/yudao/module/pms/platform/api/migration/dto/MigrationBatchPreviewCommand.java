package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.List;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.text;

/** Read immutable reconciliation evidence within an exact Owner batch identity. */
public record MigrationBatchPreviewCommand(Long tenantId, String ownerContextCode, String purposeCode,
        String releaseId, String sourceSystem, String sourceTable, List<MigrationPreviewRecordRequest> records) {
    public MigrationBatchPreviewCommand {
        var preview=new MigrationPreviewCommand(tenantId,sourceSystem,sourceTable,records);
        tenantId=preview.tenantId();sourceSystem=preview.sourceSystem();sourceTable=preview.sourceTable();records=preview.records();
        ownerContextCode=text(ownerContextCode,32,"ownerContextCode");
        purposeCode=text(purposeCode,64,"purposeCode");
        releaseId=text(releaseId,128,"releaseId");
    }
}
