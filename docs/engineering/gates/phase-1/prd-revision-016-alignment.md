# PRD V1.8修订016联动回写与验证记录

> 状态：`DOCUMENT_STATIC_ALIGNMENT_VERIFIED`；独立设计复审、物理契约复核及运行验收仍由当前Gate控制。
> PRD变更：`CHG-PRD-2026-09-06-016`；输入PRD Blob：`37c709bf49ce3813042b650eea44050537cec4e0`；输出PRD Blob：`ebd8f115566e0eacb20294f1b261a37f8ff81d12`。
> 当前复核PRD Blob：`4b7bd7a4b099e18edb7e5a10c8c27615118c9d7b`；SHA-256：`df80a00713fdf63466dd0660c8abd898424479e2f309c64efdcd74c3624f1d2c`。
> 输入提交：`992731aaab3e5d1d8e61e27f256524ff372bdff3`；业务基准：`641538d67d2a6b6705b1b9c04f245c18adaaffe8`。
> 授权范围：本轮需求方明确要求PRD正文、追溯投影与受影响设计共同修订；未请求也未执行master合并、应用代码改写或部署。

## 1. 审查项与回写落点

| 项 | Requirement切片 | 修正/验证语义 | SDS落点 | 文档状态 | 运行验收 |
|---|---|---|---|---|---|
| R01 | PM-03@V1、EXE-03@V1、PM-11@V1 | 售前S0/S4且没有EXE-02、S5、S6 → 授权采集/联调可执行并从S4闭环，不补造任务 | `docs/design/05-state-machine.md`；`20-test-design.md#TC-PRD016-R01` | STATIC_ALIGNED | NOT_RUN |
| R02 | PM-03@V1、PLN-04@V1、SCH-05@V1、EXE-06@V1 | 裁剪模板首次批准；S4期间计划换版 → 仅PROJ按冻结图准出/唯一后置/准入推进；后期换版不退回 | `docs/design/06-workflow-design.md`；`20-test-design.md#TC-PRD016-R02` | STATIC_ALIGNED | NOT_RUN |
| R03 | CLO-01@V1、CLO-02@V1、PM-10@V1 | 未完成交付的代理商自服或取消项目 → 按退出类型校验；NO_TRACKING/EXCEPTION不先要求正常准出 | `docs/design/05-state-machine.md`；`20-test-design.md#TC-PRD016-R03` | STATIC_ALIGNED | NOT_RUN |
| R04 | ACC-03@V1、ACC-04@V1、CLO-01@V1 | 元数据及附件完整、结论不通过/整改/未知 → 证据保留，不满足验收通过及NORMAL门禁 | `docs/design/08-data-model.md`；`20-test-design.md#TC-PRD016-R04` | STATIC_ALIGNED | NOT_RUN |
| R05 | CUT-03@V1 | P3缺必填时保存、提交；补齐后提交 → 保存留P3、缺项提交拒绝；只有有效提交进入P4 | `docs/design/06-workflow-design.md`；`20-test-design.md#TC-PRD016-R05` | STATIC_ALIGNED | NOT_RUN |
| R06 | PM-05@V2、RPT-02@V2、PM-03@V1 | V1未启用PM-05、RPT-02、SCH-03 → V1事实链可独立验收；E2E-22/23才执行转销与报表 | `docs/design/02e-version-scope-matrix.md`；`20-test-design.md#TC-PRD016-R06` | STATIC_ALIGNED | NOT_RUN |
| R07 | EXE-03@V1、INT-12@V1 | IMP批准业务快照；独立中心同类手工输入 → 前者经Owner/权限重验可执行，后者拒绝；不依赖SCH-03 | `docs/design/12-integration-design.md`；`20-test-design.md#TC-PRD016-R07` | STATIC_ALIGNED | NOT_RUN |
| R08 | INT-05@V1、INT-09@V1 | HR离职与目录启用按不同顺序到达 → 独立来源版本保留，离职/禁用任一有效均拒绝并撤销会话 | `docs/design/02c-data-ownership-matrix.md`；`20-test-design.md#TC-PRD016-R08` | STATIC_ALIGNED | NOT_RUN |
| R09 | PLT-02@V1、INT-12@V1 | 有幂等键但来源/签名/对象授权无效 → 不创建可用文件版本或业务引用；留下拒绝审计 | `docs/design/13-file-design.md`；`20-test-design.md#TC-PRD016-R09` | STATIC_ALIGNED | NOT_RUN |
| R10 | INS-02@V2、INS-04@V2、INT-12@V2 | 预检后未再次输入临时密码或授权已撤销 → 不执行正式任务；新输入/授权与适用预检同时成立才执行 | `docs/design/14-security-design.md`；`20-test-design.md#TC-PRD016-R10` | STATIC_ALIGNED | NOT_RUN |
| R11 | PLN-01@V1、PLN-03@V1 | 模板无S5/S6；计划路径多义/占比无效 → 只计算实际参与阶段，多义/错误不形成生效计划 | `docs/design/08-data-model.md`；`20-test-design.md#TC-PRD016-R11` | STATIC_ALIGNED | NOT_RUN |
| R12 | PM-06@V2、COM-01@V1、ACC-03@V1 | 范围A追加B，旧报告仅覆盖A或仅上传B → 原A事实不变，总范围需新总体明确通过版本 | `docs/design/09-database-design.md`；`20-test-design.md#TC-PRD016-R12` | STATIC_ALIGNED | NOT_RUN |
| R13 | CUT-03@V1、INT-12@V1 | P3采集项下发/成功/失败/重复回调 → 任务/清单/采集项/设备/结果版本精确绑定，不替代业务通过 | `docs/design/10-api-design.md`；`20-test-design.md#TC-PRD016-R13` | STATIC_ALIGNED | NOT_RUN |
| R14 | INS-09@V2、NFR-02@V2 | 超时1、30、31及0秒，31秒附审批说明 → 1..30可配置，其他拒绝；超时后续命令按冻结规则 | `docs/design/14-security-design.md`；`20-test-design.md#TC-PRD016-R14` | STATIC_ALIGNED | NOT_RUN |
| R15 | INS-07@V2 | 工程师申请并尝试最终归档，授权服务经理归档 → 申请/处理/确认/归档权限分离，服务经理重验所有门禁 | `docs/design/07-authorization-design.md`；`20-test-design.md#TC-PRD016-R15` | STATIC_ALIGNED | NOT_RUN |
| R16 | INT-03@V1、INT-05@V2、AUT-01@V2 | OA材料审批与SUB平台审批；CRM治理工作台 → 分操作Owner/兜底；未定义治理工作台不进入V2 | `docs/design/12-integration-design.md`；`20-test-design.md#TC-PRD016-R16` | STATIC_ALIGNED | NOT_RUN |

