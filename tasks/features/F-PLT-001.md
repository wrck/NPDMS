# F-PLT-001 统一文件身份与版本管理

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`
> Technical Plan Gate：`PASS / NPDMS-FPLT001-TECHPLAN-20260826-01-R3`
> Implementation Done Gate：`PASS / 38f36b1 + 890196d2 + 24f3c1a4 / 独立裁决GO`
> 当前任务：`F-PLT-001已完成；等待下一工程单元`
> Requirement ID：`PLT-02（V1/P0，FR-PLT-008）`
> Feature Spec：`specs/features/F-PLT-001-unified-file-identity-and-version-management.md`
> Feature物理契约：`specs/features/F-PLT-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-26-f-plt-001-unified-file-identity-and-version-management.md`
> 锁定规格提交：`9328c1316655639c100c98f3c16701af10db5fbf`

## 实施边界

2026-09-08现状复核：以上PASS/Done记录对应历史已交付文件底座，不覆盖当前Spec的修订重验证范围。当前Feature Ready仍只由Spec维护；保留历史Implementation Done，但PLT-02当前切片不自动恢复完成。详见[逐需求审查](../delivery-units/DU-20260908-REQUIREMENT-IMPLEMENTATION-REVIEW.md#逐需求证据)；本轮没有新增业务运行验收。

- 本Feature只实现PLT-02统一文件公共能力，不合并PLT-01、INT-11、INT-12或各业务域审批状态。
- PLATFORM持有FileArtifact、FileVersion、FileReference业务真值；INFRA只提供技术存储回执和短时访问能力。
- 首期仅处理50MB以内文件，复用Spring Multipart和Yudao既有文件存储链，不修改基础框架。
- 首个消费者为SOL客户延期依据；PLT只冻结文件事实，不推进PRE-01审批状态。
- 本Feature不包含历史附件迁移、Deployment、SIT、UAT或Release。

## 任务跟踪

- [x] Task 1 建立六表、字典/策略/权限/Job种子、50MB配置和Feature工作单（PASS / `c1e3a46` / 独立裁决GO）
- [x] Task 2 实现批准的INFRA技术存储回执适配（PASS / `38f6a6b` / 独立裁决GO）
- [x] Task 3 定义PLT公共文件契约、Provider注册与持久化原语（PASS / `974feb3` / 独立裁决GO）
- [x] Task 4 实现上传初始化、用途授权与策略选择（PASS / `0a2869a` + `48fe53c` / 独立裁决GO）
- [x] Task 5 实现50MB正向上传、版本提交、引用绑定与文件事件投递（PASS / `81e1c0f` + `9b4a9d4` / 独立裁决GO）
- [x] Task 6 接入首个SOL消费者并解除材料主线阻断（PASS / `34ce4df` + `eb42aa3` / 独立裁决GO）
- [x] Task 7 实现短时下载/预览访问（PASS / `5b90a16` / 独立裁决GO）
- [x] Task 8 实现解绑、草稿删除、失效与归档（PASS / `6611d83` + `83ee8b4` / 独立裁决GO）
- [x] Task 9 建设响应式统一文件界面并接入SOL客户延期依据（PASS / `7e68962` + `75cf9c3` / 独立裁决GO）
- [x] Task 10 完成真实MySQL、Quartz、浏览器与独立Feature Done复审（PASS / `5acac64` + `38f36b1` / 独立裁决GO）

## 上下游解除条件

- F-SOL-001 Task 6材料分支：Task 5～6提供真实上传、固定版本引用及锁定重验后解除。
- F-SOL-001 Task 9材料场景：Task 9接入SOL用途并完成真实MySQL链路后解除。
- F-SOL-001 Task 10：上述材料主线闭合后返回其正式浏览器验收。

> 检查点（2026-08-27）：基线规格`9328c13`；F-PLT-001 Implementation Done已通过，证据为原实现`6d6c6ea`、可选扫描`890196d2`、展示修复`24f3c1a4`及独立GO；受管基线已同步，无阻塞。下一步定位最近未通过的开发Gate，不进入Deployment/SIT/UAT/Release。

### 2026-10-23 文件卡片按所选版本显示原上传名称

- 用户反馈割接人工证据上传后版本递增、名称仍为首次文件名。原FileArtifact.name作为稳定工件初始名称保留；补充当前引用版本名称及版本分页名称的可空响应字段，从既有COMPLETED上传会话读取，按租户/工件/版本/注册技术文件身份精确匹配，不修改历史或新增持久化字段。
- 公共PmsFileVersionReferenceList按所选版本显示名称，预览标题和下载名复用同一名称；历史版本按原分页查询精确查找，迟到响应仍受原generation保护。版本历史补充名称展示。新查询使用场景Query对象及Mapper XML，空集合返回空、授权失败不读取元数据，不扩大文件访问范围。
- 两个新前端失败用例先复现当前和历史文件名错误，再修复后文件组件12项、割接受控流程48项通过。后端13项通过，其中7项真实MySQL检查包含源名称、旧版本、工件名称不变、未完成会话/错误技术回执过滤、空集合及租户/权限拒绝。首次集成上下文因既有FileArtifactApiImpl新增的三个独立写入提供方未装配而失败，测试配置补齐未使用提供方mock后通过，实际被验证的查询/授权/访问票据路径仍使用原实现。ESLint及diff检查通过；冻结构建和部署后浏览器结果另行记录。
- 证据：.run/file-version-name-failure-tests.log、.run/file-version-name-ui-tests.log、.run/file-version-name-cutover-tests.log、.run/file-version-name-backend-tests.log、.run/file-version-name-backend-retest.log、.run/file-version-name-eslint.log。
- 完整后端构建及22项已部署修复保护通过，冻结清单为`.run/build/releases/224316f6-f66d-4ab7-b30f-614f54b05678/manifest.json`。定向vue-tsc本次文件无错误，仍存在需求分析EntityForm.vue:159、292三处既有外部类型错误，不标记全量通过。当前运行后端仍未包含名称字段，待本次独立重启批准及部署复验；用户无需再次上传来触发名称修复。
- worktree-backend.ps1 ValidateOnly已通过，配置固定local,dac、未变更运行服务。证据：.run/file-version-name-build.log、.run/file-version-name-preflight.log。已按.run/AGENTS.md请求本次59191独立重启批准，批准前不替换运行包；真实浏览器名称验证待部署，用户原页未导航。
- 用户随后明确批准本次重启并复验，通过worktree-backend.ps1部署冻结包224316f6-f66d-4ab7-b30f-614f54b05678，59191健康、PID 38720、local,dac配置保持；DAC独立健康检查通过，未启停。真实浏览器重新打开2113184905149976576，当前V8显示“插槽show tech.txt”，原“是”结论保持、上传输入可用、无错误提示。真实版本历史显示V2~V8原名为插槽show tech.txt、V1仍为2.png，8个版本均可用，未重命名历史记录。复验使用现有V8，无新上传或清单提交，用户原标签页未导航；保留自建正确显示的复验页。证据：.run/file-version-name-deploy.log、.run/file-version-name-deployed-browser.json、.run/file-version-name-deployed-db.txt。

### 2026-10-23 当前文件引用读取去除重复工作

- 配合F-CUT-003首次加载性能复验，消除FileCurrentReferenceQueryService在完整业务键已授权、已精确读取后，再委托FileQueryService重复授权和读取同一引用的工作；仅复用原引用响应映射，租户、主体、业务键、Owner拒绝、空槽、非法工件身份及Owner不可用规则保留。没有缓存或改动上传/绑定的并发版本与锁内校验。
- 原实现失败用例确认单次请求重复两次Owner调用；修复后8项检查通过，校验V8和引用版本12原样回传及原授权/异常边界。证据.run/cutover-2s-reference-red-valid.log、.run/cutover-2s-reference-green.log。冻结部署及真实浏览器首次加载验收仍待完成，状态见F-CUT-003同日记录。
