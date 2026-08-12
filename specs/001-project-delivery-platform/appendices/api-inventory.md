# 附录D：接口清单

## 1. 清单定位

本清单用于关联业务需求、接口域和正式契约编号前缀。URI、请求响应、错误码、事件、版本和评审规则统一遵循`api-design-specification.md`；模块所有权遵循`module-boundary-and-naming.md`。

- `API-*`是现有需求规格追溯ID，保持稳定。
- `PMS-*`是正式接口契约编号前缀，详细设计时补充TYPE和SEQ。
- Yudao上游接口作为内部集成基线，不在本清单中重复登记；PMS目标契约必须通过适配层把公司和部门字段统一为`company_*`和`department_*`，不得暴露`org_*`或`organization*`。
- 新增PMS Business API使用`/api/v1/pms/...`。
- 外部系统只能调用明确列入Integration API设计的接口，不默认开放全部Business API。
- Internal API只通过目标模块`-api`暴露，不映射为跨模块数据库访问。

<!-- LANDSCAPE -->

## 2. 接口域

| 追溯ID | 所有模块 | 契约编号前缀 | PMS业务资源根路径 | 用途 | 版本 |
| --- | --- | --- | --- | --- | --- |
| API-PROJ-001 | pms-module-project | PMS-PROJ-PROJECT | /api/v1/pms/projects | 项目创建、修改、查询和状态动作 | V1 |
| API-PROJ-002 | pms-module-project | PMS-PROJ-PROJECT-TREE | /api/v1/pms/projects/tree | 非固定项目树、子树移动和全后代查询 | V1 |
| API-PROJ-003 | pms-module-project | PMS-PROJ-TASK-TREE | /api/v1/pms/project-tasks/tree | 非固定任务WBS、移动、依赖和汇总 | V1 |
| API-PROJ-004 | pms-module-project | PMS-PROJ-PORTFOLIO | /api/v1/pms/portfolios | 项目组合及成员管理 | V2 |
| API-ENG-001 | pms-module-engineering | PMS-ENG-ENGINEERING | /api/v1/pms/engineering | 工勘、方案、到货、安装、配置和联调 | V1/V2 |
| API-CUT-001 | pms-module-cutover | PMS-CUT-CUTOVER | /api/v1/pms/cutover | 割接准备、评审、执行、回退和闭环 | V1/V2 |
| API-SRV-001 | pms-module-service | PMS-SRV-SERVICE | /api/v1/pms/service | 巡检、工单、维保和续保 | V1/V2 |
| API-RES-001 | pms-module-asset | PMS-AST-ASSET | /api/v1/pms/assets | 设备、备件、RMA和技术公告 | V1/V2 |
| API-OUT-001 | pms-module-outsourcing | PMS-OUT-OUTSOURCING | /api/v1/pms/outsourcing | 服务商、转包、结算和付款状态 | V2 |
| API-ANA-001 | pms-module-analytics | PMS-ANA-ANALYTICS | /api/v1/pms/analytics | 组合、进度、质量、风险和资源的只读分析 | V2 |
| API-INT-001 | pms-module-integration | PMS-INT-INTEGRATION | /api/v1/pms/integrations | 映射、同步任务、重试、对账和监控 | V2 |

## 3. 外部系统候选

ITR、CRM、ERP、OA、身份认证和对象存储为候选外部系统。具体权威数据源、同步方向、频率和错误补偿均标记【待确认】；本平台自身PMS数据不属于外部同步。

## 4. 详细设计门禁

每个资源进入开发前，必须将契约编号前缀展开为具体的`QUERY`、`CMD`、`ACTION`或`EVENT`编号，并补齐：

- HTTP方法、完整URI和版本；
- Request/Response Schema；
- 权限标识和数据范围；
- 错误码；
- 幂等、事务、并发和审计策略；
- OpenAPI示例和契约测试；
- 对应REQ、FR和AC编号。

项目树、任务WBS的`maxDepth`只允许作为单次查询保护参数，不得成为固定业务层级上限。
