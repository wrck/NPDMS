# 缺失配置补迁结果

执行日期：2026-10-19。授权：用户“完成缺失配置的迁移”。本记录承接 [迁移前盘点](inventory.md)，不改写盘点时点事实。

来源：59191 / `npdms_domain_test`；目标：59299 / `npdms_fullcycle_v4_3d5c63bd453d`，前端18199。使用模块正式创建、发布、启用接口在线完成，无服务重启。

## 补迁结果

| 配置 | 来源 | 目标结果 |
|---|---|---|
| 工期调整审批 `pms-sol-duration-change` | 来源已发布 v2 | 已发布目标 v1：`pms-sol-duration-change:1:a01b2b2c-cb85-11f1-baf3-00ff0b78abd7` |
| 现场培训标准表单 `TRAINING_FORM_20260920` | 模板 `2101693184017952769`，修订 `2101695268234719234` / v2 | 模板 `2112067304307236865`，修订 `2112067304319819778` / v1，已发布且启用 |
| 阶段计划建议规则 | 4条启用规则 | 4条均已创建并启用，未迁移1条停用规则 |

浏览器验收另发现目标流程分类为空，导致模型列表无法显示。已补齐本次审批引用的来源分类：来源分类ID `2` / code `1` / name `1` → 目标分类ID `1`（code、name、description、status、sort保留来源值），通过正式分类创建接口完成，未改写流程定义。

目标版本由正常发布流程生成，保留来源映射，不伪造来源修订号。BPM XML与来源一致；培训表单配置、规则、引擎及设计器/渲染器版本与来源一致。

| 目标规则ID | 阶段 | 签约方式 | 来源类型 | 参考阶段 | 月偏移 | 日偏移 |
|---|---|---|---|---|---|---|
| 1 | S5 | DIRECT_SIGN | PMS_IMPORTED | — | 0 | 0 |
| 2 | S4 | DIRECT_SIGN | STAGE_PLAN | S5 | -3 | -14 |
| 3 | S4 | CHANNEL_SIGN | DURATION_REQUIRE | — | 0 | -14 |
| 4 | S1 | 不限定 | STAGE_PLAN | S4 | 0 | -14 |

## 验证与边界

- 真实浏览器已确认4条建议规则均启用并打开详情；新增分类后工期调整审批模型可见，已打开设计器，但图形节点未渲染（`stage-rules-list.png`、`stage-rule-detail.png`、`duration-bpm-model-visible.png`、`duration-bpm-real-diagram.png`，均位于下列证据目录）。
- 培训标准表单在真实页面显示已发布、已启用，并成功预览表单字段（`training-standard-template-published.png`、`training-standard-form-preview.png`）。
- 图形预览限制：来源工期审批XML没有BPMNDiagram布局，目标XML与来源完全一致，设计器画布无节点。保留来源原件，不擅自重绘或重新发布；不将此项记为流程图渲染通过。
- 正式接口回读确认发布和启用状态。重复规则创建被服务端拒绝；已发布表单修改被服务端拒绝。
- 来源及目标各14张保护表的原有记录逐行内容核对一致，覆盖项目、培训、工程配置、割接、项目模板、动态表单、角色授权和定时任务。目标只在两张动态表单表各新增1条，其余上述保护表无新增。
- 原有4个审批定义的ID和XML内容与迁移前一致，证据为 `existing-bpm-preserved.json`。
- 目标原有22个定时任务、权限与业务记录保持不变。割接配置已发布元数据差异保留待确认，未覆盖不可变历史。
- 本次验收范围为配置迁移和页面可用性，不声称已重新执行工期变更业务审批闭环。

## 证据

- `.run/build/config-migration-20261019/preflight.json`：迁移前快照。
- `.run/build/config-migration-20261019/migration-plan.json`：来源内容及目标正式接口载荷。
- `.run/build/config-migration-20261019/migration-results.json`：正式接口结果、目标ID与失败路径验证。
- `.run/build/config-migration-20261019/postflight.json`：迁移后状态与原有记录保护核对。
- 核对脚本：`python .run/build/config-migration-20261019/verify.py`。




- `.run/build/config-migration-20261019/browser-config-verification.json`：真实浏览器配置页面与预览验收（含图形布局限制）。


## 后续授权：补齐工期审批图形布局

2026-10-19，用户明确授权补齐图形布局且保持审批节点与流转规则不变。目标59299已通过正式模型保存、发布接口生成 **v2**：`pms-sol-duration-change:2:452e1f00-cb87-11f1-baf3-00ff0b78abd7`。上述迁移时发现的图形预览限制已在目标新版本解决。

仅追加BPMNDiagram/BPMNPlane、3个节点Bounds和2条连线waypoint，横向排列开始事件、服务经理审批、结束事件。去掉新增图形片段后XML与修改前逐字一致，process元素及所有模型配置保持不变；候选人策略仍为35，原两条sequenceFlow的ID、起点和终点未变。

真实浏览器18199打开流程模型设计器，3个节点、2条有向连线和“服务经理审批”文字均可见，已人工查看截图确认显示正常。浏览器未再次保存或发布。没有执行审批业务实例，因本次未改变审批语义。

v1部署XML保留原件；新版本XML与补图文件完全一致；59191来源XML未改动。正常发布流程将旧定义停止用于新发起，不覆盖旧部署内容，不挂起在途实例。

证据目录：`.run/build/duration-bpmn-layout-20261019/`：

- `before.bpmn`、`after.bpmn`、`preflight.json`：精确修改边界。
- `result.json`：正式保存、发布成功，模型配置未变，目标v2定义ID。
- `history-verification.json`：v1原件、v2资源、来源内容核对通过。
- `browser-verification.json`：5个图形元素均可见及实际显示位置。
- `diagram-rendered.png`：真实浏览器显示正常截图。
