# NPDMS实现基线状态

## 当前入口（2026-09-08复核）

本文件是现状导航，不维护独立Feature Ready、Implementation Done或全局准入状态。当前唯一事实源已是本仓库，不再由外部规格快照、109文件清单或8月15日全局阻断驱动开发。

| 维度 | 当前权威与证据 |
|---|---|
| 业务与工程基线 | [PRD V1.8修订018](../docs/baseline/prd-v1.8.md)、[工程链](../docs/engineering/00-engineering-chain.md)；采用变更级实质审查 |
| 逐需求代码现状 | [2026-09-08审查及回执](delivery-units/DU-20260908-REQUIREMENT-IMPLEMENTATION-REVIEW.md#逐需求证据)：100项正式需求、111切片，区分真实实现、受控增量、旧功能与缺口 |
| Feature Ready | [Feature Spec入口](../specs/features/README.md)，以具体Spec为准；不再给出全局Feature Ready=NO |
| 实施进度与Done | [当前Feature Task](features/README.md)，历史合法子闭环Done不外推为当前Requirement完整实现 |
| Requirement覆盖 | [生成矩阵](../docs/traceability/requirement-matrix.md)、[结构化投影](../docs/traceability/requirement-version-coverage.json)；NOT_STARTED表示正式覆盖未启动，不等于仓库没有代码 |
| 复用与退役边界 | [存量实现清单](implementation-baseline-inventory.md)；机器分类不代替功能完成判断 |
| 排他写入与集成 | [Delivery Unit投影](delivery-units/README.md)，以master的DU记录为准 |

此前逐需求审查是源码、装配、契约、测试及已有运行证据审查，不是新的全业务运行验收；该轮未运行应用、MySQL、浏览器或外部系统，不产生Deployment/SIT/UAT/Release结论。后续基础设施恢复另见下方回执。当前实质缺口及每项可复用代码只在逐需求审查证据中记录，不以入口文案制造第二套状态。

## 当前优先：项目交付主线（2026-09-08）

用户已明确先做好开发前准备和状态检查，再分析项目交付主线。本轮[主线准备与任务分析](../docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md)覆盖S0～S6参考全集及八条关键子流程；第一准备单元围绕已解决设计的PM-01首次项目经理指派，同时按独立范围收口模板—ACC—COM合同，不从既有CUT/INS分支反向选择主线。

工具链、主线模块编译和DU护栏检查已取得结果；用户授权并确认使用窗口后，[固定测试环境已重建并验证](delivery-units/DU-20260908-TEST-INFRASTRUCTURE-RESET.md)。具体迁移、连接、凭据来源及删除边界只在该回执记录；应用/业务/UI验收与各Feature正式契约仍按其实际范围继续准备。CUT认领已释放且工作树保留；INS Task 9在80f2b58a后由独立TRAE会话认领，本任务不改其代码或认领。

## 此前准备的独立代码单元（按当前DU执行）

[开发准入复验](delivery-units/DU-20260908-DEVELOPMENT-ADMISSION-REVALIDATION.md)承接上述完整审查。原DU格式诊断已按历史Git/正文证据补齐元数据，未放宽校验器；原10条错误的报告继续作为当时记录保留。

- CUT配置基础下一代码单元：[F-CUT-001 Task 4](delivery-units/DU-20260908-FCUT001-EXAMPLE-SEED.md)，完整示例初始化；原V133候选编号不再用于新主干写入，最终版本在集成窗口确定。
- 巡检规则基础下一代码单元：[F-INS-001 Task 9](delivery-units/DU-20260908-FINS001-TASK9.md)，服务端认证与授权设备范围驱动的规则只读选择。与CUT初始化互不依赖，不扩成巡检运行时或第三方连接器。
- 两项认领状态、Owner和精确路径只读各自DU；编码前必须已经激活且目标分支包含实际认领提交。这里不另存一套“可开发”状态，也不将准备好的入口写成Task完成。
- F-INT-012后续代码仍须先收敛唯一计划中的下一未完成Task；F-AST-001仍须完成当前master的运行Done复验。这些相关缺口不作为上述两项编码的全局前置。
- 其他Requirement的NOT_STARTED或REVALIDATION_REQUIRED保持原义，不因存在可开发单元而整批放行；跨Owner契约缺口仍按其具体范围阻断。

## 2026-08-15历史记录（不再作为当前准入）

以下原表、提交、验证和阻断描述保留为当时的历史事实；其“当前”仅指2026-08-15，不适用于当前工程链。

| 项目 | 当前值 |
|---|---|
| 规格提交 | `b7c9d2a8de04391637aef942bc200ff43aec2122` |
| 快照文件 | 109 |
| 规格校验 | PASS |
| 工程入口迁移 | PASS |
| 存量实现状态 | `BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED` |
| Feature Ready | NO |

### 2026-08-15实施提交

| 顺序 | 提交 | 内容 |
|---|---|---|
| 1 | `e6f97e5` | 建立规格基线同步、校验与冲突保护工具 |
| 2 | `c3ed12f` | 锁定来源提交为109文件的本地规格快照 |
| 3 | `0fc43eb` | 将工程入口切换到锁定规格基线 |
| 4 | `7dd3baf` | 登记存量实现差异和后续纠偏入口 |

### 2026-08-15验证结果

| 校验项 | 结果 | 说明 |
|---|---|---|
| Python自动化测试 | PASS | `33/33` |
| 规格快照校验 | PASS | 清单、来源提交与文件摘要一致 |
| 工程基线规则校验 | PASS | 正式工程入口和历史任务边界一致 |
| 存量实现清单校验 | PASS | 受控实现面均有唯一分类和需求引用 |
| 后端完整构建 | PASS | JDK `25.0.1`，`mvn clean verify`，30个模块 |
| 前端锁定依赖安装 | PASS | pnpm `9.15.5`，`--frozen-lockfile` |
| 前端生产构建 | PASS | `build:prod`；既有环境变量和CSS兼容性警告不影响产物生成 |
| Git差异格式校验 | PASS | `git diff --check` |

当前阻断来自真实实现语义差异，不来自团队、UAT负责人、部署参数或生产环境。已确认的`EXCLUDED_CURRENT`与`SEMANTIC_REWORK`对象尚未完成代码、菜单、API和前向迁移纠偏，因此不得开始新的首发Feature实现。

后续按三个独立计划处理：

1. `npdms-cutover-current-model-correction`；
2. `npdms-asset-maintenance-fact-rework`；
3. `npdms-service-handover-rework`。

`INS-05/SrvReport`保留为V2后置能力，不生成九月首发任务。
