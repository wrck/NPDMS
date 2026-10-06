# 公共默认框架与原生交付检查点联合验证

当前限定集成验证通过，整体业务接入验收仍未完成。两个原始检查点和无冲突 merge 均保留；本轮在其后追加公共关联/下载并发修复及独立证据，不包含交底/旧 ACC 后续包、KNO 草稿或项目创建/详情/历史活动。

| 当前运行 | 结果 | 精确范围 |
|---|---|---|
| 最终普通 JAR / 后端 | 555 通过，0 失败，0 错误，3 跳过 | 42 模块构建；70 个简单类名选择器匹配 71 类、558 用例。不是全仓回归。 |
| MySQL + H2 公共材料/文件 | 32 通过 | 撤回/关联/下载 13 H2 + 13 MySQL，MySQL 文件发现 6；V398 实际脚本作用于已有材料。Owner/权限及最终下载票据为限定夹具端口。 |
| 六原生附件 | 57 通过 | 实际原生 Owner、服务、文件/材料和 MySQL；六实际页面 Chromium 上传、归集失败回滚、重试、共享身份、脏表单保留、撤回和公共完成判断。认证/项目/存储/设备端口为确定性夹具。 |
| 到货签收 / 核对清单 | 5 通过 | 实际 MySQL 与两页 Chromium；上传、归集回滚/重试、共享材料身份及撤回完成判断。 |
| 公共默认表单浏览器 | 1 通过 | SQL/Redis 登录经生产过滤器、真实 form-create、GET 恢复、固定/必填/多选扩展同事务、显式清空精确拒绝及回滚、只读 Host；HTTP 传输为 MockMvc。 |
| 当前 UI 明确选择集 | 13 文件、78 用例通过 | 使用仓库 `vitest.pms-file.config.ts`；逐例 JSON。前次 77 按该次日志保留；本轮以明确选择器和逐例 JSON 为准。 |
| 共享文件槽浏览器 | 通过 | late Owner 返回拒绝、字符串大 ID、版本/全键刷新、detached 状态；限定 HTTP 文件夹具。 |
| 最终普通 Tomcat | 启动 + 21 请求通过 | 普通 JAR，无测试类路径、security mock 关闭；实际密码登录、OAuth2/Redis、过滤链、角色/菜单、Owner 范围、原生 CAS、重放/GET 回执及撤销 Owner 后拒绝。仅隔离 V373 + 部分失败 V374 schema。 |
| 原始全链 Flyway | 失败，原样保留 | V374 第 72 行，MySQL 1267 排序规则冲突。未改历史 SQL、repair、跳版本或手动补后续版本。不能以独立 V398 通过代替全链迁移。 |

这些运行相互重叠，不能相加；浏览器单独运行也不改写 package 的三个 SKIP。最终逐类/逐例结果见 [backend-current.json](backend-current.json)，203 个交付/测试源文件 SHA256 见 [source-files.json](source-files.json)，最终 package 对应的201 文件源码快照见 [package-source-files.json](package-source-files.json)。普通 JAR SHA256 为 `bc3698e1de84da325bb7173c363fea2ae99c464bacf060d02b2efb9709ada873`，四个关键公共服务 class 与当前 target 字节一致，运行 JAR 未带 Mockito/JUnit/Testcontainers。

本轮修复七个源文件中的两个生产公共服务。`DeliveryFulfillmentService.associate` 要求调用方事务，在同一材料行锁下重查 tenant/project/ACTIVE，拒绝旧 ACTIVE 对象；关联和撤回的双向先后竞争、同时竞争、关联/外层撤回回滚及 tenant 负例均保留。`NativeGeneratedFileService.requestDownload` 在实际 Owner 锁后锁当前材料，检查归属、ACTIVE、FILE 和冻结 artifact/version/digest，再签发下载票据。合并基线的两个真实失败反例及修复后结果见 [negative-cases.json](negative-cases.json)。资产下载服务保持原生检查点的原字节，其原生单测在当前联合选择集中通过。

六原生的提交/完成归集分别保留六项仅 VIEW 项目范围拒绝、六项仅查询权限拒绝。不是用总数代替权限反例；枚举顺序及十二条用例逐项列在 negative-cases。前序 start 是原生独立操作，调试/联调 start 后的 status=1 不等于完成或材料归集成功。

