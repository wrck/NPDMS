# NPDMS规格基线锁定与选择性同步设计

> **文档状态：** IN_REVIEW
> **设计日期：** 2026-08-14
> **适用基线：** PRD V1.7、工程链 V1.8、SDS Phase 3 Baseline
> **目标仓库：** `E:\AICoding\Projects\NPDMS`
> **文档属性：** 工程实施设计，不改变PRD业务语义、版本范围或数据模型裁决

## 1. 背景与问题

规格仓库已形成PRD V1.7、工程链V1.8、01～20 SDS、13领域规格、需求追溯和P3-E09数据模型基线。NPDMS是独立实现仓库，但其仓库规则仍把本地旧`specs/`、`tasks/plan.md`和`tasks/todo.md`声明为实现事实源。

只读审计确认，拟供实现读取的109个正式资产中，NPDMS仅12个文件与规格仓库一致，47个内容漂移，50个缺失；缺失项包括PRD V1.7、08～20 SDS、关键ADR、追溯合同和数据模型合同。如果不先校准事实源，新Feature会继续引用旧FR、旧`pms_`目标表和已排除业务。

## 2. 设计目标

1. 规格仓库继续作为业务需求和系统设计的唯一事实源；
2. NPDMS保存可离线读取、可由Git复现的规格快照；
3. 每次同步绑定一个完整规格提交和逐文件SHA-256；
4. 同步范围使用显式允许清单，不复制整仓或中间过程文档；
5. NPDMS可在Feature Ready前自动识别快照缺失、漂移和来源不一致；
6. 用最少机制解决事实源漂移，不建立外部签署、审批附件或第二套Git元数据系统。

## 3. 非目标

- 不把NPDMS变成新的需求维护入口；
- 不反向同步NPDMS文档到规格仓库；
- 不同步评审草稿、门禁中间证据、历史归档、外部输入或工程计划；
- 不因同步自动修改PRD、SDS、Feature状态或门禁结论；
- 不自动删除NPDMS存量代码、Flyway迁移、菜单或测试数据；
- 不授权历史数据迁移、数据切换或生产发布；
- 不把本任务包装成第四个业务Feature。

## 4. 事实源与优先级

同步后的实现读取顺序为：

```text
规格仓库锁定提交中的PRD
  > 工程链与批准ADR
  > SDS与领域规格
  > Feature Spec
  > Implementation Plan / Task
  > NPDMS代码与测试
```

NPDMS中的同步文件是锁定提交的只读快照，不拥有独立业务语义。发生冲突时必须先回到规格仓库处理，再生成新的快照；不得直接修改NPDMS快照掩盖差异。

## 5. 选择性同步范围

### 5.1 核心实现输入

核心包当前为87个文件，约2.38 MB：

- `docs/README.md`；
- `docs/engineering/00-engineering-chain.md`；
- `docs/baseline/prd-v1.7.md`、`requirement-baseline.yaml`；
- `docs/design/`当前正式SDS分册；
- `docs/decisions/`当前ADR和开放问题；
- `docs/traceability/`正式追溯与机器合同；
- `specs/001-project-delivery-platform/00-master-spec.md`；
- 13份当前领域需求规格。

### 5.2 数据模型与迁移设计输入

模型包当前为22个文件，约2.28 MB：

- `specs/001-project-delivery-platform/appendices/`正式业务、权限、状态、API、物理模型和迁移设计附件；
- `ddl-item-decision-register.json`；
- `target-field-catalog.jsonl`。

总计109个文件、约4.66 MB。同步实现必须由版本化允许清单生成精确列表，不得只依赖目录通配符。后续正式文件新增或移除时更新允许清单并经过正常Git评审，不要求反复修改本设计正文中的审计快照数字。

## 6. NPDMS新增工程资产

| 资产 | 职责 |
|---|---|
| `docs/specification-baseline/allowlist.json` | 以排序后的相对路径和类别定义允许同步的正式资产，不接受目录通配符 |
| `docs/specification-baseline/manifest.json` | 保存规格仓库标识、锁定提交及109个文件的路径、类别和SHA-256 |
| `docs/specification-baseline/README.md` | 说明快照只读边界、同步命令、异常处理和升级方式 |
| `scripts/sync_specification_baseline.py` | 从显式规格提交读取允许清单文件；默认只检查，显式`--apply`才写入 |
| `scripts/validate_specification_baseline.py` | 离线校验本地快照与manifest精确一致 |
| `scripts/tests/test_specification_baseline.py` | 覆盖正常同步、缺文件、内容漂移、路径越界和错误提交等回归场景 |

manifest保持轻量，最小字段如下；示例哈希只用于表达长度和格式，不代表真实基线值：

```json
{
  "schemaVersion": 1,
  "source": {
    "repositoryId": "project-delivery-platform-spec",
    "commit": "0123456789abcdef0123456789abcdef01234567"
  },
  "files": [
    {
      "path": "docs/baseline/prd-v1.7.md",
      "category": "BASELINE",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ]
}
```

不在manifest中保存本机绝对路径、签署人、批准附件、二次提交状态或环境实例值。源仓库路径通过命令参数传入，Git提交和文件哈希已经提供可复现性。

## 7. 同步流程

```text
指定规格仓库与40位sourceCommit
  → 验证提交对象存在
  → 从Git对象读取允许清单文件
  → 计算并排序逐文件SHA-256
  → 检查目标路径不越出允许根目录
  → 预览新增、替换和冲突清单
  → 显式--apply写入NPDMS
  → 生成manifest
  → 离线validator复算
  → 独立提交规格快照
```

