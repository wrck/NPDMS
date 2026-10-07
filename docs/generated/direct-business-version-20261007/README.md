# 默认业务版本、字段配置与上传历史检查点

日期：2026-10-07。结论：**本次默认内容版本、字段配置及交付件历史增量已通过适用隔离 MySQL、真实浏览器及迁移验收。此结论不代表全部生产实体迁入或生产部署完成。**

最终浏览器验收代码基线：`ffc6172cb5a3220c3e6363f3422bc5e3f54619d5`，树 `53e2c7680e7b6160a42f4d7f4226888c80f82161`。它包含目标分支原 `4a91006bb73cc3b77cb2b458d4f2dbf3a5a06a44` 的商务修复。本报告后续提交仅归档验收状态，不改实现或测试。发布目标为 `fix/visio-template-followup`，未授权合入 master 或部署。

## 实现与验收对应

| 能力 | 默认实现 | 已验证及边界 |
|---|---|---|
| 直接继承 | ProjectBusinessController → DefaultProjectBusinessService → BusinessMapper | 空业务类继承 HTTP/CRUD/权限/范围/回执，无需模型目录贡献器或业务适配器；端口替身测试不代替 SQL 验收 |
| 内容版本 | DefaultVersionedProjectBusinessService、VersionedProjectBusinessController、ProjectBusinessHistory | H2 覆盖创建、保存、冻结生效、复制、放弃、比较、并发冲突、不可变历史及当前正文/扩展旁路拒绝；修订保持原正数编号 |
| 表单与扩展 | 默认 Service 同时提供字段与动态表单策略；复用 EntityFormApi/EntityExtensionApi | 表单绑定、发布布局校验、数值扩展转换、未提交字段保留、正文/扩展事务、修订快照；无需每实体再写表单策略类 |
| 字段配置 | BusinessFieldConfigurationApi/Service、默认 Controller 方法、ProjectBusinessFieldConfiguration | 租户内持久化标签、列序、显示/查询/排序；CAS、审计；两个空 Service 共用，无逐业务适配器 |
| 配置权限 | 默认 CONFIGURE 操作，permissionPrefix:configure | 普通更新权不授予租户全局配置权；需求分析 manage 不能被映射为 configure；V401 只登记可分配权限，不给角色赋权 |
| 简单查询 | BusinessMapperQueries 与可信 ORM 字段映射 | 仅已开放的持久化标量字段、受控方向、稳定 id 尾序；拒绝注入字段、私有字段、被关闭查询/排序字段；空权限范围不扩大 |
| 复杂查询 | 业务重载 selectPage 并调用自有 Mapper XML | 保留租户/项目范围和有效字段检查；需求分析既有可见修订逻辑不替换为普通查询 |
| 统一交付件 | 公共 CRUD/上传/完成判断及 ProjectBusinessDeliveries | 继续同一 plt_delivery_material、四项业务身份；完成判断只看最新有效且文件可用的记录 |
| 上传历史 | /{id}/deliverables/history，同一材料表查询 | 按用户裁决，不新增冻结引用清单/快照。失效/撤回元数据只读，不进入当前完成判断，不通过历史恢复写入或失效文件访问 |

## 本地执行结果

- 默认继承与查询配置：DirectBusinessInheritanceTest 14 项、BusinessListConfigurationTest 5 项通过。另有模型发现/默认定义原回归 7+4 项通过。
- H2 业务回归：SiteSurveyInheritedBusinessTest 18、RequirementInheritedBusinessTest 15、DirectVersionedBusinessTest 25 项通过（包括字段配置安装后交付件端口可正常 stub 的测试夹具回归）。继承测试有重叠，不将这些数字当作独立业务覆盖总数。
- 上传历史：API 授权与只读投影 1 项通过（外部端口替身）；共享 Mapper XML 查询 1 项通过。H2 测试仅在测试侧转换 MySQL bit 字面量及变长 binary cast，未更改生产 SQL，不能替代 MySQL 方言验收。
- 前端 6 套 19 项运行检查通过，包含字段配置失败保留、离开确认、排序续页、修订保存正文不丢失、历史只读及切换历史后保留原上传重试键。
- 全 `yudao-server -am package -DskipTests` 通过；此为构建，不等于应用部署/启动验收。
- 联合树商务复验 31 项通过：DppmsOrderSyncAdapterTest 14、CommerceAuthorityIngestServiceTest 17。
- 实际前端类型检查仍有 4 项旧错误：CollectionDialog.vue:390、processDefinition/index.vue:151、ProjectSchedulePanel.vue:142、schedulePresentation.ts:148。未报告全量 TypeScript 通过。

## 隔离真实验收结果

已取得以下真实执行证据，不能将后续未执行任务或旧 JUnit 文件混入结果：

