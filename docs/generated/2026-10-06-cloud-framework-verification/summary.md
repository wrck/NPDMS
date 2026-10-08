# 云端公共框架验证与交接（2026-10-06）

已安全切换 `fix/visio-template-followup`，HEAD 精确为 `fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721`，父提交 `faab7dd9564cb2d2d0dbee655538d140ae860c3d`。进入时工作树洁净，检查点 386 项文件哈希匹配。本轮已获用户明确授权，准备发布公共任务自身临时分支 `integration/unified-framework-20261006` 的 WIP checkpoint；未部署、未合并。

当前公共层修复及独立验证已形成可复核结果；整体公共业务能力尚未全部验收。原生 41 文件首包受 Library 访问链路网络错误阻塞，未取得 ZIP 字节，未应用补丁。CAS 撤回、用途 SPI 组合和未知 MIME 处理已独立实施到 WIP；当前限定测试 32 项：30 通过、1 失败、1 跳过。失败为旧 fulfillment 服务接受过期 ACTIVE 材料对象，尚未补共享行锁/状态重验。新快照不能引用旧 209+2、Tomcat21 或 Chromium 结果为验收。详见 [wip-checkpoint.md](wip-checkpoint.md)、[wip-test-results.json](wip-test-results.json)、[wip-source-files.json](wip-source-files.json)。

## 三项实施前的稳定结果（历史 51 文件快照）

| 验证 | 结果 | 快照与限制 |
|---|---|---|
| 当前选择的后端回归及 42 模块打包 | 211 项：209 通过，0 失败，0 错误，2 跳过 | 打包时 48 文件快照；最终 51 文件中 18 个变动 Java 文件哈希完全相同。两项浏览器条件测试跳过，不能计入通过。 |
| 文件稳定键发现，真实 SQL/策略注册 | H2 6、专属 MySQL 6，均通过 | 包含 Owner 拒绝先于 SQL、Provider 不可用、跨租户、缺失槽位、已解绑、新版本及生产 Long 序列化；策略提供方为隔离 Owner fixture。 |
| 真实 Tomcat 登录/权限/事务/回执 | 21 项全部通过，最新普通应用 JAR 实际启动 | 原生登录、SQL/Redis OAuth2、生产 Security/租户过滤链、原生 SiteSurvey 服务和真实 ProjectScope；schema 停在 V374，后台/外部服务按隔离配置禁用，角色及成员为 fixture。 |
| 共享文件及动态表单前端运行测试 | 7 套件，27 项全部通过 | 最终字符串大 ID 与传递 DTO/证据状态类型快照；不包含未下载的首包。 |
| 真实 Chromium 声明表单 | 通过 | 实际 HTTP/登录及生产表单组件；完成响应丢失后刷新恢复、类型化动态表单、必填显式清空拒绝并回滚。详情及项目范围 fixture 限制见 verification.json。 |
| 真实 Chromium 文件槽位 | 通过 | 实际共享组件/HTTP 客户端；文件完成后消费者拒绝、全页稳定键恢复、大 ID、新版本、解绑状态。文件响应由明确的 Playwright fixture 提供，SQL/Owner 在独立 MySQL 测试验。 |
| 类型检查 | 未全通过 | 全仓 vue-tsc 触发 4 GiB V8 堆上限；范围检查只剩未改动的 processDefinition、ProjectSchedulePanel、schedulePresentation 三项既有错误。 |
| 全仓后端收集 | 历史较早快照 5848：5234 通过、78 失败、69 错误、467 跳过 | 957 类。仅为继续收集使用 maven.test.failure.ignore=true；BUILD SUCCESS 不等于测试通过。当前修复后没有再跑完整全仓，不与上述结果相加。 |
| 较宽前端历史检查 | 122：115 通过、7 失败 | 原生 operationAdapters HTTP mock/路由契约问题仍保留。仅改 fixture 的诊断 15 通过/1 路由失败，不是原测试通过。另一次共享文件较宽检查 29 通过/1 旧 CUSTOMER_DELAY 静态断言失败。 |
| 原始全链迁移 | 失败 | V374 第 72 行，MySQL 1267 排序规则冲突；权威字符集 URL 和明确 session collation 在两个新数据库重查仍失败。未 repair、未改历史 SQL、未跳版本；V388–V397 未应用。 |

