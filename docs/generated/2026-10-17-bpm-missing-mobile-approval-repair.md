# BPM 缺少手机号阻断工期审批修复

## 现象与根因

项目 `992203060197` 当前工期计划为 36，变更 `2111391138248302594` 仍为 `PENDING_APPROVAL`，流程 `6965fb9b-ca0e-11f1-af8b-00ff0b78abd7`。申请人 y06496、审批人 z01010 的账号均未维护手机号。

59191 日志在 2026-10-17 17:44:30 显示：驳回结束调用 `BpmProcessInstanceServiceImpl.processProcessInstanceCompleted` → `BpmMessageServiceImpl.sendMessageWhenProcessInstanceReject` → `SmsSendServiceImpl.validateMobile`，抛出 `1002013000 / 手机号不存在`。短信异常位于业务结果事件发布之前，导致审批操作回滚，并非服务经理资格或项目范围校验失败。

## 修复边界

仅在 BPM 短信通知调用边界识别 SYSTEM 既有 `SMS_SEND_MOBILE_NOT_EXISTS` 错误并记录警告，保留用户 ID 和模板码作为定位依据，不记录手机号。流程通过、驳回、任务分配及超时通知复用该边界。

不补填手机号、不调整账号或角色权限、不降低 SYSTEM 短信手机号校验。其他短信业务错误继续抛出；业务结果发布、审批归属、租户、流程状态、幂等及业务失败回滚均沿用原路径。无新 API 或数据库迁移。

## 已验证

- 修复前：新增 12 项通知测试中，4 个缺少手机号场景全部复现失败，其余 8 项通过；日志 `.run/bpm-missing-mobile-before.log`。
- 修复后：通知回归 12 项、服务经理访问边界 11 项、项目节点审批流程归属 18 项，共 41 项通过；日志 `.run/bpm-missing-mobile-after.log`。
- 原有真实 H2/Flowable 事务测试改用真实 BPM 消息服务，并让短信 API 返回缺少手机号错误：12 项通过，覆盖手工通过、驳回、取消、历史结果、错误审批人、候选人篡改、授权撤销、业务结果消费者失败及事务回滚；日志 `.run/bpm-missing-mobile-flowable.log`。
- 本次修改路径的差异另行检查。工作树整体差异检查另报其他会话 `RevisionFiles.vue` 文件尾空行，本次未修改该文件。

## 部署与浏览器边界

第一次完整编译成功，但构建期间其他会话生成 V498 发布清单，源码一致性检查拒绝冻结；未绕过检查。基于更新后源码重新完整构建成功，22 项既有运行修复基线检查通过，冻结清单 `.run/build/releases/380694e4-6ab6-49f3-af52-5b80b1cd5163/manifest.json`，日志 `.run/bpm-missing-mobile-build-retry.log`。

部署前预检通过，冻结产物和运行 `local,dac` 配置均通过校验；日志 `.run/bpm-missing-mobile-deploy-preflight.log`。用户明确批准部署 `380694e4`，并要求保留项目 197 待审批。已使用 `scripts/worktree-backend.ps1` 和该冻结清单部署 59191，日志确认 `backend healthy`，新进程 PID 为 33940，运行参数仍为 `local,dac`、端口 59191；日志 `.run/bpm-missing-mobile-deploy.log`。48182 未操作。

部署后通过既有管理员 API 只读核对，工期计划 36、变更 `2111391138248302594` 的 `PENDING_APPROVAL` 状态、流程状态及服务经理任务均与部署前一致。任务 `696bef1a-ca0e-11f1-af8b-00ff0b78abd7` 仍未结束，审批人未变；前后记录逐项比较通过。证据为 `.run/bpm-missing-mobile-process-before.json`、`.run/bpm-missing-mobile-process-after.json`、`.run/bpm-missing-mobile-inspect.json` 和 `.run/bpm-missing-mobile-inspect-after.json`。

按照用户选择，未办理通过或驳回，未修改业务数据。当前浏览器会话无可复用标签，未额外切换登录会话；本次部署后验证边界为服务健康和真实接口的待审批状态核对，未执行真实浏览器审批闭环。53 项定向测试提供缺少手机号时审批事务行为的回归证据，不替代该未执行的浏览器闭环。

本次改动未提交、未推送，其他会话改动保留。
