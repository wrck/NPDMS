# 割接完整只读、方案预览与闭环重复上传验证

来源：本次用户授权，关联 F-CUT-005、F-CUT-006。当前为部署前验证记录，不能视为浏览器业务闭环通过。

- 完整任务：当前审批候选可只读精确任务；实际作出审批决定的人员保留历史只读权限。未领取角色成员仅当前共享待办有效。任务、清单、方案、闭环和附件逐次校验租户及精确任务；审批只读标记和空写动作阻止编辑。使用 CUT 保存的项目、设备及就绪引用，不绕过 PROJ/IMP 的普通数据范围。BPM 功能未修改。
- 方案预览：复用公共 PLT 文件组件及授权读取。Word 在本机 LibreOffice 转为 PDF，返回前再次检查预览权限、Owner、引用及版本可用性，不写新业务版本，不修改原件或历史，不发送到外部 Office 服务。
- 闭环重复上传：读取当前 PLT 引用，向上传器传入 artifactId 和 expectedReferenceVersion；上传后推进本地引用游标。引用读取失败禁用上传，可重试，现有表单与文件卡片保留。

## 已执行验证

- 后端首轮 CUT 74 项和 PLT 5 项通过，含真实 DOC/DOCX 转换。后续只读来源修复编译通过，75 项 CUT 中仅新测试的多余普通 readiness 桩导致严格桩检查失败；修正该测试后，受影响的 TaskQuery 7 项和 SourceRead 1 项通过，未放宽产品校验或 Mockito 严格规则。日志：`.run/cutover-complete-read-tests.log`、`.run/cutover-complete-read-final-tests.log`、`.run/cutover-task-source-read-final-tests.log`。
- 前端审批及只读路由 42 项、闭环/详情/项目来源 13 项、公共文件组件 6 项通过；补充实际点击引用读取重试，闭环 7 项再次通过，表单节点保持同一实例。对应日志：`.run/cutover-complete-read-ui-tests.log`、`.run/cutover-closure-source-read-ui-tests.log`、`.run/cutover-preview-ui-tests.log`、`.run/cutover-closure-retry-final-ui-tests.log`。修改文件 ESLint 通过。
- 真实转换的 DOC 和 DOCX 各两页，内嵌图片及原文件字节保持不变。通过 Poppler 渲染并逐页检查中文、英文、表格、图片及分页；该合成样本正常，不据此保证任意 Word 文档像素一致。证据位于 `.run/office-preview-acceptance/`。
- 浏览器已打开用户任务 2110653642904092672，其闭环仍为草稿、两份已有 cachemem.png 附件均 V1，表单原结果保留，未提交结束流程。点击原文件下载的 HTTP 响应声明 257643 字节，但浏览器监测工具返回空响应体，未取得可校验的原文件副本，因此尚未实际再次上传，不能宣称新增版本成功。

## 冻结及部署状态

完整构建成功，22 项已验证修复基线通过，冻结清单为 `.run/build/releases/29ee2548-77bc-4cc8-b236-455678877395/manifest.json`；`worktree-backend.ps1 -ValidateOnly` 预检通过。证据：`.run/cutover-read-preview-closure-build.log`、`.run/cutover-read-preview-closure-precheck.log`。闭环展示许可同时接受 query-closure 或 query-approval，写操作仍受原权限及 readonly 约束，相关 ESLint 通过。

用户本轮明确回答“暂不重启”，因此未执行部署或服务启停，59191 与 48182 均保持当前运行。闭环前端修复由现有 Vite 热更新提供；新增后端预览及完整只读行为尚未运行验收。

后续服务经理改派候选、清单人工证据及文件格式变更已继续修改后端来源，因此上述冻结清单不包含这些新增改动，先前预检不能用作当前来源的部署结论；再次部署须重新构建冻结并预检。本轮未擅自重启。

## 待完成

获准部署后执行真实浏览器完整只读/Word 预览；用户原闭环文件再次上传与失败重试。浏览器监测未返回下载的二进制响应体，仅能说明未取得原文件副本，不能据此断言实际下载失败。未更改闭环结果、提交归档或覆盖既有附件。
