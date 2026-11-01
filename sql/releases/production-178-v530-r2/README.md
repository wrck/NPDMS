# 正式空库初始化：178 最终结构与正式配置（复审修订版）

2026-11-01 需求方指定 `172.17.90.178:13306/npdms` 为权威来源，明确不使用旧 B498
推导正式基线，只迁入正式配置，不迁入测试业务和测试人员；同日明确排除
`SAT_MANUAL_EXAMPLE` 示例问卷。来源只读事务及捕获时间、摘要、筛选条件见
`source-manifest.json`。178 未执行迁移、DDL 或数据修改。

本修订替代未部署的 `production-178-v530` 候选包；旧包和旧验证库保留追溯。
B530 只在新的空库执行，不可覆盖已执行旧候选 B530 的数据库。

## 内容与边界

- `migrations/B530__production_178_initialization.sql`：518 张表、240 个触发器，
  59 类选定配置表中的 6,026 条配置记录。结构来自178的520张表，排除源
  `flyway_schema_history` 和 V312 明确退役且无当前 Mapper、源数据为0行的
  `sol_engineering_briefing_retired`。目标 Flyway 自建历史表，不复制来源执行记录。
- 结构包括178已有的45张Flowable引擎表，以及旧基线缺失的13张业务/同步表。
  所有列、索引、FK、CHECK、生成列及触发器正文、顺序均按来源保留；仅去掉源业务数据
  推高的自增水位，由实际初始化种子重新确定；触发器DEFINER绑定目标迁移身份。
- 保留406类字典及其值、33个正式角色及菜单授权、当前参数、表单/打印模板、业务视图、
  标准满意度问卷、已发布状态机、已发布割接配置及结构化子项。
- 保留五套启用的项目模板及10个已发布修订、7个流程模型和11份流程定义（包含保留修订
  所需的历史定义）、23份静态资源。源配置身份、发布状态、快照和引用保持一致；不改写
  冻结设计、不伪造审批、不复制流程实例。带有旧测试命名但被正式模板实际引用的业务视图
  按依赖保留，不凭名字删除。
- 排除测试项目、客户、设备、业务/审批/同步/审计实例、匹配历史、测试人员、组织人员绑定，
  以及专用验收数据。排除隔离验收项目模板草稿、停用的旧割接配置、草稿状态机、示例问卷，
  以及178唯一明确标注“合成验收、非生产判定”的巡检规则及其子项。
- 补入来源基础同步绑定可追溯的4家公司、206个部门，保留原ID、编码、层级及启停状态；
  不导入负责人、联系方式、人员绑定和源同步执行记录。排除25个OA/代码生成案例菜单、
  5条对应角色授权、OA示例字典及3个值；调试短信通道和10个依赖模板继续排除。
- 仅建立来源既有的`admin/id=1`及超级管理员角色关系。管理员停用且密码为不可登录标记；
  不读取或复制来源密码、个人资料。租户联系和域名绑定清空；内部OAuth客户端的secret、
  外部授权类型和回调地址清空。不导入数据源、文件存储、Token、外部连接及通知账户凭据。

## 准备和正式执行

本包只适用于**新的空库**。已有178、B498或旧fresh链数据库不得切换到本包；不使用
`repair`、`clean`、手改历史或关闭验证解决冲突。B530首次被用于正式环境后不可重生成覆盖；
之后新增正式迁移必须大于V530，并延续同一条链。

本地只生成、校验迁移目录，不连接数据库：

```powershell
python -B scripts/prepare_baseline_migrations.py
python -B scripts/prepare_baseline_migrations.py --check
python -B scripts/production/release.py pack migrations INIT178V530R2 .run/baseline-migrations .run/INIT178V530R2
python -B scripts/production/release.py verify migrations .run/INIT178V530R2
```

目标库须为MySQL8.4、`utf8mb4`、`utf8mb4_unicode_ci`，`lower_case_table_names=0`，
具有与来源一致的大小写行为。数据库/迁移身份、TLS、备份和发布窗口按
[正式四机部署手册](../../../docs/engineering/deployments/npdms-production-four-hosts.md)配置。
正式环境使用宿主机原生Flyway和现有`publish/activate/migrate-info/migrate/migrate-validate`
流程；包中Compose仅用于本地演练。必须保持`cleanDisabled=true`、`baselineOnMigrate=false`、
`validateOnMigrate=true`、`validateMigrationNaming=true`、`placeholderReplacement=false`。
原生操作脚本已经强制这些参数，BPMN/表单中的`${...}`是业务表达式。

通过`migrate`实际执行B530，不使用`baseline`命令替代。SQL开头会拒绝非空库。
创建阶段暂缓FK检查以恢复相互引用的配置，随后恢复FK检查；必须运行下面的实际引用校验，
不能仅凭迁移成功放行。失败保留现场和日志，未经对应库授权不自动清库或repair。

初始化后通过安全通道在同一个SQL会话设置`@npdms_admin_password_hash`为新的BCrypt哈希，
再执行`bootstrap-admin.sql`。客户端必须遇错即停，不得使用`--force`。脚本拒绝缺失/不合规
哈希和重复初始化，不写入任何明文密码，也不默认设置文件存储。

## 验证和尚需配置的事项

只读校验器使用PyMySQL，密码仅经`NPDMS_VERIFY_DB_PASSWORD`环境变量进入进程：

```text
python scripts/production/verify_database.py --host TARGET --port PORT --schema DATABASE --user READONLY_USER --ssl-ca CA.pem --output RESULT.json
```

从包含当前源码和本发布包的检出目录运行；远程连接要求CA及主机名验证，本地回环演练可省略CA。
首次管理员设置后增加`--phase bootstrapped`。校验器比对518张表的完整DDL（忽略自增水位
及MySQL8.4补写的等价字符集文本）、240个触发器、选定配置值摘要、FK和平台逻辑引用、
项目模板运行引用、当前实体直接持久化字段及同步目标列。复审新增组织父子、文档/问卷发布指针和BPM资源引用检查；引导后仍核对管理员身份和
其他种子字段，并校验BCrypt格式。任何未列入种子的运行表数据都会拒绝。退出1代表完整性失败，退出2代表
完整性通过但仍有目标配置/业务验收待办；不会将基础初始化包装成生产就绪。

实际验证：本次专用新库完成migrate、validate、重复migrate（0条），结构和6,026条配置
一致；285组FK无孤儿引用，402个当前实体直接字段无缺列；11份BPMN、7个模型和23份静态资源与上一轮校验逐值一致，沿用其
关闭自动DDL的Flowable8.0.0引擎启动及可读性证据。管理员缺哈希拒绝、首次初始化成功、重复初始化拒绝。
证据见[初始化验证记录](../../../docs/generated/2026-11-01-production-178-review/README.md)。

正式启用前仍须完成：目标存储及外部连接的受控配置；组织确认、真实人员和授权；正式巡检规则
（178中只有被明确排除的合成规则）；生产环境真实浏览器业务验收。项目模板闭环审核人
保留来源的管理员引用1，目标人员职责必须按原生配置流程确认；不得通过数据库修改已发布
快照。此包未部署到正式服务器，也未重启任何应用。

重现导出使用`scripts/production/build_initialization.py`，输入是本次已审查的只读结构和
配置快照；输出目录必须不存在。不得把未筛选的整库数据作为配置输入。原始快照摘要及
每表配置摘要已冻结在来源清单，后续环境变更不能覆盖本包。
