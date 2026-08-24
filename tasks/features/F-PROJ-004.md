# F-PROJ-004 项目业务属性判定、模板匹配历史与影响识别

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS / NPDMS-FPROJ004-FEATURE-READY-20260825-06`
> Implementation Done Gate：`NOT_EVALUATED`
> 当前阻断：无
> 当前任务：Task 6 完成全链验证、评审与Implementation Done证据
> Requirement ID：`PM-07`（仅PROJ子切片）
> Feature Spec：`specs/features/F-PROJ-004-project-business-attribute-classification.md`
> Feature物理契约：`specs/features/F-PROJ-004-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-004-template-match-decision-history.md`
> 锁定规格提交：`79125ceac092f7b586c66bbd251e9eb93ba894a2`

## 事实边界

- 复用Project既有四属性和TemplateMatcher，不根据既有代码判断Feature已完成。
- 唯一新增业务事实为append-only模板匹配决策历史；异步系统操作日志仅作可选关联。
- 不新增分类状态轴、分类案例、独立影响表、属性历史表、重实例化接口或CHG事件。
- 用户已禁用测试驱动顺序，但每个Task完成前执行风险匹配验证。
- 当前只推进Implementation，不准备Deployment、SIT、UAT或Release材料。

## 任务跟踪

- [x] Task 1 建立历史事实表、值域纠偏和持久化模型
- [x] Task 2 实现统一属性判定、匹配决策和历史构造
- [x] Task 3 将首次匹配历史纳入项目创建原子事务
- [x] Task 4 实现创建后属性修正、影响评估和历史查询API
- [x] Task 5 改造创建与详情界面
- [ ] Task 6 完成全链验证、评审与Implementation Done证据

## 非阻断边界

- INT来源定位、自动建项、传输重试与对账由后续INT Feature承接。
- CHG分派、处理与关闭由后续CHG-01承接。
- PM-08、E2E、Deployment、SIT、UAT和Release不因本Feature关闭。