- `11459f8c44e0d99d4636fa210ef26866bfecc78f`：版本化真实 MySQL 24 项通过。V400/V401 原 SQL 首次和重复执行、中文 UTF-8 及不自动给角色授予权限均通过。
- `271128726f13a9e5200061c3e2ebdcb2402c3e7d`：公共交付件真实 MySQL 9 项通过，包含新的授权后归集分页。工勘/需求分析联合真实浏览器 1 项、4 组检查、79 次 HTTP 请求均为 200/code=0。
- 证据位于验收环境 `/workspace/validation-final-11459f8c-20261007/.run/27112872-validation` 和 `.run/11459f8c-validation`。迁移结果为后者 `migrations-utf8/result.json`。浏览器使用真实前端组件、继承接口和 SQL，鉴权/项目事实/外部存储端口仍使用隔离夹具，不等于生产部署验收。

- `01ede231f5fd0f39a79993b199bb871c5de87a8c`：25 项版本化真实 MySQL 全部通过。后续两次提交只修改浏览器定位，保留此结果。
- `ffc6172cb5a3220c3e6363f3422bc5e3f54619d5`：完整版本化真实浏览器 1 用例通过，0 失败/错误/跳过，脚本退出 0。全部 3 组检查完成，100 次 HTTP 全部为 200/code=0。
  - 修订、表单/扩展保存、不可变冻结及当前版本生效。
  - 复制、比较、取消/确认放弃、历史保留、修订号递增、未保存离开确认。
  - 仅 code 配置继承默认标题、改名、重开、恢复默认；配置 CAS 版本 1 → 2 → 3。
- 最终证据目录：`/workspace/validation-final-11459f8c-20261007/.run/ffc6172c-validation`，包含 `command-results.json`、`version-maven.log`、`browser-evidence/result.json`、`browser-evidence/http.json`、`frozen.png` 和 `history.png`。专属容器已正常清理，28474/27462/27463 无监听。

最终生产代码与 `01ede231` 相同；公共交付件后端、工勘/需求分析业务逻辑及 V400/V401 自各自通过后没有修改。验收按实际受影响范围复用已有结果，不把全部测试说成同一提交上一次运行，也不重复无关检查。

版本化浏览器暴露的问题及处理：

1. `bbf6bc3c` 曾发现修订保存时忙碌态重渲染清空正文修改，HTTP 成功但标题仍为 Original；已稳定 computed 字段列表，运行测试先失败再通过。最终浏览器已验证修订与当前标题均为 Revised。
2. 版本浏览器夹具在 Mockito 默认 inline maker 与父类 subclass maker 混用时，准备交付件 stub 触发内部断言。`a41bb6fc` 统一为父类 maker，并补充夹具失败回归，不关闭断言或削弱业务规则。
3. `a41bb6fc` 复验在 Compose 报 Healthy 后的环境标记 SQL 报 MySQL socket 不存在，Maven 和浏览器都未启动，计数为 0。`01ede231` 把健康检查改为 TCP，避免接受官方镜像 socket-only 的临时初始化服务；隔离库、随机账号、marker、server UUID 和正常清理约束不变。
4. 字段配置 API 的空属性表示继承默认设置，但编辑器原先让 null 覆盖默认值。`01ede231` 过滤 null/undefined，保留 false/0；组件失败回归修复后通过，浏览器新增仅提供字段 code 的配置、默认标签回显及 CAS 版本检查。

5. `9e8268b9` 将比较选项绑定到具名 combobox 的 aria-controls listbox；随后 `ffc6172c` 将点击目标改为包含该控件的唯一可见外框，避免窄 input 被后缀图标拦截。最终三组全部通过；未跳过比较、未 first() 随意选取、未 force click。

此前任务接口与测试启动阻塞均已解除。失败记录保留，不将失败轮次中未执行的检查写成通过；最终通过来源见上文。

可用验收入口：`scripts/tests/verify_direct_business_version_mysql.sh`、`scripts/tests/verify_native_delivery_stage2_mysql.sh` 及同目录的默认交付件隔离测试脚本。遵守脚本已有隔离库、用户、环境标记与 server UUID 检查，禁止指向共享/生产数据库。

## 后续归集分页修复

公共项目归集原先先按材料表分页，再过滤 Owner 不可读记录。当同业务类型的私有草稿占据第一页时，后续可读交付件会被隐藏，甚至错误返回总数 0。已用真实公共 Service 和权限端口反例复现（预期可读记录，实际空列表），再修复为按项目分批扫描、先做 Owner 可读判断后分页，总数只计算可读记录；同一请求内复用同一四项业务身份的授权判断。202 条跨原始页的回归通过。

精确业务类型/实体 ID 的历史和完成判断仍保留原精确查询路径。公共归集为保证精确可见总数，需要扫描该项目匹配的材料元数据；每次 SQL 分批 200 条，不加载文件正文，也不暴露隐藏记录数量。此增量的 API/查询检查共 3 项通过；真实 MySQL 9 项及工程联合浏览器的复验证据见上节。

## 结论范围

新增普通业务可通过薄 Controller/Service/Mapper/实体和默认页面接入；需要内容版本时继承版本默认实现。无须为相同公共能力逐业务添加适配器，特殊规则仍通过重载和专用操作扩展。工勘和需求分析的既有业务接入与公共交付件联合链已经验证。

本次没有执行生产部署、生产登录及真实外部文件存储验收，没有完成全部存量业务或旧原生交付件的迁移；本机十项目详情补全是独立任务，不纳入本报告完成结论。全量前端类型检查的 4 项既有错误仍未消除。
