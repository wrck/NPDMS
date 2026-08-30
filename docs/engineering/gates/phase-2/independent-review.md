# SDS Phase 2 V1.8 独立复审

> 复审日期：2026-08-20
> 复审方式：fresh-context只读对抗复审、修复定点复核、最终全范围复审
> 当前状态：`APPROVED`
> Gate结论：`GO / READY_FOR_PHASE_3_V1.8`
> 范围：PRD V1.8的100项V1/V2正式需求；原重点差量为PM-03、PM-11、CUT-03、INT-12，2026-08-21补充复核CUS-02、CUT-07承载对象

## 1. 最终结论

Phase 2 V1.8的08、08a、09、10、11、12、13、15、16分册，100项显式契约映射及87对象/98来源绑定/1排除源的迁移契约已通过独立复审。ADR-0030的六张物理设计表能够最小承载ProjectTask执行契约、完成判定和CUT-03版本化清单；ADR-0031补充的两个逻辑对象均采用NONE_NEW/FEATURE_FORWARD_MIGRATION，不改变当前核心DDL或P3-E09哈希。两项决策均不复制目标业务正文或DAC技术状态，不新增PRD外角色、审批、割接阶段或通用工单。

2026-08-25 F-PROJ-004首次Feature Ready复审为NO-GO；整改后当前迁移契约为88对象/99来源绑定/1排除源，新增`ProjectTemplateMatchHistory`已进入受管生成链。此统计只证明机器契约已同步，不替代待执行的第二次Feature Ready独立裁决。

2026-08-25最终聚焦裁决`NPDMS-FPROJ004-FEATURE-READY-20260825-06`为`GO`：PRD修订、显式选模、属性写入旁路、权威历史字段矩阵、审计主体/原因来源及迁移受管链均已闭合。该GO只放行Feature Ready与后续快照锁定/Technical Plan，不代表实现、迁移、SIT/UAT或发布完成。

2026-08-28 F-PLT-002聚焦候选把共享模板、不可变修订和通用实例归PLT，Preparation专用实例继续物理分离，旧`pms_eng_form_*`保持不迁移不双写；当前生成契约为90对象/101来源绑定/1排除源。此段只登记待审候选，不改变既有Phase 2历史裁决，也不代表F-PLT-002 Feature Ready通过。

2026-08-28 F-CUS-001实现补丁完成正式回写后，客户主档目标表、MarketRelation、地点引用和五维权限切片进入受管生成链，当前契约为93对象/104来源绑定/1排除源；实现证据锁定NPDMS `a9f8b7c568546839d3d641531f8036bb75889a82`及V106～V108。该回写不改变当前核心DDL精确表集。

2026-08-29 F-COM-001修订008差量送审前，`AcceptanceScopeBinding`及V70三类来源进入受管候选，生成统计为94对象/107来源绑定/1排除源。本段只登记待审候选，不改写本文件既有独立裁决；P3-E09差量和Feature Ready均未据此通过。

2026-08-30 F-ACC-001同一Gate整改提交`5c1e1ff2498abf838310da607ae5d1426953b3ad`经独立复审GO：94对象/109来源绑定/1排除源，ADR-0039、报告草稿/当前唯一、原子发布替换撤销、变更事件及完整附件来源索引闭环；仅放行进入Feature Spec Gate，不批准Feature Ready、Technical Plan、代码或Flyway。

2026-08-30 F-ACC-002同一Gate整改提交`b98d0caafb724a13433aec382bafa30c02d30091`经独立复审GO：ADR-0041、同项目`T-SAT-SURVEY→D-SAT-REPORT`精确应交根、不可变RemediationFact驱动的collectionKey/taskRevision链及94对象/111来源绑定/1排除源闭合；仅放行Feature Spec Gate，不批准Feature Ready、Technical Plan、代码或Flyway。

允许将ADR-0030标记为`ACCEPTED`、Phase 2分册标记为`BASELINE`，并进入Phase 3形成Feature和前向DDL设计。

## 2. 复审发现与关闭

