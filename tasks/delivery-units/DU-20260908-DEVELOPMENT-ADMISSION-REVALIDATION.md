# DU-20260908-DEVELOPMENT-ADMISSION-REVALIDATION 开发准入复验

> DU状态：`PLANNED`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`承接100需求/111切片审查；规范旧DU元数据，复核现有未完成Task准入，校准执行指引并准备精确开发认领；不实施业务代码`
> Owner：`Codex当前工程链复验目标会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`a26ad5d14d618ec09d76a4dc7937017f49cf846e`
> 认领提交：`SELF`
> 修改边界：`tasks/delivery-units/DU-20260908-DEVELOPMENT-ADMISSION-REVALIDATION.md;tasks/delivery-units/DU-20260903-FINT012-PARTIAL-CODE-RECEPTION.md;tasks/delivery-units/DU-20260903-FINT012-SOURCE-RECOGNITION.md;tasks/delivery-units/DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION.md;tasks/delivery-units/DU-20260908-FCUT001-EXAMPLE-SEED.md;tasks/delivery-units/DU-20260908-FINS001-TASK9.md;tasks/delivery-units/README.md;docs/superpowers/plans/2026-08-30-f-cut-001-risk-survey-matrices.md;tasks/features/F-CUT-001.md;tasks/features/F-INS-001.md;tasks/features/F-INT-012.md;tasks/features/README.md;tasks/implementation-baseline-status.md`
> 串行资源：`DU元数据与索引；后续开发认领由master协调者串行激活`
> 旧功能范围：`NONE`
> 验证：`DU真实校验和冲突负测；历史声明/提交证据保持；原计划与Spec/Task契约核对；开发写边界和认领祖先；追溯只读一致性；独立复核`
> 集成记录：`NONE`

## 目标与批准依据

用户当前目标为“完成工程链的复验，直到可以进入代码开发”。[100需求/111切片审查](DU-20260908-REQUIREMENT-IMPLEMENTATION-REVIEW.md)已完成；本DU补齐开发准入，而非把所有Feature Done、测试环境或Phase 1/2全量审计前置为编码Gate。PRD业务语义、Owner、接口/数据和适用验收标准不变。

本轮核对现有Spec/Task/唯一Technical Plan、相关Question与代码依赖，独立识别：CUT配置基础的Task 4示例初始化、INS规则基础的Task 9授权选择投影已有完整契约；INT-12下一未实现Task尚未形成唯一有效计划，不能从剩余清单直接开写。F-AST-001已有32项静态契约检查通过，但其当前运行Done复验未完成，不伪报MySQL/浏览器通过。

三份旧DU造成现行校验的10条格式错误。只补齐规范元数据，将原始状态、来源、认领/集成事实保留在正文；没有历史证据的字段写NONE，不重写历史或倒签认领，也不修改校验器放宽标准。

## 计划与完成条件

1. 本DU先在master登记并激活，再修改旧DU头部、现有CUT计划的过期编号/测试环境指引以及Task引用。
2. 原历史正文保留，Git可证明的认领/集成事实照实引用；来源登记与后继接收保持两个不同事实。
3. 验证现行DU校验、冲突/未认领拒绝测试及同仓权威规则；检查现有Task文件和API依赖，明确可编码范围及仍受约束的范围。
4. 在master先登记两份PLANNED开发DU；本治理DU释放边界后再激活。编码前必须包含实际激活提交；本轮不创建SQL或Java实现、不启用服务、不迁移测试库、不重设账号。
5. 独立复核无必须整改项、必需检查通过且开发DU合法激活后，才报告“可以进入代码开发”。这不表示整个100需求全部Ready或已实现。

## 当前证据与回执

进行中。既有全量DU校验10条错误尚未消除；不得提前标记通过。
