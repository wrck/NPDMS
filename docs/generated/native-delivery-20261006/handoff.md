# 原生业务统一交付件增量与验收交接

到货原生页面已修复上传后自动归集、归集失败重试、重开恢复真实引用及统一清单刷新；需求分析页面已补统一材料台账；旧方案客户文件已接到现有公共上传和原生 attach。到货写权限、删除权限、占位项目范围及范围版本重检缺口已修复；新增原生 Owner SPI 授权公共撤回，旧材料撤回后公共完成恢复。核对清单附件也已通过原生文件策略、来源、保存/提交事务接入同一材料实体，并修复旧页面字段/版本接线。到货与核对清单限定链通过真实 Chromium、生产控制器/服务及独占 MySQL；全部原生业务尚未验收完成。

基点是 `fix/visio-template-followup` / `fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721`，已 fetch 核对。先读了[原交接](../2026-10-06-cloud-checkpoint/handoff.md)与[两份历史方案](../../reference/unified-business-local-plans-20261006/README.md)。没有创建项目、迁移项目详情、修改公共框架/交付底层/API合同、执行真实迁移、部署、提交、push 或 merge。四个禁改目标及全局 `FileUpload.complete` 自动登记钩子均保持原边界。

## 可整合的代码

| 目标 | 最终行为 |
| --- | --- |
| `engineering/service/arrival/ArrivalDeliveryRegistration.java` | 原生更新与删除分别检查自己的业务权限；MANAGE 范围排除占位项目并重检范围版本。 |
| `engineering/service/arrival/ArrivalNativeDeliveryAccess.java` | 使用现有 Owner SPI 授权公共材料操作：真实租户/主体、query+update、状态0/2、MANAGE、非占位项目、锁后范围及 ACTIVE 生命周期；不开放第二个 PLT 匿名文件槽。 |
| `engineering/service/arrival/ArrivalServiceImpl.java` | 删除先验证真实 Owner 写范围，再保留既有状态及版本 CAS。 |
| `engineering/service/arrival/ArrivalFilePolicyProvider.java` | 上传、替换、挂接、解绑使用 MANAGE；读取使用 VIEW；变更只允许状态 0/2，锁后范围版本变化拒绝。 |
| `views/pms/engineering/arrival/index.vue` | 公共上传成功后读取并保存已持久化 Owner，由既有保存服务登记真实来源材料；不自动提交表单中的其他未保存编辑。归集失败保留文件和重试入口；成功刷新同一个公共清单；显式撤回实际替换/解绑的旧材料，拒绝根据不可读取文件的 null 结果撤回。重开从材料身份与完整原生业务键恢复实际引用，读取失败阻止误开新上传。文件事件绑定源 Owner，旧页面迟到事件不能写到新记录。 |
| `views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue` | 历史与当前修订均展示同一真实实体根的只读公共材料清单；保存后刷新，不把 revision ID 当 entity ID。 |
| `views/pms/engineering/solution/index.vue`、`SolutionChapterForm.vue` | 旧客户 URL 上传替换为既有客户方案选择器、公共两段上传和原生 attach。先保存真实根；失败保留文件/已完成引用、显示错误，重试不重复保存未改变的草稿或上传；保留其他 remark 字段和历史 URL，显示原生文件定位符。 |

| `acceptance/service/deliverablechecklist/ChecklistAttachmentFilePolicy.java`、`ChecklistAttachmentSources.java`、`ChecklistAttachmentRegistration.java` | 复用文件/来源 SPI，草稿0可写，其他状态只读；query/update、真实主体、MANAGE/VIEW、项目 ACTIVE/占位/范围重检；沿用旧 doc/xls/ppt/txt/pdf、5MiB。原生来源为 ACC.CHECKLIST_ATTACHMENT，独立于 PASS 结果类型。 |
| `acceptance/service/deliverablechecklist/DeliverableChecklistServiceImpl.java`、`acceptance/service/acceptance/AcceptanceResultDeliveryAccess.java` | 保存成功后归集锁定真实附件；提交前归集再冻结；有已登记材料拒绝移项目。现有 ACC Owner 仅对附件来源委派文件策略授权公共撤回；保持既有成果写入和通用上传拒绝。 |
| `views/pms/acceptance/deliverable-checklist/index.vue`、`ChecklistAttachments.vue`、原生 API TS 类型 | 删除旧裸上传；创建后保留真实根，上传/原生保存/失败重试/公共台账/撤回；读取已持久化 Owner，不保存表单脏编辑。实际 DTO 是 deliverableUrl 与数字 CAS version，旧 attachmentUrl/字符串版本接线已纠正。历史 URL 保持只读。 |

