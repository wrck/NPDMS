# DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION 项目交付主线准备

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`开发前工作树/认领/工具链/运行环境检查；补齐DU新文件与真实认领校验；以项目交付S0～S6主线分析任务与依赖；不启动业务开发`
> Owner：`任务01a07efb-aaea-7440-8c34-c093d3b841b3`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`cc4ff449c1b580bd1ede7216b1696201b5a9a415`
> 认领提交：`cd29b3adf9ecf2699edc8ed9495ed74eabb02ead`
> 修改边界：`scripts/validate_delivery_units.py;scripts/tests/test_validate_delivery_units.py;docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md;tasks/delivery-units/DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION.md;tasks/delivery-units/DU-20260908-FCUT001-EXAMPLE-SEED.md;tasks/delivery-units/README.md;tasks/features/F-PROJ-001.md;tasks/features/F-PROJ-008.md;tasks/features/README.md;tasks/implementation-baseline-status.md`
> 串行资源：`master治理与派发状态；只读检查固定测试环境，不变更数据库/账号/容器持久卷`
> 旧功能范围：`NONE`
> 验证：`DU已跟踪/已暂存/未跟踪路径与真实认领拒绝；原治理回归；主线模块编译；PRD/Spec/Task/Q核对；Flyway只读validate与独立复核`
> 集成记录：`本次提交交付护栏、状态检查与主线分析；运行验收环境的数据处置/恢复尚需明确授权，故仅INTEGRATED_PARTIAL，不宣布全部准备就绪或业务Done`

## 用户范围与写入边界

2026-09-08用户明确“现阶段先做好开发前的所有准备和状态检查，然后分析项目交付主线的任务，这是整个系统最主要的交付直线”。当前优先级据此切换为项目交付主线，不从先前已准备的CUT/INS候选反向选择业务主线。

本DU激活时，原两份CUT/INS开发DU由本任务持有且工作树干净、未开工，故先释放排他认领并保留分支/工作树。后继80f2b58a已将INS Task 9交给TRAE独立Owner并移出本DU边界；该交接保留，本任务不再修改巡检认领/代码。CUT仍保持释放。任何后续开工按其最新DU，不删除或合并历史候选。

工程治理变化只实现既有“全部写入位于认领边界、认领已提交”规则：把未跟踪新文件纳入路径检查，并拒绝未解析到Git提交的活动SELF认领；保留master协调入口及既有业务/历史保护，不新增审批、状态源、框架或Phase全量前置。

业务Requirement/Owner/API/数据库语义不变。主线分析引用PRD第13.1章及当前SDS、Feature和Task；不新造Feature编号、不回写Ready/Done、不修CLO、联系人或其他业务代码。文档只提出后续准备/设计与开发顺序，不替代正式Feature Technical Plan。

## 执行顺序

1. 登记并激活本DU，同时释放本任务持有的两份未开工认领。
2. 核对工具链、工作树、验证入口和固定环境；仅执行只读环境诊断，不执行migrate、repair、reset、清库或账号重设。
3. 重现并修正DU校验盲点，完成聚焦负向/正向回归和现有治理检查。
4. 分析完整S0～S6参考主线及八条关键子流程，区分V1主闭环、V2增强、已有基础、缺失Feature和真实合同缺口，整理可并行准备包。
5. 在现状入口记录当前方向、准确阻断和下一步；独立复核后本地提交，不推送。

## 当前状态

2026-09-08用户已明确将巡检Task 9交接给TRAE当前巡检会话；本准备DU移交该巡检DU记录的写边界，不再持有或修改它。其他主线准备范围、Owner和状态不变。


本轮可安全执行的准备检查、护栏修复、状态校准和[主线任务分析](../../docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md)已完成。固定运行验收环境仍未就绪，因此不能把本轮完成表述为“全部开发准备已完成”。

## 实测与交接

- 本地计划be4eda2a、激活cd29b3ad先于工具代码写入；并行80f2b58a为TRAE交接、b812a73a为其Task 9测试装配边界扩展，均不属于本任务成果，已保留其GOV边界移交、INS Owner及最新DU/唯一计划。最终提交只接收本任务的未提交文件，不动其业务工作树或代码。
- 原护栏缺口已由两个真实Git场景重现：无认领分支只有未跟踪新文件时CLI错误返回0；SELF未提交激活时错误通过。修复后10项聚焦测试通过，并额外覆盖中文路径、暂存/未暂存、忽略项、改名两端、释放后重新激活与分支继承。master协调例外未扩大，不把脚本当操作系统隔离或允许分支自行扩大DU。
- 原治理回归：变更级审查8项、同仓权威3项通过；与10项护栏测试合计21项，本轮未把重复审阅重跑重复计数。当前DU与Requirement追溯检查通过，未改变Feature实施状态及100需求/111切片分布。
- 收口检查：9个待提交路径均在本DU边界内；新增本地文档引用均可定位，DU索引及需求追溯投影检查通过，`git diff --check`通过。未将并行任务的已提交文档、业务代码、SQL、配置或凭据纳入本次差异。
- 编译：`mvn.cmd -o -B -pl pms-module-project,pms-module-engineering,pms-module-commerce -am "-DskipTests" test-compile`，30模块BUILD SUCCESS，51.156秒；`pnpm.cmd run ts:check`退出0。仅证明源码/测试源码及前端类型检查，不表示运行测试、应用或UI验收通过。
- 环境：工具链满足本轮编译入口；固定MySQL/Redis健康，标准应用端口未观察到监听，未启动/停止应用。工作区测试连接配置与运行容器用户名/密码均不一致；只在诊断进程临时注入运行凭据后连接成功，未打印秘密或修改.env。
- Flyway：仅执行validate，真实失败为已应用V133～V150共18个版本当前主干未解析、主干V160～V204共45个版本未应用。临时容器由--rm移除；未执行migrate、repair、reset、忽略模式、清库或账号重设。后续须确认固定测试数据保全/重建和统一配置来源，不能直接覆盖迁移历史。
- 主线：以PRD第13.1章S0～S6参考全集和8个关键子流程为准，保留V1/V2义务；最早准备包为PM-01首次项目经理指派的Spec/计划重验证，与独立验收合同包划清依赖。Q-FPROJ-009已解决设计，不重复请求业务裁决；Q-TPLACC-001不扩大阻断独立包A。未新造Feature编号、审批角色或业务语义。
- 独立复核：新上下文只读审阅者对当前脚本/测试/主线文档及状态变更给出APPROVE，无Critical/Required；其亲自执行10项护栏测试及普通/带base-ref DU校验均通过，其他编译/环境结果明确为引用本任务证据。
- 未完成边界：固定测试环境恢复与后续主线正式契约/计划落位、业务代码、MySQL业务/Schema及真实浏览器验收仍未完成。当前只释放本GOV写入边界以交付准备成果，不签署业务Ready/Done或UAT/Release；继续写入须按实际下一范围认领。
