# 项目实施交付管理平台规格索引

## 规格文件

- `00-master-spec.md`
- `01-platform-and-permission.md`
- `02-project-initiation.md`
- `03-planning-and-execution.md`
- `04-cutover-and-stabilization.md`
- `05-acceptance-and-closure.md`
- `06-inspection-and-maintenance.md`
- `07-assets-and-outsourcing.md`
- `08-analytics-and-integration.md`
- `appendices/data-dictionary.md`
- `appendices/state-machines.md`
- `appendices/permission-matrix.md`
- `appendices/api-inventory.md`
- `appendices/acceptance-traceability.md`
- `appendices/module-boundary-and-naming.md`
- `appendices/api-design-specification.md`

## 基线说明

- 本目录是SPECIFY阶段评审基线。
- 用户评审通过前，不进入PLAN、TASKS或IMPLEMENT阶段。
- 规格变更必须同步更新追溯矩阵。

## 开发资料使用约定

- 本目录中的Markdown规格是需求与开发的唯一事实来源（SSOT）。
- 设计、开发、测试和验收均应引用稳定的FR、BR、DR、API、NFR、AC编号。
- 任何需求变更先修改对应Markdown领域卷及`appendices/acceptance-traceability.md`，再重新生成评审导出件。
- Word文档仅用于集中评审、打印和签批，不作为代码实现或测试用例编写的优先输入。

## 技术规范适用关系

- 后端最简集成基线为`https://gitee.com/yudaocode/yudao-boot-mini.git`的`master-jdk25`分支；mini外模块从`https://github.com/YunaiV/ruoyi-vue-pro.git`同名版本分支获取。两个上游的实际集成提交均记录在主规格DEC-015。
- `appendices/module-boundary-and-naming.md`是模块命名、领域边界、数据所有权和模块依赖的规范来源。
- `appendices/api-design-specification.md`是URI、契约编号、请求响应、幂等、事件和版本治理的规范来源。
- `appendices/api-inventory.md`保存接口域、追溯ID和契约编号前缀，不重复定义公共API规则。
- 当领域分卷与公共技术规范表述不一致时，先按公共技术规范修订领域分卷，不得由实现自行选择口径。
