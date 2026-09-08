# 项目实施约束

## 事实来源

- 本仓库是业务、设计、实现、测试与验收证据的唯一事实源，不再维护外部规格仓快照或第二套Feature状态源。
- 权威优先级固定为：`PRD > Engineering Constitution > SDS > Feature Spec > Technical Plan > Task > Code > Test / Runtime Evidence`。下游可以细化上游，但不得静默改变业务语义。
- 修改设计或代码前，按上述权威顺序读取 `docs/baseline/prd-v1.8.md`、`docs/engineering/00-engineering-chain.md`、`docs/README.md`、相关SDS、Feature Spec和当前Task中的任务相关章节。先用Requirement ID、路径和索引定位，不要求通读无关分册；本轮已读且未变化的内容不重复加载。纯文字或排版更正只读目标文件及适用文档规则，不借此跳过实际业务契约。
- 规格与实现变更在同一分支内按上游到下游推进：契约变化时先修订正式规格，再修改代码、迁移和测试，最后按影响更新追溯投影与证据引用；契约未变时不制造规格修订。禁止以代码、测试、浏览器证据或索引投影反向覆盖正式规格。
- `tasks/plan.md` 和 `tasks/todo.md` 已标记为历史材料，不再生成或驱动新开发任务。
- 本项目禁止使用项目记忆补全需求、设计或验收结论；不确定事项必须回到仓库文档或标记为【待确认】。

## 变更级实质审查（2026-09-08需求方批准）

- 取消SDS Phase 1/2对日常变更的整阶段批准、全库检查和顺序准入阻断；Phase名称仅作设计分类，旧阶段记录仅作历史审阅证据，不要求每次修改同步各分册状态/标题/批准字样。
- 每次只在现有Task、DU或提交说明中记录一次受影响Requirement（工程治理变更记录批准依据）、Owner、接口/表/直接消费者、实际验证和审阅结论。不新增Phase状态文件；一项事实只在其权威位置修改，其他文档引用，投影只在来源变化影响它时生成。
- 日常设计/实现准入依据本次范围内的实际契约是否明确及风险是否经过审查。跨Owner、权限、状态机、API、幂等、并发、文件和数据库变化必须覆盖相关消费者及对应拒绝/回滚/历史保护检查；不运行与本次变化无关的全阶段审计作为前置。
- `validate_sds_phase1.py`和`validate_sds_phase2.py`为显式选择的全量审计工具，不是Feature/Task准入；默认不执行审计且不得输出虚假PASS。全量审计发现仅按实际影响路由，不凭整份PRD身份变化或历史Phase未批准扩大阻断。
- 旧分册、计划和Question中的纯Phase 1/2流程前置以本节及工程链当前规则为准；真实需求冲突、未定义API/Schema或未完成的适用验证仍阻断依赖工作。不得据此自动清除Question、Feature Ready/Done或发布/迁移限制，不改变历史GO/NO-GO/FAIL。

## 规格与实现硬规则

- 未经批准的变更请求，不得修改PRD业务语义。
- V1/V2实现不得夹带V3或`OUT_OF_SCOPE`事项。
- 不得臆造业务角色、审批节点、状态转换、阈值、Gate或数据Owner。
- 不得直接写生命周期状态绕过状态机，也不得绕过服务端授权与数据范围。
- 不得暴露或持久化明文设备密码、私钥、Token或其他Secret。
- 不得覆盖不可变历史、快照、批准版本、审计记录或来源证据。
- 通知送达和外部HTTP成功不等于业务完成，除非正式契约明确如此定义。
- 不得为了使测试通过而降低校验、授权、状态机或业务规则。
- 已明确批准的工程流程迁移可以替换对应的治理断言；实际业务、安全、数据完整性及失败测试必须保留，不能用“门禁已取消”隐藏真实缺陷。
- 评审草稿、门禁证据、计划、外部输入、生成报告和临时副本必须按`docs/README.md`分类；不得在正式目录创建并行的`*-draft.md`、`*-review.md`或`*-final2.md`。

## 缺失、歧义与追溯

