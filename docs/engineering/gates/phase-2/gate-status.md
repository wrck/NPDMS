# SDS Phase 2 Review

> 审查状态：`IN_REVIEW`
> 依据：PRD V1.7、SDS Phase 1 `BASELINE`、实施仓库证据提交 `856d052`
> 结论：`NOT_READY_FOR_PHASE_3`
> 评审边界：V1.6独立评审结论保留；当前按 PRD V1.7 的103项正式契约和84对象/95来源绑定/1排除源机器事实执行纠偏，完成独立复审前不恢复放行。

## 1. 输出状态

| 输出 | 状态 | 门禁要点 |
|---|---|---|
| P2-01 实现事实盘点 | PASS | 69 个业务表、59 个 Controller、65 个 DO 已按一致性分类 |
| 08 Data Model | BASELINE | Owner、聚合、引用、版本、快照、历史及六项复审修复已通过独立复审 |
| 08a Domain Entity Migration Alignment | BASELINE ADDENDUM | 84个对象、95条来源绑定和1个排除源均有机器契约；真实批次字段映射与执行仍由`AI-MIG-000`在后续迁移Gate控制 |
| 09 Database Design | BASELINE | 表级约束、PM-05/06专表、临时用户名、消费确认和前向迁移已通过独立复审 |
| 10 API Design | BASELINE | 103项显式契约、PM-05/06和DAC命令保留原独立复审结论；当前范围纠偏等待本轮独立复审 |
| 11 Event Design | BASELINE | producer、consumer、version、顺序、Inbox/Outbox及业务消费完成边界已通过独立复审 |
| 12 Integration Design | BASELINE | 字段Owner及全部外部系统操作级请求/响应映射已通过独立复审；环境参数待Feature联调登记 |
| 13 File Design | BASELINE | 文件身份、引用、版本、哈希、权限、归档已通过独立复审 |
| 15 Cache & Concurrency | BASELINE | 缓存非真值、版本冲突、树/归属并发已通过独立复审 |
| 16 Exception & Idempotency | BASELINE | 错误码、重放、超时、部分失败、补偿已通过独立复审 |

## 2. 硬门禁

| 门禁 | 当前状态 | 通过条件 |
|---|---|---|
| 数据 Owner 明确 | PASS | 每个业务事实只有一个 Owner，跨域仅引用/快照 |
| 版本、快照、历史、审计可实现 | PASS | 数据模型和数据库设计同时落位 |
| API 可追溯 | PASS | 103项均有显式数据对象、表、API、事件/集成/文件、工作流和授权契约；校验器检查符号真实存在 |
| 状态通过 command/transition 改变 | PASS | 无通用状态字段直改 API/仓储绕过 |
| 事件契约完整 | PASS | producer/consumer/version/idempotency/order 齐全 |
| 外部集成可恢复 | PASS-WITH-FOLLOWUP | 全部外部系统已登记操作级字段映射、业务确认和失败语义；具体 endpoint、认证和数值型 timeout/retry 在Feature联调前登记 |
| 实现漂移已识别 | PASS | 现有表/API/模块按与 PRD/SDS 一致性分类 |

## 3. 已登记漂移

| 编号 | 事项 | 处理状态 |
|---|---|---|
| P2-DRIFT-01 | 独立维保/续保语义超出范围 | CLOSED：08/09/10/12 已统一为 AST MaintenanceFact、ACC ServiceHandover 和客观状态边界，无续保经营入口 |
| P2-DRIFT-02 | 技术公告被实现为本地发布治理 | CLOSED：08/09/10/12 已统一为 V2 ITR 同步、查询和引用，本地治理保持 V3 |
| P2-DRIFT-03 | `license_key` 缺少敏感信息边界 | CLOSED：08/09/13/16 已区分授权材料与 DeviceCredential，并禁止未证明来源和明文迁移 |
| P2-DRIFT-04 | 可配置状态字典可能被误作状态机 | CLOSED：08/09/10/16 已固化状态代码、受控命令和字典边界 |

