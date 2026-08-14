# SDS Phase 2 工程化自审

> 日期：2026-08-15
> 状态：`APPROVED`
> 结论：`READY_FOR_PHASE_3`
> 结论边界：本文件记录当前103项范围纠偏自审；独立放行依据以`independent-review.md`的固定提交范围和GO结论为准

## 1. 审查范围

- 正式分册：`docs/design/08`、`09`、`10`、`11`、`12`、`13`、`15`、`16`。
- 过程证据：实现事实盘点、Phase 2 gate status、执行计划。
- 追溯资产：103项 `requirement-matrix.md` 与生成器。
- 上游：PRD V1.7、SDS Phase 1 `BASELINE`。

## 2. 已执行校验

| 校验 | 结果 |
|---|---|
| PRD语义校验 | PASS，0 semantic issues |
| 13领域生成校验 | PASS，formal=103、V3=30、OUT_OF_SCOPE=9 |
| 脚本单测 | PASS，230/230（含Phase 2、PRD独立白名单、附录C核心对象一致性、结构化历史排除、普通`PENDING`及`PENDING_*`不得排除当前契约、五项范围统计、BASELINE分册状态、核心迁移契约全部7张WorkOrder/工时禁表、历史API/对象/表/中英文文件Context回流负测、免责声明绕过负测、混合标题层级解析、迁移对象表精确映射及门禁正反用例） |
| 业务命名门禁 | PASS |
| Phase 2专用校验 | PASS，含08a的9份正式分册元数据、103项显式契约及链接/锚点有效 |
| 追溯矩阵 | PASS，103个唯一Requirement，103行均链接逐项 `phase2-contract-map.md` |
| 独立复审 | PASS，三项Required全部CLOSED，无Critical或新的Required |
| `git diff --check` | PASS |

可重复执行命令：

```powershell
py -3.13 -B scripts\validate_sds_phase2.py
py -3.13 -B scripts\validate_prd_semantics.py --prd docs\baseline\prd-v1.7.md
py -3.13 -B scripts\validate_prd_domain_generation.py --prd docs\baseline\prd-v1.7.md --domains specs\001-project-delivery-platform\domains
py -3.13 -B -m unittest discover -s scripts\tests -p "test_*.py"
py -3.13 -B scripts\check_business_naming.py
git diff --check
```

## 3. 自审发现与修正

