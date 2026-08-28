# F-CUS-001 客户主档与本地生命周期

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION_COMPLETE`
> Feature Ready Gate：`PASS`
> Technical Plan Gate：`EXECUTED / 用户确认六项闭环决策`
> Implementation Done Gate：`PASS / NPDMS-FCUS001-IMPLEMENTATION-DONE-20260828-01`
> 当前阻断：`无`
> 当前任务：`F-CUS-001已完成；按工程链定位下一Feature`
> Requirement ID：`CUS-03`（V1）
> 关联 Requirement：`CUS-01`、`CUS-02`、`CUS-04`、`INT-03`，均不宣称完成
> Feature Spec：`specs/features/F-CUS-001-customer-master-and-local-lifecycle.md`
> Technical Plan：`docs/superpowers/plans/2026-08-28-f-cus-001-customer-master-implementation-closure.md`
> 锁定规格提交：`18377fd9fc45b54217b21488cfb46a8d320d4bd8`

## 已完成范围

- 建立独立 `pms-module-customer-api` 与 `pms-module-customer`，由 CUS 持有当前客户写 Owner。
- 新管理端资源使用 `/pms/customers`，对外规范语义为 `/api/v1/pms/customers`。
- V106～V108 前向迁移客户主档、分类权限、菜单权限，并保留旧客户 ID。
- PROJ、AST 通过公开 API 提供客户摘要和删除引用守卫，不访问 CUS 业务表或实现层。
- 旧 project 客户页面只保留历史列表与详情，禁止全部旧写操作和写代理。
- 实现五维权限切片、Department code、CRM 四级分类精确校验、联系方式三态裁剪、CAS、幂等、软删除和恢复能力。
- 真实浏览器完成简称更新、停用、删除守卫、旧页只读、刷新持久化及 320/768/1024/1440 响应式验证。
- 真实 MySQL 完成唯一性、CAS、删除/恢复身份保持和未展平权限切片验证。
- V123受控验收种子补齐透明目录、权限负向、跨租户、删除守卫及正向验收前提，不预置客户主档事实。
- 业务创建入口移除`CRM_SYNC`；平台临时客户强制填写原因并持久化待对账状态。
- 已删除客户可显式筛选和恢复；创建、更新及生命周期命令按用户意图稳定复用幂等键。

## 验证证据

- `output/f-cus-001-v18/browser-acceptance.md`
- `output/f-cus-001-v18/database-evidence.md`
- `output/f-cus-001-v18/regression-summary.md`
- `docs/engineering/evidence/f-cus-001-browser-evidence.json`
- `output/f-cus-001-v18/browser-current/result.json`
- Java 定向测试：25/25 通过。
- 真实 MySQL 测试：10/10 通过。
- F-CUS-001 Python 契约：16/16 通过。
- 合并后回归：38模块Reactor测试、隔离MySQL定向测试、前端Vitest、`ts:check`及生产构建通过。
- 真实浏览器：平台/临时客户创建、稳定幂等重放、删除/恢复、只读与空范围拒绝、跨租户隔离、引用守卫、`super_admin`保留及四档响应式全部PASS，控制台、页面及网络错误为0。

## 未完成边界

- `INT-03` CRM 连接、认证、同步、重试、对账和 MarketRelation 目录同步未实现。
- `CUS-01` 用户资产库全景未实现。
- `CUS-02` 客户服务等级时态版本未实现。
- `CUS-04` 项目联系人管理未实现。
- Deployment、SIT、UAT、Release 均未完成。
- 正式规格仓库提交`18377fd9`已将F-CUS-001、CUS-03与F-AST-001标记为`IMPLEMENTATION_COMPLETE`，并由官方同步工具锁定到本地快照。
- 完整Python契约套件仍报告P3-E09冻结工件、V1.8载体计数和既有追踪材料漂移；这些既有规格治理问题不属于F-CUS-001完成声明，已保留原始失败，不以本Feature修改掩盖。
- 用户现有后端进程占用`yudao-server.jar`，Spring Boot repackage无法重命名该文件；不停止用户进程。38模块Reactor测试、编译和前端生产构建均已通过。

## 任务跟踪

- [x] Task 1～12：模块、迁移、领域规则、命令、生命周期、查询、跨域 API、旧写退役和工作台实施
- [x] Task 13：当前可执行 MySQL 与浏览器验收证据
- [x] Task 14：规格仓库回写、提交及官方快照同步完成
- [x] Task 15：最终验证、合并后代码审查和Implementation Done回写完成

本记录确认F-CUS-001本地客户主档Feature已完成，但不构成关联Requirement、UAT、发布Gate或Release GO。

> 检查点（2026-08-28）：六项用户确认决策已落实，CUS阻断全部消除；代码审查未发现Required/Critical问题，F-CUS-001与F-AST-001在正式规格仓库及锁定快照中均为`IMPLEMENTATION_COMPLETE`。
