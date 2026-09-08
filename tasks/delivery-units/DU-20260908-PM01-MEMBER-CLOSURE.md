# DU-20260908-PM01-MEMBER-CLOSURE 项目基础成员闭环

> DU状态：`CLAIMED`
> DU类型：`TASK`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-005=TASK_COORDINATED`
> Task范围：`Task 10接续：按既定工程管理部/项目管理员角色能力补齐ProjectTreeScope；关闭误设Q-FPROJ-011，不增加创建者例外授权`
> Owner：`Codex当前项目交付主线会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/2916/NPDMS`
> 认领基线：`560aa78563c0541eaa6449308c4c52ccd71cde0e`
> 认领提交：`SELF`
> 修改边界：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectscope/**;pms-module-project/src/main/resources/mapper/projectscope/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectscope/**;docs/design/07-authorization-design.md;docs/decisions/open-questions.md;tasks/features/F-PROJ-001.md;tasks/delivery-units/DU-20260908-PM01-MEMBER-CLOSURE.md;tasks/delivery-units/README.md`
> 串行资源：`上述PROJ及前端路径；固定23316/npdms_test与26379，应用59280/19081；无业务迁移修改，首次应用启动沿现有配置初始化Flowable引擎表；不建第二套环境、不停他人服务`
> 旧功能范围：`NONE`
> 验证：`联合请求权限/资格/幂等/版本/事务回滚，旧服务经理直接消费者，组件/类型/构建与真实浏览器；不宣称阶段业务完成`
> 集成记录：`本次master提交交付已有MANAGE项目的联合/分次成员管理及页面闭环，39项后端、9项前端定向测试通过；Q-FPROJ-011阻断无MANAGE的新建未指派项目首次指派。Task 10/Feature保持IN_PROGRESS，不宣称全部基础范围或阶段业务Done；释放本DU写边界`

依据修订019及用户确认的阶段流转前基础范围。上轮成员写API及角色权限已交付；本轮加法组合已有服务经理和项目经理写命令，补候选与页面，不重复实现已有底座。开始时间2026-09-08 21:34:01。契约在SDS10集中细化；当前Task保持IN_PROGRESS，已完成及受阻范围在本DU一次记录。

打包发现通用公司/角色查询的直接消费者测试未适配新增接口，补入上述COM测试单文件边界，仅补不可调用分支的测试替身方法，不改COM业务实现或数据库；这是已交付公共接口的编译回归修复，不新增商务需求。

交付（PM-01/PM-08，PROJ Owner）：新增联合`update-members`、受项目范围约束的经理候选和当前经理一致快照查询；只读模型/最终版本投影、整次原子事务、旧服务经理审计和Outbox均保留。独立经理入口保留完整授权，联合入口在预授权根锁内执行，避免中途改派操作者后错误重验。前端从项目详情提供增补/移除、显式主责选择及联合服务经理表单，复用原幂等意图状态、部门/站点和服务经理候选；责任历史仍由原列表展示。验收发现并修复创建响应serviceManagerAssigned固定false、项目列表MAINT维护阶段残留。未改变业务生命周期、冻结模板/任务或后端租户源码。

实际验证：

- 使用JDK25和固定MySQL，项目经理事务用例11/11（含联合失败回滚、幂等、并发读写一致快照），候选2/2，旧服务经理7/7，角色API10/10，创建应用9/9，共39项通过。基础命令为`mvn.cmd -o -q -pl yudao-server -am '-Dtest=ProjectManagerMemberMySqlTest,ProjectManagerCandidateServiceTest,ProjectManagerAssignmentApplicationServiceTest,ProjectParticipantFactApiImplTest' '-DskipITs=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-javaagent:D:/Maven/Repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' package`；修正后仅重跑受影响类，创建响应另跑`-pl pms-module-project -am -Dtest=ProjectManualCreationApplicationServiceTest`。MySQL权限/数据范围接口为可控替身；联合服务经理实际写方法执行真实SQL，不将替身说成完整鉴权验收。
- 前端成员表单运行测试3/3、旧服务经理面板4/4、HTTP合同2/2，共9项；命令使用现有`vitest.pms-file.config.ts`定向运行。`pnpm run ts:check`通过，Vite构建通过；保留既有legacy CSS `*zoom`警告。停用自身验收后端后完成最终`mvn -o -q -pl yudao-server -am -DskipTests package`，不重复执行业务测试。
- 真实浏览器：页面创建992203060002，自动匹配已发布模板992203040001/v2，生成7阶段/7任务/2里程碑/2交付件/7门禁；普通类无匹配时正确阻断。该项目创建者仅VIEW，首次指派被1014024033拒绝，已登记Q-FPROJ-011。
- 使用既有正式创建API的serviceManagerUserId参数准备自有项目992203060003（没有直接改库建立项目成员）。随后所有成员修改均由真实页面提交：联合更换服务经理1→100并增补PM1/100，最终版本2；单独增补103并改主责103，版本3；移除100/103时未指定接任者被页面拦截且无POST，显式选1后版本4。刷新后当前主责1、仅PM1有效，原加入区间及被移除成员历史保留，服务经理100仍有效，ACTIVE/S0与ASSIGNED一致；无登录401、跨租户403且无数据返回。320px对话框完整位于视口内并可键盘聚焦；截图经视觉检查。

审阅：联合核心经独立只读审查，修复“联合响应暴露中间版本”和“读取可能撕裂”后针对性复核无Required/Critical；对应序列化和并发快照用例已通过。后续创建响应布尔值、MAINT标签修复完成自审与定向验证。浏览器观察到既有路由next弃用和侧栏图标属性警告，未扩大到全站UI治理；本成员操作过程无新pageerror。

环境与证据：测试MySQL/Redis仍为原Compose；已沿仓库默认首次启动机制由Flowable官方引擎初始化缺少的运行表，未手写引擎DDL。前端保持原多租户机制启用、单一可用租户自动选择并隐藏切换，未关闭后端隔离。停用了本次自己的59280后端和19081 Vite，未停他人服务；专用浏览器已关闭。两项自有测试项目及creator=`pm01-browser-20260908`的两条上游候选资格夹具保留供复验。日志/截图位于`.run/pm01-member-closure/`，不含提交凭据；未创建第二套基础设施或执行全库清理。

未完成：Q-FPROJ-011需要需求方确认首次指派授权来源，未把创建者VIEW改成MANAGE；Q-TPLACC-001等既有独立验收契约限制保持，未实施S0～S6阶段内业务，也不宣称通知已送达。初次Mockito自附加失败采用显式agent修复；新工作树首次类型检查因缺少Vite自动声明失败，在构建生成后复验通过；最后打包遭自身运行JAR占用，停自身服务后重试。总耗时从21:34:01至本交付提交，超过原效率目标，本次不追加流程治理修改。
