# DU-20260902-ACC-AST-SELECTIVE-INTEGRATION ACC与AST选择性集成

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-ACC-001=FEATURE_EXCLUSIVE;F-ACC-002=FEATURE_EXCLUSIVE;F-AST-002=FEATURE_EXCLUSIVE`
> Task范围：`按Requirement选择性集成F-ACC-001初验/终验报告、F-ACC-002满意度闭环与F-AST-002产品类型受控副本；不接收COM重复实现、F-INS实现或来源工作树未提交改动`
> Owner：`Codex本次master ACC-AST选择性集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`caaf008c96b9a9fc94a2e337e8749cff07dedb65`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/**;docs/decisions/**;docs/design/**;docs/engineering/gates/phase-2/**;docs/superpowers/plans/2026-08-30-f-acc-00*;docs/superpowers/plans/2026-08-30-f-ast-002*;docs/traceability/**;specs/001-project-delivery-platform/domains/ACC-*;specs/001-project-delivery-platform/domains/AST-*;specs/001-project-delivery-platform/domains/SRV-*;specs/features/F-ACC-00*;specs/features/F-AST-002*;specs/features/README.md;tasks/features/F-ACC-00*;tasks/features/F-AST-002.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-ACC-AST-SELECTIVE-INTEGRATION.md;tasks/delivery-units/README.md;pms-module-project/**;pms-module-platform/**;pms-module-asset/**;pms-module-service/**;yudao-module-infra/**;yudao-server/**;sql/migrations/V16*.sql;sql/migrations/V17*.sql;scripts/**;yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/**;yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/**;yudao-ui/yudao-ui-admin-vue3/src/router/modules/remaining.ts`
> 串行资源：`ACC公共契约;AST产品类型公共契约;PLT文件与导出契约;PROJ任务契约;V164起Flyway;Requirement追溯投影`
> 旧功能范围：`F-ACC-001明确替代的V17验收报告入口仅保留历史读取并标记废弃；旧电子完工证明、旧满意度/回访原始事实按规格PRESERVE_EXISTING/PRESERVE_RAW，不作为新能力基础；AST不得以conpType、旧字典、型号或自由文本替代产品类型稳定编码`
> 验证：`Feature与物理契约校验;Flyway静态及可用环境验证;ACC/AST/PLT/PROJ聚焦测试与Reactor构建;前端类型检查、测试与生产构建;五轴代码审查`
> 集成记录：`AST=master@524a70e7；F-ACC-001=master@e53f7243；F-ACC-002=master@b3e7c76e；排除COM-A祖先、来源工作树脏改动、F-INS实现、来源生成矩阵与冲突Flyway编号；三个Feature均保持IN_PROGRESS / BLOCKED_BY_SPEC`

## 集成裁决

- F-ACC-001先于F-ACC-002集成；两者只接收`21423d9c`之后的ACC/PLT/PROJ需求能力，不重复接收COM-A祖先。
- F-AST-002只接收`a52b22b4..68bc56ec`中产品类型受控副本、公共查询、受控导入和Inspection只读适配器；排除同提交夹带的CUT/IMP/COM迁移及F-INS任务状态。
- PRD修订010和master现行SDS优先；来源分支的生成矩阵、Gate投影和旧Flyway编号不得覆盖master，均在集成后重新生成或重编号。
- `Q-GOV-20260901-001`保持`BLOCKED_BY_SPEC`：来源ACC/AST同号修订010不进入master；本DU只形成可构建候选增量，不恢复Feature Ready或Implementation Done。
- 历史分支Implementation Done只作候选证据；三个Feature须在master最终内容上重新验证后再分别登记当前状态。

## 交接

- 当前：AST、F-ACC-001、F-ACC-002已按独立提交顺序进入master；V17单行验收栈已标记废弃，旧电子完工证明及旧满意度/回访原始事实继续保留且禁止作为新能力实施基础。
- 验证：AST 27模块依赖构建及200项适用单元测试通过；F-ACC-001契约12项、后端44项适用测试、前端3项测试与类型检查通过；F-ACC-002契约20项、后端117项适用测试、前端类型检查与生产构建通过。MySQL分类测试分别跳过27/4/8项，未倒签真实运行证据。
- 后续：本DU释放排他写边界；`Q-GOV-20260901-001`关闭后须新建运行复验DU，补齐当前master真实MySQL、Chromium（ACC）和独立Implementation Done裁决。
- 完成口径：本DU只完成可构建代码增量、前向迁移、公共契约、UI和治理投影的选择性集成，不产生Feature Ready或Implementation Done。
