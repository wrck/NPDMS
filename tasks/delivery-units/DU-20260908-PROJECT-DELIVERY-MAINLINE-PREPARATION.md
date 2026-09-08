# DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION 项目交付主线准备

> DU状态：`PLANNED`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`开发前工作树/认领/工具链/运行环境检查；补齐DU新文件与真实认领校验；以项目交付S0～S6主线分析任务与依赖；不启动业务开发`
> Owner：`任务01a07efb-aaea-7440-8c34-c093d3b841b3`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`cc4ff449c1b580bd1ede7216b1696201b5a9a415`
> 认领提交：`SELF`
> 修改边界：`scripts/validate_delivery_units.py;scripts/tests/test_validate_delivery_units.py;docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md;tasks/delivery-units/DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION.md;tasks/delivery-units/DU-20260908-FCUT001-EXAMPLE-SEED.md;tasks/delivery-units/DU-20260908-FINS001-TASK9.md;tasks/delivery-units/README.md;tasks/features/F-PROJ-001.md;tasks/features/F-PROJ-008.md;tasks/features/README.md;tasks/implementation-baseline-status.md`
> 串行资源：`master治理与派发状态；只读检查固定测试环境，不变更数据库/账号/容器持久卷`
> 旧功能范围：`NONE`
> 验证：`DU已跟踪/已暂存/未跟踪路径与真实认领拒绝；原治理回归；主线模块编译；PRD/Spec/Task/Q核对；Flyway只读validate与独立复核`
> 集成记录：`NONE`

## 用户范围与写入边界

2026-09-08用户明确“现阶段先做好开发前的所有准备和状态检查，然后分析项目交付主线的任务，这是整个系统最主要的交付直线”。当前优先级据此切换为项目交付主线，不从先前已准备的CUT/INS候选反向选择业务主线。

两份CUT/INS开发DU均由本任务持有，工作树干净、未开工。本DU激活时释放其排他认领，保留全部分支/工作树和已确认契约，不改变Feature状态。任何后续开工须重新认领，不删除或合并历史候选。

工程治理变化只实现既有“全部写入位于认领边界、认领已提交”规则：把未跟踪新文件纳入路径检查，并拒绝未解析到Git提交的活动SELF认领；保留master协调入口及既有业务/历史保护，不新增审批、状态源、框架或Phase全量前置。

业务Requirement/Owner/API/数据库语义不变。主线分析引用PRD第13.1章及当前SDS、Feature和Task；不新造Feature编号、不回写Ready/Done、不修CLO、联系人或其他业务代码。文档只提出后续准备/设计与开发顺序，不替代正式Feature Technical Plan。

## 执行顺序

1. 登记并激活本DU，同时释放本任务持有的两份未开工认领。
2. 核对工具链、工作树、验证入口和固定环境；仅执行只读环境诊断，不执行migrate、repair、reset、清库或账号重设。
3. 重现并修正DU校验盲点，完成聚焦负向/正向回归和现有治理检查。
4. 分析完整S0～S6参考主线及八条关键子流程，区分V1主闭环、V2增强、已有基础、缺失Feature和真实合同缺口，整理可并行准备包。
5. 在现状入口记录当前方向、准确阻断和下一步；独立复核后本地提交，不推送。

## 当前状态

准备与分析进行中。固定测试库只读检查已经发现连接配置差异和分支迁移历史不一致，不能把容器健康或静态检查通过视为全部运行验收环境就绪。结果完成后在本记录及主线分析中如实收口。
