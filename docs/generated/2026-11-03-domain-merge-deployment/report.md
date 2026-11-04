# domain-migration 合并与正式部署回执

2026-11-03。状态：需求方回复“批准迁移并继续部署”后，已完成加密备份、V534迁移、正式后端切换及只读浏览器核验。每日02:00任务已登记；正式环境既有Quartz禁用配置保持，自动定时执行尚未生效。

- 来源分支：`codex/domain-migration`，最新本地提交`9a757268b6c36b9a1c6dd2eccdae85f456ef2a69`（自动同步项目合同号）。fetch后远端仍落后于此提交。
- 合并提交：`dc6a9ccf8c320ec8966c0e3f238636fcfc7a7f8a`，目标分支`codex/local-performance-20261025`。26个文件，保留当前发货合同查询和来源新增合同号投影能力；未推送。
- 336个原有未提交文件已保护并复核；唯一重叠文档的原有修改已恢复，临时定向stash已清除，索引没有未解决冲突。
- 35项定向测试通过，覆盖合同事实SQL的租户/项目边界、关系建立、失败租户、并发保护、超长合同号及项目创建。全量后端编译打包通过，22项已部署修复基线检查通过。
- 冻结产物：`M:/AICoding/CodexData/artifacts/domain-merge-20261103/build/releases/d041fbdb-3e06-4ee0-9694-01607a10bbd2/manifest.json`；应用SHA-256为`9b3aa09995c0bf891b0a05b2ce801a4f88f9b2dd2804cb791096f0e3b142bbfd`。
- 发布包：`M:/AICoding/CodexData/artifacts/domain-merge-20261103/backend-release.tar.gz`，SHA-256为`e189f34de56d9fbfa21744b0dd25cad587f331e91023fe9ebe6c78a5783826df`。现有MinIO修复适配器及其他部署附件原样保留。
- BE已激活`/opt/sdp/backend/releases/SDP20261103_CONTRACT_PROJECTION`，PID106906，readiness为UP，自动重启计数0；DAC仍为PID17159，未重启。

## 执行前核查与迁移依据

执行前正式库为B531、V531.2、V533，版本533。`proj_project.contract_no`实际是`VARCHAR(128)`、`utf8mb4_unicode_ci`、可空且无索引；81,321个未删除项目该字段均为空。新任务id及handler均不存在。

不能直接回放来源分支的V531/V532：版本已落后，原脚本还会改变正式排序规则。已准备[正式V534前向迁移](../../../sql/releases/production-contract-projection-20261103/README.md)，仅扩为MEDIUMTEXT并登记每日02:00任务，不立即手动执行全量同步。批准后已按备份、迁移、校验、激活后端顺序执行。

候选累计迁移包位于BE的`/opt/incoming/SDP20261103_CONTRACT_SCHEMA`，保留原3份SQL原件及新增V534。Flyway info确认仅V534为Pending；普通validate因Pending拒绝。随后仅在准备期验证中显式允许Pending，4份迁移校验通过；未启用outOfOrder、未执行repair/clean/migrate，未改写历史。

更正准备阶段判断：新代码仅在存在Quartz Scheduler时才尝试登记`projectContractProjectionJob`。正式环境排除了Quartz自动配置，因此不能把数据库任务状态“正常”视为调度已运行；扩列仍是新代码即时合同号投影的数据库前置条件。

切换前只读核验：后端仍为`SDP20261102_STORAGE_PATH`、PID102051、自动重试计数5（此前存储修复遗留计数）；迁移仍为`SDP_LICENSE_20261102`；DAC PID17159、计数0。两者均active，该准备阶段未重启；最终执行结果见下文。

执行证据：本工作树`.run/domain-merge-20261103/`及`.run/certificate-session-20261102/ssh/`中的150～159号contract请求与结果。

## 已批准执行结果

