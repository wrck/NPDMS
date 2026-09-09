# F-SOL-003 需求分析动态表单组合与版本冻结

## 当前增量：实体数据驱动表单（2026-09-09）

按用户本轮要求调整并以需求分析验收：普通正文唯一保存于SOL实体，PLT保留模板/文件运行上下文，不再提供业务普通值写入出口；已有完成版和旧PLT来源值保留。先修订本Feature/SDS共享契约，再实现、前向精确回填、定向校验及真实浏览器“加载—保存—重开—完成只读”验收。当前增量实施中；下方既有Implementation Complete只代表原数据分工，不自动覆盖本次差量。认领见DU-20260909-DELIVERY-DEMO-BUSINESS-UI。

### 本次实现与验证记录（2026-09-09）

- 已实现：`PreparationDO.entityValueJson`及根Mapper实体CAS；创建、PATCH、完成、修订、查询、对比与SCH事实从SOL普通值读取，PLT使用`inspectEntityData`只读投影。V210精确匹配租户、Owner、对象键及PRE-04后复制旧来源值，不修改旧PLT正文；新业务上下文正文为空。旧手工表单继续由PLT保存。
- UI沿用需求分析现有页面；保存后必须回读实体，回读失败保留填写/操作意图；实体contentVersion变化可刷新，完成版不恢复待保存缓存。按`frontend-ui-engineering`补充状态反馈及FormCreate行操作键盘适配。
- 已通过：SOL/PLT定向后端45项（含真实MySQL临时表迁移、实体CAS/租户/完成版保护及回滚）；应用级MySQL5项（实体正文保存/克隆、完成版保护、引用事件幂等、失败回滚、并发完成，保留既有迁移断言）；前端运行时及交互23项；规格契约9项。应用级测试使用真实SOL/PLT实现和MySQL，PROJ/权限由测试替身提供；未参与的生成/辅助上传服务明确断言零调用，不能作为全栈授权或上传验收。
- 构建：`mvn -o -B -pl yudao-server -am -DskipTests package`、Vite acceptance构建、`pnpm run ts:check`通过。首次类型检查因缺少自动导入声明失败，按既有构建流程生成后复查通过。测试装配补齐现有FileArtifactApi新增依赖及FILE_READ查询权限，不修改生产授权规则。
- 迁移：固定Compose `npdms-50eb-test`、MySQL23316/`npdms_test`已由本工作树`docker compose ... run --rm --no-deps migrate -target=210 migrate`验证179项历史迁移并前向执行V210成功；无reset/repair、无旧值覆盖。临时表测试仅连接内写入；应用级测试专用对象已清理。
- 尚未执行：真实浏览器验收及320/768/1024/1440布局/键盘检查。用户允许分支独立59480/19481，但前端启动命令被执行环境policy拒绝，未绕过、未启动分支服务、未停止p903。需本机服务可用后再进行“加载—保存—重开—完成只读”和实体数据库落值核对；本次差量**未验收完成**，不晋级历史Feature状态。
- 可复跑入口：`RequirementAnalysisEntityDataMigrationIntegrationTest`、`RequirementAnalysisApplicationMySqlIntegrationTest`（必须`skipITs=false`及固定测试库环境变量）；`vitest.pms-file.config.ts`下`RequirementAnalysisDynamicForm.runtime.spec.ts`及`requirementAnalysisInteraction.spec.ts`；`python scripts/tests/test_fsol003_dynamic_form_amendment.py`。本机日志在`.run/delivery-demo/entity-*.log`；实现未提交、未推送，原有`.gitignore`修改保留。

运行续验（2026-09-09 14:03）：应用内Browser已恢复并实际访问19481，页面返回`ERR_CONNECTION_REFUSED`。本分支后端由本任务启动，PID26472、59480监听，`/actuator/health`为`UP`，连接固定测试MySQL23316/Redis26379；复用13:33已通过的本分支JAR构建。前端启动仍被执行环境以`blocked by policy`拒绝，去除非必要开关覆盖后仍拒绝；因此真实UI业务闭环仍未执行，不替代为其他服务或静态页面。未停止p903，后台服务日志为`.run/delivery-demo/entity-backend.log`。

运行续验（2026-09-09，本轮应用内Browser）：基线`7d8f89a3`。复用本分支已通过的`dist-entity-acceptance`构建，使用既有Vite preview在`127.0.0.1:19481`启动实际应用，`/admin-api`代理指向本分支`localhost:59480`；后端健康检查实测`UP`。应用内浏览器已显示真实登录页，初始化时捕获的控制台error为空。未停止或占用其他任务的59280/19081服务，未修改生产实现、测试数据或凭据。此前“前端无法启动”的环境阻塞在本轮已解除；当前等待用户在指定应用内浏览器登录。加载—保存—重开—完成只读、实体落值及四视口验收仍未执行，不记为通过，不晋级本次增量或改写下方历史Done。

### 2026-09-09 真实浏览器续验与局部修复