## 2. 自动生成链

`docs/baseline/prd-v1.8.md`与`需求/PRD-项目实施交付管理平台.md`按字节一致；13领域需求、Requirement覆盖Markdown/JSON及Phase 2契约均重新生成。修订号和Git Blob从PRD实际输入推导，不再硬编码008；当前工程状态从gate-status读取，不再固定“当前规格阻断：无”。

100项Requirement、主版本V1 53/V2 47、111个正式切片（V1 53/V2 58）保持不变。新增E2E-22/23只隔离转销/报表的V2验收，不新增Requirement或扩大版本承诺。

## 3. 受影响Feature与历史事实保护

| Feature | 待重验证切片 | 权威Task | 处理 |
|---|---|---|---|
| F-COM-001 | COM-01@V1 | `tasks/features/F-COM-001.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PLT-001 | PLT-02@V1 | `tasks/features/F-PLT-001.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PROJ-001 | PM-01@V1、PM-03@V1 | `tasks/features/F-PROJ-001.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PROJ-002 | PM-02@V1 | `tasks/features/F-PROJ-002.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PROJ-005 | PM-08@V1 | `tasks/features/F-PROJ-005.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PROJ-006 | PM-10@V1 | `tasks/features/F-PROJ-006.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PROJ-007 | PM-11@V1 | `tasks/features/F-PROJ-007.md` | Spec当前Ready重验证，原Task字节保持不变 |
| F-PROJ-008 | PM-03@V1 | `tasks/features/F-PROJ-008.md` | Spec当前Ready重验证，原Task字节保持不变 |

上述标记不撤销历史Implementation Done，也不把文档修订当作新实现完成。没有对应Feature的ACC/CLO/INS等义务继续显示实际未开始/未覆盖状态，不能用一个旧Feature的FULL覆盖新语义。

## 4. 首轮实际检查（历史；当前结果见末节）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `/usr/bin/python3 -m unittest discover -s scripts/tests -p test_prd_revision_016_alignment.py -v` | 0 | PASS |
| `/usr/bin/python3 scripts/validate_prd_revision_016_alignment.py` | 0 | PASS |
| `/usr/bin/python3 scripts/validate_prd_domain_generation.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains` | 0 | PASS |
| `/usr/bin/python3 -m unittest discover -s scripts/tests -p test_generate_requirement_traceability.py -v` | 1 | FAIL |
| `/usr/bin/python3 scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md` | 2 | FAIL |
| `/usr/bin/python3 scripts/validate_prd_semantics.py --prd docs/baseline/prd-v1.8.md` | 1 | FAIL |
| `/usr/bin/python3 scripts/validate_sds_phase1.py` | 1 | FAIL |
| `/usr/bin/python3 scripts/validate_sds_phase2.py` | 1 | FAIL |
| `/usr/bin/python3 scripts/validate_sds_phase3.py` | 1 | FAIL |
| `/usr/bin/python3 scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --check` | 0 | PASS |
| `/usr/bin/python3 scripts/generate_phase2_contract_map.py --prd docs/baseline/prd-v1.8.md --check` | 0 | PASS |
| `git diff --check` | 0 | PASS |

### 未通过检查：`/usr/bin/python3 -m unittest discover -s scripts/tests -p test_generate_requirement_traceability.py -v`

