package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record MigrationSourceRecordPage(
        List<MigrationSourceRecordFact> records,
        Long nextAfterSourceRecordId) {

    public MigrationSourceRecordPage {
        try {
            records = completeList(records, "records").stream()
                    .sorted(Comparator.comparing(MigrationSourceRecordFact::sourceRecordId))
                    .toList();
            if (nextAfterSourceRecordId != null) {
                nextAfterSourceRecordId = positive(nextAfterSourceRecordId, "nextAfterSourceRecordId");
            }
        } catch (cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException ex) {
            throw corrupted(ex.getMessage());
        }
        if (records.isEmpty() && nextAfterSourceRecordId != null) {
            throw corrupted("empty page forbids next cursor");
        }
        if (!records.isEmpty() && nextAfterSourceRecordId != null
                && !nextAfterSourceRecordId.equals(records.getLast().sourceRecordId())) {
            throw corrupted("next cursor must equal the last sourceRecordId");
        }
    }
}
