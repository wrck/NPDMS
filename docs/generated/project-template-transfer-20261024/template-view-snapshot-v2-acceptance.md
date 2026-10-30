# 模板业务视图冻结快照补齐与验收

日期：2026-10-24。需求方授权继续补齐模板快照、解决 VIEW_NOT_BOUND、保留配置及不可变历史，并在当前测试环境使用 admin 验收。

## 修复结果

通过既有模板 draft 保存、validate、publish API，将五套 `TPL-CFG20261014-*` 模板发布为 v2。普通类直签、工程类直签各补12处，其余三套各补9处，共51处。仅在已有精确业务视图引用且快照缺失的绑定上，保存该已发布视图的查询结果；其他设计内容、匹配条件、规则及纯PAGE路由绑定保持原样。五份原发布修订逐字段比对一致，15条视图注册记录逐字段比对一致。

未改应用代码、未执行新Flyway迁移、未重启服务。原 v1 和旧项目84572继续使用原冻结内容，不回填不可变历史。

## 验证

- 五套模板保存读回与提交内容一致，发布校验均为 valid=true，发布读回一致；各保留 v1 并新增 v2。
- 失败验证：临时在第一套草稿中提交过期视图版本，validate 返回 BUSINESS_VIEW_SNAPSHOT_MISMATCH，publish 返回 BUSINESS_VIEW_REVALIDATION_FAILED；发布修订集合及原发布内容不变。恢复正确草稿后发布成功。
- admin真实浏览器创建 `84573 / PJT2026000002 / 模板业务视图快照v2验收-20261024`，页面唯一自动命中普通类直签 v2，显示16个任务。
- 数据库确认新执行计划源修订为996610195020096，含12处视图快照；16个任务均引用状态机996610195170001。旧项目计划仍引用996610195020091，未补写视图快照。
- 浏览器打开新项目“割接上线”：业务视图与空割接列表正常显示，操作权限为“允许”，无 VIEW_NOT_BOUND。项目处S0，阶段规则仍返回 NODE_EXECUTION_FORBIDDEN；未推进阶段或发起割接审批，不能据此声明审批全流程完成。

## 证据与边界

当前测试入口19086、后端59286、目标库172.17.90.178:13306/npdms。沿用此前批准的admin公司001、办事处310407范围，没有新增权限。

`.run/template-view-snapshot-20261024/` 保存 prepared.json、resolved-views.json、五份validation/readback、publish-receipts.json、stale-snapshot-rejection.json、preservation-result.json、instance-verification.json、browser-template-match.txt、browser-created.txt、browser-cutover-entry.txt/png；prepare/apply/publish/verify脚本记录本次操作。旧记录保留原时点结论。

本次为配置修复，自审限定字段差异、原发布记录保护及失败恢复；不晋级Feature整体状态。其他任务的业务事实自动完成及提示行为不在上述验收结论内。
