# SDS Phase 2 工程化自审

> 日期：2026-08-19
> 状态：`REVALIDATION_REQUIRED`
> 结论：`NOT_READY_FOR_PHASE_3_V1.8`
> 范围：PRD V1.8 的 100 项 V1/V2 正式需求；本轮仅修正 Phase 2 数据、迁移、数据库、API、事件和集成契约

## 1. 本轮修正

| 编号 | 修正 | 结果 |
|---|---|---|
| S2-V18-01 | 项目状态拆分为 `current_stage`、`lifecycle_status`、`assignment_status` 和派生 `display_status`；CLO-02唯一产生 `NORMAL_CLOSED`，PM-10产生 `EXCEPTION_CLOSED` | 设计和契约映射已同步 |
| S2-V18-02 | IMP-02安全检查退出当前范围，IMP仅保留IMP-01质量检查；删除安全聚合、目标表、API和事件 | 迁移契约不再生成安全对象 |
| S2-V18-03 | COM-02履约对账退出当前范围；COM仅保留COM-01 ERP合同/订单/订单行与平台交付范围，删除履约回写API、履约汇总事件和对账聚合 | 不新增履约对账实体或目标表 |
| S2-V18-04 | ACC-05转V3；ACC-06保留V2静态服务交接快照，不创建持续服务跟踪对象 | 交接模型和迁移契约已收敛 |
| S2-V18-05 | 迁移对象由84个收敛为81个，来源绑定由95条收敛为92条；对象表机器映射精确同步 | 禁止已退出对象回流 |
| S2-V18-06 | Stage→ProjectTask工作台统一使用必填WorkBinding，默认TASK_NATIVE承载通用任务详情，其他类型按绑定关系执行并按对应事实完成 | 逻辑SDS与契约映射已同步；物理承载保持BLOCKED_BY_DESIGN |

## 2. 可复现校验

| 校验 | 结果 |
|---|---|
| PRD V1.8语义 | PASS，0 semantic issues |
| Phase 2范围门禁 | PASS，100项，V1=53、V2=47；仍为REVALIDATION_REQUIRED |
| Phase 3前置门禁 | PASS，100项映射；未发布为SDS基线 |
| 核心迁移Schema契约 | PASS |
| 领域实体迁移对齐 | PASS，81对象、92来源绑定、1个排除源 |
| Phase 2契约映射生成器 | PASS，无漂移 |
| 领域迁移契约生成器 | PASS，无漂移 |
| 目标字段目录校验 | PASS，11份产物 |
| 脚本单元测试 | PASS，246/246 |
| `git diff --check` | PASS |

## 3. 未关闭项与边界

- 本轮没有批准Phase 2，也没有写入独立复审GO；独立复审需在本轮修正形成固定提交后重新执行。
- 生产配置和适用发布证据仍是后置门禁，不因本轮设计校验通过而放行。`AI-MIG-000`只在Release包含历史迁移或数据切换时作为前置门禁，并且只允许在批准窗口内执行；普通功能发布不适用。
- Q08索引仍是候选，性能验证仍属于后续Feature/P3-E06；隔离MySQL执行只证明DDL可执行。
- 历史工单/工时及续保/持续服务跟踪不形成当前V1/V2对象、目标表、API、文件入口或权限；仅允许按已确认迁移治理保存受限来源证据。

## 4. 自审结论

本轮Phase 2差量修正已完成机器契约同步和自动校验，但尚未经过本轮独立只读复审。因此当前工程状态保持：

`REVALIDATION_REQUIRED / NOT_READY_FOR_PHASE_3_V1.8`
