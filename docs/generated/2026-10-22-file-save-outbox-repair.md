# 文件保存与自动归集并发修复记录

来源：2026-10-22 用户要求继续排查并修复需求分析首次保存偶发文件错误、日志自动归集延迟，真实浏览器复验，保留其他会话改动，重启前确认。本记录为过程证据，不修改业务规格。

## 根因与修改

- 文件保存：`FileArtifactApiImpl.lockAndRevalidateReferenceSets` 先使用 RR 快照枚举附件并锁定文件，随后当前读可以看到快照之后提交的新附件；该附件不在已锁文件集合中，旧实现误报 `1010001014 / 文件Artifact不存在`。现对未纳入锁定集合的新文件或版本返回既有文件事实版本冲突；保持 Owner 授权、锁顺序、完整集合比较、真实文件缺失和不可用校验。
- 同页触发条件：需求分析附件仍在服务端校验时，保存按钮原本可点击。复用上传组件的 `isBusy()` 状态，在 `RevisionFiles` 汇总新增/替换上传，在 `EntityForm` 汇总各附件字段，上传结束前禁止保存及通过既有工作区守卫冻结、切换。失败或完成解除上传忙状态；不自动接受跨页面并发变更。
- 自动归集：Outbox 默认 RR 下扫描保留不同事件类型的不匹配行锁，同步运行的其他消费者使用 `SKIP LOCKED` 时可能领不到待处理文件事件。仅将 `claimDue` 事务改为 `READ_COMMITTED`，释放不匹配行锁。事件过滤、租户过滤、批量上限、投递后 CAS 和失败重试保持原有语义；未调整任务频率或批量修改历史事件。

## 验证

- 隔离 MySQL 复现使用新建随机 schema，结束后仅清理自身 schema。生产 Mapper XML、实际 `PlatformOutboxDeliveryApiImpl` 和 Spring 事务代理的并发测试：旧代码应领 50 条文件事件实际 0 条；修复后 50 条，同类已锁事件仍跳过，其他租户不可见。
- 文件回归用例在旧实现返回错误的文件缺失码，修复后返回版本冲突。后端定向测试共 42 项通过：文件 API 30、Outbox API 11、真实 MySQL 并发 1。命令入口 `.run/run-outbox-concurrency.ps1` 持有工作树构建锁并沿用固定 JDK/Maven/依赖库；日志 `.run/outbox-concurrency-tests.log`，失败证据分别为 `.run/outbox-concurrency-tests-red.log` 与 `.run/file-reference-consistency-tests-red.log`。
- 前端 `EntityForm.runtime.spec.ts` 和 `RevisionFiles.runtime.spec.ts` 共 16 项通过，覆盖上传状态传递、多个字段全部结束才允许保存、卸载释放状态和原有保存/历史行为。额外执行 `EntityPanel.runtime.spec.ts`：4 项通过、4 项因已有测试桩缺少 `validateCompletion` 失败，未修改其测试桩或业务实现。日志 `.run/file-save-outbox-ui-tests.log`。
- 全量 `vue-tsc` 未通过：既有 `DynamicFormFileFactVO` 缺少状态属性类型、配置日志分页参数可空、方案来源测试组件 props、任务恢复测试的 `initialProject` 类型错误。未把全量类型检查记为通过；日志 `.run/file-save-outbox-types.log`。

## 真实浏览器证据及边界

使用 `19191` 前端、`59191` 后端，界面创建隔离项目 `PJT2026000067`（ID `2110018733130579977`），示例客户、示例公司/办事处，工程类直签模板 v1；仅为该项目添加管理员作为项目经理和服务经理。需求实体 `2112982052722741249`，草稿修订 `2112982052722741250`。

1. 初次正常顺序上传拓扑并保存返回 `SAVED`。
2. 仅对该隔离修订持有 45 秒行锁（无数据修改），通过浏览器先上传后保存，复现上传提交后保存返回 `1010001014 / 文件Artifact不存在`；正文填写保留。锁自动回滚释放。原始响应见 `.run/file-save-outbox-browser/concurrent-save-before.json`。
3. 前端修复后，同样延迟上传完成请求时保存按钮不可用；上传完成后恢复可用。点击保存返回 `SAVED`，内容版本 3。证据位于 `.run/file-save-outbox-browser/upload-blocks-save.txt`、同名 PNG、`upload-then-save.json`。

后端构建通过，冻结产物：`.run/build/releases/b89624f6-ad5b-4d66-857f-f6e3f5be8f5b/manifest.json`，运行修复基线 22 项校验通过。构建入口 `scripts/worktree-build.ps1 -Target backend -MavenArguments @('-Dmaven.test.skip=true')`，日志 `.run/file-save-outbox-build.log`；测试已另行执行，打包未重复运行全套测试。

