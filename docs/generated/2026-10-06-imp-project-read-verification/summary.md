# 历史阶段快照：IMP 配置调试与联调的公共读取范围接入

本目录保留第一阶段13项验收及原实现patch。后续测试已迁到应用装配模块并扩展到四个身份；当前未提交源码、28项结果和完整待发布清单以[合并批次报告](../2026-10-06-project-read-batch-verification/summary.md)为准，不能将此处旧源码hash当作当前源码，也不能累加两阶段测试数。

本段基线为 `d4fca642fe17a230286ae9ef6ed5ea883f927701`，变更保留在私有工作树，未提交、未推送。生产改动只有 `LegacyProjectReadBindings.java` 增加两行身份：`IMP/configuration`、`IMP/jointTest`。两者复用同一个 `OwnerProjectReadScopePolicy` 与 `projectId`，由 `ProjectBusinessScopeAccess` 调用项目 `PROJECT_VIEW` 范围；没有新增实体专用策略。

真实生产目录保持各自的 `pms:imp-configuration:query`、`pms:imp-joint-test:query` 权限和原可读投影。Owner 项目字段从实际 DO/Mapper 行读取，不能通过客户端字段筛选伪造。原生控制器 query 权限、`NativeAttachmentAccess` 的 query + 项目 VIEW 读取规则及 DO 的 projectId 是本次绑定依据；目录贡献者、原生服务、生命周期、统一操作列表、字段开放和文件完成链路均不修改。

## 该阶段源码验收

独占 tmpfs MySQL `27601/imp_project_reads_verify`；实际生产工程目录、两个 MySQL Mapper/XML、Spring 公共绑定/访问装配、系统 Role/Menu/Permission 服务和权限表。项目范围 API 返回值受控，系统权限服务按真实 SQL 角色/菜单判断，缓存代理与完整登录过滤链未加载。

相同测试文件与运行脚本在未绑定基线为 **1 通过、2 断言失败、10 错误、0 跳过**；合法读取错误为 `SCOPE_POLICY_NOT_DECLARED`。只加入两个身份后为 **13 通过、0 失败/错误/跳过**，Maven reactor 构建成功。首次权限 mock 变参类型错误、随后权限服务夹具装配错误分别留存，均不作为业务红例或当前源码失败；见[逐用例结果](test-results.json)。

| 验收项 | 参数化用例数 | 结果 |
|---|---:|---|
| 本项目真实行、游标分页、跨项目/已删除/无 projectId 拒绝、客户端不能开放 Owner 字段 | 2 | 通过 |
| 空或 null 项目范围列表为空、详情拒绝 | 2 | 通过 |
| 跨租户行隔离、tenant/user 必须匹配已登录身份 | 2 | 通过 |
| 无 query 权限拒绝、撤销一个实体权限不影响另一个已授权实体 | 2 | 通过 |
| 只读和原生写权限用户的统一 CREATE/UPDATE/DELETE/COMPLETE 均拒绝；只读用户原生附件写入拒绝 | 2 | 通过 |
| 真实行 Owner 改归属及项目范围撤销后立即收紧读取 | 2 | 通过 |
| 其他未绑定 IMP/installation 仍报 SCOPE_POLICY_NOT_DECLARED | 1 | 通过 |

写入拒绝同时核对记录 code/version 未变、幂等/审计/事件端口未使用、没有项目锁调用。没有把 query 权限替代原生 update 权限，也没有为这些旧模型补造统一操作或声明式写入能力。

## 精确变更与证据

- [implementation.patch](implementation.patch)：生产两行 + 一个新 MySQL 验收类 + 一个独占运行脚本；不包含报告本身。
- [source-files.json](source-files.json)：三项变更源码和十九项未变生产依据的 SHA-256；[private-evidence-manifest.json](private-evidence-manifest.json) 固定红/绿日志、XML 与实际编译 class 哈希。
- 运行：激活 `/workspace/toolchains/activate.sh`，然后 `IMP_READ_MAVEN_SETTINGS=/workspace/toolchains/maven-settings.xml bash scripts/tests/verify_legacy_engineering_project_reads_mysql.sh`。
- [boundaries.json](boundaries.json)：四禁改路径/KNO/历史 SQL/原生写服务边界及清理结果。专用 Compose 已删除，共享 MySQL/Redis 保持原容器运行。

## 剩余范围

本段仅接入两个身份，没有扩大其他实体的权限依据。上一轮 34 个缺范围身份中的这两项已得到本段实际验证，其余 32 项未接入；未重跑全 79 模型，不能把此数字推导成全目录运行验收。

当前新源码未重建普通应用 JAR，未跑完整 Tomcat 登录/UI/浏览器、项目树授权合并算法、生产缓存代理或全量后端。上一轮普通 JAR/登录/浏览器结果不充作本段结果。V374 历史迁移、旧阶段冻结合同/outbox、KNO、项目创建/详情/历史迁移均保持既有边界，没有 repair/skip 或补造合同。本段不需要新的产品权限决策；发布尚待本段精确 diff 复核。