```text
hase-1-追溯链) / [02领域](../design/02-domain-model.md) / [02d契约](../design/02d-cross-context-contracts.md) / [04模块](../design/04-module-design.md) / [05状态](../design/05-state-machine.md#2-核心状态机) / [06流程](../design/06-workflow-design.md#2-核心审批流) / [07权限](../design/07-authorization-design.md#2-权限层次) / [08数据](../design/08-data-model.md#8-cutoverinspection-与-service-operations) / [09数据库](../design/09-database-design.md#7-cutoverinspection-与服务状态) / [10接口](../design/10-api-design.md#9-cut割接-api) / [11事件](../design/11-event-design.md#6-impacc-与-cut-事件) / [13文件](../design/13-file-design.md) / [15并发](../design/15-cache-and-concurrency.md) / [16异常](../design/16-exception-and-idempotency.md) / [P2契约](phase2-contract-map.md#cut-05) | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |\n| INT-02@V2 | ITR版本同步 | ITR故障入向及ITR来源CUT归档结果出向 | INT-04唯一拥有公告；出向失败不回滚本地归档 | AST（资产管理） | Asset Management | Device / DeviceArchive / RMAReplacement / AssetSyncSnapshot | ITR来源同步批次/映射处理流；不直接改写Device业务状态 | ProjectDeviceScope | AssetApplicationService | Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot | 数据一致性+归属+来源版本 | 全局 | V2 | P0 |  | [01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链) / [02领域](../design/02-domain-model.md) / [02d契约](../design/02d-cross-context-contracts.md) / [04模块](../design/04-module-design.md) / [05状态](../design/05-state-machine.md#2-核心状态机) / [06流程](../design/06-workflow-design.md#2-核心审批流) / [07权限](../design/07-authorization-design.md#2-权限层次) / [08数据](../design/08-data-model.md#9-customerassetcommerce-与-resource) / [09数据库](../design/09-database-design.md#5-asset-设备归属与维保基本事实) / [10接口](../design/10-api-design.md#11-cusastcomres-与-kno-api) / [11事件](../design/11-event-design.md#9-主数据商务资源与知识事件) / [12集成](../design/12-integration-design.md) / [15并发](../design/15-cache-and-concurrency.md) / [16异常](../design/16-exception-and-idempotency.md) / [P2契约](phase2-contract-map.md#int-02) | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |\n| INT-05@V2 | 钉钉/HR/OA集成 | OA领料/外采流程及转包待办链接协作 | OA/钉钉不拥有平台审批与业务状态 | PLT（平台公共能力） | 基础平台能力 / Device Access & Collection | Todo / FileArtifact / AuthorizationGrant / ChangeRequest / DeviceCredential / CredentialGrant / CollectionTask | 公共能力状态机、授权审批流和采集任务授权/回调流 | TenantOrganizationProjectScope / BusinessObjectDeviceCredentialScope | PlatformApplicationService / CollectionOrchestrationService | Todo、FileArtifact、AuthorizationGrant、ChangeRequest、AuditRecord、DeviceCredential、CredentialGrant、CollectionTask、CallbackRecord | 安全+权限+审计+幂等 | 全局 | V2 | P0 |  | [01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链) / [02领域](../design/02-domain-model.md) / [04模块](../design/04-module-design.md) / [05状态](../design/05-state-machine.md#2-核心状态机) / [06流程](../design/06-workflow-design.md#2-核心审批流) / [07权限](../design/07-authorization-design.md#2-权限层次) / [08数据](../design/08-data-model.md#10-analytics基础平台与-knowledge-reference) / [09数据库](../design/09-database-design.md#10-文件事件幂等和状态历史支撑表) / [10接口](../design/10-api-design.md#12-ana-与公共能力-api) / [11事件](../design/11-event-design.md#8-inspection-与-service-事件) / [12集成](../design/12-integration-design.md) / [15并发](../design/15-cache-and-concurrency.md) / [16异常](../design/16-exception-and-idempotency.md) / [P2契约](phase2-contract-map.md#int-05) | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |\n| INT-12@V2 | 设备连接与采集平台集成 | 在线巡检复用统一设备连接与采集契约 | 不重复建设凭证、任务或采集执行引擎 | PLT（平台公共能力） | Device Access & Collection | DeviceCredential / CredentialGrant / CollectionTask / CallbackRecord | 凭证授权与采集任务状态机；任务下发和回调消费流 | BusinessObjectDeviceCredentialScope | CollectionOrchestrationService | DeviceCredential、CredentialGrant、CollectionTask、CallbackRecord | 安全+权限+幂等 | 全局公共能力 | V2 | P0 |  | [01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链) / [02领域](../design/02-domain-model.md) / [02d契约](../design/02d-cross-context-contracts.md) / [04模块](../design/04-module-design.md) / [05状态](../design/05-state-machine.md#2-核心状态机) / [06流程](../design/06-workflow-design.md#2-核心审批流) / [07权限](../design/07-authorization-design.md#2-权限层次) / [08数据](../design/08-data-model.md#11-device-access--collection-数据模型) / [09数据库](../design/09-database-design.md#9-device-access--collection-关键表) / [10接口](../design/10-api-design.md#13-device-access--collection-api) / [11事件](../design/11-event-design.md#7-collection-事件链) / [12集成](../design/12-integration-design.md) / [13文件](../design/13-file-design.md) / [15并发](../design/15-cache-and-concurrency.md) / [16异常](../design/16-exception-and-idempotency.md) / [P2契约](phase2-contract-map.md#int-12) | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |\n| NFR-02@V2 | 设备凭证及巡检非功能需求 | 在线巡检命令超时与规则化后续命令处理 | 当前命令终止失败；后续按冻结的已发布规则决定并留痕 | PLT（平台公共能力） | 基础平台能力 / Device Access & Collection | Todo / FileArtifact / AuthorizationGrant / ChangeRequest / DeviceCredential / CredentialGrant / CollectionTask | 公共能力状态机、授权审批流和采集任务授权/回调流 | TenantOrganizationProjectScope / BusinessObjectDeviceCredentialScope | PlatformApplicationService / CollectionOrchestrationService | Todo、FileArtifact、AuthorizationGrant、ChangeRequest、AuditRecord、DeviceCredential、CredentialGrant、CollectionTask、CallbackRecord | 安全+权限+审计+幂等 | 全局公共安全基线/巡检专项 | V2 | P0 |  | [01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链) / [02领域](../design/02-domain-model.md) / [04模块](../design/04-module-design.md) / [05状态](../design/05-state-machine.md#2-核心状态机) / [06流程](../design/06-workflow-design.md#2-核心审批流) / [07权限](../design/07-authorization-design.md#2-权限层次) / [08数据](../design/08-data-model.md#10-analytics基础平台与-knowledge-reference) / [09数据库](../design/09-database-design.md#10-文件事件幂等和状态历史支撑表) / [10接口](../design/10-api-design.md#12-ana-与公共能力-api) / [11事件](../design/11-event-design.md#7-collection-事件链) / [15并发](../design/15-cache-and-concurrency.md) / [16异常](../design/16-exception-and-idempotency.md) / [P2契约](phase2-contract-map.md#nfr-02) | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |\n'

======================================================================
FAIL: test_requirement_slice_statuses_are_derived_from_feature_coverage_and_tasks (test_generate_requirement_traceability.GenerateRequirementTraceabilityTest.test_requirement_slice_statuses_are_derived_from_feature_coverage_and_tasks)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/repository/scripts/tests/test_generate_requirement_traceability.py", line 167, in test_requirement_slice_statuses_are_derived_from_feature_coverage_and_tasks
    self.assertIn("IMPLEMENTATION_PARTIAL", row)
AssertionError: 'IMPLEMENTATION_PARTIAL' not found in '| PM-01@V1 | 项目创建与指派 | 项目创建与指派的V1主交付业务结果 | V1 | PROJ（项目治理） | 项目治理 | Project / ProjectTask / TaskWorkBinding / ProjectTemplate | Project或Task状态机；WorkBinding统一必填且默认TASK_NATIVE，其他类型按关系装载与事实完成 | ProjectTreeScope | ProjectApplicationService | Project、ProjectTask、TaskWorkBinding、TaskCompletionRule、ProjectTemplate | 业务规则+权限+树查询+统一工作台投影 | S0 | V1 | P0 |  | [01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链) / [02领域](../design/02-domain-model.md) / [04模块](../design/04-module-design.md) / [05状态](../design/05-state-machine.md#2-核心状态机) / [06流程](../design/06-workflow-design.md#2-核心审批流) / [07权限](../design/07-authorization-design.md#2-权限层次) / [08数据](../design/08-data-model.md#4-project-delivery-数据模型) / [09数据库](../design/09-database-design.md#4-project-delivery-表设计) / [10接口](../design/10-api-design.md#5-proj项目治理-api) / [11事件](../design/11-event-design.md#5-projectasset-与-analytics-事件) / [15并发](../design/15-cache-and-concurrency.md) / [16异常](../design/16-exception-and-idempotency.md) / [P2契约](phase2-contract-map.md#pm-01) | [F-PROJ-001](../../specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md)（PARTIAL） | [F-PROJ-001 Task](../../tasks/features/F-PROJ-001.md)（COMPLETE） | NOT_STARTED | REVALIDATION_REQUIRED |'

----------------------------------------------------------------------
Ran 9 tests in 0.989s

FAILED (failures=3)

```

