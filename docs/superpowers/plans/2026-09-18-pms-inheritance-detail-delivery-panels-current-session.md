# PMS-Inheritance 项目详情交付面板补齐：当前会话记录

> 日期 2026-09-18；基准 `0c6509a37`；分支 `codex/domain-migration`。
> 需求来源：[项目交付页面数据DemoV2](../../../需求/项目交付/项目交付页面数据DemoV2.html)（21 个子页面）。
> 目标视图：`pms/project/inheritance/detail/index.vue`（菜单 18020 'project-detail' → PmsProjectInheritanceDetail，路由域 `/pms-inheritance`）。
> 项目表约束（用户指定）：补全面板关联的项目表必须是 `proj_project`。

## 范围判定

- 详情视图主数据本就消费 `/pms/projects`（`ProjectMasterController` → `ProjectMasterDO` = `@TableName("proj_project")`），导轨 17 个既有面板已覆盖 Demo 大部（0.1 客户与联系人、1.1 基本信息/实施范围、1.2 项目成员、2.1 用户联系人、2.2 工勘准备、2.3 需求分析、3.1 项目工期、4.x/5.x 交付流程 ProjectFlowPanel、6.2 满意度、6.3 验收报告、6.4 实例交付件等）。
- 与 [2026-09-17 交付详情视图补齐](2026-09-17-project-delivery-detail-view-completion-current-session.md) 同口径的真实缺口 3 个（该次补的是另一视图 `pms/project/project-detail`，本视图未同步）：Demo 1.1.1 序列号详情、Demo 1.1.1.1 配置Log、Demo 2.2.1 物料换货。
- Demo 6.1 现场培训无后端既有实现（同前次判定：问卷推送由满意度模块承接），本次不造假页面。

## 交付

- `inheritance/detail/DeliveryModuleTable.vue`（新增，配置驱动模块表面板）：props `{config, projectId}`；表格+分页+行详情弹窗+行内操作（confirm→run→重载）+前往完整页面；状态值→{label,tone} 映射。
- `inheritance/detail/index.vue`：
  - 项目概览导轨（overviewSteps）新增 `序列号详情`（pms:equipment:query）、`配置Log`（pms:equipment-config:query）；交付准备导轨新增 `物料换货`（pms:imp-material-exch:query，V256 现行权限码）；TAB_KEYS 同步三个键（深链 `?tab=` 可用）。
  - 三配置全部消费既有真实 API，不新增后端：序列号详情 = `/pms/equipment/page?projectId`；配置Log = 后端分页仅按设备过滤，先取项目设备（≤200）再按设备合并日志、enrich 序列号、按采集时间倒排切片（含 fileUrl 下载操作，Demo "log文件可支持点击下载"）；物料换货 = `/pms/imp-material-exch/page?projectId`，行内 提交（草稿）、推送CRM（已通过且 PENDING；后端 external 集成保留中，点击将返回 MATERIAL_EXCH_CRM_NOT_CONNECTED，属如实暴露契约）。
  - 状态口径按后端 DO 注释：设备 0在库/1在用/2故障/3维修中/4已报废；换货 0草稿~6已终止。完整页面路由 `/pms/imp-material-exch`（V257 对齐后现行值）。

## 验证（dev 18181 / backend 58280）

- `pnpm ts:check`（vue-tsc 全仓）：0 错误（首跑 2 个类型错误已修复：el-button type 联合类型、window.open 返回值）。
- vite 按需编译：两个改动模块 transform 200，无 error。
- 真实 API（dev 登录令牌）：`/pms/projects/1010` 返回 `codeRuleVersion:"LEGACY_IMPORT"`（V260 导入 proj_project 的标记，项目表链路证据）；`/pms/equipment/page?projectId=1010` 返回序列号/位置/维保起止/状态真实行；`/pms/equipment/config-log/page?equipmentId=1010` 返回 configType/collectedAt/fileHash/fileUrl 真实行；`/pms/imp-material-exch/page?projectId=1002` 返回换货单（已通过、crmPushStatus=CLOSED，推送操作不显示，符合守卫）。
- 实库直连（proj_project SELECT）被容器 MySQL 凭据阻断，未执行；以 API 响应证据替代。

