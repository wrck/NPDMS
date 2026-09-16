package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 版本 Diff 对比结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDiffDTO {
    private Integer fromVersion;
    private Integer toVersion;
    private List<DiffEntry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffEntry {
        /** 变更类型: ADDED/REMOVED/MODIFIED */
        private String changeType;
        /** 字段路径（如 entity.name / fields[0].label） */
        private String fieldPath;
        private String oldValue;
        private String newValue;
    }
}
