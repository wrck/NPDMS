# 工程类－直签 · 源计划 v4 全周期验收

2026-10-19，隔离项目 `PJT2026000001`（项目 1）已通过真实浏览器操作完成正常闭环，最终 Owner 状态为 `NORMAL_CLOSED`：16/16 项任务完成，7 个当前阶段执行均完成，7 项必选交付件均为 `ACCEPTED`。两个可选交付件仍为 `PENDING`，未补造提交记录。

[最终正确项目详情截图](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-correct-project-final-reopened.png) · [最终事实与执行历史](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-final-safe-facts.json) · [正常闭环 Owner 及审批读回](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-final-normal-closure-owner-api-readback.json) · [12 项 UI 观察清单](ui-observations.md)

## 验收对象与边界

- 来源为测试环境现行“工程类－直签 · 计划 v4”：原项目 `992203060197`，有效计划 `2104870198458802262`。隔离环境导入后的模板、计划编号为 1，本地界面显示计划 v1；验收使用来源 v4 的业务内容与已冻结规则。
- 使用获准继续验收的隔离项目，后端 `59299`、前端 `18199`；缺失模板、表单及基础配置按现行来源通过正式配置接口补齐。业务记录的办理、提交、确认、审批和返工通过真实页面完成；只读 API/数据库用于核对最终事实，不直接改业务状态。
- S0～S3 最初操作经早期入口完成，随后在用户指定的“项目交付 → 项目详情”逐项重开核对相同业务记录；S4 及后续操作使用指定详情及其原生业务页面。正确路由为 `/pms/project-management/project-master-detail?projectId=1`。
- 这是隔离场景的软件业务闭环验收。客户评价与签字、配置调试和联调使用明确标识的本地模拟材料；割接覆盖本计划要求的方案及审批，不包含生产割接执行、真实设备操作或真实客户凭据移交，也未发送外部通知。
- 结论限定本计划及本次走通的路径，不代表各模块所有异常分支、全部 Feature 或生产上线验收完成。

来源：[源计划元数据](../../../.run/build/source-v4-fullcycle-20261018/source-plan-v4-metadata.json)、[阶段任务及操作清单](../../../.run/build/source-v4-fullcycle-20261018/source-stage-task-operation-inventory.json)。

## 最终业务结果

| 阶段 | 本次完成的业务 |
| --- | --- |
| S0 项目立项 | 人员指派、项目联系人 |
| S1 工前准备 | 工勘确认、需求分析完成 |
| S2 施工计划 | 施工计划批准、经审批施工计划交付件接受 |
| S3 实施方案 | 生成并保存方案正文，PM 提交、SM 审批通过 |
| S4 实施部署 | 到货、两台设备安装、配置调试、业务联调、割接方案审批；后续对硬件及 S4 完成受控第二轮执行 |
| S5 验收交维 | 培训客户确认；完工证明与满意度联合签署，评分 80 分达标；初验、终验及对应交付件完成 |
| S6 项目闭环 | 原生校验通过，PM 提交，SM2 与材料审核人 1 分别在实际 BPM 待办中批准，项目正常闭环 |

闭环申请 `2111986771715870722`，真实流程 `03af1639-cb59-11f1-b5f0-00ff0b78abd7`，最终 `APPROVED`。两个节点分别为 `serviceManagerReview`（审核人 2）与 `materialReview`（审核人 1），均有独立审核记录。PM 在审核结束后重新打开项目详情，显示正常闭环、已批准、16/16 和全部阶段 100%。

[PM 提交回执](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-fixed-explicit-permission-submit-ui-result.json)、[服务经理审核](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-sm-bpm-approve-ui-result.json)、[材料审核](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-admin-material-bpm-approve-ui-result.json)、[审批历史截图](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-admin-bpm-final-history.png)。

必选交付件：经审批施工计划、配置 Log、联调记录、满意度有效结果、到货签收单、初验证明和终验证明，7 项均接受。工程自检表、机房实施承诺书为冻结规则中的可选项，2 项保持待提交。最终逐个读取当前 Owner 接口得到 9 项记录，关闭后均不可写；没有用旧交付件实例表的空结果判定缺失。

[当前项目实例读回](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-final-project-instances-owner-readback.json)、[9 项交付件当前 Owner 读回](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-final-deliverable-current-owner-readback.json)。

## S6 发现与处理

第一次关账检查正确阻断了硬件任务的旧安装结果：第二台设备完成安装后，原结果 1 已非当前结果，已完成任务的历史关联未被自动覆盖。通过原生返工预览确认只影响硬件任务和所属 S4 的执行轮次，再执行受控返工；第二轮关联当前结果 2 并按全设备事实完成。旧安装记录、旧执行轮次、关联历史和 S5 已完成记录均保留，随后关账业务检查通过。

