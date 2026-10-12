package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.List;

/** One source identity, with concrete Owner entities or an explicit retained-history reason. */
public record NativeEntityMigrationRow(String domain, String sourceKey, List<ExternalTargetMapping> targets,
                                       String reasonCode) {
    public NativeEntityMigrationRow {
        targets = List.copyOf(targets);
        if (targets.isEmpty() == (reasonCode == null)) {
            throw new IllegalArgumentException("A native result must have targets or a retained reason");
        }
    }

    public static NativeEntityMigrationRow mapped(String domain, String key, ExternalTargetMapping... targets) {
        return new NativeEntityMigrationRow(domain, key, List.of(targets), null);
    }

    public static NativeEntityMigrationRow retained(String domain, String key, String reason) {
        return new NativeEntityMigrationRow(domain, key, List.of(), reason);
    }
}
