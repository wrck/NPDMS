# 任务状态机及项目模板规则数据比对

日期：2026-10-24。范围：租户1，任务状态机及五类正式项目模板当前发布配置。只读核查，不代表全系统业务规则或业务闭环验收。

## 结论

阻断差异为任务状态机发布状态。两端8条流转规则的起点、动作、终点、标准状态映射、允许角色、进入条件和退出条件逐项一致。

| 项目 | codex/domain-migration 对应库 | 当前测试库 |
|---|---|---|
| 数据库 | 127.0.0.1:24306/npdms_domain_test | 172.17.90.178:13306/npdms |
| 状态机ID | 2 | 996610195020063 |
| 修订号 | 1 | 1 |
| 状态 | PUBLISHED | DRAFT |
| 生效起点 | 2026-09-17 20:37:29.824 | 1970-01-01 08:00:00 |
| 发布时间 | 2026-09-17 20:37:29.824 | 空 |
| 创建来源 | v89-fproj007 | migration-v502-59299-config |
| 有效且未删除的版本数 | 1 | 1 |
| 当前可用已发布版本数 | 1 | 0 |

测试库成功执行V502；该迁移第530～532行明确仅在没有现有配置时插入DRAFT及后续8条流转，不复制源库发布事实。来源库发布人为空，属于既有迁移配置事实，不能虚构为人工发布。

## 流转规则

- 待分配→待开始：ASSIGN；项目经理或获授权的跨区域服务经理；assign权限及当前任务分配要求。
- 待开始→进行中：START；当前有效执行人；execute权限及实际开始时间要求。
- 进行中→待验收：SUBMIT；当前有效执行人；execute权限及progress=99条件配置。
- 待验收→已完成：COMPLETE；项目经理或规则审批人；complete权限及completionRuleSatisfied=true。
- 上述前四种状态→已关闭：CANCEL；项目经理或规则审批人；complete权限及reasonRequired=true。

上述为持久化规则内容比对，未通过实际任务操作验证各条件执行结果。

## 五类模板

两端五类模板均ACTIVE，已发布Designer文档业务内容一致。比较将businessViewRevisionId按entity_type/view_key/revision_no映射，忽略空值和重新冻结的businessViewSnapshot；因此不宣称业务视图快照原文完全一致。

| 模板 | 来源版本 | 测试版本 | 阶段/任务数 |
|---|---|---|---|
| 普通类－直签 | 2 | 1 | 7/16 |
| 工程类－直签 | 2 | 1 | 7/16 |
| 普通类－非直签 | 2 | 1 | 7/13 |
| 工程类－非直签 | 2 | 1 | 7/13 |
| 非直签－原厂督导 | 3 | 1 | 7/13 |

匹配规则、阶段任务、完成规则、交付件及Gate等均随Designer整体比较。本轮未重新比较BPM部署和动态表单，也未触发匹配或分配写入。

## 代码依据与处理边界

TaskStateMachineMapper.xml的selectCurrentPublished要求同租户、PUBLISHED、effective_from不晚于服务端当前时间且未删除。ProjectManualCreationServiceImpl.java第215～221行据此查询，没有结果即抛出“当前租户没有生效的已发布任务状态机版本”；第284行将该版本传入模板实例化。

因此已有模板发布不能自动使租户任务状态机生效。后续应使用现有发布服务发布测试库草稿996610195020063，当前乐观锁version=0；实际操作前须重读。控制器入口为POST /api/v1/pms/project-task-state-machines/{id}/actions/publish，要求pms:project-task-state:manage、Idempotency-Key和If-Match，通过服务端规则校验、幂等及审计。不得直接SQL修改状态，也无需复制源库ID或发布时间。

本次只完成比对，未发布、迁移、重启或修改业务数据，未执行浏览器业务验收。

## 复核证据

`.run/business-rule-comparison-20261024/`包含source.json、target.json、两端模板修订快照及comparison.json。连接均设置transaction_read_only=1并使用各库一致性快照；两库取数不是跨库原子快照。复核脚本为`.run/business-rule-compare.py`及`.run/business-rule-templates.py`。模板大字段排序曾触发MySQL排序内存不足，改为本地排序后读取成功，未改服务器配置。