## 真实浏览器验收（agent-browser 0.31.1，dev 18181 + backend 58280，项目 1002）

- 路由事实：`/pms-inheritance/project-detail?projectId=1002`（菜单域 993109100501，admin UI 登录 admin/admin123 成功）。
- 导轨 9 段 24 页签逐一点击断言可见面板：全部渲染操作界面（表格/描述列表/按钮/输入，empty=true 的为项目数据"暂无"状态，界面存在）。三新面板实数据断言：序列号详情 3 行（表头 序列号/设备名称/产品型号/安装位置/维保起止/状态，状态列映射"在库"生效）；配置Log 3 台设备合并 3 条日志（含 3 个 下载 行内操作）；物料换货 1 行（ME-V35-001 已通过 + crmPushStatus=CLOSED，行内 提交/推送CRM 均不显示，守卫符合）。
- 交付流程 S0~S6 逐阶段点击：7 个阶段全部渲染阶段面板（阶段业务办理/刷新业务结果/重新加载阶段绑定 + 阶段任务表；STAGE_CONTRACT_NOT_FROZEN 为如实状态提示）。
- 会话网络 4xx/5xx：0。
- 证据截图：`.run/acceptance-detail-{base,sn-result,config-log,material-exch}.png`。

## 真实缺陷报告（另一视图未提交工作树代码，未改动）

`pms/project/project-detail/index.vue`（2026-09-17 会话产物，与本次并行）存在三处口径问题：
1. 设备 statusMap `2:维修中、4:已借出` 与 EquipmentDO 注释（2故障、4已报废）不符；
2. 推送CRM show 条件 `r.status === 3 && !r.crmPushStatus`：换货单创建即写 PENDING，`!r.crmPushStatus` 恒假，可推送行永远不显示操作（应为 `crmPushStatus === 'PENDING'`）；
3. 完整页面路由 `/pms/eng-material-exch` 为 V257 对齐前旧值（现行 `/pms/imp-material-exch`）。

## 深验收：展示完整性+操作（第二轮，需求"不单单有页签，要有完整展示信息和操作"）

第一轮只断言"页签存在+界面渲染"；按加深口径复查展示字段口径与操作实效，补三处展示缺口后再验收：

- 展示缺口修复（`DeliveryModuleTable.vue` 列类型扩展 `enum`/`html`）：
  - `enum` 列：字符串值→展示标签映射（按列 `valueMap`）；`html` 列：去标签展示（stripHtml）。
  - 物料换货补列：换货类型（INCOMPATIBLE→不兼容 等 4 值，与后端 VO 注释口径一致）、规格型号、单位、备注；`不符合项说明` reason 富文本去标签；`CRM推送` PENDING待推送/SENT已推送/RECEIVED已回执/CLOSED已关闭。
  - 配置Log补 `配置内容` configContent 列（Demo 1.1.1.1 字段）。
  - 序列号详情补 Demo 1.1.1 跨页操作：行内 `配置Log` → `switchTab('config-log')`。
- `pnpm ts:check`：直接 node 调 vue-tsc EXIT=0（pnpm 包装层偶发 exit 134 原生崩溃，与类型无关，直跑通过）。

真实浏览器（agent-browser，项目 1002，dev 18181）：

