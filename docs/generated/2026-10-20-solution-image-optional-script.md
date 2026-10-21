# 实施方案常规图片与可选脚本上传

来源：本会话用户明确要求“实施方案图片上传文件格式需要同步支持常规的图片格式，配置脚本上传脚本文件不要求强制上传”。按本次授权同步修订 SCH-01 必填与验收口径，保留其他会话改动。

## 修改范围

- `SolutionReviewChapterForm.vue` 两处拓扑附件统一保留 PPT/PPTX/VSD/VSDX，并支持 PNG、JPG/JPEG、GIF、BMP、WEBP。仍使用已有上传组件、数量和 5MB 大小限制；富文本编辑器原本已支持 `image/*`，不改共享编辑器或上传接口。
- 前端必填说明与四处脚本提示改为可选上传，脚本预览及上传后文件版本保留原行为。
- `SolutionSubmissionValidator` 不再要求至少一份脚本文件。缺失/空脚本引用允许继续提交校验，显式异常引用结构仍拒绝。已提供的脚本仍逐一验证方案归属、类型、引用键、版本、哈希与锁定有效性。
- 项目背景、目标、拓扑、部署及 PRE-04 来源与差异校验保持；未改变权限、项目范围、审批状态机或冻结历史。无迁移，不改既有方案记录。

## 验证

- 后端 `SolutionSubmissionValidatorTest`、`SolutionTieredReviewServiceTest` 合计 **29 项通过**，覆盖空脚本、提供脚本时失效/跨方案拒绝、需求跨项目拒绝及已有冻结来源不覆盖。修改前相应空脚本断言失败，日志 `.run/solution-optional-script-before.log`；修改后 `.run/solution-optional-script-after.log`。固定工具链与独占锁入口 `.run/test-solution-optional-script.ps1`。
- 前端 `solutionRequirementSources.runtime.spec.ts`、`solutionWorkbench.runtime.spec.ts` 合计 **20 项通过**。两处拓扑控件使用真实 UploadFile 组件校验全部图片和原文档格式，继续拒绝 EXE 与超限文件。日志 `.run/solution-image-types-after.log`。
- 真实浏览器独立验收页在项目 `992203060197` 上传六个专用 4×4 像素测试图片：现网拓扑 PNG/JPG/JPEG、建设后拓扑 GIF/BMP/WEBP。六次真实文件上传均 HTTP 200、业务 code 0，并显示对应文件名；脚本可选提示可见。证据 `.run/solution-image-optional-browser.json`，测试文件 `.run/solution-image-fixtures/`。
- 验收未保存或提交业务方案；仅形成六份独立上传测试文件，未改方案引用或审批。其余写请求只有既有的项目操作能力检查。独立验收页已关闭，不替用户保存测试图片。

## 部署前记录

遵守此前“暂不重启，保留产物”的决定，59191 与 48182 均不操作。图片扩展已由前端热更新生效；**脚本免传服务端变更尚未部署**，真实空脚本提交仍待明确批准部署后的独立业务样例验收。保留既有冻结包，不提交、不推送。

首次完整编译成功，但构建期间存在并行源码更新，被冻结入口按规则拒绝发布；未使用该未冻结包。对应日志 `.run/solution-optional-script-build.log`。

等待既有工作树构建独占锁释放后，按最新完整工作树重新构建通过，22 项运行修复基线通过。新冻结包 `b7e96fea-7c96-46d5-bf16-f3f866f39b50`，清单 `.run/build/releases/b7e96fea-7c96-46d5-bf16-f3f866f39b50/manifest.json`；日志 `.run/solution-optional-script-build-final.log`。保留原 `35fad14c`、`40144e1c` 等冻结包，未替换运行包。

`scripts/worktree-backend.ps1 -Manifest <上述清单> -ValidateOnly` 部署预检通过（`.run/solution-optional-script-preflight.log`）。59191 仍为 PID 304，48182 仍为 PID 17780。

## 本次明确批准后的部署

用户明确批准重启 59191、部署 `b7e96fea` 并验证不上传配置脚本也能提交，48182 不动。已通过 `scripts/worktree-backend.ps1 -Manifest .run/build/releases/b7e96fea-7c96-46d5-bf16-f3f866f39b50/manifest.json -ApprovedRestart` 部署，记录 `.run/solution-optional-script-deploy.log`：22 项运行基线通过、`backend healthy`、新后端 PID `26364`。

