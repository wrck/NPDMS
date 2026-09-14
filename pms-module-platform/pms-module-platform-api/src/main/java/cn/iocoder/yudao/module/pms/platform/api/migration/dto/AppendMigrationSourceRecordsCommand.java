package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.HashSet;
import java.util.List;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

/** Atomic bounded append; existing source identities must replay identical immutable facts. */
public record AppendMigrationSourceRecordsCommand(List<AppendMigrationSourceRecordCommand> records) {
    public AppendMigrationSourceRecordsCommand {
        records = completeList(records, "records");
        if (records.isEmpty() || records.size() > 1000) throw invalid("records size must be 1..1000");
        var first = records.getFirst();
        var keys = new HashSet<String>();
        for (var row : records) {
            if (!first.tenantId().equals(row.tenantId()) || !first.batchId().equals(row.batchId())
                    || !first.sourceSystem().equals(row.sourceSystem()) || !first.sourceTable().equals(row.sourceTable())
                    || !keys.add(row.sourcePk())) throw invalid("source page must contain distinct identities in one batch");
        }
    }
    public Long tenantId() { return records.getFirst().tenantId(); }
    public Long batchId() { return records.getFirst().batchId(); }
}
