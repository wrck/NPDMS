# NPDMS Feature 切分方案

> 状态：`ACTIVE`
> 依据：工程链第6/7/10节、PRD V1.7 S0阶段、SDS Baseline（规格快照`b71b5e3`）
> 定位：实现侧拆解资产；业务语义以PRD为准，设计契约以SDS为准，本方案只做切分与排序。

## 1. 切分原则

1. Feature 必须形成可独立验收的业务闭环（工程链第6节）；
2. 依赖先行：被依赖方先建供给端，消费方后建（PM-01 创建硬依赖 PM-03 模板）；
3. 存量面按实现基线清单规则逐 Feature 判定：`BLOCKED_BY_SPEC` 的存量只在对应 Feature 范围内转为可复用、重构、后置或排除；
4. 每个 Feature 落实自身权限（07-authorization）与审计要求，不建立独立"权限/审计 Feature"；
5. `BLOCKED_BY_SPEC`、`RUNTIME_RETIRED` 存量面不得因图省事整体复活。

## 2. 第一条 Vertical Slice（工程链第10节）

| 切片环节 | 归属 | 说明 |
|---|---|---|
| 认证/登录 | 基础平台 | `PLATFORM_UPSTREAM_UNCHANGED`，不建 Feature |
| 客户基础数据 | 项目表快照字段 | 按 ADR-0021，`proj_project` 直接保存客户编码/名称；CUS-01 完整域为 V2 |
| 手动创建项目 | F-PM01 | PM-01 手动场景 |
| 选择项目模板→实例化 | F-PM03→F-PM01 | PM-03 供给端先行；实例化由创建流程执行 |
| 人工指派服务经理 | F-PM01 | PM-01 规则5（V1 人工确认） |
| 项目详情与项目树 | F-PM02 | PM-02 |
| 权限 | 各 Feature 内 | ProjectTreeScope 服务端注入 |
| 审计 | 各 Feature 内 | 版本快照、操作留痕 |

## 3. Feature 顺序与状态

| Feature | 需求 | 范围摘要 | 依赖 | 状态 |
|---|---|---|---|---|
| F-PM03 项目模板基座 | PM-03 | 模板/版本/四维匹配/发布校验/阶段-任务-里程碑-交付件-门禁定义与匹配查询（供给端） | 无 | PLAN_READY |
| F-PM01 项目手动创建 | PM-01 | 手动创建表单、模板选择+默认唯一命中、实例化冻结、S0初始化、人工指派服务经理 | F-PM03 | PLANNED |
| F-PM02 项目树与详情 | PM-02 | 项目树关系、无环移动、按需加载查询、进度汇总口径、项目详情 | F-PM01、F-PM03 | PLANNED |

### 明确后置（不进入首条切片）

- CRM/ERP 自动创建项目：INT-01（V1 后续 Feature）
- 服务经理自动指派：PM-08（V2）
- 项目任务管理全量：PM-11（依赖 F-PM01 实例化后的任务数据）
- 业务属性识别与分类：PM-07；项目回退/关闭：PM-10；转销/合并/多期：PM-05/06（V2）
- 组合管理：`proj_project_portfolio` 已在核心DDL，Feature 后置

## 4. 存量处置原则

| 存量面 | 处置 |
|---|---|
| `pms_project_template`/`pms_phase_template` 及其前后端入口 | 迁移契约为 `NEW_ONLY`（无已证明历史来源）：按 F-PM03 新模型重建，旧表冻结、运行入口退役（参照 V50/V51 先例），菜单逻辑撤销 |
| `pms_project`/`pms_project_team` 等 | F-PM01/F-PM02 范围内逐对象判定：目标表已在核心DDL的走 `CURRENT_FORWARD` 对齐，未在的按 SDS 契约前向迁移新建 |
| 旧 `pms_*` 迁移文件 | 不改已执行迁移；新模型一律新版本号前向迁移 |

## 5. 修订规则

- Feature 状态变化或新增 Feature 时更新本文件；
- 需求矩阵（`docs/traceability/requirement-matrix.md`）为规格仓库受管快照，Feature 状态回写必须先改规格仓库再同步，禁止本地直改。
