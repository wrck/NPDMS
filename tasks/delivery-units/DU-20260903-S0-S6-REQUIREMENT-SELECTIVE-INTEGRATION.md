# DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION S0～S6需求标记选择性集成

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-PROJ-008=TASK_COORDINATED;F-IMP-001=TASK_COORDINATED;F-IMP-002=FEATURE_EXCLUSIVE`
> Task范围：`审查2026-08-21之后未进入master、但已按S0～S6正式Requirement实施的代码；按Requirement和Task边界选择性接收，不以来源Task状态NOT_STARTED否定已存在实现，也不把局部实现倒签为Feature Done`
> Owner：`Codex S0～S6需求实现防遗漏集成会话`
> 分支：`codex/s0-s6-requirement-selective-integration-20260903`
> 认领基线：`master@33b621065d88b6f2abc1193b46e8ac6aaad49855`
> 修改边界：`tasks/features/F-PROJ-008.md;tasks/delivery-units/DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION.md;yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/**;F-IMP-001/F-IMP-002后续独立选择性集成边界`
> 验证：`Requirement标记；来源提交文件边界；来源父版本与当前master Blob一致性；组件测试和构建证据；不导入未裁决业务语义`
> 集成记录：`PM-03@V1/F-PROJ-008 Task 3A从a3bd0043接收阶段门禁工作台、前端API和组件测试；Q-FPROJ-009继续仅阻断首次PROJECT_MANAGER指派及真实S0→S1闭环。F-IMP-001/F-IMP-002按EXE-01～06继续逐文件复核，不因来源状态标签排除已实现代码。`

## 判定原则

- 代码、迁移、测试与可重复证据优先用于判断“是否已实施”；Feature Task标签只决定正式治理状态，不作为代码存在性的反证。
- 已实现且不依赖未裁决语义的切片可直接进入master，并在Task中准确记录其完成边界。
- 未完成端到端闭环只阻断相关验收和Feature Done，不自动回退已经完成的独立代码切片。
- 来源分支中的生成投影、旧Flyway编号、重复祖先、生产Fake及未批准PRD语义不得随代码一并进入master。

## 本次已接收：F-PROJ-008 Task 3A

- Requirement：`PM-03@V1=PARTIAL`。
- 来源：`codex/f-proj-008-stage-advance@a3bd0043`。
- 接收：阶段准备度/流程定义/流程启动/相邻推进前端API；`ProjectStageGatePanel.vue`；组件测试；项目详情页接入。
- 兼容性：来源父提交`d69b3ff8`的两个既有前端文件与`master@33b62106` Blob一致，故该切片可无冲突移植。
- 排除：来源对`open-questions.md`的历史写入；首次项目经理指派命令；S0→S1真实Chromium完成证明；任何Feature Done转记。

## 后续复核边界

- `F-IMP-001 / EXE-06@V1=PARTIAL`：公开合同、AST Owner支撑和消费适配须按实际文件与master现状复核，已实现部分不得继续记为纯NOT_STARTED。
- `F-IMP-002 / EXE-01@V1=FULL`：来源Task 1～11存在实际实现；须将IMP自有代码、前端、测试与迁移从混合CUT分支选择性迁入，并将旧V133～V142迁移重排到master当前V192之后；Task 12生产装配和真实浏览器继续单独判定。
- S1/S2/S3/S5/S6已进入master的PRE/SOL/PROJ/ACC/CLO代码不重复合入；后续仅对Branch-only文件差量和Requirement归属做遗漏扫描。
