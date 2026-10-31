# 测试环境角色权限只读核查

核查时间：2026-10-31T22:32:48+08:00。目标：172.17.90.178 / npdms / 租户 1；对照：本机 npdms_domain_test / 租户 1。全部为只读查询，不包含凭据或用户名单。

目标 33 个未删除角色、823 条有效角色菜单关联；本地 42 个未删除角色。授权以菜单 ID 对照，并另列同 ID 菜单身份差异。角色权限是直接关联；实际用户权限还受其他角色、租户、菜单状态、缓存及业务对象范围影响。超级管理员有代码级特殊授权，不能仅按显式关联判断其权限。

## 全角色概览

| 角色编码 | 名称 | 状态 | 类型 | 数据范围值 | 启用用户数 | 直接关联 | 本地有而目标无 | 目标有而本地无 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| `admin` | 管理员 | 0 | 2 | 5 | 8 | 2 | 0 | 0 |
| `areaLeader` | 办事处主任 | 0 | 2 | 5 | 38 | 0 | 0 | 0 |
| `callbackPer` | 回访人员 | 0 | 2 | 5 | 1 | 0 | 0 | 0 |
| `common` | 普通角色 | 0 | 1 | 2 | 0 | 203 | 61 | 0 |
| `componentAdmin` | 产品组件管理员 | 0 | 2 | 5 | 1 | 0 | 0 | 0 |
| `CUT_RND_APPROVER` | 割接研发审批 | 0 | 2 | 5 | 0 | 6 | 0 | 0 |
| `CUT_SECOND_LINE_APPROVER` | 割接二线审批 | 0 | 2 | 5 | 0 | 6 | 0 | 0 |
| `dataViewer` | 数据查询角色 | 0 | 2 | 5 | 1 | 0 | 0 | 0 |
| `dispatchSettleStaff` | 项目转包结算人员 | 0 | 2 | 5 | 0 | 0 | 0 | 0 |
| `engineeManager` | 工程管理部 | 0 | 2 | 5 | 5 | 10 | 0 | 8 |
| `engineeManagerLeader` | 工程管理部主管 | 0 | 2 | 5 | 1 | 2 | 0 | 0 |
| `ENGINEERING_MANAGEMENT_REVIEW` | 工程管理部方案复审 | 0 | 2 | 5 | 0 | 20 | 0 | 0 |
| `financialAP` | 财务AP | 0 | 2 | 5 | 8 | 0 | 0 | 0 |
| `financialStaff` | 财务人员 | 0 | 2 | 5 | 11 | 0 | 0 | 0 |
| `presalesStaff` | 售前测试专员 | 0 | 2 | 5 | 3 | 0 | 0 | 0 |
| `probAdmin` | 技术公告管理员 | 0 | 2 | 5 | 11 | 0 | 0 | 0 |
| `probRD` | 研发人员 | 0 | 2 | 5 | 76 | 10 | 0 | 0 |
| `probSupporter` | 技术支持人员 | 0 | 2 | 5 | 39 | 23 | 0 | 0 |
| `PROJECT_MANAGER` | 项目经理 | 0 | 2 | 5 | 369 | 179 | 0 | 1 |
| `projectAdmin` | 项目管理员 | 0 | 2 | 5 | 16 | 0 | 0 | 0 |
| `projectAFQC` | 安服质量监督员 | 0 | 2 | 5 | 4 | 0 | 0 | 0 |
| `projectAreaManager` | 项目区域负责人 | 0 | 2 | 5 | 3 | 0 | 0 | 0 |
| `projectMember` | 项目成员 | 0 | 2 | 5 | 145 | 0 | 0 | 0 |
| `projectPreSales` | 项目售前 | 0 | 2 | 5 | 4 | 0 | 0 | 0 |
| `projectSubAdmin` | 子项目管理员 | 0 | 2 | 5 | 7 | 0 | 0 | 0 |
| `projectViewer` | 项目查阅员 | 0 | 2 | 5 | 11 | 0 | 0 | 0 |
| `projectYFQC` | 研发质量监督员 | 0 | 2 | 5 | 2 | 0 | 0 | 0 |
| `SALES_REPRESENTATIVE` | 销售代表 | 0 | 2 | 5 | 0 | 0 | 0 | 0 |
| `SERVICE_MANAGER` | 服务经理 | 0 | 2 | 5 | 39 | 209 | 0 | 4 |
| `super_admin` | 超级管理员 | 0 | 1 | 1 | 2 | 153 | 7 | 14 |
| `user` | 普通用户 | 0 | 2 | 5 | 202 | 0 | 0 | 0 |
| `userManager` | 用户管理员 | 0 | 2 | 5 | 1 | 0 | 0 | 0 |
| `warrantyCallbacker` | 项目维保回访专员 | 0 | 2 | 5 | 1 | 0 | 0 | 0 |

状态 0 表示启用；数据范围值按数据库原值列示，不推断该角色可访问的业务项目。

## 对照差异

本地独有角色：`TPL_FLOW_REVIEW_ACCEPTANCE`、`crm_admin`、`fast001_browser_denied`、`fast001_browser_operator`、`fast001_browser_readonly`、`fcus001_browser_denied`、`fcus001_browser_operator`、`fcus001_browser_readonly`、`test-dp`。这些角色可能为测试、专项或其他环境角色，不能据此直接创建。
目标独有角色：无。

### 割接研发审批（`CUT_RND_APPROVER`）

- 同 ID `992602050001`：name: 割接任务列表 → 割接任务工作台；path: tasks → cutover-task；component: pms/cutover/tasks/index → pms/cutover/cutover-task/index

### 割接二线审批（`CUT_SECOND_LINE_APPROVER`）

- 同 ID `992602050001`：name: 割接任务列表 → 割接任务工作台；path: tasks → cutover-task；component: pms/cutover/tasks/index → pms/cutover/cutover-task/index

### 工程管理部方案复审（`ENGINEERING_MANAGEMENT_REVIEW`）

- 同 ID `993109100501`：name: 项目交付（新功能暂停） → 项目交付（新功能）

### 项目经理（`PROJECT_MANAGER`）

目标有、本地无（保留现状，须核查来源）：
- `970000000000099786` 项目设备清单导出（`pms:project-device:export`；有效）

- 同 ID `992602050001`：name: 割接任务列表 → 割接任务工作台；path: tasks → cutover-task；component: pms/cutover/tasks/index → pms/cutover/cutover-task/index

### 服务经理（`SERVICE_MANAGER`）

目标有、本地无（保留现状，须核查来源）：
- `1201` 我的流程（`—`；有效）
- `19102` 完工证明删除（`pms:acc-completion-certificate:delete`；有效）
- `993109180005` 培训记录删除（`pms:imp-training:delete`；有效）
- `970000000000099786` 项目设备清单导出（`pms:project-device:export`；有效）

- 同 ID `992602050001`：name: 割接任务列表 → 割接任务工作台；path: tasks → cutover-task；component: pms/cutover/tasks/index → pms/cutover/cutover-task/index

### 普通角色（`common`）

本地有、目标无（需先确认菜单用途及是否停用）：
- `1117` 支付管理（`—`；停用或删除）
- `1126` 应用信息（`—`；有效）
- `1127` 支付应用信息查询（`pay:app:query`；有效）
- `1128` 支付应用信息创建（`pay:app:create`；有效）
- `1129` 支付应用信息更新（`pay:app:update`；有效）
- `1130` 支付应用信息删除（`pay:app:delete`；有效）
- `1132` 秘钥解析（`pay:channel:parsing`；有效）
- `1133` 支付商户信息查询（`pay:merchant:query`；有效）
- `1134` 支付商户信息创建（`pay:merchant:create`；有效）
- `1135` 支付商户信息更新（`pay:merchant:update`；有效）
- `1136` 支付商户信息删除（`pay:merchant:delete`；有效）
- `1137` 支付商户信息导出（`pay:merchant:export`；有效）
- `1150` 秘钥解析（`—`；有效）
- `1161` 退款订单（`—`；有效）
- `1162` 退款订单查询（`pay:refund:query`；有效）
- `1166` 退款订单导出（`pay:refund:export`；有效）
- `1173` 支付订单（`—`；有效）
- `1174` 支付订单查询（`pay:order:query`；有效）
- `1178` 支付订单导出（`pay:order:export`；有效）
- `1254` 菜单记录不存在
- `1281` 报表管理（`—`；停用或删除）
- `1282` 报表设计器（`—`；有效）
- `2000` 商品中心（`—`；有效）
- `2002` 商品分类（`—`；有效）
- `2003` 分类查询（`product:category:query`；有效）
- `2004` 分类创建（`product:category:create`；有效）
- `2005` 分类更新（`product:category:update`；有效）
- `2006` 分类删除（`product:category:delete`；有效）
- `2008` 商品品牌（`—`；有效）
- `2009` 品牌查询（`product:brand:query`；有效）
- `2010` 品牌创建（`product:brand:create`；有效）
- `2011` 品牌更新（`product:brand:update`；有效）
- `2012` 品牌删除（`product:brand:delete`；有效）
- `2014` 商品列表（`—`；有效）
- `2015` 商品查询（`product:spu:query`；有效）
- `2016` 商品创建（`product:spu:create`；有效）
- `2017` 商品更新（`product:spu:update`；有效）
- `2018` 商品删除（`product:spu:delete`；有效）
- `2019` 商品属性（`—`；有效）
- `2020` 规格查询（`product:property:query`；有效）
- `2021` 规格创建（`product:property:create`；有效）
- `2022` 规格更新（`product:property:update`；有效）
- `2023` 规格删除（`product:property:delete`；有效）
- `2025` Banner（`—`；有效）
- `2027` Banner创建（`promotion:banner:create`；有效）
- `2028` Banner更新（`promotion:banner:update`；有效）
- `2029` Banner删除（`promotion:banner:delete`；有效）
- `2030` 营销中心（`—`；有效）
- `2362` 商城系统（`—`；停用或删除）
- `2387` 内容管理（`—`；有效）
- `2758` AI 大模型（`—`；停用或删除）
- `2759` AI 对话（`—`；有效）
- `5811` 领料出库单完成（`mes:wm-product-issue:finish`；有效）
- `6400` WMS 系统（`—`；停用或删除）
- `6401` 基础数据（`—`；有效）
- `6402` 仓库管理（`—`；有效）
- `6403` 仓库查询（`wms:warehouse:query`；有效）
- `6404` 仓库创建（`wms:warehouse:create`；有效）
- `6405` 仓库更新（`wms:warehouse:update`；有效）
- `6406` 仓库删除（`wms:warehouse:delete`；有效）
- `6490` WMS 首页（`wms:home:query`；有效）

