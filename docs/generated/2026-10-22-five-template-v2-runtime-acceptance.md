# 五份模板 v2 隔离项目运行验收

日期：2026-10-22。初轮验收四份直签/非直签模板通过，厂商督导未通过。用户随后授权修复并部署，督导任务启动/业务保存/任务完成复验通过，冻结计划和阶段完成记录未变；详见末节及专项复验报告。初轮证据保留其发生时点。

## 范围与判定

按用户要求使用快速模型，在真实浏览器中为五份已发布模板 v2 分别新建隔离项目，通过正式界面保存业务记录、刷新重开，并沿正常业务与审批流程推进。配置、按钮可见、页面可打开不能替代实际办理。既有项目冻结计划未修改。

全部新增记录及材料为合成隔离验收，不代表真实客户实施、设备操作、客户签署或物流。未发送客户调查/推送，未执行真实设备命令。初轮验收未修改业务代码、未直接改写生命周期状态，未执行服务启停、提交或推送；后续获授权的代码修复与部署另见末节。

## 初轮结果矩阵

| 模板 / v2发布修订 | 隔离项目 | S1工前准备与S2施工计划同时开展 | S5验收交维独立办理 |
|---|---|---|---|
| 普通类直签 993009900032 / 993009900073 | PJT2026000068 / 2110018733130579978 | 通过：计划46与工勘/需求草稿保存并重开 | 通过：满意度先于培训、培训先于终验、终验先于初验，均保存并刷新重开 |
| 工程类直签 993009900033 / 993009900074 | PJT2026000072 / 2110018733130579982 | 通过：计划48与工勘/需求草稿保存并重开 | 通过：初验0%时终验生效；培训0%时满意度草稿保存重开，再保存培训草稿重开 |
| 普通类非直签 993009900034 / 993009900075 | PJT2026000069 / 2110018733130579979 | 通过：需求V1与计划44在两阶段均未完成时保存并刷新重开 | 通过：培训0%时满意度草稿保存重开，再保存培训草稿重开 |
| 工程类非直签 993009900035 / 993009900076 | PJT2026000071 / 2110018733130579981 | 通过：需求V1与计划47在两阶段均未完成时保存并刷新重开 | 通过：培训0%时满意度草稿保存重开，再保存培训草稿重开 |
| 厂商督导 993009900036 / 993009900077 | PJT2026000070 / 2110018733130579980 | 失败：可选阶段自动完成后，未办理任务不能启动 | 失败：S5自动完成、两任务进行中0%，业务入口仅查看/下载，不能保存办理 |

满意度和培训保存的是明确未完工/未完成的草稿，证明独立办理和持久化，不宣称客户反馈或培训已经完成。本次不以S6整项目闭环作为验收目标。

## 督导模板运行缺陷

项目70在S0完成后，S1/S2自动变为DONE，任务仍未开始。任务准入表达式S0完成已满足，但启动入口提示“所属阶段尚未进入”。独立全流程入口可以保存并重开需求和计划草稿，不能替代任务入口通过。

源码与S1/S2现场表现一致：

- `ProjectStageAdmissionService.java:194`：任务启动准入只接受父阶段ACTIVE。
- `ProjectTaskPlanCompletionService.java:213`：任务完成校验同样要求父阶段ACTIVE。

项目70随后正常完成方案审批、配置、联调及割接审批，10:44:05进入S5。S5立即显示DONE、进度0%、完成0/2；满意度和培训任务均自动IN_PROGRESS。这里没有人工START被拒的证据，不能与S1/S2混写。满意度仅查看/下载，培训仅查看/下载PDF，均无新增、编辑、提交或完成动作。

为排除割接业务尚未闭环的影响，通过正式独立CUT入口完成同一CUT-2113098740199821312的合成跟踪闭环，10:48归档，稳定结果`CUTOVER_CLOSURE:2113099854542508032:2`，设备覆盖1/1。归档后刷新S5，两个任务仍只读。未修改S5草稿、阶段状态或正常关闭项目。

初轮保留缺陷证据，未绕过校验。后续用户授权的修复与复验另见末节。

## 工程直签72 S5通过证据

