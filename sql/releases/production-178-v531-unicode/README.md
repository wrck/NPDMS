# 178 正式配置的 B531 字符集迁移包

**状态：字符集方案已确认，空库迁移验证通过，未部署。只用于新的空库，不用于升级178或任何已有库。**

需求方2026-11-01指定以178正式包为基础生成新包，DPPMS原库只读。本包沿用
`production-178-v530-r2` 的518张表、240个触发器、6026条正式初始化配置；不追加
另一套fresh包的示例数据，不修改原包，不导入DPPMS业务记录、原账号密码或同步实例。

## 本次变化

- 4105个字符列统一字符集为utf8mb4；1407列调整字符集／排序规则，184张表调整默认值。
- 按既有逐字段方案：3571列为utf8mb4_unicode_ci，497列为utf8mb4_bin，37列为
  utf8mb4_0900_bin。后两类保留安全摘要、密文、引擎及内部精确身份的必要比较规则。
- 7个已核定的业务来源身份字段采用unicode_ci：基础同步source_key、合同主来源键、
  订单及订单行来源键、订单合同关系的两个来源键、旧项目明细来源合同号。
- 所有非CREATE TABLE SQL字节原样保留，包括种子、不可变JSON快照、BPMN、触发器正文、
  非空库拒绝逻辑。表中其余类型、长度、NULL、默认值、索引、FK和CHECK不得变化。
- 原库schema与配置捕获来源、原迁移SHA-256和新迁移SHA-256见`source-manifest.json`；
  逐字段决策见`collation-policy.json`。新SQL保留来源换行字节，`.gitattributes`禁止转换。

## 已确认的比较语义与验证边界

只读比较发现DPPMS当前44张来源表的365个字符字段中，35列在general_ci与unicode_ci
之间存在相等分组变化；补查另发现2列，共37列。例如原来不同的部分客户名、姓名、工号会变为相等。
这不改变存储字节，但可能影响匹配、去重、关联。最初11列的完整分组检查超时，拆分聚合后仍有7列未完成，未算作通过。
不能以字符转换没有丢字或空库迁移成功代替业务比较语义确认。

需求方已明确“仍采用 unicode_ci：保留所有原值，接受已列出的比较语义变化”。
裁决见[Q-PROD-DPPMS-COLLATION-20261101](../../../docs/decisions/open-questions.md)。因此保留本包的unicode_ci方案，不改为general_ci。
两个PLT节点载体字段的待审例外沿用来源方案的原状态，没有擅自认定为新binary例外。
应用显式BINARY、派生哈希、Java字符串比较未改动，也没有执行真实业务同步或浏览器验收。

本包仅完成字符集范围改造。178原包已知的8张有效源码表、39个既有表字段等缺口
仍见[前次双包比较](../../../docs/generated/2026-11-01-production-migration-rehearsal/README.md)，
不因本包生成而宣称当前分支完整可部署。正式巡检规则来源缺失也仍未解决。

## 文件与检查

- `migrations/B531__production_178_unicode_initialization.sql`：唯一主库迁移。
- `migrations/release.json`：使用现有发布工具生成的逐文件冻结清单。
- `bootstrap-admin.sql`：从178正式包保留的管理员初始化入口，未执行。
- [只读比较与隔离验证报告](../../../docs/generated/2026-11-01-production-unicode-comparison/README.md)。

本地只检查文件，不操作数据库：

```powershell
python -B scripts/production/release.py verify migrations sql/releases/production-178-v531-unicode/migrations
```

本包没有替换`prepare_baseline_migrations.py`或正式默认发布入口；不要将B530与B531放在
同一个已有库迁移链中。正式部署仍须单独授权，遵循现有四机部署手册；不得repair、clean、
改历史或绕过校验。MySQL8.4、明确的Connector/J与TLS身份校验、binlog下创建触发器权限
仍是前置条件。隔离验证的临时权限不构成向正式迁移账号授予全局权限的建议。
