# PRD修订016：SDS Gate技术阻塞修复与复核

> 当前技术结论：`TECHNICAL_GO`
> 正式阶段结论：`BLOCKED_BY_REVIEW`；不是APPROVED，也不是Release GO。
> 评审方式：`SELF_REVIEW_ONLY`；ChatGPT修复、内容复核和机器验证，不冒充独立Reviewer。
> PRD：`PRD_V1.8_REVISION_016`；Blob `4b7bd7a4b099e18edb7e5a10c8c27615118c9d7b`
> PRD SHA-256：`df80a00713fdf63466dd0660c8abd898424479e2f309c64efdcd74c3624f1d2c`
> 修复起点：`dfc3459e8bfd974a147692e69ca60f69e28e85ca`；未合并master。
> 设计取舍：`docs/decisions/0044-revision-016-design-and-carrier-alignment.md`（IN_REVIEW）。

## 1. 实际关闭的技术问题

| 原问题 | 当前修复 | 检查落点 |
|---|---|---|
| PM-03/PM-11把模板图降成数字相邻，Stage绑定无明确载体 | 保留原BPM权限、定义身份和六类Owner谓词；新增模板边/冻结边/Stage主绑定、ENTRY上下文及原子推进合同 | 08/09/10、规范对象目录、参考DDL、默认边及当前主绑定约束 |
| PM-06仍生成MultiPhaseProjectGroup和旧API/事件 | 换成同一ACTIVE项目的追加申请，COM唯一范围水位及不可变版本；保留旧范围证据但不覆盖新增范围 | 02/08/09/10/11/15/20；旧群组从活动映射删除，历史解释保留 |
| 三类项目退出与强制S6不一致 | 正常/不予跟踪由CLO业务命令与PROJ同事务产生，异常关闭由PM-10；ProjectExitRecord保存实际阶段 | 07/09/11/16；MySQL验证NO_TRACKING从S4、EXCEPTION从S1及非法Writer拒绝 |
| ACC报告对象混入说明文字，没有规范对象—表映射 | 独立报告根与不可变版本；根按项目/类型唯一、报告绑定精确范围/文件/初验引用 | AcceptanceReportRevision映射、同租户FK、报告版本唯一；失败报告允许保存而不证明通过 |
| RPT-02仅有摘要，没有完整口径 | 全状态/真实阶段/超期/三终态、两套闭环率、根与节点粒度、零分母、快照下钻/导出及撤权 | 08/19/20，保留V2边界；未执行真实报表UI |
| 首次项目经理指派死锁 | 按PM-01既有规则由获权服务经理/工程管理部指派，以真实责任事实完成绑定 | Q-FPROJ-009设计解决，实施重验证保留；无TASK_NATIVE越权例外 |
| Phase 2对象名称和来源Owner解析不正确 | 使用规范对象/表标识；Owner解析支持Requirement@V1/V2并拒绝切片冲突 | 101对象、112来源绑定、1排除源；同输入重建与校验 |
| 校验器固定修订007/必须已批准才能校验内容 | 派生当前PRD身份；技术内容与批准分别验证，默认仍拒绝无当前独立/需求方批准的GO | 3阶段--technical通过；默认命令只剩明确批准缺口，不把失败改成全Gate通过 |

100项Requirement和111个版本切片不变。PRD及其镜像、历史Feature Task、DU、应用代码、已执行Flyway和原核心DDL均未改写。8个Feature/切片的实施重验证标记保留；技术设计通过不推导新的Implementation Done。

## 2. 物理验证的真实证据

机器契约：`docs/traceability/sds-revision-016-physical-contract.json`。
参考DDL：`specs/001-project-delivery-platform/appendices/sds-revision-016-carriers.mysql.sql`。
执行证据：`docs/engineering/gates/phase-2/revision-016-mysql-schema.json`。

执行于GitHub Actions run `34072580437`，输入提交`dcf22747b763988ad426e677901c115ca1a9f22c`。当时的受限传输只在runner工作区展开这三个文件；报告保存实际contract/DDL哈希，与当前提交内容逐字节绑定。MySQL为8.4.11，容器network=none，无宿主端口、无生产连接，验证schema原先不存在；所有记录为人工合成测试数据，执行后容器删除。

