# 本地检查点转云端交接（2026-10-06）

本次仅按用户授权收束、提交并推送当前工作树；这不是功能验收完成。后续云端是唯一功能写入者。本地不再修复功能。仓库：`https://github.com/wrck/NPDMS`；remote：`origin`；分支：`fix/visio-template-followup`。提交前本地 HEAD：`faab7dd9564cb2d2d0dbee655538d140ae860c3d`；远端原 SHA：`fd999c9801981ebbe10b80f6d8ce2e8ddaef1994`，是本地祖先。最终检查点 SHA 以云端 fetch 后此分支 HEAD 为准。

## 继续工作的边界

- 不改变 PRD 业务语义，不夹带 V3/OUT_OF_SCOPE，不猜角色、Owner、Gate、状态转换、阈值；真实缺失规格才登记 BLOCKED_BY_SPEC。
- 新实体的普通接入保持 DO、纯 Mapper、schema、声明；公共基础闭环先于 WorkBinding/动态适配。不得逐实体复制 Controller/Service/Provider/Vue。
- 未放行的旧模型范围保持拒绝。历史、批准版本、来源证据与 native state/revision/freeze/原幂等必须保留。
- 四目标仍禁止新的实现写入：EngineeringBusinessModelContributor.java、views/pms/delivery-business/site-survey/index.vue、DeliveryEntityAccessPolicy.java、SiteSurveyDeliveryAccessPolicy.java；此前通用文件上传完成钩子拒绝也仍有效。本提交保留已有用户改动，不代表重试拒绝动作。
- 不部署、不连接生产/共享数据库、不使用真实账号。资源用独占 Compose 的 MySQL/Redis；JDK25/Maven、Node/Corepack/pnpm 宿主运行。

## 公共框架当前状态

1. 声明字段、操作权限与归属映射（测试 projectRef）、安全 default CRUD、服务端类型/required/readable、必带版本 CAS、持久 ledger/audit/outbox 已实现；第三普通实体没有专用生产入口。薄继承的差异钩子保留同一安全序列。
2. 无历史动态表单使用公共能力工厂、原 EntityForm/Extension API、公共 form/binding HTTP 入口与实际 form-create。必填更新省略保旧，显式清空拒绝；固定字段保存也必须校验已绑定必填扩展，防止绕过。最新 `DeclaredCapabilitiesRuntimePersistenceTest` 10 项通过（含固定字段绕过、雪花 ID 精确字符串与非法 ID 拒绝）。早期 required-form 1 失败/1 错误和 fixedOnly 红测试不视为通过，最新证据见 verification.json。
3. 刷新恢复仅保存 key、摘要和上下文定位信息，不保存业务 payload/Secret；GET 查询原持久回执，不重新执行未知意图。**迟到 A 回执误删 B 意图已经修复**：成功、明确拒绝及 GET 恢复清理都比较原 key 与摘要，并刷新当前 pending。新增多调用方乱序三项红测试全部复现，修复后意图套件 9 项通过；相关七套件 68 项通过。多调用方乱序的真实双浏览器验收仍未做。
4. 声明 identity alias、默认运行视图后端工厂及通用前端 Host 已实现，版本/稳定码/上下文/只读限制保留。实际 Chromium 已验证默认只读视图。全量 Spring/Tomcat 装配、旧 Owner 运行视图全面验收仍未完成。
5. 统一交付桥接使用声明 projectRef 映射与独立 UPDATE 权限、ProjectScopeApi 及项目 ACTIVE 写契约；native Owner 优先。桥接 MySQL 4 项通过。通用上传完成钩子没有实现，不能把外部 HTTP/送达当业务完成；各旧 Owner、完整上传/归档全链仍需继续验收。
6. 真实登录链已通过生产 AdminAuth/OAuth2、实际 SQL 角色/权限、实际 Redis token cache、Token/租户过滤器与方法权限。**跨租户 Redis 命中缺陷**原因为公共 guard 只核对 user ID，未核对 principal tenant 与 header/actor tenant，PMS 路由又不属于默认 `/admin-api` 租户过滤路径。已在 PermissionBusinessAccessGuard/TenantCallerContext 强制身份租户一致；缓存命中和冷缓存的跨租户负例均拒绝。与权限缓存异步失效窗口不是同一问题。真实 Chromium 登录后提交丢响应刷新：只有一次主体/ledger/audit/outbox；表单保存、必填显式清空 rollback、默认只读均通过。测试 HTTP transport 是 MockMvc 生产过滤器/控制器，**不等于整个部署应用登录验收**；ProjectScopeApi 在该 fixture 中为确定性的测试事实，完整真实 Owner 范围/全应用仍待验。