两份原始 checkpoint 是公共 `b54632ade7176d824744862945800318bdb7563f`、原生 `be007ec3379b3c3f6951fe7a4f0052eec4c11097`，共同基线 `fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721`。merge `ffa43d9e8e996be5eb7da0ca30087c6881516d58` 的 186 文件均核对原字节；本轮后续七源文件 diff/hash 单独记录。23 个原生生成证据文件经浏览器重跑后恢复原字节，新证据另存。禁写四路径与基线相同/缺席；全局 FileUpload.complete 注册钩子未实施。详情见 [provenance.json](provenance.json)。提交及普通 push 的最终 SHA 另以实际 Git 回执报告。

完整验收清单：

| 目标 | 当前结论 |
|---|---|
| 公共默认 CRUD、实体/服务/API/数据/SQL、明确权限/tenant/Owner | 当前选择集及限定真实登录链通过；不能等同所有存量 Owner 已接入。 |
| 原生生命周期、原操作权限、原操作绑定回执、当前 Owner 恢复检查 | 当前选择集和真实 Tomcat 请求通过；剩余存量 Owner 适配未完成。 |
| 固定字段与已绑定扩展同事务、省略保留、显式必填清空拒绝 | 当前 persistence 与默认表单浏览器通过；原生合法草稿规则由原生服务保持。 |
| 默认运行视图、无历史表单、刷新 intent 与迟到回执 | 当前 runtime/UI/真实浏览器限定用例通过；多浏览器并发及全部原生 Host 未验。 |
| 公共材料、要求、使用关系、提交分别保留各自职责 | 当前集成通过；公共 Java CAS 不撤回模板/归档/已有使用关系材料。 |
| 真实操作统一归集、上传/管理展示、公共完成判断 | 当前原生八页面和选择集通过；所有业务操作闭环未完成。 |
| CATALOG/native-purpose 显式 SPI | 当前公共策略单测通过；默认仍为 NATIVE_FROZEN，不能据此宣称已有所有原生 Owner 开通动态 CATALOG。 |
| 撤回并发、幂等、审计/outbox 同事务、冻结证据 | 当前 MySQL/H2 通过；完整异步收敛未通过。 |
| 真实异步阶段结果收敛 | 旧隔离 schema 上 `ProjectTaskBusinessAssociationService.synchronizeStage` 第 71 行唯一当前阶段合同断言失败，outbox 返回 not-completed；日志留档，不作为完成。 |
| V398 前向迁移 | 已有材料 + 默认 version=0 + 文件/版本/digest/业务 revision/历史锚点保留，MySQL/H2 通过；全链仍由 V374 阻塞。 |
| 存量约 38 Owner、剩余原生入口 | 未完成；38 是历史计划估计，不是本轮重新清点。 |
| KNO scopeVersion / Owner 保存 version 合同 | 后续阻塞；不采用常量 0/Owner ID 或放宽持久 scope 合同绕过。交底手工附件/旧 ACC 新包也不在本轮 merge。 |
| 全仓后端、全 UI、typecheck/build、真实外部存储/ClamAV | 未全量执行；历史失败按原快照保留，不标绿。 |
| 生产库/真实历史迁移/部署/master/force-push | 未实施；本轮只按授权提交并普通推送 fix 分支。 |

21 个历史红类未在本轮重跑；另两类已在当前选择集中通过。具体类、旧数量和原 XML hash、未运行范围、原生后续事项见 [not-run.json](not-run.json)，不沿用先前快照总数作当前结论。完整 runtime/浏览器断言和限制见 [runtime-and-browser.json](runtime-and-browser.json)。

失败和跳过证据未删除：旧 ACTIVE 关联红例保留在公共 WIP；合并基线下载两红例；固定端口缺失导致 53 个连接错误；MySQL 夹具行过宽；Spring 夹具 Duration 转换缺失；默认裸 Vitest 的十个 CSS 装载失败；启动清理误判僵尸进程；原始 V374 全链失败。夹具问题按实际原因修复，不改业务断言。命令、退出码、原始日志和 XML 哈希见 [execution-evidence.json](execution-evidence.json) 及 backend-current；原始日志、随机登录凭据、缓存、截图和临时 SQL 数据不提交。

本轮专属 Compose `npdms-joint-unified-20261006`、`npdms-joint-declared-20261006` 和两原生脚本项目已清理，普通 Tomcat 已退出；只使用核对标签、端口和 tmpfs 的独占实例。共享 MySQL/Redis 的原 ID、端口及运行状态未变，清理证据在 provenance。
