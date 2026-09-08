# DU-20260908-FCUT001-EXAMPLE-SEED CUT配置基础示例初始化

> DU状态：`PLANNED`
> DU类型：`TASK`
> Feature协调：`F-CUT-001=TASK_COORDINATED`
> Task范围：`唯一Technical Plan Task 4：完整幂等前向示例迁移及其静态契约；不是CUT运行态或Feature最终Done`
> Owner：`任务01a07efb-aaea-7440-8c34-c093d3b841b3`
> 分支：`codex/fcut001-example-seed-20260908`
> Worktree：`E:/AICoding/worktrees/npdms-cfgseed`
> 认领基线：`44811ab4b064070ef71e494336ad45d1175303e8`
> 认领提交：`SELF`
> 修改边界：`sql/migrations/V*__fcut001_risk_survey_matrix_examples.sql;scripts/tests/test_fcut001_matrix_examples.py;tasks/features/F-CUT-001.md;tasks/delivery-units/DU-20260908-FCUT001-EXAMPLE-SEED.md`
> 串行资源：`Flyway最终编号仅在master集成窗口确定；DU索引由master协调者串行生成，不由本Task实现修改`
> 旧功能范围：`NONE`
> 验证：`24类普通风险、五类17/25/23/24/8共97项、12类调研及匹配组合静态验证；实施后按原计划完成适用MySQL前向/幂等与最终DoD`
> 集成记录：`NONE`

## 正式输入与交付

- Requirement：CUT-07@V1、CUT-09@V1、CUT-10@V1。
- [Feature Spec](../../specs/features/F-CUT-001-cutover-unified-configuration-foundation.md)、[唯一计划Task 4](../../docs/superpowers/plans/2026-08-30-f-cut-001-risk-survey-matrices.md#task-4-幂等前向示例迁移)、[当前Task](../features/F-CUT-001.md)及已批准ADR-0031共同限定本范围；Q-FCUT001-001～003已关闭，004不构成阻断。
- CUT拥有统一配置根、采集项、绑定规则；只读消费V129字典/最小示例和V132已实现字段，不新增Owner表、业务API或新生命周期，不改已发布修订及原始证据。
- 产物只是一份新编号的幂等SQL和一份静态测试。稳定键EXAMPLE_*、高段ID、明确“示例”名称；覆盖精确命中、部分限定、优先级让位、无匹配和停用不参与。不得从附件缺名推断新业务规则。
- 原V133只作历史候选称呼。创建SQL前读取当时master迁移序列，并在集成时串行确认最终编号；本DU不预约V205或任何编号，也不授权低版本补写。

## 实施与停止条件

上述Owner为当前真实任务，分支/Worktree在PLANNED阶段只表示待创建的目标。master提交PLANNED后创建独立工作树，再激活并同步认领提交；目标分支含真实激活提交后才允许写入。当前工程链复验目标止于准备合法代码入口，不在认领时假造SQL、测试或迁移成功。

后续编码按既有计划完成最小完整示例、对应静态与真实迁移验证；固定测试环境、数据与账号操作遵守`docs/development.md`及具体授权。此认领不授权生产操作、清库、重设账号或修改相邻Feature。若需修改三表契约或边界外文件，先回master修订相应上游/DU，不把失败检查绕过。

## 交接

- 已完成：正式输入与开发范围复验。
- 剩余：实际SQL、测试及全部适用运行验证；目前均未执行，不产生Task/Feature Done。
- 后续静态命令：`python -B -X utf8 -m unittest discover -s scripts/tests -p test_fcut001_matrix_examples.py -v`（测试文件创建后执行）。
- 最终MySQL及浏览器仍按Feature Task收口；本DU结束不自动完成CUT-01～10或全部V1需求。
