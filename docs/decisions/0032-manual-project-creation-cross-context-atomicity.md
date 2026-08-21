# ADR-0032：手动项目创建跨Context同步原子性

## 状态

`ACCEPTED`

## 日期

2026-08-21

## 需求依据

- PRD V1.8：`PM-01`、`PM-03`、`ACC-04`。
- PRD增量：`CHG-PRD-2026-08-21-001`，创建失败不持久化Project或创建草稿。
- 需求方对`Q-FPROJ-002`的确认：创建时同步完成PROJ与ACC初始化，要么全部完成，要么全部不完成，不允许初始化中间状态。
- SDS基线：跨Context契约默认采用Outbox、Inbox、幂等、补偿和对账；业务模块不得直接访问其他Context Repository。

## 问题

F-PROJ-001在创建正式Project时，还必须按冻结模板形成ProjectTask执行契约和ACC拥有的项目交付件实例。若沿用默认最终一致性，接口可能在ACC交付件尚未形成时返回项目创建成功，从而产生需求方明确禁止的创建中间状态。

本决策必须同时保持两个边界：

1. 创建完成语义是PROJ与ACC事实全有或全无；
2. `acc_project_deliverable`仍由ACC拥有，PROJ不得直接写ACC Repository。

## 决策

### 1. F-PROJ-001采用同步原子创建例外

- 本例外只适用于F-PROJ-001正式项目首次创建，不改变其他跨Context流程默认最终一致性的SDS基线。
- PROJ应用服务在一个MySQL本地事务中编排正式Project、Stage、ProjectTask、执行契约、幂等成功结果、成功审计和Outbox。
- PROJ同步调用ACC公开内部应用接口；ACC实现以Spring `Propagation.MANDATORY`加入调用方事务，并仅通过ACC自己的Repository写入项目交付件实例。
- Project、Stage、ProjectTask、执行契约、ACC交付件实例及同事务支撑记录只有全部成功才共同提交；任一校验、授权、并发或持久化失败均整体回滚。
- `ProjectCreated`只在全部事实可提交后写入同事务Outbox；不得先发布事件再补建ACC事实。

### 2. 不产生初始化中间状态

- 不新增Project `DRAFT`、`INITIALIZING`或交付件初始化`PENDING`状态，不建立Saga、补偿建单或异步初始化任务。
- `acc_project_deliverable.status=PENDING`若由正式模型使用，只表示完整创建后的交付件等待后续业务提交，不表示项目创建未完成。
- 失败审计可在业务事务回滚后独立追加，但不得包含Project业务事实、可继续初始化的operation或创建草稿。
- 创建失败后允许当前页面在内存中保留用户输入供修正；刷新后不恢复，也不写浏览器持久化存储。

### 3. 部署与演进约束

- 实施基线必须保证PROJ与ACC共享同一MySQL事务资源和Spring事务管理器；不满足时F-PROJ-001不得启动或发布。
- 若后续拆分数据库、事务管理器或服务，必须先批准创建完成语义变更并更新PRD/SDS/Feature/验收；不得把同步失败降级为处理中、最终一致或部分成功。
- 内部应用接口是Owner边界，不是Repository共享入口；事务传播不改变ACC对交付件事实的所有权。

## 备选方案

### A. 事件驱动最终一致初始化

- 优点：Context解耦，天然适配未来拆库。
- 否决原因：项目可能先成功、交付件后补建，违反“创建全有或全无”。

### B. 同步调用但保留处理中状态

- 优点：超时后可恢复，不要求单事务覆盖全部写入。
- 否决原因：会产生需求方禁止的初始化中间状态。

### C. 只冻结模板要求，进入阶段时再创建交付件

- 优点：缩小创建事务。
- 否决原因：不满足PM-03“项目创建时按模板加载交付件”的完成语义。

### D. 同步内部应用接口加入同一事务

- 优点：直接证明创建响应对应完整的PROJ与ACC事实，失败可整体回滚，同时保留Owner Repository边界。
- 结论：采用。

## 影响与门禁

- `POST /api/v1/pms/projects`只有在PROJ与ACC全部初始化完成后才能返回成功；不存在202初始化operation或轮询接口。
- 必须提供ACC任一点失败、并发冲突和数据库异常的真实MySQL集成测试，证明PROJ、ACC、幂等成功记录和Outbox均无残留。
- 必须验证PROJ源码没有ACC Mapper/Repository依赖，ACC接口没有`REQUIRES_NEW`、吞异常或异步注解。
- 本ADR只批准F-PROJ-001事务语义，不批准目标工程实施、Flyway执行、UAT或发布。

## SDS落位

- `docs/design/02d-cross-context-contracts.md`登记默认最终一致性的F-PROJ-001例外。
- `docs/design/16-exception-and-idempotency.md`登记跨Context同步原子事务、失败语义和演进门禁。
- `specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`及其Technical Plan引用本ADR并定义验收证据。