### 未通过检查：`/usr/bin/python3 scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md`

```text
usage: validate_prd_baseline.py [-h] --prd PRD --report REPORT
                                --expected-version EXPECTED_VERSION
                                --expected-status EXPECTED_STATUS
validate_prd_baseline.py: error: the following arguments are required: --report, --expected-version, --expected-status

```

### 未通过检查：`/usr/bin/python3 scripts/validate_prd_semantics.py --prd docs/baseline/prd-v1.8.md`

```text
[FAIL] CLO-01 业务验收标准 MISSING_BOUNDARY_SCENARIO: 第二及后续WHEN未描述边界、权限不足或失败条件
[FAIL] CLO-02 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第3个THEN没有可观察的状态、记录、数据或禁止结果
[FAIL] EXE-03 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第7个THEN没有可观察的状态、记录、数据或禁止结果
[FAIL] EXE-03 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第8个THEN没有可观察的状态、记录、数据或禁止结果
[FAIL] INT-09 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第7个THEN没有可观察的状态、记录、数据或禁止结果
[FAIL] PM-03 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第11个THEN没有可观察的状态、记录、数据或禁止结果
[FAIL] PM-10 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第3个THEN没有可观察的状态、记录、数据或禁止结果
[FAIL] PM-10 业务验收标准 UNOBSERVABLE_ACCEPTANCE: 第4个THEN没有可观察的状态、记录、数据或禁止结果
SUMMARY: 8 semantic issues in 6 requirements

```

### 未通过检查：`/usr/bin/python3 scripts/validate_sds_phase1.py`

```text
[FAIL] docs/design/01-requirement-traceability.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02-domain-model.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02a-context-map.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02b-aggregate-boundary-decisions.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02c-data-ownership-matrix.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02d-cross-context-contracts.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02e-version-scope-matrix.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/04-module-design.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/05-state-machine.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/06-workflow-design.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/07-authorization-design.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] SDS master Phase 1 summary missing markers: ['| SDS Phase 1 | `BASELINE` | `READY_FOR_PHASE_2_V1.8` | `docs/engineering/gates/phase-1/gate-status.md` |']
[FAIL] Phase 1 gate README missing markers: ['APPROVED / READY_FOR_PHASE_2_V1.8', '111个目标版本切片']
[FAIL] cross-context contracts must have unique Producer rows and exact Requirement traceability; malformed=['PROJ阶段推进', 'COM范围追加→PROJ/ACC', 'ACC验收报告Fact', 'CLO终态命令及事件', 'IMP→DAC命令来源', 'CUT P3→DAC→CUT', '外部文件回调'] invalid=['PROJ阶段推进', 'COM范围追加→PROJ/ACC', 'ACC验收报告Fact', 'CLO终态命令及事件', 'CUT P3→DAC→CUT', '外部文件回调'] contractErrors=[] missingLinks=[]
[FAIL] Phase 1 gate missing markers: ['审查状态：`APPROVED`', '结论：`READY_FOR_PHASE_2_V1.8`', '需求方批准：`GO`', '机器门禁：`PASS`']
[FAIL] Phase 1 gate must keep one exact revision 007 APPROVED/READY/GO metadata set without pending claims

```

