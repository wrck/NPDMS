package cn.iocoder.yudao.module.pms.platform.api.export;

import java.util.List;

public record ExportBusinessDataSnapshot(
        String outcome,
        String normalizedFilter,
        String scopeSnapshot,
        List<String> allowedFields,
        boolean includeFiles,
        Long scopeVersion,
        List<List<String>> rows) {
}
