# F-COM-001按Requirement整体合并实施计划

> 计划状态：`APPROVED / IN_PROGRESS`
> Requirement：`COM-01@V1`；拆分支撑能力`PLT迁移证据`、`IMP/AST实施地点`
> Delivery Unit：`DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE`

## 目标

将COM-A与COM-B从“分支二选一”改为“需求能力重组”：以COM-A已验证纵向闭环为实现基础，吸收COM-B非重复的来源接入、人工候选/对账、项目范围水位、当前范围查询和冲突处置能力，并把不属于COM事实Owner的PLT迁移证据及IMP/AST地点独立落位。

## 能力映射

| 能力 | 来源 | 合并裁决 |
|---|---|---|
| ERP合同/订单/订单行只读副本 | A+B | 单一COM Owner模型；保留A的字段Owner与并发守卫，吸收B的批次来源身份与重放语义 |
| 人工待核对与ERP对账 | B为主，A有降级契约 | 纳入统一COM；人工事实永不晋级为ERP权威 |
| 合同—订单—项目关系及公司范围 | A+B | 保留A的SYSTEM公司授权和显式关系，吸收B的稳定来源身份 |
| DeliveryScope命令、历史和冲突 | A+B | 保留A事务/ACC守卫，吸收B项目级版本、`getAssignedScope`与显式冲突处置 |
| 交付范围地点 | A | 唯一使用项目办事处发生时快照 |
| AST站点/位置/文本降级 | B | 迁到IMP/AST实施地点；不得进入COM当前范围地点Owner |
| 验收阶段范围绑定 | A | 保留进入验收阶段和阶段内新版本原子绑定 |
| 平台迁移证据 | B | 拆为PLT能力；独立Requirement、Feature/Task和消费方就绪前保持增量未Done |

## 实施顺序

1. PRD/SDS：建立修订010，统一COM地点、人工对账、范围版本和ACC绑定语义；同步领域规格和SDS。
2. Feature：形成唯一F-COM-001规格、物理契约和旧实现处置；COM-A/COM-B历史规格均不得单独驱动实现。
3. 基础闭环：选择性迁入COM-A公共契约、Owner Provider、业务实现、REST、UI与测试；Flyway重编为master未占用的V160起序列。
4. 非重复增量：逐能力吸收COM-B，禁止复制其AST地点列和第二套范围表；补齐真实Provider/调用方。
5. 拆分能力：PLT迁移证据与IMP/AST地点分别建立Feature/Task；未就绪时只保留明确的`IN_PROGRESS/BLOCKED_BY_SPEC`，不挂靠F-COM-001完成。
6. 验证：运行规格、追溯、DU、后端、前端、迁移及真实浏览器检查；仅以master结果裁决状态。

## 提交策略

- 提交一：权威需求、规格、计划和认领收敛。
- 提交二：COM-A纵向闭环适配进入master，状态为可构建增量。
- 提交三：COM-B非重复能力及PLT/IMP拆分。
- 提交四：测试、运行证据、追溯投影和最终状态。

任一中间提交可进入master，但不得在统一验收完成前标记F-COM-001 Implementation Done。
