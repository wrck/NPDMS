# 项目详情交付视图补齐：当前会话实施记录

> 基准：`0c6509a37`；分支 `codex/domain-migration`。
> 需求来源：[项目交付页面数据DemoV2](../../需求/项目交付/项目交付页面数据DemoV2.html)（项目详情 21 个子页面）。
> 状态：四个缺口模块前端源码完成；dev 服务器按需编译与真实 API 数据验证已执行。本记录不宣称发布可用。

## 范围判定

- Demo 21 个子页面中 17 个已由现有项目详情（`pms/project/project-detail`，菜单 18020）导轨覆盖：项目概览 5 固定项、工勘/需求/交底、方案计划、实施部署、割接、验收收尾、维保巡检。
- 真实缺口 4 个（后端均已有实现，不造假数据）：Demo 1.1.1 序列号详情（设备分页）、Demo 1.1.1.1 配置Log、Demo 2.2.1 物料选择/发起换货（换货协同）、Demo 6.2 满意度调查。
- Demo 6.1 现场培训：grep 全仓无 Training 后端控制器，无既有实现；问卷推送（6.1/6.2 推送至客户手机）由满意度问卷模块（`/satisfaction-questionnaires/:token` 公开问卷）承接。本次不建现场培训假页面，未造现场培训后端。

## 本阶段代码

- `pms/project/project-detail/index.vue`：新增左侧导轨分组 `设备序列号`（流程步骤：序列号详情、配置Log），工程实施流程步骤新增 `物料换货`，验收收尾流程步骤新增 `满意度调查`；四个配置驱动模块面板全部消费既有真实 API。
- `序列号详情`：`getEquipmentPage({projectId,...})`，列含序列号/产品型号/安装位置/维保起止，状态用 PMS_EQUIPMENT_STATUS 映射，前往完整页面路由 `/pms/asset/equipment`。
- `配置Log`：后端 config-log 分页仅支持按设备过滤（无 projectId），新增 `loadProjectConfigLogs`——先取项目设备（至200台），按设备合并日志页、enrich 序列号、按采集时间倒排后切片分页；前往完整页面路由 `/pms/asset/equipment-config-log`（菜单 19008）。
- `物料换货`：`getMaterialExchangePage({projectId,...})`，状态用 PMS_APPROVAL_STATUS 映射；行内操作：提交（草稿）、推送CRM（已通过且未推送）；前往完整页面路由 `/pms/eng-material-exch`（菜单 19189）。
- `满意度调查`：`listResults(projectId)` 为结果视图数组，包装为 `{list,total}`；未纳入项目树治理范围的项目，后端返回 1014024033，详情视图面向任意项目，按空数据处理（catch → 空列表），不吞其他错误；行内操作：下载报告（`getResultDownload(resultId, 1)`）；前往完整页面路由 `/pms/project/satisfaction`。
- 三处既有 API 分页参数类型按实际过滤字段修正：`getEquipmentPage`/`getMaterialExchangePage` 用 `PmsProjectPageParam`，config-log 新增 `EquipmentConfigLogPageParam`；不修改 URL、权限或后端源码。

## 测试与验证边界

- `vue-tsc --noEmit` 全仓类型检查：0 错误。
- dev 服务器（18181）按需编译：项目详情模块及四个依赖 API 模块全部 200，无 Pre-transform error。
- 真实 API（58280）数据验证：配置Log 30 条（设备1010，属项目1010）、设备 31 台（项目1010 有 3 台）、换货 1 单（项目1002，已通过、CRM推送状态 CLOSED）、满意度任务/结果当前为空集（问卷模块未生成任务）；满意度带 projectId 对未治理项目返回 1014024033 已由视图容错。
- 未做：真实浏览器人工操作闭环、换货提交/推送CRM 写操作的端到端执行、满意度问卷真实生成后的展示。这些继续按各自 Feature（F-AST-001、F-ACC-002 及换货协同）验收路径进行，本记录不替代其 Done Gate。