| 编号 | 发现 | 风险 | 修正 |
|---|---|---|---|
| S2-R01 | 08中Preparation/Solution适用Requirement最初概括过宽 | 错误追溯范围 | 修正为PRE-01～05、PLN-01～04、SCH-01～05、SOL-01 |
| S2-R02 | Phase 1中`DeviceOwnershipChanged`与`DeviceAssigned`并存 | 公共事件名称不稳定 | 11明确前者为AST域内事件、后者为02d稳定跨Context事件 |
| S2-R03 | `InspectionDispatched`最初把DAC列为Consumer | 与唯一采集请求契约方向冲突 | DAC只消费`CollectionTaskRequested`；Inspection事件仅供Todo/ANA |
| S2-R04 | 旧实现的维保/公告/license_key/状态字典语义漂移 | 将过时实现固化为目标能力 | 通过MaintenanceFact、ServiceHandover、ITR只读镜像、DeviceCredential和前向迁移纠正 |
| S2-R05 | 外部接口缺少当前环境端点、认证和数值型SLA | 无法直接联调/生产 | 登记为Feature联调前接口配置档案门禁，不虚构业务阈值 |
| S2-R06 | 显式保存凭证后本次任务仍被描述为临时模式 | 违反INT-12验收并导致授权快照缺失 | 改为凭证、默认创建人授权和任务原子创建；任务记录新凭证及授权快照，失败不降级 |
| S2-R07 | CRM/ERP合同订单Owner混写 | 外部字段相互覆盖 | 按已确认Q-02/INT-01明确ERP核心合同订单、CRM经营状态、平台交付范围与履约事实 |
| S2-R08 | PM-05/PM-06只有领域级通用落位 | 转销和多期关系无法实现 | 新增专属聚合、表、API、事件、并发、异常和权限契约 |
| S2-R09 | 正式需求仅校验文档链接，无法证明契约覆盖 | 机械追溯掩盖需求空洞 | 新增103项逐项显式契约映射和符号级校验，含缺块/不存在表的负向测试 |
| S2-R10 | 外部集成缺请求/响应字段和业务确认 | 无法判定接口成功与业务成功 | 12 §4.1逐系统登记字段、来源/幂等键、业务确认与失败处理 |
| S2-R11 | Collection完成可由模糊契约终态触发 | 业务结果未消费即误报完成 | 业务入口必须匹配消费确认；仅PRD独立中心允许成功终态回调完成，失败/取消分离 |
| S2-R12 | “历史事实不可删除”被误解为V1/V2历史工单/工时查询、导出和附件入口 | 恢复已后置的WO/工时能力并无端预建对象、表和权限 | Q-P2-001采用方案B：仅`AI-MIG-000`在批准真实批次内保存不可变来源载荷或受限归档证据；删除当前API和WO文件Context，并增加负向校验 |
| S2-R13 | PRD正文3.3已删除工单核心对象并纳入满意度任务与问卷，但附录C仍列工单/WO-01～06 | 正式基线内部冲突可使下游重新恢复已后置能力 | 两份PRD附录C第4项改为满意度任务与问卷并回指ACC-02；3.4明确历史事实仅由`AI-MIG-000`保存证据；新增附录C负向门禁并同步SHA登记 |
| S2-R14 | Phase 2校验器只拦两个历史对象和两张历史表 | 可用中文Context、非历史前缀或其他工时表绕过回流门禁 | 从核心迁移Schema契约读取`forbiddenV1V2Tables`，结构化拦截全部7张WorkOrder/工时禁表及对象别名，同时允许EXCLUDED/COMPATIBILITY_ONLY/历史排除证据与未来独立变更说明 |
| S2-R15 | `PENDING`和任意`PENDING_*`曾被当作非当前契约处置 | 当前打卡、WorkOrder或WO消费者可借待定状态绕过V1/V2范围门禁 | 仅允许`EXCLUDED`、`COMPATIBILITY_ONLY`或结构化“历史排除/不进入当前”处置跳过；增加打卡、WorkOrder和WO消费者负测 |
| S2-R16 | Phase 1追溯分册仍写V3 29项，且Phase 2未复核完整范围统计 | PRD范围变化可在设计文档中静默漂移 | 修正为V1 55、V2 48、当前103、V3 30、OUT_OF_SCOPE 9，并由校验器从PRD附录A.1/A.3.1/A.4重算后核对01分册 |
| S2-R17 | 文件保留/灾备和缓存容量/TTL使用`IN_REVIEW`表示后续运行参数 | BASELINE设计分册与当前Gate未决状态混淆 | 改为`DEFERRED_TO_PHASE_3`；校验器禁止九份Phase 2 BASELINE分册残留`IN_REVIEW`并要求两项明确后置标记，不扫描Gate和历史评审文件 |

## 4. 关键语义核对

| 结论 | 状态 |
|---|---|
| 项目和任务不固定层级，层级与任务依赖正交 | PASS |
| 设备同一时点只有一个最具体项目归属，上级通过祖先投影统计 | PASS |
| 必要主数据保存本地同步副本，外部Owner不被平台覆盖 | PASS |
| IMP上传，ACC审核齐套与归档 | PASS |
| DAC是独立Context/子应用边界，平台不重建采集引擎 | PASS |
| 临时密码不持久化；未保存时任务留存临时用户名；显式保存成功后本次任务切换为新凭证及默认授权快照 | PASS |
| 凭证默认仅创建人可用，授权按五元组收敛 | PASS |
| 业务状态只能通过command/transition改变，字典不产生迁移能力 | PASS |
| 外部HTTP、待办、通知和消息投递成功不等于业务完成 | PASS |
| 维保经营模块、续保报表、周报日报、工单时效和通用割接时效未进入新契约 | PASS |
| 技术公告V2仅ITR同步与引用，本地治理保持V3 | PASS |
| 历史工单/工时不形成V1/V2对象、表、API、文件入口或权限；不可删除义务由`AI-MIG-000`不可变来源证据承载 | PASS |

## 5. 自审结论

当前按PRD V1.7校准为V1 55项、V2 48项、V1/V2正式需求103项、V3 30项和`OUT_OF_SCOPE` 9项；领域迁移设计覆盖84对象、95来源绑定和1排除源。V3 Requirement、WO消费者、钉钉打卡事实及历史工单/工时用户入口未进入当前范围；`PENDING`范围绕过、统计漂移和后置状态混淆三项Required均已修复并通过fresh-context定点复审。Q-P2-001已按方案B收口。Phase 2正式结论为`APPROVED / READY_FOR_PHASE_3`。

P3-E09维持`MODEL_BASELINE_READY`，仅表示数据模型基线一致；Q08索引仅为候选，仍须Feature查询与性能验证。`AI-MIG-000`、真实历史迁移和数据切换继续作为独立后续门禁，不因Phase 2批准而放行。外部接口配置档案、存储保留数值和容量参数分别属于Feature联调/Phase 3运行证据，不得在缺少Owner与SLA时机械填充。
