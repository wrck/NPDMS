# 模板993009900016及项目992203060198工期与分工配置恢复

需求方明确授权恢复“工期与分工界面确认”原业务配置，不用施工计划审批替代，保留历史及校验。来源为docs/generated/2026-10-06-visio-system-record-template/source-template-published-designer.json中的PRE_DURATION及D_INTERFACE，与原2026-09-22来源Designer一致。

## 恢复范围

- 名称恢复“工期与分工界面确认”。
- 业务视图恢复2101606310708887554、PLN_CONSTRUCTION_PLAN，Owner为PLN、对象类型CONSTRUCTION_PLAN，复用工期面板。
- PRE_DURATION_DONE恢复原ALL组合：DURATION_EFFECTIVE与DELIVERABLE/D_INTERFACE；不使用CONSTRUCTION_PLAN_APPROVED。
- 恢复原D_INTERFACE“分工界面（含辅料）”定义：S1/PRE_DURATION任务级、必选、至少1项，来源UPLOAD或BUSINESS_RESULT；不伪造或代上传材料。
- 原联系人修复、割接配置、其他任务、阶段、规则、门禁、里程碑和交付件定义保持。剔除服务端业务视图快照后逐项比对，差异仅上述任务、规则表达式及D_INTERFACE。

模板草稿通过正式PUT保存，Compiler valid=true、issues=[]，重新读取确认恢复。模板草稿另有之前未发布的PRE_CONTACT、DEPLOY_CUTOVER及其规则差异，不能把它们默认为本轮新增；需求方已明确批准发布完整草稿，包含既有联系人和割接改动。正式预检后发布V3/993009900054；发布Designer与批准草稿完全一致，原发布版本保留。回执.run/duration-template-published-receipt.json。

## 项目生效及历史保护

初次预览正确返回STARTED_BINDING_IMMUTABLE；原任务已经完成，不能直接覆盖旧轮次绑定。现有rework-apply校验也禁止夹带交付件新增，未修改或绕过该约束。

依次使用现有正式命令完成两次前向计划修订：

1. V5/2104870198458802234仅新增原D_INTERFACE。预览唯一直接变更deliverable:D_INTERFACE/ADD，无任务直接修改、门禁或里程碑定义修改；服务端携带expectedPreview重验后生效。
2. V6/2104870198458802235仅恢复PRE_DURATION名称、绑定与完成条件；原生正式预览确认唯一直接任务更新。事务rework-apply选中task:PRE_DURATION，并由服务端纳入所属stage:S1；无交付件/里程碑/门禁修改。新任务第2轮2109618734363537410、新S1第3轮2109618734250291202，其他已完成任务和阶段不返工。

16条原已结束轮次的计划、状态、起止、提交及规则结果逐项保持。只有被新轮次替代的两个旧轮次current由true改false，这是当前指针变更，不是历史事实覆盖。总轮次数从25到27。原完成轮次保留原业务绑定，不以恢复配置重算历史；当前新轮次使用V6工期与分工条件。

当前工期任务IN_PROGRESS，D_INTERFACE待提交，未强制自动完成。真实浏览器核对：导航及任务标题恢复原名称，实际渲染工期面板，显示有效工期V1、2026-10-01至2027-03-31、182天；必选“分工界面（含辅料）”显示待提交，刷新后保持。工勘结束日期与现工期不一致提示原样保留，不修改业务数据消除提示。源API的presentation为AVAILABLE，注册精确匹配PLN_CONSTRUCTION_PLAN，无EXECUTION_CONTEXT_STALE或VIEW_NOT_BOUND。

证据：.run/duration-restore-before.json、duration-restore-candidates.json、duration-restore-template-validation.json、duration-interface-apply.json、duration-task-rework-apply.json、duration-restore-verified.json。两个apply回执内key为本地关联标识，已注明recordedKeyIsLocalCorrelationOnly；实际请求使用原API帮助函数生成的幂等键，未将本地标识误用于重放，未重试成功写入。

## 恢复场景暴露的事务补强

