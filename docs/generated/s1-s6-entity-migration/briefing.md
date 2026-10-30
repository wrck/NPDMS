# 工程交底独立实体承接

## 范围与基线

- 仓库：`wrck/NPDMS`。
- 来源分支：`codex/liteflow-remediation`。
- 来源提交：`d13798671f1ce0ca44f5d17a88c92fe579f92eb9`。
- 实施分支：`codex/s1-s6-business-entity-migration`。
- 单一迁移单元：工程交底，原代码标识 `FR-ENG-006`，原主表 `pms_eng_briefing`。
- 按需求方本轮指示，以代码和表结构为依据，不比对或修改 PRD。不重做需求分析和现场工勘。

## 已读取的旧实现与承接范围

完整读取原 `BriefingController`、5 个 VO、`BriefingService`、`BriefingServiceImpl`、
`BriefingDO`、`BriefingMapper`、独立页面和前端 API，以及 V27 建表和 V218 类型字典补充。
这些文件全部位于上述锁定提交。原独立页面字节数 17400、Git blob
`4975af095d59b1530af4f680d2ff65b04d78b87a`；复制前已在本地重新计算匹配。

承接全部 22 个显式业务字段、共享审计和租户字段，保留模板快照、前序快照、正文、
文件 URL/名称/大小/校验值、编制和审核信息、生成/发布时间、状态和并发版本。
保留新增、更新、删除、详情、分页、生成、审核通过/驳回、发布、作废操作。
状态仍为 0 草稿、1 已生成、2 已审核、3 已发布、4 已作废；类型保留
`STANDARD / EMERGENCY / CUSTOM`，不替换原字典取值。

## 新旧隔离

- 新实体：`BriefingEntityDO`，直接继承共享 `TenantBaseDO`，不继承旧业务类。
- 新表：`sol_engineering_briefing`，新增只读追溯字段 `legacy_source_id`。
- 新 HTTP 前缀：`/api/v1/pms/engineering-briefings`；保留 9 个原业务操作路由，另加显式承接路由。
- 新 Service、Mapper、查询对象和 VO 独立；普通运行 SQL 不访问旧表。
- 新页面：`pms/engineering/briefing/entity/index`；API：`engineering/briefing/entity.ts`。
- 新菜单使用高段 ID `993109170001`，复制原菜单父级、权限和有效状态；仅给原菜单已有角色关系增加同权限入口。
- 不删除、覆盖、停用原类、接口、表、页面、菜单、权限或项目详情入口。
- 旧项目详情对旧 BriefingApi 的调用保持原样；本提交不将旧入口偷偷切到新实现。

新页面来自完整旧页面。模板内容逐字保留，所有事件处理函数保留，只切换业务 API、
组件名称，并清空“编辑后新建”可能残留的模板、文件及审核元数据。
共享项目选择、用户选择、标签、富文本编辑器和上传组件继续复用。

## 存量承接

V251 只建独立表和增加独立菜单，不隐式搬运业务数据。
`POST /api/v1/pms/engineering-briefings/import-legacy?id=...` 为显式逐对象承接入口，
同时要求既有交底查询和创建权限；租户仅取服务端上下文。

专用 `BriefingEntityImportMapper` 是新代码中唯一可读取旧表的 Mapper，没有旧表写方法。
`BriefingEntityImportSource` 是不继承 BaseDO 的普通投影，插入参数也使用此投影，
避免平台字段填充器将历史空审计字段改成当前时间或当前用户。

每个对象一个事务，先锁定同租户来源，再锁定目标。保留原主键、删除标记和全部内容；
不调用生成、审核、发布等业务操作。原主键已被新对象占用、来源改变或目标被修改时拒绝，
不重编号、不自动覆盖、不修改旧关联。重复承接比较完整字段和共享元数据，不使用摘要替代内容比较。
首次插入后回读完整内容；不一致抛异常并依赖 Spring 事务回滚。
实际事务及数据库参数行为仍需 MySQL 集成验证。

应先完成计划承接，再开放新表上的新增操作，以减少原主键与新建对象的冲突。
发生冲突需要明确处置，不通过扩大权限、覆盖目标或回写旧表绕过。

## 仅在新副本处理的实现问题

1. 复制原状态规则，独立 Mapper 锁查询放入 XML，显式约束租户与逻辑删除。
2. 修正原状态操作手工增加 `@Version` 后再更新、又忽略更新计数的风险；新实现由插件增加一次版本并检查影响行数。
3. 新建不接受客户端指定主键、初始并发版本或来源；业务 DTO 不接受审核和生命周期元数据。
4. 状态写入前清空更新者和更新时间，由现有填充器记录本次操作，而不是保留读出的旧更新信息。
5. 分页转换为专用 Query；新接口时间查询为左闭右开。原接口查询语义不变。

## 实际验证

本会话环境为 JDK 21、Node.js 22；不是项目要求的完整 JDK 25/Maven 环境。

- 独立编译并运行新状态策略，46 项断言通过，覆盖 5 个合法状态、非法/空状态和审核动作。
- 执行真实新前端 API 源码的 10 项调用场景，检查 HTTP 方法、路由、参数及输入不变性。
- 执行结构检查：22 业务字段、响应字段、全部承接字段、10 路由/权限注解、Mapper XML、旧表只读边界、新表/菜单及页面模板/事件保留。
- 首次状态策略编译发现闭合括号缺失；修正后重新编译及运行通过，不将失败的首次运行计作通过。
- 新增 7 项 Service 和 6 项 ImportService 的 JUnit/Mockito 用例；**尚未执行**。

运行可用检查：

```sh
python tests/migration/briefing/verify_structure.py
javac -d /tmp/briefing-check \
  pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/briefing/entity/BriefingEntityStatePolicy.java \
  tests/migration/briefing/BriefingEntityStatePolicyCheck.java
java -cp /tmp/briefing-check BriefingEntityStatePolicyCheck
NODE_PATH="$(npm root -g)" node tests/migration/briefing/api-check.cjs
```

Node 检查需要可解析项目安装或全局安装的 TypeScript，不代表完整 Vue 类型检查。

## 未完成项与完成口径

本提交提供工程交底可独立使用的新实体、接口、页面和显式存量承接代码，
**不声明工程交底的全链路验收完成，更不声明 S1～S6 全部模块迁移完成**。

未执行：完整后端编译、Spring/Mockito/JUnit、MySQL 建表/迁移/冲突与回滚实测、
前端完整类型检查与构建、真实 API 和浏览器验收、部署或运行库变更。
当前容器没有完整仓库工作树，Git 直连因 DNS 失败；代码通过 GitHub 连接创建。

尚未切换：新的模板/任务/阶段消费者、完成事实 Provider 与执行上下文适配。
现有项目详情/模板旧调用保持不动；后续接入必须使用新业务 Owner，不能将仅增加 WorkBinding 视为完成。

原交底服务的文档生成仍是占位实现，项目存在性校验仍为空扩展点，审核人由请求提供。
这些旧问题在本次审计中明确保留为限制；本提交没有声称真实文件已生成、增加新项目准入规则或补全审核身份语义。

发布保护：提交前重新读取目标分支 HEAD，以最新树为父基线；只新增本迁移单元路径，
使用非强制快进更新引用。提交后对比父提交，确认没有任何旧文件修改、删除或改名。
