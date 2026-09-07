# ADR-0038：合同管理员按SYSTEM当前公司范围访问合同订单

> 状态：`ACCEPTED`<br>
> 日期：2026-08-29<br>
> Requirement：`COM-01@V1`<br>
> 前置批准：`CHG-PRD-2026-08-29-009`补充候选`1a9ca704422b275be9d19629d2d61af1782138c4`

## 背景

Q-FCOM-001已由需求方选择方案B并经PRD Baseline Gate独立批准。合同管理员的首次合同可见性不能由功能权限、部门树、既有项目关系或DeliveryScope产生，也不新增第二套合同授权事实。

## 决策

1. SYSTEM继续拥有现有`UserCompanyDepartmentScope`；COM直接调用现有`OrganizationScopeApi.getActiveScopes(subjectUserId)`，不修改Yudao基础平台，不新增合同专用SYSTEM接口、表或Provider。
2. 合同管理员的合同侧`ContractProjectScope`是当前有效scope行中非空`companyCode`的原值精确去重集合。部门、主范围标记、scopeRole、项目关系和DeliveryScope均不扩大或缩小该集合。
3. 合同目录、详情、销售订单、订单行和项目—合同关系维护使用同一集合。空范围或Owner事实未知/不可用时列表返回空，详情和写操作拒绝；合同所属公司编码缺失时不得推断。
4. 关系写入前重新读取当前scope并按合同公司编码重验；成功时把按scope ID排序的全部命中`id/version`写入既有`AuditRecord.authorizationSnapshot`。撤权或到期不删除历史关系，但下一次查询和维护立即按新当前事实判定。
5. 查询和关系维护分别要求既有`pms:commerce:contract:query`、`pms:commerce:contract:relate`；合同金额及已标记商务敏感字段明文另需`pms:commerce:contract:sensitive-read`。字段权限不扩大公司范围。
6. 正向公司范围不进入COM缓存。列表使用场景化Query的必选公司编码集合并保持精确字符串相等；空集合不得通过省略SQL条件变成全量。

## 物理与Gate影响

- 复用现有SYSTEM授权事实、COM公司编码字段及平台审计事实；不新增表、字段、索引、Flyway或迁移来源。
- P3-E09影响为`NO_PHYSICAL_DELTA`；本ADR只形成授权、API、缓存、异常和审计SDS差量。
- 独立GO后只允许回写本ADR为`ACCEPTED`和当前SDS差量为`READY / GO`，再进入同一Feature Spec与机器契约整改。

## 明确排除

- 不修改Yudao基础平台或SYSTEM授权生命周期。
- 不新增合同授权表、合同专用授权API、部门到公司的推导或租户全量兜底。
- 不批准F-COM-001 Feature Ready、Technical Plan、产品代码、Flyway、SIT、UAT、Deployment或Release。
- 不改变Q-FCOM-002的退出/回退阻断边界。
