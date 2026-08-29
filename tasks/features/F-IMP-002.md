# F-IMP-002 到货签收与里程碑事实

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`IMPLEMENTATION_PENDING`
> Feature Ready Gate：`READY / GO`（锁定提交`4b5a2ac9`）
> Technical Plan Gate：`PASS / GO`（锁定提交`e0184ac4`）
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`EXE-01@V1=FULL`
> Feature Spec：`specs/features/F-IMP-002-arrival-acceptance.md`
> 复用审计：`specs/features/F-IMP-002-legacy-reuse-audit.md`
> 物理契约：`specs/features/F-IMP-002-physical-contract.json`
> 事实契约：`specs/features/F-IMP-002-arrival-fact-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-imp-002-arrival-acceptance.md`

## 当前最小工作单元

- 按唯一当前有效的F-IMP-002 Technical Plan进入Task排他认领；先实施Task 1公共事实契约与生产依赖适配，不提前执行功能验收。
- 计划输入限于正式PRD/SDS、Feature Spec、旧实现审计和机器契约；XLSX/附件只可参考，不参与决策或形成阻断。

## Technical Plan候选

- 当前候选：`docs/superpowers/plans/2026-08-30-f-imp-002-arrival-acceptance.md`。
- 候选覆盖五表前向迁移、ArrivalAcceptance聚合与状态机、FactApi、DeliveryEvidence/ACC双向事件、REST/UI、真实MySQL和真实浏览器验收。
- 受控COM/AST/PLT/ACC替身只允许用于计划GO后的单元/集成测试；生产Provider未形成仍阻断Implementation Done和真实浏览器正向闭环。
- `e0b44970`独立复审为NO-GO；当前整改只补齐PROJ项目经理/S4资格、生产Adapter与持久Inbox/调度路径、应用级CURRENT_FORWARD及Flyway合入时定号，不回写PASS。
- `5805db7f`最小复审已关闭上述PROJ、事件链和迁移项；当前仅整改COM正式`getAssignedScope`生产依赖，禁止以现有可分割余量接口降级替代。
- `e0184ac4`独立最小整改复审GO；授权回写Technical Plan PASS并进入计划执行，生产COM/AST/ACC依赖仍阻断相应Task、Implementation Done和真实浏览器正向闭环。

## 已完成的Ready候选输入

- 审计旧后端、前端、配置、运行数据/迁移、状态、权限和测试，逐项裁定复用边界。
- 定义`pms_eng_arrival -> imp_arrival_*`字段、状态、完整性与不可迁行处置；旧tinyint不得直接产生ACCEPTED。
- 锁定COM DeliveryScope与AST Device事实组成的应到范围、水位和失效语义。
- 锁定ACC-04不可变证据事件、归档待重试和不回滚签收边界。
- 形成ArrivalAcceptanceFactApi、三张到货Owner表和EXE-01最窄DeliveryEvidence两表机器契约。
- 明确批次DRAFT/PARTIALLY_ACCEPTED/DIFFERENCE_PENDING/ACCEPTED/CONFIRMED转换、项目里程碑独立判定，以及IMP出向/ACC入向事件幂等边界。

## 未授权事项

- Technical Plan通过评审前不得生成DDL、Flyway、后端、前端或测试实现。
- 生产依赖未形成前不得声明Implementation Done、真实MySQL生产闭环或真实浏览器正向验收。
