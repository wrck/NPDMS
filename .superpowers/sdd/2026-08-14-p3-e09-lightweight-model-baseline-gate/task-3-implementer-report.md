# Task 3 实施报告：统一P3-E09模型基线正式口径

## READ 与影响

- Requirement：P3-E09数据模型基线；AI-MIG-000历史迁移与数据切换边界。
- 初始范围为Task简报列出的Phase 3门禁、08/09 SDS与开放问题；冲突扫描额外发现当前Phase 3自审、运行事实、生产证据、Phase 2迁移对齐、部署/测试设计及关联ADR仍把四角色签署、批准哈希或`BLOCKED_BY_REVIEW`作为模型基线条件，已按最小必要范围同步。
- N/A：PRD、DDL、领域/API、权限、业务状态机、业务流程、历史迁移和数据切换执行均未修改。

## IMPLEMENT

- P3-E09统一为`MODEL_BASELINE_READY`：当前DDL、1,883项逐项裁决、`DEFER=0`、MySQL 8.4隔离执行、独立复审`GO`和Git基线共同证明模型可作为SDS及后续Feature输入。
- 移除现行文档对四角色外部附件、OA/电子签名、独立批准JSON、迁移批准状态机和双确认提交的要求；`approvedDdlSha256`保持显式为空，且不是模型基线条件。
- `AI-MIG-000`、历史数据迁移和数据切换继续`OPEN`；未经真实批次的范围、水位、程序、校验、演练、对账和回退验证不得执行。Q08继续仅是候选索引，仍需Feature查询计划和P3-E06性能验收。
- 修复`p3-e09-confirmation-packet.json`的派生文件哈希漂移：Task2更新逐项寄存器后旧哈希未同步，使用既有生成器重建；没有改变DDL或逐项决策。

## TEST

- PASS：`py -3.13 -m unittest discover -s scripts/tests -p "test_*.py" -v`（181 tests）。
- PASS：`generate_p3e09_confirmation_packet.py --check`、`generate_phase3_evidence_packets.py --check`。
- PASS：核心迁移契约、DDL逐项寄存器、Phase 3证据寄存器、SDS Phase 3 validators与`git diff --check`。
- PASS：冲突扫描仅保留工程链、已批准轻量设计/计划及明确否定旧机制的表述；当前正式规则不再要求旧审批结构。

## SELF-REVIEW

- 模型事实完整：通过；19份正式文档与派生确认包一致，P3-E09只表示模型输入。
- DDL漂移失效：通过；生成器和哈希绑定检查通过，任何DDL/寄存器关键证据变化仍会使基线校验失败。
- 迁移误放行：通过；所有当前门禁明确AI-MIG-000、历史迁移和切换继续`OPEN`，且必须真实批次验证。
- 冗余治理：通过；未新增外部附件、OA、电子签名、独立批准JSON、状态机或双确认提交。
- 未读取、修改或暂存两份受保护未跟踪资料；未修改`progress.md`；未推送。

## 已知限制与后续

- Phase 3整体仍为`NOT_READY_FOR_SDS_BASELINE`，P3-E08及其他下游/审查项未被标记完成。
- 真实迁移批次形成后，按当时事实完成AI-MIG-000；本次基线提交不授予迁移、切换或生产发布权限。

## Round 1 校准：候选待 fresh independent review

- 反馈核对确认初始发布把`DEFER=0`错误直接派生为`MODEL_BASELINE_READY`，且Phase 3注册器只在外层状态为`VERIFIED`时调用完整模型校验。现已改为`MODEL_BASELINE_REVIEW_PENDING`默认候选态；任何声明`MODEL_BASELINE_READY`的状态均无条件执行正式制品哈希、MySQL、`DEFER=0`、不同责任人、正式独立复审引用及精确`GO`校验。
- 新增唯一正式记录`docs/engineering/gates/phase-3/independent-review.md`，当前仅为`IN_REVIEW / PENDING_FRESH_REVIEW`，绑定候选Git基线、DDL哈希、1,883项决策、MySQL事实和Q08候选边界；未写入GO。
- 清除了逐项Reviewer overlay与非空`approvedDdlSha256`的现行要求。逐项寄存器保留1,883项已决策事实，独立复审只核对候选整体一致性；`approvedDdlSha256`显式`null`，仅由未来历史迁移门禁管理。生成器已覆盖该规则，重生不会恢复逐项签署。
- P3-E09当前阻断`DATA_MODEL_BASELINE`；`AI-MIG-000`、历史迁移和数据切换继续`OPEN` / 阻断，Q08仍为候选索引。没有将其他Phase 3门禁标为完成。
- 测试：`py -3.13 -m unittest discover -s scripts/tests -p "test_*.py" -v`通过，184/184。新增“`DEFER=0`但无复审保持pending”“手改Ready且缺复审证据失败”及“重生不恢复逐项Reviewer overlay”覆盖。
- 校验：核心契约、DDL寄存器、Phase 3证据寄存器、SDS Phase 3、Phase 3提交模板、confirmation packet、phase3 packet、同步与catalog检查、`git diff --check`均通过。
- 受保护的两份未跟踪资料未读取、未修改、未暂存；未修改`progress.md`；未推送。下一步由fresh reviewer在精确候选Git上给出`GO`后，再进行Round 2最终发布。