### 未通过检查：`/usr/bin/python3 scripts/validate_sds_phase2.py`

```text
[FAIL] V1.8 Phase 2 gate conclusion must be: NOT_READY_FOR_PHASE_3_REVISION_007
[FAIL] V1.8 Phase 2 gate missing token: AI-MIG-000
[FAIL] V1.8 Phase 2 contract map missing marker: Phase 3验证注记状态：`REVALIDATION_REQUIRED`
[FAIL] V1.8 Phase 2 contract map missing revision 007 semantics: 甘特展示和受控依赖新增、更新、删除
[FAIL] V1.8 Phase 2 contract map missing revision 007 semantics: V2增加授权清单导出及受控流程跳转配置优化
[FAIL] V1.8 Phase 2 contract map missing revision 007 semantics: V2创建OA领料/外采流程引用
[FAIL] V1.8 Phase 2 contract map missing revision 007 semantics: V2在线巡检复用同一凭证、任务和采集执行引擎
[FAIL] V1.8 Phase 2 contract map missing revision 007 semantics: 后续命令是否继续由任务冻结的已发布规则决定并留痕
[FAIL] Phase 2 migration gate evidence does not match current contract: docs/engineering/gates/phase-2/gate-status.md expected=93对象/104来源绑定/1排除源
[FAIL] V1.8 Phase 2 contract missing physical carrier table: proj_project_template_task_definition
SUMMARY: 10 Phase 2 validation issues

```

### 未通过检查：`/usr/bin/python3 scripts/validate_sds_phase3.py`

```text
[FAIL] Phase 3 gate has invalid review state: REVALIDATION_REQUIRED
[FAIL] Phase 3 gate conclusion mismatch; expected=READY_FOR_SDS_BASELINE_V1.8 actual=None
[FAIL] Phase 3 approved gate missing required token: 111个目标版本切片
[FAIL] 14-security-design.md missing metadata: 文档状态：`BASELINE`
[FAIL] 20-test-design.md missing metadata: 文档状态：`BASELINE`
[FAIL] contract map missing marker: Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`
[FAIL] PM-03 declares unknown domain object: ProjectTemplateVersion
[FAIL] PM-03 declares unknown domain object: StageTransitionDefinition
[FAIL] PM-03 declares unknown domain object: Stage/TaskWorkBinding
[FAIL] PM-03 declares unknown target table for its objects: 既有PROJ模板/阶段/快照承载；图与Stage绑定物理差量见09分册及受影响Feature，未重验证不得声称NO_PHYSICAL_DELTA
[FAIL] PM-11 declares unknown domain object: ProjectStage/ProjectTask
[FAIL] PM-11 declares unknown domain object: Stage/TaskWorkBinding
[FAIL] PM-11 declares unknown domain object: CompletionRule
[FAIL] PM-11 declares unknown domain object: 任务树及依赖
[FAIL] PM-11 declares unknown target table for its objects: proj_project_task
[FAIL] PM-11 declares unknown target table for its objects: proj_project_task_execution_contract
[FAIL] PM-11 declares unknown target table for its objects: proj_project_task_completion_evaluation
[FAIL] PM-11 declares unknown target table for its objects: proj_task_tree_path
[FAIL] PM-11 declares unknown target table for its objects: proj_task_dependency
[FAIL] PM-06 declares unknown domain object: ContractScopeAppendRequest
[FAIL] PM-06 declares unknown domain object: COM ProjectScopeVersion引用
[FAIL] PM-06 declares unknown domain object: 范围差异与逐阶段影响
[FAIL] PM-06 declares unknown target table for its objects: PROJ追加申请及COM唯一范围水位/分配；物理复用/新增/兼容处置见09分册，旧多期群组不作为当前输入
[FAIL] ACC-03 declares unknown domain object: AcceptanceReportRevision
[FAIL] ACC-03 declares unknown domain object: 验收结论及精确范围/来源证据引用
[FAIL] ACC-03 declares unknown target table for its objects: acc_acceptance
[FAIL] ACC-03 declares unknown target table for its objects: acc_acceptance_item
[FAIL] ACC-03 declares unknown target table for its objects: acc_confirmation
[FAIL] Phase 3 gate missing required token: P3-E02
[FAIL] Phase 3 gate missing required token: P3-E03
[FAIL] Phase 3 gate missing required token: P3-E04
[FAIL] Phase 3 gate missing required token: P3-E05
[FAIL] Phase 3 gate missing required token: P3-E06
[FAIL] Phase 3 gate missing required token: P3-E08
[FAIL] Phase 3 gate missing required token: DOWNSTREAM-GATED
[FAIL] Phase 3 gate missing required token: MODEL_BASELINE_READY

