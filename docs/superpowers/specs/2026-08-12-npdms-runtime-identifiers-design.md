# NPDMS 运行标识统一设计

> 状态：`APPROVED_BY_REQUESTER`
> 批准方案：A（仅统一技术运行标识）
> 日期：2026-08-12

## 1. 目标

在首次 Git 基线提交前，将新实施仓库的环境变量、默认数据库和运行配置统一为 NPDMS，避免新仓库继续暴露 PMS 旧技术标识。

## 2. 变更规则

| 对象 | 原标识 | 目标标识 |
|---|---|---|
| 环境变量前缀 | `PMS_*` | `NPDMS_*` |
| 默认数据库名 | `pms_platform` | `npdms` |
| Spring Profile | `pms-dev` | `npdms-dev` |
| 本地配置文件 | `application-pms-dev.yaml` | `application-npdms-dev.yaml` |
| E2E/脚本运行变量 | `PMS_E2E_*`、`PMS_CHROME_PATH` 等 | 对应的 `NPDMS_*` |

同步修改范围包括 `.env.example`、Docker Compose、环境生成脚本、后端运行配置、测试脚本和直接说明这些配置的开发文档。

## 3. 明确不变的边界

以下内容属于业务、代码或接口命名，不在本次技术配置统一范围内：

- PRD Requirement ID 和领域编码；
- `pms-module-*` Maven 模块；
- Java 包名 `*.pms.*`；
- `/api/v1/pms` 和现有管理端 API；
- 前端 `views/pms` 路由目录；
- `PMS_PROJECT_STATUS` 等业务字典类型。

上述内容如需改为 NPDMS，应另行开展兼容性和架构迁移评估。

## 4. 安全约束

- `.env` 和本地 `application-npdms-dev.yaml` 不进入 Git；
- 示例配置不包含真实密码、Token、私钥或第三方 API Key；
- Docker Compose 对必需凭据保持缺失即失败，不提供可误用的默认密码。

## 5. 验收标准

1. 运行配置、脚本及测试中不再引用旧 `PMS_*` 环境变量。
2. 默认数据库统一为 `npdms`。
3. Spring 本地开发 Profile 和配置文件统一使用 `npdms-dev`。
4. 业务标识、模块、包名、API 和业务字典未发生变更。
5. Compose 配置校验、仓库配置校验和敏感信息扫描通过。
6. 首次提交仅纳入受版本控制的正式文件，不纳入本地环境、日志、构建产物和过程缓存。
