package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入冲突检测结果 DTO（批次5-T3，借鉴 Appsmith Git 导入冲突解决）。
 *
 * <p>导入配置包前，对每个 item 检测目标环境是否已有同 configCode 的版本：
 * <ul>
 *   <li>无冲突：目标环境无此 configCode 或无 ACTIVE 版本，直接导入</li>
 *   <li>有冲突：目标环境已有 ACTIVE 版本，需用户选择解决方式</li>
 * </ul></p>
 *
 * <p>解决方式（Resolution）：
 * <ul>
 *   <li>KEEP_SOURCE: 保留源版本（用导入包的快照覆盖目标环境）</li>
 *   <li>KEEP_TARGET: 保留目标版本（跳过此项导入）</li>
 *   <li>SKIP: 跳过此项（不导入也不保留）</li>
 * </ul></p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportConflictDTO {
    /** 配置包来源环境 */
    private String sourceEnvironment;
    /** 目标环境 */
    private String targetEnvironment;
    /** 冲突项列表 */
    @Builder.Default
    private List<ConflictItem> conflicts = new ArrayList<>();
    /** 无冲突项数（可直接导入） */
    private int noConflictCount;
    /** 总项数 */
    private int totalCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictItem {
        /** 配置类型 */
        private String configType;
        /** 配置 ID */
        private Long configId;
        /** 配置编码 */
        private String configCode;
        /** 源环境版本号 */
        private Integer sourceVersion;
        /** 源环境变更说明 */
        private String sourceChangeLog;
        /** 源环境操作人 */
        private String sourceCreateBy;
        /** 源环境创建时间 */
        private String sourceCreateTime;
        /** 目标环境版本号（已有） */
        private Integer targetVersion;
        /** 目标环境变更说明 */
        private String targetChangeLog;
        /** 目标环境操作人 */
        private String targetCreateBy;
        /** 目标环境创建时间 */
        private String targetCreateTime;
        /** 用户选择的解决方式（KEEP_SOURCE/KEEP_TARGET/SKIP），默认 null 表示待选择 */
        private String resolution;
    }
}