同步读取指定Git提交，不读取源工作区未提交内容。若源工作区对允许清单中的已跟踪文件存在未提交修改，命令必须提示并拒绝把`HEAD`隐式当作同步提交；未跟踪外部资料及允许清单外的过程文件不参与同步。

目标文件已有本地修改时，默认检查模式报告冲突；`--apply`也不得静默覆盖未提交修改。操作者必须先提交、还原或明确处理目标差异。

## 8. 仓库规则调整

NPDMS根`AGENTS.md`保留JDK 25、模块化单体、基础平台、宿主机前后端和真实浏览器验收等实现约束，同时修正事实源描述：

1. 本地规格快照是锁定输入，不是独立事实源；
2. manifest中的`source.commit`决定当前业务与设计版本；
3. 修改设计或代码前仍按工程链读取PRD、工程链、相关SDS、Feature Spec和Task；
4. 旧`tasks/plan.md`和`tasks/todo.md`标记为`SUPERSEDED`，只保留历史追溯；
5. 新任务只从当前Feature Spec生成，不继续执行旧FR任务。

## 9. 存量实现分类与处理边界

规格同步完成后，存量代码按以下四类登记：

| 分类 | 含义 | 当前实例 |
|---|---|---|
| `CURRENT_52` | 属于九月首发52项，可进入Feature复用或重构 | 项目、客户、现场实施、割接主任务、验收闭环等经逐项核对的实现 |
| `VALID_V2_POSTPONED` | 需求合法但不进入九月首发 | `SrvReport`对应`INS-05` |
| `EXCLUDED_CURRENT` | 已明确排除或退出当前领域 | `CutExecution`、`CutObservation`的逐步骤执行和稳定观察语义 |
| `SEMANTIC_REWORK` | 部分字段或能力可复用，但当前Owner或生命周期错误 | `SrvMaintenance`、`MaintenanceTransition` |

具体规则：

- `CutExecution/CutObservation`不得继续作为割接写模型；只有能逐字段证明属于P6闭环的结果事实才可迁入`CutoverClosure`；
- `SrvMaintenance`停止独立维保菜单/API和经营生命周期，可证明的客观维保事实归AST；
- `SrvReport`保留为V2巡检报告资产，但不进入首发菜单、Feature和UAT；
- `MaintenanceTransition`不原样沿用，交接事实重构为`ServiceHandover`，续保经营字段隔离；
- 基础平台MES生产工单不属于本次PMS业务排除范围，不得按关键词误删；
- 禁止仅改类名、表名或页面标题冒充业务语义对齐。

## 10. 失败与阻断规则

| 情况 | 处理 |
|---|---|
| sourceCommit不存在、不是40位提交或无法读取 | 同步失败，不生成manifest |
| 允许清单文件在源提交中缺失 | 同步失败并列出精确路径 |
| 目标路径越出登记根目录 | 同步失败，禁止写入 |
| NPDMS快照文件缺失或SHA不一致 | `validate`失败，阻断Feature Ready |
| NPDMS直接修改快照 | `validate`失败；必须先回到规格仓库变更并重新同步 |
| 发现目标仓库未登记的旧正式资产 | 输出冲突清单，进入基线对齐任务；不自动删除 |
| 团队、UAT负责人、联调窗口、生产参数未确定 | 按九月计划最晚安全点后置，不阻断本次同步 |
| 历史迁移范围未确定 | 不阻断Feature设计；继续阻断迁移程序、演练和数据切换 |

## 11. 测试设计

### 11.1 正向测试

- 从真实锁定提交生成109项manifest；
- 新增50个缺失文件、替换47个漂移文件、保持12个相同文件；
- 离线validator复算路径、数量和SHA全部一致；
- NPDMS在不访问源仓库时仍可读取完整PRD、工程链、相关SDS和追溯合同；
- 重复同步同一提交不产生Git差异。

### 11.2 负向测试

- 错误或短提交ID必须失败；
- 删除一个快照文件必须失败；
- 修改一个字节但保留manifest必须失败；
- manifest包含`../`、绝对路径或允许根之外路径必须失败；
- 目标快照存在未提交修改时`--apply`必须拒绝覆盖；
- 允许清单中混入`docs/engineering/gates/`、`docs/superpowers/plans/`、`archive/`或外部输入时必须失败；
- Feature引用OUT_OF_SCOPE、V3或未登记Requirement时必须由现有追溯校验拒绝。

## 12. 实施拆分

实施按可独立回退的提交拆分：

1. NPDMS增加allowlist、manifest、同步/校验脚本及测试；
2. 从规格仓库锁定提交同步109个正式资产；
3. 合并调整NPDMS `AGENTS.md`，将旧任务计划标记为`SUPERSEDED`；
4. 生成存量代码四分类清单；
5. 按领域分别纠偏已排除和语义错误对象；
6. 完成基线校验、后端构建、前端适用检查和追溯检查；
7. 再进入三个首发Feature的Spec与薄切片实现。

规格同步、仓库规则调整和业务代码纠偏不得合并为一个大提交。任何代码删除或Flyway前向迁移均在后续实施计划中列出精确文件、测试和回退方式。

## 13. 验收标准

1. NPDMS提交中存在可验证的规格manifest，绑定真实规格提交；
2. 109个登记文件路径、数量和SHA-256全部通过；
3. NPDMS仓库规则不再把漂移的本地文档声明为独立事实源；
4. 旧任务计划已明确失效，不再驱动新开发；
5. 存量实现四分类完整且逐项绑定当前Requirement；
6. 已排除业务和错误割接状态机不再进入首发菜单、API和前向迁移；
7. V2合法资产未被误删，也未进入首发验收范围；
8. 整个过程不读取或提交受保护的外部割接分析文档，不改变PRD业务语义。