```

## 5. 不代表已经关闭的工程门禁

本轮没有执行Java/前端构建、真实MySQL、浏览器E2E、真实外部联调、生产安全扫描或部署。Phase 1/2/3维持REVALIDATION_REQUIRED/BLOCKED_BY_PRD_DELTA，等待各自要求的独立语义复审和证据；静态检查通过不等于SDS整体验收或上线批准。

F-PROJ-008图/目标准入及F-COM-001非S6闭环资格的目标合同已修正，但Provider、持久化和旧测试仍须按新合同验证；不声称NO_PHYSICAL_DELTA或沿用旧PASS。未触及已执行DDL、原Task完成事实、DU认领或master。

## 6. 变更文件

- `"\351\234\200\346\261\202/PRD-\351\241\271\347\233\256\345\256\236\346\226\275\344\272\244\344\273\230\347\256\241\347\220\206\345\271\263\345\217\260.md"`
- `"specs/001-project-delivery-platform/domains/ACC-\351\252\214\346\224\266\344\270\216\351\241\271\347\233\256\351\227\255\347\216\257\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/ANA-\347\273\217\350\220\245\345\210\206\346\236\220\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/CUT-\345\217\230\346\233\264\345\210\207\346\215\242\344\270\216\347\250\263\345\256\232\346\262\273\347\220\206\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/IMP-\347\216\260\345\234\272\345\256\236\346\226\275\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/PLT-\345\271\263\345\217\260\345\205\254\345\205\261\350\203\275\345\212\233\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/PROJ-\351\241\271\347\233\256\346\262\273\347\220\206\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/SOL-\344\272\244\344\273\230\345\207\206\345\244\207\344\270\216\346\226\271\346\241\210\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/SRV-\346\234\215\345\212\241\350\277\220\350\220\245\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `docs/baseline/prd-v1.8.md`
- `docs/design/00-system-detailed-design.md`
- `docs/design/01-requirement-traceability.md`
- `docs/design/02-domain-model.md`
- `docs/design/02a-context-map.md`
- `docs/design/02b-aggregate-boundary-decisions.md`
- `docs/design/02c-data-ownership-matrix.md`
- `docs/design/02d-cross-context-contracts.md`
- `docs/design/02e-version-scope-matrix.md`
- `docs/design/04-module-design.md`
- `docs/design/05-state-machine.md`
- `docs/design/06-workflow-design.md`
- `docs/design/07-authorization-design.md`
- `docs/design/08-data-model.md`
- `docs/design/09-database-design.md`
- `docs/design/10-api-design.md`
- `docs/design/11-event-design.md`
- `docs/design/12-integration-design.md`
- `docs/design/13-file-design.md`
- `docs/design/14-security-design.md`
- `docs/design/15-cache-and-concurrency.md`
- `docs/design/16-exception-and-idempotency.md`
- `docs/design/20-test-design.md`
- `docs/engineering/00-engineering-chain.md`
- `docs/engineering/gates/phase-1/gate-status.md`
- `docs/engineering/gates/phase-1/prd-revision-016-alignment.md`
- `docs/engineering/gates/phase-2/gate-status.md`
- `docs/engineering/gates/phase-3/gate-status.md`
- `docs/traceability/phase2-contract-map.md`
- `docs/traceability/requirement-matrix.md`
- `docs/traceability/requirement-version-coverage.json`
- `scripts/close_prd_revision_016.py`
- `scripts/generate_phase2_contract_map.py`
- `scripts/generate_requirement_traceability.py`
- `scripts/tests/test_prd_revision_016_alignment.py`
- `scripts/validate_prd_revision_016_alignment.py`
- `specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md`
- `specs/features/F-COM-001-project-qualification-contract.json`
- `specs/features/F-PLT-001-unified-file-identity-and-version-management.md`
- `specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
- `specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`
- `specs/features/F-PROJ-005-service-manager-manual-assignment.md`
- `specs/features/F-PROJ-006-project-rollback-exception-close-and-reopen.md`
- `specs/features/F-PROJ-007-project-task-tree-and-native-workbench.md`
- `specs/features/F-PROJ-008-physical-contract.json`
- `specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
- `specs/features/README.md`


## 5. 最终文档复核（修订016）

本节是当前结果；第4节保留首轮失败证据。修正验收条款的可观察输出、跨域契约表的唯一Producer/Consumer结构、生成器绝对/相对路径一致性及原有V2业务义务。未把Gate改为APPROVED/GO，也未执行业务代码、数据库、浏览器、真实集成或部署。

| 命令 | 退出码 | 当前结果 |
|---|---|---|
| `/usr/bin/python3 -m unittest discover -s scripts/tests -p test_generate_requirement_traceability.py -v` | 0 | PASS |
| `/usr/bin/python3 -m unittest discover -s scripts/tests -p test_prd_revision_016_alignment.py -v` | 0 | PASS |
| `/usr/bin/python3 scripts/validate_prd_revision_016_alignment.py` | 0 | PASS |
| `/usr/bin/python3 scripts/validate_prd_semantics.py --prd docs/baseline/prd-v1.8.md` | 0 | PASS |
| `/usr/bin/python3 scripts/validate_prd_domain_generation.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains` | 0 | PASS |
| `/usr/bin/python3 scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --check` | 0 | PASS |
| `/usr/bin/python3 scripts/generate_phase2_contract_map.py --prd docs/baseline/prd-v1.8.md --check` | 0 | PASS |
| `/usr/bin/python3 scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md --report docs/engineering/gates/phase-1/prd-revision-016-alignment.md --expected-version V1.8 --expected-status 正式基线` | 1 | NOT_PASSED |
| `/usr/bin/python3 scripts/validate_sds_phase1.py` | 1 | NOT_PASSED |
| `/usr/bin/python3 scripts/validate_sds_phase2.py` | 1 | NOT_PASSED |
| `/usr/bin/python3 scripts/validate_sds_phase3.py` | 1 | NOT_PASSED |

### 当前未通过：`/usr/bin/python3 scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md --report docs/engineering/gates/phase-1/prd-revision-016-alignment.md --expected-version V1.8 --expected-status 正式基线`