- 业务规则缺失、歧义或冲突时不得猜测：标记`BLOCKED_BY_SPEC`，登记到`docs/decisions/open-questions.md`，只继续不依赖该问题的独立工作。
- 每个Feature、API、数据库变更、事件、工作流和测试必须引用一个或多个Requirement ID，并维护`Requirement -> SDS -> Feature -> Code -> Test`链路。
- Feature Ready只由Feature Spec维护；Implementation Status只由当前Feature任务记录维护；并行认领、写边界、Worktree交接和master集成回执只由`tasks/delivery-units/DU-*.md`维护。一个Delivery Unit可以覆盖多个Feature或Task，但Feature仍是唯一Done单元；索引、追溯矩阵、Git、CI和浏览器结果只作投影或证据。
- 任何实现写入前必须先在master提交Delivery Unit认领，并让目标分支包含该认领提交；分支名、Worktree目录、分支内Task状态和继承提交均不构成认领。废弃路径不得承接新Feature，只能由声明`旧功能范围`的DU执行废弃补强、安全修复、历史只读、迁移解释或删除。
- 外部集成必须定义系统Owner、方向、权威字段、映射、来源键、幂等键、超时、重试、补偿、对账、降级和审计。

## 任务执行协议

- 实施任务遵循`READ -> PLAN -> IMPLEMENT -> TEST -> SELF-REVIEW -> REPORT`，这些是工作职责，不要求每步新建文档或等待确认；只读任务不进入IMPLEMENT。
- 开始时一次说明交付物、完成条件、修改文件、Requirement ID或治理批准依据、实际领域/API/数据库/权限/状态机影响、验证和风险。小改动在对话中给出短计划；多步工作复用当前Task/DU，不另建重复计划或审批表。
- 结束时先报告完成范围和结果，再给变更文件、需求覆盖、实际验证、未执行项及原因、剩余限制。自审与独立审查、代码存在与功能验收明确区分，不重复粘贴全过程。

## 项目级 Skills

项目技能位于`.agents/skills/`，只在任务匹配时读取对应入口；不为每次编辑同时加载全部流程。

- [npdms-change-delivery](.agents/skills/npdms-change-delivery/SKILL.md)：需要衔接规格、DU、实现与验证的Feature/Task或工程治理变更。
- [npdms-implementation-review](.agents/skills/npdms-implementation-review/SKILL.md)：按Requirement/Feature核对工程链与实现现状；仅在用户要求时回写状态。

Skill提供执行方法，不产生新的业务语义、审批权、永久状态或Git授权。通用Skill示例与项目已批准规则不一致时，遵循更高优先级指令及本项目正式规则，并指出实际冲突；不能把Skill自身的额外流程当成项目Gate。项目技能名称独立，不假定会覆盖或合并全局同名Skill；不修改全局Skills、插件或配置来完成仓库内任务。

## 技术基线

- 后端基线：`yudao-boot-mini` `master-jdk25`，锁定提交见 `docs/upstream-sources.md`。
- mini 缺失的 Yudao 模块：仅从 `YunaiV/ruoyi-vue-pro` 的锁定提交按需导入。
- 共享文件以 mini 为准，不得用完整仓库整体覆盖。
- 首期为模块化单体，由 `yudao-server` 统一装配；PMS 业务模块统一使用 `pms-module-*`。
- Yudao 平台接口保持上游定义；新增 PMS Business API 遵循 `/api/v1/pms/...` 规范。
- 模块级功能设计和实施前必须先完整审计仓库内对应旧实现，逐项判断可直接复用、需复制增强和不可复用的边界；需要增强时先复制到新的类、组件或页面后再改造，旧类、旧页面、旧接口、旧数据和原有功能保持不变，除非锁定规格另行批准修改。
- WorkBinding、自动匹配和其他动态适配属于基础能力之上的接入层；必须先从模块整体需求完成可独立使用的基础功能闭环，再接入适配层，不得按单一消费者倒置实施顺序或把完整模块拆成无法组装的零散能力。

## 运行与验证

- Docker Compose 是 MySQL、Redis 和 Flyway 本地基础设施的权威运行入口，不承载前端和后端应用。
- 后端在宿主机使用 JDK 25/Maven 构建和运行；前端在宿主机使用 Node.js/Corepack/pnpm 构建和运行。
- 宿主机应用与 Docker 基础设施的启动、端口和验证方式以 `docs/development.md` 和 `compose.yaml` 为准。
- 配置模板不得包含真实凭据；本地默认值只能用于隔离开发环境。
- 每项复杂的核心自研任务遵循复用优先、最小实现、失败测试、重构与完整验证顺序，简单任务遵循最小实现、快速验证。
- 编译、静态页面或 API 单测不能替代业务验收；UI 闭环必须由真实浏览器完成。
- 功能模块完成后必须补充初始化数据：按SDS落字典、菜单、配置等有明确定义的种子；无明确定义内容的以示例数据迁移补充（前向版本、幂等、creator标识、高段ID或专用前缀），且必须覆盖关键维度的组合情况（含精确命中、部分限定、优先级让位、无匹配与停用不参与等场景）以及对象全环节（如模板须覆盖S0~S6全阶段与阶段、任务、里程碑、交付件、门禁及门禁引用各要素类型）；受权威来源映射约束的值域（如CRM属性映射）不得臆造取值。

