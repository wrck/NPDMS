# PRD V1.8修订012：巡检安全审核最后事实生效

> 变更编号：`CHG-PRD-2026-09-02-012`<br>
> 状态：`APPROVED / MERGED_INTO_BASELINE`<br>
> 影响Requirement：`INS-03`、`INS-09`<br>
> 关联裁决：`Q-FINS001-005`方案A，需求方于2026-09-02确认

## 1. 决议

1. 安全审核事实只追加，不覆盖、不删除。只有`DRAFT` revision可以追加审核事实。
2. 同一租户、同一revision、同一命令/正则内容摘要存在多条事实时，按`reviewed_at DESC, id DESC`选择唯一当前结论。`reviewed_at`由服务端生成，时间相同时以更大事实ID为准。
3. 最后一条为`PASSED`才允许发布，最后一条为`REJECTED`则阻断；追加`PASSED -> REJECTED`立即撤销发布资格，追加`REJECTED -> PASSED`后恢复发布资格。
4. 内容摘要变化或形成新revision必须重新审核，旧摘要或旧revision的`PASSED`不得复用。
5. 审核人权限后续撤销不追溯修改既有历史事实；需要撤销当前结论时，追加新的`REJECTED`事实。
6. 审核与发布共享规则聚合锁和CAS边界，发布事务必须在锁内重新选择最后事实，避免审核与发布形成混合快照。
7. revision发布后不得继续追加审核；纠正命令、正则或审核结论必须复制形成新草稿revision并重新审核。

## 2. 不变范围

- 不新增Requirement、业务角色、审批节点、多人会签、规则状态、API动作、数据表或第三方能力。
- 不修改既有审核事实、已发布revision、历史任务快照或审计记录。
- 本修订不关闭`Q-FINS001-006`，不批准Yudao System公开API扩展，不提供生产审核授权Provider，也不放行完整发布入口或Feature Done。
- 旧`pms_srv_rule`、旧接口、旧页面继续保留；新能力不得建立在旧载体上。

## 3. 验收边界

- 同revision同摘要追加`PASSED -> REJECTED`后发布必须失败；追加`REJECTED -> PASSED`后方可恢复发布资格。
- 两条事实`reviewed_at`相同时，较大`id`决定当前结论。
- 旧摘要`PASSED`、旧revision `PASSED`、非`DRAFT`追加审核均拒绝且无业务副作用。
- 审核与发布并发时，发布要么看到锁内最后事实并据此裁决，要么因CAS冲突失败，不得以过期`PASSED`发布。
- 本修订完成只表示Q-FINS001-005业务语义关闭，不表示生产授权、代码、迁移、SIT、UAT、Release或Implementation Done完成。
