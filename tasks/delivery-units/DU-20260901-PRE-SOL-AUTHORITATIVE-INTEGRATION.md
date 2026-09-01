# DU-20260901-PRE-SOL-AUTHORITATIVE-INTEGRATION PRE/SOL权威选择性集成

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`F-SOL-001/F-SOL-002/F-SOL-003集成审计;PRE-03/PRE-05/SOL-01覆盖审计`
> Owner：`Codex本次master PRE-SOL权威集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`f1c2b74a92aff44753f49ead7426b79c3e8a9018`
> 认领提交：`SELF`
> 修改边界：`docs/superpowers/plans/2026-09-01-pre-sol-authoritative-integration.md;tasks/delivery-units/DU-20260901-PRE-SOL-AUTHORITATIVE-INTEGRATION.md;tasks/delivery-units/README.md;tasks/features/README.md;docs/generated/branch-history-audit-2026-09-01-pre-sol-integration.md`
> 串行资源：`master Feature任务矩阵;master分支时间线`
> 旧功能范围：`NONE`
> 验证：`50项工程链测试；28模块test-compile；73项PRE/SOL Maven聚焦测试；42项前端聚焦测试；Delivery Unit与分支审计生成器`
> 集成记录：`无源码移植；3e27f047与master@2bdbb04c补丁等价，29111833的6个PRE/SOL路径与master树等价，486727a3为ACC分支9参数适配且不适用于master`

## 目标与边界

本DU以`master`为唯一权威集成分支，审查全部本地分支、提交、Worktree脏项和stash中的PRE/SOL物理Owner内容。只接收正式Feature/Task链路允许、当前master真实缺失且可构建的增量；补丁等价、主干已包含、跨Feature不适用、已替代或陈旧副本不得重复合入。

本DU不改变`F-SOL-001`、`F-SOL-002`、`F-SOL-003`的既有Implementation状态，不把`PRE-03`、`PRE-05`或`SOL-01`从相邻实现推导为完成。`F-SOL-003`固定章节旧载体已由独立废弃DU处理，本DU不重新认领旧路径。

## 全时间线裁决

| 候选 | 物理变更 | 裁决 | master动作 |
|---|---|---|---|
| `codex/f-sol-003-legacy-deprecation@3e27f047` | F-SOL-003固定章节旧载体废弃标记及防回流守卫 | `PATCH_EQUIVALENT`；稳定patch-id与`master@2bdbb04c`一致 | 不重复移植，沿用既有废弃回执 |
| `29111833`历史merge提交 | 5个旧现场勘察路径及前端`solution/index.ts` | `ALREADY_IN_MASTER`；6个路径均与master树等价 | 不重复合并 |
| `codex/f-acc-001-sds@486727a3` | `PreparationMySqlIntegrationTest`增加`serviceManagerUserId`构造参数 | `CROSS_FEATURE_NOT_APPLICABLE`；该提交依赖ACC分支9参数命令，master权威命令仍为8参数 | 禁止移植，避免破坏master编译 |
| 其余master外文本命中 | IMP、PROJ、PLT、CUT的文档或非PRE/SOL Owner实现 | `CROSS_FEATURE_NOT_APPLICABLE`或`SUPERSEDED` | 保持原Feature隔离 |

- `M:/AICoding/CodexData/worktrees/7a76/NPDMS`共有22项已跟踪PRE/SOL相关脏改动：其中18项与来源`3e27f047`和当前master完全一致；3个共享生成/测试脚本及`specs/features/README.md`为落后于master的分支副本，裁决为`STALE_COPY`。同工作树2项`.run/*.pid`删除不属于PRE/SOL集成范围。
- `M:/AICoding/CodexData/worktrees/50eb/NPDMS`的914个PRE/SOL路径命中全部位于`.codex-tmp/qa/`临时候选快照，无已跟踪变更，裁决为`STALE_COPY / TEMPORARY_EVIDENCE`，不得作为源码输入。
- 2个stash均无PRE/SOL物理路径命中；没有发现`MISSING_VALID_DELTA`。
- `PRE-03`、`PRE-05`仍无正式Feature/Task实施链，保持`NOT_STARTED`；`SOL-01`只存在F-PLT-002的部分覆盖，保持`PARTIAL`。本DU不得据相邻能力改变这些Requirement状态。

## 交接

- 已完成：审查全部本地分支、master外提交、全部Worktree脏项和stash；有效的F-SOL-001、F-SOL-002、F-SOL-003实现均已存在于master，因此不产生新的源码提交。
- 权威快照：以`master@75a489902f423bd1c929a279e3751a9c76d662a0`和截点`2026-09-01T23:23:14.4365352+08:00`生成[PRE/SOL集成后全部分支时间线](../../docs/generated/branch-history-audit-2026-09-01-pre-sol-integration.md)，覆盖22个本地分支、16个Worktree、470条master外分支提交与2个stash。
- 旧功能：`LegacyProjectDurationWrites`、`LegacySiteSurveyWrites`、`LegacyRequirementAnalysisFixedSections`继续以`tasks/implementation-baseline-inventory.json`中的`DEPRECATED_READ_ONLY`为权威；不得回到旧入口继续实施。
- 验证：50项Python工程链测试通过；Maven 28模块`test-compile`通过；加Byte Buddy agent后15个PRE/SOL测试类共73项通过；前端7个聚焦spec共42项通过。
- 环境说明：JDK 25下Mockito inline无法自行附加agent，显式使用仓库依赖的Byte Buddy agent后同一测试集合通过；这是测试进程启动条件，不构成产品代码缺陷。
- 结论：`INTEGRATED_COMPLETE`只表示本轮PRE/SOL权威审计和选择性集成已完成、写边界已释放；不新增或提升任何Feature Implementation Done状态。
