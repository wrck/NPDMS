# 项目84572工勘保存拦截诊断

2026-10-24，需求方反馈“工勘保存未执行：项目任务命令不符合当前规则”。

## 已确认原因

当前项目84572使用计划1、模板v1。任务 `PRE_SURVEY` / `2113838306889756676` 为IN_PROGRESS；当前执行 `2113838309678968833` 为第1轮ACTIVE。工勘记录数为0；浏览器仍保留用户尚未提交成功的表单，没有刷新或代为提交。

运行日志13:01:41定位到 `ProjectBusinessExecutionService.requireWritableView`。该绑定声明 `businessViewRevisionId=2101604731582791681`、组件SOL_SITE_SURVEY、策略REFERENCE_EXISTING，但缺少 `businessViewSnapshot`。服务端按原规则拒绝写入。此缺口已在模板v2修复，但旧项目原冻结内容按历史保护规则没有被回填；详见[模板修复记录](template-view-snapshot-v2-acceptance.md)。

## 已准备的最小修复内容

本地方案只给 `task:PRE_SURVEY.workBinding.businessViewSnapshot` 加入模板v2中同一明确视图引用的已发布快照。除该快照外，原绑定逐字段一致；不导入整份模板v2，不修改其他任务、完成规则、阶段、交付件、里程碑或门禁。

本地文件：`.run/shared-query-audit-20261024/survey-repair-designer-proposed.json` 与 `survey-repair-proposal.json`；只读数据库证据：`survey-plan-inspection.json`；未保存表单现场：`browser-survey-unsaved.txt`。

## 正式恢复路径与批准边界

`ProjectPlanImpactAnalyzer` 不允许已开始节点直接变更绑定。已有正式 `rework-apply` 仅支持结束后的所选节点返工：须先经任务状态机明确结束当前工勘轮次，再创建只含上述字段差异的项目计划草稿，预览并通过原定向返工/计划生效服务建立新轮次。原计划、原轮次和既有完成证据保留；其他任务不返工。服务端实际预览与锁重验必须仍通过，不能仅凭本地JSON方案宣称可以生效。

这会改变当前工勘轮次，超出此前只授权用户信息编辑/移出验收的操作范围，执行前须需求方明确确认。当前没有创建运行草稿、结束任务、返工、生效计划、修改业务数据或再次重启，也没有降低服务端快照校验。

恢复后须用admin回到84572重新核对工勘填写内容并保存，验证真实Owner记录落库、旧计划及已完成历史保留；尚未执行，不能记为工勘已修复可保存。

## 批准执行后发现的数据库前置缺口

需求方随后明确“运行”。已通过admin正式API创建项目计划草稿，并只补入现场工勘的精确已发布业务视图快照；未结束任务。计划预览返回500，运行日志定位 `DeliveryMaterialMapper.selectListForRequirement` 查询 `m.version` 不存在，尚未进入任务结束或返工提交。

当前测试库Flyway从498基线开始，19条已执行历史至518；`plt_delivery_material` 有1条既有记录，缺失仓库V398定义的BIGINT NOT NULL DEFAULT 0版本列。此缺口属于计划预览读取交付件定义的前置结构依赖，不应删除查询字段或跳过交付件影响校验来绕开。

已准备前向迁移 `V519__restore_delivery_material_command_version.sql`：缺列时按V398原定义补列；已有兼容列不改动任何版本值；类型、可空或默认值不兼容则报错。原材料身份、文件版本、业务修订和证据保留。未修改V398或现有Flyway历史。

`python scripts/tests/verify_v519_delivery_material_version_mysql.py` 在新建隔离库 `npdms_v519_45884b11e4c2` 通过11项MySQL/Flyway验证：从518基线升级、列契约、旧行版本默认0、业务字段和更新时间及关联证据保留、validate、重复migrate历史不变、重复SQL、非零版本保留，以及类型/可空/默认值冲突拒绝。证据 `.run/v519-verification-9a9f9685c0/results.json` 及同目录日志。

当前生产代码未改动；运行库V519未执行，需另行确认此表结构迁移后继续已获准的工勘修复。草稿和未保存工勘表单保留，原有效计划、工勘状态及已有完成历史未改。

## 获准执行后的实际结果

需求方明确允许执行V519。已使用Compose/Flyway 11.10.5将V519应用到当前测试库172.17.90.178:13306/npdms；先对19条既有迁移严格validate，再加入V519执行migrate，随后validate和重复migrate均通过。原1条材料的所有原字段、旧迁移历史及项目计划原样保留，新增version为0。证据：`.run/v519-remote-deployment/verification.json`、前后快照及日志。本次没有重启后端或操作59191。

项目计划草稿3版本1仅补现场工勘快照。服务端预览确认只有该节点UPDATE及关联REEVALUATE，无完成规则、交付件、里程碑和门禁定义变化；唯一前置问题为原工勘已开始绑定不可改。随后经原任务cancel动作将工勘任务结束为CLOSED，再调用正式rework-apply原子建立工勘第2轮及生效计划3（计划v2）。旧执行2113838309678968833保留第1轮TERMINATED及结束证据，新执行2113861196079042562为第2轮。只有工勘被返工，其他当前节点沿正式计划服务重挂版本，既有完成轮次保留原计划和原证据。源模板仍为原v1，没有导入整份模板v2。

admin真实浏览器在新标签页进入84572工勘第2轮，沿用原未保存表单中供电、网口、资源等选项及结论cs保存成功；原标签页未关闭或清空。列表显示记录 `PJT2026000001-GK-001`，ID `2113861503051763713`，数据库确认creator和surveyor_user_id均为1，结论为`<p>cs</p>`，状态0草稿。保存后回读展示相同选项与结论。未执行确认或归档；工勘任务保持IN_PROGRESS，不能把草稿保存成功写成工勘业务完成。

历史保护复核通过：旧计划designer/execution_snapshot及来源、创建证据不变；全部既有已结束轮次逐字段一致；新计划与旧设计除工勘businessViewSnapshot外逐字段一致；其他任务状态及实际时间不变；旧工勘轮次保留、新工勘轮次为2。证据：`.run/shared-query-audit-20261024/survey-preservation-result.json`、`survey-cancel-receipt.json`、`survey-apply-receipt.json`、`survey-after-save.json`及`browser-survey-saved.txt`。