正常完成S0～S4相关业务与审批后，初验任务仍进行中0%时，从终验任务入口保存合成报告，实体`2113101950979416066`于10:56:11完成并生效，终验任务100%；刷新重开为当前有效报告V1。

随后在培训任务仍进行中0%时，从满意度任务入口保存五项全部“否”的未完工草稿，刷新重开保留；再从培训任务入口保存明确未完成的培训草稿，刷新重开保留。未发起调查或推送。

割接首轮虽单击提交，限定只读核对发现`CUT-2113101286188486656`仍为`CLOSURE_IN_PROGRESS/P6`，闭环实体`2113101738720333824`仍为DRAFT、无提交/归档时间，及时撤回归档成功表述。随后正常重开正式独立CUT入口，确认原两PNG附件V1已绑定后提交，11:11显示割接成功、归档完成、设备1/1，稳定引用`CUTOVER_CLOSURE:2113101738720333824:2`。未重传附件或直接改状态。首轮核对证据`direct/72-cut-readonly-persistence.tsv`仅反映当时状态，不能作为后续当前结论。

## 普通直签68 S5通过证据

S1工勘确认与需求冻结、S2重新倒排及审批、S3方案审批均从正式UI完成。S4到货、硬件、配置、联调与日志自然归集完成；CUT-2113108864717000704审批后经正式独立入口完成合成闭环，刷新重开归档，稳定引用`CUTOVER_CLOSURE:2113109321237630976:2`、设备覆盖1/1。

S5按以下顺序完成真实保存与刷新重开：

1. 培训0%时先保存满意度五项“否”的未完工草稿。
2. 终验仍进行中0%时保存未完成培训草稿，无评价签字。
3. 初验仍进行中0%时保存终验报告，实体`2113110155071594498`于11:28:31完成并生效，重开为当前有效V1。

结束时S5为25%、总任务12/16；培训、满意度和初验仍0%，未办理S6。证据`direct/68-satisfaction-before-training-reopened-dom.txt`、`68-training-before-final-reopened-dom.txt`、`68-final-before-initial-reopened-dom.txt`。

两份直签项目实际执行的顺序分别如上记录，本轮不宣称穷举所有任务排列。72未另行执行“培训早于终验”顺序，68补充了这一依赖的实际办理证据；未撤销72既有生效报告或修改历史以制造测试前置。

## 工程非直签71 S5通过证据

正常硬件登记、配置与联调业务完成，专属日志识别与自然归集分别通过。CUT-2113104355437547520完成D级简易方案及既有发起人、服务经理审批后，S4为100%、项目10/13。独立CUT第一次提交被“等待附件绑定完成”守卫拒绝，正常刷新等待绑定稳定后重新提交，11:07归档，刷新重开确认稳定引用`CUTOVER_CLOSURE:2113104787786403840:2`、设备覆盖1/1。

从满意度任务入口先保存五项“否”的未完工草稿，此时培训未完成0%；整页刷新重开保留。再从培训任务入口保存明确未完成的草稿，刷新重开保留。没有评价签字、调查推送或S6操作。证据`channel/71-satisfaction-before-training-reopened-dom.txt`、`71-training-after-satisfaction-reopened-dom.txt`及对应PNG；割接归档证据`71-cut-independent-archived-reopened-dom.txt/png`。

## 普通非直签69 S5通过证据

69正常完成工勘确认、需求冻结、计划44审批、方案审批及S4硬件、配置、联调。两类专属日志分别识别并自然归集。CUT-2113113658101796864完成既有两节点审批后，从正式独立入口保存并提交合成闭环；整页刷新重开确认11:44归档、稳定引用`CUTOVER_CLOSURE:2113114089733427200:2`、设备覆盖1/1。

S5培训仍进行中0%时，满意度任务入口保存五项全部“否”、明确未完工的合成草稿，整页刷新重开保留。随后培训任务入口保存未完成草稿，刷新重开保留，尚无客户评价签字。结束时S5仍0%、总任务10/13，未调查推送、签字或办理S6。证据`channel/69-satisfaction-before-training-reopened-dom.txt`、`69-training-after-satisfaction-reopened-dom.txt`及对应PNG。