## Round 2 校准：正式复审字段与候选提交绑定

- 核心提交`cfd60d6`使`MODEL_BASELINE_READY`必须完整通过`validate_model_baseline`：正式复审记录固定校验`status`、`conclusion`、候选提交、DDL/逐项哈希、1,883项、`DEFER=0`与MySQL测试结果，并逐项精确匹配模型事实；`IN_REVIEW`、`PENDING`、非`GO`矛盾文本、旧提交/哈希或错误计数均失败。
- 领域迁移对齐校验允许模型基线使用显式`approvedDdlSha256: null`，但永久要求`HISTORICAL_DATA_MIGRATION`和`DATA_CUTOVER`继续被P3-E09阻断；新增对应正反用例。
- 门禁、自审、ADR-0022与两份现行迁移附录已统一为候选待fresh review，不得作为SDS/Feature输入；清除了三类Owner逐项签署和非空批准哈希的旧口径。Q08仍是候选索引，DDL与1,883项决策未修改。
- metadata提交`c175eee`仅把正式复审记录的`candidateCommit`绑定到`cfd60d6`；记录仍是`IN_REVIEW / PENDING_FRESH_REVIEW`，没有写入`GO`或关闭P3-E09。
- 回归：189/189单测通过；核心迁移契约、DDL逐项寄存器、领域实体迁移对齐、Phase 3证据寄存器、SDS Phase 3、catalog生成检查和`git diff --check`均通过。
- 冲突扫描未发现提前发布的P3-E09/SDS/Feature口径；唯一命中为迁移附录中明确否定“三类Owner逐项签署或非空批准哈希”的说明。受保护资料未读取、修改或暂存；未修改`progress.md`；未推送。

## Round 3 校准：正式复审候选不可替换性

- 核心提交`c1b87b19a9b4e9e1e16034f3bdc1de92b701074b`规定`GO`复审记录的8个固定字段均必须且只能出现一次；即使同字段同时出现旧值和正确值也失败。`testResult`和`isolatedMysqlExecution.status`分别独立要求精确`PASS`。
- `candidateCommit`在`READY`路径必须是完整40位十六进制SHA；校验器只读执行Git `cat-file`、`merge-base --is-ancestor`和`show`，验证其为真实commit、当前HEAD可达，并比较候选/当前HEAD的正式DDL blob以及候选逐项寄存器DDL与items哈希。未新增签署附件、批准JSON或迁移审批结构。
- 覆盖重复字段、双方FAIL、短SHA、不存在commit、非祖先commit和存在但DDL/逐项制品漂移的负测；候选`c1b87b19a9b4e9e1e16034f3bdc1de92b701074b`的实际只读制品校验返回空错误。
- metadata提交`bbc546b`仅把当前`IN_REVIEW / PENDING_FRESH_REVIEW`记录的`candidateCommit`绑定为上述完整SHA；未写入`GO`、未放行SDS/Feature，AI-MIG-000、历史迁移与数据切换继续阻断。
- 回归：196/196单测通过；核心迁移契约、DDL寄存器、领域迁移对齐、Phase 3证据寄存器、SDS、catalog/confirmation/phase3 packet生成检查和`git diff --check`均通过。受保护资料未读取、修改或暂存；未修改`progress.md`；未推送。
## Round 4 最终发布