- 本轮结论：**标准模板下的实体正文核心闭环通过；Demo扩展实体闭环仍为BLOCKED_BY_DEPENDENCY**。以下记录接续上方等待登录检查点，不改写原Feature历史Done，不宣称整个Demo/S1～S6、SCH-01或本次增量全部验收完成。
- 环境：用户提供账号后通过应用内Browser真实登录`127.0.0.1:19481`，关闭“记住我”，未保存密码；真实后端59480连接固定`npdms-50eb-test` MySQL23316/`npdms_test`与Redis26379。仅使用专用项目`992203060001`，无请求替身、无直接改状态、无清库/迁移/生产访问，也未停止其他任务服务。
- 真实正向：创建V1，空必填保存触发三项客户端校验且保持草稿；填写三个真实Editor字段；未保存刷新显示放弃确认，取消保留输入；通过SOL保存、刷新页面重开回读；完成冻结V1；从有效版创建V2并继承正文；修改、保存、完成V2；历史抽屉及版本对比正确显示V1/V2背景变化、目标/拓扑不变。
- 真实并发与存储：两窗口同版加载V2，A先保存后B用旧版本保存；B未覆盖A的数据库值并保留本地输入，回读后重试成功。最终V1=`2097570155619299330`（历史完成、contentVersion=3），V2=`2097572343187910657`（唯一有效完成、contentVersion=3），无当前草稿；SQL直接确认各自`entity_value_json`保存不同背景、共享目标/拓扑，两个PLT上下文`value_json`均为`{}`，正文保存不递增文件上下文版本。V1正文和完成时间未被V2覆盖；有效指针切换按原契约推进根version。
- 修复一：真实保存后概览卡片仍显示旧contentVersion，而详情已刷新。`ProjectRequirementAnalysisPanel.vue`改为同时回读概览及详情，成功后一起更新；不调用会卸载保存中表单的`loadDetail`，任一回读失败保留原界面。新增父面板运行测试验证延迟/失败回读、概览同步及不卸载，不替代真实业务验收。
- 修复二：完成版隐藏保存动作但11个旧Editor仍为`contenteditable=true`，因为该组件接受`readonly`而非FormCreate的`disabled`。`RequirementAnalysisDynamicForm.vue`仅在本域运行规则中映射`readonly/editorConfig.readOnly`，保留模板原有只读值；权限变化原位更新且不替换规则/正文。运行测试覆盖权限切换、配置保留、未保存值保留；最终构建浏览器确认完成版可编辑区为0、保存按钮为0、附件写入口不显示。不修改公共Editor、PLT渲染器或后端授权。
- 真实REST补验：完成实体PATCH拒绝（`1011002003`）；通过手工实例API读取/写入业务上下文均拒绝（`1010003008`）；未登录读取拒绝（`401`）；拒绝后重新读取完成正文与根版本均不变。这些是实际后端API验证，不冒充非经理登录或跨租户浏览器验收；已有适用权限/租户后端测试证据沿用。
- 验证：本轮最终3文件30项Vitest通过；acceptance构建通过，`vue-tsc --noEmit`通过，`git diff --check`通过。实际完成版320/768/1024/1440视口的document宽度均不超视口，已目视320正文/历史及1440正文；最终截图`.run/delivery-demo/entity-browser-desktop.png`，构建与类型检查日志为`entity-browser-build.log`/`entity-browser-typecheck.log`。捕获的浏览器console error为空；CDP缓冲显示相关读取/克隆/保存HTTP200，但出现过旧事件淘汰，故不宣称全程完整网络错误统计为0，也不宣称全部键盘/WCAG已通过。
- Demo扩展依赖：通过真实PLT REST新建、保存并发布本仓30字段Demo配置（模板`2097573726276431873`、R1=`2097573726293209090`）；复制旧验收项目模板到专用草稿`993009001590`并使用数值ID精确绑定。项目模板validate明确拒绝：缺definitionRevisionId、阶段start/normalClosure及精确规则引用，旧T-START不符合现行基本操作规则，且BPM流程定义不可用；修正测试工具的版本字段及数值ID后，PRE-04表单自身不再报无效，剩余为上述项目模板依赖。测试库现有六份新F-PROJ-009场景模板也均为DRAFT，不能擅自发布或从旧sortOrder臆造图/职责来绕过。新项目模板未发布；未被绑定的Demo表单已通过正常命令停用，保留修订和草稿证据，不删除历史。API调试脚本位于被忽略的`.run/delivery-demo/entity-runtime-api.ps1`，仅从进程环境接收登录凭据。
- 剩余：由模板Owner提供符合现行契约的Demo验收项目及精确冻结绑定后，继续多选/明细/显式空值的实体保存、重开与键盘/四视口；真实附件字节流程与完整非经理/跨租户浏览器矩阵本轮未执行，不能由标准模板正向结果代替。当前改动未提交、未推送，原`.gitignore`修改保留。

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / GO（规格整改提交 4d04dbd63bbd01683416563bece31da6cd53f849）`
> Technical Plan Gate：`PASS / NPDMS-FSOL003-TECHPLAN-20260828-01-R2`
> Implementation Done Gate：`PASS / NPDMS-FSOL003-DYNAMICFORM-IMPLEMENTATION-20260828-01-R1`
> Requirement ID：`PRE-04（V1/P0）`
> Feature Spec：`specs/features/F-SOL-003-requirement-analysis-versioning.md`
> Feature物理契约：`specs/features/F-SOL-003-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-28-f-sol-003-dynamic-form-composition-and-versioning.md`
> 锁定规格提交：`44d172b31de089d96d172f82368f1467bf059259`

## 当前最小工作单元

- 按已批准新中文 Technical Plan 完成一个整体正向闭环；全部接通后统一执行整体测试和验收。

> 检查点（2026-08-28）：基线`44d172b3`、实现`70a278b5`；Implementation Done以`NPDMS-FSOL003-DYNAMICFORM-IMPLEMENTATION-20260828-01-R1`通过，固定MySQL、前后端全量、39模块打包、两轮真实浏览器及FILE_READ权限回归均通过；无阻塞；下一步保持开发阶段，识别最近一个前置已满足但未通过的开发Gate。