## 领域边界

- 项目组合与项目父子树是两个独立模型。
- 项目树和任务 WBS 都不得实现为固定层级。
- 模块间不得依赖目标模块的 `-biz`、Service、Mapper、Repository 或直接访问其业务表。
- 无稳定跨模块调用方时，不创建空的 `-api` 模块。

## 数据库查询编码

- 新增或改造数据库查询前，必须读取并遵守 `docs/coding/database-query-interface.md`。
- 除主键和稳定复合唯一键查询外，Mapper 查询方法必须只接收一个场景化 Query 对象；禁止长位置参数列表、`Map` 和万能查询对象。
- 简单单表条件使用类型安全的 `LambdaQueryWrapperX`；联表、窗口函数、动态集合、锁查询等文本 SQL 必须进入 Mapper XML。
- 禁止 SQL 注解、`${}`、`.last(...)` 及在 Service/Controller 中拼接 SQL。
- 空权限集合或空集合筛选必须返回空结果，不能因省略条件扩大查询范围；租户、权限和模块表所有权边界必须保持生效。

# AI编码行为

## 推进与停止

- 已授权且契约明确的变更，采用仓库惯例和最小可行方案持续推进到约定完成点；不因“第一版完成”或通用Skill的重复批准步骤自行停下。用户指定的评审节点仍须停下。
- 不影响业务语义、Owner、安全或外部副作用的局部实现选择，可说明假设后继续；会改变结果的业务歧义、缺失契约、写入冲突或高影响操作授权必须先确认。提问前完成不依赖该答案的已授权工作，报告具体冲突条款、受影响范围和最小待决事项。
- 只读审查、诊断和方案请求不授权修改实现或回写状态；“继续”“完成”不扩大原范围。提交遵循用户授权及工程链已有规定，提交前读取并遵守`git-commit`技能，不自动推送。
- 使用子代理须有用户或适用指令授权，且子任务能独立验收；明确输入、输出和互不重叠的写边界。主代理继续有用的本地工作并负责整合，不把主问题整包转交或重复审查已确认事实。

## 最小实现与有效验证

- 每处修改直接服务于本次请求；保留用户和并行改动，不顺手重构、改格式或删除既有死代码。只清理本次修改造成的无用引用和代码。
- 复用现有能力，不为未发生的场景增加抽象、配置、兼容层或防御逻辑；报告真实缺陷，包括受支持使用中会发生的少见情况。正确的地方直说正确，不制造发现。
- 每项检查先回答：会发现什么具体故障，结果会改变哪个决定？按改动和风险选择验证，不能证明行为、只复述实现的测试不增加。结果仍适用时不重跑；仅因新改动、失败、未决问题或工程链明确要求扩大或重复验证。
- 已授权的本地验证中，本次改动导致的失败应定位原因、在范围内修复并重跑受影响检查；隔离条件按实际环境确认，不默认测试数据可丢弃或任意服务可停用。既有无关失败如实记录，不掩盖也不借机扩大修复范围。
- “验证适度”不取消本项目规定的失败测试、授权/状态/历史保护、真实浏览器验收及适用迁移检查；没有执行的验证不能写成通过。

## 沟通

默认中文，结论在前、证据就近，说明必要的假设和取舍；按任务大小控制篇幅。只报告有意义的进展、发现和决策，不堆固定表格、重复清单或“仍在运行”的空更新。

For long-running asynchronous work:

- Empty `write_stdin` polls MUST use `yield_time_ms >= 180000`;
  prefer `300000` when intermediate output is not needed.
- `functions.wait` MUST use `yield_time_ms >= 180000`.
- `functions.exec` MUST set its outer `@exec yield_time_ms` at least
  30000 ms longer than the longest nested tool wait, so the outer
  code cell does not yield first.
- Do not apply the long wait to non-empty `write_stdin` calls that
  send interactive input.
- These tools return early when the process or cell completes.

Do not wake the model merely to report that work is still running.
