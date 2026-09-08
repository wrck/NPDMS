# DU-20260908-AGENTS-SKILLS-REFINEMENT 工作区指令按需化

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`依据用户指定的两篇原文整理调整当前工作区AGENTS和Skills；不执行业务需求审查或修改实现状态`
> Owner：`Codex当前工作区Agents和Skills调整会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`35fb30b47ca51649dd689cee16db8d8ffa3db8ee`
> 认领提交：`ec848d4cf429c7ce29085ee978155b04af739f84`
> 修改边界：`AGENTS.md;.agents/skills/npdms-change-delivery/SKILL.md;.agents/skills/npdms-implementation-review/SKILL.md;tasks/delivery-units/DU-20260908-AGENTS-SKILLS-REFINEMENT.md;tasks/delivery-units/README.md`
> 串行资源：`仓库AGENTS入口与项目级Skills；DU索引`
> 旧功能范围：`NONE`
> 验证：`Skill结构与本地引用；真实任务场景推演；核心约束及差异自审；DU认领与修改边界`
> 集成记录：`本DU随最终指令修改在master同次提交收口；仅完成工作区AGENTS和两项Skills调整，不产生业务审查或Feature完成结论`

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

- 认领：计划提交`4cb69a3b`，激活提交`ec848d4c`；确认master包含激活提交后才开始修改AGENTS和Skills。
- 已完成：AGENTS按相关章节阅读、契约变化、任务完成点和真实风险区分流程；新增变更交付与实现审查两个短技能入口。Skill名称独立，不触碰全局配置，不额外建立支持文档或脚本。
- 范围验证：本轮差异只涉及五个声明路径；活动认领无写边界冲突。七个项目核心约束章节及异步等待规则与认领前逐字一致；15个本地文件引用可定位；`git diff --check`通过。
- Skill结构：系统Python及Codex现成Python均缺少PyYAML，官方`quick_validate.py`未能执行。使用仓库已有`js-yaml 4.1.1`验证两份入口的YAML、必填字段、名称和说明约束及未完成占位，均通过；这是替代结构检查，不是官方校验器通过。未安装依赖。
- 行为验证：独立子代理对错字更正、已批准接口改动及本轮失败修复、只读Requirement现状审查、带状态回写但缺少认领范围的请求进行四项只读前向推演，未发现额外审批、越权或虚假完成；这是指令推演，不是业务运行验收。最终自审另明确了含状态更新请求的完成点，防止只交审查结论即停止。
- 既有失败：DU全量校验仍有10条旧格式诊断，来自`DU-20260903-FINT012-PARTIAL-CODE-RECEPTION`、`DU-20260903-FINT012-SOURCE-RECOGNITION`和`DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION`。加入本DU前后诊断一致，本DU及活动认领聚焦检查无新增错误；历史文件和校验器均未修改，不宣称全量PASS。
- 未执行：Maven、前端构建、浏览器、数据库、Phase 1/2全量审计及逐需求业务审查；本次仅指令变更，这些检查不能证明本次交付，亦未据此更新任何Feature状态。
- 结论：本轮指令范围自审通过，适用的结构、引用、写边界与行为推演完成；保留上述校验环境限制及旧诊断。最终本地提交后释放本DU写边界，不推送远端。
