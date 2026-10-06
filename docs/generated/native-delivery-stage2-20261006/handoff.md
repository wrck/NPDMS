# 原生统一交付件第二阶段交接

配置、联调、外采、外包、领料、换料的六个原生附件入口已改用公共上传组件、文件操作和同一交付材料实体；原生保存事务归集实际文件，提交/完成前锁定来源文件再冻结。最新后端定向验证为 **9 类 / 109 项通过**，前端 **5 文件 / 31 项通过**；六个真实原生页面经过 Chromium、实际方法授权控制器/服务和独占 MySQL 验证。配置设备档案新增持久定位符，下载继续检查设备授权与文件授权；旧 URL 下载兼容。**全部业务验收仍未完成**，剩余入口及业务撤回缺口见[矩阵](acceptance-matrix.md)。

基点 `fix/visio-template-followup` / `fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721`。本阶段增量相对父任务已取得的第一包；第一包 41 文件逐项 SHA-256 一致，无重叠修改。没有提交、push、merge、部署、生产数据操作或真实迁移。增量文件与补丁由仓库外本地包交付，未再上传或调用 Library。

## 代码与范围

| 目标 | 实际行为 |
| --- | --- |
| `engineering/service/attachment/` | 六类真实 Owner 的文件策略、来源、事务归集和既有交付访问 SPI；区分 IMP 与 RES，校验租户、真实登录主体、原生 query/update 权限、原生可编辑状态、MANAGE/VIEW、非占位项目、树版本锁后重检与写入项目 ACTIVE（提交/完成前归集也要求 MANAGE 和原生 update，不降级 VIEW）。只支持确切原生用途；不开放第二个泛型 PLT 上传槽。 |
| 六个原生 ServiceImpl | create/update 拒绝新增或替换裸 URL；历史 URL 只读保留，不假造文件。update 成功后归集，提交/完成前锁定实际文件集合。材料已存在后禁止改变项目根。外采/领料修正真实 Long/Integer 版本比较并检查 CAS 更新结果；换料保留序列快照、审批和既有 CRM 分支，外包保留 SITE_SURVEY 关联约束。 |
| 六个 Mapper 与 XML、`NativeAttachmentOwnerLockQuery` | 租户/id/deleted 精确 Owner 锁，文本锁 SQL 在 XML。新增三个原先没有的 XML；未扩大查询范围，未新增通用 SQL/Map 查询。 |
| `engineering/attachment/NativeAttachments.vue` 与六个 index.vue | 公共上传/文件引用/交付台账；创建后重新打开已持久化根进行上传。上传成功后 GET 已保存 Owner → 原生 UPDATE → GET，不保存表单脏编辑；换料不重写序列快照。归集失败保留已完成文件并重试；精确材料撤回、冻结版本历史、公共完成刷新。迟到上传事件和 Owner 响应不得写入新打开记录。审批详情为只读。原生容器内水平滚动，避免宽台账撑出业务弹窗。 |
| `engineering/requestTime.ts` | 既有可空申请时间保持 undefined，避免归集历史空时间记录时凭空制造时间；明确非法字符串仍拒绝。 |
| `asset-api/device/dto/NativeConfigurationFileLocator` | 持久身份 `pms-native-config:v1:<configurationId>:<materialId>`，不存短期下载 URL。正 Long、大 ID、版本格式严格校验；不改变公共文件 API。 |
| `ConfigurationServiceImpl.archiveNativeConfigLogs` | 仅新登记实际材料写设备日志，同事务失败回滚；重复原生保存/完成不重复记日志。根 `config_log_url varchar(500)` 保留既有 URL，不累积材料 ID。定位符只写设备日志；实际文件 SHA 随日志保存。 |
| `asset/DeviceConfigurationLogDownloadService` | 保留设备权限/可见范围、租户、SN、日志归属、一次性 grant 校验；原生定位符检查真实 ACTIVE 材料、冻结 artifact/version/hash 与引用用途，先复用 `NativeGeneratedFileApi.requestDownload` 获取文件 Owner 授权与锁，再重检 ACTIVE 材料及冻结证据；不向外暴露未验证的 URL。替换、解绑、撤回、文件授权失败不降级 URL 路径；旧 URL 沿旧 FileApi 路径。 |

