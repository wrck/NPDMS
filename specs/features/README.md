# Feature Spec 索引

本目录保存经SDS基线派生的正式Feature Spec。Feature Spec只拆解已批准需求与设计，不得改变PRD业务语义、领域Owner、权限或状态模型。

| Feature | 名称 | Requirement | 规格状态 | Feature Ready | 实施状态 |
|---|---|---|---|---|---|
| [F-PROJ-001](F-PROJ-001-manual-project-creation-and-template-initialization.md) | 手动项目创建与模板初始化 | PM-01、PM-03 | BASELINE | READY | IMPLEMENTATION_COMPLETE（NPDMS `1c76050`） |
| [F-PROJ-002](F-PROJ-002-project-split-tree-and-progress-aggregation.md) | 项目拆分、项目树与进度汇总 | PM-02 | BASELINE | READY | IMPLEMENTATION_COMPLETE（NPDMS `57923b1`） |
| [F-PROJ-003](F-PROJ-003-project-subtree-authorization-and-unified-scope.md) | 项目子树授权与统一数据范围 | PM-04 | BASELINE | READY | IMPLEMENTATION_COMPLETE（NPDMS `9ab894f`） |
| [F-PROJ-004](F-PROJ-004-project-business-attribute-classification.md) | 项目业务属性判定、模板匹配历史与影响识别 | PM-07 | BASELINE | READY（`NPDMS-FPROJ004-FEATURE-READY-20260825-06`） | IMPLEMENTATION_COMPLETE（`NPDMS-FPROJ004-IMPLEMENTATION-DONE-20260825-07`；仅PROJ子切片） |
| [F-PROJ-005](F-PROJ-005-service-manager-manual-assignment.md) | 服务经理人工指派与责任分布 | PM-08（V1） | BASELINE | READY（`NPDMS-FPROJ005-FEATURE-READY-20260825-01`） | NOT_STARTED |

状态和门禁遵循`docs/engineering/00-engineering-chain.md`：只有Requirement追溯、业务规则、状态、权限、API、数据变化、验收标准及相关Open Question全部关闭后，Feature才可进入Implementation。
