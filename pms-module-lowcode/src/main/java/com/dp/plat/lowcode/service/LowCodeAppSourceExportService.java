package com.dp.plat.lowcode.service;

import com.dp.plat.lowcode.dto.AppSourceManifest;

import java.util.List;

/**
 * 应用源码导出服务（批次5-T10）。
 *
 * <p>借鉴网易轻舟源码导出，将低代码应用（按 bizType 分组的全部配置）打包为
 * 可独立部署的源码 ZIP — 无黑盒引擎，所有配置为可读 JSON，DDL 为标准 SQL，
 * 运行时依赖开源的 {@code pms-lowcode} Maven 模块。</p>
 *
 * <p>ZIP 结构：</p>
 * <pre>
 * app-manifest.json          — 导出元数据
 * entities/{code}.json       — 实体设计（含字段 + 关联）
 * ddl/{mysql,postgresql,sqlserver}.sql  — 多方言 DDL 脚本
 * forms/{code}.json          — 表单配置
 * lists/{code}.json          — 列表配置
 * tabs/{code}.json           — 标签页配置
 * related-pages/{code}.json  — 关联页配置
 * microflows/{code}.json     — 微流定义
 * rules/{code}.json          — 规则定义
 * connectors/{code}.json     — 连接器配置（凭据已脱敏）
 * triggers/{code}.json       — 触发器定义
 * pom.xml                    — 独立部署 Maven 项目 POM
 * README.md                  — 部署说明
 * </pre>
 */
public interface LowCodeAppSourceExportService {

    /**
     * 预览导出清单（不生成 ZIP，仅返回元数据）。
     *
     * @param bizType 业务类型（应用分组键），为 null 时导出全部
     * @return 导出清单（含各类型配置数量统计）
     */
    AppSourceManifest previewManifest(String bizType);

    /**
     * 导出应用源码为 ZIP 字节数组。
     *
     * @param bizType 业务类型（应用分组键），为 null 时导出全部
     * @return ZIP 文件字节数组
     */
    byte[] exportAsZip(String bizType);

    /**
     * 查询可导出的应用列表（按 bizType 去重）。
     *
     * @return bizType 列表（去重）
     */
    List<String> listExportableApps();
}