工程 Java 路径相对 `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/`，验收 Java 路径相对 `pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/`；Vue 路径相对 `yudao-ui/yudao-ui-admin-vue3/src/`。对应测试与全部改动文件见 [verification.json](verification.json)。所有变更仍在工作树，供父任务按文件整合。

## 旧统计核对和实际未接入清单

旧「32 组生产入口 / 29 条写路径」不能作为当前通过数。当前已提交交接和两份参考方案没有可逐行复算的同口径 32/29 清单；源码中的工勘 `savefile` / `confirmresult` 也已不存在。当前命令是 `SiteSurveyEntityDomainCommands` 的 create/update/confirm/archive 及表单保存。没有把旧 preparation 的 `SITE_SURVEY_ITEM` 文件策略冒充当前 `SOL/siteSurvey` 接入。

[可重生成的源码索引](source-inventory.json)列出完整文件、行号、调用标记：16 个直接文件策略实现，20 个原生材料登记调用点，以及 11 个组件中的 13 处 `<UploadFile>`（本次旧方案客户文件、核对清单替换前是 15 处）。**调用点不等于写路径，组件不等于生产入口；这些数没有装配/业务通过含义。** 例如培训多个命令复用生成 helper，需求分析保存/完成/复制复用文件 helper。CUT 和新到货验收不计入生产已通过。

| 实际缺口 / 目标 | 当前证据与需要的下一步 |
| --- | --- |
| 六个 Host DELIVERY 声明 | `EngineeringBusinessModelContributor.java` 中 SOL/requirementAnalysis、siteSurvey、solution、briefing 和 IMP/training、arrival 均缺 DELIVERY；ACC 五个已有。目标文件仍是明确禁改项，未用别的文件重新声明绕过。原生显式面板修复不代表 Host 已完成。 |
| 当前 SOL/siteSurvey 材料归集与完成事实 | 当前服务、表单及来源目录中未找到完整原生材料登记链。页面和两个访问策略目标仍受禁止；state3 是否追加材料未决定。不能通过修改允许的邻接文件绕过这些目标。 |
| 历史培训无授权记录分支 | `TrainingServiceImpl.renderAndUpload(employeeGeneration=false)` 仍有 `fileApi.createFile` 匿名旧分支。历史链接重发失效策略未决定；未回填授权、假造 file/revision 或替历史重新生成快照。新授权删除/撤销后禁止降级的已有保护经 14 项授权测试复验。 |
| 两套方案拓扑文件 | `solution/SolutionChapterForm.vue` 的 asIsUrl/toBeUrl 与 `solution-reviewed/SolutionReviewChapterForm.vue` 的同名字段仍为旧 UploadFile URL。应新增独立原生拓扑来源；复用目录图像媒体时才需要目录协调。原生方案状态授权须沿既有服务，不能复用仅适用已生成或客户文档的用途来绕过。 |
| 旧方案客户文件（本次已修复） | `solution/SolutionChapterForm.vue` 的 customerPlanUrl 已复用 `solution-reviewed` 的公共两段上传及 `SolutionCustomerDocumentService.attach`；运行回归和真实浏览器均通过，浏览器 HTTP 是显式夹具，尚未补该实体完整真实后端/数据库全链。 |
| 交底手工附件 | `briefing/index.vue` 的 fileUrl 仍旧上传；已生成交底走 `NativeGeneratedFileApi`。现有 `BriefingGeneratedFilePolicy` 明确只支持生成快照，公众文件变更不可用；手工上传需要协调原生用途/媒体契约，未擅自扩大生成策略。 |
| 验收旧附件 | `acceptance/acceptance/index.vue:175` 的 attachmentUrl 仍旧上传；它的根为 ACC/acceptance，不能与新 ACC/acceptanceActivity 报告根互换。核对清单旧上传已修复，PASS 结果与附件来源仍分开。 |
| 其他工程附件 | configuration/configLogUrl、joint-test/evidenceUrl、ext-proc/attachmentFiles、outsource/attachmentFiles、material-req/attachmentFiles、material-exch/reasonFiles、announcement/fileUrl 仍旧上传。精确组件及行号在源码索引；尚无本次验证的原生归集链；可做的原生工作与具体依赖已逐项列在末节，未笼统视为公共阻塞。 |
| 旧准备项/施工计划变更 | Preparation 与 ConstructionPlanChange 有受控文件策略，不能据此声称其材料已自动进入统一实体。来源映射和材料登记仍待协调/验收。 |
| 替换旧材料后的完成恢复（本次已补） | 新增到货原生授权适配与页面显式公共撤回。真实浏览器证明：旧 ACTIVE 失效材料使判定失败；撤回后旧 WITHDRAWN/新 ACTIVE、两个版本保留，公共判定恢复已满足，Owner 版本不变。后台自动撤回另见公共合同建议，未假定已经实现。 |

