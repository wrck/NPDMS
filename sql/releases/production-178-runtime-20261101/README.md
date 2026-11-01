# 178当前运行结构正式初始化迁移包

2026-11-01。**以178当前运行库实表为权威，正式包518张表；主库与DAC校验完成，未部署。**
本包替代先前526表源码补全候选，后者已撤回。不会补入仅仓库源码存在的表、字段、索引或约束。

## 结构和数据来源

2026-11-01 21:51（北京时间）重新只读核对178：520张物理表，表定义及触发器与已审阅的178
正式快照一致，没有新增、删除或结构变化；比较时仅忽略运行数据推高的AUTO_INCREMENT水位。
沿用正式包已批准的排除规则：不复制源`flyway_schema_history`，不重建退役表
`sol_engineering_briefing_retired`，因此为518张正式表。新库自行建立Flyway历史。

主库：9458列、240个触发器、285组外键、6026条选定正式配置。五套项目模板及10个发布修订、
流程、字典、菜单、组织主数据、不可变快照保持。测试业务、原用户密码、外部连接和同步实例不导入。
确认使用unicode_ci的决定保持：4105字符列全部utf8mb4，其中3571列unicode_ci，497列bin，
37列0900_bin；保留已核定的安全／内部精确身份例外，不改写任何字段值。

主库只含`B531__production_178_unicode_initialization.sql`，与已验证B531逐字节相同。
本包**不包含**V531.1、8张源码新增表、39个源码新增字段或相关索引／CHECK变化。
DAC仍为独立的13份冻结迁移至V26，24张业务表，使用独立数据库和Flyway历史。

## 检查与执行边界

纯文件校验，不连接数据库：

```powershell
python -B scripts/production/release.py verify migrations sql/releases/production-178-runtime-20261101/migrations
python -B scripts/production/release.py verify dac-migrations sql/releases/production-178-runtime-20261101/dac-migrations
```

仅用于新的空库；已有库沿原迁移链，不得clean、repair、修改历史或拼接其他基线。
不得将DAC迁移放入主库，也不得将工作树历史SQL或未冻结增量追加到本包。
现有默认初始化入口未切换，正式部署仍须单独授权并按既有原生Flyway发布流程执行。
保留cleanDisabled、baselineOnMigrate=false、validateOnMigrate、validateMigrationNaming、
placeholderReplacement=false；迁移器显式使用MySQL Connector/J，并校验证书及主机身份。
binlog下触发器创建权限按部署方案处理，隔离测试中的临时权限不代表正式全局授权建议。

`bootstrap-admin.sql`保留原正式管理员初始化流程，本次未执行。管理员真实凭据、连接、文件存储、
正式巡检规则等环境配置不从测试值推导，也不将缺少正式来源的合成验收规则补回。

## 证据与限制

[最终报告](../../../docs/generated/2026-11-01-production-runtime-package/README.md)、
[汇总](../../../docs/generated/2026-11-01-production-runtime-package/summary.json)。

- 365个DPPMS来源字符列转换无字节差异。328列相等分组保持，37列有已确认政策下的变化，未完成项为0。
- 本次剩余7列使用独立聚合、单条30秒上限、REPEATABLE-READ只读事务完成，未修改原库配置或数据。
- 主库首次迁移、validate、重复执行、非空库拒绝、冻结数据保护和逐表配置对账通过；仅初始管理员／
  租户的4个原本由CURRENT_TIMESTAMP生成的执行时间字段单独记录，不忽略任何来源历史时间。
- DAC首次迁移、校验和重复执行通过。SQL未变，按逐文件哈希复用有效隔离证据，没有无意义重跑。

本包匹配178当前运行库，不宣称仓库所有未部署源码都与该库兼容。源码补全方案和相应验证保留为撤回
候选，不进入最终包。另发现的结项守卫源码表名引用修正仅是工作区独立变更，未编译或部署，也不夹入SQL。
未执行业务同步、完整业务数据导入、应用启动或浏览器业务验收；DPPMS和178始终只读。