用户随后明确批准重启 59191。已使用上述冻结清单执行 `scripts/worktree-backend.ps1 -Manifest ... -ApprovedRestart`，退出码 0，22 项既有修复基线通过，后端健康检查通过，新进程 PID 44428，固定 `local,dac` 配置。部署日志 `.run/file-save-outbox-deploy.log`。未重启 DAC。

部署后的跨页面文件事实冲突、正常保存重试、配置及联调日志自动归集均已完成真实浏览器复验，细节见下。未提交、未推送，其他会话改动保留。

### 部署后浏览器复验（2026-10-22）

- 两个真实页面使用同一项目67的需求草稿。自然顺序下，B页新增第6个拓扑附件，A页随后修改普通字段并保存成功，新附件保留；旧页面本身不等于事务内旧快照，不把该顺序记作精确竞态覆盖。
- 精确竞态仅对该隔离修订持有45秒行锁，自动回滚、不写数据。第二轮在B页显示“上传完成，服务端正在执行适用校验”、观察到complete-upload请求发出后，再点击A页保存。B上传成功（Artifact `2113060500216487938`、Reference `872`），A保存返回 `1010001017 / 文件事实版本已变化`，不再误报文件不存在。响应与页面证据：`.run/file-save-outbox-browser/concurrent-save-after.json`、同名TXT/PNG。
- 正常重试返回 `SAVED`、`newConcurrencyBasis: 6`；随后明确确认刷新并重开，7个拓扑附件均仍已绑定，普通字段“冲突刷新重试成功20261022”保留。证据：`conflict-retry-save.json`、`conflict-retry-reopened.txt`。
- 第一轮保存排队早于上传校验，保存成功而上传返回 `1010001001 / 文件业务授权Provider不可用`，未把该轮记为成功上传或目标竞态覆盖；未删除其文件或修改批准历史。
- 配置和联调日志：项目67仍处S0，两个WBS任务均待分配，准入明确显示施工计划和实施方案未满足。任务工作区文件历史面板只有查询、刷新和关闭，无上传设备日志按钮；未通过全局入口绕过任务来源，也未推进项目阶段。证据：`configuration-task-readonly.txt/png`、`joint-task-readonly.txt`。经主代理代码核对，独立配置入口及自动解析任务的联调入口均为正式受支持来源，后续使用该入口验证，不推进任务状态。

- 通过设备管理正式新增仅属于项目67的合成设备，SN `PJT67-OUTBOX-20261022-01`，名称及备注明确非实物、隔离验收；人工补录原因和证据如实填写本次验收，不构造外部订单或真实资产凭证。
- 独立配置入口上传合成parser支持日志成功，UI显示2026-10-22 08:15:05归档1台、脱敏0处，解析在网版本 `PJT67-CONFIGURATION-OUTBOX-20261022-1.0`。交付件汇总显示原始source.txt和设备v1.txt均“已归集”，WBS配置任务“启用功能与配置Log”变为“已验收”；联调交付件此时仍“待提交”，未串入。证据：`configuration-archived.txt/png`、`configuration-auto-collected.txt/png`、`joint-before-upload.txt`。
- 独立联调入口上传成功，真实请求带 `originTaskId=2112981516619386890`，import `2113061775112937474`；UI显示08:16:00归档1台、版本v2，解析 `PJT67-JOINT-OUTBOX-20261022-1.0`，原件和设备分拆件material分别为 `9100000000000000470`、`9100000000000000471`。首次立即刷新时联调仍待提交；正常消费者随后自动归集，最终结果见下。证据：`joint-import-response.json`、`joint-archived.txt`、`joint-auto-collected.txt/png`、`log-deliverable-summary.txt`。

- 最终归集历史通过真实“交付件检查 → 上传 / 查看材料”确认：配置“启用功能与配置Log”已满足，业务文档自动归集时间08:15:30，材料只为该SN的v1.txt；联调“运行业务及联调记录”已满足，时间08:16:30，材料只为该SN的v2.txt。没有点击提交材料或重新检测。关闭材料窗口再重开联调任务，交付件仍已验收，任务仍待分配、进度0%，项目仍S0，准入未满足保持。证据：`configuration-submission-history.txt/png`、`joint-auto-collected.txt/png`、`joint-reopened.txt`。
- 主代理只读数据库核对的实际归集耗时为配置24.954秒、联调29.702秒（从import创建起算），8条文件事件均自动投递、retry为0。对应补充证据：`collection-timing.json`、`event-timing.json`、`submission-timing.json`。本次证明的是合成日志上传、解析归档和交付材料自动归集，不把文件事件送达或交付件满足等同于配置/联调业务任务完成。
