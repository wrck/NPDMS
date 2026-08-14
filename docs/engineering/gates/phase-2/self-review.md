# SDS Phase 2 工程化自审

> 日期：2026-08-15
> 状态：`PHASE2_CORRECTION_IN_REVIEW`
> 结论边界：V1.6独立评审证据保留在`independent-review.md`；本文件同步记录当前103项范围纠偏自审，不伪造新的独立复审结论

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
| 脚本单测 | PASS，201/201（含Phase 2、PRD独立白名单、混合标题层级解析、迁移对象表精确映射及门禁正反用例） |
| 业务命名门禁 | PASS |
| Phase 2专用校验 | PASS，含08a的9份正式分册元数据、103项显式契约及链接/锚点有效 |
| 追溯矩阵 | PASS，103个唯一Requirement，103行均链接逐项 `phase2-contract-map.md` |
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

## 5. 自审结论

V1.6首轮独立复审给出六项 Required 和`NO-GO`，修复后定点独立复审确认全部关闭并给出`GO`。当前按PRD V1.7重新校准为103项正式需求、84对象/95来源绑定/1排除源，并清除V3 Requirement、WO消费者和钉钉打卡事实的当前范围漂移；不把本次自审写成新的独立评审。Phase 2 Gate保持`IN_REVIEW / NOT_READY_FOR_PHASE_3`，直至本轮纠偏、未决边界和独立复审全部收口。外部接口配置档案、存储保留数值和容量参数分别属于Feature联调/Phase 3运行证据，不得在缺少Owner与SLA时机械填充。
