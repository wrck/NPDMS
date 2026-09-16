package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用源码导出清单（批次5-T10）。
 *
 * <p>记录一次导出操作的元数据，作为 ZIP 包内 {@code app-manifest.json} 的内容。
 * 借鉴网易轻舟源码导出 — 无黑盒引擎，所有配置均为可读 JSON + 标准 SQL DDL，
 * 运行时依赖 {@code pms-lowcode} 开源 Maven 模块。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSourceManifest {

    /** 导出格式版本 */
    @Builder.Default
    private String manifestVersion = "1.0";

    /** 应用标识（bizType 作为应用分组键） */
    private String appCode;

    /** 应用名称（取 bizType 首字母大写或自定义） */
    private String appName;

    /** 描述 */
    private String description;

    /** 导出时间 */
    private LocalDateTime exportTime;

    /** 导出人 */
    private String exportBy;

    /** 源系统标识 */
    @Builder.Default
    private String sourceSystem = "PMS-LowCode";

    /** 低代码平台版本 */
    private String platformVersion;

    /** 各类型配置数量统计（configType → count） */
    private Map<String, Integer> configCounts;

    /** 实体表名列表（用于 DDL 导出范围说明） */
    private java.util.List<String> entityTables;

    /** 是否已脱敏连接器凭据 */
    @Builder.Default
    private boolean credentialsRedacted = true;

    /** 独立部署说明（写入 README） */
    @Builder.Default
    private String deploymentGuide = "无黑盒引擎部署：解压后执行 mvn package 生成可部署 WAR/JAR，"
            + "DDL 脚本在 ddl/ 目录下按目标数据库选择执行，配置 JSON 导入目标环境的低代码平台即可。";
}
