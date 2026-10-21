# 新P14：头行业务键修复及全量真实预览

结果：**PREVIEW_READY**。新任务`2111504179094777858`，真实预览Run `2112220196368515074`，2026-10-20 00:31:52至00:33:23，用时90.802秒。仅59284专用库npdms_reverify_20261014，未执行迁移或真实同步。

## 已明确的业务键

| 对象 | 业务分组/关联键 |
| --- | --- |
| 订单头 | tenant_id＋source_system＋company_code＋order_type＋order_no |
| 订单行 | tenant_id＋source_system＋company_code＋line_type＋order_no |

行号line_no继续区分组内原明细。新P14目标查询不再把行的order_type当作原行身份；原line_type及数量保持。能解析到真实父头时，从父头取得其正式order_type和ID；未命中的关联留作后处理引用，不再作为原表只读预览的新业务约束或新裁决项。

前轮要求376条类型不同记录再次批准预览，判断已撤回。旧解析器的限制不能推翻本侧已明确的原表直写和引用延后原则；本次没有修改或执行旧解析器/原任务。源头尚不存在和同号不同类型都只登记待回填引用；没有跨类型任选父头、改原类型、填占位订单或虚构目标ID。

## 实现改动

- 头查询沿用order_type，行目标查询使用line_type；tenant_id始终由服务端运行上下文限定。
- 按已有裁决取MAX(orderExecNumber)，其他头字段同键异值仍拒绝；明细仍保留realOrderExecNumber及原数量。
- 拓展属性暂不读取/承接，目标已有custom_info不覆盖；没有新增镜像或业务快照表。
- 新source_record_key采用BASE_P14独立前缀；原任务实现、原DPPMS前缀读取逻辑及绑定保持。
- 缺父ID的预览原行逐条日志登记原ID、来源、公司、类型、订单号、行号和数量，仅计读取数。正式写入仍拒绝缺真实父ID，已有目标、来源绑定、租户、删除和同版本冲突保护保持。

[本轮实际来源SQL](P14-business-keys-source-20261020.sql)只查询pm_order_data_from_erp、pm_order_line_from_erp；两个单表来源各读取一次，无借货/未拆分ERP回查。查询仅含单表分组和一层外投影，没有新增多层关联。订单头引用缓存复用一次读取结果，行继续按原生ResultHandler分批处理。

## 真实读取与计数

| 指标 | 本次结果 |
| --- | --- |
| 当前原头物理行 | 93223 |
| 当前原明细物理行 | 388207，空订单号0 |
| 表头逻辑读取 | 92890 |
| 明细逻辑读取 | 387154 |
| Run总读取 | 480044 |
| 可规划插入 | 478464（预览计划，未写业务） |
| 引用待回填 | 1580，仅计读取；不计计划插入/更新/未变 |
| 正式业务及绑定写入 | 0 |

原始物理行与逻辑读取数的差额来自已明确的表头最大执行单及完全同值归组；不同行号和同键异值不会被静默合并。来源当前数量已不同于10月19日的91572/380605快照，本报告按本次真实读取和00:34:40只读复查登记，旧快照保留。

1580条日志对应1580个不同原ID，已逐条回查真实来源，来源ID、来源系统、公司、line_type、订单号、行号、orderQuantity和openQuantity全部相同，差异0。当前337条在同来源、同公司、同订单号存在其他类型的头，1243条没有该同号原头；这些是后置引用情况，不再列为预览业务冲突或要求用户重新批准类型规则。实际写入前仍需合法真实引用，当前不执行写入。

## 验证及保护

原失败由本轮原生MySQL临时表检查复现为P14_MANDATORY_ORDER_MISSING。修复后30项有效检查通过：本轮6项业务键检查、原7项回归、15项隔离和2项Mapper装配；覆盖未命中引用的全量读取、真实写入拒绝/回滚、类型与数量原值、最大执行单、其他字段冲突、手工目标保护、明细行号保留及独立来源前缀。首次新增测试使用临时表自读写触发MySQL“Can't reopen table”，已将测试准备改为明确VALUES后重跑通过，没有为测试修改产品规则。

构建验证使用宿主JDK25/Maven及现有worktree-toolchain、工作树互斥锁和专用依赖仓库。本轮采用既有59284候选冻结方式，仅替换已测试的新P14类族与两个XML共12个条目，其余包内容逐条一致；没有执行59191构建部署流程。

180项正式表计数/最大ID/原任务版本等保护前后相同；新正式绑定0、非预览Run0、活动任务0。Flyway、Flowable结构更新、Quartz、Batch及事件驱动作业保持关闭。只按已有授权重载59284，不操作59191，不执行正式DDL、Flyway迁移、真实同步、原任务、环境数据修正或授权写入。

24项最新预览状态为21 PREVIEW_READY、3 FAILED；P10/P18/P33沿用原回执，本轮未重跑，也不因此宣称其问题已解决。

## 证据

- [本轮结构化结果](P14-business-keys-preview-20261020.json)。
- [真实API/Run终态](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/P14-preview.json)。
- [逐条原ID、类型、数量及来源核对](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/pending-reference-proof.json)。
- [180项保护和24项最新状态](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/preview-proof-P14.json)。
- [30项有效检查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/test-summary.json)、[原失败复现](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/baseline-failing-test.xml)。
- [候选包差异边界](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/candidate-manifest.json)、[59284重载证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p14-business-keys-20261020/reload-proof.json)。

本轮只修改独立新P14及其定向检查，沿用现有分支未提交；保留工作树已有其他修改及历史证据。
