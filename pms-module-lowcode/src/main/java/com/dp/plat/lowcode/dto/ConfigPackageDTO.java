package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 配置包（用于环境晋升）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigPackageDTO {
    private String sourceEnvironment;
    private String targetEnvironment;
    private List<PackageItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageItem {
        private String configType;
        private Long configId;
        private String configCode;
        private Integer version;
        private String snapshot;
    }
}
