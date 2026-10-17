# 业务联调交付件满足后自动完成任务：修复与验收

来源：需求方2026-10-17确认“改为交付件满足后自动完成”；规格见[F-IMP-005第7节](../../specs/features/F-IMP-005-joint-debugging-configuration.md)。验收环境为19191前端、59191后端、npdms_domain_test，项目992203060198。

## 原因与变更

原生效计划v6（2104870198458802235）中，业务联调任务2107838747850678283的完成条件为`ALL(TASK_NATIVE_STATUS(DONE), DELIVERABLE(D_JOINT))`。D_JOINT已满足，但当前轮次没有人工提交，因此保持IN_PROGRESS符合原规则。需求方确认后，通过新计划版本替换后续完成判断，保留旧计划和已完成轮次。

服务端复用当前计划完成求值、ACC交付件锁定重验、Outbox重评和原冻结任务状态机。仅联调PAGE绑定且独立DELIVERABLE完成条件允许免人工提交；旧人工规则、反向条件和常量条件不享受此行为。不可用事实保持失败关闭；旧规则等待人工提交不新增无限重试。当前轮次、活动阶段、退出规则、门禁、已开始子任务及幂等守卫保留。无直接更新业务表状态、无伪造联调通过记录。

## 已执行验证

- 97项定向测试通过：任务完成规则25、任务生命周期49、计划影响14、轮次规划9，0失败/错误。涵盖未满足/未知交付件、旧提交规则、其他PAGE、活动阶段/退出/门禁保护、自动状态机与系统审计、重复事件及历史保护。
- 本地日志快照回归29项通过（AST16、PLT13）。构建发现已存在的Mapper方法更名与部署字节码不一致；复核仅`lock`到`selectByFileForUpdate`及常量池编号变化，对应XML仅select id不同，SQL租户/文件/deleted条件和FOR UPDATE完全一致。保留全部旧批准项，仅增加精确等价后继字节码，原22项修复校验通过，校验程序未改。
- 已生成计划v7草稿2104870198458802263。只修改DEPLOY_JOINT_DONE为`DELIVERABLE(D_JOINT)`，无交付件、门禁、里程碑写入差异，影响预览issues为空。真实浏览器从“阶段状态→项目计划→影响预览”确认通过、生效按钮可用，未执行业务写入，无页面错误。
- 官方工作树构建成功并冻结产物5ff6c227-2651-4078-a42d-cdd1e1ea3f52，清单`.run/build/releases/5ff6c227-2651-4078-a42d-cdd1e1ea3f52/manifest.json`。`scripts/worktree-backend.ps1 -Manifest ... -ValidateOnly`确认冻结产物、当前运行修复及local,dac配置通过，无服务变更。日志为`build-reviewed.log`、`deployment-preflight.log`。

## 部署与真实页面验收结果

需求方明确批准部署、59191重启及项目v7生效后，于2026-10-17完成以下实际操作与核验：

1. 部署前原PID28884已不在运行，59191未监听；使用正式`worktree-backend.ps1 -Manifest ... -ApprovedRestart -StartIfOffline`从上述冻结产物恢复，实际PID18336、端口59191、配置local,dac，健康检查通过。19191前端当时也已离线，按`worktree-frontend.ps1`恢复为PID32376。数据库、Redis和端口按原入口保持不变，未启动或重启DAC。部署时为OAuth2 SQL日志显式保留WARN级别，避免验证登录输出凭据。
2. 真实浏览器登录19191，进入项目992203060198的“阶段状态→项目计划”，再次预览v7后点击“生效计划→确认生效”。原生POST返回planVersionId=2104870198458802263、revisionNo=7；当前计划显示v7，草稿为空。
3. 计划生效事件触发服务端正式重评：业务联调任务2107838747850678283从IN_PROGRESS自动变为DONE、进度100%，实际完成时间2026-10-17 10:32:21（Asia/Shanghai），任务版本4→5。本轮2107838749171884033的submitted_at仍为空，没有伪造人工提交。自动完成判定2111284146892619777为SATISFIED，系统actor=0；审计2109660989849897359记录executionMode=AUTOMATIC及原状态机路径SUBMIT→COMPLETE。相较操作前仅新增一次完成判定及一次该任务完成审计。
4. 从项目详情S4选择“业务联调”，等待工作台交付件和业务区加载完成，确认任务完成、进度100%、D_JOINT已满足，人工提交/完成按钮不再展示。进入“执行历史与规则结果”，可查看项目计划v7和v6，展开DEPLOY_JOINT显示完成规则满足、未人工提交；关闭、整页重载后重新进入，完成状态保持。页面JavaScript错误为空，复查浏览器业务写请求为空。
5. 对比部署前留存快照：D_JOINT原两份v2日志材料及完整提交历史、D_CONFIGURATION原材料及完整提交历史均不变；旧计划的designer_document、execution_snapshot原文及所有已结束执行轮次完整记录均保持不变。本次没有重新上传、解析日志、人工提交材料或人工完成任务，也没有直接写业务状态。

此局部任务完成链路及指定项目真实页面验收已闭环，不晋级F-IMP-005整体Feature，不据此宣称设备基线对比或联调通过事实已实施。未提交或推送Git。

证据位于`.run/build/joint-task-completion-20261017/`：

- `tests.log`、`test-evidence.json`、`snapshot-regression.log`、`repair-review/`：测试及部署修复保护。
- `preview.json`、`preview-browser.json`、`plan-v7-preview.png`、`plan-applied-browser.json`：计划影响和原生页面生效回执。已有生效回执，不应重复执行生效脚本。
- `deployment-start.log`、`frontend-start.log`、`runtime-after.json`：正式入口启动和实际冻结产物/进程/配置。
- `completion-evidence.json`、`task-after.json`、`plan-after.json`、`evaluation-after.json`、`audit-after.json`：任务自动完成和系统审计。
- `joint-after.json`、`configuration-after.json`、`plans-db-after.json`、`rounds-db-after.json`及对应before快照：材料与不可变历史保护。
- `completion-browser.json`、`joint-task-done.png`、`joint-completion-history.png`、`joint-task-reopened.png`：真实页面完成、历史及重开。验收脚本修正了中文关闭按钮定位，并增加等待工作台实际交付件加载后再截图；复验通过，无额外应用代码修改。

复验读取入口为`check-completion.py`及`accept-completion-browser.py`；计划已生效，`accept-plan-browser.py --apply`不应再次运行。
