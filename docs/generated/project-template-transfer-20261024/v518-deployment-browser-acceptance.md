# V517/V518 测试环境迁移与浏览器验收记录

日期：2026-10-24。依据：需求方批准补齐割接审批业务视图初始化、保留配置与历史、在当前测试环境执行迁移并使用admin验证；随后明确批准只补充admin的001公司＋310407办事处测试范围。

## 结果

- V517、V518已执行至`172.17.90.178:13306/npdms`，Flyway当前版本518。严格validate通过（19项历史），重复migrate无待执行迁移。未重启应用。
- V518仅发布V502原样默认PAGE视图`996610195020037`，保持被模板引用的ID、业务正文和创建来源；已发布/停用/删除跳过，自定义内容或其他修订冲突失败保护。
- admin真实浏览器创建项目`84572 / PJT2026000001 / V518初始化迁移验收-20261024`成功，唯一自动命中`TPL-CFG20261014-GENERAL-DIRECT` v1。页面展示当前模板、执行计划和16个任务，数据库确认16个任务均冻结V517已发布状态机`996610195170001`。
- 独立割接工作台及“我的审批待办”正常打开；任务内割接列表也正常加载为空。完整的任务业务视图验收仍有下述快照缺口，不记为全部通过。

## 历史与范围保护

迁移前后核对旧Flyway历史、旧状态机及规则、其他业务视图、被修复视图的业务正文均不变；模板、修订、既有项目/任务/成员、角色及菜单关系、割接配置、表单和问卷等受保护表摘要一致。比较发生在浏览器新增验收数据之前。

admin原来没有联合范围，服务端拒绝模板匹配。经单独批准，只新增范围`996610245180001`：用户1、租户1、公司001、部门310407、沿用现有`super_admin`标识，creator/updater为`v518-browser-acceptance`；不改角色分配或已有范围。验收项目选择已有“测试客户”（16200000000），不绑定合同，实施地点为“初始化迁移验收测试环境”。验收项目与范围保留供复核。

## 验证与证据

- `python scripts/tests/verify_v518_cutover_view_mysql.py`：真实MySQL 8.4/Flyway 11.10.5隔离库，V517 20项、V518 17项通过。覆盖精确发布、业务正文/历史/其他表保护、validate、重复执行、自定义Provider/动作/Schema/版本拒绝、其他修订冲突、已发布/停用/删除保护、注入更新失败回滚与恢复。
- 隔离证据：`.run/v517-verification-fb94db5333/v518-results.json`及同目录日志。
- 远端证据：`.run/v518-remote-deployment/verification.json`、`before.json`、`after.json`、`migrate.log`、`validate-after.log`、`repeat.log`。Docker直连超时后使用进程内本机转发执行Compose Flyway，转发随进程退出；未修改数据库访问权限或防火墙。
- 浏览器入口：`http://127.0.0.1:19086`，既有后端59286，真实admin会话。`.run/v518-browser/`保存`template-match.txt`、`create-success.txt`、`project-instantiated.txt`、`approval-entry.txt`、`cutover-task-entry.txt/png`、`database-verification.json`与`admin-scope-receipt.json`。

## 未通过范围与后续修复边界

新项目`DEPLOY_CUTOVER`绑定引用`businessViewRevisionId=996610195020037`，但冻结执行快照没有`businessViewSnapshot`。`ProjectOperationCapabilityQueryService.presentation`因此返回`VIEW_NOT_BOUND`，任务规则区可见此提示。V518发布视图不会补写已有模板/计划的不可变快照；这与“视图草稿未发布”是不同缺口。后续应沿合法模板复制、校验和发布链生成完整快照的新修订，验证新实例；不能覆写已发布模板或本次项目历史来消除提示。

项目仍在S0待指派服务经理，S4准入条件不满足，发起割接显示`NODE_EXECUTION_FORBIDDEN`，没有绕过阶段、执行割接创建或审批提交。空列表和入口加载不证明审批业务完成；未推进Feature整体完成状态。

本次仅迁移与种子修复，无应用代码变更，未运行应用构建；SQL保护分支和历史保留已自审，无独立评审声明。未提交或推送Git。