上述结果互有重叠，禁止加总。所有当前源文件 SHA256 见 [source-files.json](source-files.json)；逐类、逐用例结果、日志哈希与关键生产源文件哈希见 [verification.json](verification.json)。保留失败、跳过及中间 fixture 错误；未保存精确失败前 fixture 字节的个别记录明确标注，未用当前绿色源码哈希代替旧失败源码。

## 已修复与保留的行为

- 身份别名不能遮蔽另一声明或原生服务的真实实体类型；预留全部真实身份后再建立别名，元数据查询不初始化 Owner 服务。
- 公共交付桥接的实际 Spring 文件策略/Owner 服务依赖图原有循环在真实启动中复现，已通过延迟注入破环，生产授权入口保持。
- 原生 SiteSurvey 的 GET 回执恢复沿实际原生服务路由；新回执绑定原操作和版本，重放/恢复都检查原操作权限和当前 Owner。旧未绑定回执继续拒绝，不重写历史。
- 普通 DO inline resultMap、外层事务重放 rollback-only、原子 CAS、幂等摘要、审计/outbox 失败同事务回滚、固定字段保存与必填扩展同事务均在当前选择回归中通过。原生合法不完整草稿仍保留；省略已有必填扩展保留，显式清空拒绝。
- 前端迟到成功、拒绝和恢复按原 key+摘要清理 intent 的检查保留；运行视图 fixture 验证默认工厂无需专用实体页面/服务 Bean，但对象读取、操作及 Owner 范围仍独立授权。
- 文件查询可省略 artifactId，仍要求完整五段稳定业务键，先检查当前 Owner READ 后查精确引用；有 artifactId 时继续严格匹配。不会自动登记材料、重绑或把 DETACHED 当 ACTIVE。
- 共享文件 ID 保留字符串或安全整数，拒绝舍入数字；上传提示为 50MiB。动态表单与既有工期证据的文件字段类型随之同步，未实施项目创建/详情新行为或历史迁移。
- 仅修复有证据的测试 fixture/runner 问题：匹配登录主体、事务线程上下文、原生 mapper XML/请求 SqlSession、H2 关闭方式、明确 mapper 方法白名单、非浏览器导入及独立 node:test 收集。原授权、事务、历史与失败断言保留。

## 验收清单

