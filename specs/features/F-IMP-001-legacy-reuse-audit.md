# F-IMP-001 旧实施记录复用审计

> Requirement：`EXE-06（V1/P0）`
> 审计结论：`NEW_ONLY / PRESERVE_LEGACY / REBUILD_AFTER_OWNERS`
> Feature Spec：`specs/features/F-IMP-001-implementation-readiness-snapshot.md`

## 1. 审计范围

- 后端：旧arrival、installation、configuration、jointtest的Controller、Service、Mapper、DO和VO；PRE-02 `SiteSurveyReadinessApi`。
- 前端：四套旧工程实施页面及API。
- 数据库：`V10__pms_engineering_tables.sql`及后续安装地点增强迁移。
- 测试：安装乐观锁/地点测试、四套页面测试现状、公开项目与设备API测试。

## 2. 逐项结论

| 存量对象 | 当前事实 | 结论 | F-IMP-001处理 |
|---|---|---|---|
| `pms_eng_arrival` | 单记录CRUD，整数`0/1/2`，允许删除；无正式到货批次、差异和最终确认契约 | 不可直接作为EXE-01权威事实 | 保持不变；等待EXE-01 Owner Feature公开事实 |
| `pms_eng_installation` | 单设备CRUD和`0/1/2/3`状态；有乐观锁与地点版本增强 | 可参考技术模式，不能直接升级为EXE-02完成 | 保持旧入口；等待EXE-02 Owner Feature完成映射 |
| `pms_eng_configuration` | 人工CRUD和开始/完成/异常；不具备EXE-03 CollectionTask、解析尝试和组件绑定事实 | 不可复用为EXE-03权威结果 | 保持不变，不直查 |
| `pms_eng_joint_test` | 人工CRUD和开始/通过/失败；无正式采集消费与问题引用契约 | 不可复用为EXE-04权威结果 | 保持不变，不直查 |
| 四套旧页面/API | 可新增、编辑、删除并直接调用旧状态动作 | 与正式Owner聚合和历史不可变边界不一致 | 不修改、不嵌入新EXE-06流程 |
| 旧测试 | 只有安装地点/乐观锁和一个前端表单序列化测试；到货、配置、联调无正式业务闭环测试 | 不能证明EXE-01～04完成 | 不作为READY证据 |
| `SiteSurveyReadinessApi` | PRE-02工勘就绪事实 | 语义与EXE-06不同 | 仅参考inspect/lockAndRevalidate接口模式，不复用数据或判定 |
| `ProjectScopeApi`等PROJ公开契约 | 已提供项目范围和锁定重验 | 可直接复用技术契约 | 由IMP通过API消费，不直查PROJ表 |
| `AssetDeviceScopeApi` | 只返回缺失/不可用/重复SN分类，不返回稳定设备ID与归属版本 | 不足以冻结EXE-06设备范围 | 需AST Owner提供最窄只读事实契约 |
| `DeviceQueryApi` | 按deviceId返回设备与归属摘要 | 可用于已知ID查询，但不能完成SN选择闭环 | 作为后续AST契约设计输入 |

## 3. 数据与迁移边界

- `ImplementationReadinessSnapshot`正式物理表为`imp_implementation_readiness_snapshot`，不得写`proj_project_stage_snapshot`。
- 迁移契约对该聚合固定为`REBUILD_AFTER_OWNERS`。只有EXE-01～04 Owner事实、字段映射和版本水位通过各自Feature Gate后才能重建。
- 不从旧tinyint状态、测试种子、附件、菜单或页面按钮推断`READY`；不建立兼容层、双写或后台修正脚本。
- 旧功能保持可用；新Feature通过公开API旁路读取正式Owner事实，后续是否退役旧入口不在本Feature范围。

## 4. 结论

F-IMP-001只能新建快照聚合与公共契约。旧实现保留且不直接消费；项目范围API可复用，设备范围API需由AST补充稳定事实。当前复用审计不关闭EXE-01～04或EQP-01的Feature Gate。