## 按实际命令链填写的端到端验收矩阵

PASS 只对写明的层级和夹具成立；UNIT 为实际服务/授权回归，未替代真实业务全链。NOTRUN 和 BLOCKED 均不算完成。

| 入口 / 命令链 | 同一实体材料接入 | 本次实际验证 | 端到端结论 |
| --- | --- | --- | --- |
| RA 原生表单保存 → registerSaved/lockForFreeze | 真实 revision 文件归根实体；材料与用途分开 | 文件批量 2、访问 6、成果证据 8；前端当前/历史根身份测试 | UNIT；完整上传/保存/复制/历史浏览器 NOTRUN |
| RA complete → activated；copy → copyImmutableReferences | 已有业务成果及真实旧版本复用 | 上述文件/证据回归；新增统一台账 | UNIT；完整业务全链 NOTRUN |
| 当前工勘 save/confirm/archive | 缺完整材料来源/原生归集 | 源码核对，没有更改禁改目标 | BLOCKED；state3 规则及声明/策略边界未解除 |
| 旧方案客户文件 → 原生根保存 → 上传 → attach | 本次原生页面接线已完成，保留历史 URL、其他 remark 字段与失败后的引用 | 原页面运行回归；**真实 Chromium + 实际 Vue/helper + 显式 HTTP 夹具**，一次保存/一次上传/两次 attach，重开显示原生定位符 | UI 限定 PASS；真实后端/数据库该链 NOTRUN，未替代已有服务 15 项 |
| 方案客户文件（reviewed）→ attach | 公共两段上传 + 原生锁/版本 + 同一材料 | 服务 15；前端客户文件 helper 回归 | UNIT；真实方案浏览器/数据库全链 NOTRUN |
| 方案生成 HTML → uploadDeliveryFile | 已有公共文件/材料 helper | 共享上传套件 | UNIT；原生生成/下载全链 NOTRUN |
| 方案原审批通过 / 分级审批结果 | 已有 BUSINESS_RESULT，保留实际版本/审批锚 | 成果证据 4、分级审核服务 10 | UNIT；完整 BPM/浏览器 NOTRUN |
| 交底 generate → NativeGeneratedFileApi | 已生成真实文件和材料；手工附件另有缺口 | 生成 3、原生生成文件策略 4 | UNIT；真实生成/审批/发布/下载全链 NOTRUN |
| 新培训 issue/reissue/HTML/PDF | 已有受控生成与新授权 | 培训服务 7、授权 14、成果证据 5；打印下载组件 | UNIT；完整真实授权/浏览器/生成文档 NOTRUN |
| 新培训授权 confirm → grant file + native result | 不允许撤销/删除后降级匿名 | 授权 14、业务授权文件上传 3 | UNIT；完整客户确认 E2E NOTRUN |
| 历史无授权培训确认/重发 | 业务结果存在，但匿名文件分支未统一 | 明确保留既有分支和未决策略 | BLOCKED_BY_SPEC，未实现历史策略 |
| 到货上传 → 原生 update → materials/requirements | 公共上传、真实文件来源、同一材料 ID，既有 Owner 保存注册 | **真实 Chromium + 控制器方法权限 + 生产服务 + 独占 MySQL**；一次上传一材料，实体/项目查询一致，未保存备注不入库 | **限定链 PASS**；完整部署应用登录 NOTRUN |
| 到货替换 → 注册失败 → 重试归集 | 文件先成功；失败事务回滚 Owner；重试复用现有上传 | **Chromium + MySQL**：版本数/会话数都是 2，材料 1→2，Owner 版本失败不变/成功只加 1 | **限定链 PASS**；显式原生授权撤回后完成恢复也通过 |
| 到货 withdraw / ADD_VERSION / detach → common completion | 原始版本保留；撤回不计数；替换/解绑失败闭合 | **MySQL**：公共 evaluateCompletion 撤回后 false，替换/解绑拒绝；不伪造历史 | **限定失效判定 PASS** |
| 到货 sign / update / delete / 文件变更授权 | 签收生成 RECEIPT；自己的权限、范围、状态、CAS | 策略 16、注册 6、命令 4、新 Owner 7；MySQL 注册回滚、跨租户、已签收写/删拒绝 | UNIT + 限定集成 PASS；完整角色/项目范围真实集成 NOTRUN |
| ACC 原生报告 saveDraft / submit | direct 独立活动登记真实文件；绑定投影保留原要求 | 报告命令 2、原生访问 12、结果访问 11 | UNIT；原生完整报告上传/结果/归档 E2E NOTRUN |
| ACC 原生满意度 response / reservation / decision / generated file | 来源文件/生成文档及结果已有接入 | 六套件 19 项，包括原生报告；授权文件上传另测 | UNIT；完整客户/代办/生成/归档浏览器 NOTRUN |
| ACC completionCertificate customerConfirm/archive | 真实业务成果登记 | 服务 10 | UNIT；完整原生办理浏览器 NOTRUN |
| ACC archiveDocument archive | 原生结果登记与来源验证 | 原生服务 11 | UNIT；完整归档/下载/失效 E2E NOTRUN |
| ACC deliverableChecklist draft update / submit → attachment registration；withdraw | 独立来源 ACC.CHECKLIST_ATTACHMENT 归同一根，材料不是 requirement/submission；提交后禁止写/撤回 | **Chromium + 真实控制器/服务 + MySQL**：上传、失败回滚、复用重试、实体/项目同一材料、撤回不计数、历史保留；MySQL 提交冻结负例 | **限定链 PASS**；完整部署登录/实际项目角色 NOTRUN |
| ACC deliverableChecklist check-pass | 既有 PASS 业务结果继续登记，附件为另一个来源用途 | 原生服务 7、文件/来源/归集 7、成果访问11回归 | UNIT；完整核对/关联验收 E2E NOTRUN |
| CUT 与新 arrivalAcceptance | 不以源代码/依赖声明替代生产装配 | yudao-server POM 当前含 CUT 依赖；没有本次完整 Boot 装配/生产入口证据 | NOTRUN，不计入生产通过 |
| 全部 Host、通用默认框架/泛型 delivery | 交由并行云端任务 01a10fe7；六声明仍受禁改 | 本次未改公共文件 | 等待父任务协调整合，NOTRUN |

