# 附录A：核心数据字典

## 1. 公共字段

- `id`：全局唯一标识。
- `tenant_id`：首期使用默认租户值，保留未来多租户扩展。
- `status`：只能由状态机动作修改。
- `version`：乐观锁版本，防止并发静默覆盖。
- `creator/create_time/updater/update_time`：审计字段。
- `deleted`：逻辑删除标记；有业务历史的关键对象不得删除。

<!-- LANDSCAPE -->

## 2. 核心对象

| 编号 | 对象 | 定义 | 生命周期 | 最低公共字段 |
| --- | --- | --- | --- | --- |
| DR-COM-001 | 客户/用户单位 | 服务对象及资产归属主体 | 同步/创建 → 分级 → 服务 → 续保/失效 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-002 | 联系人 | 客户沟通、签署和评价参与人 | 创建 → 主次标识 → 更新 → 失效 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-003 | 项目 | 实施交付治理主体，可多级多节点 | 承接 → 准备 → 实施 → 验收 → 闭环 → 维护 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-004 | 项目阶段/里程碑 | 生命周期控制点及进度基线 | 计划 → 执行 → 校验 → 完成/回退 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-005 | 设备/序列号 | 交付与维保资产最小追踪单元 | 发货 → 安装 → 配置 → 在网 → 维保 → 替换/退役 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-006 | 任务/待办/工单 | 人员需要执行的具体事项 | 创建 → 分派 → 处理 → 审核/验证 → 关闭 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-007 | 方案/计划 | 项目或割接的可执行基线 | 草稿 → 评审 → 发布 → 变更 → 归档 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-008 | 交付件/文件 | 阶段完成和验收证据 | 生成/上传 → 校验 → 审核 → 归档 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-009 | 风险/问题/遗留项 | 影响交付成功的异常对象 | 识别 → 分级 → 处置 → 验证 → 关闭/转移 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-010 | 割接任务 | 生产变更及业务切换控制对象 | 准备 → 评估 → 审批 → 执行/回退 → 观察 → 归档 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-011 | 巡检任务/规则/结果 | 设备健康检查及问题发现对象 | 准备 → 执行 → 报告 → 确认 → 整改 → 闭环 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-012 | 维保空间/续保任务 | 设备服务期限和续保机会集合 | 识别 → 下发 → 跟踪 → 转化/关闭 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-013 | 服务商/转包/付款 | 外部交付和费用结算对象 | 准入 → 申请 → 审批 → 履约 → 回访 → 付款 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-014 | 技术公告 | 产品风险及治理要求 | 编写 → 会签 → 发布 → 命中 → 治理 → 统计 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-015 | 项目组合 | 按战略、客户、区域或专项规则汇集项目的治理与分析视角，不改变项目层级 | 创建 → 配置成员 → 发布 → 重算/快照 → 失效 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-016 | 非层级项目关系 | 扩容、续采、改造等不构成父子包含关系的项目关联 | 【建议】建立 → 生效 → 变更 → 失效 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-017 | 合同及回款事实 | 以所属公司和合同编号识别的合同主档，以及与主档分离的回款来源事实 | 【建议】同步/创建 → 归属解析 → 生效 → 回款跟踪 → 到期/终止；公司解析优先级【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-018 | 销售/退货订单 | 按来源、所属公司、订单类型和订单编号识别的交易单据 | 【建议】同步 → 校验 → 生效 → 变更/取消/替换 → 完结；变更关系类型【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-019 | 订单行 | 订单下的产品或服务明细，是项目实施范围的最小交易粒度 | 【建议】同步 → 校验 → 分配 → 发货/退货 → 完结；退货数量规则【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-020 | 项目订单行实施范围 | 某项目对某订单行的已确认实施数量及其映射、生效和变更证据 | 【建议】待映射/待数量 → 有效 → 调整 → 失效；数量公式【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-021 | 装箱/发货包 | 承载合同归属、收件、快递、发货时间和维保区间的物流凭证 | 【建议】接收来源 → 解析合同 → 发货 → 签收/异常 → 归档；签收与归档门禁【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-022 | 设备物流事件 | 某设备一次发货、退货、再发、返还或RMA相关业务事实 | 【建议】接收来源 → 分类/映射 → 生效 → 更正/冲销 → 归档；正式动作、冲销及终态【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-023 | 项目设备归属 | 设备与项目在一段有效期间内的实施归属及转移历史 | 【建议】待确认 → 生效 → 转移/交接 → 结束；同一时点唯一归属【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-024 | 设备关系 | 母子序列号、RMA替换等具有方向和生效证据的设备间关系 | 【建议】建立 → 校验 → 生效 → 变更/结束；正式关系类型【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-025 | CRM执行证据 | CRM执行单、产品配置、订单辅助关联及特殊合并下单证据 | 【建议】同步 → 校验/合并 → 关联 → 更新/失效 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-026 | 同步批次 | 一次外部数据读取、校验、写入、失败和游标推进的处理单元 | 【建议】创建 → 运行 → 成功/部分成功/失败 → 对账/重试；计数分类【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-027 | 外部对象映射 | 不可变来源业务键与平台目标业务对象之间的可追溯映射 | 【建议】识别 → 建立 → 校验 → 更新/失效 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-028 | 数据问题记录 | 无法唯一映射、来源冲突或证据缺失记录的处理闭环 | 【建议】发现 → 待处理 → 处理中 → 已解决/不处理；正式处置状态【待确认】 | id、tenant_id、status、version、creator、create_time、updater、update_time |
| DR-COM-029 | 项目交付汇总 | 可按项目重建的订单、数量、设备和待处理指标读模型，不是权威明细 | 【建议】计算 → 发布 → 对账 → 重算/过期 | id、tenant_id、status、version、creator、create_time、updater、update_time |