[原失败校验](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-normal-closure-check-ui-result.json)、[返工预览](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-hardware-rework-native-preview-result.json)、[返工回执](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-hardware-controlled-rework-apply-ui-result.json)、[新旧关联历史](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-hardware-links-after-rework-readonly.json)。

后续提交暴露实际功能缺陷：材料审核人 1 已有完整、有效、同租户的显式审核授权，但内部权限查询误受 PM 的 SELF 用户列表范围过滤。修复后沿用同一审核人和授权链提交成功。PM 的普通用户列表仍只返回用户 3，角色仍为 SELF，174 项菜单集合未改变。

[修复前拒绝](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-normal-closure-submit-ui-result.json)、[既有完整授权链](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/s6-admin-audit-exact-tenant-grant-chain-readonly.json)、[修复后 PM 数据范围及权限对照](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/explicit-permission-fix-pm-self-and-grants-readonly.json)。

## 本次功能修复及验证

| 修复 | 保持的边界 | 验证 |
| --- | --- | --- |
| 实施方案把富文本空壳误判为已填写，导致冻结来源的背景/目标未生成 | 保留已有人工正文及图片、表格等有效内容 | 新失败测试复现后通过；2 个前端测试文件共 19 项；真实页面生成、保存、重开及审批通过 |
| 审核人身份校验误受调用者 SELF 用户范围限制 | 仍校验租户、用户存在与启停；普通用户查询范围不扩大 | 27 项身份/依赖/BPM 定向验证通过；PM 提交方案、SM 实际审批通过 |
| 显式授权链查询误受调用者 SELF 用户范围限制 | 真实角色菜单授权、精确权限码、租户、启停、撤权及锁定全部保留，不使用超级管理员快捷放行 | 真实 H2、生产 XML 和数据权限拦截器复现 2 项失败；修复后 41 项显式权限测试加 5 项身份回归共 46 项通过；实际闭环提交与双节点审批通过 |

前两处与本次第三处各修改一个功能实现及对应测试，未改 PRD、审批节点、冻结规则或 UI 样式。第三处新增私有查询助手沿用场景化 Query 对象，无新接口、SQL 或授权配置。

验证日志：[前端 19 项](../../../.run/build/source-v4-fullcycle-20261018/solution-source-empty-html-after.log)、[身份及 BPM 27 项](../../../.run/build/source-v4-fullcycle-20261018/admin-user-identity-and-review-regressions-green.log)、[显式权限修复前失败](../../../.run/build/source-v4-fullcycle-20261018/explicit-permission-scope-before.log)、[显式权限修复后 46 项](../../../.run/build/source-v4-fullcycle-20261018/explicit-permission-scope-after.log)。两轮后端验证包含重复的 5 项身份回归，不累加为独立用例数。

后端通过固定工具链和工作树互斥锁构建，22 项既有修复基线校验通过。最终冻结产物为 `24ccd897-bb8b-4930-9b0b-cc9d6f06b433`。对比前一冻结来源，仅新增显式权限实现和对应测试两文件差异；原有混合工作树改动未重置或覆盖。

[最终构建及 22 项基线检查](../../../.run/build/source-v4-fullcycle-20261018/explicit-permission-backend-build-freeze.log)、[本次来源差异核对](../../../.run/build/source-v4-fullcycle-20261018/explicit-permission-source-delta-review.json)。

## 环境及保留事项

本次新批准的部署只重启 59299，最终进程 35376；共享 59191/48182、前端 18199 及基础设施的进程/容器身份保持不变。此次部署复用既有凭据和内存密钥，没有再次初始化数据库或重设口令。环境、业务数据、附件、审批与执行历史均保留，未提交或推送 Git。

较早一次环境中断恢复曾按单独批准重设隔离专用账号凭据，并使用新的内存加密密钥。旧同步连接原记录保留，其旧密文暂不可用；本轮剩余验收不依赖该连接，也未据此声明 DAC/连接恢复验收通过。

最终证据目录扫描 936 个文本文件，实际运行 Secret 及 Bearer/JWT/bcrypt 模式命中均为 0，未输出 Secret 值。该结论仅覆盖本轮文本证据扫描。

[获准部署及服务保护证据](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/explicit-permission-owned-backend-restart-evidence.json)、[最终文本证据扫描](../../../.run/build/source-v4-fullcycle-20261018/browser-acceptance/fullcycle-final-secret-scan.json)。

本报告由本次浏览器回执、页面截图/文本、当前 Owner 只读接口、执行历史和定向验证日志汇总。重建摘要命令：`python .run/build/source-v4-fullcycle-20261018/write-final-acceptance-report.py`；UI 清单基于同次证据审阅。报告属于验收过程证据，不替代正式业务规格。
