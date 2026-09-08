# DU-20260908-DEVELOPMENT-ADMISSION-REVALIDATION 开发准入复验

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`承接100需求/111切片审查；规范旧DU元数据，复核现有未完成Task准入，校准执行指引并准备精确开发认领；不实施业务代码`
> Owner：`Codex当前工程链复验目标会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`a26ad5d14d618ec09d76a4dc7937017f49cf846e`
> 认领提交：`44811ab4b064070ef71e494336ad45d1175303e8`
> 修改边界：`tasks/delivery-units/DU-20260908-DEVELOPMENT-ADMISSION-REVALIDATION.md;tasks/delivery-units/DU-20260903-FINT012-PARTIAL-CODE-RECEPTION.md;tasks/delivery-units/DU-20260903-FINT012-SOURCE-RECOGNITION.md;tasks/delivery-units/DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION.md;tasks/delivery-units/DU-20260908-FCUT001-EXAMPLE-SEED.md;tasks/delivery-units/DU-20260908-FINS001-TASK9.md;tasks/delivery-units/README.md;docs/superpowers/plans/2026-08-30-f-cut-001-risk-survey-matrices.md;tasks/features/F-CUT-001.md;tasks/features/F-INS-001.md;tasks/features/F-INT-012.md;tasks/features/README.md;tasks/implementation-baseline-status.md`
> 串行资源：`DU元数据与索引；后续开发认领由master协调者串行激活`
> 旧功能范围：`NONE`
> 验证：`DU真实校验和冲突负测；历史声明/提交证据保持；原计划与Spec/Task契约核对；开发写边界和认领祖先；追溯只读一致性；独立复核`
> 集成记录：`d70a9474完成旧元数据与开发计划准备；本次提交释放治理边界并激活两份独立开发DU，分支含激活提交经真实Git校验后才可编码；不产生Feature Done`

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

文档与基线复验已完成，两个独立工作树已从PLANNED提交d70a9474真实创建。本次提交释放本治理DU并激活两份代码DU；最终代码准入必须以两分支包含实际激活提交、DU校验通过为证据，不能仅凭本治理DU收口反推。

- 旧DU元数据：三份原始正文逐字保持，原头部所有引用值仍可追溯；F-INT-012原认领886bc7ce保留，无事前认领证据的S0与来源登记不倒填。SOURCE_RECOGNITION仅是QUARANTINED来源审查，不复制后继PR #4代码接收事实。
- `validate_delivery_units.py --check-index`已真实通过，35份DU无格式错误；没有修改校验器，原10条错误消除。
- 准入相关测试23项通过：DU校验/冲突拒绝8项、变更级审查8项、同仓权威3项、F-INS计划与范围4项。此前F-AST-001的32项静态契约检查仅为相应范围证据，不作全局开发前置或业务运行通过。
- Java基线：`mvn.cmd -o -B -pl pms-module-service,pms-module-cutover -am "-DskipTests" test-compile`于2026-09-08完成，27模块BUILD SUCCESS，31.192秒；验证生产及测试源码编译，不表示执行了Java测试、MySQL或浏览器。既有弃用/unchecked警告未转为本次修复范围。
- 当前Requirement追溯`--check`通过；100项/111切片的覆盖分布及Feature Done未变。18个准入文档引用可定位；仅声明的Markdown路径改变，业务代码、SQL、PRD/SDS语义和全局配置均未修改。
- 计划校准：CUT使用正式Spec为权威输入，Task4不再补写旧V133，Task4/6均统一到当前固定测试环境。两份开发DU显式登记真实当前任务为Owner及待创建的独立短路径，PLANNED不冒充已创建或已认领。
- 独立复核首轮提出执行位置和环境指引冲突两项，修正后复核无Required语义问题；复核过程中发现的临时索引滞后已由原生成器刷新并真实通过check-index。
- 工作树准备：在master先提交两份PLANNED记录d70a9474，再以git worktree创建`codex/fcut001-example-seed-20260908`和`codex/fins001-task9-20260908`，分别位于`E:/AICoding/worktrees/npdms-cfgseed`与`E:/AICoding/worktrees/npdms-ins9`；创建后分支/路径正确且工作树干净。只检出Git跟踪内容，未复制node_modules、环境凭据或未提交文件。
- 激活协调：两代码DU在本治理DU释放边界的同次master提交转为CLAIMED，随后各分支只允许ff-only同步该提交；实际认领值由其SELF对应的Git激活提交解析，后续编码不可在未包含认领的旧快照上开始。
- 保留范围：F-INT-012下一代码Task仍须先收敛唯一计划；F-AST-001运行Done复验仍未完成；其他需求保持其真实Ready/阻断边界。本轮不执行迁移、清库、账号重设、业务实现或Phase 1/2全量审计，不签署Feature Done或发布。
