# 施工计划审批成果自动关联验收（2026-10-09）

需求方确认：施工计划审批通过后自动关联当前项目匹配的“施工计划”交付件要求。

## 实现及边界

施工计划原BPM回调仍负责校验输入、审批批次生效和阶段/任务日期回写。补齐EngineeringOperationResultSource的StagePlanBatch事实翻译；新增PLN/STAGE_PLAN/CONSTRUCTION_PLAN_APPROVED原生成果源，沿用已有成果日志、Outbox投递及ACC归集服务，不另建材料Writer或审批。

只允许最新生效且当前工期修订一致的批准批次作为当前证据。批准批次ID与原生效时间构成果身份；查询和锁定重验均校验租户、项目和精确对象。旧批次、过期工期、未审批、缺少BPM或生效时间不伪装当前成果。不声称工期变更等全部状态都已覆盖成果提交屏障；当前源不声明transactionalChangeCoverage。

目标沿用有效冻结计划automaticSources；按本次用户确认，冻结任务明确绑定PLN/STAGE_PLAN、完成规则明确引用交付件、同任务同阶段的该交付件允许BUSINESS_RESULT时也匹配。未按名称、固定阶段或交付件编码推断，不改写冻结历史。提交和材料登记继续沿用原幂等键、版本重验、旧提交退场与历史保留。无匹配不新增要求，后台失败走原Outbox持久重试。

## 验证

后端6类72项定向检查通过：批准回调及输入/状态失败、当前/旧版成果、工期变化、跨项目跨租户与身份拒绝、空对象范围、冻结目标匹配/无匹配/禁用来源/关闭项目、成果日志幂等和归集/提交保护。JDK25动态attach失败后使用显式ByteBuddy测试agent重跑受影响检查；没有更改MockMaker、业务校验或降低测试断言。package成功，无数据库迁移。

部署到59191/npdms_domain_test。验收期间共享测试服务再次启动，连接短暂中断，恢复后确认新成果源仍可读取并继续验收，最终PID37008。没有重启MySQL、Redis或DAC。

真实Chromium项目992203060198、交付件2101716952857630520：

- 已有当前批准#29缺关联，使用原授权成果提交接口一次补关联，Idempotency-Key为stage-plan-result-backfill:29:2101716952857630520，不伪造新的成果形成事件。仅修复该已确认测试对象，未批量改写其他项目历史。
- 浏览器创建#30草稿、保存原因并提交服务经理审批。草稿无日期时被原前端完整性校验阻止提交；以#29批准版为真实输入，通过原重新倒排/草稿更新API维护6阶段和17任务日期后，在浏览器重新提交。
- 浏览器由实际服务经理（管理员）在原审批抽屉填写意见并通过。#30审批生效后无需点击关联或提交按钮，交付件自动ACCEPTED，页面显示“已满足”，提交历史显示成果30并保留成果29。
- 数据追溯：批准#30仅一个formation日志；现行提交request_key等于result-change:<原notificationId>，证明来自自动投递。两版提交保留，只有新提交为当前；批准批次29/30及BPM和生效时间都保留。

证据目录`.run/dac-host/stage-plan-auto-result-acceptance/`：existing-29-association-receipt.json、new-plan-receipt.json、schedule-input-receipt.json、submitted-plan-receipt.json、approval-receipt.json、automatically-associated-detail.json、acceptance.json、approval-pending.png、approved-auto-associated.png。测试日志`.run/stage-plan-auto-result-tests{,-r2,-r3}.log`，构建`.run/stage-plan-auto-result-package.log`；验收脚本`.run/dac-host/accept-stage-plan-auto-{submit,approve}.py`及prepare-stage-plan-acceptance-input.py。

只证明本次增量，不声明F-PROJ-009整体完成。验收记录保留；代码未提交或推送。
