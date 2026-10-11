# 项目交付全流程模板默认业务适配

2026-10-09，按用户确认，仅适配当前已经接入统一默认主干的工勘、需求分析；其余业务保持现有实现。操作对象为本机 27580 后端、27581 前端中的 `TPL-DELIVERY-FLOW-20261009`，模板 ID `993009900020`。

当前结果：用户随后要求创建 `pms-cutover-done-approval` 并确认“服务经理审批”。流程已部署为 v1，模板草稿资源版本已推进至 `5`，Compiler 预检通过、问题数为 0，模板仍未发布。单项割接审批已在独立验收项目中完成真实浏览器闭环，详见[割接审批创建与验收记录](cutover-approval.md)。下文保留首次适配的范围及当时阻断，`adaptation.json` 仍为该时点证据。

## 首次适配保存的变更

草稿资源版本从 `3` 推进为 `4`，仍为 `DRAFT`，未发布。

| 任务 | 原办理组件 | 默认办理组件 |
|---|---|---|
| PRE_SURVEY：现场工勘与环境适配核对 | SOL_SITE_SURVEY | DIRECT_BUSINESS_SOL_SITE_SURVEY |
| PRE_REQUIREMENT：需求分析 | PROJ_REQUIREMENT_ANALYSIS | DIRECT_BUSINESS_SOL_REQUIREMENT_ANALYSIS_REVISION |

复用当前已发布且启用的业务视图修订 `2101606320578084869`、`2101606320578084872`。实体类型分别使用默认实现的 `siteSurvey`、`requirementAnalysisRevision`，办理方式为 `BUSINESS_COMPONENT`，以 `projectId` 映射项目上下文，关联方式保持 `REFERENCE_EXISTING`。需求分析不再使用旧入口专属的动态表单参数，表单选择与保存由默认主干承接。

工勘完成条件仍为 `SURVEY_CONFIRMED / ALL`；需求分析仍同时要求 `REQUIREMENT_ANALYSIS_COMPLETED / ALL` 和 `D_REQUIREMENT` 满足。没有把普通保存、任意附件上传或 HTTP 成功替代为业务完成。需求分析原生成结果和文件归集来源声明保持原值。

逐字段比较确认：除上述两项 WorkBinding 外，6 个阶段、其余 15 项任务、39 条规则、17 项交付件、5 项门禁以及其余模板配置均未改变。没有修改既有项目或发布快照，也没有创建验收项目。

## 首次适配验证与当时阻断

- 保存前回读草稿及资源版本，确认没有并发变化；通过正常草稿 API 保存，随后回读与准备的配置完全一致。
- 两项业务视图的实体、归属、组件、发布状态和精确修订均已核对；完成事实目录同时支持对应新实体类型及既有完成事实。
- 在真实浏览器重新打开模板，进入 S1 任务画布，确认两项默认组件、项目关联方式及原完成条件正常回显。见[需求分析回显](requirement-binding.png)。
- 适配前后 Compiler 预检均只有同一项错误，新增错误为 0；因此不能报告模板整体校验通过。

当时阻断：`tasks[11].workBinding`，即 `DEPLOY_CUTOVER / 割接上线`，引用 `pms-cutover-done-approval:1:7251f330-ba71-11f1-ae93-00ff0b78abd7`。当时后端查不到该流程定义，返回 `APPROVAL_DEFINITION_UNAVAILABLE`；首次适配没有自动改用其他审批流程。该项已由后续经用户确认的流程创建和精确引用更新解决。

首次适配仅改运行配置，未修改应用代码、重启服务或执行代码提交。未执行新版本发布及该模板的项目实例全流程验收。该次结构化变更和前后预检结果见 [adaptation.json](adaptation.json)；原始读写记录位于本机 `.run/default-chain-20261009/template-adaptation-20261009/`。