- 加密备份覆盖`proj_project`、`infra_job`、`flyway_schema_history`的全部行及表结构；本次未修改的触发器未包含在该专项导出中。使用官方Ubuntu客户端包，SHA-256与服务器软件源元数据匹配；临时解包使用，未安装服务或增加权限。
- BE备份位置：`/data/backups/contract-projection-20261103/before.sql.gz.enc`。原始导出72,348,232字节，加密文件9,827,440字节；主机加密凭据保护随机备份密钥。完整解密解压摘要一致，未恢复写入正式库；不等同于异机灾难恢复演练。摘要详见[机器回执](receipt.json)。
- 经既有生产入口发布并激活累计迁移包`SDP20261103_CONTRACT_SCHEMA`，只执行V534，耗时3.464秒；迁移后4份Flyway历史/脚本校验通过。无repair、clean、outOfOrder或其他迁移。
- `contract_no`现为MEDIUMTEXT，保留`utf8mb4_unicode_ci`、可空和原注释；81,321行`id + contract_no`有序摘要在迁移前、迁移后和后端切换后均为`720bd1c136de9794a7cd5be0f3acbab037e4c3870f008bf2414c6be38fbc4aef`。
- 新后端于00:56完成启动，冻结JAR摘要匹配，readiness为UP。唯一一次正式后端切换得到PID106906、自动重启计数0；DAC保持PID17159。启动就绪前健康检查短暂拒绝连接/503，随后恢复；第一次页面请求恰在启动期间失败，就绪后重新查询成功。
- 真实浏览器只读核对：定时任务页可查到id993009532001、处理器`projectContractProjectionJob`、Cron `0 0 2 * * ?`、状态“正常”；项目列表加载81,321条。证据：[任务页面](job-browser.png)、[项目列表](project-browser.png)。未提交业务修改或手动执行全量同步；本次不宣称同步业务执行验收通过。

## 自动调度限制

正式`/etc/sdp/backend/application-prod.yaml`仍排除`QuartzAutoConfiguration`，与现有生产配置模板一致；代码没有其他Scheduler工厂。该配置使新增任务仅完成数据库登记，不能在02:00自动触发。全局调度启用是额外运行配置变更，需核对其他既有任务影响后处理；本次未扩大到全局调度启用或再次重启。

新增执行证据：`.run/certificate-session-20261102/ssh/`中的160～177号contract请求与结果。174号检查误用了包内文件名`app.jar`，在激活前即失败；175号改用清单中的`yudao-server.jar`核对通过并完成唯一一次切换。

## 名称变更前端补发

需求方发现名称提交`72af9ca59`未生效并明确要求“部署”后，补发前端`SDP20261103_BRANDING`到FE1/FE2。此前仅部署后端造成遗漏；两台旧前端均为`SDP20261102_LOGISTICS`。新构建显式使用`VITE_APP_TITLE=服务交付平台`，其余生产API、租户、验证码等设置沿用既有构建配置。源码与此前准备的完整前端构建输入一致，构建前后指纹稳定，编译34.50秒通过；未重新运行自动化测试。前端既有DAC1.1.8下载组件使用已验证安装包及带摘要文件名，现网原1.1.5下载文件原样保留。

两机经完整2,828文件清单校验、Nginx配置检查、正式publish/activate入口及平滑reload完成切换。旧版本保留；没有再次重启后端/DAC或执行数据库迁移。FE1入口HTTPS返回新标题；FE2根路径按现有白名单返回404，改用白名单培训记录路径验证新前端HTML成功（只验证页面壳，不伪称有效客户业务验收）。

真实浏览器刷新原有两个页签后，标题分别为“服务交付平台 - 项目列表”和“服务交付平台 - 我的站内信”；左上名称为“服务交付平台”，项目列表显示81,321条。未提交业务数据。见[截图](branding-browser.png)、[前端回执](frontend-branding-receipt.json)。执行证据为180～189号branding请求/结果。