只读复核运行 JAR 与冻结清单一致、运行配置为 `local,dac`；48182 仍为原 PID `17780`。无迁移、无额外服务启停，不修改或提交其他会话源码。

## 部署后真实浏览器验收结果

- 使用项目详情“全流程业务 → 实施方案”的当前 `solution-reviewed` 工作台验收；旧独立列表页只作入口识别，未在旧页面保存/提交记录。
- 初始独立样例项目 `992203060010` 的新方案 `30051`：四类脚本及文件引用均为空，保存、重开通过。真实提交已通过脚本及 PRE-04 校验，随后因该项目未配置审核级别判定返回 `1011003006`（项目审核判定依据缺失）。按原规则完整回滚，方案仍为草稿 status 0/version 1，无审核记录，不补写规则或来源。
- 改用既有独立项目 `2110018733130579975`（工程直签16任务内嵌验收20261016），确认已有有效项目经理和服务经理职责、有效 PRE-04 第 1 版及明确普通审核判定；不新增权限、成员或规则。
- 新方案 **`30052` / V2**：不选择任何脚本文件，四类脚本内容全部留空；仅上传专用 JPG 拓扑测试文件，并填写明确标识为隔离验收用途的设备/机柜说明。真实保存草稿、离开、重开后，拓扑、部署数据保留，四类脚本仍为空。
- 真实点击“提交审核 → 提交审批”，成功创建流程 **`1d6e6996-cc93-11f1-bb18-00ff0b78abd7`**。审批页面显示“审批中”和“服务经理审核”；当前方案只读。审批页面的真实 `solution-reviews/source?businessKey=SOL_REVIEW:1:30052` 响应 code 0、status 2，脚本为四个空字符串且无文件引用。
- 只读数据库复核：方案 `30052` status 2/version 3、脚本引用 0；审核记录 #30 为 RUNNING，来源版本 3、请求版本 1；Flowable 实际待办 `serviceManagerReview`（服务经理审核）已形成。真实 PRE-04 来源 `2110820092715307011` 冻结为 `PRE-04:r1`，审核判定冻结为普通 0。未办理通过、驳回或其他审批意见。
- 原批准方案 `30045` 保持 status 3/version 4；未提交项目 197 的业务方案 `30049`，其仍为草稿（验收期间用户/并行会话已自行保存到 version 4，本会话未写入该记录）。未修改历史。
- 脱敏证据：`.run/solution-optional-script-acceptance.json`；独立验收浏览器页已关闭，原用户页面保留。浏览器机械操作先委派快速模型，其独立连接失效且恢复失败后由主会话接续，未绕过页面或服务端校验。

本次“脚本文件不上传也能提交”真实业务验收通过。部署后未改代码，复用此前 29 项后端、20 项前端检查，不重复构建或扩测。

## 2026-10-21 恢复上传读取与手工录入并存

用户明确要求配置脚本还原为最初“解析和手动录入并存”。已核对旧 `SolutionChapterForm.vue`：原能力为 FileReader 读取文本到输入框、可继续编辑，并非 SCH-03 的 V2 结构化解析服务。仅复用其交互语义到当前启用的 `SolutionReviewChapterForm.vue`，不恢复旧页面或旧接口。

- 移除当前四类脚本输入框的额外 readonly，保留方案 readOnly 对应的 disabled；直接手填或上传 TXT 读取后继续编辑均可。脚本仍非必填。
- 编辑后的脚本文字随现有方案正文保存；上传原文件和文件版本保持原件，下载文案明确为“下载原始脚本文件”，不把手改文字伪装成原文件内容。文件归属及版本校验、权限、并发和冻结历史保护保持。
- SCH-01 核心规则同步说明两种入口及原件保护。未修改后端、共享上传组件、运行包或其他会话改动；无需重启。
- 针对手录四类及重开、文件读取后再编辑、原文件保留和只读历史增加 2 项回归场景；修改前因输入框 readonly 失败。修改后需求来源、方案工作台和脚本文件保存 3 个相关测试文件共 **28 项通过**。日志 `.run/solution-script-manual-before.log`、`.run/solution-script-manual-after.log`。
- 快速模型在真实浏览器使用独立项目 `992203060010` 的现有验收草稿 `30051` 完成：四类手工输入 → 保存 → 切基本信息 → 重开完整回填；网络 TXT 文件选择上传 → 文本读取 → 手工追加 `show status` → 保存 → 重开，编辑后正文和 `solution-script-manual-fixture.txt · v1 · 已保存` 原文件引用均保留。证据 `.run/solution-script-manual-browser.json`。自建标签页已关闭，未发起审批或操作其他方案。
- 只读数据库复核草稿 `30051` 仍 status 0，实际并发版本为 4（页面方案标签为 V1，两者已在证据中分别记录）；网络正文包含读取的两行及手加 `show status`，文件引用 1 份。原批准方案 `30045` 仍 status 3/version 4，审批中方案 `30052` 仍 status 2/version 3。浏览器未再次操作已提交只读样例，该保护由本次定向组件检查覆盖。

