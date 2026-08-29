# F-CUT-002 旧割接任务实现复用审计

> Requirement：`CUT-01（V1/P0）`、`CUT-02（V1/P0）`
> 审计结论：`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`

## 1. 审计范围

- 后端：`pms-module-cutover`的cut-task Controller、Service、Mapper/XML、DO/VO、`CutTaskStatusRules`和CUT治理Provider；同时核对risk/plan与任务的旧关联。
- 前端：`views/pms/cutover/cut-task`、`api/pms/cutover/cut-task`及其字典、菜单和按钮权限。
- 数据与配置：`V12__pms_cutover_tables.sql`、`V13__pms_cutover_menus.sql`、`V16__pms_business_button_permissions.sql`、V19/V20/V35测试数据和正式领域迁移契约。
- 状态与权限：旧任务状态流转、删除/评审动作、项目/设备数据范围和服务端授权。
- 测试：`CutTaskStatusRulesTest`、`CutTaskMapperContractTest`和现有前端测试覆盖。

## 2. 逐项判断

| 存量对象 | 当前事实 | 结论 | F-CUT-002处理 |
|---|---|---|---|
| `pms_cut_task` | 保存项目、编号、名称、类型、组网、来源、等级、计划/实际时间、旧状态和版本 | 有效任务身份可前向迁移，业务语义不可整表照搬 | 按审批后映射契约迁入`cut_task`；旧表、旧接口和旧行为不改写、不双写 |
| 旧task Controller/Service/VO | 通用CRUD；客户端可传编号、来源、等级和状态输入；支持物理逻辑删除 | 不可直接复用 | 复制Yudao Controller/Service/VO技术结构后改为`/api/v1/pms/cutover-tasks`命令；来源、编号、等级和状态由服务端持有，不暴露通用删除 |
| `validateProjectCutoverReady` | 仅判断`projectId`非空，未读取EXE-01～04权威事实或保存快照 | 不可复用为EXE-06 | 保留旧方法不改；新应用服务只消费F-IMP-001公开契约 |
| `CutTaskStatusRules` | `DRAFT/PREPARING -> PENDING_REVIEW -> CLOSURE_IN_PROGRESS`，并将6/7/8视为旧终态 | 与P1接入、P2等级确认和A/B/C/D分支不兼容 | 不修改旧规则；为新`CutoverTask`复制并实现正式状态机 |
| 旧cut-task页面/API | 前端可选来源、输入来源ID、默认C级，并显示编辑、删除、提交评审、通过和驳回 | 不可增强为P1/P2工作台 | 新建页面和API文件，旧页面保持不变；新页面不默认等级、不伪造后续动作 |
| `pms:cut-task:*`菜单/权限 | 以通用CRUD和`audit`区分按钮，不表达一线工程师、项目/设备范围或`allowedActions` | 不可直接作为新授权 | 新增`pms:cutover-task:*`功能权限；服务端结合PROJ scope action、AST设备范围、任务负责人和状态守卫 |
| `pms_cut_risk` | 旧任务下的风险/调研项、处理人和状态 | 不是P2问卷、人工等级或评估版本 | 不迁入`cut_assessment`，不反推答案或等级；保持旧数据不变 |
| `pms_cut_plan`及其旧评审 | 旧任务下方案与独立评审状态 | 不属于本Feature | 不迁移、不修改；P4/P5由后续Feature审计 |
| 现有测试 | 覆盖旧三个评审迁移、旧终态和Mapper租户/删除/排序静态契约 | 可防止旧行为被误改，不证明CUT-01/02 | 保留作为回归；新增独立聚合、迁移、授权、并发、真实MySQL和浏览器验收 |
| V19/V20/V35测试种子 | 含默认值、人工状态和为页面展示生成的记录 | 不是生产权威事实 | 不用于证明映射完整性、IMP READY、授权或浏览器闭环 |

## 3. 前向迁移边界

- `CutoverTask`执行正式`CURRENT_FORWARD`，但迁移单元是“可证明的有效任务事实”，不是将旧DO、tinyint状态或页面默认值原样复制到新Owner。
- 可候选映射的存量字段限于经核对后的任务ID/编号、项目、名称、类型、组网、计划/实际时间、租户、创建/更新审计和版本。来源、等级、状态和活动设备范围须有单独完整映射才可迁移。
- 旧表没有客户稳定引用、设备范围、IMP快照ID/版本、P2评估版本、阶段历史、来源事件ID或当前负责人；不得用空值、默认C级或测试种子补成正式新事实。
- 旧tinyint状态与`GRADE_CONFIRMING/SURVEYING/PLAN_DRAFTING`无一一对应；映射无法证明时，保留旧记录且不生成可继续新任务。
- Feature Ready前须将字段/状态/完整性映射和不可迁行的处置固化到正式迁移契约；实施时仅使用新Flyway前向迁移。

## 4. 复用方式与结论

- 直接复用：Yudao租户、通用返回、校验注解、乐观锁和项目现有Query对象编码模式；不复用旧业务语义。
- 复制增强：新建`cut_task`对应领域/应用/接口和新P1/P2页面，使旧类、旧页面、旧接口及旧数据保持不变。
- 不可复用：旧就绪校验、旧状态机、通用删除/评审、默认C级、前端来源/等级输入、旧风险记录到P2评估的推导。

结论：F-CUT-002不是`NEW_ONLY`。它必须保留旧运行面，以新类/新页面实现正式P1/P2语义，并仅将经完整映射证明的`pms_cut_task`任务事实前向迁入`cut_task`。`pms_cut_risk`不是`CutoverAssessment`的权威历史来源。