<!-- PORTRAIT -->

## 3. 层级对象

项目与任务树至少包括：`id`、`parent_id`、`root_id`、`path`、`depth`、`sort`、`version`。`path`和`depth`用于查询和校验，不得替代真实父子关系，也不得设计固定层级列。

## 4. 关键关系与基数

| 关系 | 基数与规则 |
| --- | --- |
| 项目 → 子项目 | 一个项目可有任意数量直接子项目；每个非根项目同一时点只有一个直接父项目，禁止循环 |
| 项目 ↔ 项目组合 | 多对多；组合成员不改变项目父子层级、项目状态或权威主数据 |
| 项目 ↔ 非层级项目关系 | 多对多且有方向/类型；默认不产生层级权限、进度汇总或闭环门禁继承 |
| 项目 ↔ 合同 | 多对多；拆分后的多个项目可关联同一合同，一个项目也可关联多个合同 |
| 合同 ↔ 订单 | 多对多；不得把合同压缩为订单头上的单值属性 |
| 订单 → 订单行 | 一对多；订单行在所属订单内按行号识别 |
| 项目 ↔ 订单行 | 通过项目订单行实施范围建立多对多关系，并保存项目分配数量、映射状态和有效期 |
| 设备 → 物流事件 | 一对多；多次发货、退货和RMA事件不得覆盖设备身份 |
| 项目 ↔ 设备 | 通过项目设备归属建立带有效期的历史关系；同一时点唯一归属规则【待确认】 |
| 设备 ↔ 设备 | 有方向的多对多关系；用于母子序列号或RMA替换，禁止自关联【建议】 |

## 5. 业务身份规则

- 项目编码在同一租户内唯一【建议】；历史重复编码进入数据治理，不静默覆盖。
- 合同以“所属公司 + 合同编号”作为业务身份【建议】；公司未知时保留来源事实，不生成猜测归并。
- 订单以“来源系统 + 所属公司 + 订单类型 + 订单编号”作为业务身份【建议】。
- 订单行以“订单 + 行号”作为业务身份；项目分配关系不能改变订单行身份。
- 设备以租户内SN识别【建议】；历史冲突只有在证据充分时才可归并。
- 外部来源记录以“来源系统 + 来源对象 + 不可变来源键”追溯，不通过名称或编号后缀反查。

## 6. 生效、历史与未知状态

- 项目组合成员、项目合同、项目订单行实施范围、项目设备归属和设备关系均需保留来源与生效区间；关系失效不等于删除历史。
- 拆分、合并、转移、替换和同步修复应保留变更批次、原因及变更前后关系。
- 待映射、待数量确认、公司未知、动作未分类等状态属于有效的数据质量状态，不得为通过非空校验擅自填值。
- 已确认数量、ERP已发货数量和设备SN数量分别解释；未知或待处理记录进入异常统计，不计入已确认完成量。
- 汇总对象必须记录计算时间并可从权威明细重建；对账不一致时标记差异，不覆盖明细。

## 7. 数据所有权

- 项目管理域拥有本系统内的项目、项目组合、项目层级、任务WBS、阶段、风险、问题和交付物主数据；项目不是外部项目管理系统的复制数据。
- 工程、割接、服务、资产和外协模块分别拥有本领域过程数据。
- 分析模块只保存可重建的汇总或快照，不作为项目核心数据权威源。
- 集成模块保存映射、同步批次、游标、失败明细和对账结果，不复制无必要的业务主表。
