---
status: Review Baseline
title: PMS 平台模块边界与命名规范
version: v1.0
decision: DEC-013
---

# 附录F：PMS平台模块边界与命名规范

## 1. 文档目的

本文定义项目实施交付管理平台的模块命名、目录组织、领域边界、数据所有权和模块协作方式，作为代码仓库初始化、模块创建、依赖评审和未来服务拆分的强制约束。

适用原则：

- 后端基础工程以`yudao-boot-mini`的`master-jdk25`分支为最简集成起点。
- mini未提供但PMS需要的Yudao模块，从`YunaiV/ruoyi-vue-pro`的同名版本分支按需获取。
- Yudao只提供平台基础能力，不承载PMS业务规则。
- PMS是完整的产品业务域，所有业务模块统一使用`pms-`前缀。
- 业务模块拥有本领域数据，通过API契约或领域事件协作。
- 首期采用模块化单体，由`yudao-server`统一部署。
- 模块边界必须支持未来按业务域独立部署。

## 2. 总体架构

```text
项目实施交付管理平台
├── Yudao Platform：公共框架、身份、权限、流程、文件、基础设施
└── PMS Platform：项目交付、工程、割接、服务、资产、外协、分析、集成
```

Yudao Platform是技术底座，PMS Platform是业务产品边界。PMS业务对象不得因为复用Yudao能力而下沉至平台模块。

上述两层是逻辑边界，不要求额外创建`yudao-platform/`或`pms-platform/`物理父目录。为降低上游同步和版本升级成本，工程根目录保持`yudao-boot-mini`结构，PMS模块以新的根级Maven Module增量加入。

## 3. Yudao平台模块

### 3.1 模块清单

| 模块 | 职责 |
| --- | --- |
| `yudao-dependencies` | Maven BOM与依赖版本集中管理 |
| `yudao-framework` | 公共框架、通用组件及Spring Boot Starter，包括Web、安全、数据访问、缓存、消息、日志和校验等技术支撑 |
| `yudao-module-system` | 用户、公司、部门、角色、菜单、权限及数据权限基础能力 |
| `yudao-module-infra` | 文件、配置、日志、任务、消息及基础设施能力 |
| `yudao-module-bpm` | 流程定义、实例、任务和审批基础能力；从完整仓库同版本分支获取，属于PMS必须追加的扩展，不是mini仓库默认模块 |
| `yudao-server` | 模块化单体启动及装配入口 |

### 3.2 双上游基线

| 用途 | 仓库 | 分支 | 本次核验提交 | 版本属性 |
| --- | --- | --- | --- | --- |
| 最简基础骨架 | `https://gitee.com/yudaocode/yudao-boot-mini.git` | `master-jdk25` | `e6d814cb59cfc204f02aa2516799073382aba801` | revision `2026.06-jdk25-SNAPSHOT`；Java 25；Spring Boot 4.1.0 |
| mini外扩展模块 | `https://github.com/YunaiV/ruoyi-vue-pro.git` | `master-jdk25` | `a6558325b0f09017f531f1e5891613ef9b468132` | revision `2026.06-jdk25-SNAPSHOT`；Java 25；Spring Boot 4.1.0 |

上游依据：