## 测试与证据

最新后端定向回归按类去重 **34 类 / 245 项，0 fail/error/skip**（按最后一次同类成功结果去重；不是一次全仓运行）：原有227，加到货 Owner7、核对清单文件7、既有核对服务新增3、独占 MySQL新增1。前端 **9 文件 / 62 项通过**。这不是全仓测试或全部生产业务验收。明细、日志哈希及范围见 [verification.json](verification.json)。

浏览器证据：[数据库与断言](arrival-browser.json)、[实际页面截图](arrival-browser.png)。两段 HTTP 使用真实 FileArtifactController，Owner 保存使用真实 ArrivalController，清单和判定使用真实 DeliveryController；方法权限与事务代理开启。夹具使用已认证 actor17/tenant7、确定性的权限/ProjectScope/项目 ACTIVE 外部端口和保留真实 bytes 的技术存储替身，**不等于完整应用登录、真实项目树授权、完整存储/下载或部署验收**。类型及要求为专用测试夹具，未改生产义务；schema 由当前 DO 推导，未运行 Flyway。

旧方案浏览器证据：[断言与实际请求](solution-customer-browser.json)、[截图](solution-customer-browser.png)。使用原生实际 Vue 和既有上传/attach helper，HTTP 响应是独立夹具，未冒充真实后端或数据库。核对清单另有 [数据库与浏览器断言](checklist-browser.json)、[实际截图](checklist-browser.png)，与到货共用独占 MySQL/真实控制器夹具；两个根没有合并。

独立运行 `python3 scripts/tests/run_native_solution_customer_browser.py`；这条浏览器验证补充了已通过的真实服务单测，未使完整业务全链晋级。

