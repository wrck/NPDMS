# ADR-001：实施执行域业务命名统一

Status: Accepted

## Decision

`Field Execution` → `Implementation Execution`（实施执行域）  
`FieldQualityCheck` → `ImplementationQualityCheck`  
`FieldSafetyCheck` → `ImplementationSafetyCheck`

## Reason

1. `Field` 在软件工程上下文中容易被理解为字段，而不是现场实施。
2. PRD 的业务语言使用“实施、实施部署、实施执行”。
3. 该 Context 不仅包含现场动作，还包含配置结果解释、业务联调、实施风险和实施证据。
4. `Implementation Execution` 更准确表达 bounded context 的业务职责。

## Scope

- 活动 SDS、Context Map、聚合、状态机、工作流、权限和追溯矩阵
- 追溯矩阵生成器及后续代码/模块命名

## Excluded

- database/form/API 中表示“字段”的 `field` 技术语义
- 已形成的历史评审证据、签署记录和基线快照原文
- 本 ADR 不改变 PRD 业务范围、版本或验收标准

## Aliases

- `Field Execution`：历史名称，仅用于历史证据追溯
- `Implementation Execution`：当前规范名称
