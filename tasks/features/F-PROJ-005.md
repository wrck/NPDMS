# F-PROJ-005 服务经理人工指派与责任分布

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS / NPDMS-FPROJ005-FEATURE-READY-20260825-01`
> Implementation Done Gate：`PASS / 裁决任务01a03545-11b6-74a2-8ea5-177a96dd1e55`
> 当前阻断：无
> 当前任务：Task 1～6已完成，按工程链定位下一Feature
> Requirement ID：`PM-08`（仅V1人工指派）
> Feature Spec：`specs/features/F-PROJ-005-service-manager-manual-assignment.md`
> Feature物理契约：`specs/features/F-PROJ-005-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-005-service-manager-manual-assignment.md`
> 锁定规格提交：`9c55a7b965cadd85e893bab92c2def5881490cb7`

## 事实边界

- V1只实现人工候选、即时指派/改派、责任分布和通知；不实现自动指派。
- `ASSIGNED`要求有效主责服务经理与有效项目经理同时存在；本Feature不实现PM-11。
- 成员时间表保留历史，通知使用Outbox快照和SYSTEM持久deliveryKey，不新增历史或通知重试表。
- 用户已禁用测试驱动顺序，但每个Task完成前执行风险匹配验证。
- 当前只推进Implementation，不准备Deployment、SIT、UAT或Release材料。

## 任务跟踪

- [x] Task 1 建立SYSTEM与成员物理基础（迁移契约5/5、25模块Reactor编译通过）
- [x] Task 2 提供SYSTEM候选与幂等站内信API（定向测试8/8、18模块Reactor通过）
- [x] Task 3 改造PROJ指派事务与状态语义（提交`54a8503`）
- [x] Task 4 增加候选与责任分布查询API（提交`0f591b5`）
- [x] Task 5 完成Outbox站内信投递闭环（提交`1bfe206`）
- [x] Task 6 改造响应式界面并完成Feature验收（Implementation Done正式裁决GO）

## Task 6 验证证据

- 前端：Vitest 4/4；变更文件ESLint、Prettier、Stylelint通过。
- 后端：SYSTEM 54/54、PLATFORM 13/13（另7项真实MySQL测试按默认策略跳过）、PROJ 269/269（另19项真实MySQL测试按默认策略跳过）；Reactor `BUILD SUCCESS`。新增授权回归证明工程管理人员无需先成为服务经理即可在既有MANAGE范围内执行首次指派，授权授予/撤销仍保留服务经理角色限制。
- 真实MySQL：服务经理并发版本、责任区间、状态组合及Outbox重试3/3通过；隔离库从空库成功执行并校验V1～V84；迁移契约6/6。SYSTEM公司`930800`、部门`930801`和user 1组织范围均为既有单租户`tenant_id=1`事实，未创建租户0副本。
- 浏览器：候选查询、人工主责指派/改派、责任分布查询均HTTP 200；刷新后显示`管理员`、`OFFICE-HZ-DEMO`和`已指派`；1440/1024/768/320四档横向溢出均为0，当前功能控制台无异常。
- 租户开关：与既有部门、公司接口保持同一框架语法；业务代码不读取开关或`TenantContextHolder`，关闭时不注册租户拦截器，开启时由`TenantLineInnerInterceptor`自动为公司、部门、用户及组织范围查询注入租户条件。SYSTEM定向7/7、PROJ定向29/29通过，未修改`yudao-framework`、OAuth2或通用用户DTO。
- 隔离验收：最终包独立以`yudao.tenant.enable=true`启动后，租户1访问租户0项目的候选和指派分别返回`1014024033`、`1014024034`；前后计数均保持assignment=6、idempotency=0、audit=0、outbox=0，专用幂等键记录为0。单租户真实浏览器仍使用既有项目`992002000000`完成正向闭环，不新增租户1验收项目或SYSTEM主数据副本。
- 独立裁决：任务`01a03545-11b6-74a2-8ea5-177a96dd1e55`正式裁决`GO`，确认原NO-GO租户阻断闭环且不重开Feature Ready/SDS。
- 边界：未实现自动指派、PM-11、预约生效、独立历史表或Deployment/SIT/UAT/Release材料。

> 检查点（2026-08-25）：基线`9c55a7b`；Implementation Done已GO；自动化、空库V1～V84、单/多租户运行态及四档响应式证据通过；无阻塞；下一步按工程链定位下一Feature。