复验：JDK25/Maven、现有锁定前端依赖、Python Playwright、`/usr/bin/chromium` 与 Docker 可用后运行 `bash scripts/tests/verify_native_delivery_mysql.sh`；需要 Maven settings 时设置 `NATIVE_DELIVERY_MAVEN_SETTINGS`。脚本仅创建自己命名的 tmpfs Compose MySQL、固定 localhost28461 专用 schema，拒绝复用已有同项目容器，退出自动清理。生成源码索引用 `python3 scripts/tests/capture_native_delivery_inventory.py`。本环境日志位于 `.run/native-delivery-20261006/`。

保留了失败证据：最初写策略负例确实失败；首次前端新增行为测试失败。真实浏览器还发现本增量的 `.bind(...)` 事件表达式只返回函数，上传成功但归集未触发；已修复并增加通过实际模板事件触发的回归。之后测试夹具补齐现有 RECEIPT 种子、正确的控制器事务代理和失败注入条件；未修改生产事务/校验规则来让测试通过。

## 确切剩余行动与协调事项

其余 URL 入口不是一个公共 API 阻塞：可沿用本次核对清单做法新增原生文件策略/来源/原生事务归集。下面的 UNIMPLEMENTED_NATIVE 是尚未实现的原生任务，不冒充未获授权项，也不计生产通过。

| 具体未接入入口 | 实际根 / 现有写状态 / 实施方案与限制 |
| --- | --- |
| `engineering/configuration/index.vue:121` configLogUrl | IMP/configuration；update 状态0/1/3。原生可接入；须保留 ConfigurationServiceImpl.archiveConfigLog 的 DeviceConfigLogRecordApi 设备档案写入，不能把 file locator 当外部 URL 直接替换。需新增原生文件定位符到设备档案的明确消费者合同或独立文件材料展示；当前无这条桥。 |
| `engineering/joint-test/index.vue:188` evidenceUrl | IMP/jointTest；仅非终态0/1可写，2/3终态。可原生 SPI+source+update/结果冻结归集；沿用现有 log/txt/cfg/conf/doc/xls/ppt/pdf。UNIMPLEMENTED_NATIVE。 |
| `engineering/ext-proc/index.vue:305` attachmentFiles | IMP/externalProcurement；update草稿0/驳回4。原生多附件 SPI+source+事务保存/提交冻结，保留审批证据，不以审批 HTTP 成功完成。UNIMPLEMENTED_NATIVE。 |
| `engineering/outsource/index.vue:283` attachmentFiles | RES/outsourceRequest；update0/4。同上，注意是 RES 而非 IMP。UNIMPLEMENTED_NATIVE。 |
| `engineering/material-req/index.vue:272` attachmentFiles | IMP/materialRequisition；update0/4。同上。UNIMPLEMENTED_NATIVE。 |
| `engineering/material-exch/index.vue:248` reasonFiles | IMP/materialExchange；update0/4；理由文件应保留其审批与 CRM 推送分支，不能让传输成功代替业务完成。原生接入可做；UNIMPLEMENTED_NATIVE。 |
| `engineering/announcement/index.vue:194` fileUrl | KNO/announcement，无 projectId，草稿0可写。原生租户/全局 query+update+Owner版本授权可做，不能套 ProjectScope。公共默认非项目 Owner 桥要协调；当前无相应 EntityFieldProvider、泛型后备拒绝。UNIMPLEMENTED_NATIVE + 公共桥依赖。 |
| `engineering/briefing/index.vue:187` fileUrl | SOL/briefing，手工更新草稿0；生成 HTML/PDF 已有受控快照。应增加手工附件独立原生用途/来源，沿用旧上传5MiB/Office/txt/pdf，不扩大 BriefingGeneratedFilePolicy。原生可做；UNIMPLEMENTED_NATIVE。 |
| `engineering/solution/SolutionChapterForm.vue:151,176`、`engineering/solution-reviewed/SolutionReviewChapterForm.vue:107,132` asIsUrl/toBeUrl | 两页面同 SOL/solution 根。需要独立拓扑来源/真实修订锚和现有方案编辑授权，避免套客户文档/生成文档类别；当前 IMPLEMENTATION_PLAN 目录媒体没有 png/jpeg。原生新来源接入可做；选择复用目录并支持图像则需协调前向种子，不能擅自放宽公共类别。UNIMPLEMENTED_NATIVE。 |
| `acceptance/acceptance/index.vue:175` attachmentUrl | ACC/acceptance 旧根，update草稿0；新报告的 ACC/acceptanceActivity 是另一实体。可独立原生 SPI/source/事务归集；不得改挂到新报告。UNIMPLEMENTED_NATIVE。 |
| Preparation 与 DurationChange | 文件策略分别 SOL/SITE_SURVEY_ITEM、SOL/CONSTRUCTION_PLAN_CHANGE；无对应材料来源/保存归集。Preparation根/历史lineage需明确，不能称当前工勘已接入。DurationChange可归 constructionPlan 真实根+change修订来源；现有项目经理/客户延期证据授权不可省略。UNIMPLEMENTED_NATIVE，Preparation身份需协调。 |