| 编号 | 发现 | 严重度 | 修复与复核 | 状态 |
|---|---|---|---|---|
| P2-V18-R01 | 重验证阶段可提前把ADR-0030改成ACCEPTED | Required | Gate状态与ADR状态双向校验：REVALIDATION仅允许PROPOSED，APPROVED必须ACCEPTED；正反向测试通过 | CLOSED |
| P2-V18-R02 | DAC技术状态复制只拒绝三个固定字段名 | Required | 解析CUT结果字段并按status/state/dispatch/schedule语义拒绝；改名dispatch变体与合法字段无误报测试通过 | CLOSED |
| P2-V18-R03 | BLOCKED_BY_DESIGN仅扫描数据库和契约映射 | Required | 扩展到08/08a/09/10/11/15/16、ADR-0030和phase2-contract-map九处正式契约；逐位置注入均被拒绝 | CLOSED |
| P2-V18-R04 | CUT结果表缺少生成current_marker所需的选择事实 | Required | 增加selection_started_at/selection_ended_at选择区间；当前唯一约束只对未结束区间生效，结果正文不可变，切换在同一事务关闭旧区间并追加新结果 | CLOSED |

## 3. 关键业务与模型结论

| 范围 | 结论 |
|---|---|
| ProjectTask通用详情 | 默认`TASK_NATIVE`就是WorkBinding的一种；其他类型按绑定关系操作真实业务实体，不建设第二套业务正文 |
| 完成判定 | complete命令校验任务、执行契约和Owner事实版本，追加TaskCompletionEvaluation；通知、HTTP或组件加载成功不等于任务完成 |
| CUT-03 | 清单仍属于CUT-01的P3工作台；清单根、采集项和结果版本化，D级不创建清单，不新增采集阶段 |
| DAC边界 | CUT只保存CollectionTask/结果版本引用和业务解释；DAC技术状态、调度状态和凭证事实不复制到CUT |
| 迁移 | 存量ProjectTask仅前向初始化显式TASK_NATIVE版本1；不按名称、菜单、模块、URL或历史状态推断业务绑定或完成事实 |
| pms_cut_risk | 只允许字段级证明的任务、原编码/名称/类型、说明和填写事实；不推断采集项版本、Schema、必填、CollectionTask、自动结果、业务通过或配置缺口 |

## 4. 可复现证据

| 校验 | 结果 |
|---|---|
| PRD V1.8基线与语义 | PASS，67/67；100项正式需求，V1=53、V2=47 |
| 13领域生成 | PASS，formal=100、V3=31、OUT_OF_SCOPE=9 |
| Phase 1 / Phase 2 / Phase 3状态校验 | PASS；Phase 1/2基线有效，Phase 3仍须V1.8差量验证 |
| Phase 2契约映射 | PASS，100项；生成器无漂移 |
| 领域实体迁移 | PASS，93对象/104来源绑定/1排除源；生成器无漂移 |
| 物理承载负向门禁 | PASS，缺表、提前接受ADR、DAC状态及改名dispatch状态、BLOCKED_BY_DESIGN回流、缺选择区间均被拒绝 |
| 脚本单元测试 | PASS，349/349 |
| `git diff --check` | PASS |

## 4.1 CUS-02/CUT-07定点差量复审（2026-08-21）

| 复核项 | 结论 |
|---|---|
| CUS-02 Owner与承载 | `CustomerServiceLevelRevision`归CUS；只追加等级与策略时态版本，不从联系人或关系快照反推历史等级 |
| CUT-07 Owner与承载 | `CutoverConfigurationRevision`归CUT；后台配置版本与`CutoverPlan`分离，字典值仍由基础平台拥有 |
| 迁移边界 | 两对象均为`NONE_NEW / FEATURE_FORWARD_MIGRATION`；当前契约为87对象/98来源绑定/1排除源 |
| 物理影响 | 不修改当前核心DDL、Flyway或P3-E09逐项寄存器；未来表只允许由CUS-02/CUT-07 Feature前向迁移创建 |
| 权限与状态 | 复用PRD既有客户服务管理岗、管理层、系统管理员的割接配置维护权限及管理员配置发布权限；不新增角色、审批节点或业务状态 |

差量结论：`GO`。本附录只补充ADR-0031引起的两对象变化，原复审其他范围和后置门禁不变。

## 5. 后置门禁

- ADR-0030六表已进入当前冻结核心DDL并通过P3-E09模型一致性验证；该结论不等于批准实际Flyway或生产执行。ADR-0031的四张未来Feature表不在当前核心DDL，须分别由CUS-02/CUT-07 Feature以前向迁移审批创建。
- Q08仍是候选索引，只能在Feature查询计划与P3-E06性能验证后确认，不因Phase 2 GO而自动批准。
- `AI-MIG-000`不是普通功能发布门禁。只有Release包含历史迁移或数据切换时才适用，须绑定真实批次、验证结果和批准窗口。
- 本结论不批准Feature完成、DDL执行、历史迁移、数据切换、生产环境或Release。

## 6. Gate结论

`APPROVED / GO / READY_FOR_PHASE_3_V1.8`