此前S1读取补丁只在无代理单测验证捕获GRAPH_TARGET_STALE返回阻塞。真实返工激活S1后，内层inspect事务拦截器已标记回滚，外层返回时触发UnexpectedRollbackException，readiness返回500；不能把无原业务错误弹窗视为完整查询成功。

最小修复为仅在只读inspect入口对ServiceException设置noRollbackFor，由外层调用者决定回滚；readiness仍仅转换精确GRAPH_TARGET_STALE，其余权限、租户、图与版本异常继续传播并由外层回滚。实际resolve推进命令及其事务规则未改。

新增真实Spring事务拦截器测试覆盖内外参与事务：阻塞读取正常提交、无写命令动作；外项目图错误继续回滚；推进命令拒绝非PENDING目标并回滚。相关后端50项通过（图14、readiness15、推进21），日志.run/s1-readiness-tests.log。不是用模拟正常页面替代事务测试。

## 发布、部署恢复及最终验收

需求方分别明确批准完整模板草稿发布及本次59191重启。模板已发布V3/993009900054，项目独立有效计划仍为V6；模板发布没有自动迁移其他已有项目。

事务补丁第一次完整构建在2026-10-12 20:25:22成功并冻结，部署预检通过；实际启动暴露并行CUT增量的Spring循环依赖。循环路径经过需求分析、项目操作入口策略、CutoverProjectOperationAdapter、割接intake及实施就绪服务，返回需求分析依赖。未启用全局允许循环依赖，也未绕过权限或状态校验。先通过官方入口回滚此前健康冻结包59cbddfc-cd37-438e-8763-548175be3ab7，恢复59191（PID33908），保留模板V3及项目V6数据。

最小装配修复：CutoverProjectOperationAdapter只在取得CutoverTaskIntakeApi时使用@Lazy构造器参数，操作元数据注册不提前构造完整业务链；原操作描述、授权、版本、对象与执行命令全部不变。完整应用启动是真实验证，未将编译成功当作可运行。

最终scripts/worktree-build.ps1在2026-10-12 20:44:07全模块BUILD SUCCESS，日志.run/duration-restore-deployment-retry-build.log。并行源码仍变动；沿用已授权的成功编译产物发布策略，在同一工作树锁内核对实际包时间、事务补丁和Lazy参数注解字节，并通过原22项修复基线后官方冻结。未修改严格默认脚本或既有修复校验。

最终发布清单.run/build/releases/087468f2-9d04-40fe-a7d8-38a8da4d3ab3/manifest.json，SHA256 e6af65cb20f9e94ca0536d637b26fc1f830a4e46a29bdb1a9f9c9ad73764e7c6，source记录并行源码授权及实际构建时间。使用scripts/worktree-backend.ps1 -Manifest ... -ApprovedRestart重试已批准部署，local,dac，新PID27808。2026-10-12 20:46:15实际应用启动，backend healthy；DAC没有启停，独立检测dac healthy。

真实API验收：返工中的S1、后续S2/S3已完成历史保留时，stage-advance-readiness返回code=0、advanceAllowed=false、nextStage=null、guidance=GRAPH_TARGET_STALE；原阻塞判定保留，不再因UnexpectedRollback返回500。没有将GRAPH_TARGET_STALE业务原因隐藏为允许推进。16条原已结束轮次与重启前逐项相同，总27条轮次，项目计划V6、模板发布V3均保持。证据.run/duration-restoration-final-runtime.json。

真实浏览器重新打开S1入口无原错误弹窗或系统异常；工期任务显示原工期面板及必选待提交的分工界面，刷新成功，无VIEW_NOT_BOUND或EXECUTION_CONTEXT_STALE。执行历史明确显示工期任务第2轮“工期与分工界面确认/进行中”及原第1轮“施工计划/已完成”，S1第3轮进行中及原第1/2轮完成历史保留。历史显示原名称正是原冻结证据未覆盖。

本次覆盖配置恢复、原校验、事务边界及应用装配/浏览器验收；并行割接完整业务闭环未在本次验收。未代用户上传分工材料、修改工期日期或完成任务。未改PRD或SQL历史迁移，其他并行修改保留；未提交或推送Git。
