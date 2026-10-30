---
feature: visio-delivery-template-completion
status: in-progress
updated: 2026-09-23
branch: fix/visio-template-completion
commits: 3f7c9906e..(pending)
---

# 流程图项目模板收尾：修复发布阻塞并完成 S0~S5 全流程验收

## Report

## [S1] Problem

`TPL-DELIVERY-FLOW-20260922`（ID 993009900014）按 Visio 交付流程图 6 页配置的模板内容已完整（6 阶段 / 17 任务 / 17 交付件 / 5 门禁 / 39 规则，V16 证据预检通过，V17 与 V16 逐字段一致），但仍是 DRAFT、未发布、不可用于新项目匹配——"完整可运行模板"目标未达成。交接后实测发布预检出现 8 项失败（4×`RULE_BUSINESS_FACT_UNAVAILABLE`、4×`RULE_BUSINESS_BINDING_UNAVAILABLE`），涉及四个业务结果事实：`PROJECT_ASSIGNMENT_COMPLETED`、`CONSTRUCTION_PLAN_APPROVED`、`IMPLEMENTATION_SOLUTION_APPROVED`、`CUTOVER_COMPLETED`。

根因已定位：59191 端口运行的后端是 2026-09-21 22:30 构建的旧 jar，早于今日 16:42 合入的接入提交 `273621cee`（四个 Completion/BusinessView Provider 位于 pms-module-project / -engineering / -cutover，HEAD 均存在）。发布阻塞的本质是运行时陈旧，不是模板配置缺陷。

## [S2] Design

- 独立修复分支 `fix/visio-template-completion`（自 `codex/domain-migration` HEAD `3f7c9906e`），不按工程实施链（DU/Feature 晋级）进行。
- 运行环境：容器 `npdms-domain-test` 不变（MySQL 24306 / Redis 24379 / 库 `npdms_domain_test` / 租户 1）；后端改为宿主机 **59291**、前端 **19291**，均从本 worktree HEAD 构建/启动；旧 19191/59191 进程属主仓库，不触碰。
- 步骤：Flyway validate/migrate → 构建 yudao-server → 启动 59291/19291 → `POST /api/v1/pms/project-templates/993009900014/actions/validate` 预检 → 通过后 `POST .../actions/publish` 发布。
- 验收口径（用户已确认）：发布成功后，用真实项目完整走通 **直签** 与 **非直签** 两条 S0~S5 路径，节点完成依据全部来自原模块真实业务事实与人工提交，不绕过状态机、不直改生命周期状态；非直签路径必须体现跳过初验/终验与到货验收、培训直接进入的模板分流。
- 若新后端预检仍失败，则按暴露的具体问题修复（代码或模板配置），修复后回归预检。
- 交付记录延续 `docs/generated/2026-09-22-visio-delivery-template/` 目录惯例，本轮证据写入该目录新子目录，不改写历史证据。

## [S3] Out of Scope

- 旧模板 910001/910002/910003 的 `exact definitionRevisionId required` 列表报错：既有历史数据兼容问题，本轮不修（用户已确认）。
- 不修改 PRD/SDS 业务语义，不新增阶段（S6 仍不做）、不自动关闭项目。
- 不触碰主仓库 E 盘的 19191/59191 进程及其 `.run` 产物。
- 不为验收降低任何校验、授权或状态机约束；工程管理部复审等分级审批能力保持原样。

## Tasks

- [ ] T1: 环境就绪 — acceptance: 59291 `/actuator/health` UP、19291 前端可访问、Flyway validate 通过且无待迁移（covers: S2）
- [ ] T2: 预检复验 — acceptance: 新后端 validate 返回 `valid:true` 且 issues 为空；规则模拟通过（covers: S2）
- [ ] T3: 发布模板 — acceptance: publish 成功，发布修订数 ≥1，模板进入已发布状态；原有 25+ 模板版本与状态不变（covers: S2; depends: T2）
- [ ] T4: 直签项目 S0~S5 全流程 — acceptance: 新建真实项目命中本模板，S0 指派→S1 工前四任务→S2 施工计划审批→S3 方案审核→S4 部署汇合（含割接成功归档）→S5 初验/终验/培训/满意度全部按规则推进完成，全程使用原模块业务事实（covers: S2; depends: T3）
- [ ] T5: 非直签项目 S0~S5 全流程 — acceptance: 非直签项目跳过到货验收与初验/终验、由客户协调直接进入培训与满意度，其余同 T4（covers: S2; depends: T3）
- [ ] T6: 收尾评审与交付记录 — acceptance: 验证命令与结果记录完整，评审通过，特性文档置 delivered（covers: S2; depends: T4, T5）