```text
[PASS] 文档版本: 期望 V1.8
[PASS] 文档状态: 期望 正式基线
[PASS] 活动未决标记: 0项
[PASS] 需求编号唯一: 解析100项；重复=无
[PASS] 逐项版本归属: 缺少=无
[PASS] V3与正式正文分离: 仍在详细需求块=无
[PASS] V1/V2字段-用户角色: 缺少0项：无
[PASS] V1/V2字段-目标版本: 缺少0项：无
[PASS] V1/V2字段-业务场景/描述: 缺少0项：无
[PASS] V1/V2字段-核心业务规则: 缺少0项：无
[PASS] V1/V2字段-用户故事: 缺少0项：无
[PASS] V1/V2字段-业务验收标准: 缺少0项：无
[PASS] V1/V2字段-涉及数据: 缺少0项：无
[PASS] V1/V2字段-权限与数据范围: 缺少0项：无
[PASS] V1/V2字段-异常降级留痕: 缺少0项：无
[PASS] V1/V2字段-依赖关系: 缺少0项：无
[PASS] V1/V2验收可观察: 缺少WHEN/THEN=无
[PASS] V1/V2验收归属正确: 验收条款必须位于对应需求块并包含WHEN/THEN
[PASS] 排除能力未进入正式需求: 冲突=无
[PASS] V3演进章节: 要求存在二级章节‘V3演进范围’
[PASS] V3无当前验收承诺: V3只保留目标、范围、前置条件和演进方向
[PASS] 排除清单完整: 缺少=无
[PASS] 正式索引存在: 索引100项
[PASS] 正式索引与正文一致: 索引100项/正文100项
[PASS] 附录A.2正式需求统计一致: 期望={'V1/V2正式需求总数': 100, 'V1主版本需求': 53, 'V2主版本需求': 47}；实际={'V1/V2正式需求总数': 100, 'V1主版本需求': 53, 'V2主版本需求': 47}
[PASS] Requirement目标版本切片完整: 主切片=100；补充=11；总计=111；V1=53；V2=58；重复=无
[PASS] 附录A.2目标版本切片统计一致: 期望={'Requirement目标版本切片总数': 111, 'V1目标版本切片': 53, 'V2目标版本切片': 58}；实际={'Requirement目标版本切片总数': 111, 'V1目标版本切片': 53, 'V2目标版本切片': 58}
[PASS] 文末基线版本一致: 文末应声明PRD V1.8正式基线
[PASS] 文末正式需求数一致: 正文=100项
[PASS] 文末主版本统计一致: V1=53、V2=47
[PASS] 文末目标版本切片统计一致: 期望总计111、V1 53、V2 58
[PASS] INT-12进入正式索引: INT-12必须为V1公共能力
[PASS] 排除编号未进正式索引: WO-07/WO-11仅用于排除追溯
[PASS] V1.8演进统计: 编号V3=31；跨需求=5；缺少=无
[PASS] 配置基础前置与明确延期例外: 动态模板/表单/匹配配置须前置；正文明确延期保持原版本；不得保留未定义V2效率增强
[PASS] V1.8退出需求边界: ACC-05仅进入V3；COM-02/IMP-02不得进入正式或V3索引
[PASS] V1.8项目状态分层: 必需标记=current_stage,lifecycle_status,NORMAL_CLOSED,EXCEPTION_CLOSED,派生展示状态
[PASS] CUT流程-CUT-01核心任务保留: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-CUT-11退出当前范围: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-问卷人工判级: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-P3配置缺口不阻断: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-完整方案轻量校验: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-P5否项驳回: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-专项提前时间自然日: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-保障人员受控修改: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-P6提交即归档: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-无步骤观察扩张: 割接流程必须符合0807流程设计及已确认业务决策
[PASS] CUT流程-无遗留项归档阻断: 割接流程必须符合0807流程设计及已确认业务决策
[FAIL] 工作台-模板定义StageTask绑定: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-不重复配置业务导航: 项目工作区与割接工作台必须符合已确认线框设计
[PASS] 工作台-WorkBinding类型完整: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-WorkBinding统一必填: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-StageTask导航不限制树深: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-通用任务详情基础能力: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-绑定任务按关系执行: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-通用详情不替代绑定业务: 项目工作区与割接工作台必须符合已确认线框设计
[PASS] 工作台-项目概览六页签: 项目工作区与割接工作台必须符合已确认线框设计
[FAIL] 工作台-任务完成按绑定类型判定: 项目工作区与割接工作台必须符合已确认线框设计
[PASS] 工作台-割接入口与五步工作台: 项目工作区与割接工作台必须符合已确认线框设计
[PASS] 工作台-CUT03同一P3工作台: 项目工作区与割接工作台必须符合已确认线框设计
[PASS] 工作台-采集结果不等于业务通过: 项目工作区与割接工作台必须符合已确认线框设计
[PASS] 集成清单分层: 外部系统与平台组件必须分开计数
[PASS] CRM统一命名: SMS不得作为独立外部系统
[PASS] PMS内部边界: PMS不得作为外部系统
[PASS] 采集平台组件边界: 采集平台作为平台组件/子应用
[PASS] 核心对象索引: 缺少=无
[PASS] 附录C不含工单核心对象: 工单已退出V1/V2核心对象；附录C必须与正文3.3一致
[PASS] 附录C满意度核心对象: 必须唯一列出满意度任务与问卷并以正式需求ACC-02为主要来源
[PASS] V1/V2语义质量: 问题0项；需求=无
[PASS] 差异报告PRD版本: 报告应登记V1.8
[PASS] 差异报告SHA-256: 当前=DF80A00713FDF63466DD0660C8ABD898424479E2F309C64EFDCD74C3624F1D2C
SUMMARY: 63 passed, 8 failed, 71 total
```

### 当前未通过：`/usr/bin/python3 scripts/validate_sds_phase1.py`