## 2026-10-21 恢复五种脚本格式及调整来源

- 用户明确要求恢复 TXT、CFG、CONF、SH、LOG，保留解析后编辑、手工录入及非必填。已同步文件选择、读取及保存前扩展名校验，大小限制、原文件、版本/哈希及方案归属保持原有约束。这里只读取文本，不启用 SCH-03 V2 结构化解析或执行脚本。
- Git 提交 `92129b4a2c8eaecf1c27c9d9b13c874361a32182`（2026-10-16，`fix(engineering): 校验实施方案必填与来源文件`）说明其目的为核验必填章节、有效需求版本及真实脚本文件归属，支持来源快照及版本化上传下载。同一提交将原五种扩展名缩为 TXT、输入框设为 readonly，并要求至少一份文件。这些具体改动已由提交差异核对；未查到将手工录入及其他文本格式取消作为必要条件的独立依据，不推断作者未记录的意图。
- 上一轮恢复两种输入方式时仍留下 TXT 格式限制，本轮补齐。文件留痕与不可变历史保护继续保留，手改正文随方案保存，不覆盖上传原件。
- 后端失败用例复现：普通 `.sh` 被文件名 MIME 校验拒绝，带 `#!/bin/sh` 的文本被 Tika 识别为 `application/x-sh`，仅加文件名扩展仍拒绝。修复仅让真实 UTF-8 Shell 文本按已获用途允许的 `text/plain` 接收；二进制改后缀、含 NUL/非法文本及用途不允许时仍拒绝，继续经过大小、摘要与安全扫描链路。
- 前端 3 个相关测试文件共 **40 项通过**，包含五种格式与大写扩展名的文本读取、继续编辑、原文件保留、只读历史，以及保存时保留原始文件名并拒绝 `.txt.exe`。日志 `.run/solution-script-formats-ui-after.log`；修改前新增保存用例有 5 项失败，日志 `.run/solution-script-formats-ui-before.log`。
- 后端 `FileContentValidationTest` 共 25 项：**24 项通过、1 项外部扫描环境集成检查按既有条件跳过**，无失败。覆盖普通 SH、带 shebang 的 SH、二进制改后缀/非文本拒绝、不允许的用途拒绝及既有格式/摘要/扫描保护。先前两种 SH 均失败；仅放开后缀后仍有 shebang 用例失败，实测识别类型为 `application/x-sh`。日志 `.run/solution-script-formats-backend-before.log`、`.run/solution-script-formats-backend-detect.log`、`.run/solution-script-formats-backend-after.log`，使用固定工具链与同一独占锁。
- 本轮真实浏览器连接失效，按文档恢复发现仍无可用浏览器；未改草稿、未上传、未保存、未提交审批，不能将此前 TXT 的验收冒充五种格式通过。阻塞证据 `.run/solution-script-formats-browser.json`。本轮未重启 59191 或操作 48182，保留其他会话改动。
- 固定入口完整构建及 22 项既有运行修复基线通过，冻结包 `a7b4ad8a-cdc0-4a50-9f11-332912fb8e73`，清单 `.run/build/releases/a7b4ad8a-cdc0-4a50-9f11-332912fb8e73/manifest.json`，日志 `.run/solution-script-formats-build.log`。冻结入口核验构建前后源码一致，保留其他会话源码。只读部署预检因其他会话再次持有构建锁而未执行，日志 `.run/solution-script-formats-preflight.log`；未绕过锁或部署。新后端修复尚未上线，不能将定向检查、构建或冻结替代真实文件保存重开验收。
