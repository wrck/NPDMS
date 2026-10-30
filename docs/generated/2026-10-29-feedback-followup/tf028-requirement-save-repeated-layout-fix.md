# 84575 需求分析保存慢：重复布局读取局部优化

2026-10-29。用户反馈 TEST_ONLY 项目 84575 需求分析保存很慢。本记录区分现存运行证据、源码可证的重复调用和本地修复，不把它们合并为已通过的浏览器性能结论。

## 运行证据与限制

- 本次浏览器保存约在 18:52–18:53，完成确认约 18:53:30；未取得 Network 精确耗时，不能断言慢在写事务或保存后回读。
- 当时 59191 PID 为 31956，日志为 `.run/logs/satisfaction-manual-backend.log`。18:52–18:54 窗口可见多个 HTTP 线程密集查询 SYSTEM 用户和组织范围，但业务模块 SQL 与 HTTP 边界未打印；仅按线程统计不能关联为一次需求保存。脱敏统计 `.run/feedback-20261029-requirement-save-sql-summary.json` 只保存 Mapper 名称及数量，不复制参数值。
- 只读查询 `infra_api_access_log` 的 18:51–18:55 需求相关 URL 未取得记录，结果 `.run/feedback-20261029-requirement-save-access-log.json`。随后的一次线程快照未捕捉正在处理的 HTTP 调用。现有运行证据不足以给出本次保存/完成的精确阶段耗时。

## 已确认的重复工作与最小改动

`RequirementAnalysisEntityProvider.save -> files.registerSaved` 与 `freeze -> files.lockForCompletion` 均需检查附件集合，并通过 `FileArtifactApiImpl.lockAndRevalidateReferenceSets` 逐集合重验 Owner。`RequirementAnalysisRevisionFilePolicy` 原来每个集合都调用 `forms.layout`；`EntityFormService.layout` 又进入 `registry.requireReadable` 并读取、解析表单 Schema。

对 Owner 固定核心附件键，原策略已直接以 `RequirementAnalysisFields.attachmentKeys()` 判定有效，后续判断完全不使用布局。此次只把布局解析移到额外自定义附件键需要的分支。核心键仍先执行当前 `access.read`；每次锁重验仍执行原 expectedScopeVersion 校验及 `mapper.lockRevision`/写入的 `access.lock`；PLT 后续 Artifact、Version、Reference 锁及精确文件事实对比不变。

已有 READ 批内复用保留，`Inspection.layoutLoaded` 明确区分“未读取”与“读取后没有布局”。同一批核心键先于自定义键时，自定义键仍会加载 Schema；反向顺序也保持验证。没有跨请求或跨事务缓存，没有增加 PLT API、SQL、DDL或业务字段，没有改变事务、附件用途/大小/媒体类型、状态机与历史保护。

本次生产文件只有 `RequirementAnalysisRevisionFilePolicy.java`。精确增量 `.run/feedback-20261029-requirement-save-production.diff`；修改前生产文件和测试文件保存于 `.run/feedback-20261029-requirement-save-before/`，区分工作树原有并行改动。

## 验证与审核

失败测试先行：`.run/feedback-20261029-requirement-save-red.log` 中核心集合查询、逐集合锁重验两例准确复现不必要布局读取；其余 45 项保持通过。

修复后 `.run/feedback-20261029-requirement-save-green.log` 共 47 项通过（PLT 34、ENG 13）。独立审核建议收紧错误码并补边界后，仅重跑受影响 `RequirementAnalysisRevisionFilePolicyContextTest`，8 项通过，见 `.run/feedback-20261029-requirement-save-final.log`。去重有效检查为 49 项：Context 8、TopologyAttachment 7、FileReferenceSetPolicyBatch 4、FileArtifactApiImpl 30。`git diff --check` 两文件通过。构建和测试遵循 `scripts/worktree-toolchain.ps1` 的 JDK25、工作树 Maven 仓库和同一互斥锁。

测试覆盖：全核心集合不解析无用布局；核心/自定义键两种顺序；未知自定义键及缺失布局拒绝；自定义 Schema 异常不吞掉；核心逐集合锁保留；陈旧 scope 精确返回 `FILE_SCOPE_VERSION_CONFLICT`；查询和锁阶段撤权均拒绝且不触碰 Mapper；核心写仍要求当前经理与可变草稿。既有测试继续覆盖不可用拓扑、完整文件事实比较和文件版本变化。

sol-6.1 高推理独立审核未发现生产问题；本记录不代替审核代理自己的回执。

## 未验证边界

本次没有部署、重启、迁移或业务写入。运行实例尚未包含本次生产增量；只能说明已消除一处可证明的重复 Owner 读取/Schema 解析，不能给出真实浏览器提速数值或宣称“保存慢已验收”。后续冻结部署后应以正常阶段任务保存/回读分别计时，并保留失败及附件历史保护验证。

## 冻结产物（19:05）

通过 `scripts/worktree-build.ps1 -Target backend -MavenArguments @('-DskipTests','-DskipITs=true')` 完成 42 模块 clean/package，构建 2 分 51 秒成功。该次打包跳过测试；上文 49 项检查仍为适用的独立定向验证，不写成全量测试通过。

- 清单：`.run/build/releases/28901f37-9b90-41e6-87fa-b1d125467ddd/manifest.json`。
- JAR SHA-256：`f1acc3e5b58bc678eb109c90e23cf11b0098c2ce1e3d17ae5ed4b4db9ffdd77d`。
- 后端 Maven reactor 源码指纹：`f0ca0f3b9c1be88cd30aced3b3cfee1eba1264d01db1c1510f9b31c879a03f49`，构建前后及冻结后当前树一致。
- 冻结 JAR 中 `RequirementAnalysisRevisionFilePolicy.class` 及 `$Inspection.class` 与本次目标目录字节完全一致，二者包含 `layoutLoaded`；22 项已部署修复基线保护通过。
- 固定 `worktree-backend.ps1 -ValidateOnly` 通过 `local,dac`、受保护 178 测试数据库、禁迁移及 UMC 配置校验，未操作服务。固定启动入口中 Flyway、Liquibase、SQL初始化、Hibernate/Flowable/Quartz/Batch 自动建表及 Druid DDL 均保持关闭。
- 冻结时运行 JAR SHA-256 为 `8b48e0fb2421dddd16ad380b1002e4662082587b2a502c124732f7e42bbafb17`，核对仍未变化；新包未部署，未重启 59191/48182 或前端。

回执 `.run/feedback-20261029-requirement-save-freeze-receipt.json`；日志 `.run/feedback-20261029-requirement-save-build.log`、`.run/feedback-20261029-requirement-save-precheck.log`。完整冻结包包含构建时工作树已有并行增量，不宣称只包含本次性能修复。

## 方案下载观察的边界

并行浏览器观察从“60 秒内未捕捉到下载事件”补充为“两个附件的预览内容响应均成功，按钮最终恢复”。源码在 PDF 全页转换、Word 生成、交付材料登记均成功及身份重验后才执行下载；错误会写入章节页顶部 `solutionDownloadError`。因此尚不能仅由下载事件未捕捉判定业务失败或 PDF.js 永久挂起；继续由并行审核结合实际请求、页面错误和下载原件定位，本次不修改该前端链路。