## Owner 适配待办

初始 38 个 Owner 范围待适配，已补四个经源实现审计的租户全局目录只读绑定：ACC/satisfactionQuestionnaireTemplate、PLT/formTemplate、KNO/announcement、PRJ/stageSuggestionRule；公共策略 MySQL 权限/跨租户/投影测试 2 项通过，四原生模块的真实整模块集成验收仍待做。**剩余 34 个保持拒绝**，精确清单见 owner-scope-inventory.json。公司/客户/设备复合权限、CUT 审批发起人/审批人及已决摘要、任务/交付件来源归属不能简单降为项目字段或租户放行。声明 alias 与另一无 alias 声明同名的冲突边界尚需补验。

## 当前已知失败与 NOTRUN

- 当前扩展后端回归整体失败：2 失败、12 错误，均在工程模块的三个测试类：engineering.framework.DeclaredBusinessRuntimePersistenceTest（10 errors）、SiteSurveyBusinessModelPersistenceTest（2 errors）、SiteSurveyPublicOperationPermissionTest（2 failures）。报错为新公共身份一致性 guard 拒绝测试 actor/principal/tenant 不一致。云端须核查真实调用契约及 fixture，保留失败测试，不能降低授权。其余被选中的平台、验收及 RA/工勘服务测试通过；不可宣称整个 29 交付链最终验收通过。
- 前端扩大检查：operationAdapters.runtime.spec.ts 7 失败（当前工勘 receipt/HTTP defaults mock 不一致）；presentationHost.runtime.spec.ts 导入失败（window 缺失，最近补的 auth fixture 尚未解决）。三个 node:test 文件误用 Vitest 收集的失败已用正确 Node runner 补跑，42 项通过；错误收集日志仍保留。最终相关七项公共框架套件 68 通过不覆盖上述失败。
- 常规回归两个 browser 条件测试跳过：旧 synthetic HTTP fixture 与新真实登录 fixture 都需专有交互运行。新真实登录 fixture 已单独实际运行 1 项通过并有 Chromium 结果；旧 fixture 跳过不算最新通过。
- 全量初始化/前向迁移、全部 Owner 真实 UI、共享权限缓存异步失效窗口、通用上传完成桥接、完整应用真实登录/启动和业务验收 NOTRUN/未完成；旧 32 文件冻结结果不能延伸到最新工作树。
- 新增/既有 SQL 迁移 V388–V397 随当前内容保存；本次未在生产或共享库运行。

## 本地安全收束与提交范围

当前工作树全部相关源代码、测试及迁移按显式审查清单提交，保留既有用户/旧实施改动；不盲目 add -A。不纳入三项：LOG_FILE_IS_UNDEFINED（运行日志）、docs/generated/2026-09-27-fullflow-test/browser/tmp-training.png 和 tmp-view.png（临时截图），原文件留本地。被忽略的 .run、target、node_modules、私有 C: 证据、缓存和临时 Compose 也不上传。所有候选文本做高置信度密钥/token 检查，无命中；唯一 password 字面值候选是 DO_NOT_PERSIST 测试哨兵。没有真实账号凭据上传。

自建 Compose 项目 npdms-declared-framework-4600 在核对标签与 tmpfs 挂载后删除；本次 Vite PID34064 已结束。27461、27462、27463、27464 均无监听，旧 27463 Vite 也已结束。不停用共享服务。

verification.json 保存检查摘要、每类结果、原日志 SHA256、Chromium 断言和限制（原始日志/截图留本地）。checkpoint-files.json 保存暂存文件内容 SHA256 与 Git blob；自身散列由最终交接回复提供，避免自引用。移交后只在云端 fetch/checkout 此分支继续。

暂存差异 `git diff --cached --check` 有一项未修告警：既有新增测试资源 `pms-module-engineering/src/test/resources/site-survey-isolated-mysql/V249__site_survey_typed_business_fields.sql:88` 末尾新空行。按迁移指令保留原文件；未将检查写为通过。

云端可读的两个既有方案源文档已按参考输入分类保存至 `docs/reference/unified-business-local-plans-20261006/`；原始字节及来源哈希见该目录 README。旧计划不是当前验收结论；未复制含 token 的运行文件。

两份来源参考 Markdown 保留原始字节，其中两空格硬换行也产生 trailing-whitespace 告警；不为检查点改写历史来源。全部暂存 whitespace 告警仅来自这两份副本和上述既有 SQL 末尾空行，仍不记录为通过。
