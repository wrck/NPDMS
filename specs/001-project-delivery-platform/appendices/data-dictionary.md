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

<!-- PORTRAIT -->

## 3. 层级对象

项目与任务树至少包括：`id`、`parent_id`、`root_id`、`path`、`depth`、`sort`、`version`。`path`和`depth`用于查询和校验，不得替代真实父子关系，也不得设计固定层级列。

## 4. 数据所有权

- PMS模块拥有项目、项目组合、项目层级、任务WBS、阶段、风险、问题和交付物主数据。
- 工程、割接、服务、资产和外协模块分别拥有本领域过程数据。
- 分析模块只保存可重建的汇总或快照，不作为项目核心数据权威源。
- 集成模块保存映射、同步批次、游标、失败明细和对账结果，不复制无必要的业务主表。
