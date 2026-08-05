# 项目实施交付管理平台规格索引

## 规格文件

- `00-master-spec.md`
- `domains/PLT-public-platform-capabilities-srs.md`
- `domains/CUS-customer-and-service-relationship-srs.md`
- `domains/PROJ-project-governance-srs.md`
- `domains/COM-contract-and-order-fulfillment-srs.md`
- `domains/SOL-delivery-preparation-and-solution-srs.md`
- `domains/IMP-field-implementation-srs.md`
- `domains/CUT-change-cutover-and-stability-srs.md`
- `domains/ACC-acceptance-and-project-closure-srs.md`
- `domains/AST-asset-management-srs.md`
- `domains/RES-resource-and-outsourcing-srs.md`
- `domains/SRV-service-operations-srs.md`
- `domains/KNO-technical-knowledge-governance-srs.md`
- `domains/ANA-business-analytics-srs.md`
- `domains/项目实施交付管理平台规格体系调整原则（13领域版）.md`
- `appendices/requirement-migration.md`
- `appendices/data-dictionary.md`
- `appendices/state-machines.md`
- `appendices/permission-matrix.md`
- `appendices/api-inventory.md`
- `appendices/acceptance-traceability.md`
- `appendices/module-boundary-and-naming.md`
- `appendices/api-design-specification.md`
- `appendices/project-order-model-options-review.md`
- `appendices/project-order-target-schema-evidence.md`
- `appendices/project-order-physical-schema.mysql.sql`
- `appendices/project-order-migration-mapping.md`
- `appendices/table-structure-business-rule-traceability.md`

## 基线说明

- 本目录是已确认的SPECIFY阶段基线。
- `00-master-spec.md`负责系统场景和跨领域编排，13份领域SRS负责唯一Owner业务定义。
- 145项正式FR全部按唯一Owner迁移；FR、BR、DR、AC保留历史编号，编号前缀不代表当前Owner。
- `FR-ENG-021 到货签收管理`归属`IMP 现场实施`；`AST 资产管理`消费实施结果并维护资产档案，不重复定义签收流程。
- 原`01-`至`08-`分卷保留为迁移来源和证据，不再作为新增需求的领域归属依据。
- PLAN已经确认，计划文件为`tasks/plan.md`。
- TASKS已经确认，任务清单为`tasks/todo.md`。
- IMPLEMENT尚未开始；进入实现后必须按任务依赖、测试和检查点执行。
- 规格变更必须同步更新追溯矩阵。

## 开发资料使用约定

- 本目录中的Markdown规格是需求与开发的唯一事实来源（SSOT）。
- 设计、开发、测试和验收均应引用稳定的FR、BR、DR、API、NFR、AC编号。
- 任何需求变更先修改唯一Owner的Markdown领域卷、`appendices/requirement-migration.md`及`appendices/acceptance-traceability.md`，再重新生成评审导出件。
- Word文档仅用于集中评审、打印和签批，不作为代码实现或测试用例编写的优先输入。

## 技术规范适用关系

- 后端最简集成基线为`https://gitee.com/yudaocode/yudao-boot-mini.git`的`master-jdk25`分支；mini外模块从`https://github.com/YunaiV/ruoyi-vue-pro.git`同名版本分支获取。两个上游的实际集成提交均记录在主规格DEC-015。
- `appendices/module-boundary-and-naming.md`是模块命名、领域边界、数据所有权和模块依赖的规范来源。
- `appendices/api-design-specification.md`是URI、契约编号、请求响应、幂等、事件和版本治理的规范来源。
- `appendices/api-inventory.md`保存接口域、追溯ID和契约编号前缀，不重复定义公共API规则。
- `appendices/project-order-model-options-review.md`用于快速回顾项目、合同、执行单和实施订单模型的方案取舍。
- `appendices/project-order-target-schema-evidence.md`记录旧库实证、迁移风险和项目—订单行—SN目标表结构建议；其中标为待确认的事项不得直接固化为业务规则。
- `appendices/project-order-physical-schema.mysql.sql`是MySQL 8.x物理DDL评审草案，不是已经批准执行的版本化迁移。
- `appendices/project-order-migration-mapping.md`定义旧表到目标表的逐表映射、问题分类、对账门禁、切换和回退。
- `appendices/table-structure-business-rule-traceability.md`把27张目标表中的对象、关系和约束证据追溯到FR、BR、DR与AC；其中【建议】和【待确认】内容不得当作已批准业务制度。
- `docs/decisions/0001-project-order-line-scope-model.md`记录以ERP订单行实施范围作为项目交付主链的提议决策。
- 当领域分卷与公共技术规范表述不一致时，先按公共技术规范修订领域分卷，不得由实现自行选择口径。