## 合成发货初始化

用户追加批准：“允许，仅限这五个隔离项目的合成设备”。五台设备均先由真实浏览器正式新增，再通过[V512迁移](../../sql/migrations/V512__five_v2_acceptance_shipments.sql)补充每台一条合成发货及设备三项发货摘要。精确限定主键、项目、SN、租户1、PLATFORM_MANUAL和原摘要为空，不涉及真实订单或项目/任务状态。来源V2_ACCEPTANCE，事件ACCEPTANCE_SAMPLE，creator为v2_runtime_acceptance。

事务验证：首次十个写语句各影响1行，重复执行各0行，错误租户各0行；回滚后设备摘要和发货记录保持原状。仅挂载V512通过指定Compose/Flyway在npdms_domain_test执行，migrate与validate成功，执行后五台设备与发货记录关联一致。未重启应用。

证据位于`.run/five-template-v2-runtime-20261022/shipment/`：`fixtures.json`、`rollback-validation.json`、`applied.tsv`、`deployment/migrate.log`、`deployment/validate.log`。验证辅助脚本最初因SQL注释分号插入计数查询导致解析失败，修正后完成上述验证；失败连接未提交数据。

## 过程中的有效校验与边界

71/72需求拓扑首次冻结曾提示缺有效文件，正常保存、刷新重开确认附件后冻结成功，保留过程证据，不将该现象定性为持续阻塞。71计划在工勘修改结束日期后受到版本过期保护，正常重新倒排后提交并审批通过。

并行浏览器输入曾出现目标文本夹入其他字符，原因未确认，后改为单代理独占浏览器写入。会话恢复后部分代理无法发现浏览器，新快速模型会话可恢复；不以API或模拟浏览器替代正式UI。

## 验收中途运行包切换

69完成S1后，11:33附近施工计划重算、审批人加载及项目页刷新发生系统异常/Network Error。只读检查确认59191新进程PID58676于11:33:12启动，运行`.run/dac-route-server.jar`、`local,dac`；本会话没有执行启停。随后health恢复UP，真实浏览器重开69数据保留，正式重新倒排与计划44审批于11:36:09通过，继续S3。

对前后冻结发布物的定向class/XML清单比较显示，project模块业务关联相关11项发生变化，acceptance、platform和cutover三模块未变化；结果见`runtime-module-delta.json`。此前实操证据保留其发生时点，不将其伪装为切换后的重跑证据。收尾已在新运行包下只读复核70：S1工勘仍提示“所属阶段尚未进入”；S2任务待分配按需0%，制定施工计划禁用；S5满意度仅查看/更多操作，培训仅查看/下载PDF，均无新建或编辑，阶段历史仍S5已完成。未改写70记录。既有S2草稿加载曾自动呈现dirty，离开时放弃未保存，没有把这一现象当作任务入口办理通过。证据为`channel/70-post-deploy-overview-dom.txt`、`70-post-deploy-s1-survey-dom.txt`、`70-post-deploy-s2-plan-dom.txt`、`70-post-deploy-satisfaction-dom.txt`、`70-post-deploy-training-dom.txt`。不为了重测而撤销既有生效报告或修改历史。

## 证据索引

本地证据目录：`.run/five-template-v2-runtime-20261022/`。

- S1/S2：`direct/68-s1-survey-draft-saved-dom.txt`、`68-s1-requirement-saved-dom.txt`、`68-s2-draft-reopened-dom.txt`；`direct/72-s1-requirement-reopened-dom.txt`、`72-s2-draft-reopened-dom.txt`；`channel/69-s1-reopened-dom.txt`、`69-s2-reopened-dom.txt`、`71-s1-reopened-dom.txt`、`71-s2-reopened-dom.txt`。
- 督导失败：`supervision/s1-task-blocked-dom.txt`、`s2-task-blocked-dom.txt`及PNG；`s5-after-cutover-closure-satisfaction-dom.txt`、`s5-after-cutover-closure-training-dom.txt`，以及对应只读任务PNG。
- 工程直签S5：`direct/72-final-before-initial-saved-dom.txt`、`72-final-before-initial-reopened-dom.txt`、`72-satisfaction-before-training-reopened-dom.txt`、`72-training-after-satisfaction-reopened-dom.txt`。

