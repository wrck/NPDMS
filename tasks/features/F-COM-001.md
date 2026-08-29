# F-COM-001 合同订单关联与交付范围分配

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`  
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`  
> Feature Ready Gate：`PASS`  
> Technical Plan Gate：`PASS / GO`（`c33b0836f71e0875008a084ff360e7027d276ec9`）  
> Implementation Done Gate：`NOT_REVIEWED`  
> 当前阻断：`无`  
> 当前任务：`Task 1 / Step 6：接通项目阶段进入和验收阶段内新版本绑定`
> Requirement ID：`COM-01@V1`；协作`PM-03`、`PM-10`、`ACC-03`  
> Feature Spec：`specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md`  
> Technical Plan：`docs/superpowers/plans/2026-08-29-f-com-001-contract-order-and-delivery-scope.md`  
> 主要执行会话：`01a04bfb-568d-7d42-a38f-7b45e1767ebb`  
> 分支/工作树：`codex/f-com-001-feature-ready` / `M:\AICoding\CodexData\worktrees\fcom\NPDMS`

## 实施边界

- 实现COM合同/销售订单/订单行权威副本、合同管理员公司范围、项目—合同关系、DeliveryScope完整命令与历史、ERP冲突冻结通知、项目进入验收阶段及阶段内新范围版本ACC绑定。
- COM、PROJ、ACC、AST、SYSTEM只通过已批准公开契约协作；不直接访问其他Context业务表，不把验收报告、AST站点或技术默认值当作Owner事实。
- 只实现最小权限键和服务端控制点；实施与验收身份通过正式授权配置取得闭环所需权限，不固定角色映射，不删除鉴权或租户隔离。
- 不修改PRD/SDS、V70/V72、Yudao CRM或SYSTEM Provider；不实现第三方ERP/CRM连接器、COM-02、历史生产迁移或Q-FCOM-002退出/回退关闭规则。
- V124必须先在八张固定影子表装载和对账，再用一条多表`RENAME TABLE`同时归档V70的`com_order_line`、`com_delivery_scope`、`com_delivery_scope_detail`并发布目标表；V125只在V124完整成功后执行。

## 修改与验证范围

- 后端：`pms-module-commerce-api`、`pms-module-commerce`、`pms-module-project-api`、`pms-module-project`及其聚焦测试。
- 数据：新增V124/V125；V124包含影子装载、切换前对账、原子换名、失败恢复和幂等重放，V125包含权限、菜单与受控验收种子。
- 前端：`yudao-ui/yudao-ui-admin-vue3`下PMS Commerce API、合同/订单/范围页面与测试。
- 验证：聚焦单测、真实MySQL事务与迁移矩阵、前端类型/构建、全仓适用回归及真实Chromium公开UI/REST闭环。

## Task 1：一次完成正向业务闭环

- [x] Step 1：以最终接口签名补齐聚焦失败测试并确认RED。
- [x] Step 2：实现公开Owner契约和PROJ/ACC真实Provider。
- [x] Step 3：实现V124目标模型、影子转换、切换前对账和单条多表原子换名。
- [x] Step 4：实现COM权威副本与合同管理员公司范围。
- [x] Step 5：实现DeliveryScope命令、历史、AST校验和冲突通知。
- [ ] Step 6：接通项目阶段进入和验收阶段内新版本绑定。
- [ ] Step 7：完成REST、前端和V125正式配置。
- [ ] Step 8：复跑聚焦集合至GREEN并完成必要重构。

## Task 2：整体验证与Implementation Done送审

- [ ] Step 1：完成真实MySQL与后端事务矩阵。
- [ ] Step 2：完成前端测试、类型检查和生产构建。
- [ ] Step 3：完成全仓、Flyway、规格及V124失败恢复验证。
- [ ] Step 4：完成真实Chromium公开UI/REST闭环。
- [ ] Step 5：整理证据、自审、提交并申请Implementation Done独立评审。

Task详细步骤、精确文件、命令和验收条件以唯一Technical Plan为准。Task局部完成不得宣称Feature或Requirement完成；全部实现和验证完成后只申请一次Feature Implementation Done裁决。

> 检查点：基线=b6c0176c；当前Gate=Implementation/Step6；已通过=范围兼容及直接分配/调整/释放、历史追加、AST写前重验、ERP冲突冻结通知，后端聚焦42项及机器契约16项PASS；阻塞=无；下一步=接通项目阶段进入及验收阶段内新范围版本的ACC原子绑定。