- [yudao-boot-mini仓库](https://gitee.com/yudaocode/yudao-boot-mini)
- [`mini master-jdk25`根POM](https://gitee.com/yudaocode/yudao-boot-mini/blob/master-jdk25/pom.xml)
- [`mini yudao-framework`模块POM](https://gitee.com/yudaocode/yudao-boot-mini/blob/master-jdk25/yudao-framework/pom.xml)
- [YunaiV/ruoyi-vue-pro完整仓库](https://github.com/YunaiV/ruoyi-vue-pro)
- [`完整仓库 master-jdk25`根POM](https://github.com/YunaiV/ruoyi-vue-pro/blob/master-jdk25/pom.xml)
- [`完整仓库 yudao-module-bpm`](https://github.com/YunaiV/ruoyi-vue-pro/tree/master-jdk25/yudao-module-bpm)

mini根POM默认启用`yudao-dependencies`、`yudao-framework`、`yudao-server`、`yudao-module-system`和`yudao-module-infra`。完整仓库的`master-jdk25`包含`yudao-module-bpm`目录，但根POM中该模块仍默认注释；引入PMS工程后必须显式装配和验证。

`yudao-framework`当前包含`yudao-common`以及MyBatis、Redis、Web、Security、WebSocket、Monitor、Protection、Job、MQ、Excel、Tenant、Data Permission、IP等Starter。原则上保留上游框架结构，通过依赖选择使用能力，不复制或改名框架组件。

### 3.3 扩展模块引入规则

- 最简基础骨架及两个仓库的同名共享路径以mini为权威来源；禁止用完整仓库整体覆盖mini。
- mini外模块只从完整仓库的同名版本分支和已记录提交获取。当前V1明确引入`yudao-module-bpm`；后续新增模块必须形成范围和依赖评审。
- 每个扩展模块必须作为完整迁移单元处理，包括模块源码、根POM注册、`yudao-server`依赖、依赖管理差异、数据库脚本、菜单权限数据、配置项，以及对应上游接口和管理端页面增量。
- 若扩展模块依赖完整仓库对`yudao-dependencies`、`yudao-framework`、system、infra或server的新增修改，只允许按文件差异提取最小兼容补丁，并记录来源提交、文件清单、原因和验证结果。
- 两个上游提交必须同时写入项目版本清单；版本属性相同只是准入条件，不能替代编译、迁移、启动和流程回归验证。
- 上游升级必须先升级mini基线，再重新选择完整仓库兼容提交并重做差异评审，不允许独立滚动某一来源后直接发布。

### 3.4 平台边界

Yudao平台模块禁止拥有以下PMS业务对象及其业务规则：

- 项目、项目组合、项目层级、任务WBS；
- 工勘、方案、到货、安装、配置和联调；
- 割接、回退和稳定观察；
- 巡检、维保、续保和服务工单；
- 设备、备件、RMA和技术公告；
- 服务商、转包、结算和付款状态。

平台模块可以提供公共框架、身份、权限、流程、文件、通知和审计等通用能力，但不得以通用表或通用Service替代业务模块的数据所有权。

`yudao-framework`只提供可复用技术能力和Starter，不定义项目、任务、设备、割接、巡检等PMS业务模型，也不得形成绕过业务模块API的“通用业务Service”。

## 4. PMS业务模块

| 领域代码 | Maven模块 | 数据所有权与核心职责 |
| --- | --- | --- |
| `PMS-PROJ` | `pms-module-project` | 项目承接、项目团队、项目组合、项目层级、任务WBS、里程碑、风险、问题、验收与闭环 |
| `PMS-ENG` | `pms-module-engineering` | 工勘、实施方案、到货、安装、配置和业务联调 |
| `PMS-CUT` | `pms-module-cutover` | 割接准备、风险评估、方案审批、执行、回退和稳定观察 |
| `PMS-SRV` | `pms-module-service` | 巡检、服务工单、维保和续保 |
| `PMS-AST` | `pms-module-asset` | 设备、备件、RMA和技术公告 |
| `PMS-OUT` | `pms-module-outsourcing` | 服务商、转包、合同、结算和付款状态 |
| `PMS-ANA` | `pms-module-analytics` | 项目组合、进度、风险、质量和资源的只读分析 |
| `PMS-INT` | `pms-module-integration` | ERP、CRM、OA等外部集成、字段映射、同步、重试、对账和监控 |

### 4.1 分析域限制

`pms-module-analytics`：

- 只读消费业务数据或事件；
- 可以保存可重建的指标快照、汇总和分析模型；
- 不拥有项目、任务、设备等业务主数据；
- 不参与业务事务，不得反向修改业务状态。

### 4.2 集成域限制

`pms-module-integration`拥有映射、同步批次、游标、失败明细、补偿任务和对账结果，不复制无必要的业务主表。外部数据写入业务域时，必须调用目标业务模块API，不能直接写目标模块数据库。

## 5. 目标目录结构

```text
project-delivery-platform/
├── yudao-dependencies/
├── yudao-framework/
├── yudao-module-system/
├── yudao-module-infra/
├── yudao-module-bpm/                 # 从完整仓库同版本分支获取
├── pms-module-project/
│   ├── pms-module-project-api/
│   └── pms-module-project-biz/
├── pms-module-engineering/
│   ├── pms-module-engineering-api/
│   └── pms-module-engineering-biz/
├── pms-module-cutover/
├── pms-module-service/
├── pms-module-asset/
├── pms-module-outsourcing/
├── pms-module-analytics/
├── pms-module-integration/
├── yudao-server/
└── yudao-ui/
    └── yudao-ui-admin-vue3/          # mini基础集成及完整仓库扩展模块增量
```

每个可被其他模块调用的业务模块应提供独立的`-api`子模块；实现、数据库访问和内部Service放在`-biz`子模块。没有跨模块契约的模块也应保留未来拆分时创建`-api`子模块的能力。

管理端采用官方`yudao-ui-admin-vue3`作为Vue3 + Element Plus基础前端。mini仓库的`yudao-ui/yudao-ui-admin-vue3`主要保存集成API和页面增量，完整前端来源以该目录README指向的官方前端仓库为准。【待确认】代码落地时采用前后端同库还是独立仓库管理。

## 6. 模块调用规范

### 6.1 允许的调用

```text
pms-module-engineering-biz
        ↓
pms-module-project-api
        ↓
pms-module-project-biz
```

允许的协作方式：

- 调用目标模块`-api`暴露的应用接口；
- 发布或订阅版本化领域事件；
- 通过集成模块与外部系统交换数据；
- 通过Yudao平台API使用通用能力。

### 6.2 禁止的调用

禁止：

- 业务模块直接依赖其他业务模块的`-biz`；
- 直接调用其他模块内部Service、Mapper或Repository；
- 直接访问、联表更新或共享其他模块业务表；
- 在公共模块中共享业务DO、Mapper或数据库表结构；
- 通过前端绕过目标模块API修改业务状态。

### 6.3 契约内容

`-api`模块允许包含：

- API Interface；
- Request DTO、Response DTO；
- Command、Query；
- 对外稳定的Enum和事件契约。

`-api`模块禁止包含：

- DO或Entity；
- Mapper、Repository；
- 数据库表结构；
- 仅服务于目标模块内部实现的工具类。

## 7. 数据所有权与事务边界

- 每张业务表必须具有唯一归属模块。
- 单模块事务只能原子修改本模块拥有的数据。
- 跨模块流程采用API编排、事件驱动或可补偿流程，不使用跨模块共享事务掩盖边界。
- 调用方只能保存目标对象的标识和必要快照，不复制目标模块完整主数据。
- 所有跨模块写操作必须具备幂等、失败重试、审计和对账策略。

## 8. 命名规范

### 8.1 模块命名

```text
pms-module-{domain}
pms-module-{domain}-api
pms-module-{domain}-biz
```

禁止新建承载PMS业务的`yudao-module-*`模块。

### 8.2 领域代码

| 领域 | 代码 |
| --- | --- |
| 项目 | `PMS-PROJ` |
| 工程 | `PMS-ENG` |
| 割接 | `PMS-CUT` |
| 服务 | `PMS-SRV` |
| 资产 | `PMS-AST` |
| 外协 | `PMS-OUT` |
| 分析 | `PMS-ANA` |
| 集成 | `PMS-INT` |

### 8.3 API契约编号

```text
{DOMAIN}-{RESOURCE}-{TYPE}-{SEQ}
```

示例：

```text
PMS-PROJ-PROJECT-CMD-001
PMS-ENG-INSTALLATION-CMD-001
PMS-CUT-CUTOVER-ACTION-001
```

具体TYPE、URI、请求响应和版本规则见`api-design-specification.md`。

## 9. 依赖与边界评审清单

- [ ] 模块名称是否使用正确的`yudao-`或`pms-`前缀？
- [ ] 业务对象是否归属唯一业务模块？
- [ ] 是否仅依赖目标模块`-api`而非`-biz`？
- [ ] 是否避免跨模块直接访问数据库？
- [ ] 写操作是否明确事务边界、幂等、审计和补偿？
- [ ] 分析模块是否保持只读？
- [ ] 集成模块是否通过目标模块API落业务数据？
- [ ] API和事件契约是否具备稳定编号与版本？
- [ ] 拆分为独立服务后是否仍能保持相同数据所有权和契约？

## 10. 验收标准

- 新增PMS业务模块全部采用`pms-module-*`命名。
- 架构依赖检查不得出现业务模块直接依赖其他模块`-biz`的情况。
- 数据库表、Mapper和Service均能追溯到唯一领域所有者。
- 跨模块集成测试只通过API或事件完成协作。
- 模块独立部署演练不要求修改对外契约或迁移其他模块业务表。