明细：[两份直签](2026-10-22-five-template-v2-runtime-direct.md)、[两份非直签](2026-10-22-five-template-v2-runtime-channel.md)、[厂商督导](2026-10-22-five-template-v2-runtime-supervision.md)。

## 厂商督导可选阶段任务修复（2026-10-22）

用户授权修复可选阶段自动完成后按需任务无法办理的问题，保留按需规则，不调整既有项目冻结计划。根因为任务启动、业务上下文写入和任务完成均要求所属阶段为ACTIVE，误阻断DONE阶段中仍有效的按需任务。

本次只调整上述三个校验点：启动和完成从当前有效冻结计划读取required=false；业务写入从同计划的任务实例读取required_task=false，并同时要求阶段实例及当前阶段执行轮次均DONE。任务自己的当前轮次、项目ACTIVE、任务状态、准入规则、业务完成事实、提交、退出规则、授权与版本校验继续执行。未回退阶段、未改写冻结计划、未调整初始化或历史数据。

失败复现：三个新增正向回归用例在修复前均失败，其余58项通过。修复后8个测试类共169项通过，覆盖启动、业务写入、任务完成、阶段完成、闭环、生命周期和计划安装；包含必选/缺省标记、未进入/跳过/终止阶段、关闭项目、失效任务轮次及未满足业务完成条件的拒绝用例。日志为`.run/optional-stage-task-red.log`与`.run/optional-stage-task-green.log`。这些结果不代替真实浏览器验收。

部署前只读基线保存于`.run/optional-stage-task-before.tsv`：70仍ACTIVE，生效计划8800017924661870977、version=0，S0～S5实例及阶段执行轮次均DONE、version=2；S1/S2三项按需任务PENDING_ASSIGN/PENDING，S4硬件及S5培训/满意度按需任务IN_PROGRESS/ACTIVE。冻结快照摘要已记录，供部署后比较。

代码与定向测试完成；12:03:30工作树后端构建成功，冻结清单为`.run/build/releases/3a78ad9b-fc80-4888-b42b-6568c0b15146/manifest.json`。正式部署入口的ValidateOnly检查通过，确认冻结产物、22项既有修复基线与local,dac配置；日志为`.run/optional-stage-task-build.log`和`.run/optional-stage-task-preflight.log`。

用户随后明确批准重启59191部署并继续快速模型验收。本会话使用正式入口部署成功，后端健康、PID62440、local,dac，日志`.run/optional-stage-task-deploy.log`。部署后冻结计划ID/version/快照摘要及S0～S5阶段实例、执行轮次的ID/status/version均与部署前一致，证据`.run/optional-stage-task-after-deploy.tsv`。

快速模型真实浏览器复验完成：工勘正式开始、保存重开、确认归档，任务完成100%；需求V1、施工计划45、满意度及培训草稿均从任务入口保存并整页刷新重开。满意度先于培训保存，两项仍未完成0%，未完工草稿未被算作完成。硬件入口可编辑，缺失安装位置被必填校验拦截并取消；这项没有成功保存重开证明，不扩大为六项全部业务完成。项目最终7/12、58%，仍ACTIVE，六项按需标记均保留。详见[专项真实浏览器复验](2026-10-22-optional-stage-task-browser.md)。

实操前后，项目70完整冻结计划行、全部阶段实例、阶段执行轮次、阶段历史记录逐字一致，结果`.run/optional-stage-task-invariants-result.json`为identical=true，前后文件为`.run/optional-stage-task-invariants-before-browser.tsv`与`.run/optional-stage-task-invariants-after-browser.tsv`，摘要均为`DD9954E6D7E0B7CB84C9C162EB0EE73768A97AA495DEF7743AEE26F1FB062F5C`。最终任务状态见`.run/optional-stage-task-final-tasks.tsv`。工勘保存/确认曾短暂显示结果待确认，刷新后稳定归档成功；原提示证据保留，未重复新建或绕过状态机。
