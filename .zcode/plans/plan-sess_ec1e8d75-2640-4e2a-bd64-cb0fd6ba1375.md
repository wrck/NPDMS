# 按 PRD v1.8 依次实现 S0~S6 全部阶段（仅 V1 需求，绕开 Q-FPROJ-009）

## 现状结论（调研结果）
- SDS 分册（docs/design/00~20）齐全；17 个既有 Feature Spec 全部 READY。
- **S0 已基本完成**：F-PROJ-001~007、F-SOL-001~003、F-CUS-001、F-PLT-001/002 已 IMPLEMENTATION_COMPLETE/DONE。
- **S1 需求（PRE-01/02/04）已实现**；SOL-01@V2 本批次不做。
- **S2~S6 的 V1 需求全部无 Feature Spec、无实施链**（pms-module-engineering/cutover 里的 Controller 属遗留脚手架，不构成需求级完成）：
  - S2：PLN-01~04（P0×2、P1×2）
  - S3：SCH-01、SCH-05（均 P0）
  - S4：EXE-01~06（P0×5、P1×1）
  - S5：ACC-01~04（均 P0）
  - S6：CLO-01~02（均 P0）
  - 关联承载：CUT-01~07/09/10（V1 部分；F-CUT-001 在途）
- 阻断：Q-FPROJ-009（阻断 F-PROJ-008 Task 3）、Q-GOV-20260901-001（PRD 修订编号冲突，阻断部分分支合入）——保持 BLOCKED，只推进不依赖它们的独立需求。

## 执行顺序（每波一个或多个 DU，串行推进）

### Wave 0：在途收尾（S0 前置闭环）
1. F-COM-001（合同订单关联与交付范围，DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE 已 CLAIMED）：完成剩余实施与集成回执。
2. F-CUT-001（CUT-07/09/10 配置基础）：完成 V133 示例迁移与最终 DoD。
3. F-AST-001：完成 REVALIDATION。
4. F-PROJ-008：完成除 Task 3（被 Q-FPROJ-009 阻断）外的任务。

### Wave 1~5：S2→S3→S4→S5→S6 逐阶段实现
每个需求的统一协议（遵循 READ→PLAN→IMPLEMENT→TEST→SELF-REVIEW→REPORT）：
1. **规格链先行**：按需修订对应 SDS 分册（领域模型/状态机/API/数据库等）→ 编写 `specs/features/F-*.md` Feature Spec（含 Requirement@V1 覆盖声明与 physical-contract.json）→ 修订需登记 PRD change-log 时不改业务语义（无批准变更不动 PRD）。
2. **DU 认领**：先在 master 提交 `tasks/delivery-units/DU-*.md` 认领，再从 master 建实施分支包含该认领提交。
3. **实现**：审计 pms-module-engineering/cutover 遗留代码，按"可复用/复制增强/不可复用"逐项判定（旧类旧接口保持不变）；后端按 `/api/v1/pms/...` 规范、Mapper 遵守 `docs/coding/database-query-interface.md`；前端在 `yudao-ui/yudao-ui-admin-vue3/src/{api,views}/pms/` 对应域实现页面。
4. **测试与验收**：单测 + 真实浏览器 UI 闭环验收（编译/静态页面不作数）。
5. **种子数据**：按 SDS 与组合维度覆盖（精确命中/部分限定/优先级让位/无匹配/停用不参与）补充迁移。
6. **投影更新**：更新 tasks/features 状态、docs/traceability 矩阵（用 scripts/generate_requirement_traceability.py 等脚本再生成）。

波次内容：
- **Wave 1 (S2)**：PLN-01 施工计划自动推算 → PLN-04 施工计划审批 → PLN-02 工期紧张预警 → PLN-03 超期标红与统计
- **Wave 2 (S3)**：SCH-01 实施方案在线编审 → SCH-05 方案审核与重大复审
- **Wave 3 (S4)**：EXE-01 到货签收 → EXE-02 硬件安装记录 → EXE-03 配置Log采集解析 → EXE-04 业务联调配置收集 → EXE-05 单机风险标记 → EXE-06 割接上线门禁（依赖 CUT-01~06，如 CUT 各项无 Spec 则一并建链；QUARANTINED 的 codex/f-cut-001-matrices 分支候选按 DU 规则重新认领评估）
- **Wave 4 (S5)**：ACC-01 现场培训电子化 → ACC-02 满意度收集 → ACC-03 验收报告 → ACC-04 交付件归档
- **Wave 5 (S6)**：CLO-01 闭环条件校验 → CLO-02 项目闭环审批

## 全局约束
- 仅实现 V1 需求；V2（SOL-01、PRE-03/05、SCH-02~04、IMP-01、ACC-06、INS-01~09、SUB-01~05、RES-01、CUT-08）与本批次 V3/OUT_OF_SCOPE 一律不做。
- 不臆造业务语义：遇到规则缺失即标 BLOCKED_BY_SPEC 登记到 docs/decisions/open-questions.md，继续独立工作。
- 不绕过状态机、授权、数据范围；不降低校验以通过测试。
- 当前分支 docs/prd-v1.8-p0-p1-fixes 为文档分支，实施前确认其合入状态并从 master 起新 DU 分支。

## 风险与说明
- 这是跨多批次的大工程，按波次推进，每波完成后汇报并更新追溯投影；若单会话内未全部完成，剩余波次在后续会话按本计划继续。
- Q-FPROJ-009 关闭前 S0 阶段门完整闭环缺 Task 3，不影响 S2~S6 独立推进。
- Q-GOV-20260901-001 可能阻断涉及 ACC/INS/AST/PROJ 重复修订编号分支的合入，涉及 S5 时如遇冲突按该 open question 的裁决流程处理。