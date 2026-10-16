# 服务经理与项目经理全流程权限配置及应用记录

日期：2026-10-16。状态：APPLIED_AND_CONFIGURATION_VERIFIED。范围：当前本地环境租户1。用户已明确批准“按清单应用，保留现有授权和其他会话改动”。

## 应用前事实

- SERVICE_MANAGER（993109110001）：菜单授权0条，角色关联119条。
- PROJECT_MANAGER（993109110002）：菜单授权0条，角色关联773条。
- V225仅建立成员候选角色，明确不初始化菜单权限；因此不是页面缓存或角色停用造成。
- 不改现有用户角色关系、data_scope、项目责任区间或项目授权记录。

## 已批准并应用的边界

项目经理：126项日常办理权限。服务经理：同一组126项，加13项指派、授权及业务审核权限。菜单祖先仅用于显示，后续应用时应排除停用入口及不在当前主线的旧入口。

不包含项目手工创建、全局模板维护/发布、迁移同步、设备凭据与连接授权、客户敏感字段、工程管理部重大方案复审、二线/研发专属角色资格、项目异常关闭/回退/重开。实际审核仍由冻结候选、项目参与关系和服务端数据范围重验；系统角色不替代业务资格。

## 两角色共用权限

|业务|权限键|
|---|---|
|项目办理|`pms:project:query`<br>`pms:project:update`<br>`pms:project-team:query`<br>`pms:project-team:create`<br>`pms:project-risk:query`<br>`pms:project-risk:create`<br>`pms:project-task:query`<br>`pms:project-task:create`<br>`pms:project-task:update`<br>`pms:project-task:assign`<br>`pms:project-task:execute`<br>`pms:project-phase:query`<br>`pms:project-phase:gate`<br>`pms:project:progress-policy:update`<br>`pms:project:progress-policy:submit`<br>`pms:project-plan:manage`<br>`pms:project-plan:rework`|
|项目资料|`pms:customer:query`<br>`pms:customer-contact:query`<br>`pms:customer-contact:create`<br>`pms:customer-contact:update`<br>`pms:device:query`<br>`pms:device-version:query`<br>`pms:commerce:contract:query`<br>`pms:commerce:scope:query`|
|工勘需求|`pms:sol-site-survey:query`<br>`pms:sol-site-survey:create`<br>`pms:sol-site-survey:update`<br>`pms:preparation-survey:query`<br>`pms:preparation-survey:manage`<br>`pms:preparation-survey:fill`<br>`pms:requirement-analysis:query`<br>`pms:requirement-analysis:manage`|
|计划方案|`pms:construction-plan:query`<br>`pms:construction-plan:duration-manage`<br>`pms:imp-stage-plan:query`<br>`pms:imp-stage-plan:create`<br>`pms:imp-stage-plan:update`<br>`pms:imp-stage-plan:submit`<br>`pms:sol-solution:query`<br>`pms:sol-solution:create`<br>`pms:sol-solution:update`<br>`pms:imp-doc-template:query`|
|到货安装|`pms:arrival-acceptance:query`<br>`pms:arrival-acceptance:create`<br>`pms:arrival-acceptance:edit-own-draft`<br>`pms:arrival-acceptance:confirm`<br>`pms:arrival-acceptance:resolve-difference`<br>`pms:imp-arrival:query`<br>`pms:imp-arrival:create`<br>`pms:imp-arrival:update`<br>`pms:imp-installation:query`<br>`pms:imp-installation:create`<br>`pms:imp-installation:update`|
|配置联调|`pms:imp-configuration:query`<br>`pms:imp-configuration:create`<br>`pms:imp-configuration:update`<br>`pms:imp-joint-test:query`<br>`pms:imp-joint-test:create`<br>`pms:imp-joint-test:update`<br>`pms:device-log-archive:query`<br>`pms:device-log-archive:import`<br>`pms:device-log-archive:download`<br>`pms:device-configuration-log:download`|
|割接办理|`pms:cutover-task:query`<br>`pms:cutover-task:create`<br>`pms:cutover-task:save-assessment`<br>`pms:cutover-task:submit-assessment`<br>`pms:cutover-task:save-checklist`<br>`pms:cutover-task:submit-checklist`<br>`pms:cutover-task:query-plan`<br>`pms:cutover-task:save-plan`<br>`pms:cutover-task:download-plan`<br>`pms:cutover-task:submit-plan`<br>`pms:cutover-task:query-approval`<br>`pms:cutover-task:query-closure`<br>`pms:cutover-task:save-closure`<br>`pms:cutover-task:submit-closure`<br>`pms:cutover-task:manage-spare`|
|培训满意度|`pms:imp-training:query`<br>`pms:imp-training:create`<br>`pms:imp-training:update`<br>`pms:imp-training:issue`<br>`pms:acceptance:satisfaction:query`<br>`pms:acceptance:satisfaction:manage`<br>`pms:acceptance:satisfaction:collect`<br>`pms:acceptance:satisfaction:download`<br>`pms:acceptance:satisfaction:export`<br>`pms:acc-completion-certificate:query`<br>`pms:acc-completion-certificate:create`<br>`pms:acc-completion-certificate:update`<br>`pms:acc-completion-certificate:submit`|
|验收交付|`pms:acceptance:report:query`<br>`pms:acceptance:report:write`<br>`pms:acceptance:report:complete`<br>`pms:acceptance:report:download`<br>`pms:imp-deliverable:query`<br>`pms:imp-deliverable:create`<br>`pms:imp-deliverable:update`<br>`pms:imp-deliverable:archive`<br>`pms:acc-deliverable-checklist:query`<br>`pms:acc-deliverable-checklist:create`<br>`pms:acc-deliverable-checklist:update`<br>`pms:acc-deliverable-checklist:submit`<br>`pms:acc-archive-document:query`<br>`pms:acc-archive-document:create`<br>`pms:acc-archive-document:update`<br>`pms:acc-archive-document:submit`<br>`pms:acc-project-closure:query`<br>`pms:acc-project-closure:create`<br>`pms:acc-project-closure:update`<br>`pms:acc-project-closure:submit`|
|业务文件|`pms:file:query`<br>`pms:file:upload`<br>`pms:file:download`<br>`pms:file:preview`<br>`pms:file:archive`|
|工程交底换货|`pms:sol-briefing:query`<br>`pms:sol-briefing:create`<br>`pms:sol-briefing:update`<br>`pms:sol-briefing:generate`<br>`pms:sol-briefing:publish`<br>`pms:imp-material-exch:query`<br>`pms:imp-material-exch:create`<br>`pms:imp-material-exch:update`<br>`pms:imp-material-exch:submit`|

