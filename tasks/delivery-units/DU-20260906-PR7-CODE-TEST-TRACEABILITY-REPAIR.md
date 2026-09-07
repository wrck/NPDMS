# DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR PR #7代码、测试与追溯修复

> DU状态：`IN_PROGRESS`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-IMP-001=TASK_COORDINATED;F-IMP-002=TASK_COORDINATED;F-COM-001=TASK_COORDINATED;F-ACC-001=TASK_COORDINATED;F-ACC-002=TASK_COORDINATED;F-INT-012=TASK_COORDINATED;F-CUT-001=TASK_COORDINATED;F-CUT-002=TASK_COORDINATED;F-CUT-003=TASK_COORDINATED;F-CUT-004=TASK_COORDINATED;F-CUT-005=TASK_COORDINATED;F-CUT-006=TASK_COORDINATED;F-CUT-007=TASK_COORDINATED;F-CUT-008=TASK_COORDINATED;F-CUT-010=TASK_COORDINATED`
> Task范围：`PR #7代码、测试、任务追溯修复；COM ERP来源字段；CUT独立流程；治理套件；用户确认的单租户1默认及前向迁移`
> Owner：`wrck授权的本次PR #7审查修复会话`
> 分支：`codex/code-fact-chronological-integration-acc-int-cut-20260904`
> Worktree：`GitHub Actions隔离检出与本次会话审查工作区`
> 认领基线：`220486237b9570ab3d2b0663df39c89be2a5ec69`
> 认领提交：`SELF`
> 修改边界：`.github/**;pms-module-engineering/**;pms-module-engineering-api/**;pms-module-commerce/**;pms-module-cutover/**;pms-module-platform/**;pms-module-project/**;scripts/**;tasks/features/**;specs/features/**;specs/001-project-delivery-platform/domains/**;specs/001-project-delivery-platform/evidence/migration/**;docs/traceability/**;docs/design/**;docs/baseline/**;docs/development.md;docs/engineering/gates/**;docs/decisions/**;tasks/delivery-units/**;yudao-ui/yudao-ui-admin-vue3/**;yudao-server/src/**;yudao-server/pom.xml;yudao-framework/yudao-spring-boot-starter-biz-tenant/src/**;sql/migrations/**;需求/**`
> 串行资源：`PR #7分支写入;Feature任务和Requirement生成投影;CI配置;单租户默认与前向迁移`
> 旧功能范围：`NONE`
> 验证：`Java 25构建与业务回归；前端构建/类型/单测；COM及CUT MySQL真实集成；默认租户及迁移验证；按实际修改范围复审；文档机械检查不阻断代码合并`
> 集成记录：`PR #7/8已合入master（05305352/1d73b4f0）；本DU继续承接用户确认的COM-01真实正向链修复，不追认来源分支历史授权`

## 边界

master只登记治理认领，不接收PR业务代码；修复候选留在PR #7。不得改变未获用户授权的PRD/SDS语义、Owner或原始572条重放证据，不得跳过失败测试伪造成功，不将IN_PROGRESS Feature升级为Done，不激活F-IMP-002 Task 12待裁决的Controller/Job，不合并PR或自动批准Review。

初始审计起点：PR head `82c207824b9cc8a67668191c40002decc1070f1b`。验证与交接记录据实补充；没有执行的测试保持未验证。

## 2026-09-07 replay全面复核与补合

用户要求创建新PR并合并。以master `05305352`、replay `86929dc9`和共同祖先`22048623`复核GitHub三点比较的146个文件：25个与master相同；24个COM文件属于旧方案、重复片段或已修复夹具；9个IMP文件属于重复方法/Mapper/测试或回退校验；16个INFRA文件属于已排除的第二文件Owner；21个SQL已有后继迁移或属于旧方案；51个为重放工作流、脚本、历史回执与投影。保留master，不重新接收上述重复或被替代内容。

另在分支端点比较中确认PM-01创建接口回归：Feature 8.3允许可选服务经理人工确认，应用服务和既有调用方仍使用`serviceManagerUserId`，Controller却传null。本次仅恢复该字段和转发，复用既有指派权限、人员范围校验与事务，不修改Schema/状态机/页面。请求传66的回归测试在修复前实际收到null；同时保留不传人员的兼容路径。无SQL改动的后续PR不再被PR #7特定迁移清单误拦截，真实迁移验证保留。

## 2026-09-07 第一轮修复范围（历史）

`757ec7c8eb4df9b18658191176ae5e029f0fbcc5`起补EXE-06公开事实校验，未改变当时待裁决的租户语义；COM保留幂等和业务表均不写入的强断言，CUT重复桩去重。后续修复已进入`598d79659c30b69d049f9a4531b4bd5da925b866`，不追认Feature Done。

## 2026-09-07 当前用户裁决

1. 销售订单、订单行是ERP同步事实；只使用已登记旧表及字段映射，补齐COM数据库/DO/API不一致，不创建另一套ERP Owner。
2. CUT是独立业务模块；不得引入商务合同补录或外部可用性作为通用流程门槛。审批字段按CUT本域事实修复；保留权限、方案版本及真正业务前置，不伪造外部事实。
3. 按最新用户要求移除与实现无关的机械校验、重复测试和门禁，保留业务行为、权限、事务、幂等及真实迁移检查；不修改历史批准记录制造通过。
4. 默认单租户ID明确为1；此前租户0仅作历史来源。新增运行默认、种子与迁移目标使用1。既有迁移文件和审计历史不批量改写，来源租户与目标租户区分保留；碰撞或无法证明来源归属时拒绝迁移。

本次裁决是上述有限规格修订的用户授权。先回写正式规格，再落代码与验证，不代表对历史数据生产切换、Flyway repair或所有Feature完成的授权。

## 2026-09-07 本地修复与方案调整检查点

工作区为`M:/AICoding/CodexData/worktrees/6644/NPDMS`，已包含master认领边界提交`43482230`。COM已修复ERP现有字段传递、来源/启停状态分离、范围明细列映射及测试夹具；CUT已修闭环事件时间戳和旧行转换器缺少Spring注册。隔离Compose `npdms-pr7-6644`（MySQL端口33444）迁移至V204及重复迁移通过；COM导入MySQL测试5项、前端212项测试、类型检查和生产构建通过。业务修复仍为工作树候选，未推送或合并PR。

需求方最终说明取代专用单租户装配及服务端固定租户限制：保留原有多租户源码与机制，默认业务租户为1，前端默认选择列表首项，仅有一个租户时隐藏选择/切换；多个租户仍使用原选择机制。已撤下专用过滤器、任务切面及相关装配测试。CUT不存在独立接入合同需求，不新增合同或门禁。移出代码CI的全量Python治理、历史重放及投影校验；删除重复旧迁移哈希测试，保留SQL实质约束检查但不限定换行排版。原90文件补丁不得直接整包执行，Feature状态不晋级。