- 正式独立复审记录已按 fresh closure 给定事实回写为`APPROVED / GO`：候选提交`c1b87b19a9b4e9e1e16034f3bdc1de92b701074b`、当前DDL和items SHA、`itemCount=1883`、`deferCount=0`、`testResult=PASS`及指定复审范围均精确绑定，固定字段各只出现一次。
- 生成链由该唯一正式记录派生`MODEL_BASELINE_READY`，仅放行`DATA_MODEL_BASELINE`；`approvedDdlSha256`保持显式`null`，`AI-MIG-000`、`HISTORICAL_DATA_MIGRATION`和`DATA_CUTOVER`继续阻断。Phase 3整体仍为`NOT_READY_FOR_SDS_BASELINE`，Q08仍为候选索引。
- 已同步Phase 3 gate/status/self-review/runtime、SDS 08/09/20、相关ADR及迁移证据说明；未修改PRD、DDL、领域/API/权限/业务状态机或业务流程，未修改`progress.md`。
- 验证：全量`py -3.13 -m unittest discover -s scripts/tests -p "test_*.py"`通过196/196；核心契约、DDL寄存器、84领域迁移、Phase 3、SDS validators均PASS；confirmation/catalog/Phase3/sync生成检查和`git diff --check`均PASS。曾发现确认包在寄存器重生前生成导致items SHA漂移，已按正确生成顺序重生并复检PASS。
- 冲突扫描未发现现行正式文档残留的`MODEL_BASELINE_REVIEW_PENDING`或“待独立整体一致性复审”模型输入口径；受保护未跟踪资料未读取、修改或暂存；未推送。

## Round 4 复审加固新候选

- 核心提交`37218eec2fcf82224a90e0b59f9e187bed71849d`将P3-E09回落为`MODEL_BASELINE_REVIEW_PENDING`：上一轮`GO`不覆盖本轮复审校验加固，当前不得作为SDS/Feature数据模型输入；`AI-MIG-000`、历史数据迁移和数据切换继续阻断，Q08仍为候选索引。
- 复审策略新增且唯一校验`reviewDate`、`reviewRange`；READY路径要求范围为可解析的完整40位Git提交、基线可达候选、候选可达当前HEAD，并从候选寄存器的`items`重新计算canonical SHA，与候选声明和当前事实三重一致。
- 四处现行正式资产和冲突扫描测试均按状态同步：PENDING必须明确“当前新候选待fresh review”且不得宣称发布；未来READY必须明确“正式独立复审已GO、模型基线已发布”且不得保留待复审提示。
- metadata提交`1cf3ed118a400df0af5fdf00dff7ca92bb6a5ffa`仅将唯一正式复审记录绑定到核心候选及`a37c70aa0251419cd69f8a6969cbabb23d7ed834..37218eec2fcf82224a90e0b59f9e187bed71849d`范围，不自引用metadata提交，记录仍是`IN_REVIEW / PENDING_FRESH_REVIEW`，未写GO。
- 验证：全量`py -3.13 -m unittest discover -s scripts/tests -p "test_*.py"`为200/200；核心契约、DDL寄存器、领域迁移对齐、Phase 3、SDS validators均PASS；catalog/confirmation/phase3/sync生成检查与`git diff --check`均PASS。候选与范围Git可达性已用`rev-parse`和`merge-base --is-ancestor`验证。
- 受保护未跟踪资料未读取、修改或暂存；未修改`progress.md`，未推送。

## approvedDdlSha256 单点语义修复

- 范围：仅移除 P3-E09 当前模型基线契约、生成/同步链、校验器、模板和正式当前文档中的 `approvedDdlSha256`。P3-E09 不定义迁移批准哈希；`AI-MIG-000`、历史数据迁移和数据切换继续阻断。
- 防回归：模型基线证据出现该 legacy 字段（包括 `null`、空值或哈希）即报 `legacy migration approval field is not allowed in P3-E09 model baseline`；字段不存在合法。
- 验证：全量单测 `197/197` PASS；核心契约、DDL 决策、领域迁移、Phase 3、SDS 校验器及 catalog/confirmation/phase3/sync 生成检查全部 PASS；`git diff --check` PASS。
- 不变性：UTF-8 结构化对比确认 DDL SHA、items SHA、1,883 项决策和 Q08 裁决均未变化。
- 残留分类：当前正式文档与当前生成制品为零；脚本仅保留禁止该 legacy 字段的校验和负测；历史/过程计划材料未修改且不参与当前机器契约。
- 提交：`593e06261994dca92b328c2ed0b7d32b90621f4a`；受保护未跟踪资料未读取、修改或暂存，未推送。