- 24 页签再遍历：全部可见面板渲染展示+操作（表格/描述/按钮/输入统计齐全，empty 为项目数据"暂无"口径）。
- 物料换货行断言：表头 12 列齐全，行值映射生效——换货类型"不兼容"、reason 富文本已去标签为纯文本、CRM推送"已关闭"、状态"已通过"。
- 序列号详情：3 行各行内 `配置Log` 操作；点击后页签实际切到"配置Log"（跨页操作实效断言通过）。
- 配置Log：行点击行详情弹窗打开，字段含 `配置内容`（SIGNATURE_UPDATE / signature library updated to v3.2.1 / hash-1002-sig-20260310），取消关闭正常；`下载` 操作存在于有 fileUrl 行。
- 客户与联系人（Demo 0.1）：维护客户信息（基础/行业/拓展）、新增联系人、载入客户联系人、变更历史操作齐全。
- 用户联系人（Demo 2.1）：表头含 主联系人/状态/操作；`新增联系人` 弹窗字段与 Demo 一致（客户/来源/姓名/部门/职务/角色/手机/电话/邮箱/主联系人/状态/备注），开+取消不落数据。项目 1002 无既有联系人行，行级 编辑/删除/失效 依托表头与弹窗结构存在，实数据操作留待有行项目。
- 会话网络：135 admin-api 请求，4xx/5xx = 0。
- 证据截图：`.run/acceptance-detail2-{base,config-log,contacts}.png`。

## 写操作端到端（第三轮，用户"继续"且确认外部接口不扩大范围）

- **真实缺陷1（本次交付，已修）**：三面板"前往完整页面"path 两处 404——第一轮只验面板渲染未点链接。真实菜单路由：设备 `/customer-asset/equipment`、物料换货 `/pms/engineering/procurement/imp-material-exch`（配置Log `/pms/asset/equipment-config-log` 本就正确），已按菜单树 `get-permission-info` 修正。
- **真实缺陷2（pms-module-engineering 既有缺陷，已修）**：`MaterialExchangeServiceImpl.updateStatus` 手动 `setVersion(getVersion()+1)` 与 DO `@Version` + `OptimisticLockerInnerInterceptor` 双重递增——插件把手动+1 后的值当旧值写 WHERE，恒比 DB 版本超前一位，**所有状态流转 0 行静默失败**（返回 code 0 data true，DB update_time 不变；general log 抓到 `WHERE version=1` 而 DB version=0 实证）。修复：删除手动递增，交插件自动 WHERE/SET。范围排查：其余模块同写法均为 wrapper 更新或 DO 无 `@Version`（如 AcceptanceActivityDO），不受影响，缺陷仅此一处。
- 构建注意：`mvn -pl yudao-server package` 的 repackage 从本地仓库 `D:/Maven/Repository` 解析 SNAPSHOT，需先 install 变更模块；增量下 jar 可能 no-op，删除 `target/yudao-server.jar*` 后重建可强制生效（本次 asset 曾混入 9/2 旧件致启动失败 DeviceScopeFactApi 缺失）。
- **写链路验证（dev 58280，npdms_domain_test，项目1002，UI+API 混合）**：
  - 提交：详情页面板行内 提交（confirm→PUT submit）草稿→已提交，面板即时刷新；
  - 审批：完整页 审批对话框（通过/驳回/退回修改/转办/加签 + 审批人/意见）PASS → 已提交→已通过，approverUserId/approveAction 落库，crmPushStatus 保持 PENDING；
  - 推送CRM：详情页 行内 推送CRM（已通过且 PENDING 才显示）→ PUT push-crm 返回 `MATERIAL_EXCH_CRM_NOT_CONNECTED` 错误体，**状态/推送状态/版本均不变**——外部 CRM 集成保持预留，未扩大范围、未伪造成功；
  - 退回/删除/终止接口同修复后正常（测试数据清理时验证 RETURN→草稿→delete）。
- 测试数据（ME-ACCEPT-1002-01/02/03）已清理：30002 退回后删除、30004 直接删除、30003（已通过终态）DB 软删；项目 1002 换货单恢复为仅真实 ME-V35-001。
- 创建表单中文载荷经 Git Bash curl 会 GBK 损坏致 500（Invalid UTF-8），改用 `--data-binary @file` 纯 ASCII 解决。

## 未做

- 用户联系人行级 编辑/删除/失效 写操作端到端：项目 1002 无既有联系人行；新增弹窗字段与 Demo 2.1 一致已验证，实数据操作留待有行项目按各自 Feature 验收路径进行。
- 推送CRM 的真实外部对接：external 集成按契约保留（MATERIAL_EXCH_CRM_NOT_CONNECTED），不得扩大范围。
- 提交未执行（等待用户明确指令；含本文件与另一会话的未提交产物）。