| 指标 | 实际结果 |
|---|---|
| 参考表 | 12张创建成功 |
| 数据库约束用例 | 42/42 PASS |
| 物理契约SHA-256 | `774a52b7e6f17e65790bcfd2eb454632d5b68ecd0fe5b156371228cc9e3f29e0` |
| 参考DDL SHA-256 | `4241fc07bcfbb3744eb6924168d3b41de6f3616b2276d9d17ce17ebf78872b99` |
| 原核心DDL | SHA-256 `6b203bf3b4cc860dfaef1221977f2b48a620c0077638d857582ff7bb033e275b`，字节未改 |

首次run `34072426080`因连接探测命中初始化临时MySQL进程而退出，DDL尚未执行，未记为成功。修正为检测最终服务连续可查询后重跑；没有放宽SQL约束或删除失败用例。

42项验证覆盖租户空值与隔离、发布版本/时间、引用一致性、唯一默认边、自环拒绝、正图版本、阶段绑定类型与当前唯一、范围版本单调、重复申请、批准后才能应用、验收报告版本/跨租户引用、三类退出Writer及S4真实关闭。数据库约束不能证明图整体无环、运行时权限、前向升级兼容、业务审批、UI或外部集成已完成；这些仍须对应Feature验证。

## 3. 可重复执行的检查

统一入口：`python scripts/verify_sds_revision_016_repair.py --output /tmp/sds016-checks.json`。它不写Gate批准、不连接数据库、不修改业务文件；执行现有检查并单独记录默认正式Gate的拒绝结果。

| 检查 | 结果 |
|---|---|
| 现有SDS Phase 1/2/3校验器回归 | 123项通过 |
| 实体迁移映射回归 | 36项通过 |
| 本轮物理契约、GO与证据变异测试 | 23项通过 |
| Requirement生成器回归 | 10项通过 |
| 修订016联动回归 | 12项通过 |
| 工作台契约回归 | 8项通过 |
| 总计 | 212项单元/变异测试通过 |
| Phase 1/2/3技术内容 | 全部通过 |
| 领域来源、Owner与对象/表映射 | 101对象/112来源绑定/1排除源通过 |
| PRD正式基线/语义/联动 | 通过；73项基线检查、0语义问题 |
| 13领域生成与Requirement/Phase2投影漂移 | 通过 |
| 参考DDL与唯一机器字段定义 | 完全一致 |
| git diff --check | 通过 |

远端最终同提交验证结果保存为`docs/engineering/gates/phase-1/revision-016-gate-checks.json`；该记录的inputCommit和actionsRunId由实际执行环境产生。报告中的PASS只证明对应命令，不替代独立审查。验证器仍保留已存在的权限、状态、幂等、对象错配、跨域Owner、排除范围和回归检查；新增测试证明同名约束改成恒真、可写current marker、证据过期、删负向用例、复制旧GO和SELF_REVIEW_ONLY均不能通过对应检查。

## 4. 正式Gate还需要什么

当前不再由未定义对象、错误Owner、旧PM-06模型或未执行参考DDL阻塞；Phase 1/2/3技术结果为TECHNICAL_GO。正式状态仍为REVALIDATION_REQUIRED / BLOCKED_BY_REVIEW，需求方批准及独立复审为PENDING。

必须由真实独立Reviewer审阅当前输入绑定的PRD、SDS、物理契约和本轮结果，并按既有治理登记需求方批准，才允许按Phase顺序晋级APPROVED/READY。原independent-review.md保持修订007历史，未改名或换日期冒充新审查。用户“修复Gate达到GO”只作为本轮执行授权，没有伪记为其已签署全部新产物。

P3-E09历史CORE_MIGRATION_SUBSET保持原边界；新12表参考Schema的独立审查、Feature前向迁移/升级测试不得套用旧批准。P3-E01～08和AI-MIG-000按对应最晚安全点执行；本次未宣称Feature Ready/Done、Deployment、SIT、UAT、历史迁移或Release GO。
