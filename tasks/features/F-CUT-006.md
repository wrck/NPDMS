# F-CUT-006 P6割接跟踪与闭环

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-06@V1=FULL`
> Feature Spec：`specs/features/F-CUT-006-p6-cutover-closure.md`
> 机器合同：`specs/features/F-CUT-006-api-contract.json`、`specs/features/F-CUT-006-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-006-legacy-reuse-audit.md`

## 当前最小工作单元

- 形成CUT-06完整纵向Feature，不拆成INT-12专用Provider碎片。
- 跨模块仅保留`ProjectScopeApi/FileArtifactApi/INT-12`消费端口；正常正向闭环使用`src/test`受控替身，不修改其他Owner或Yudao。
- 最近Gate：F-CUT-006 Feature Ready独立复审。GO前不生成Technical Plan、不实现代码或DDL。
- 锁定提交`d52acdfb`独立复审为`NO-GO`；当前仅整改一设备一采集任务、归档/晚到回调、平台事务顺序/resultRef及PLT迁移证据生命周期四项规格阻断。

## 状态边界

- `Q-FCUT004-001`的P6职责变化回P4分支保持`BLOCKED_BY_SPEC`，不进入正常P6闭环。
- 生产INT-12与下游项目/资产消费者缺失阻断真实浏览器和Implementation Done，不阻断Ready后CUT自有内核及受控替身实现。
- 旧`pms_cut_execution/pms_cut_observation`和旧页面保持不变。
