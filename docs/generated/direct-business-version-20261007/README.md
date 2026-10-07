# 默认业务版本、字段配置与上传历史检查点

日期：2026-10-07。结论：**实现已推进，最终验收未完成，不作为全部默认能力或全部生产实体迁入完成的证明。**

代码基线：`11459f8c44e0d99d4636fa210ef26866bfecc78f`，树 `56ef5f0f724140fb6ff078ea163add76e5c8083b`。它包含目标分支 `4a91006bb73cc3b77cb2b458d4f2dbf3a5a06a44` 的商务修复；当前仅发布在 `integration/direct-business-version-20261007`，没有推进目标 fix 分支到未完成验收的框架代码。

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
- H2 业务回归：SiteSurveyInheritedBusinessTest 18、RequirementInheritedBusinessTest 15、DirectVersionedBusinessTest 24 项通过。继承测试有重叠，不将这些数字当作独立业务覆盖总数。
- 上传历史：API 授权与只读投影 1 项通过（外部端口替身）；共享 Mapper XML 查询 1 项通过。H2 测试仅在测试侧转换 MySQL bit 字面量及变长 binary cast，未更改生产 SQL，不能替代 MySQL 方言验收。
- 前端 6 套 19 项运行检查通过，包含字段配置失败保留、离开确认、排序续页、修订保存正文不丢失、历史只读及切换历史后保留原上传重试键。
- 全 `yudao-server -am package -DskipTests` 通过；此为构建，不等于应用部署/启动验收。
- 联合树商务复验 31 项通过：DppmsOrderSyncAdapterTest 14、CommerceAuthorityIngestServiceTest 17。
- 实际前端类型检查仍有 4 项旧错误：CollectionDialog.vue:390、processDefinition/index.vue:151、ProjectSchedulePanel.vue:142、schedulePresentation.ts:148。未报告全量 TypeScript 通过。

## 待完成的硬验收

1. 对上述联合代码运行隔离真实 MySQL：DirectVersionedBusinessTest，以及统一材料的历史/权限/有效性用例。
2. 对同一代码运行版本化真实浏览器及工勘/需求分析联合浏览器。脚本已增加字段配置持久化、恢复默认、撤回历史只读与完成判断场景。
3. 在隔离 MySQL 验证 V400 字段配置表及 V401 权限登记的首次和幂等执行；不得顺便给角色授予新权限。
4. 将失败修复落实后重新跑受影响检查，再推进目标 fix 分支。旧提交浏览器通过不能替代新提交。

已有版本浏览器曾在 `bbf6bc3c` 发现“保存修订时正文修改被忙碌态重渲染清空”，后端虽返回 SAVED，标题仍为 Original。已通过稳定 computed 字段列表修复，组件运行回归先失败再通过；**修复后的完整浏览器链尚未取得通过证据**。

当前验证活动启动接口返回 `The invoking thread is not attached to an active Aeon`。本云端没有 Docker/MySQL，Chromium 启动受进程通信 socket 限制；因此没有用 H2、编译或模拟控件替代上述未运行项目。此阻塞不影响已确认代码改动和本地测试的真实性。

可用验收入口：`scripts/tests/verify_direct_business_version_mysql.sh`、`scripts/tests/verify_native_delivery_stage2_mysql.sh` 及同目录的默认交付件隔离测试脚本。遵守脚本已有隔离库、用户、环境标记与 server UUID 检查，禁止指向共享/生产数据库。