**父任务/01a10fe7 公共框架协调（本任务未实施）：**

1. 六个 Host DELIVERY 声明仍在禁改的 EngineeringBusinessModelContributor.java；须由父任务处理拒绝边界。当前工勘四禁写边界和 state3 追加、历史无授权培训链接失效两项用户决定仍未解除。没有换类、换路径、换环境绕过。
2. 稳定业务键发现：公共 `FileArtifactController` 的 GET `/file-references` 必须已知 artifactId+完整业务键；`FileQueryService` 没有只按完整业务键找当前引用的公共查询。上传成功、原生归集失败后整页刷新，尚无材料可提供 artifactId。建议框架增加已授权 current-by-stable-key 查询（Controller/VO、FileQueryService、FileReferenceMapper、前端 api/pms/platform/file）；测试跨租户、业务只读权限、DETACHED/换版、真实字符串大ID、不得枚举他人文件。本轮只保证当前页失败重试，不能宣称刷新失败上传可恢复。
3. Java原生撤回：`PlatformDeliveryMaterialApi` 只有登记/查询，未提供撤回；`DeliveryMaterialService.withdrawTrusted` 是包内方法。既有 HTTP withdraw 已足以完成本轮到货/核对显式撤回，**不是它们的剩余阻塞**。若要原生保存事务中自动撤回旧材料，应由框架扩 API及Impl/MaterialService，携带确切 Owner/来源身份并重检权限、状态、租户、锁；测试CAS回滚、版本历史、重复提交、已冻结拒绝、公共计数恢复。不要求全局 complete 钩子。
4. 目录与Owner授权耦合：`DeliveryFilePolicyProvider.inspectOrDelegate` 一旦存在匹配 Owner validator，无论 purpose 是否目录类型都调用 validateUpload；现有 SPI 同时负责 Owner访问和上传约束，API模块没有目录约束查询。新增原生 requireDeliveryAccess 适配会阻断泛型目录上传，不能在原生层复制目录约束绕过。建议框架明确 separate owner authorization / native purpose constraints / catalog constraints 的顺序，或提供目录事实 API；目标 `DeliveryMaterialUploadPolicyValidator`、DeliveryFilePolicyProvider、DeliveryOwnerAccess 与相应授权测试。保证禁用类型、大小/媒体、原生冻结、siblings路由、非项目Owner均失败闭合；保留 RA/ACC 原生成果不能由泛型面板写入。
5. 当前源生来源 `ACC.CHECKLIST_ATTACHMENT` 通过来源SPI+registerNativeSourceFile无须目录才能归集/读取；测试中的同名 Catalog requirement 是专用夹具，不是生产种子。若生产要以此目录类型创建数量义务，父任务须协调唯一前向目录种子/正式配置及实际迁移验收。未让材料凭空成为必交义务。
6. 共享 PmsFileUploader 的说明/预检固定50MiB，核对策略沿旧上限5MiB，原生页面已明确5MiB，服务端严格限制。共享文件/材料 Vue props 对返回的字符串 Snowflake ID 有 Number 类型警告；不能 Number() 丢失精度。建议框架把大小/完整字符串ID契约统一，补>2^53真实ID访问测试。本轮浏览器没有pageerror，警告不等于全应用类型通过。
7. 补真实应用装配、登录、实际角色/项目树、归档/下载全链。当前全后端构建既有阻塞 integration `SpringJdbcStreamingReader.java:93` 的 JdbcTemplate.execute 重载歧义（环境setup日志）；未扩大无关模块修复。CUT和新到货验收未据此晋级。

原交接里的工程身份夹具失败、前端 operationAdapters/presentationHost 失败仍是历史未解除项；全 ts 既有 28 项失败本次未复跑。此前 B13 Boot51 HTTP、独立 60 checks 只保留其局部范围。V374 原始 collation 迁移失败，337 项仅是隔离对齐后的旧证据，本次没有声称原样迁移/部署通过。权限异步缓存失效窗口未扩展为本任务主题。