工程 Java 表内路径相对 `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/`，Vue 相对 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/`。确切全部改动见增量包 manifest；测试范围与命令见[verification.json](verification.json)。源码类别 `IMP.CONFIGURATION_LOG` 等是来源用途，**未创建生产必交义务、目录种子或 submission 历史**。材料为 `plt_delivery_material`，requirement 为义务、fulfillment 为用途、submission 为历史归档，未互换。

## 验证与实际限制

- 独占 MySQL 45 项：六类材料身份/项目聚合一致、归集失败 Owner 回滚、复用文件重试、提交/完成冻结、跨租户/权限/占位/范围版本/项目生命周期拒绝、提交/完成只拥有 VIEW 时拒绝、实际替换和解绑使旧冻结版本失效、撤回材料不计完成、裸 URL 新建/更新拒绝。配置设备日志通过真实同事务 JDBC 夹具验证去重和回滚。这个日志写端口是受控替身，不是资产日志实际实现的 HTTP 全链。
- 原生回归 7 类 41 项：配置/联调既有状态、换料 CRM 未接通拒绝与序列快照、外包工勘执行联接、事务及版本约束。未为测试放宽业务校验。
- 资产下载 1 类 23 项：旧 URL、新定位符、大 ID、撤回、实际旧冻结文件不可用、解绑、文件授权撤销、格式、跨租户、Owner 锁先于冻结文件锁、授权期间 ACTIVE→WITHDRAWN 拒绝；设备 grant/内容流原有负例保留。文件/材料外部端口受控，真实替换/解绑事实由 MySQL 套件另行验证；未声称实际资产 HTTP/外部存储下载全链。
- 前端 5 文件 31 项：六类上传归集重试与冻结、已保存根/大 ID、只读、无根、空时间、原生既有页面逻辑、迟到事件/响应拒绝。真实 Chromium 六页全部通过，`pageErrors` 为零；见[断言](native-six-browser.json)及六张页面截图。

浏览器调用真实 `FileArtifactController`、六个原生控制器、`DeliveryController` 和真实服务，事务与方法权限代理开启。使用 actor17/tenant7 和确定性的权限、ProjectScope、项目生命周期、设备选择、存储端口。**没有完整应用登录/真实角色树/真实外部存储/Flyway**；表由当前 DO 推导，不是生产迁移兼容验收。六个来源目录/数量义务只在夹具设置，不能当成生产已种子化或六项业务自然必交。

最新 45 项日志保存在 `.run/native-delivery-stage2-20261006/mysql-full-45.log`/`.xml`；UI 修复后的浏览器单项复验另存 `mysql-browser-final.log`/`.xml`，不重复计入 109。定向回归 `native-regression-final.log`，UI `native-ui-final.log`。包中附相同证据及 SHA。保留初次夹具 SQL 列与原页面断言失败证据；后续修正的是夹具/过时 URL 断言，没有降低生产授权或回滚规则。

复验 MySQL：激活 JDK25 后设置需要的 `NATIVE_DELIVERY_MAVEN_SETTINGS`，运行 `bash scripts/tests/verify_native_delivery_stage2_mysql.sh`。脚本只创建项目 `npdms-native-delivery-stage2-20261006` 的 tmpfs MySQL，localhost28471，拒绝复用已有容器并在退出清理；HTTP28472、Vite28473 专用。不要复用生产/共享数据库。源码索引使用第一包 `capture_native_delivery_inventory.py` 的模块入口，把 OUTPUT 指向本阶段目录；不要覆盖第一包索引。

## 父任务协调与未完成项

1. **原生业务撤回/终止尚未使材料失效**：外采、外包、领料、换料的 `withdraw*`/`terminate*` 只改状态5/6，已归集 FILE 仍 ACTIVE；读取策略保留历史读权限，所以公共计数可能仍满足。本轮验证通过的是可编辑态的“撤回材料”，不是这八个业务命令。需要父任务/01a10fe7 已认领的可信 Java 撤回合同，再在实际原生命令事务中逐材料衔接；不能用禁止历史读取或私写材料表替代。未实施公共 API/核心服务更改。
2. **公共 MIME/上传提示**：旧配置/联调接受 `.log/.cfg/.conf`，浏览器空 MIME 会走 octet，当前公共内容检测拒绝。负例证明确实失败；六页使用实际 TXT 验证。父任务已协调框架安全识别这些明确文本扩展，不放行任意 octet。本地服务仍严格5MiB，公共上传提示目前50MiB待框架对齐。
3. **目录用途与原生 Owner SPI**：原生 purpose 授权与目录类型约束不能互相绕过；当前新增 native validator 仍拒绝泛型目录上传。父任务/01a10fe7 负责公共原生冻结与目录用途组合、CAS 撤回/前向迁移。我们未改相关框架/API合同，整合后需再验泛型上传。
4. **稳定键恢复**：第一包已报，上传完成但原生归集失败后整页刷新，现有材料尚不能发现该文件；没有已授权 current-by-stable-key 公共查询。本阶段保证当前页复用重试，未宣称刷新可恢复。父任务框架合同落实后再接原生恢复。
5. **通用下载冻结版本风险**：现有 `NativeGeneratedFileService.requestDownload` 取当前引用，未核对材料冻结 version/hash。资产读取已自行经现有 FileEvidenceApi 精确核对并锁定，避免旧定位符下载新版本。公共服务本体未改；父任务可评估其他消费者。
6. **KNO 公告实际 Owner**：为工程 `KNO/announcement`、`kno_announcement`，无 projectId；原生 query/create/update/delete/publish/disable 各用 `pms:kno-announcement:<action>`。草稿0可更新，publish0→1、disable1→2。现有 `LegacyTenantReadBindings` 仅租户只读，绝非写授权；缺原生文件策略/来源/归集以及公共非项目写 Owner 桥，不能套 ProjectScope。现有版本 Long/Integer 比较和未检查 updateById 也是未修改旧问题。公告没有因只读绑定晋级接入。
7. **真实尚未实现**：公告、交底手工附件、旧验收附件、两套方案拓扑、施工照片、旧准备项/施工计划变更材料归集仍见矩阵。旧服务执行证据/离线文件为 Retired 旧范围，列为历史维护缺口，不承接新功能。这些并非都缺权限，而是尚未实现或需具体身份/类别协调。
8. **禁改与待用户决定**：`EngineeringBusinessModelContributor.java`、当前工勘页面、两个禁改访问策略均未写入；未用旁路声明绕过六个 Host DELIVERY 缺口。工勘 state3 追加材料、历史无授权培训重发失效仍待决定。禁止全局 `FileUpload.complete` 登记钩子保留；新培训授权撤销/删除后不降级匿名路径。

CUT、新到货验收、完整 Host/泛型、实际部署登录/角色/存储下载全链均 NOTRUN。全 ts 既有28失败、integration `SpringJdbcStreamingReader.java:93` 构建歧义、V374 原始 collation 失败与仅隔离对齐后337迁移通过保持原证据范围；本阶段没有宣称全仓、原样迁移或部署通过。角色异步缓存窗口未扩展为本任务主题。
