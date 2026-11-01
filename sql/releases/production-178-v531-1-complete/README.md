# 178正式来源完整初始化迁移包：531.1

**已撤回，不作为最终交付或部署输入。** 需求方随后明确“以178当前运行库实表为准”。
本目录526表及源码增量方案仅保留验证追溯；最终包为
[178运行结构包](../production-178-runtime-20261101/README.md)，518张正式表，不含V531.1增量。

2026-11-01。**主库及DAC迁移验证通过，DPPMS/178来源库只读，未部署。**
需求方已确认继续采用unicode_ci、保留所有原值并接受已查明的比较语义变化；本次完成剩余7列
全量检查、补齐核定源码结构缺口，并保留全部178正式配置。

## 冻结内容

| 项目 | 内容 |
|---|---|
| 主库 | B531原件 + V531.1结构增量，526张表、9615列、240个触发器、285组外键 |
| 正式配置 | 6026条，沿用178正式筛选；五套项目模板及10个已发布修订、正式流程和组织主数据保留 |
| 字符集 | 4187字符列全部utf8mb4；3641列unicode_ci、509列bin、37列0900_bin |
| DAC | 13份冻结迁移，至V26，24张业务表；必须使用独立数据库及独立Flyway历史 |
| 应用源码修正 | 结项守卫Mapper改用V255已更名的acc_project_closure_record；随包附单行补丁和原始文件 |

`migrations/B531__production_178_unicode_initialization.sql`与上一轮B531逐字节相同。
`V531_1__complete_approved_source_structure.sql`只从锁定提交
`77bc691cbc5ad1e58bb51252f2a31dd5047fd6d6`的8份明确来源SQL选择必要语句，
不重放全部历史目录，也不混入另一套fresh初始化的示例配置。
来源文件、SHA-256和范围见`structure-lineage.json`；两条迁移由`migrations/release.json`共同冻结。

新增8张表：四张设备维保原始事实／项目关系表、两张同步发布范围表、两张项目旧明细证据表。
现有表新增39列，另恢复4列可空性、替换3个有效修订唯一索引、补充1个来源身份查询索引和
2条联系人归属／有效期CHECK。新表自身118列，总新增157列。没有删除、合并或改写业务记录。

不重建`sol_engineering_briefing_retired`、已退役软件观察表或旧合同镜像表。
不重新添加178按既有批准取消的历史成员日期CHECK；其他授权、状态、历史保护约束保留。
三列新增来源摘要由ascii_bin升级为utf8mb4_bin，保留精确比较；其他新增字段沿用已批准来源
的明确比较规则。B531已确认的业务来源键unicode_ci方案不回退。

## 文件校验与使用边界

解包到独立目录，执行纯文件检查：

```powershell
python -B scripts/production/release.py verify migrations sql/releases/production-178-v531-1-complete/migrations
python -B scripts/production/release.py verify dac-migrations sql/releases/production-178-v531-1-complete/dac-migrations
```

本主库包用于**新的空库**。不可将B530、另一套fresh基线、历史SQL目录或并行未冻结迁移拼入本目录；
不可把DAC SQL放入主库。已有库不在本次执行范围，不得clean、repair、手改历史或绕过校验。
非空库拒绝、迁移重复执行历史保持、篡改清单拒绝均有真实验证。

正式部署使用现有原生Flyway发布流程并另行授权；本次没有切换现有默认初始化入口。
执行必须保留cleanDisabled、baselineOnMigrate=false、validateOnMigrate、
validateMigrationNaming和placeholderReplacement=false。迁移器显式加载MySQL Connector/J，
TLS校验证书与主机身份；binlog环境中的触发器创建权限按既有部署方案处理。
验证环境中的临时权限不构成正式全局授权建议。

`bootstrap-admin.sql`保留178正式管理员初始化流程，未执行；真实账号凭据、外部连接、存储、
正式巡检判定规则等部署期配置不从测试环境生成或猜测。原包明确排除的合成巡检规则不会补回。

## 对应应用源码

`source-fixes/project-closure-table-reference.patch`仅将结项守卫查询的旧表名改为实体和V255一致的
`acc_project_closure_record`。租户、项目范围、逻辑删除过滤及顺序不变。实测旧SQL报表不存在，
修正后实际XML SQL按预期返回，跨租户、范围外和已删除记录均排除。
应用构建时必须包含此修正；本包不含重新构建的后端JAR，不代表运行中服务已更新。
补丁基线、文件及补丁摘要见`source-fixes/manifest.json`。

## 验证证据

[完整报告](../../../docs/generated/2026-11-01-production-complete-migration/README.md)和
[结果汇总](../../../docs/generated/2026-11-01-production-complete-migration/summary.json)。
365列的字符转换检查无字节差异；相等分组328列保持、37列按已确认方案变化，未完成项为0。
这是数据类型/比较规则和初始化包的验证，没有执行DPPMS业务同步或完整业务导入，也没有应用
启动或真实浏览器验收；不宣称所有跨字段变换、绑定或运行时流程已经验收。

原始宽范围实体扫描保留3项旧导入类映射告警，完整原件随证据交付；对应路径已按批准的退役／
历史范围区分，没有为了扫描通过重建废弃表或降低检查。核定活跃结构和本次新增完整性问题均为0。
本包的源码范围是上述锁定提交加明确单行修正，不夹带工作树并行的登录、客户链接、培训及V532改动。