## 4. 独立复审 Required 项

| 编号 | 原问题 | 修复状态 | 证据 |
|---|---|---|---|
| R-P2-01 | `saveAsCredential` 后本次任务仍错误保留为临时模式 | CLOSED | 08 §11、09 §9.3、10 §13.2、16 §9：同命令创建凭证/默认授权/任务并切换凭证模式，失败则不创建任务 |
| R-P2-02 | CRM/ERP合同订单字段Owner冲突 | CLOSED | 08 §9.3、09 §8.2、10 §11、12 §4～5：按Q-02/INT-01明确ERP核心合同订单、CRM经营状态、平台交付事实 |
| R-P2-03 | PM-05/PM-06仅机械追溯，无专属实施契约 | CLOSED | 08 §4、09 §4.4、10 §5.1～5.2、11 §5、15 §5.4、16 §7 |
| R-P2-04 | 校验器只查链接存在，不查契约覆盖 | CLOSED | `phase2-contract-map.md` 103项显式映射；校验器检查ID集合、必填字段、真实表/API/事件/集成/文件符号和专项令牌 |
| R-P2-05 | 外部集成缺少请求/响应字段映射 | CLOSED | 12 §4.1覆盖CRM、ERP、ITR、MES、钉钉、HR、OA、LDAP/AD、备件、授权、UMC、财务、通知和采集子应用 |
| R-P2-06 | `CollectionCompleted` 可被模糊“契约终态”提前触发 | CLOSED | 08/09/10/11/16显式区分BUSINESS_CONSUMPTION和PRD独立中心CALLBACK_TERMINAL；失败/取消不发布完成 |

## 5. 当前阻塞

| 编号 | 状态 | 当前阻塞 | 解除条件 |
|---|---|---|---|
| P2-CORR-01 | IN_REVIEW | 当前范围、统计和校验器纠偏尚未完成独立复审 | 本轮纠偏任务全部完成并由 fresh-context 评审给出 GO |
| P2-CORR-02 | OPEN | 迁移契约仍需改为从登记的冻结实现提交读取证据 | 完成冻结提交绑定校验并证明实现 HEAD 前进不影响结果 |
| P2-CORR-03 | BLOCKED_BY_SPEC | 历史资料的当前承载边界尚待需求方决定 | 仅关闭相关历史资料接口/文件边界；不阻断其他独立纠偏 |

真实接口地址、认证材料、数值型 timeout/retry/limit 仍是 Feature 联调前证据门禁，不构成 Phase 2 当前阻塞。

## 6. 批准后数据证据对齐说明（2026-08-13）

用户要求08/09显式参考此前的数据元与数据迁移结论，并要求核心链之外的领域实体同样可迁移。本次新增08a，逐项覆盖全部显式领域数据对象的来源证据、迁移策略、排除项与Gate，同时补充证据层级、项目—合同—订单行—设备主链、来源载荷/结构化字段双层保存、迁移问题和`AI-MIG-000`漂移门禁。该段仅记录当时增量评审曾保留Phase 2 GO的历史批准证据；其结论已被本轮`IN_REVIEW / NOT_READY_FOR_PHASE_3`状态覆盖，不主张当前GO。新增内容已通过当时的领域实体迁移覆盖校验、Phase 2/3校验、PRD/领域校验和脚本单测，但不冒充此前独立复审已覆盖本轮纠偏；实际字段映射、DDL和数据迁移仍由`AI-MIG-000`及后续领域迁移工作包单独阻断和放行。

增量独立首审发现R-MIG-01/02；修复后定点复审结论为`GO`，两项均`CLOSED`，无Critical/Required。08a转为`BASELINE ADDENDUM`。P3-E09当前为`MODEL_BASELINE_READY`，只证明数据模型基线一致；真实历史迁移与数据切换仍由`AI-MIG-000`保持`OPEN`，不得据此执行。