```text
[FAIL] docs/design/01-requirement-traceability.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02-domain-model.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02a-context-map.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02b-aggregate-boundary-decisions.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02c-data-ownership-matrix.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02d-cross-context-contracts.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/02e-version-scope-matrix.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/04-module-design.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/05-state-machine.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/06-workflow-design.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] docs/design/07-authorization-design.md missing current Phase 1 metadata: > 文档状态：`BASELINE`
[FAIL] SDS master Phase 1 summary missing markers: ['| SDS Phase 1 | `BASELINE` | `READY_FOR_PHASE_2_V1.8` | `docs/engineering/gates/phase-1/gate-status.md` |']
[FAIL] Phase 1 gate README missing markers: ['APPROVED / READY_FOR_PHASE_2_V1.8', '111个目标版本切片']
[FAIL] Phase 1 gate missing markers: ['审查状态：`APPROVED`', '结论：`READY_FOR_PHASE_2_V1.8`', '需求方批准：`GO`', '机器门禁：`PASS`']
[FAIL] Phase 1 gate must keep one exact revision 007 APPROVED/READY/GO metadata set without pending claims
```

### 当前未通过：`/usr/bin/python3 scripts/validate_sds_phase2.py`

```text
[FAIL] V1.8 Phase 2 gate conclusion must be: NOT_READY_FOR_PHASE_3_REVISION_007
[FAIL] V1.8 Phase 2 gate missing token: AI-MIG-000
[FAIL] V1.8 Phase 2 contract map missing marker: Phase 3验证注记状态：`REVALIDATION_REQUIRED`
[FAIL] Phase 2 migration gate evidence does not match current contract: docs/engineering/gates/phase-2/gate-status.md expected=93对象/104来源绑定/1排除源
SUMMARY: 4 Phase 2 validation issues
```

### 当前未通过：`/usr/bin/python3 scripts/validate_sds_phase3.py`

```text
[FAIL] Phase 3 gate has invalid review state: REVALIDATION_REQUIRED
[FAIL] Phase 3 gate conclusion mismatch; expected=READY_FOR_SDS_BASELINE_V1.8 actual=None
[FAIL] Phase 3 approved gate missing required token: 111个目标版本切片
[FAIL] 14-security-design.md missing metadata: 文档状态：`BASELINE`
[FAIL] 20-test-design.md missing metadata: 文档状态：`BASELINE`
[FAIL] contract map missing marker: Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`
[FAIL] PM-03 declares unknown domain object: ProjectTemplateVersion
[FAIL] PM-03 declares unknown domain object: StageTransitionDefinition
[FAIL] PM-03 declares unknown domain object: Stage/TaskWorkBinding
[FAIL] PM-03 declares unknown target table for its objects: 既有proj_project_template_revision
[FAIL] PM-03 declares unknown target table for its objects: proj_project_template_task_definition
[FAIL] PM-03 declares unknown target table for its objects: proj_project_stage_snapshot；图与Stage绑定的复用或新增物理差量见09分册及受影响Feature，未重验证不得声称NO_PHYSICAL_DELTA
[FAIL] PM-11 declares unknown domain object: ProjectStage/ProjectTask
[FAIL] PM-11 declares unknown domain object: Stage/TaskWorkBinding
[FAIL] PM-11 declares unknown domain object: CompletionRule
[FAIL] PM-11 declares unknown domain object: 任务树及依赖
[FAIL] PM-11 declares unknown target table for its objects: proj_project_task
[FAIL] PM-11 declares unknown target table for its objects: proj_project_task_execution_contract
[FAIL] PM-11 declares unknown target table for its objects: proj_project_task_completion_evaluation
[FAIL] PM-11 declares unknown target table for its objects: proj_task_tree_path
[FAIL] PM-11 declares unknown target table for its objects: proj_task_dependency
[FAIL] PM-06 declares unknown domain object: ContractScopeAppendRequest
[FAIL] PM-06 declares unknown domain object: COM ProjectScopeVersion引用
[FAIL] PM-06 declares unknown domain object: 范围差异与逐阶段影响
[FAIL] PM-06 declares unknown target table for its objects: PROJ追加申请及COM唯一范围水位/分配；物理复用/新增/兼容处置见09分册，旧多期群组不作为当前输入
[FAIL] ACC-03 declares unknown domain object: AcceptanceReportRevision
[FAIL] ACC-03 declares unknown domain object: 验收结论及精确范围/来源证据引用
[FAIL] ACC-03 declares unknown target table for its objects: acc_acceptance
[FAIL] ACC-03 declares unknown target table for its objects: acc_acceptance_item
[FAIL] ACC-03 declares unknown target table for its objects: acc_confirmation
[FAIL] Phase 3 gate missing required token: P3-E02
[FAIL] Phase 3 gate missing required token: P3-E03
[FAIL] Phase 3 gate missing required token: P3-E04
[FAIL] Phase 3 gate missing required token: P3-E05
[FAIL] Phase 3 gate missing required token: P3-E06
[FAIL] Phase 3 gate missing required token: P3-E08
[FAIL] Phase 3 gate missing required token: DOWNSTREAM-GATED
[FAIL] Phase 3 gate missing required token: MODEL_BASELINE_READY
```

### 关闭边界

16项PRD整改及对应SDS规则、13领域派生与追溯来源一致性已按本节检查复核。Phase 1/2/3仍为REVALIDATION_REQUIRED / BLOCKED_BY_PRD_DELTA；旧校验器中要求修订007批准的断言不构成本轮放行证据。剩余物理契约、迁移、独立设计复审及运行验收须按当前Gate逐项提交证据。
8个受影响Feature保留Spec差量标记；原Feature Task及Delivery Unit的历史字节保持不变。不把旧FULL+Done投影为新基线COMPLETE。
一次性修订脚本及9个传输片段在本轮成功提交中删除；保留正式生成器、26项静态检查及回归测试。临时远端执行工作流由本轮结束前移除。
