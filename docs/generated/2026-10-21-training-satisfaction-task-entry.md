# 现场培训、满意度任务入口与启动提示修复

来源：用户反馈项目 `992203060197` 的现场培训、满意度调查表没有对应业务入口，点击开始任务报错。仅修复既有页面入口及任务开始条件展示，保留其他会话改动。

## 当前事实与原因

- 项目当前为 ACTIVE/S5。现场培训任务 `2107837711337496593`、满意度任务 `2107837711337496594` 均为 PENDING_ASSIGN，冻结契约分别绑定已发布的 `IMP_TRAINING_CONFIRMATION`、`ACC_SATISFACTION`。培训和满意度专业工作台均已存在，无需新造业务页、角色或完成状态。
- 用现有项目经理 `y06496` 登录后的只读请求确认：两个工作台及业务上下文均可查询，未出现缺失视图或 Owner 查询拒绝；工作台却都返回 START。业务上下文因尚未开始返回只读，培训引用视图本身还仅支持 QUERY，因此不能把“有任务开始按钮”当作已存在完整业务办理入口。脱敏证据 `.run/training-satisfaction-readonly.json`。
- 既有拒绝审计显示两任务启动返回 `1014024046`（项目任务命令不符合当前规则）。有效计划 `2104870198458802264` 的不可变规则明确为：`ACCEPT_TRAINING_ENTER` 要求 `ACCEPT_FINAL` 完成；`ACCEPT_SATISFACTION_ENTER` 要求 `ACCEPT_TRAINING` 完成。197 当前终验未完成，培训也未完成；服务端不应放行启动。
- 查询侧原来只有 APPROVAL 类型使用同一冻结准入预览过滤 START，业务组件任务未过滤，也未展示缺少的前置条件，造成“按钮可点、实际拒绝”的不一致。

## 修改

- `ProjectTaskQueryService` 对 BUSINESS_OBJECT/BUSINESS_COMPONENT 的 START 复用已有准入预览。仅 MATCHED 保留 START；NOT_MATCHED、UNKNOWN 均不开放。保留其他已获授权操作，受限任务用户仍看不到不可见前置任务名称。服务端启动命令校验原样保留。
- `ProjectFlowPanel` 展示“开始前请先完成：终验/现场培训”或条件暂不可用提示，不将查询失败当作满足条件。
- 现场培训、满意度调查继续使用任务中已有的内嵌工作区。按用户后续纠正，撤回本会话新增的两个“进入…”跳转按钮和回调，保留内嵌页面、前置提示及原工作台权限、项目范围和离开保护；未修改其他会话原有代码。
- 不调整终验→培训→满意度的冻结顺序，不改批准版本、历史快照、业务记录、角色授权、任务状态或迁移数据。

## 当前部署与浏览器验收

- 用户明确批准后，已由 `scripts/worktree-backend.ps1` 部署冻结包 `c9431bcd`；59191 PID 为 10328，健康检查 UP、配置 `local,dac`、22 项既有修复基线验证通过。48182 前后仍为 PID 33644，未操作。日志 `.run/training-satisfaction-deploy.log`。
- 部署后使用项目经理 `y06496` 只读验证：培训、满意度均不返回 START，分别明确缺少终验、现场培训；业务查询仍可用。任务仍分别为 PENDING_ASSIGN/version 2、PENDING_ASSIGN/version 3。证据 `.run/training-satisfaction-after-deploy-summary.json`。
- 撤回跳转按钮后，2 个受影响组件文件 **35 项通过**，日志 `.run/training-satisfaction-inline-only-after.log`；此前已通过且未再修改的任务状态动作 5 项沿用原证据。新增按钮对应的测试随功能一并撤回，不计入当前覆盖。
- 真实浏览器确认两个任务均保留内嵌工作区、没有新增跳转按钮、没有 START，前置提示分别为终验和现场培训；未开始、上传、保存、提交或审批现有业务。证据 `.run/training-satisfaction-inline-only-browser.json`。无迁移、未提交或推送，其他会话改动保留。

## 早期构建记录（当前结果以上方收口为准）

