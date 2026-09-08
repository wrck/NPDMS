# DU-20260903-FINT012-SOURCE-RECOGNITION F-INT-012来源实现登记

> DU状态：`QUARANTINED`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`F-INT-012来源代码存在性审查及Spec/Task登记；不含来源实现直接合入或实施认领`
> Owner：`NONE`
> 分支：`NONE`
> Worktree：`NONE`
> 认领基线：`33b621065d88b6f2abc1193b46e8ac6aaad49855`
> 认领提交：`NONE`
> 修改边界：`NONE`
> 串行资源：`NONE`
> 旧功能范围：`NONE`
> 验证：`bee5bcda中的来源代码与文件Owner审查记录；本次未运行应用或迁移`
> 集成记录：`NONE`

## 元数据归一与原始来源（2026-09-08）

原状态`SOURCE_IMPLEMENTATION_RECOGNIZED / CODE_NOT_MERGED`、原类型`FEATURE_SOURCE_AUDIT`及以下审查结论保持为原截点事实。现用QUARANTINED表示当时不能直接接收的来源实现隔离，GOVERNANCE表示此记录只登记来源审查；没有历史实施Owner、目标分支、协调模式或独立认领证据的字段写NONE。认领基线字段仅承载原master审查基线，不能证明认领。

- 原Feature：`F-INT-012`。
- 原Requirement声明：`INT-12@V1=FULL`；关联`EXE-03/04、CUT-03/06、INS-02/04、NFR-02`，不证明实现完成。
- 来源分支：`prereq-parallel-check-kKiAdn@cdfbd71a1722f9696c1dbb8713566de9e88ff97c`。
- 来源提交：`84258059`、`d2d1765f`、`cdfbd71a`。
- 本记录创建于`bee5bcda556b56eb99321e194ff20f447068e166`，不倒签实施认领。后继适配/合入仅由[代码接收DU](DU-20260903-FINT012-PARTIAL-CODE-RECEPTION.md)维护，不把后继PR #4合入复制为本来源登记的集成事实。

以下“后续”及统计要求对应原审查截点；当前进度以F-INT-012 Task与后继接收DU为准。

## 审查结论

来源分支已存在采集任务、凭证授权、一次性取密、回调事实与消费确认的后端实现、Mapper和测试，故F-INT-012不得再被解释为“实际未开始”。

但该实现不能按原文件直接进入当前master：来源V104同时建立已被F-PLT-001替代的`infra_file_artifact/infra_file_version`，并改变Integration模块结构；直接合入会造成第二文件Owner、Maven依赖方向变化和V104～V106低版本迁移失效。

## 本DU登记内容

- 接收F-INT-012 Feature Spec，保留正式Requirement和Owner边界；
- 新建权威Feature Task，记录三个实际实现提交和已完成代码范围；
- 明确后续须以当前F-PLT-001、当前Integration模块和master迁移序列重构后选择性迁入；
- 不接收旧Infra文件模型、旧迁移、旧生成追溯投影或整支分支。

## 防遗漏要求

后续S4/采集能力实施计划必须将上述三个来源提交作为复用审计输入。任何完成度统计都应将其标识为`SOURCE_BACKEND_IMPLEMENTATION_EXISTS / CURRENT_MASTER_ADAPTATION_REQUIRED`，不得回退为纯`NOT_STARTED`，也不得在未完成适配时倒签为Implementation Done。
