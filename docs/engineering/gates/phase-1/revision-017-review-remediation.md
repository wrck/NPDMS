# 修订017：PR #1已确认问题整改

> 范围：PM-01首次项目经理职责、数据库通用字段、CLO-02摘要及与master合并冲突。
> 用户确认：项目经理指派同步Feature；参考表按数据库规范确定；其他复核建议接受。
> 当前记录不产生新的Gate，不声明应用功能、升级迁移、Feature Done或正式SDS批准。

## 已完成

- F-PROJ-001承接PM-01@V1首次PROJECT_MANAGER指派，补齐范围、权限、唯一命令、API、验收和T-ASSIGN-PM事实完成路径；不要求已有项目经理，不把PM-09@V2或TASK_NATIVE COMPLETE作为前置。F-PROJ-005只消费该责任事实。
- CLO-02在状态总表中明确产生NORMAL_CLOSED或NO_TRACKING_CLOSED；PM-10仍只产生EXCEPTION_CLOSED。
- 12张参考表按09分册2.1统一version INT、creator/updater VARCHAR(64)、create_time/update_time DATETIME(3)、deleted BIT(1)，同步索引列名，保留现有COM水位表的payload_version和last_change_type。不改正式Flyway或应用表。
- 保留master巡检011～013、ERP/CUT/默认租户014及近期应用修复；PR #1重号的011/012/014/015/016仅保留来源历史，由未占用的017统一承接。原始来源为4cb69b66，主干应用基线为c104ae08，认领为d20f7808。
- 33个文本冲突逐项合并；满意度、CUT、导出、COM现有细则保留，投影从合并后的正式输入重新生成。当前目录为106对象/124来源绑定/1排除源；历史统计不改写。

## 验证

- 新隔离MySQL 8.4参考Schema：12表、42/42用例通过；使用无网络、无宿主端口的容器，不连接现有业务数据库。通用审计字段使用非数字字符串schema_test，证明不再受BIGINT主体限制。
- 首次执行发现索引仍引用created_at并失败；改为create_time后在新空验证Schema重跑通过，没有降低约束或把首次失败记为成功。
- 当前结果另存于`../phase-2/revision-017-standard-fields-mysql-schema.json`；原修订016执行报告与历史自审记录保留不变。
- 24项参考Schema/Owner/批准边界回归、10项追溯生成回归、39项实体映射回归、12项PRD联动回归通过，共85项；当前Schema生成一致性和PRD联动检查通过。
- 本地执行报告明确标记LOCAL_WORKTREE及sourceTreeDirty；sourceCommit表示执行工作树的Git基线，不冒充已提交输入。PRD和Schema内容绑定使用Git文本规范，不改用户工作区换行来满足校验。

## 保留的既有问题

完整实体映射校验在未修改master和本合并工作树产生完全相同的13项失败：ExportTask覆盖/Owner、ProjectStageSnapshot与AcceptanceScopeBinding跨域归属、SatisfactionCollection的PRESERVE_RAW规则，以及三项CUT备件对象证据识别。它们来自主干既有校验口径与已登记映射不一致，本轮不通过删除主干映射使检查清零。

因此本次两项整改与参考Schema验证已完成，但不把旧TECHNICAL_GO复制为合并后全套检查通过；现有Phase 1/2/3继续REVALIDATION_REQUIRED，整体技术与机器状态保持PENDING。真实Reviewer只对本次差量进行定向复核，不代签整个SDS批准。
