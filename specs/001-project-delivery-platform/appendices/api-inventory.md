# 附录D：接口清单

## 1. API设计原则

- API路径、请求、响应、错误码和事件结构必须版本化。
- 所有写接口明确权限、幂等键、乐观锁、事务边界和审计。
- 分页、树查询和导出分别使用适合的数据结构，禁止一次返回全量500万任务。
- 模块内部API不得暴露数据库DO或允许调用方修改本模块业务表。

<!-- LANDSCAPE -->

## 2. 接口域

| 编号 | 领域 | 建议路径 | 用途 | 版本 |
| --- | --- | --- | --- | --- |
| API-PROJ-001 | PMS | /admin-api/pms/project | 项目创建、修改、查询和状态动作 | V1 |
| API-PROJ-002 | PMS | /admin-api/pms/project-tree | 非固定项目树、移动和全后代查询 | V1 |
| API-PROJ-003 | PMS | /admin-api/pms/task-tree | 非固定任务WBS、移动、依赖和汇总 | V1 |
| API-PROJ-004 | PMS | /admin-api/pms/portfolio | 项目组合及成员管理 | V2 |
| API-ENG-001 | 工程实施 | /admin-api/engineering | 工勘、方案、到货、安装、配置和联调 | V1/V2 |
| API-CUT-001 | 割接 | /admin-api/cutover | 割接准备、评审、执行、回退和闭环 | V1/V2 |
| API-SRV-001 | 持续服务 | /admin-api/service | 巡检、工单、维保和续保 | V1/V2 |
| API-RES-001 | 资产外协 | /admin-api/asset | 设备、备件、RMA和公告 | V1/V2 |
| API-OUT-001 | 外协 | /admin-api/outsourcing | 服务商、转包、结算和付款状态 | V2 |
| API-ANA-001 | 分析 | /admin-api/pms-analytics | 组合、进度、质量、风险和资源分析 | V2 |
| API-INT-001 | 集成 | /admin-api/pms-integration | 映射、同步任务、重试、对账和监控 | V2 |

## 3. 外部系统候选

ITR、CRM、ERP、OA、身份认证和对象存储为候选外部系统。具体权威数据源、同步方向、频率和错误补偿均标记【待确认】；本平台自身PMS数据不属于外部同步。