### 工程管理部（`engineeManager`）

目标有、本地无（保留现状，须核查来源）：
- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `1222` 流程任务的更新（`bpm:task:update`；有效）
- `992209220341` 工程管理部方案复审（`pms:sol-solution:major-review`；有效）

### 技术支持人员（`probSupporter`）

- 同 ID `992602050001`：name: 割接任务列表 → 割接任务工作台；path: tasks → cutover-task；component: pms/cutover/tasks/index → pms/cutover/cutover-task/index

### 超级管理员（`super_admin`）

本地有、目标无（需先确认菜单用途及是否停用）：
- `5720` 工艺流程（`—`；有效）
- `5721` 工艺路线查询（`mes:pro-route:query`；有效）
- `5722` 工艺路线创建（`mes:pro-route:create`；有效）
- `5723` 工艺路线更新（`mes:pro-route:update`；有效）
- `5724` 工艺路线删除（`mes:pro-route:delete`；有效）
- `5725` 工艺路线导出（`mes:pro-route:export`；有效）
- `5811` 领料出库单完成（`mes:wm-product-issue:finish`；有效）

目标有、本地无（保留现状，须核查来源）：
- `19019` 割接任务（`pms:cut-task:query`；停用或删除）
- `19020` 割接风险（`pms:cut-risk:query`；有效）
- `19021` 割接方案（`pms:cut-plan:query`；停用或删除）
- `19062` 割接任务创建（`pms:cut-task:create`；有效）
- `19063` 割接任务更新（`pms:cut-task:update`；有效）
- `19064` 割接任务删除（`pms:cut-task:delete`；有效）
- `19065` 割接任务审核（`pms:cut-task:audit`；有效）
- `19066` 割接风险创建（`pms:cut-risk:create`；有效）
- `19067` 割接风险更新（`pms:cut-risk:update`；有效）
- `19068` 割接风险删除（`pms:cut-risk:delete`；有效）
- `19069` 割接方案创建（`pms:cut-plan:create`；有效）
- `19070` 割接方案更新（`pms:cut-plan:update`；有效）
- `19071` 割接方案删除（`pms:cut-plan:delete`；有效）
- `19072` 割接方案审核（`pms:cut-plan:audit`；有效）

- 同 ID `18020`：status: — → 1
- 同 ID `993109100501`：name: 项目交付（新功能暂停） → 项目交付（新功能）

## 目标角色直接权限明细

列出角色自身关联；有其他角色的用户可能获得额外权限，菜单停用条目不能据此视为可使用。

### 管理员（`admin`）

- `18000` 项目交付（`—`；有效）
- `993109180007` 阶段施工计划（`pms:imp-stage-plan:query`；有效）

### 办事处主任（`areaLeader`）

- 无直接菜单关联。

### 回访人员（`callbackPer`）

- 无直接菜单关联。

### 普通角色（`common`）

