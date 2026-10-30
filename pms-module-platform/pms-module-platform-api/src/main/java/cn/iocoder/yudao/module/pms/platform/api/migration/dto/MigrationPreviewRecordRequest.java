package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.optionalText;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.sha256;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.text;

/** 迁移预演单条请求：来源主键 + 可选的待导入内容校验和（用于身份一致性判断）。 */
public record MigrationPreviewRecordRequest(String sourcePk, String sourceChecksum) {

    public MigrationPreviewRecordRequest {
        sourcePk = text(sourcePk, 128, "sourcePk");
        sourceChecksum = optionalText(sourceChecksum, 64, "sourceChecksum");
        if (sourceChecksum != null && !sourceChecksum.matches("[0-9a-f]{64}")) {
            throw MigrationEvidenceContractRules.corrupted("sourceChecksum must be a lowercase sha256 hex digest");
        }
    }
}
