# 工前判断复用工勘记录

> 后续要求已落实：历史工前确认页面、接口和兼容代码已剔除，见 [移除检查回执](../confirmation-code-removal.json)。本页保留本轮来源更正时的原始验证记录。
2026-09-22 需求方更正：工前确认已包含在工勘记录中，不需要单独填写；指定第 8 项“上架加电是否需要原厂实施”和第 9 项“是否需要导轨、托盘”对应此前两个支线依据。

当前模板 `993009900014` / `TPL-DELIVERY-FLOW-20260922` 为 **V16 / DRAFT**。S0～S5 的 6 个阶段、17 个任务、17 个交付件、5 个门禁、6 条依赖和 39 条规则保留。S1 完成条件现在仅为 PRE_SURVEY、PRE_REQUIREMENT、PRE_DURATION、PRE_CONTACT，工勘继续消费 SOL 原生确认结果；不再要求另交工前确认表。保存后重新读取及 Compiler 预检均通过，见 [模板](designer.json)、[预检](precheck.json)、[界面验证](template-browser.json)。

## 实际调整

- 工前输入统一使用同份 SOL 工勘记录。原独立提交入口已停用，新规则目录不再提供 `preparationConfirmation.*`；原有 15 条记录按各自快照只读展示。原前缀仍保留冻结程序的历史读兼容。
- PLT 工勘表单 `993109090006` 新修订 `992209220346`（修订 2）仅增加独立支线判断展示；输入字段及 fieldBindings 与原修订逐项相同。旧修订 `993109090007` 内容和历史绑定未改写。
- 字段对应及等值条件存在表单组件配置，通过工勘 fieldBindings 读取，Java / Vue 不内置这三个字段名。项目规则目录继续由公开实体元数据和参数配置提供，目前 56 项。
- 第 10 项物料环境为否：CRM 改单；第 8 项为是且第 9 项为是：物料申领；第 8 项为是且第 9 项为否：外采事前申请。未填写显示未知。此对应仅采用本次需求方明确裁决。
- 三个支线仍是独立办理提示，沿用原流程入口，不代表外部系统已经发起或完成；不增加统一回流或支线等待。

## 验证证据

在前端 **19191**、后端 **59191**、Compose **npdms-domain-test** / 数据库 **npdms_domain_test**、租户 1 完成：

- 后端 13 项针对性测试通过：独立提交拒绝、授权及项目范围、历史不变、冻结字段兼容和项目规则读取。宿主机完整后端打包通过。
- 前端 16 项测试和全量 `pnpm ts:check` 通过，包括未知/false 区分、8 种分支组合、字段改名配置及原工勘绑定读写。
- 真实浏览器填写现有字段，检查 8 种组合，保存同一工勘、重新打开只读详情并执行原生确认。没有新增独立确认；工勘浏览器无页面或业务 API 错误。
- 原已确认工勘的直接更新被 Owner 状态校验拒绝（1011001002），内容保持不变。独立确认 POST 被拒绝（400），原 15 条历史保持不变。
- V344 在指定隔离库成功迁移，289 个迁移验证通过；重复迁移为 0。后端健康检查 UP。

汇总：[verification.json](verification.json)、[工勘浏览器回执](survey-browser.json)、[字段与历史停用检查](retirement-check.json)、[表单配置](survey-schema.json)、[只读详情截图](survey-reopened-readonly.png)、[历史截图](history-readonly.png)。

本轮复用隔离验收项目 `992203060019`，形成工勘 `2102387388929748993`，不改造 CRM 来源值。该验收项目原先使用其他已发布模板；本轮证明工勘来源调整、原生确认和目标模板配置正确，未发布源图项目模板或宣称已完成其 S0～S5 全流程运行验收。前轮 BPM 等证据仍见 [V15 历史回执](../remaining/README.md)。模板列表仍存在旧模板 910001/910002/910003 缺精确定义修订的既有错误；目标模板读取和预检正常，相关 API 回执保留在 template-browser.json。

验证命令：`mvn -pl pms-module-project -am test -Dtest=ProjectPreparationConfirmationTest,ProjectFieldRuleApiImplTest -Dsurefire.failIfNoSpecifiedTests=false`；`mvn -pl yudao-server -am package -DskipTests`；`pnpm exec vitest run --config vitest.pms-file.config.ts`（仅运行本次三个相关文件）；`pnpm ts:check`。迁移经指定 Compose migrate 服务执行。临时编排脚本位于 `C:/Users/user/AppData/Local/Temp/npdms-template-visio-20260922/` 的 configure_survey_source.py、browser_survey_source.py、browser_template_survey_source.py、build_survey_source.ps1 和 migrate_survey_source.ps1。

未提交或推送；Feature / Implementation Done 状态不因本增量晋级。
