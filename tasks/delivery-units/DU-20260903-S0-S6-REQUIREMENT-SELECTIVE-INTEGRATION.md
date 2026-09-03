# DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION S0～S6需求标记选择性集成

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-PROJ-008=TASK_COORDINATED;F-IMP-001=TASK_COORDINATED;F-IMP-002=FEATURE_EXCLUSIVE`
> Task范围：`审查2026-08-21之后未进入master、但已按S0～S6正式Requirement实施的代码；按Requirement和Task边界选择性接收，不以来源Task状态NOT_STARTED否定已存在实现，也不把局部实现倒签为Feature Done`
> Owner：`Codex S0～S6需求实现防遗漏集成会话`
> 分支：`codex/s0-s6-requirement-selective-integration-20260903`
> 认领基线：`master@33b621065d88b6f2abc1193b46e8ac6aaad49855`
> 来源截点：`F-PROJ-008=a3bd00438d8be9bdd18f90802c7370af4152efdd;F-IMP-001/F-IMP-002=eda54bd0c911641c0d977288ee63b3a1df81e69d`
> 验证：`Requirement标记；来源提交文件边界；来源父版本与当前master Blob一致性；依赖闭包；迁移版本唯一性；来源测试与独立Gate证据；不导入未裁决业务语义`

## 判定原则

- 代码、迁移、测试与可重复证据用于判断“是否已实施”；Feature Task标签只决定正式治理状态，不作为代码存在性的反证。
- 已实现且不依赖未裁决语义的切片可以进入master，并在Task中准确记录完成边界。
- 未完成端到端闭环只阻断相关验收和Feature Done，不自动回退已经完成的独立代码切片。
- 来源分支中的生成投影、旧Flyway编号、重复祖先、生产Fake及未批准PRD语义不得随代码一并进入master。

## S0～S6逐阶段扫描结果

| 阶段 | 主要正式能力 | 结果 |
|---|---|---|
| S0 | 项目启动、阶段Gate、首次推进 | 发现并接收`F-PROJ-008 Task 3A`阶段门禁工作台；首次PROJECT_MANAGER指派与真实S0→S1继续受`Q-FPROJ-009`阻断 |
| S1 | 准备、工勘与计划 | PRE/SOL已在master，未发现新的branch-only可独立接收代码 |
| S2 | 需求分析 | SOL/PROJ相关实现已在master，未发现新的branch-only可独立接收代码 |
| S3 | 实施方案 | SOL及流程绑定实现已在master，未发现新的branch-only可独立接收代码 |
| S4 | 到货、安装、配置、联调与实施就绪 | 发现`F-IMP-001`AST支撑和`F-IMP-002 Task 1～11`整体漏接；本DU选择性接收。F-IMP-003～005仍只有规格/前置合同，不倒签实现 |
| S5 | 验收 | ACC代码此前已选择性进入master；本DU不重复合入来源分支 |
| S6 | 正常闭环/异常关闭 | PROJ/CLO现有实现已在master；未发现新的branch-only可独立接收代码 |

## 已接收一：F-PROJ-008 Task 3A

- Requirement：`PM-03@V1=PARTIAL`。
- 来源：`codex/f-proj-008-stage-advance@a3bd0043`。
- 接收：阶段准备度/流程定义/流程启动/相邻推进前端API；`ProjectStageGatePanel.vue`；组件测试；项目详情页接入。
- 兼容性：来源父提交`d69b3ff8`的两个既有前端文件与`master@33b62106` Blob一致，可无冲突移植。
- 排除：来源对Open Question的历史写入；首次项目经理指派命令；S0→S1真实Chromium完成证明；任何Feature Done转记。

## 已接收二：F-IMP-001 AST物理Owner支撑

- Requirement：`EXE-06@V1=PARTIAL`的支撑Task，不宣称EXE-06完成。
- 接收：`DeviceScopeFactApi`及DTO、稳定异常、AST生产Provider、查询对象、Mapper/XML增量、事务执行器、合同/单元/Mapper/MySQL候选测试。
- 依赖闭包：master已存在PROJ系统资格Provider、COM当前已分配范围Provider及平台文件能力；不重复导入来源实现。
- 排除：`ImplementationReadinessSnapshot`核心聚合、生产Provider、REST/UI和Feature Done。

## 已接收三：F-IMP-002 Task 1～11

- Requirement：`EXE-01@V1=FULL`。
- 接收：公开事实API、到货领域与应用代码、五表持久化、Mapper/XML、REST候选、前端工作台、组件/单元/Mapper/MySQL候选测试、机器合同和Technical Plan。
- 状态：`IMPLEMENTATION_TASKS_1_TO_11_COMPLETE / TASK12_PENDING`。
- 生产边界：Controller继续不注册生产组件；三个Job继续PAUSED；不注册Fake/fallback。
- 平台合并：在master现有Outbox支持事件集合上追加`ImplementationEvidencePublished`，不覆盖`AcceptanceReportVersionChanged`、`SatisfactionTaskCreated`和`SatisfactionResultVersionChanged`。

### Flyway重排

| 来源 | master |
|---|---|
| V133 | V193 |
| V134 | V194 |
| V135 | V195 |
| V136 | V196 |
| V137 | V197 |
| V138 | V198 |
| V139 | V199 |
| V140 | V200 |
| V141 | V201 |
| V142 | V202 |

迁移内容Blob保持来源不变，仅重排路径；迁移合同测试同步读取V193～V202。旧V133～V142未接收，避免在master V192之后被Flyway视为低版本迁移。

## 明确排除

- `codex/f-cut-001-matrices`中的CUT、COM、PLT重复祖先和生成追溯投影；
- F-IMP-003、F-IMP-004、F-IMP-005只有规格或前置合同的未实现部分；
- F-IMP-002 Task 12的生产Bean装配、ACC生产消费者、Job激活、真实浏览器和Implementation Done；
- `prereq-parallel-check-kKiAdn`的F-INT-012跨阶段候选，本DU不扩大为通用集成Feature合并；
- 未提交工作树内容和任何来源分支自报Done转记。

## 复验边界

本DU完成Git对象、需求归属、依赖和迁移序列的选择性集成审查；来源分支已有聚焦测试与真实MySQL证据。本会话未在独立运行环境重新执行Maven、MySQL或Chromium，因此合入后仍须从最新master执行Task 12前的受影响模块构建、V1～V202空库迁移和到货聚焦回归，不以本DU替代最终运行Gate。
