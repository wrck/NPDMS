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