## 仅服务经理新增

|权限键|
|---|
|`pms:project:assign`|
|`pms:project:authorization:query`|
|`pms:project:authorization:manage`|
|`pms:project:authorization:revoke`|
|`pms:construction-plan:duration-approve`|
|`pms:sol-solution:audit`|
|`pms:preparation-survey:waiver-approve`|
|`pms:cutover-task:approve`|
|`pms:sol-briefing:audit`|
|`pms:acc-completion-certificate:audit`|
|`pms:acc-deliverable-checklist:audit`|
|`pms:acc-archive-document:audit`|
|`pms:acc-project-closure:audit`|

## 应用与验证

已对运行库全部有效菜单核对：139个唯一权限键均存在，无需新造接口权限。应用通过SYSTEM正式`/system/permission/assign-role-menu`接口及PermissionService事务和缓存失效执行，未直接写权限表。因接口为整组分配，提交前重新读取既有授权取并集，未取消任何原授权。

|角色|菜单及按钮节点|有效唯一权限键|data_scope|用户角色关联数|
|---|---:|---:|---:|---:|
|PROJECT_MANAGER|155|126|5（仅本人，未调整）|773（未变化）|
|SERVICE_MANAGER|168|139|5（仅本人，未调整）|119（未变化）|

正式接口回读与数据库只读复核一致：批准权限全部存在，没有新增清单外权限；服务经理完整覆盖项目经理节点集合，差异为13项批准的管理/审核权限。停用或未批准权限的上级页面不自动授权，必要的业务按钮权限独立保留，防止为了显示目录夹带额外功能权限。

首次项目经理分配已成功写入155节点，但核验脚本未将接口返回的字符串ID转换为整数而误报缺失；只读核对后修正核验，按并集幂等重放未新增、未删除项目经理节点，再正常完成服务经理分配。该异常属于本地核验格式问题，没有降低服务端校验。

本轮完成配置及持久化验证；未冒用现有员工账号，也未使用认证绕过验证业务办理或跨项目拒绝。用户可刷新权限或重新登录加载菜单；实际业务仍受现有参与角色、冻结审批候选和项目数据范围校验。本轮未重启59191或48182，未修改其他会话源码，未提交或推送Git。

执行准备及无敏感信息的回执：`.run/manager-role-permission-application/initial-prepared.json`、`prepared.json`、`applied.json`。令牌只在进程内使用，回执不保存令牌。

## 后续纠正：满意度详情统一交付查询依赖

用户以项目经理账号y06496报告项目992203060198的满意度查看权限错误。代码核对确认详情内嵌`DeliveryPanel`还要求`pms:delivery:query`，首次126项清单漏掉此依赖。本次在已授权全流程查看范围内，通过同一SYSTEM正式分配接口按既有集合并集，为两个角色各补1个只读权限节点，未授予`delivery:operate/manage`或旧入口父菜单。

补齐后项目经理156节点/127权限、服务经理169节点/140权限，原集合均保留，data_scope未改。只读核对y06496实际角色关系已同时包含项目查询、客户查询、完工证明查询和统一交付查询权限。首次回执保存在`before-delivery-query-applied.json`，最新回执为`applied.json`。实际角色账号浏览器复验与客户关联读取修复另见`2026-10-16-project198-view-access-repair.md`。
