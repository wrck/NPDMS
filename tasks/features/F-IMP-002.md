# F-IMP-002 到货签收与里程碑事实

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_REVIEW`
> Feature Ready Gate：`REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`EXE-01@V1=FULL`
> Feature Spec：`specs/features/F-IMP-002-arrival-acceptance.md`
> 复用审计：`specs/features/F-IMP-002-legacy-reuse-audit.md`
> 物理契约：`specs/features/F-IMP-002-physical-contract.json`
> 事实契约：`specs/features/F-IMP-002-arrival-fact-contract.json`

## 当前最小工作单元

- 提交F-IMP-002 Feature Ready独立复审；复审前不生成Technical Plan、不实施、不执行功能验收。
- 复审输入限于正式PRD/SDS、Feature Spec、旧实现审计和机器契约；XLSX/附件只可参考，不参与决策或形成阻断。

## 已完成的Ready候选输入

- 审计旧后端、前端、配置、运行数据/迁移、状态、权限和测试，逐项裁定复用边界。
- 定义`pms_eng_arrival -> imp_arrival_*`字段、状态、完整性与不可迁行处置；旧tinyint不得直接产生ACCEPTED。
- 锁定COM DeliveryScope与AST Device事实组成的应到范围、水位和失效语义。
- 锁定ACC-04不可变证据事件、归档待重试和不回滚签收边界。
- 形成ArrivalAcceptanceFactApi及三张Owner表机器契约。

## 未授权事项

- Feature Ready GO前不得生成Technical Plan、DDL、Flyway、后端、前端或测试实现。
- 生产依赖未形成前不得声明Implementation Done、真实MySQL生产闭环或真实浏览器正向验收。