- `1` 系统管理（`—`；有效）
- `2` 基础设施（`—`；有效）
- `5` OA 示例（`—`；有效）
- `100` 用户管理（`system:user:list`；有效）
- `101` 角色管理（`—`；有效）
- `102` 菜单管理（`—`；有效）
- `103` 部门管理（`—`；有效）
- `104` 岗位管理（`—`；有效）
- `105` 字典管理（`—`；有效）
- `106` 配置管理（`—`；有效）
- `107` 通知公告（`—`；有效）
- `108` 审计日志（`—`；有效）
- `109` 令牌管理（`—`；有效）
- `110` 定时任务（`—`；有效）
- `111` MySQL 监控（`—`；有效）
- `112` Java 监控（`—`；有效）
- `113` Redis 监控（`—`；有效）
- `114` 表单构建（`infra:build:list`；有效）
- `115` 代码生成（`infra:codegen:query`；有效）
- `116` API 接口（`infra:swagger:list`；有效）
- `500` 操作日志（`—`；有效）
- `501` 登录日志（`—`；有效）
- `1001` 用户查询（`system:user:query`；有效）
- `1002` 用户新增（`system:user:create`；有效）
- `1003` 用户修改（`system:user:update`；有效）
- `1004` 用户删除（`system:user:delete`；有效）
- `1005` 用户导出（`system:user:export`；有效）
- `1006` 用户导入（`system:user:import`；有效）
- `1007` 重置密码（`system:user:update-password`；有效）
- `1008` 角色查询（`system:role:query`；有效）
- `1009` 角色新增（`system:role:create`；有效）
- `1010` 角色修改（`system:role:update`；有效）
- `1011` 角色删除（`system:role:delete`；有效）
- `1012` 角色导出（`system:role:export`；有效）
- `1013` 菜单查询（`system:menu:query`；有效）
- `1015` 菜单修改（`system:menu:update`；有效）
- `1017` 部门查询（`system:dept:query`；有效）
- `1018` 部门新增（`system:dept:create`；有效）
- `1019` 部门修改（`system:dept:update`；有效）
- `1020` 部门删除（`system:dept:delete`；有效）
- `1021` 岗位查询（`system:post:query`；有效）
- `1022` 岗位新增（`system:post:create`；有效）
- `1023` 岗位修改（`system:post:update`；有效）
- `1024` 岗位删除（`system:post:delete`；有效）
- `1025` 岗位导出（`system:post:export`；有效）
- `1026` 字典查询（`system:dict:query`；有效）
- `1027` 字典新增（`system:dict:create`；有效）
- `1028` 字典修改（`system:dict:update`；有效）
- `1029` 字典删除（`system:dict:delete`；有效）
- `1030` 字典导出（`system:dict:export`；有效）
- `1031` 配置查询（`infra:config:query`；有效）
- `1032` 配置新增（`infra:config:create`；有效）
- `1033` 配置修改（`infra:config:update`；有效）
- `1034` 配置删除（`infra:config:delete`；有效）
- `1035` 配置导出（`infra:config:export`；有效）
- `1036` 公告查询（`system:notice:query`；有效）
- `1037` 公告新增（`system:notice:create`；有效）
- `1038` 公告修改（`system:notice:update`；有效）
- `1039` 公告删除（`system:notice:delete`；有效）
- `1040` 操作查询（`system:operate-log:query`；有效）
- `1042` 日志导出（`system:operate-log:export`；有效）
- `1043` 登录查询（`system:login-log:query`；有效）
- `1045` 日志导出（`system:login-log:export`；有效）
- `1046` 令牌列表（`system:oauth2-token:page`；有效）
- `1048` 令牌删除（`system:oauth2-token:delete`；有效）
- `1050` 任务新增（`infra:job:create`；有效）
- `1051` 任务修改（`infra:job:update`；有效）
- `1052` 任务删除（`infra:job:delete`；有效）
- `1053` 状态修改（`infra:job:update`；有效）
- `1054` 任务导出（`infra:job:export`；有效）
- `1056` 生成修改（`infra:codegen:update`；有效）
- `1057` 生成删除（`infra:codegen:delete`；有效）
- `1058` 导入代码（`infra:codegen:create`；有效）
- `1059` 预览代码（`infra:codegen:preview`；有效）
- `1060` 生成代码（`infra:codegen:download`；有效）
- `1063` 设置角色菜单权限（`system:permission:assign-role-menu`；有效）
- `1064` 设置角色数据权限（`system:permission:assign-role-data-scope`；有效）
- `1065` 设置用户角色（`system:permission:assign-user-role`；有效）
- `1066` 获得 Redis 监控信息（`infra:redis:get-monitor-info`；有效）
- `1067` 获得 Redis Key 列表（`infra:redis:get-key-list`；有效）
- `1070` 代码生成案例（`—`；有效）
- `1075` 任务触发（`infra:job:trigger`；有效）
- `1077` 链路追踪（`—`；有效）
- `1078` 访问日志（`—`；有效）
- `1082` 日志导出（`infra:api-access-log:export`；有效）
- `1083` API 日志（`—`；有效）
- `1084` 错误日志（`infra:api-error-log:query`；有效）
- `1085` 日志处理（`infra:api-error-log:update-status`；有效）
- `1086` 日志导出（`infra:api-error-log:export`；有效）
- `1087` 任务查询（`infra:job:query`；有效）
- `1088` 日志查询（`infra:api-access-log:query`；有效）
- `1089` 日志查询（`infra:api-error-log:query`；有效）
- `1090` 文件列表（`—`；有效）
- `1091` 文件查询（`infra:file:query`；有效）
- `1092` 文件删除（`infra:file:delete`；有效）
- `1093` 短信管理（`—`；有效）
- `1094` 短信渠道（`—`；有效）
- `1095` 短信渠道查询（`system:sms-channel:query`；有效）
- `1096` 短信渠道创建（`system:sms-channel:create`；有效）
- `1097` 短信渠道更新（`system:sms-channel:update`；有效）
- `1098` 短信渠道删除（`system:sms-channel:delete`；有效）
- `1100` 短信模板（`—`；有效）
- `1101` 短信模板查询（`system:sms-template:query`；有效）
- `1102` 短信模板创建（`system:sms-template:create`；有效）
- `1103` 短信模板更新（`system:sms-template:update`；有效）
- `1104` 短信模板删除（`system:sms-template:delete`；有效）
- `1105` 短信模板导出（`system:sms-template:export`；有效）
- `1106` 发送测试短信（`system:sms-template:send-sms`；有效）
- `1107` 短信日志（`—`；有效）
- `1108` 短信日志查询（`system:sms-log:query`；有效）
- `1109` 短信日志导出（`system:sms-log:export`；有效）
- `1118` 请假查询（`—`；有效）
- `1119` 请假申请查询（`bpm:oa-leave:query`；有效）
- `1120` 请假申请创建（`bpm:oa-leave:create`；有效）
- `1138` 租户列表（`—`；有效）
- `1139` 租户查询（`system:tenant:query`；有效）
- `1140` 租户创建（`system:tenant:create`；有效）
- `1141` 租户更新（`system:tenant:update`；有效）
- `1142` 租户删除（`system:tenant:delete`；有效）
- `1143` 租户导出（`system:tenant:export`；有效）
- `1185` 工作流程（`—`；有效）
- `1186` 流程管理（`—`；有效）
- `1187` 流程表单（`—`；有效）
- `1188` 表单查询（`bpm:form:query`；有效）
- `1189` 表单创建（`bpm:form:create`；有效）
- `1190` 表单更新（`bpm:form:update`；有效）
- `1191` 表单删除（`bpm:form:delete`；有效）
- `1192` 表单导出（`bpm:form:export`；有效）
- `1193` 流程模型（`—`；有效）
- `1194` 模型查询（`bpm:model:query`；有效）
- `1195` 模型创建（`bpm:model:create`；有效）
- `1197` 模型更新（`bpm:model:update`；有效）
- `1198` 模型删除（`bpm:model:delete`；有效）
- `1199` 模型发布（`bpm:model:deploy`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1208` 已办任务（`—`；有效）
- `1209` 用户分组（`—`；有效）
- `1210` 用户组查询（`bpm:user-group:query`；有效）
- `1211` 用户组创建（`bpm:user-group:create`；有效）
- `1212` 用户组更新（`bpm:user-group:update`；有效）
- `1213` 用户组删除（`bpm:user-group:delete`；有效）
- `1215` 流程定义查询（`bpm:process-definition:query`；有效）
- `1216` 流程任务分配规则查询（`bpm:task-assign-rule:query`；有效）
- `1217` 流程任务分配规则创建（`bpm:task-assign-rule:create`；有效）
- `1218` 流程任务分配规则更新（`bpm:task-assign-rule:update`；有效）
- `1219` 流程实例的创建（`bpm:process-instance:create`；有效）
- `1220` 流程实例的取消（`bpm:process-instance:cancel`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `1222` 流程任务的更新（`bpm:task:update`；有效）
- `1224` 租户管理（`—`；有效）
- `1225` 租户套餐（`—`；有效）
- `1226` 租户套餐查询（`system:tenant-package:query`；有效）
- `1227` 租户套餐创建（`system:tenant-package:create`；有效）
- `1228` 租户套餐更新（`system:tenant-package:update`；有效）
- `1229` 租户套餐删除（`system:tenant-package:delete`；有效）
- `1237` 文件配置（`—`；有效）
- `1238` 文件配置查询（`infra:file-config:query`；有效）
- `1239` 文件配置创建（`infra:file-config:create`；有效）
- `1240` 文件配置更新（`infra:file-config:update`；有效）
- `1241` 文件配置删除（`infra:file-config:delete`；有效）
- `1242` 文件配置导出（`infra:file-config:export`；有效）
- `1243` 文件管理（`—`；有效）
- `1255` 数据源配置（`—`；有效）
- `1256` 数据源配置查询（`infra:data-source-config:query`；有效）
- `1257` 数据源配置创建（`infra:data-source-config:create`；有效）
- `1258` 数据源配置更新（`infra:data-source-config:update`；有效）
- `1259` 数据源配置删除（`infra:data-source-config:delete`；有效）
- `1260` 数据源配置导出（`infra:data-source-config:export`；有效）
- `1261` OAuth 2.0（`—`；有效）
- `1263` 应用管理（`—`；有效）
- `1264` 客户端查询（`system:oauth2-client:query`；有效）
- `1265` 客户端创建（`system:oauth2-client:create`；有效）
- `1266` 客户端更新（`system:oauth2-client:update`；有效）
- `1267` 客户端删除（`system:oauth2-client:delete`；有效）
- `2083` 地区管理（`—`；有效）
- `2713` 抄送我的（`bpm:process-instance-cc:query`；有效）
- `2714` 流程分类（`—`；有效）
- `2715` 分类查询（`bpm:category:query`；有效）
- `2716` 分类创建（`bpm:category:create`；有效）
- `2717` 分类更新（`bpm:category:update`；有效）
- `2718` 分类删除（`bpm:category:delete`；有效）
- `2720` 发起流程（`—`；有效）
- `2721` 流程实例（`—`；有效）
- `2722` 流程实例的查询（管理员）（`bpm:process-instance:manager-query`；有效）
- `2723` 流程实例的取消（管理员）（`bpm:process-instance:cancel-by-admin`；有效）
- `2724` 流程任务（`—`；有效）
- `2725` 流程任务的查询（管理员）（`bpm:task:manager-query`；有效）
- `2726` 流程监听器（`—`；有效）
- `2727` 流程监听器查询（`bpm:process-listener:query`；有效）
- `2728` 流程监听器创建（`bpm:process-listener:create`；有效）
- `2729` 流程监听器更新（`bpm:process-listener:update`；有效）
- `2730` 流程监听器删除（`bpm:process-listener:delete`；有效）
- `2731` 流程表达式（`—`；有效）
- `2732` 流程表达式查询（`bpm:process-expression:query`；有效）
- `2733` 流程表达式创建（`bpm:process-expression:create`；有效）
- `2734` 流程表达式更新（`bpm:process-expression:update`；有效）
- `2735` 流程表达式删除（`bpm:process-expression:delete`；有效）
- `2739` 消息中心（`—`；有效）
- `2740` 监控中心（`—`；有效）
- `2913` 流程清理（`bpm:model:clean`；有效）

### 产品组件管理员（`componentAdmin`）

- 无直接菜单关联。

### 割接研发审批（`CUT_RND_APPROVER`）

- `19263` 割接交付（`—`；有效）
- `970445001` 割接工作台（`pms:cutover-task:query`；有效）
- `992602050001` 割接任务工作台（`pms:cutover-task:query`；有效）
- `992602050002` 割接任务查询（`pms:cutover-task:query`；有效）
- `992602072001` 查询P5审批（`pms:cutover-task:query-approval`；有效）
- `992602072002` 处理P5审批（`pms:cutover-task:approve`；有效）

### 割接二线审批（`CUT_SECOND_LINE_APPROVER`）

- `19263` 割接交付（`—`；有效）
- `970445001` 割接工作台（`pms:cutover-task:query`；有效）
- `992602050001` 割接任务工作台（`pms:cutover-task:query`；有效）
- `992602050002` 割接任务查询（`pms:cutover-task:query`；有效）
- `992602072001` 查询P5审批（`pms:cutover-task:query-approval`；有效）
- `992602072002` 处理P5审批（`pms:cutover-task:approve`；有效）

### 数据查询角色（`dataViewer`）

- 无直接菜单关联。

### 项目转包结算人员（`dispatchSettleStaff`）

- 无直接菜单关联。

### 工程管理部（`engineeManager`）

- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `1222` 流程任务的更新（`bpm:task:update`；有效）
- `18000` 项目交付（`—`；有效）
- `992209220341` 工程管理部方案复审（`pms:sol-solution:major-review`；有效）
- `993109180007` 阶段施工计划（`pms:imp-stage-plan:query`；有效）

### 工程管理部主管（`engineeManagerLeader`）

- `18000` 项目交付（`—`；有效）
- `993109180007` 阶段施工计划（`pms:imp-stage-plan:query`；有效）

### 工程管理部方案复审（`ENGINEERING_MANAGEMENT_REVIEW`）

- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `1222` 流程任务的更新（`bpm:task:update`；有效）
- `18000` 项目交付（`—`；有效）
- `18011` 项目总览（`pms:project:query`；停用或删除）
- `18067` 项目列表（`pms:project:query`；有效）
- `18071` 项目详情（`pms:project:query`；有效）
- `19011` 实施方案（`pms:sol-solution:query`；有效）
- `19261` 项目管理（`—`；有效）
- `19262` 工程实施（`—`；有效）
- `19268` 工程准备（`—`；有效）
- `992209220341` 工程管理部方案复审（`pms:sol-solution:major-review`；有效）
- `993109100501` 项目交付（新功能）（`—`；停用或删除）
- `993109100502` 项目列表（`pms:project:query`；有效）
- `993109100503` 项目详情（`pms:project:query`；有效）
- `970000000000099241` 统一交付查询（`pms:delivery:query`；有效）

### 财务AP（`financialAP`）

- 无直接菜单关联。

### 财务人员（`financialStaff`）

- 无直接菜单关联。

### 售前测试专员（`presalesStaff`）

- 无直接菜单关联。

### 技术公告管理员（`probAdmin`）

- 无直接菜单关联。

### 研发人员（`probRD`）

- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `198780` 业务文件查询（`pms:file:query`；有效）
- `198782` 业务文件下载（`pms:file:download`；有效）
- `198783` 业务文件预览（`pms:file:preview`；有效）
- `992602072001` 查询P5审批（`pms:cutover-task:query-approval`；有效）
- `992602072002` 处理P5审批（`pms:cutover-task:approve`；有效）

### 技术支持人员（`probSupporter`）

- `2` 基础设施（`—`；有效）
- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `1243` 文件管理（`—`；有效）
- `18000` 项目交付（`—`；有效）
- `19263` 割接交付（`—`；有效）
- `198780` 业务文件查询（`pms:file:query`；有效）
- `198782` 业务文件下载（`pms:file:download`；有效）
- `198783` 业务文件预览（`pms:file:preview`；有效）
- `970445001` 割接工作台（`pms:cutover-task:query`；有效）
- `970462001` 查询割接闭环（`pms:cutover-task:query-closure`；有效）
- `970484000` 巡检服务（`—`；有效）
- `970484001` 巡检工作台（`—`；有效）
- `970484002` 巡检任务（`pms:inspection-task:query`；有效）
- `970484010` 巡检任务查询（`pms:inspection-task:query`；有效）
- `992602050001` 割接任务工作台（`pms:cutover-task:query`；有效）
- `992602050002` 割接任务查询（`pms:cutover-task:query`；有效）
- `992602072001` 查询P5审批（`pms:cutover-task:query-approval`；有效）
- `992602072002` 处理P5审批（`pms:cutover-task:approve`；有效）

### 项目经理（`PROJECT_MANAGER`）

- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `18000` 项目交付（`—`；有效）
- `18005` 联系人查询（`pms:customer-contact:query`；有效）
- `18006` 联系人新增（`pms:customer-contact:create`；有效）
- `18007` 联系人修改（`pms:customer-contact:update`；有效）
- `18010` 客户联系人（`pms:customer-contact:query`；有效）
- `18017` 项目风险（`pms:project-risk:query`；有效）
- `18022` 项目团队管理（`pms:project-team:query`；有效）
- `18023` 团队成员维护（`pms:project-team:create`；有效）
- `18030` 项目任务查询（`pms:project-task:query`；有效）
- `18031` 项目任务创建（`pms:project-task:create`；有效）
- `18032` 项目任务更新（`pms:project-task:update`；有效）
- `18036` 项目阶段管理（`pms:project-phase:query`；有效）
- `18038` 阶段门禁校验（`pms:project-phase:gate`；有效）
- `18039` 项目风险管理（`pms:project-risk:query`；有效）
- `18040` 风险维护（`pms:project-risk:create`；有效）
- `18067` 项目列表（`pms:project:query`；有效）
- `18069` 项目更新（`pms:project:update`；有效）
- `18071` 项目详情（`pms:project:query`；有效）
- `19000` 资产管理（`—`；有效）
- `19001` 设备档案管理（`pms:device:query`；有效）
- `19006` 设备版本查询（`pms:device-version:query`；有效）
- `19007` 配置日志查询（`pms:device:query`；有效）
- `19008` 设备配置日志（`pms:device:query`；有效）
- `19009` 现场工勘（`pms:sol-site-survey:query`；有效）
- `19011` 实施方案（`pms:sol-solution:query`；有效）
- `19013` 到货签收（`pms:imp-arrival:query`；有效）
- `19014` 硬件安装（`pms:imp-installation:query`；有效）
- `19015` 配置调试（`pms:imp-configuration:query`；有效）
- `19016` 业务联调（`pms:imp-joint-test:query`；有效）
- `19018` 交付件归集（`pms:imp-deliverable:query`；有效）
- `19029` 工勘创建（`pms:sol-site-survey:create`；有效）
- `19030` 工勘更新（`pms:sol-site-survey:update`；有效）
- `19035` 方案创建（`pms:sol-solution:create`；有效）
- `19036` 方案更新（`pms:sol-solution:update`；有效）
- `19042` 到货创建（`pms:imp-arrival:create`；有效）
- `19043` 到货更新（`pms:imp-arrival:update`；有效）
- `19045` 安装创建（`pms:imp-installation:create`；有效）
- `19046` 安装更新（`pms:imp-installation:update`；有效）
- `19048` 配置创建（`pms:imp-configuration:create`；有效）
- `19049` 配置更新（`pms:imp-configuration:update`；有效）
- `19051` 联调创建（`pms:imp-joint-test:create`；有效）
- `19052` 联调更新（`pms:imp-joint-test:update`；有效）
- `19058` 交付件创建（`pms:imp-deliverable:create`；有效）
- `19059` 交付件更新（`pms:imp-deliverable:update`；有效）
- `19061` 交付件归档（`pms:imp-deliverable:archive`；有效）
- `19094` 电子完工证明（`pms:acc-completion-certificate:query`；有效）
- `19096` 交付件检查（`pms:acc-deliverable-checklist:query`；有效）
- `19097` 项目闭环（`pms:acc-project-closure:query`；有效）
- `19098` 归档文档（`pms:acc-archive-document:query`；有效）
- `19100` 完工证明创建（`pms:acc-completion-certificate:create`；有效）
- `19101` 完工证明更新（`pms:acc-completion-certificate:update`；有效）
- `19102` 完工证明删除（`pms:acc-completion-certificate:delete`；有效）
- `19104` 完工证明提交（`pms:acc-completion-certificate:submit`；有效）
- `19110` 交付件创建（`pms:acc-deliverable-checklist:create`；有效）
- `19111` 交付件更新（`pms:acc-deliverable-checklist:update`；有效）
- `19114` 交付件提交（`pms:acc-deliverable-checklist:submit`；有效）
- `19115` 项目闭环创建（`pms:acc-project-closure:create`；有效）
- `19116` 项目闭环更新（`pms:acc-project-closure:update`；有效）
- `19119` 项目闭环提交（`pms:acc-project-closure:submit`；有效）
- `19120` 归档文档创建（`pms:acc-archive-document:create`；有效）
- `19121` 归档文档更新（`pms:acc-archive-document:update`；有效）
- `19124` 归档文档提交（`pms:acc-archive-document:submit`；有效）
- `19189` 换货协同（`pms:imp-material-exch:query`；有效）
- `19190` 换货创建（`pms:imp-material-exch:create`；有效）
- `19191` 换货修改（`pms:imp-material-exch:update`；有效）
- `19193` 换货提交（`pms:imp-material-exch:submit`；有效）
- `19196` 工程交底（`pms:sol-briefing:query`；有效）
- `19197` 交底创建（`pms:sol-briefing:create`；有效）
- `19198` 交底修改（`pms:sol-briefing:update`；有效）
- `19200` 交底生成（`pms:sol-briefing:generate`；有效）
- `19202` 交底发布（`pms:sol-briefing:publish`；有效）
- `19250` 文档模板（`pms:imp-doc-template:query`；有效）
- `19254` 文档模板查询（`pms:imp-doc-template:query`；有效）
- `19260` 客户与资产（`—`；有效）
- `19261` 项目管理（`—`；有效）
- `19262` 工程实施（`—`；有效）
- `19263` 割接交付（`—`；有效）
- `19264` 验收闭环（`—`；有效）
- `19268` 工程准备（`—`；有效）
- `19269` 工程执行（`—`；有效）
- `19270` 资源采供（`—`；有效）
- `19271` 文档表单（`—`；有效）
- `198721` 项目树移动（`pms:project:update`；有效）
- `198722` 进度策略维护（`pms:project:progress-policy:update`；有效）
- `198723` 进度策略提交（`pms:project:progress-policy:submit`；有效）
- `198760` 客户工作台（`pms:customer:query`；有效）
- `198761` 客户查询（`pms:customer:query`；有效）
- `198762` 客户创建（`pms:customer:create`；有效）
- `198763` 客户更新（`pms:customer:update`；有效）
- `198770` 设备管理（`pms:device:query`；有效）
- `198771` 设备查询（`pms:device:query`；有效）
- `198774` 配置Log下载（`pms:device-configuration-log:download`；有效）
- `198780` 业务文件查询（`pms:file:query`；有效）
- `198781` 业务文件上传（`pms:file:upload`；有效）
- `198782` 业务文件下载（`pms:file:download`；有效）
- `198783` 业务文件预览（`pms:file:preview`；有效）
- `198785` 业务文件归档（`pms:file:archive`；有效）
- `198790` 工勘准备查询（`pms:preparation-survey:query`；有效）
- `198791` 工勘准备管理（`pms:preparation-survey:manage`；有效）
- `198792` 工勘项填写（`pms:preparation-survey:fill`；有效）
- `198794` 需求分析查询（`pms:requirement-analysis:query`；有效）
- `198795` 需求分析管理（`pms:requirement-analysis:manage`；有效）
- `930900` 合同订单工作台（`pms:commerce:contract:query`；有效）
- `930903` 交付范围工作台（`pms:commerce:scope:query`；有效）
- `930920` 验收报告（`—`；有效）
- `930921` 验收报告查询（`pms:acceptance:report:query`；有效）
- `930922` 验收报告维护（`pms:acceptance:report:write`；有效）
- `930923` 验收活动完成（`pms:acceptance:report:complete`；有效）
- `930924` 验收报告下载（`pms:acceptance:report:download`；有效）
- `930925` 验收任务执行（`pms:project-task:execute`；有效）
- `930926` 项目任务指派（`pms:project-task:assign`；有效）
- `930930` 满意度管理（`—`；有效）
- `930931` 满意度查询（`pms:acceptance:satisfaction:query`；有效）
- `930932` 满意度管理（`pms:acceptance:satisfaction:manage`；有效）
- `930933` 满意度采集（`pms:acceptance:satisfaction:collect`；有效）
- `930934` 满意度导出（`pms:acceptance:satisfaction:export`；有效）
- `930935` 满意度下载（`pms:acceptance:satisfaction:download`；有效）
- `930936` 满意度调查（`pms:acceptance:satisfaction:query`；有效）
- `970445001` 割接工作台（`pms:cutover-task:query`；有效）
- `970462001` 查询割接闭环（`pms:cutover-task:query-closure`；有效）
- `970462002` 保存割接闭环（`pms:cutover-task:save-closure`；有效）
- `970462003` 提交割接闭环（`pms:cutover-task:submit-closure`；有效）
- `970484000` 巡检服务（`—`；有效）
- `970484002` 巡检任务（`pms:inspection-task:query`；有效）
- `970484010` 巡检任务查询（`pms:inspection-task:query`；有效）
- `970484020` 巡检待办查询（`pms:service-issue:query`；有效）
- `992602040001` 到货签收工作台（`pms:arrival-acceptance:query`；有效）
- `992602040002` 到货签收查询（`pms:arrival-acceptance:query`；有效）
- `992602040003` 创建到货草稿（`pms:arrival-acceptance:create`；有效）
- `992602040004` 编辑本人草稿（`pms:arrival-acceptance:edit-own-draft`；有效）
- `992602040005` 确认到货签收（`pms:arrival-acceptance:confirm`；有效）
- `992602040006` 处理到货差异（`pms:arrival-acceptance:resolve-difference`；有效）
- `992602050001` 割接任务工作台（`pms:cutover-task:query`；有效）
- `992602050002` 割接任务查询（`pms:cutover-task:query`；有效）
- `992602050003` 创建割接任务（`pms:cutover-task:create`；有效）
- `992602050004` 保存人工评估（`pms:cutover-task:save-assessment`；有效）
- `992602050005` 提交人工评估（`pms:cutover-task:submit-assessment`；有效）
- `992602050006` 保存割接采集清单（`pms:cutover-task:save-checklist`；有效）
- `992602050008` 提交割接采集清单（`pms:cutover-task:submit-checklist`；有效）
- `992602062001` 查询割接方案（`pms:cutover-task:query-plan`；有效）
- `992602062002` 保存割接方案（`pms:cutover-task:save-plan`；有效）
- `992602062003` 下载割接初稿（`pms:cutover-task:download-plan`；有效）
- `992602062004` 提交割接方案（`pms:cutover-task:submit-plan`；有效）
- `992602072001` 查询P5审批（`pms:cutover-task:query-approval`；有效）
- `992602072002` 处理P5审批（`pms:cutover-task:approve`；有效）
- `992602090001` 管理备件协同（`pms:cutover-task:manage-spare`；有效）
- `993009232001` 项目计划改版（`pms:project-plan:manage`；有效）
- `993009232002` 项目节点返工（`pms:project-plan:rework`；有效）
- `993109090008` 项目工期查询（`pms:construction-plan:query`；有效）
- `993109090009` 项目工期维护（`pms:construction-plan:duration-manage`；有效）
- `993109180001` 交付件汇总（`pms:imp-deliverable:query`；有效）
- `993109180002` 现场培训（`pms:imp-training:query`；有效）
- `993109180003` 培训记录创建（`pms:imp-training:create`；有效）
- `993109180004` 培训记录更新（`pms:imp-training:update`；有效）
- `993109180005` 培训记录删除（`pms:imp-training:delete`；有效）
- `993109180006` 培训记录外发（`pms:imp-training:issue`；有效）
- `993109180007` 阶段施工计划（`pms:imp-stage-plan:query`；有效）
- `993109180008` 阶段计划创建（`pms:imp-stage-plan:create`；有效）
- `993109180009` 阶段计划调整（`pms:imp-stage-plan:update`；有效）
- `993109180010` 阶段计划提交审批（`pms:imp-stage-plan:submit`；有效）
- `970000000000099224` 统一业务实体办理（`pms:business-model:operate`；有效）
- `970000000000099241` 统一交付查询（`pms:delivery:query`；有效）
- `970000000000099242` 统一交付操作（`pms:delivery:operate`；有效）
- `970000000000099251` 设备配置日志归档（`pms:device-log-archive:query`；有效）
- `970000000000099252` 导入配置日志（`pms:device-log-archive:import`；有效）
- `970000000000099253` 下载配置日志（`pms:device-log-archive:download`；有效）
- `970000000000099254` 配置调试与设备日志（`pms:imp-configuration:query`；有效）
- `970000000000099780` 业务文件查询（`pms:file:query`；有效）
- `970000000000099781` 业务文件上传（`pms:file:upload`；有效）
- `970000000000099782` 业务文件下载（`pms:file:download`；有效）
- `970000000000099783` 业务文件预览（`pms:file:preview`；有效）
- `970000000000099785` 业务文件归档（`pms:file:archive`；有效）
- `970000000000099786` 项目设备清单导出（`pms:project-device:export`；有效）

### 项目管理员（`projectAdmin`）

- 无直接菜单关联。

### 安服质量监督员（`projectAFQC`）

- 无直接菜单关联。

### 项目区域负责人（`projectAreaManager`）

- 无直接菜单关联。

### 项目成员（`projectMember`）

- 无直接菜单关联。

### 项目售前（`projectPreSales`）

- 无直接菜单关联。

### 子项目管理员（`projectSubAdmin`）

- 无直接菜单关联。

### 项目查阅员（`projectViewer`）

- 无直接菜单关联。

### 研发质量监督员（`projectYFQC`）

- 无直接菜单关联。

### 销售代表（`SALES_REPRESENTATIVE`）

- 无直接菜单关联。

### 服务经理（`SERVICE_MANAGER`）

- `1185` 工作流程（`—`；有效）
- `1200` 审批中心（`—`；有效）
- `1201` 我的流程（`—`；有效）
- `1202` 流程实例的查询（`bpm:process-instance:query`；有效）
- `1207` 待办任务（`—`；有效）
- `1208` 已办任务（`—`；有效）
- `1221` 流程任务的查询（`bpm:task:query`；有效）
- `1222` 流程任务的更新（`bpm:task:update`；有效）
- `18000` 项目交付（`—`；有效）
- `18005` 联系人查询（`pms:customer-contact:query`；有效）
- `18006` 联系人新增（`pms:customer-contact:create`；有效）
- `18007` 联系人修改（`pms:customer-contact:update`；有效）
- `18010` 客户联系人（`pms:customer-contact:query`；有效）
- `18017` 项目风险（`pms:project-risk:query`；有效）
- `18022` 项目团队管理（`pms:project-team:query`；有效）
- `18023` 团队成员维护（`pms:project-team:create`；有效）
- `18030` 项目任务查询（`pms:project-task:query`；有效）
- `18031` 项目任务创建（`pms:project-task:create`；有效）
- `18032` 项目任务更新（`pms:project-task:update`；有效）
- `18036` 项目阶段管理（`pms:project-phase:query`；有效）
- `18038` 阶段门禁校验（`pms:project-phase:gate`；有效）
- `18039` 项目风险管理（`pms:project-risk:query`；有效）
- `18040` 风险维护（`pms:project-risk:create`；有效）
- `18067` 项目列表（`pms:project:query`；有效）
- `18069` 项目更新（`pms:project:update`；有效）
- `18070` 服务经理指派（`pms:project:assign`；有效）
- `18071` 项目详情（`pms:project:query`；有效）
- `19000` 资产管理（`—`；有效）
- `19001` 设备档案管理（`pms:device:query`；有效）
- `19006` 设备版本查询（`pms:device-version:query`；有效）
- `19007` 配置日志查询（`pms:device:query`；有效）
- `19008` 设备配置日志（`pms:device:query`；有效）
- `19009` 现场工勘（`pms:sol-site-survey:query`；有效）
- `19011` 实施方案（`pms:sol-solution:query`；有效）
- `19013` 到货签收（`pms:imp-arrival:query`；有效）
- `19014` 硬件安装（`pms:imp-installation:query`；有效）
- `19015` 配置调试（`pms:imp-configuration:query`；有效）
- `19016` 业务联调（`pms:imp-joint-test:query`；有效）
- `19018` 交付件归集（`pms:imp-deliverable:query`；有效）
- `19029` 工勘创建（`pms:sol-site-survey:create`；有效）
- `19030` 工勘更新（`pms:sol-site-survey:update`；有效）
- `19035` 方案创建（`pms:sol-solution:create`；有效）
- `19036` 方案更新（`pms:sol-solution:update`；有效）
- `19038` 方案审核（`pms:sol-solution:audit`；有效）
- `19042` 到货创建（`pms:imp-arrival:create`；有效）
- `19043` 到货更新（`pms:imp-arrival:update`；有效）
- `19045` 安装创建（`pms:imp-installation:create`；有效）
- `19046` 安装更新（`pms:imp-installation:update`；有效）
- `19048` 配置创建（`pms:imp-configuration:create`；有效）
- `19049` 配置更新（`pms:imp-configuration:update`；有效）
- `19051` 联调创建（`pms:imp-joint-test:create`；有效）
- `19052` 联调更新（`pms:imp-joint-test:update`；有效）
- `19058` 交付件创建（`pms:imp-deliverable:create`；有效）
- `19059` 交付件更新（`pms:imp-deliverable:update`；有效）
- `19061` 交付件归档（`pms:imp-deliverable:archive`；有效）
- `19094` 电子完工证明（`pms:acc-completion-certificate:query`；有效）
- `19096` 交付件检查（`pms:acc-deliverable-checklist:query`；有效）
- `19097` 项目闭环（`pms:acc-project-closure:query`；有效）
- `19098` 归档文档（`pms:acc-archive-document:query`；有效）
- `19100` 完工证明创建（`pms:acc-completion-certificate:create`；有效）
- `19101` 完工证明更新（`pms:acc-completion-certificate:update`；有效）
- `19102` 完工证明删除（`pms:acc-completion-certificate:delete`；有效）
- `19103` 完工证明审核（`pms:acc-completion-certificate:audit`；有效）
- `19104` 完工证明提交（`pms:acc-completion-certificate:submit`；有效）
- `19110` 交付件创建（`pms:acc-deliverable-checklist:create`；有效）
- `19111` 交付件更新（`pms:acc-deliverable-checklist:update`；有效）
- `19113` 交付件审核（`pms:acc-deliverable-checklist:audit`；有效）
- `19114` 交付件提交（`pms:acc-deliverable-checklist:submit`；有效）
- `19115` 项目闭环创建（`pms:acc-project-closure:create`；有效）
- `19116` 项目闭环更新（`pms:acc-project-closure:update`；有效）
- `19118` 项目闭环审核（`pms:acc-project-closure:audit`；有效）
- `19119` 项目闭环提交（`pms:acc-project-closure:submit`；有效）
- `19120` 归档文档创建（`pms:acc-archive-document:create`；有效）
- `19121` 归档文档更新（`pms:acc-archive-document:update`；有效）
- `19123` 归档文档审核（`pms:acc-archive-document:audit`；有效）
- `19124` 归档文档提交（`pms:acc-archive-document:submit`；有效）
- `19189` 换货协同（`pms:imp-material-exch:query`；有效）
- `19190` 换货创建（`pms:imp-material-exch:create`；有效）
- `19191` 换货修改（`pms:imp-material-exch:update`；有效）
- `19193` 换货提交（`pms:imp-material-exch:submit`；有效）
- `19196` 工程交底（`pms:sol-briefing:query`；有效）
- `19197` 交底创建（`pms:sol-briefing:create`；有效）
- `19198` 交底修改（`pms:sol-briefing:update`；有效）
- `19200` 交底生成（`pms:sol-briefing:generate`；有效）
- `19201` 交底审核（`pms:sol-briefing:audit`；有效）
- `19202` 交底发布（`pms:sol-briefing:publish`；有效）
- `19250` 文档模板（`pms:imp-doc-template:query`；有效）
- `19254` 文档模板查询（`pms:imp-doc-template:query`；有效）
- `19260` 客户与资产（`—`；有效）
- `19261` 项目管理（`—`；有效）
- `19262` 工程实施（`—`；有效）
- `19263` 割接交付（`—`；有效）
- `19264` 验收闭环（`—`；有效）
- `19268` 工程准备（`—`；有效）
- `19269` 工程执行（`—`；有效）
- `19270` 资源采供（`—`；有效）
- `19271` 文档表单（`—`；有效）
- `198721` 项目树移动（`pms:project:update`；有效）
- `198722` 进度策略维护（`pms:project:progress-policy:update`；有效）
- `198723` 进度策略提交（`pms:project:progress-policy:submit`；有效）
- `198730` 项目授权查询（`pms:project:authorization:query`；有效）
- `198731` 项目授权管理（`pms:project:authorization:manage`；有效）
- `198732` 项目授权撤销（`pms:project:authorization:revoke`；有效）
- `198760` 客户工作台（`pms:customer:query`；有效）
- `198761` 客户查询（`pms:customer:query`；有效）
- `198762` 客户创建（`pms:customer:create`；有效）
- `198763` 客户更新（`pms:customer:update`；有效）
- `198765` 客户删除（`pms:customer:delete`；有效）
- `198766` 客户恢复（`pms:customer:restore`；有效）
- `198770` 设备管理（`pms:device:query`；有效）
- `198771` 设备查询（`pms:device:query`；有效）
- `198774` 配置Log下载（`pms:device-configuration-log:download`；有效）
- `198780` 业务文件查询（`pms:file:query`；有效）
- `198781` 业务文件上传（`pms:file:upload`；有效）
- `198782` 业务文件下载（`pms:file:download`；有效）
- `198783` 业务文件预览（`pms:file:preview`；有效）
- `198785` 业务文件归档（`pms:file:archive`；有效）
- `198790` 工勘准备查询（`pms:preparation-survey:query`；有效）
- `198791` 工勘准备管理（`pms:preparation-survey:manage`；有效）
- `198792` 工勘项填写（`pms:preparation-survey:fill`；有效）
- `198793` 工勘豁免审批（`pms:preparation-survey:waiver-approve`；有效）
- `198794` 需求分析查询（`pms:requirement-analysis:query`；有效）
- `198795` 需求分析管理（`pms:requirement-analysis:manage`；有效）
- `199510` 巡检规则（`pms:inspection-rule:query`；有效）
- `199511` 巡检规则查询（`pms:inspection-rule:query`；有效）
- `199516` 巡检规则选择（`pms:inspection-rule:select`；有效）
- `930900` 合同订单工作台（`pms:commerce:contract:query`；有效）
- `930903` 交付范围工作台（`pms:commerce:scope:query`；有效）
- `930920` 验收报告（`—`；有效）
- `930921` 验收报告查询（`pms:acceptance:report:query`；有效）
- `930922` 验收报告维护（`pms:acceptance:report:write`；有效）
- `930923` 验收活动完成（`pms:acceptance:report:complete`；有效）
- `930924` 验收报告下载（`pms:acceptance:report:download`；有效）
- `930925` 验收任务执行（`pms:project-task:execute`；有效）
- `930926` 项目任务指派（`pms:project-task:assign`；有效）
- `930930` 满意度管理（`—`；有效）
- `930931` 满意度查询（`pms:acceptance:satisfaction:query`；有效）
- `930932` 满意度管理（`pms:acceptance:satisfaction:manage`；有效）
- `930933` 满意度采集（`pms:acceptance:satisfaction:collect`；有效）
- `930934` 满意度导出（`pms:acceptance:satisfaction:export`；有效）
- `930935` 满意度下载（`pms:acceptance:satisfaction:download`；有效）
- `930936` 满意度调查（`pms:acceptance:satisfaction:query`；有效）
- `970445001` 割接工作台（`pms:cutover-task:query`；有效）
- `970462001` 查询割接闭环（`pms:cutover-task:query-closure`；有效）
- `970462002` 保存割接闭环（`pms:cutover-task:save-closure`；有效）
- `970462003` 提交割接闭环（`pms:cutover-task:submit-closure`；有效）
- `970484000` 巡检服务（`—`；有效）
- `970484002` 巡检任务（`pms:inspection-task:query`；有效）
- `970484010` 巡检任务查询（`pms:inspection-task:query`；有效）
- `970484011` 手工创建巡检任务（`pms:inspection-task:create`；有效）
- `970484012` 巡检准备（`pms:inspection-task:prepare`；有效）
- `970484013` 巡检执行（`pms:inspection-task:execute`；有效）
- `970484014` 分配巡检负责人（`pms:inspection-task:assign`；有效）
- `970484015` 取消巡检任务（`pms:inspection-task:cancel`；有效）
- `970484016` 生成与上传巡检报告（`pms:inspection-report:generate`；有效）
- `970484017` 巡检问题标注（`pms:inspection-task:annotate`；有效）
- `970484018` 申请巡检归档（`pms:inspection-task:request-archive`；有效）
- `970484019` 确认巡检归档（`pms:inspection-task:archive`；有效）
- `970484020` 巡检待办查询（`pms:service-issue:query`；有效）
- `970484021` 巡检待办处理（`pms:service-issue:remediate`；有效）
- `970484022` 巡检待办确认闭环（`pms:service-issue:confirm`；有效）
- `992602040001` 到货签收工作台（`pms:arrival-acceptance:query`；有效）
- `992602040002` 到货签收查询（`pms:arrival-acceptance:query`；有效）
- `992602040003` 创建到货草稿（`pms:arrival-acceptance:create`；有效）
- `992602040004` 编辑本人草稿（`pms:arrival-acceptance:edit-own-draft`；有效）
- `992602040005` 确认到货签收（`pms:arrival-acceptance:confirm`；有效）
- `992602040006` 处理到货差异（`pms:arrival-acceptance:resolve-difference`；有效）
- `992602050001` 割接任务工作台（`pms:cutover-task:query`；有效）
- `992602050002` 割接任务查询（`pms:cutover-task:query`；有效）
- `992602050003` 创建割接任务（`pms:cutover-task:create`；有效）
- `992602050004` 保存人工评估（`pms:cutover-task:save-assessment`；有效）
- `992602050005` 提交人工评估（`pms:cutover-task:submit-assessment`；有效）
- `992602050006` 保存割接采集清单（`pms:cutover-task:save-checklist`；有效）
- `992602050008` 提交割接采集清单（`pms:cutover-task:submit-checklist`；有效）
- `992602062001` 查询割接方案（`pms:cutover-task:query-plan`；有效）
- `992602062002` 保存割接方案（`pms:cutover-task:save-plan`；有效）
- `992602062003` 下载割接初稿（`pms:cutover-task:download-plan`；有效）
- `992602062004` 提交割接方案（`pms:cutover-task:submit-plan`；有效）
- `992602072001` 查询P5审批（`pms:cutover-task:query-approval`；有效）
- `992602072002` 处理P5审批（`pms:cutover-task:approve`；有效）
- `992602090001` 管理备件协同（`pms:cutover-task:manage-spare`；有效）
- `993009232001` 项目计划改版（`pms:project-plan:manage`；有效）
- `993009232002` 项目节点返工（`pms:project-plan:rework`；有效）
- `993109090008` 项目工期查询（`pms:construction-plan:query`；有效）
- `993109090009` 项目工期维护（`pms:construction-plan:duration-manage`；有效）
- `993109090010` 项目工期审批（`pms:construction-plan:duration-approve`；有效）
- `993109180001` 交付件汇总（`pms:imp-deliverable:query`；有效）
- `993109180002` 现场培训（`pms:imp-training:query`；有效）
- `993109180003` 培训记录创建（`pms:imp-training:create`；有效）
- `993109180004` 培训记录更新（`pms:imp-training:update`；有效）
- `993109180005` 培训记录删除（`pms:imp-training:delete`；有效）
- `993109180006` 培训记录外发（`pms:imp-training:issue`；有效）
- `993109180007` 阶段施工计划（`pms:imp-stage-plan:query`；有效）
- `993109180008` 阶段计划创建（`pms:imp-stage-plan:create`；有效）
- `993109180009` 阶段计划调整（`pms:imp-stage-plan:update`；有效）
- `993109180010` 阶段计划提交审批（`pms:imp-stage-plan:submit`；有效）
- `970000000000099224` 统一业务实体办理（`pms:business-model:operate`；有效）
- `970000000000099241` 统一交付查询（`pms:delivery:query`；有效）
- `970000000000099242` 统一交付操作（`pms:delivery:operate`；有效）
- `970000000000099251` 设备配置日志归档（`pms:device-log-archive:query`；有效）
- `970000000000099252` 导入配置日志（`pms:device-log-archive:import`；有效）
- `970000000000099253` 下载配置日志（`pms:device-log-archive:download`；有效）
- `970000000000099254` 配置调试与设备日志（`pms:imp-configuration:query`；有效）
- `970000000000099780` 业务文件查询（`pms:file:query`；有效）
- `970000000000099781` 业务文件上传（`pms:file:upload`；有效）
- `970000000000099782` 业务文件下载（`pms:file:download`；有效）
- `970000000000099783` 业务文件预览（`pms:file:preview`；有效）
- `970000000000099785` 业务文件归档（`pms:file:archive`；有效）
- `970000000000099786` 项目设备清单导出（`pms:project-device:export`；有效）

### 超级管理员（`super_admin`）

- `18005` 联系人查询（`pms:customer-contact:query`；有效）
- `18006` 联系人新增（`pms:customer-contact:create`；有效）
- `18007` 联系人修改（`pms:customer-contact:update`；有效）
- `18008` 联系人删除（`pms:customer-contact:delete`；有效）
- `18009` 项目主数据同步（`pms:project:sync`；有效）
- `18010` 客户联系人（`pms:customer-contact:query`；有效）
- `18011` 项目总览（`pms:project:query`；停用或删除）
- `18012` 项目树（`—`；停用或删除）
- `18013` 项目团队（`pms:project-team:query`；停用或删除）
- `18014` 项目任务（`pms:project-task:query`；停用或删除）
- `18016` 项目阶段（`pms:project-phase:query`；停用或删除）
- `18017` 项目风险（`pms:project-risk:query`；有效）
- `18018` 项目全景（`pms:project-panoramic:query`；停用或删除）
- `18020` 项目详情（`pms:project-detail:query`；停用或删除）
- `18022` 项目团队管理（`pms:project-team:query`；有效）
- `18023` 团队成员维护（`pms:project-team:create`；有效）
- `18024` 项目树管理（`—`；停用或删除）
- `18025` 项目移动（`—`；停用或删除）
- `18030` 项目任务查询（`pms:project-task:query`；有效）
- `18031` 项目任务创建（`pms:project-task:create`；有效）
- `18032` 项目任务更新（`pms:project-task:update`；有效）
- `18035` 阶段模板维护（`pms:phase-template:create`；有效）
- `18036` 项目阶段管理（`pms:project-phase:query`；有效）
- `18037` 项目阶段维护（`pms:project-phase:update`；有效）
- `18038` 阶段门禁校验（`pms:project-phase:gate`；有效）
- `18039` 项目风险管理（`pms:project-risk:query`；有效）
- `18040` 风险维护（`pms:project-risk:create`；有效）
- `18041` 项目全景（`pms:project-panoramic:query`；有效）
- `19001` 设备档案管理（`pms:device:query`；有效）
- `19002` 设备创建（`pms:device:create`；有效）
- `19003` 设备更新（`pms:device:update`；有效）
- `19004` 设备删除（`pms:device:delete`；有效）
- `19005` 设备状态变更（`pms:device:status-change`；有效）
- `19006` 设备版本查询（`pms:device-version:query`；有效）
- `19007` 配置日志查询（`pms:device:query`；有效）
- `19008` 设备配置日志（`pms:device:query`；有效）
- `19009` 现场工勘（`pms:sol-site-survey:query`；有效）
- `19010` 需求与接口（`pms:sol-requirement:query`；有效）
- `19011` 实施方案（`pms:sol-solution:query`；有效）
- `19012` 资源就绪（`pms:sol-resource:query`；有效）
- `19013` 到货签收（`pms:imp-arrival:query`；有效）
- `19014` 硬件安装（`pms:imp-installation:query`；有效）
- `19015` 配置调试（`pms:imp-configuration:query`；有效）
- `19016` 业务联调（`pms:imp-joint-test:query`；有效）
- `19017` 实施问题（`pms:imp-issue:query`；有效）
- `19018` 交付件归集（`pms:imp-deliverable:query`；有效）
- `19019` 割接任务（`pms:cut-task:query`；停用或删除）
- `19020` 割接风险（`pms:cut-risk:query`；有效）
- `19021` 割接方案（`pms:cut-plan:query`；停用或删除）
- `19024` 巡检任务（`pms:srv-task:query`；停用或删除）
- `19025` 巡检规则（`pms:srv-rule:query`；停用或删除）
- `19026` 巡检报告（`pms:srv-report:query`；停用或删除）
- `19027` 巡检问题（`pms:srv-issue:query`；停用或删除）
- `19029` 工勘创建（`pms:sol-site-survey:create`；有效）
- `19030` 工勘更新（`pms:sol-site-survey:update`；有效）
- `19031` 工勘删除（`pms:sol-site-survey:delete`；有效）
- `19032` 需求创建（`pms:sol-requirement:create`；有效）
- `19033` 需求更新（`pms:sol-requirement:update`；有效）
- `19034` 需求删除（`pms:sol-requirement:delete`；有效）
- `19035` 方案创建（`pms:sol-solution:create`；有效）
- `19036` 方案更新（`pms:sol-solution:update`；有效）
- `19037` 方案删除（`pms:sol-solution:delete`；有效）
- `19038` 方案审核（`pms:sol-solution:audit`；有效）
- `19039` 资源创建（`pms:sol-resource:create`；有效）
- `19040` 资源更新（`pms:sol-resource:update`；有效）
- `19041` 资源删除（`pms:sol-resource:delete`；有效）
- `19042` 到货创建（`pms:imp-arrival:create`；有效）
- `19043` 到货更新（`pms:imp-arrival:update`；有效）
- `19044` 到货删除（`pms:imp-arrival:delete`；有效）
- `19045` 安装创建（`pms:imp-installation:create`；有效）
- `19046` 安装更新（`pms:imp-installation:update`；有效）
- `19047` 安装删除（`pms:imp-installation:delete`；有效）
- `19048` 配置创建（`pms:imp-configuration:create`；有效）
- `19049` 配置更新（`pms:imp-configuration:update`；有效）
- `19050` 配置删除（`pms:imp-configuration:delete`；有效）
- `19051` 联调创建（`pms:imp-joint-test:create`；有效）
- `19052` 联调更新（`pms:imp-joint-test:update`；有效）
- `19053` 联调删除（`pms:imp-joint-test:delete`；有效）
- `19054` 实施问题创建（`pms:imp-issue:create`；有效）
- `19055` 实施问题更新（`pms:imp-issue:update`；有效）
- `19056` 实施问题删除（`pms:imp-issue:delete`；有效）
- `19057` 实施问题验证（`pms:imp-issue:verify`；有效）
- `19058` 交付件创建（`pms:imp-deliverable:create`；有效）
- `19059` 交付件更新（`pms:imp-deliverable:update`；有效）
- `19060` 交付件删除（`pms:imp-deliverable:delete`；有效）
- `19061` 交付件归档（`pms:imp-deliverable:archive`；有效）
- `19062` 割接任务创建（`pms:cut-task:create`；有效）
- `19063` 割接任务更新（`pms:cut-task:update`；有效）
- `19064` 割接任务删除（`pms:cut-task:delete`；有效）
- `19065` 割接任务审核（`pms:cut-task:audit`；有效）
- `19066` 割接风险创建（`pms:cut-risk:create`；有效）
- `19067` 割接风险更新（`pms:cut-risk:update`；有效）
- `19068` 割接风险删除（`pms:cut-risk:delete`；有效）
- `19069` 割接方案创建（`pms:cut-plan:create`；有效）
- `19070` 割接方案更新（`pms:cut-plan:update`；有效）
- `19071` 割接方案删除（`pms:cut-plan:delete`；有效）
- `19072` 割接方案审核（`pms:cut-plan:audit`；有效）
- `19079` 巡检任务创建（`pms:srv-task:create`；停用或删除）
- `19080` 巡检任务更新（`pms:srv-task:update`；停用或删除）
- `19081` 巡检任务删除（`pms:srv-task:delete`；停用或删除）
- `19082` 巡检规则创建（`pms:srv-rule:create`；停用或删除）
- `19083` 巡检规则更新（`pms:srv-rule:update`；停用或删除）
- `19084` 巡检规则删除（`pms:srv-rule:delete`；停用或删除）
- `19085` 巡检报告创建（`pms:srv-report:create`；停用或删除）
- `19086` 巡检报告更新（`pms:srv-report:update`；停用或删除）
- `19087` 巡检报告删除（`pms:srv-report:delete`；停用或删除）
- `19088` 巡检问题创建（`pms:srv-issue:create`；停用或删除）
- `19089` 巡检问题更新（`pms:srv-issue:update`；停用或删除）
- `19090` 巡检问题删除（`pms:srv-issue:delete`；停用或删除）
- `19094` 电子完工证明（`pms:acc-completion-certificate:query`；有效）
- `19095` 验收管理（`pms:acc-acceptance:query`；停用或删除）
- `19096` 交付件检查（`pms:acc-deliverable-checklist:query`；有效）
- `19097` 项目闭环（`pms:acc-project-closure:query`；有效）
- `19098` 归档文档（`pms:acc-archive-document:query`；有效）
- `19100` 完工证明创建（`pms:acc-completion-certificate:create`；有效）
- `19101` 完工证明更新（`pms:acc-completion-certificate:update`；有效）
- `19102` 完工证明删除（`pms:acc-completion-certificate:delete`；有效）
- `19103` 完工证明审核（`pms:acc-completion-certificate:audit`；有效）
- `19104` 完工证明提交（`pms:acc-completion-certificate:submit`；有效）
- `19105` 验收创建（`pms:acc-acceptance:create`；有效）
- `19106` 验收更新（`pms:acc-acceptance:update`；有效）
- `19107` 验收删除（`pms:acc-acceptance:delete`；有效）
- `19108` 验收审核（`pms:acc-acceptance:audit`；有效）
- `19109` 验收提交（`pms:acc-acceptance:submit`；有效）
- `19110` 交付件创建（`pms:acc-deliverable-checklist:create`；有效）
- `19111` 交付件更新（`pms:acc-deliverable-checklist:update`；有效）
- `19112` 交付件删除（`pms:acc-deliverable-checklist:delete`；有效）
- `19113` 交付件审核（`pms:acc-deliverable-checklist:audit`；有效）
- `19114` 交付件提交（`pms:acc-deliverable-checklist:submit`；有效）
- `19115` 项目闭环创建（`pms:acc-project-closure:create`；有效）
- `19116` 项目闭环更新（`pms:acc-project-closure:update`；有效）
- `19117` 项目闭环删除（`pms:acc-project-closure:delete`；有效）
- `19118` 项目闭环审核（`pms:acc-project-closure:audit`；有效）
- `19119` 项目闭环提交（`pms:acc-project-closure:submit`；有效）
- `19120` 归档文档创建（`pms:acc-archive-document:create`；有效）
- `19121` 归档文档更新（`pms:acc-archive-document:update`；有效）
- `19122` 归档文档删除（`pms:acc-archive-document:delete`；有效）
- `19123` 归档文档审核（`pms:acc-archive-document:audit`；有效）
- `19124` 归档文档提交（`pms:acc-archive-document:submit`；有效）
- `19130` 项目组合（`pms:portfolio:query`；有效）
- `19131` 组合创建（`pms:portfolio:create`；有效）
- `19132` 组合修改（`pms:portfolio:update`；有效）
- `19133` 组合删除（`pms:portfolio:delete`；有效）
- `19134` 组合发布（`pms:portfolio:publish`；有效）
- `19135` 服务等级（`pms:service-level:query`；有效）
- `19136` 等级创建（`pms:service-level:create`；有效）
- `19137` 等级修改（`pms:service-level:update`；有效）
- `19138` 等级删除（`pms:service-level:delete`；有效）
- `993109100501` 项目交付（新功能）（`—`；停用或删除）
- `993109100511` 统一业务实体（`pms:business-model:query`；有效）
- `970000000000099224` 统一业务实体办理（`pms:business-model:operate`；有效）
- `970000000000099241` 统一交付查询（`pms:delivery:query`；有效）
- `970000000000099242` 统一交付操作（`pms:delivery:operate`；有效）

### 普通用户（`user`）

- 无直接菜单关联。

### 用户管理员（`userManager`）

- 无直接菜单关联。

### 项目维保回访专员（`warrantyCallbacker`）

- 无直接菜单关联。

## 重点复核

- 技术支持人员与研发人员的 V530 直接关联已与本地一致；w04287 的真实浏览器复现中，待办查询及割接审批候选资格正常，但审批冻结文件元数据请求返回 403，需核查运行时缓存与文件对象授权，不可由静态清单认定根因。
- 两角色未直接关联 `bpm:task:update`；割接 P5 使用专用 `pms:cutover-task:approve`，不能将通用 BPM 办理权限当作已确定缺项直接授予。
- 目标中 21 个角色没有直接菜单关联；它们可能是业务身份角色，不能直接认定为错误。117 个启用用户的所有有效角色合计无启用菜单关联，需确认账号用途；角色间用户数有重叠。
- 918 条有效用户角色关联对应停用用户；本次没有发现对应缺失角色、删除用户或删除角色的关联，不能凭此清理历史。
- 目标存在指向停用菜单的角色关联；包括旧项目入口和旧割接/巡检入口，不能为了消除差异而启用或恢复退役入口。
- 不存在有效角色菜单重复关联或有效角色编码重复。
- 对比有效权限码，`SERVICE_MANAGER` 覆盖 `PROJECT_MANAGER` 的全部直接权限码；实际项目范围及身份仍需单独判定。
- 另有 1 个启用账号没有任何启用角色，未包含在上述“有角色但无菜单”的 117 个账号内；其用途待确认。
- 导航父级直接关联待核实：`CUT_SECOND_LINE_APPROVER` 和 `CUT_RND_APPROVER` 都关联割接交付 `19263`，但未在同角色直接关联项目交付父级 `18000`；`ENGINEERING_MANAGEMENT_REVIEW` 的项目列表与项目详情父级 `993109100501` 当前停用。这不代表账号必然无法访问，其他角色可能提供父级，且不得直接启用已退役菜单。
- 本报告未模拟全部账号、全部对象及每种审批状态；差异不等同授权裁决。

## 提交前静态复核

- 本轮仅检查本地文件和既有回执，未连接数据库或执行SQL；上文数据库与浏览器结论来自既有核查，不是本轮重测。
- V530当前文件SHA256为`43075095ffda02e56d0edb8ca59b70e8205b04013425bc8e78140b310a549017`，与`.run/role-permission-178-20261031/verification.json`中的成功修正回执一致；本轮未改写该已执行文件。SQL文件头的“尚未执行”是编写时原始注释，为保护已执行版本校验和而保留，实际状态以既有回执为准。
- 未执行的一次性脚本对齐V530的临时表排序规则，并拆分重复引用同一临时表的前置校验，保留显式事务及失败即停止的执行要求。两份脚本的33个唯一角色菜单组合与既有回执一致：技术支持23项、研发10项。
- 一次性脚本与V530是同一授权集合的两种入口，不应作为两项独立变更重复部署；本轮未验证一次性脚本的数据库运行结果。