| 范围 | 当前判定 |
|---|---|
| 安全切分支、精确 SHA、洁净起点与交接哈希 | 通过 |
| 默认实体注册/别名、默认服务/CRUD、API 版本和操作身份 | 当前选择用例通过；不是所有 Owner 自动接入 |
| 普通 DO 映射、可选乐观锁插件关闭时 CAS 单胜者 | 通过 |
| 实体数据、幂等账本、审计、outbox 原子事务 | 通过 |
| 同 key 不同摘要拒绝、失败无残留成功回执 | 通过 |
| 租户/主体/权限/当前 Owner fail closed | 当前独立及真实 Tomcat 用例通过；生产异步缓存失效窗口未验 |
| 原生合法草稿及生命周期、原生 fanout 保留 | SiteSurvey 当前用例通过；每次成功原生写入保留 2 账本、2 审计、5 outbox 的既定差量 |
| 回执原操作绑定、当前 Owner 变化/撤销、旧未绑定回执拒绝 | 通过 |
| 固定字段保存无法绕过绑定必填扩展、遗漏保留/清空拒绝 | 通过 |
| 默认运行视图、无历史动态表单、刷新 intent 恢复 | 限定 fixture 下通过，原生全页面/多浏览器未验 |
| 公共文件完整稳定键查询及刷新恢复 | 限定 SQL/策略与浏览器 fixture 通过 |
| 所有真实业务操作自动归集至统一材料实体 | 未完成；material/requirement/fulfillment/submission 各职责保留 |
| 交付 Java CAS 撤回、材料版本迁移、幂等重放/冻结历史保护 | WIP 已实施；H2 8 项中 7 通过，过期 ACTIVE 材料关联测试失败；MySQL/完整迁移未验 |
| 原生冻结用途与目录用途 SPI 组合 | WIP 已实施；5 项通过，原生 Owner 接入/整体启动待验 |
| .log/.cfg/.conf 缺失 MIME 安全处理 | WIP 已实施；缺失 MIME 安全文本、伪扩展可执行/二进制、用途白名单、明确类型不匹配用例通过；新 HTTP/存储验收待验 |
| KNO 非项目交付 Owner | 阻塞：只有租户内只读适配，不能视为交付写授权 |
| 剩余存量 Owner 适配 | 79 个目录身份中 34 个 OWNER_SCOPE_ADAPTER_REQUIRED，本轮没有新增绑定 |
| 原生 41 文件首包哈希、范围/重叠/禁写审查与整合 | 阻塞：未下载字节，不得宣称校验或合入 |
| 完整 schema、完整全仓回归、所有原生页面/外部存储闭环 | 未通过或未运行，详见失败清单 |
| 生产库、真实历史迁移、部署、合并、force-push | 未实施，未获授权；仅自身临时 Git 分支 checkpoint/push 已获本轮明确授权 |

## 禁写及分工边界

EngineeringBusinessModelContributor、原生 site-survey/index.vue、DeliveryEntityAccessPolicy、SiteSurveyDeliveryAccessPolicy 均未改/未创建；未实施全局 FileUpload.complete 材料登记钩子。配置日志资产下载服务留给原生活动，未修改。保护目标精确哈希/缺席状态见 verification.json。

项目创建、详情和历史迁移属于独立活动。本轮仅同步因共享 FileId 类型变动而必需的既有文件证据类型，不改项目服务/页面操作/规则。原生首包及后续六入口继续归属另一活动，不推定该活动的 245/62 或其他快照为本工作树通过证据。

## 可复现入口与阻塞

使用 `/workspace/toolchains/activate.sh`（JDK25、Maven3.9.11、Node24）及 Maven settings。专属 Compose 项目 `npdms-cloud-framework-20261006`，MySQL/Redis 27461/27464，只使用该任务 tmpfs 容器。具体启动参数、数据库迁移证据、JUnit/XML 和 HTTP/Chromium结果路径/hash 均列于 verification.json；共享 13306/16379 资源未动。

后端选择用例：`.run/cloud-20261006/current-public-selectors.txt`，使用 `mvn -pl yudao-server -am package -Dnpdms.declared.exclusive=true -Dtest=<selectors> -Dsurefire.failIfNoSpecifiedTests=false`。专属 MySQL 文件发现测试另加 `npdms.file.discovery.mysql.exclusive=true`、显式任务数据库前缀和端口。浏览器分别按 `tests/declared-business-browser/README.md`、`tests/file-reference-browser/README.md` 运行，不使用共享/生产数据库。

首包下载目标 `libfile_24add5fa169481919733f29c51037b2e`；当前 helper 完整配置后调用及一次原样重试均报 `hosted apps tools/list request failed: network`。没有 ZIP，不能检查其真实 SHA256、路径安全、41 文件范围或与本树重叠。已按用户指定路线停止，没有绕行下载。需要恢复 Library 路径或由父任务明确调整交接路线，随后处理首包整合差异；三项公共实现已按本轮新指令独立推进。技术协调详情见 [delivery-gap-contracts.md](delivery-gap-contracts.md)。

当前源码留在本工作树，最终补丁/覆盖文件及全部失败证据可由同环境读取；未上传成新的 Library 交付件，避免把未整合结果标成完整首包。资源处置的精确状态见 verification.json 的 cleanup 记录。
