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

## 待执行

已向需求方申请本次59191部署重启批准，尚未获得回复；原PID28884保持运行，DAC不操作。批准后通过真实页面生效已预览的v7，验证已有材料使任务自动DONE、进度100%、实际完成时间、自动完成审计、关闭重开、旧计划与已结束轮次不改写，且原两份联调日志及配置交付件历史不变。不重新上传或解析日志，不人工提交或完成任务。

当前记录不声称运行任务已完成，也不晋级整个Feature。

证据位于`.run/build/joint-task-completion-20261017/`：`tests.log`、`test-evidence.json`、`snapshot-regression.log`、`repair-review/`、`preview.json`、`preview-browser.json`、`plan-v7-preview.png`。复验入口为`accept-plan-browser.py`（默认只预览，`--apply`才生效）、`check-completion.py`、`accept-completion-browser.py`；后两项当前尚未执行。
