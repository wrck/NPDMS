# F-CUS-001 客户主档与本地生命周期

> Feature实施状态：`IMPLEMENTATION_PARTIAL_ACCEPTANCE`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`NOT_READY`
> 当前阻断：CRM 权威四级分类目录为空；平台/临时客户创建、删除成功、恢复和完整截图证据未完成
> 当前任务：`Task 15 已执行，完整门禁仍受仓库既有测试与静态检查失败阻断`
> Requirement ID：`CUS-03`（V1）
> 关联 Requirement：`CUS-01`、`CUS-02`、`CUS-04`、`INT-03`，均不宣称完成
> Feature Spec：`specs/features/F-CUS-001-customer-master-and-local-lifecycle.md`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-cus-001-customer-master-forward-migration.md`
> 锁定规格提交：`8e84ea2ce6750f824973f788237f4790961e59c4`

## 已完成范围

- 建立独立 `pms-module-customer-api` 与 `pms-module-customer`，由 CUS 持有当前客户写 Owner。
- 新管理端资源使用 `/pms/customers`，对外规范语义为 `/api/v1/pms/customers`。
- V87～V89 前向迁移客户主档、分类权限、菜单权限，并保留旧客户 ID。
- PROJ、AST 通过公开 API 提供客户摘要和删除引用守卫，不访问 CUS 业务表或实现层。
- 旧 project 客户页面只保留历史列表与详情，禁止全部旧写操作和写代理。
- 实现五维权限切片、Department code、CRM 四级分类精确校验、联系方式三态裁剪、CAS、幂等、软删除和恢复能力。
- 真实浏览器完成简称更新、停用、删除守卫、旧页只读、刷新持久化及 320/768/1024/1440 响应式验证。
- 真实 MySQL 完成唯一性、CAS、删除/恢复身份保持和未展平权限切片验证。

## 验证证据

- `output/f-cus-001-v18/browser-acceptance.md`
- `output/f-cus-001-v18/database-evidence.md`
- `output/f-cus-001-v18/regression-summary.md`
- Java 定向测试：25/25 通过。
- 真实 MySQL 测试：10/10 通过。
- F-CUS-001 Python 契约：16/16 通过。

## 未完成边界

- `INT-03` CRM 连接、认证、同步、重试、对账和 MarketRelation 目录同步未实现。
- `CUS-01` 用户资产库全景未实现。
- `CUS-02` 客户服务等级时态版本未实现。
- `CUS-04` 项目联系人管理未实现。
- 当前 `cus_market_relation=0`，不得猜测或伪造 CRM 权威编码，因此平台客户和临时客户创建浏览器闭环未完成。
- 当前真实客户存在 PROJ/AST 引用，没有合法无引用客户或已删除客户，删除成功与恢复浏览器闭环未完成。
- Deployment、SIT、UAT、Release 均未完成。
- 规格仓库提交 `8e84ea2` 已同步 Feature Spec、相关 SDS 和 requirement matrix；当前代码工作树已生成 `output/f-cus-001-v18/f-cus-001-spec-repo-writeback.patch`，记录 F-CUS-001 对 `domain-object-table-map`、`domain-entity-migration-contract`、`core-migration-schema-contract` 和 `phase2-contract-map` 的待回写增量。合并时先将补丁应用到规格仓库、替换 `<NPDMS_MERGE_COMMIT>`，完成规格仓库校验与提交，再由官方同步工具更新 NPDMS 受管快照。
- Task 15 定向后端测试、后端编译、Python 契约、CUS 前端范围 lint/format/style 和非压缩前端构建已通过；仓库完整 Maven 测试仍被既有 PROJ MySQL 基线数据与触发器权限阻断，完整 `ts:check` 仍被既有 Iconify 类型和缺失 Vitest 类型阻断，完整 lint 仍有 389 个既有非 CUS 样式错误，标准压缩构建在当前 Windows 环境异常退出。

## 任务跟踪

- [x] Task 1～12：模块、迁移、领域规则、命令、生命周期、查询、跨域 API、旧写退役和工作台实施
- [x] Task 13：当前可执行 MySQL 与浏览器验收证据
- [x] Task 14：规格快照同步、实现基线登记与校验完成；规格机器契约回写仍由规格仓库阻断
- [x] Task 15：最终验证已执行并记录通过项与既有仓库阻断；Implementation Done Gate 保持 `NOT_READY`

本记录不将部分验收解释为完整 Feature Done，不构成 UAT、发布 Gate 或 Release GO。
