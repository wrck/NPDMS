# F-SOL-002 工勘分工信息采集与实施就绪

> Feature实施状态：`IMPLEMENTING`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FSOL002-FEATURE-READY-20260827-01-R2`
> Technical Plan Gate：`PASS / NPDMS-FSOL002-TECHPLAN-20260827-01-R2`
> Implementation Done Gate：`PENDING`
> 当前阻断：`无；INT-05未实施时仅OA必需项保持NOT_READY，不阻断无OA正向主线`
> 当前任务：`Task 2 提供PROJ冻结WorkBinding公共事实`
> Requirement ID：`PRE-02（V1/P0）`
> Feature Spec：`specs/features/F-SOL-002-site-survey-assignment-and-readiness.md`
> Feature物理契约：`specs/features/F-SOL-002-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-27-f-sol-002-site-survey-assignment-and-readiness.md`
> 锁定规格提交：`e9e3122b33dbc34179d89326f5caa7972365f074`

## 实施边界

- 本Feature只实现PRE-02，不建设通用Schema设计器、INT-05 OA流程、第二任务树、S4命令或跨Context事件。
- SOL以六张`sol_*`表承载当前及历史事实；旧`pms_eng_site_survey`只作V1.7差距证据，不迁移、不双写。
- 固定V1表单目录由既有`infra_config`稳定键承载，项目模板仅冻结精确目录版本和Schema快照。
- 文件只冻结PLT FileArtifact精确版本；来源只保存稳定引用及归一事实；SOL不保存文件正文、URL或OA原单据。
- 每个Task实现、验证、提交后均须取得独立Implementation Done GO，才回写PASS并推进下一Task。

## 任务跟踪

- [x] Task 1 建立PRE-02六表、字典权限与Feature工作单（PASS / NPDMS-FSOL002-TASK1-IMPLEMENTATION-20260827-01-R1）
- [ ] Task 2 提供PROJ冻结WorkBinding公共事实（实施中）
- [ ] Task 3 实现SOL六表持久化原语与固定表单规则
- [ ] Task 4 实现模板初始化、当前准备查询与历史投影
- [ ] Task 5 实现逐项指派、填写与精确文件证据
- [ ] Task 6 实现提交、逐项确认及退回新版本
- [ ] Task 7 实现就绪评估、不可变快照与公共重验API
- [ ] Task 8 实现来源同步异常、外包引用与逐项豁免
- [ ] Task 9 建设响应式工勘准备界面并退役旧写入口
- [ ] Task 10 完成真实MySQL、浏览器、独立复审与Feature回写

> 检查点（2026-08-27）：Feature Ready及Technical Plan均经独立裁决GO；按锁定计划从Task 1开始实施，不重开PRD或SDS。

> Task 1候选证据（2026-08-27）：迁移契约6/6 PASS；清洁隔离MySQL从V1成功迁移至V97，六张SOL表、10条SOL域内租户复合外键、6个工勘项字典、4项权限及唯一固定目录均核验通过；固定目录JSON长度445且有效；同稳定键不同ID的存量目录可原位恢复且保持唯一，固定ID被其他配置占用时V97拒绝并保持无关配置不变；仅5份seed-owned DRAFT模板获得PRE-02绑定，PUBLISHED模板未修改。正式PASS以独立Implementation Done裁决为准。

> Task 1独立裁决（2026-08-27）：`NPDMS-FSOL002-TASK1-IMPLEMENTATION-20260827-01-R1 / GO`；允许回写PASS并推进Task 2。

> Task 2候选证据（2026-08-27）：PROJ已在既有ProjectTask ExecutionContract真值上提供窄`ProjectWorkBindingFactApi`；模板发布通过`ConfigApi`读取固定V1目录并校验唯一PRE-02目标四元组、六类冻结项及表单版本；inspect按受信租户、项目和精确目标唯一查询，lockAndRevalidate按Project→ProjectTask→当前ExecutionContract顺序锁定并重验ID、归属及三段版本。聚焦单元27/27 PASS；清洁隔离MySQL从V1迁移至V97后，精确/越租户/多记录查询与锁定当前读2/2 PASS，25模块Reactor BUILD SUCCESS。正式PASS以独立Implementation Done裁决为准。
