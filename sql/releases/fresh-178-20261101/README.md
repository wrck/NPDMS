# 178最终状态与锁定代码整合的全新库迁移包

状态：**全新库验证通过，正式环境未执行**。验证时间2026-11-01；应用来源锁定`codex/local-performance-20261025`提交`385e10f9ee310f82896a5dce5251e47aeaa17902`。用户确认的口径是“178为底座，并包含锁定分支新增结构”，同时统一字符集和排序规则。

## 适用边界

仅用于新空库。主库使用独立的B530基线，DAC保留独立V1～V26迁移集合。不得向178、179或任何已有Flyway历史的数据库切换到本集合；不复制、伪造或repair历史记录。不得混入整个`sql/migrations`目录。

178（MySQL8.4.11）仅执行只读查询。快照包含519张业务/平台表（另有1张Flyway历史表）、240个触发器、33条成功迁移历史。前后复核的结构与历史完全相同，没有导出项目、合同、设备、审批实例、账号密码或用户角色绑定等业务运行数据。来源配置仅取字典、菜单、角色及角色菜单关系；其他初始化配置来自隔离库对仓库B498及锁定增量的演练结果。

新库由527张业务/平台表组成，保留全部178底座，增加源码中8张表、157个字段。4个可空字段和3个需求修订唯一键按锁定源码覆盖；其余共同对象的语义不变。45张Flowable表完整保留，初始化属性与项目依赖Flowable8.0.0一致，`next.dbid`从1开始。不继承测试库自增水位或流程运行记录。

## 字符集规则

- 数据库及普通文本使用`utf8mb4` / `utf8mb4_unicode_ci`。
- 原有PAD SPACE二进制字段统一为`utf8mb4_bin`；原有NO PAD二进制字段继续使用`utf8mb4_0900_bin`，避免改变尾随空格的唯一性含义。
- 不保留utf8mb3、ascii或普通文本的0900_ai_ci混用。二进制来源键、哈希等字段仍区分大小写。
- DAC的192个字符列本身已全部为`utf8mb4_unicode_ci`，未修改原DAC脚本。

## 文件

| 文件/目录 | 用途 |
|---|---|
| `migrations/` | B530主库基线及SHA-256发布清单 |
| `dac-migrations/` | 13份原始DAC迁移及独立发布清单 |
| `source-manifest.json` | 178历史映射、输入摘要、58项明确的结构增量/覆盖及验证版本 |
| `expected-schema.json` | 实际通过验证的元数据，用于结构比对 |
| `expected-seed-counts.json` | 初次初始化后的种子表行数，业务启用后不再用于行数判定 |
| `verify.sql` | 初始化后的只读检查：字符集、种子、字典/角色关系及全部285个外键 |

对应证据：[最终验证报告](../../../docs/generated/2026-11-01-production-release/final-fresh-validation/README.md)。

## 执行前与验证

使用正式环境同版MySQL8.4.13，镜像摘要和来源记录见清单；Flyway11.10.5。主库与DAC使用不同数据库和凭据。配置文件经既定秘密渠道提供，不把密码写入制品。

先在仓库根目录校验冻结包：

```powershell
python scripts/production/release.py verify migrations sql/releases/fresh-178-20261101/migrations
python scripts/production/release.py verify dac-migrations sql/releases/fresh-178-20261101/dac-migrations
```

目标服务器须使用本次支持B类基线的`release.py`。`operate.sh`既有流程会在publish、activate及每次Flyway操作前校验SHA-256清单。本次实测Flyway11.10.5的validate未拒绝已执行B基线的内容变化，**不可省略清单校验或用validate代替它**。

依既定正式部署手册分别发布迁移制品，保持`baselineOnMigrate=false`、`cleanDisabled=true`、自动应用DDL关闭。首次migrate后执行validate、info、`verify.sql`；第二次migrate必须无新增执行且历史不变。B530另有非空目标保护。

建表期间仅为声明循环依赖暂停外键检查，导入数据前恢复。种子表按外键依赖排序；动态表单的两个循环/自引用外键在种子齐全后通过ALTER ADD创建，该步骤会校验已有数据。最终外键没有遗漏，285项孤儿检查均为0。240个触发器在种子导入后创建，保留178的SQL_MODE和业务保护内容。

管理员初始为禁用状态，密码是`!BOOTSTRAP_REQUIRED!`占位符；未复制178管理员密码或一般用户。生产账号、真实凭据、存储接入及启动前配置仍须按既定部署流程处理。现有B498的受控管理员引导逻辑可作为操作参考，不自动执行其中的存储初始化。

## 已验证结果与限制

主库首次迁移、validate、重复migrate、结构比对、字符集检查、初始化引用完整性均通过；非空目标保持原数据并拒绝迁移，SHA-256篡改拒绝，不可变配置删除被真实触发器拒绝。主库含527张表、9644个字段、1914个索引、285个外键、461个CHECK和240个触发器（不含Flyway自身表/索引）。7374条初始化数据外，其他业务运行表为空。178的33组技术支持/研发权限关系逐项一致。

DAC在独立MySQL8.4.13空库迁移至V26，13份脚本的migrate/validate/info/重复migrate通过；含24张业务表及1张Flyway历史表。

这些结论只覆盖数据库初始化。锁定代码的前端类型检查和后端测试仍有此前记录的失败，生产OIDC/KMS等配置尚未完备；数据库验证通过不等于应用可发布或业务验收通过。
