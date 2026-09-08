# DU-20260908-AGENTS-SKILLS-REFINEMENT 工作区指令按需化

> DU状态：`CLAIMED`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`依据用户指定的两篇原文整理调整当前工作区AGENTS和Skills；不执行业务需求审查或修改实现状态`
> Owner：`Codex当前工作区Agents和Skills调整会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`35fb30b47ca51649dd689cee16db8d8ffa3db8ee`
> 认领提交：`SELF`
> 修改边界：`AGENTS.md;.agents/skills/npdms-change-delivery/SKILL.md;.agents/skills/npdms-implementation-review/SKILL.md;tasks/delivery-units/DU-20260908-AGENTS-SKILLS-REFINEMENT.md;tasks/delivery-units/README.md`
> 串行资源：`仓库AGENTS入口与项目级Skills；DU索引`
> 旧功能范围：`NONE`
> 验证：`Skill结构与本地引用；真实任务场景推演；核心约束及差异自审；DU认领与修改边界`
> 集成记录：`NONE`

## 批准依据与边界

2026-09-08用户明确要求根据关联任务「Astra模型Agents和Skils调整」中的`GPT-6-Astra-两篇原文整理-2026-09-08.md`修改当前工作区的Agents和Skills。来源任务为`01a07bf9-af7b-7f13-84ea-c977da6eb5f5`；原文整理位于该任务的`outputs/`目录。该资料只作为指令调整的参考，不成为业务或工程状态权威。

本次核对[官方Astra行为指导](https://developers.openai.com/api/docs/guides/latest-model#gpt-6-astra-behavior)与[项目级Skill发现规则](https://learn.chatgpt.com/docs/build-skills#where-to-save-skills)。X文章采用用户指定整理稿的归纳，不声称本轮读取了原帖。

- 工程治理变更，无业务Requirement增删；Owner为本会话，直接消费者为在本仓库工作的编码与审查代理。
- 澄清相关章节阅读、授权范围内推进、风险匹配验证和完成点；保留工程链现有权威、DU、Owner、安全、历史、旧实现审计和业务验收约束。
- 仓库原无项目级Skills；新增两个名称独立、触发范围互斥的实施与审查技能，不复制或修改全局Skill，不声称同名Skill会被项目文件覆盖。
- 不修改PRD、SDS、Feature Ready、Implementation Status、数据库、API、CI或业务代码；不改变历史GO/NO-GO/FAIL，不推送远端。

## 执行与验收

1. 按工程链在master提交计划并激活本DU后再写入指令文件。
2. 精简AGENTS中的通用重复指引，并将项目实施和实现现状审查的条件性流程放入各自Skill；不建立第二套计划、审批或状态源。
3. 验证技能结构、引用可达、读写授权和状态边界，核对仅声明路径发生变化；记录真实结果并完成本地聚焦提交。

## 交接与集成回执

- 已完成：来源及当前规则读取、修改范围确认。
- 剩余：认领激活、指令修改、验证和提交。
- 验证：尚未执行；不以文档或Skill校验代表任何业务功能通过。
- 已知失败：无。