- 后端 `ProjectTaskQueryServiceTest` **21 项通过**；新增两种业务绑定均覆盖 MATCHED/NOT_MATCHED/UNKNOWN、其他操作保留及受限前置名称隐藏。修改前新增 2 项失败，修改后无失败。日志 `.run/training-satisfaction-backend-before.log`、`.run/training-satisfaction-backend-after.log`；固定工具链与工作树独占锁入口 `.run/test-training-satisfaction.ps1`。
- 前端 4 个相关组件测试文件 **46 项通过**：两个入口与父层页签回调、无 Owner 查询权限/冲突身份/错误项目不展示、明确前置提示、UNKNOWN 不放行、已有任务命令版本与幂等、Owner 失败保存和离开保护。新增入口修改前 2 项失败，前置提示修改前 3 项失败。日志 `.run/training-satisfaction-ui-before.log`、`.run/training-satisfaction-admission-ui-before.log`、`.run/training-satisfaction-ui-after.log`。
- 浏览器恢复发现仍无可用浏览器，本轮未点击业务任务的开始、提交或审批；真实浏览器验收尚未完成。记录 `.run/training-satisfaction-browser-before.json`。只读接口、组件检查及构建均不能替代该验收。
- 本次无需迁移；后端新查询行为需部署后生效。尚未授权本轮重启，未操作 59191 或 48182，未提交或推送，其他会话改动与既有产物保留。
- 固定入口完整构建及 22 项既有修复基线通过；冻结包 `c9431bcd-3e41-47d1-a898-53f29e82349a`，清单 `.run/build/releases/c9431bcd-3e41-47d1-a898-53f29e82349a/manifest.json`，构建日志 `.run/training-satisfaction-build.log`。只读部署预检通过，日志 `.run/training-satisfaction-preflight.log`，未更改服务。
- 收口只读复核：197 终验仍 PENDING_ASSIGN/version 3，现场培训仍 PENDING_ASSIGN/version 2，满意度仍 PENDING_ASSIGN/version 3；未为验收推进现有业务。19191 提供的项目详情及任务办理源码模块均 HTTP 200，已包含工作台入口回调；记录 `.run/training-satisfaction-vite-served.json`，不能据此宣称真实浏览器验收通过。

## 满意度操作栏刷新误报（2026-10-21后续）

- 用户反馈项目197满意度工作区操作栏点击刷新报“业务完成依据暂不可用”。真实浏览器仅点击一次刷新：HTTP200/业务码0，但结果为`completed=false, unknown=true`；随后Owner上下文查询成功、无recoverableError，业务关联为空。工作区没有完工证明或调查记录，任务仍IN_PROGRESS/version4。证据 `.run/satisfaction-refresh-browser-before.json`。数据库只读核对完工证明、满意度任务均为0条，见 `.run/satisfaction-refresh-native-before.txt`。
- 根因在`ProjectTaskPlanCompletionService`：已成功锁定查询的Owner事实即便带有有效聚合版本，只要关联集合为空也返回UNKNOWN/BUSINESS_FACT_VERSION_MISMATCH；前端依据unknown显示真实查询故障提示。没有业务记录是尚未办理，不应被当作Owner读取失败。
- 本次仅调整该分支分类：聚合事实缺失、版本缺失或空白仍UNKNOWN；已确认的空集合与尚未完成办理统一返回NOT_MATCHED/BUSINESS_HANDLING_PENDING。此分支位于规则求值之前，因此ANY、ALL、NOT及CONSTANT true均不能让空集合完成任务。原生完成条件、权限、项目范围、执行轮次、并发版本、客户签字/评分/归档要求及历史均不改，不自动创建完工证明或调查。
- 新增空集合场景修改前失败（预期NOT_MATCHED，实际UNKNOWN）。修改后48项后端定向检查通过，覆盖任务计划完成判定、刷新授权/版本/执行校验、Owner异常和关联失败不转成功、真实业务规则及空集合不放行。固定工具链与独占锁入口 `.run/test-satisfaction-refresh.ps1`；日志 `.run/satisfaction-refresh-backend-before.log`、`.run/satisfaction-refresh-backend-after.log`。未修改前端错误显示分支来掩盖unknown。
- 修复后的浏览器验收依赖部署新后端；本次复现不算修复后验收。尚未重启59191或操作48182，未开始、创建、保存、发起调查或审批该项目业务，未提交或推送，保留其他会话改动。
- 固定入口完整构建和22项既有修复基线通过，冻结包`4e36d2d7-64a0-4ad6-bceb-ed1ad7e57920`，清单`.run/build/releases/4e36d2d7-64a0-4ad6-bceb-ed1ad7e57920/manifest.json`；只读部署预检通过，配置local,dac，未更改服务。日志`.run/satisfaction-refresh-build.log`、`.run/satisfaction-refresh-preflight.log`。无需迁移；待本次重启批准后，仅验证197满意度刷新返回未完成且不误报，不新建完工证明、不发起或提交调查。
- 用户随后明确批准本次重启和验收，已通过固定入口部署上述产物：59191 PID47296→24184，local,dac健康，22项既有基线通过，48182监听进程均未操作。日志`.run/satisfaction-refresh-deploy.log`。
- 部署后真实浏览器fresh进入197满意度工作区，仅点击操作栏刷新一次：HTTP200/业务码0、`completed=false, unknown=false`，错误提示不再出现；仍是IN_PROGRESS/version4、Owner可写，原“新增完工证明”按现有权限正常显示且可用，空列表保留正常待办说明。初验、终验仍完成100%。证据`.run/satisfaction-refresh-browser-after.json`、截图`.run/satisfaction-refresh-browser-after.png`。部署后只读数据库核对任务版本不变，完工证明和满意度记录仍为0条，见`.run/satisfaction-refresh-native-after-deploy.txt`；本轮未创建、保存、发起调查、提交审批或修改工期，自建页关闭且保留用户会话。无需迁移，未提交或推送，其他会话改动保留。
