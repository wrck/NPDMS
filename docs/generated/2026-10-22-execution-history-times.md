# 阶段状态查看界面与业务时间

用户确认：自动完成节点按完成依据取业务开始/生效或结束/确认时间，手工提交保留实际提交时间。

根因：原 history 接口只投影手工 submittedAt，自动完成未创建手工提交凭证，界面将所有空值显示为“未提交”。

修正：按阶段分组展示阶段/任务、轮次、状态、提交时间及来源、实际结束时间。冻结规则、原计划和办理说明保留在展开详情。刷新保留已有内容，首次以打开状态挂载也读取历史。

后端使用只读 submissionTime 投影，不回填原提交列，不改写历史。人员指派/最终用户读取原冻结成员区间生效时间；需求分析、工勘、计划审批、方案审批、培训确认等由 Owner 读取精确冻结版本对应的业务时间；结果订阅读取原完成收据所指的不可变扫描形成时间，核对租户、项目、收据与扫描边界。原生阶段以本计划的必需任务业务时间汇总。原 endedAt 保留正式状态转换时间。历史业务记录缺失、版本不匹配、无可验证时间或Owner不可读时不猜测、不使用更新时间或当前时间兜底。

验证记录：前端 ProjectExecutionHistory.runtime.spec.ts 通过时间来源、手工优先、未提交/时间缺失区别、阶段分组及冻结门禁证据检查；后端 ProjectExecutionHistoryServiceTest、ProjectExecutionTimeProjectionTest、TemplateSnapshotConsumersTest 和涉及Owner的定向检查保护权限、冻结版本及只读行为。最终构建结果见 .run/execution-history-final-build.log。

浏览器：10.210.0.11:19191，吉林省纪检监察外网省级平台建设，已打开阶段状态查看并检查布局、状态、记录和刷新。运行后端尚未更新，因此此时不宣称新时间取值已完成真实业务验收；需使用新冻结产物经批准重启后核对。未修改业务数据，未提交或推送。

构建边界：首次全量 testCompile 曾被另一个会话新增的 SatisfactionTaskBusinessObjectProviderReadTest 旧式 Query 访问器阻塞（.run/execution-history-build.log）；本次未改动该文件。该会话更正访问器并释放构建锁后，重新执行本工作树的完整构建及定向测试。前端最终 5 项测试通过。

最终结果：完整后端构建成功，6 个测试类共 42 项定向测试通过；前端 5 项通过。冻结清单 `.run/build/releases/574c5156-aeb5-4623-a4d3-22ef86550d98/manifest.json`，配置 `local,dac`。尚未授权重启，冻结产物不表示已部署或完成新时间值的浏览器业务验收。
