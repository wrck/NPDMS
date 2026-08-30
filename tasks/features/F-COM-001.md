# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`READY / GO`
> Feature实施状态：`IN_PROGRESS`
> 当前阶段：`IMPLEMENTATION`
> Technical Plan Gate：`PASS / GO`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

Task 4“人工候选、关系核对与公司范围”在`f76525ef`通过独立Code Review与聚焦测试Gate，状态`PASS / GO`。Task 5候选`18237796`独立复审为`NO-GO / REVIEW_REQUIRED`；合同候选`319a616e`已关闭直管授权、DTO不变量及审计结构方向，但错误优先级、受保护减配请求量与验收保护原因码仍被裁决`NO-GO`。当前最近Gate为这三项机器语义单点整改复审；GO前不修改运行Provider，不回写Task 5 PASS。Task 8暂不并行写共享资产，下游依赖仍未解除。

## Gate输入

- `specs/features/F-COM-001-contract-order-and-delivery-scope.md`
- `specs/features/F-COM-001-physical-contract.json`
- `specs/features/F-COM-001-legacy-reuse-audit.md`
- PRD `COM-01`、SDS Commerce分册、正式迁移契约及当前`pms-module-commerce`实现

## 状态约束

- 只允许按已通过的唯一Technical Plan执行；Task 1公共API与错误机器合同先行，公共API、Flyway、权限/菜单种子与共享错误码串行合入；
- 附件/XLSX只可参考名称与样式，不参与业务、数量、迁移或不一致裁决；
- 不实现V2自动指派、COM-02或第三方ERP连接器；
- 本Feature生产Provider形成前，F-IMP-002 Task 12继续`BLOCKED_BY_DEPENDENCY`。

## Task 5整改支撑单元

- `T-FCOM001-PROJ-01`由PROJ物理Owner交付，不形成独立Feature状态。公开`ProjectDeliveryScopeQualificationFactApi.inspect/lockAndRevalidate`；直管ACTION_EDIT只由锁定目标项目行的current PROJECT_MANAGER证明，不读取授权Grant或后代范围。经理/根身份/生命周期/阶段及项目/参与者/树版本全部冻结重验，`NORMAL_CLOSED`只允许S6、树版本为正，并按机器合同顺序稳定区分初次主体/范围失败、锁定事实陈旧、Owner损坏和Provider不可用；现有`ProjectParticipantFactApi`不放宽。
- Task 3来源减量/取消、Task 5 apply/release/resolve和Task 6锁定读取统一为`orderLineId -> scopeId -> detailId -> projectId水位`，并以真实MySQL交叉并发验证无反向锁序。
- Task 5成功审计按`specs/features/F-COM-001-delivery-scope-audit-contract.json`持久化精确键与状态判别联合快照；失败与平台认领、范围、版本及Outbox一并回滚。
