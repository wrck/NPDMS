# 巡检专项实现与验证记录

记录日期：2026-10-15。状态：独立巡检的管理员模拟在线、离线及在线失败转离线闭环已完成真实浏览器验收，Task2/3/4均已归档；普通工程师/服务经理正向及正式权威产品资料仍有缺口。旧巡检专用源码已退出，用户批准按预览执行后，186行旧数据清理、16项菜单退休及三项迁移已完成，姓名展示及退休源码冻结包已部署；最终只读复核已完成，巡检新旧入口、CUS及CUT通过；服务等级经后续V479补齐已恢复，复验见续记。本文保留过程和失败证据，不替代PRD或完整业务验收结论。 后续用户扩大授权后，旧维保30行和空旧服务父目录亦已清理，新业务28表保持；该轮最终浏览器复核通过，见清理续记。

来源：[用户批准范围及六份资料对应表](../superpowers/plans/2026-10-15-inspection-special.md)、[PRD V1.8第11章](../baseline/prd-v1.8.md#第十一章-巡检服务模块功能需求)和同目录011～013修订。逐命令运行策略已按用户后续明确指示收口为整批下发、现有整体超时；保留原规则配置和历史，不新增逐命令执行机制。未修改正式PRD。

## 实现范围

| 范围 | 当前实现与保护边界 |
| --- | --- |
| 规则与任务 | 复用现行稳定规则身份、修订、安全审核、发布/停用；独立工作台、任务列表、五步详情、手工建单、项目/客户/多设备引用、准备保存与规则版本冻结。Long身份保持字符串精度，写入沿用平台幂等、版本和数据范围。 |
| 离线执行 | `.log/.txt/.zip`、50MB及解压资源上限；先脱敏再保存安全原文，按冻结设备/规则绑定解析。缺失、歧义、SN冲突、解析失败和超出数值存储精度均不能伪造通过；补传追加结果，历史保留。解析取消只终止本次业务消费，不虚构远端任务已取消。 |
| 在线执行 | PLT/DAC预检与正式采集分别创建任务、分别授权；预检不运行正式命令。默认900秒有效期来自平台配置，并绑定真实完成时间、设备、操作者、规则、端点、认证及凭证授权版本。临时密码只在当次调用内存在、正式执行重新输入。回调追加不可变事实，业务动作才推进巡检状态。 |
| 报告 | PDF/DOCX/XML自动生成；人工PDF/DOCX/DOC/XML经过实际内容验证，至少一种有效格式。每个报告冻结输入结果和精确工件版本，重生成保留V1/V2；不把外部HTTP成功或通知送达视为报告可用。 |
| 标注与待办 | 误报/不关注/确认跟踪追加修订；已产生的跟踪事实不会因后续改标注而丢失。责任人提交处理及证据、申请闭环，授权工程师/管理范围内服务经理确认。取得任务锁后读取当前负责人及确认修订，避免使用等待锁前的旧快照授权。 |
| 归档与客户历史 | 工程师申请、范围内服务经理最终归档；重新核验有效报告、完整标注、全部跟踪项及冻结证据。CUS公开API同事务登记任务、报告、设备、执行、结果、标注、问题、处理和证据来源引用；CUS未确认即不归档。客户页面新增只读巡检历史。 |
| 反馈 | 误报列表、同规则版本统计、明细权限、有效性确认、Excel/CSV快照导出及规则缺失反馈；优化仅创建新草稿。导出保留所选标注/确认版本及当时有效性，不随之后的确认或报告替换变化。 |
| 文件与治理 | 普通附件使用PLT文件能力，客户获准证明可选。报告/日志/证据冻结后禁止替换，归档任务只读。PM-10的INSPECTION提供方改查新任务及未闭环问题，包括已取消任务仍未处置的问题，不推进项目流程。已批准旧数据清理后，客户引用守卫仅查新模型，取消或删除的历史任务仍计数。 |

新入口为 `/pms/inspection/workbench`、`/pms/inspection/tasks`、`/pms/inspection/tasks/:taskId`、`/pms/inspection/rules`。新业务接口及跨Owner扩展见 [API设计的巡检专项契约](../design/10-api-design.md)。旧 `/pms/srv-*` 专用前后端源码及共享页面旧引用已退出；最终冻结包已部署，16项旧菜单已退休，六表186行已按批准预览清理；最终只读复核已完成，巡检新旧入口、CUS及CUT通过；服务等级经后续V479补齐已恢复，复验见续记。

## 迁移与初始化

| 版本 | 内容 |
| --- | --- |
| V477.1 | SRV任务、准备选择、尝试、执行、解析分段、回调回执、检测结果、报告、标注、问题、处理证据和事件。 |
| V477.2 | CUS巡检来源历史及明确的来源关系表，不复制为无来源的客户业务数据。 |
| V477.3 | 误报确认、导出快照及优化草稿来源关系。 |
| V477.4 | PLT采集用途兼容扩展；旧消费者保持原行为。 |
| V477.5 | 同Owner复合外键、状态与取值检查、不可变历史和终态保护触发器；不跨Owner直接建立业务表外键。 |
| V477.6 | 新导航与按钮权限、现行规则菜单迁址、五个文件分类、预检900秒配置和待办通知模板。已有配置不覆盖，不把旧update/delete权限猜测成新执行或归档授权。 |
| V477.7 | 十类高段ID、专用creator及EXAMPLE前缀的未发布草稿；不造权威产品编码，不填生产命令/阈值，不自动审核或发布。 |
| V477.8 | 四项原生文件来源目录初始化；已应用正式Flyway，既有PLT管理API初始化配置完整不变。 |
| V477.9 | 巡检误报CSV/XLSX导出来源目录初始化；已应用正式Flyway，既有同码配置完整不变。 |
| V478.4 | 按批准预览退休16项旧巡检菜单，4项维保菜单保持；已应用正式Flyway。 |

在当前Compose MySQL `24306` 中新建的独立验证schema `npdms_inspection_check_20261015_a13c35b`，从实际 `npdms_domain_test` 只读复制12个依赖表结构及必要定义种子，使用Compose Flyway执行V479～V485。七个迁移全部通过，V484/V485重放无重复；结果为37表、202项约束、49个触发器。新增21项菜单/权限定义、五个文件分类、一个预检配置、一个通知模板和十个草稿。

第一次验证发现V483在同一ALTER中新增索引与自引用外键的执行顺序问题，已拆为两个ALTER并在新验证schema完整通过。失败schema `npdms_inspection_check_20261015_a13c35` 原样保留，没有repair或清理掩盖失败。证据：`.run/build/inspection-db-validation-retry1/inputs.json`、`flyway-migrate.log`、`summary-counts.log`、`result.txt`；生成与执行入口为同目录 `validate.py` 和 `compose-override.yaml`，凭据只在进程环境中传递。

真实业务schema部署前的Flyway成功记录截至V477；V478是本次之外的模板变更。为避免跨过V478使其在以后常规迁移中被忽略，本次七个文件在应用前、隔离验证后仅由V479～V485重命名为V477_1～V477_7，正文不变，原验证日志及测试fixture保留原版本标识。部署使用包含已执行原件及本次七个版本的受控迁移目录，保留正常checksum验证，不修改已执行原件或启用repair。

正式目标的部署前只读预检见 `.run/build/inspection-deployment/preview.md`。397份历史SQL原件单独validate成功；完整包info当时仅列出477.1～477.7七项Pending。部署前完整包validate返回1，原因仅为这七项尚未应用，不能表述为当时完整validate通过。沿用Compose既有 `*:missing`，没有新增忽略规则；历史中294/331为Missing、295～297为既有Deleted，缺失原件及历史校验值已逐条记录。七份新正文与隔离库通过的版本完全一致。

用户批准后，通过 `.run/build/inspection-deployment/migrate.ps1` 完成七项迁移，退出码0。迁移前匹配已冻结的405条历史记录和404份输入文件；迁移后确认原历史前缀不变，恰好追加477.1～477.7七个成功版本。Flyway迁移时内部校验409项通过，未执行V478、V385、V387或repair。该入口只运行Compose一次性Flyway容器，没有启停MySQL、Redis。证据为同目录 `history-after.json`、`flyway-migrate.log` 和 `deployment-status.json`。

## 已执行验证

以下是测试/数据库契约证据，不等于完整业务验收。不同轮次可能重复覆盖同一类，不累加成“独立用例总数”。测试统一使用 `scripts/worktree-toolchain.ps1` 的JDK25.0.1+8、Maven3.8.6、`.run/build/m2`及工作树独占锁。

| 验证范围 | 实际结果与证据 |
| --- | --- |
| 判定、状态机、日志归档、报告格式/导出、CUS登记及删除守卫 | 62项通过，零失败/错误/跳过。`.run/build/inspection-targeted-tests-2.log`。覆盖无法判定、日志/压缩资源边界、非法人工格式、XXE、报告实际内容、CSV公式防护、归档来源冲突等。 |
| PLT/INT采集兼容、来源授权、凭证重验 | 46项通过，零失败/错误/跳过。`.run/build/inspection-online-platform-tests.log`。 |
| DAC预检/整批采集、签名/指纹、保存连接授权、回调及实际执行时间 | 55项通过，零失败/错误/跳过。`.run/build/inspection-online-dac-tests.log`。 |
| SRV预检、回调、共用解析及PM-10 | 最近42项通过，零失败/错误/跳过。`.run/build/inspection-latest-srv-tests.log`。含18项授权、8项回调、10项解析、6项治理守卫；已成功的解析分段不会因后续刷新改写完成时间。 |
| 前端首轮 | 18个文件ESLint通过，12个Vue组件解析和script/template编译通过。`.run/build/inspection-frontend-eslint.log`、`inspection-frontend-sfc.log`。这是补普通附件入口前的结果，后续增量需单独验证；未以此宣称vue-tsc或浏览器通过。 |
| 待办、误报确认、归档、文件策略及真实Mapper | 76项通过：待办15、反馈13、归档17、文件策略26、真实Mapper3、PLT来源文件授权2，零失败/错误/跳过。`.run/build/inspection-db-validation-retry1/targeted-tests.log`及`mysql-tests-retry.log`；首次MySQL测试因验证脚本把JDBC编码配置为utf8mb4未能连接，改为Java编码UTF-8后仅重跑3项。所有fixture和更新同连接事务回滚，`fixture-residue.log`确认残留为0，未改权威规则种子。 |
| 浏览器发现的项目树版本遗漏 | 63项通过：新访问服务9、CUS历史7、文件策略26、归档17、项目上下文Owner4，零失败/错误/跳过。`.run/build/inspection-deployment/project-tree-version-tests.log`。严格断言调用携带实际树版本，验证空/placeholder范围拒绝、Owner冲突传播及锁后改派，不放宽提供方校验。 |
| 批量项目查询与工作台分组 | 37项最终通过：项目范围7、原项目锁定4、项目候选真实MySQL5、巡检访问9、任务查询7、巡检真实MySQL5。分三轮仅重跑失败/未执行项，见`.run/build/inspection-deployment/query-tests.log`、`query-tests-retry1.log`、`query-tests-retry2.log`。首轮补齐测试装配漏载的正式XML片段；第二轮发现并修复创建人CAST导致的真实排序规则冲突，改为绑定字符串用户ID，未改变连接排序规则或断言。覆盖6687项目常数次授权、100条候选上限、字面百分号/下划线、空集合、租户隔离及两组工作台权限交集；临时表随连接关闭，事务fixture残留均为0。 |
| 设备候选分页与AST本地服务主体 | 25项通过，零失败/错误/跳过：既有Mapper1、真实MySQL5、设备选择服务7、AST请求授权7、巡检只读适配2、实际local配置装配3。先在配置缺项时得到预期失败，再补齐配置通过，见`.run/build/inspection-deployment/asset-local-config-red.log`及`asset-green-tests.log`。MySQL覆盖项目/合同去重、计数和分页、四项筛选、租户/删除边界及空输入。只在已授权隔离schema使用空设备表和同连接回滚fixture，未向业务设备表写入；隔离设备残留与失败验收任务残留均0，见`asset-green-residue.json`。 |
| 日志输入拒绝与脱敏JSON真实内容识别 | RED先复现：平台有效JSON/扫描用例被提前媒体拒绝，INT丢失输入拒绝类型，SRV离线/采集输入误分类；三轮均按预期失败，日志`input-file-red-platform/integration/service.log`。修复后四类62项中61通过、零失败/错误、1项既有ClamAV环境条件跳过；媒体17项含16通过、网关6、执行13、文件Owner策略26通过。SRV reactor未包含INT业务模块，因此网关单独补跑，未把缺执行计为通过。日志`input-file-green-tests.log`、`input-file-green-integration.log`。未运行ClamAV实服验证，未操作数据库。 |
| 文件内容读取50MB契约 | RED复现小文件按50MB上限读取及50MB边界两项均被旧20MB参数上限拒绝，5项中3通过、2错误。修复后INFRA5项、既有设备日志4项共9项通过、零失败/错误/跳过；小于50MB的调用方限制仍保留，原20MB采集读取断言通过。见`storage-read-red.log`、`storage-read-green.log`，不重复执行未受影响的媒体测试。 |
| PDF分页与续表身份 | RED从实际PDF几何复现表后标题重叠、标题与首行分离、续页SN不完整，11项中原8项通过、新3项失败。仅修复PDF间距、整组空间预留及短身份单元格重复后，11项全部通过，零失败/错误/跳过；XML/DOCX生成逻辑及原字号未改。见`report-layout-red.log`和`report-layout-green.log`；修复已受控部署，真实页面取得的V3三页PDF已逐页目视通过，见`report-qa/V3-1.png`至`V3-3.png`及`report-v3-content-check.json`。 |

补普通附件入口后，三个受影响文件ESLint、两个SFC编译通过；误报确认输入未选值从null改为undefined后，反馈面板ESLint/SFC通过。严格vue-tsc定向入口复验中，本次巡检目录、客户巡检历史及父集成路径无诊断；真实import依赖仍有14项既有错误，原始退出码2，未伪装整体通过。见 `.run/build/inspection-ui-typecheck-retry.log`。

该轮过程记录：受控 `worktree-build.ps1 -Target backend/dac -MavenArguments @('-DskipTests')` 已分别完成完整clean package及冻结；后端22项、DAC1项既有修复保护校验通过。首轮部署后端清单为 `.run/build/releases/8fb06da7-bd24-4958-8c03-7149d8e36b37/manifest.json`，DAC为 `.run/build/releases/ef980628-2320-45ad-baf9-d1fe5ae67e67/manifest.json`；日志 `inspection-backend-freeze-final.log`、`inspection-dac-freeze-final.log`。二者已成功启动并达到就绪状态，证明实际Spring装配可运行；在线/离线真实浏览器闭环、报告V1/V2下载、普通账号权限验收和旧入口退出回归尚未完成。

独立解析运行检查：通过正常TLS校验调用normalize返回200，识别一个命令块；提交明确标记的UPLOAD技术样本，任务SUCCEEDED，解析版本1.3.2，原请求重放返回同一taskId。`executionVerified=false`、无采集来源或设备身份，不形成巡检执行/结果证据。见 `.run/build/inspection-deployment/parser-runtime-check.json`。

真实Chrome首轮通过正常UI登录并取消“记住我”，没有保存认证状态。工作台、任务列表和规则页可直接打开及刷新，导航高亮正确；十类示例草稿可见，编辑和修订历史可打开。工作台、任务查询和创建项目候选实际返回业务码1014024033。定位为新 `InspectionAccessService` 的初次上下文查询未传 `expectedScopeVersion`；`CustomerInspectionHistoryService` 同类调用也遗漏，已在消费者中补充先解析当前授权范围、再携带树版本的调用，Owner校验保持不变。修复的63项定向测试已通过并部署，正在浏览器复验。截图和脱敏请求证据位于 `.run/build/inspection-browser-acceptance/`，此首轮阶段没有通过浏览器创建巡检业务数据。

修复后后端受控构建成功，用时2分56秒，22项保护检查通过。本次恢复采用的冻结清单为 `.run/build/releases/fb30e01b-6f3f-404b-97cb-cec08c50ba53/manifest.json`，记录冻结时真实离线状态；新后端 `ValidateOnly -StartIfOffline` 与首轮DAC清单 `ValidateOnly` 均退出0。日志见 `.run/build/inspection-deployment/backend-project-tree-version-freeze.log`、`backend-project-tree-version-validate.log`、`dac-offline-validate.log`。本轮未重复构建DAC或执行迁移。

## Demo结构整改与第二轮浏览器发现

按用户指定的《割接模块专项实施计划（V1＋V2，修订版）》五章组织方式修订巡检计划，补齐逐页字段、操作、保存重开、服务端通过条件及视觉验收目标；没有照搬割接审批、角色或项目联动范围。参考HTML的布局以源码核对为证据，其文件预览曾被工具策略拒绝，没有更换地址或工具绕过，也不声称完成了参考页面像素比较。

页面整改包括工作台四项统计及“巡检任务待办／自建巡检任务”两表、整页新建的项目和准备双卡、五步圆圈及连线、详情项目摘要、在线执行分区、离线点击/拖放上传区与命令清单、同页报告和标注、闭环已完成/未完成两表。保留平台导航与现有提交契约；历史可查看，未来步骤的浏览不推进业务状态。长来源ID移入可展开详情，仍保留完整Long、报告版本、执行来源和文件引用。灰底、白卡、字体密度、蓝色分区和主按钮参照Demo；没有填入Demo中的人员、统计、设备或成功结果。

第二轮真实浏览器发现两个实际问题：

- 当前HTTP入口的`crypto.randomUUID`不可用，第一次创建规则未发出POST。五个新页面/组件的16处调用改为平台已有`generateUUID`，HTTP回退相关5项既有测试及局部ESLint/SFC检查通过，未改公共工具实现。
- 当前租户有6687个可见项目，工作台、任务列表和项目候选逐项目加载经理事实，实际请求超过30秒；数据库单条SQL本身很快。已改由PROJ公开契约批量提供当前可见范围及真实有效服务经理交集，项目候选由Owner一次SQL限100项返回；SRV据此查询，写入仍保留原项目/树版本及锁后重验。工作台两表分别筛选非本人创建的未终结任务及本人创建任务，两者均继续受负责人/管理范围和可见项目交集限制。该后端修复的37项定向验证已通过，受控完整构建成功（3分04秒、22项保护检查通过），冻结清单为`.run/build/releases/c1e06aa4-0664-4785-9613-5c1bb72571ba/manifest.json`，ValidateOnly退出0。构建及只读校验日志为`.run/build/inspection-deployment/backend-query-freeze.log`和`backend-query-validate.log`；用户已批准并完成本次59191部署，DAC源码和产物未变。

UUID修复后，管理员通过正常UI创建唯一验收草稿`ACCEPT-INS-20261015-785295`，只填写检测项、描述、基础分类和排序。第一次保存后关闭重开，第二次将描述和排序改为716后保存、关闭重开，再刷新重开，内容均保持；仍是草稿修订1、元数据版本2，命令、阈值和产品类型为空，没有审核或发布，也未修改十类示例。截图为 `13-rule-reopen-first.png`、`14-rule-reopen-second.png`、`15-rule-refresh-reopen.png`。

Demo整改后规则页再次只读验收：1440×1000桌面和390×844窄屏均无页面级横向溢出，未捕获页面脚本错误；新建、编辑和保存入口保留，原验收草稿内容仍正确。截图位于 `.run/build/inspection-browser-acceptance/16-rules-demo-desktop.png`、`17-rules-demo-editor.png`、`18-rules-demo-mobile.png`，主线程已目视核对。该次没有修改、保存或发布；工作台、任务新建及详情仍需后端修复部署后的真实浏览器验收。

同一草稿点击“发布预检”，服务端返回五项必填问题：严重级别、命令、预期结果正则、阈值和适用产品类型。首轮发现结果位于长表单底部，截图视窗未覆盖结果且字段说明为英文；已修复为已知字段/校验项中文显示，并在响应后的页面更新完成、再次核对当前修订后滚动到结果。未知信息保留服务端原文，原始路径/代码/说明可展开查看，保存、审核和发布契约不变。

局部ESLint及SFC编译通过后，真实浏览器1440×1000复验确认五行中文结果进入当前视窗，展开首项仍为`severityCode / REQUIRED / REQUIRED`。关闭重开仍为草稿修订1、版本2，排序716和描述保持不变，页面异常为0，未保存、审核或发布。截图为`.run/build/inspection-browser-acceptance/21-rule-precheck-chinese.png`及`23-rule-precheck-raw-detail.png`，主线程已目视核对五行结果截图。

本轮前端分批ESLint及12个SFC的script/template/style检查通过，共享样式编译通过。定向vue-tsc先发现详情报告标题闭包中的空值类型错误，已通过捕获当前任务的computed修正；复验巡检/API/客户巡检范围无诊断，其余14项与原有依赖诊断逐条一致。真实退出码仍为2，不能写成整体类型检查通过；日志为`.run/build/inspection-ui-demo-typecheck.log`及`inspection-ui-demo-typecheck-retry.log`。

批量查询修复部署后的真实浏览器结果：工作台首次0.859秒、刷新0.219秒；任务列表首次0.204秒、刷新0.094秒；项目候选首次0.266秒，均为HTTP200且业务码0，既有三项请求超时未再出现。当前新任务表为0行，工作台四项统计和两组任务列表的空态与数据一致；这不替代有数据时的分组业务验收。主线程目视核对`24-workbench-final.png`、`25-tasks-final.png`，同时发现列表三个筛选下拉框被局部100%宽度规则挤窄。

该样式问题只在列表筛选区修正内容区宽度，未修改新建表单或业务脚本。局部ESLint/SFC通过，真实浏览器复验桌面项目/方式/状态宽度分别240/160/160px，390px手机视窗无页面级横向溢出；主线程目视核对`29-tasks-select-width-desktop.png`和`30-tasks-select-width-mobile.png`。新建页五步导航、项目/设备卡与准备信息卡也已在实际页面查看。

使用现有项目1001（北京华盛金融核心网络集成项目）和设备1001（SN-EQP-2026-001）只提交一次`ACCEPT-INS-20261015-QUERY`验收建单，服务端返回1015005001“产品类型服务身份未授权”；仍停留新建页，项目、设备、名称和背景保持，未跳转成功详情。只读复查任务、任务设备和事件残留均0。实际首先触发AST服务调用主体校验，不能将该次响应记为已验证“产品类型数据缺失”分支，也不能宣称建单/保存重开成功。证据为`27/28`建单截图及`.run/build/inspection-deployment/device-options-runtime-diagnosis.json`。

查询失败保护使用浏览器仅中断设备候选请求的方式验证：已有合法设备选择后切换项目，旧设备清空，错误和重新加载入口可见，保存按钮禁用，任务POST计数0；解除中断后查询恢复。未保存离开选择“取消”保留表单，选择“确定”返回列表，均未提交业务写入。主线程目视核对`31-device-query-failed.png`，返回截图为`32-leave-confirmed-list.png`。

该轮过程记录：本轮还发现设备候选首次耗时13.015秒，后续实际3.047～3.907秒。数据库摘要显示项目/合同OR倒序查询扫描约134万设备；只读EXPLAIN确认现有项目、合同索引可分别选出约99和4行，合并后再回表。已将新巡检候选分页改为Mapper XML中的项目/合同UNION去重查询，保留旧设备页面查询和保存锁定校验；未新增业务库索引或迁移。AST只读服务主体缺项已按现行ADR-0036补齐到local配置，仅登记固定INSPECTION技术主体及两个读动作，缺配置和写动作继续拒绝，没有创建登录账号或授予用户角色。两项增量的25项验证已通过，当前运行包仍是`c1e06aa4`，新代码尚未经运行环境复验。

该轮过程记录：随后受控完整后端构建退出1，用时2分03秒，失败发生于CUT的`testCompile`：`CutoverChecklistMatchInput`已替代原设备类型List入参，六个测试文件的七处调用尚未同步。具体为ControllerContractTest、FullFlowMySqlTest、FullFlowPositiveLoopMySqlTest、ApplicationServiceTest两处、MatchingTest和PositiveLoopMySqlTest，见`.run/build/inspection-deployment/backend-asset-green-freeze.log:1489`。读取差异时CUT生产文件仍在被并行修改，因此保留其工作，未代改、跳过测试编译或恢复旧接口；没有生成新manifest，也未执行新部署。该完整构建失败不改变先前25项定向测试的结果，但仍阻断本次两项增量的冻结与部署；须待并行调用方同步、受控构建成功后再按新冻结清单申请本次增量重启。

构建失败后只读复查：59191仍为PID48240、`local,dac`和`c1e06aa4`实际产物，health返回200；48182、19191、59192的进程与监听均未变化，工作树构建锁已释放，本轮新增manifest为0。证据为`.run/build/inspection-deployment/asset-build-failure-runtime-status.json`，没有执行服务操作或业务POST。

用户要求继续验收后再次只读核对：CUT负责会话已同步原失败测试的对象入参，重新开始受控完整构建。当前运行包仍为`c1e06aa4`，解包核对尚不包含设备候选UNION或新的local只读主体配置；三张AST权威表仍为空，失败建单残留0。见`.run/build/inspection-deployment/acceptance-refresh-status.json`。真实浏览器重新登录后，工作台首次/刷新0.562/0.203秒、任务列表0.157/0.062秒、项目候选0.203秒，展开下拉再查0.125秒且返回100项，均HTTP200、业务码0；任务0条、规则11条，未捕获页面或控制台错误。未提交建单、保存或发布，不能据此宣称服务主体修复已运行。证据为`33-current-query-response-summary.json`和同批33/35/36截图，主线程目视核对工作台与全页新建。

此次重构建的Maven reactor于13:05返回BUILD SUCCESS（2分18秒），包含CUT测试编译；受控入口随后因`Source changed during build; artifact cannot be published`整体退出1。构建窗口内13:04:49，CUT多个Java/XML、测试及共享`ProjectBusinessContextApi/Impl`发生修改，均属源码指纹范围；Markdown和页面样式不在该范围。没有绕过前后源码比较、生成manifest或运行ValidateOnly。日志为`.run/build/inspection-deployment/backend-asset-green-freeze-retry1.log`。已请求协调约3分钟的源码冻结窗口，待并行写入停止后再构建；本轮25项AST定向结果仍有效，但不能把这次Maven成功当成当前全部源码的一致冻结产物。

用户随后明确回复“创建模拟记录进行验收”和“已协调暂停修改，可以立即冻结”。通过AST设备维护和产品类型受控导入接口创建两台新设备`ACCEPT-INS-20261015-A/B`，ID分别为`2110600122140016642`、`2110600125768089602`，均明确标记模拟验收并引用既有项目/客户1001。新增产品类型`MOCK_INS_20261015_A/B`，来源`INSPECTION_MOCK_ACCEPTANCE`、版本`MOCK-1`，仅各绑定对应的新设备。读回确认三张产品类型表当前各2条、两条有效设备映射、两条Owner创建设备历史和两条受控导入成功审计；原设备1001没有新增映射。没有把模拟来源表述为CRM生产数据，没有直接写业务表。证据见`.run/build/inspection-deployment/mock-data-db-check.json`及`.run/build/inspection-browser-acceptance/mock-data/`。

该轮过程记录：真实规则页面创建并保存`ACCEPT_INS_20261015_LATENCY`，名称“【模拟验收】离线合成延迟检测”，规则ID`970485012`、修订ID`2110600966717652993`。关闭重开后仍为草稿修订1、版本1，命令`show version`、30秒、超时不继续、正则`AcceptanceLatency:`、数值阈值`≤10ms`及两个模拟产品编码均完整保留。描述明确人工合成、不得用于生产巡检；这些参数仅是获准模拟验收输入。尚未预检通过、安全审核或发布，十类原示例和此前元数据验收草稿未改。证据为`mock-data/rule-saved-detail.json`及`38-mock-rule-saved-reopened.png`，主线程已目视核对。

该轮过程记录：准备了A设备10ms、B设备缺失数值和补传99ms三份明确标记模拟的离线日志，以及结构无效的归一化失败输入。预期分别为正常、未判定、异常和上传拒绝；尚未通过巡检执行链路验证，不能写成实际结果。源码稳定窗口内，retry2在CUT新增测试编译错误处失败；负责会话修正后retry3的Maven构建成功，但期间CUT测试文件再次变化，仍由源码一致性检查拒绝冻结。日志保留在`backend-asset-green-freeze-retry2.log`和`retry3.log`，未跳过测试编译或绕过冻结保护。

retry4先确认30秒源码稳定，再经标准入口完成完整clean package，耗时2分33秒，源码前后比较和22项保护检查通过，整体退出0。冻结清单为`.run/build/releases/4abedf5b-8565-4fca-b7d1-cfdb5fd2dd41/manifest.json`，固定`local,dac`，包含当前工作树的CUT并行改动；随后`ValidateOnly`退出0。证据为`source-stability-retry4.json`、`backend-asset-green-freeze-retry4.log`及`backend-asset-green-validate-retry4.log`。用户单独批准后完成59191部署，22项保护检查通过，独立宿主50788成功退出0，后端PID48640在其退出后保持运行，运行包与清单一致、health为200；DAC8132、前端44800、网关1324不变，锁已释放。实际部署证据为`.run/build/inspection-deployment/asset-backend-deployment-status.json`。

新部署后，真实规则页对上述模拟修订预检返回业务码0、问题为空，并返回两个AST名称候选；应用候选、保存到版本2、再次预检无问题，随后记录安全审核`PASSED`并正式发布。审核引用`INS-REVIEW-2110603249375346689`与发布记录一致，发布结果为`PUBLISHED`、修订1、版本3，未停用其他修订。主线程已核对实际响应及发布后只读界面，证据为`mock-data/rule-release-steps.json`、`rule-published-result.json`及`39`～`41`截图。该通过结果只适用于明确标记的模拟规则，不代表十类生产规则包完成。

模拟任务首次真实建单为`1 / INS-2110603390243229696`，项目1001、两个新模拟设备、`OFFLINE/PREPARING`，创建业务码0、版本1。准备页分别选择上述规则修订并保存，返回列表重开后两条选择完整；再次保存、重开和刷新后任务版本3，背景、设备和规则选择均一致。项目候选0.219秒、设备候选0.969秒、创建1.062秒。截图`42`～`45`及`mock-data/task-final-detail.json`为实际证据，主线程已核对；该管理员为owner但不是项目1001的有效服务经理。

任务1随后通过页面开始一次离线尝试并实际下载冻结命令清单。用户进一步要求“复用割接模块的模拟项目和设备”后停止该任务后续写入；只读收尾为任务`EXECUTING/版本4`、尝试1`OFFLINE/ACTIVE`，执行、结果、证据及回调回执均为空。上传自动化初次误选隐藏附件输入，尚未形成可证明的日志POST或拒绝结果，不作为UI功能缺陷或解析失败证据；后续应使用明确的“选择离线巡检日志文件”入口。原记录和命令清单保留，见`mock-data/task-interrupted-detail.json`。

按用户新指令，后续验收复用CUT模拟项目`992203060201 / 默认适配页面回归 20261012`及设备`2109575624887562241 / NATIVE-UI-20261012-DEVICE-02`、`2109575662145564674 / NATIVE-UI-20261012-DEVICE-03`。项目已有客户`2102228462045130754 / 中国科学院声学研究所`及admin有效主责服务经理、项目经理；五个关联CUT任务全部已归档，九条设备范围关联无活动占用。设备客户字段为空不违反现行Owner选用或巡检创建契约，不据此补造设备客户。只读清单为`cutover-two-device-reuse-check.json`，写入前事实快照为`cut-reuse-before.json`。

这两台既有模拟设备的当前产品类型映射为空。按既有模拟数据授权，通过AST受控导入新增明确标记的`MOCK_INS_CUT_REUSE_20261015`类型、同名来源键和两条设备映射，来源仍为`INSPECTION_MOCK_ACCEPTANCE / MOCK-1`；唯一固定幂等请求返回业务码0、typeId3、sourceMappingId3、replayed=false。未新建项目、设备、账号或授予角色，未调用CUT或项目状态动作。导入后只读核对产品类型/来源/有效设备映射总数为3/3/4；项目、成员、设备、CUT任务、闭环及范围关联六组既有字段与导入前一致，新增一条成功导入审计。见`cut-reuse-after-check.json`；请求原件、来源样本与响应位于`.run/build/inspection-browser-acceptance/cut-reuse/`。

原模拟规则通过真实页面复制修订2，新增上述类型并保留原A/B适配，命令和阈值不变；保存、权威名称回填、两次预检及安全审核后发布为`2110607611698970626 / PUBLISHED / 版本3`。原修订1自动停用，内容保留且任务1冻结引用未被替换。见`cut-reuse/rule-revisions-readback.json`和截图`50`、`51`；主线程已目视核对发布页。

复用任务`2 / INS-2110608319095689216`通过真实页面创建，项目`992203060201`、两台既有模拟设备、OFFLINE。分别选择规则修订2并保存，重开、第二次保存、重开及刷新后为`PREPARING / 版本3`，背景、设备、规则引用一致，当前账号owner和manager均为true。项目候选0.109秒、设备候选0.859秒、创建0.921秒。见`cut-reuse/task-final-detail.json`、截图`52`～`54`；主线程已核对数据及刷新页面。这仍是管理员操作证据，不替代普通角色权限验收。

这次导入前遇到环境中断：19191短暂连接拒绝，MySQL socket短暂不可达；只读复查时MySQL/Redis容器均显示13:41:11启动、healthy，不能仅凭该时间推断重建或退出原因。原前端44800已退出，新的本工作树Vite进程50732恢复19191并返回200；后端48640和DAC8132仍运行且健康200，网关1324已退出、59192无监听。本会话未在该时段操作服务；见`cut-reuse-runtime-interruption.json`。

该轮过程记录：任务2实际开始离线尝试2并下载冻结命令清单。首次通过明确文件输入上传异常结构样本时，业务返回`1013007010 / DEPENDENCY_UNAVAILABLE`，说明日志脱敏不可用、原文件未存储；读回为`EXECUTING / 版本4`，执行和结果均0。独立TLS校验探针分别调用异常样本和正常样本的normalize，均HTTP500；DAC日志表明在JWT认证阶段请求`https://10.210.0.11:59192/.well-known/jwks.json`连接超时，尚未进入脱敏处理。故不能把此次记为异常结构校验通过或远端解析失败；网关恢复后需重新验证。探针没有提交解析任务或写业务记录。证据为`cut-reuse/normalization-rejected-detail.json`、截图`55`/`56`及`.run/build/inspection-deployment/normalize-rejection-probe.json`。

用户单独批准恢复59192后，使用已只读校验的`start-existing-gateway.ps1`隐藏宿主复用原网关入口、TLS和JWKS，未重生成身份。宿主15100成功退出0，Node38068持续运行并监听59192，严格TLS的health/JWKS检查通过；后端48640、DAC8132、前端50732保持原进程。恢复后normalize-only探针确认正常日志200、一个命令块和一个SN；异常结构样本400/INVALID_REQUEST。见`gateway-restoration-status.json`和`normalize-after-gateway-probe.json`，没有提交解析任务。

该轮过程记录：真实页面复验异常结构样本仍得到503/DEPENDENCY_UNAVAILABLE，确认巡检消费者把确定的输入拒绝误分类为可重试依赖故障。随后上传DEVICE-02的错SN样本，日志经脱敏后保存被平台文件策略拒绝，业务码`1010001007 / 文件媒体类型不符合用途策略`，尚未到SN校验；读回任务2仍版本4、执行/结果0。见`cut-reuse/upload-stage-results.json`及截图`57`/`58`。正在补失败测试并修复这两处明确故障；未把本次拒绝计为SN不符或正常/未判定结果验收通过。

上述缺陷已在代码修复、通过定向验证并确认运行包包含修复：INT用`InvalidInputException`区分DAC明确的400输入拒绝/413大小拒绝，SRV返回FILE_INVALID；认证、网络和服务故障继续作为依赖不可用。PLT仅在声明JSON且Tika检测为text/plain或application/json时，流式验证一个完整JSON值并拒绝尾随内容，再认可application/json；原字节、大小、用途白名单、扩展名、摘要及安全扫描检查保持生效，不把任意文本放行。生产文件范围为既有共享内容校验类、解析API/网关和新的巡检执行服务；无需数据库变更。

本轮完整后端构建在源码稳定后执行；首次因他项持锁而在Maven前拒绝，正常等待释放后只有一次实际clean package，耗时2分17秒、退出0、源码前后相同，22项保护及ValidateOnly均通过。冻结清单为`.run/build/releases/c4d9a061-a5be-4434-861d-fe897af03c82/manifest.json`，见`input-file-backend-freeze-after-lock.log`和`input-file-backend-validate.log`。复查发现当前后端已为13:59:13启动的PID39560、运行包匹配本树`7f5e9c32`清单；四处相关class与本轮修复不同，运行包缺少新异常子类，不能视为修复已部署。DAC8132、前端50732、网关38068仍在原进程；本会话没有实施这次后端变化，也未确认变化来源。只读对比为`input-file-runtime-refresh.json`，该记录为当时部署前状态。

用户批准c4部署后，首个独立宿主在任何服务操作前失败，原后端保持运行；错误消息未保存，不能推定失败原因。启动宿主补充安全阶段及堆栈位置后，重试前发现后端已变为PID31072、运行清单`d0de83d9-61f8-43b8-b334-4da3f17dc3cb`，source与c4同为`7621fb57`，四个生产class及新增异常子类逐字节一致，22项保护及健康检查通过。复用当前已部署修复，不重复重启。见`input-file-runtime-refresh-retry.json`；本会话未实施该次外部部署。用户随后明确授权本专项必要的后续服务重启无需逐次询问，受控入口、锁、冻结清单和校验仍保持。

真实页面确认异常结构日志返回HTTP400、`FILE_INVALID`且执行/结果未增加。错SN样本随后暴露原生材料目录缺少`INS.LOG`；四项来源`INS.LOG`、`INS.REPORT`、`INS.EVIDENCE`、`INS.ATTACHMENT`通过既有PLT目录API分别新增为19～22，与InspectionFilePolicy的名称、媒体及50MB策略一致，其他类型未改，不建立任何必交要求。实际入口为`/admin-api/api/v1/pms/delivery/types`，见`cut-reuse/delivery-types-initialized.json`。新增前向`V477_8__inspection_native_delivery_types.sql`保留已执行原件；在原隔离库用477.8的out-of-order执行验证编号调整，旧479～485原件仅用于checksum校验。仅477.8待应用并执行成功，重放无重复，停用、自定义和已删除配置均不覆盖；正式业务库本轮仅经Owner API初始化，未执行该SQL或更改Flyway历史。证据为`native-delivery-validation/`。

目录补齐后，真实错SN上传成功登记执行3为`FAILED / DEVICE_SERIAL_MISMATCH`、结果0。正常DEVICE-02日志成功登记执行4为PENDING，任务2版本6；刷新解析遇到`FILE_STORAGE_CONTENT_IDENTITY_INVALID`。已定位PLT授权读取允许50MB、INFRA读取参数仅接受20MB的契约不一致；失败测试已复现且修复后的9项定向验证通过；未把上传成功当作解析完成。验收轮询脚本未在非零业务码立即停止，重复了6次刷新后停止，后续脚本已要求非零立即中止；没有新增重复上传或执行尝试。现场见`cut-reuse/parser-refresh-500-detail.json`、截图`70-parser-refresh-500-pending.png`。

50MB修复后的首次完整构建被CUT新增测试三处不存在的`failure.getCode()`调用阻断，日志`storage-read-freeze.log`。主控准备仅三处改为`code()`的预览，用户明确授权；执行前并行会话已于14:29:30修正，因此本会话没有改写CUT文件。该单类随后真实执行5项全通过。等待同工作树互斥锁期间，另一标准构建形成`6f8395d6-a7ae-4254-bf0d-3e4af7f837c7`冻结产物，source与当前完全一致，复用已有完整产物而不重复clean；运行基线、22项保护和ValidateOnly通过，主控已核对并按用户持续授权安排59191部署。见`storage-cutover-targeted.log`、`storage-read-freeze-retry1.json`及校验日志。

50MB修复产物随后通过原受控入口部署：独立宿主52240成功退出0，后端49632保持运行，固定local,dac，运行包与6f8395d6清单一致，health为200，22项保护通过且锁已释放；DAC8132、前端50732、网关38068未变。证据为`storage-read-deployment-status.json`。用户已持续授权本次任务必要的重启，本次未重复请求批准。

执行3的冻结日志已通过真实页面按钮实际下载，工件`2110616942632857601`、版本1和文件引用与失败执行一致，内容为脱敏规范JSON，含错SN及模拟声明。最初验收监听原页面未收到下载事件，改为监听按钮打开的新窗口后成功，不是产品下载缺陷。主控已读取下载内容并目视失败行截图`71`；证据为`cut-reuse/execution3-download-evidence.json`与`execution3-normalized-downloaded.json`。

部署后只刷新既有执行4，两次实际读取完成解析，设备02结果1为PASS/10ms/MATCHED，解析版本1.3.2。设备03上传缺失数值日志形成执行5、结果2 UNJUDGED/VALUE_MISSING；页面点击完成被服务端STATE_CONFLICT拒绝，任务仍EXECUTING。补传99ms形成执行6、结果3 ABNORMAL/THRESHOLD_NOT_MET，结果2及错SN执行3均保留。相同99ms文件再次上传返回成功，但任务版本14、4条执行和3条结果均不增加；随后完成执行进入REPORT_PENDING/版本15，尝试2为COMPLETED。主控已读取前后详情并查看稳定页面，见`cut-reuse/exec4-after-read-limit-fix.json`、`unjudged-completion-blocked.json`、`report-pending-final-detail.json`及73～78截图。这些数值仅来自获准合成输入，不代表真实设备巡检。

等待修复期间补充有数据的只读UI验收：工作台四项统计为0/0/2/0，待办为空、自建分组两条EXECUTING任务，与返回事实一致。列表总数2，名称关键词命中复用任务1条，已归档筛选0条，清空恢复2条，关键响应均业务码0。主线程目视核对列表和无加载遮罩的工作台；原带遮罩截图保留，不作为稳定视觉证据。见`cut-reuse/read-list-evidence.json`、`60-workbench-two-tasks-settled.png`和`61`～`64`。本轮仅GET，没有新增或改变任务。

报告V1和V2通过真实页面分别生成AVAILABLE，任务为ANNOTATION_PENDING/版本17，两版均冻结结果1和3。每版PDF/DOCX/XML均从页面按钮获取实际文件；PDF为浏览器新窗口inline响应原始字节，DOCX/XML触发原生download，不能混称三者都触发浏览器下载事件。随后从V1历史行重新获取三格式，逐字节比较均一致，V1完整报告对象及精确引用未被V2替换，见`cut-reuse/report-v1-history-comparison.json`。主控对两版PDF、DOCX内部正文及XML核对中文、版本、两个SN、10/99ms和模拟声明，内容一致；V1三页PDF目视发现表后异常清单标题与边框重叠、续页仅重复SN前半段，另有标题孤页，故尚不标为完整报告视觉验收通过。原件和V1/V2历史保留，新生成版本修复排版，人工版本号后顺延。

误报导出所需第五项`INS.EXPORT`经当前目录GET确认缺失，随后按既有Owner策略经PLT目录API创建为23，CSV/plain/XLSX、50MB及启用状态读回一致，其他类型不变。前向477.9在原隔离库中为唯一Pending并成功应用；原四项逐字段、旧历史/checksum均不变，重放及停用/删除/同码保护通过。见`cut-reuse/delivery-export-initialized.json`和`feedback-delivery-validation/result.json`。该轮正式Flyway尚未应用477.8/477.9（后续获批执行见最终退休结果），不修改已执行原件。

PDF分页修复通过11项定向测试后，完整受控构建用时2分23秒、22项保护和ValidateOnly均通过，冻结清单为`02ced52d-06a2-44c3-b3ed-836faf68c04e`。部署前当前运行包已变化为774cee基线，实际报告类及嵌套类与新冻结产物不同；核实清单捕获同一基线后按本次任务持续重启授权部署。宿主51660成功退出0，后端4296在宿主退出后继续存活，运行包17b9b9da与清单一致，local,dac/health200，锁释放，DAC/前端/网关PID不变。见`report-layout-class-comparison.json`与`report-layout-deployment-status.json`。后续只生成新报告版本，V1/V2原件不变。

真实页面生成V3/AVAILABLE后获取三格式，PDF37860字节、DOCX3717字节、XML3261字节。主控逐页目视V3三页PDF：标题不再重叠或孤页，续表重复完整DEVICE-03及状态；中文、两个SN、10/99ms和模拟声明与冻结结果1/3一致。见`report-qa/V3-1.png`至`V3-3.png`及`report-v3-content-check.json`。DOCX内容与OOXML已核验；因打包环境缺少LibreOffice，`render_docx.py`未能生成DOCX页面，未使用用户桌面Office，不能将DOCX分页目视写成通过。

随后从页面上传该V3 PDF作为人工V4，说明明确保留文件内部V3标识，仅验证模拟人工复核上传链路；V4为MANUAL/AVAILABLE，结果仍1/3，未伪称重新执行设备检测。异常结果3先标模拟误报并确认VALID，同R2筛选仅1条，统计为异常样本1、有效误报1。CSV导出1和XLSX导出2均AVAILABLE/version3/resultCount1，实际取得文件后主控独立读取，15列及唯一来源记录一致。优化入口仅创建R3草稿`2110630073920720897`，未发布或修改R2。之后改不关注、再确认跟踪，三个标注修订及原99ms结果均保留；完成标注后task2为TRACKING/版本23，唯一issue1为PENDING，责任人admin、期限2026-10-16 18:00。见`cut-reuse/report-v4-detail.json`、`feedback-export-{1,2}-snapshot.json`、`feedback-export-content-verification.json`、`annotation-stage-final-detail.json`及89截图。此阶段未处理待办或归档。

复用CUT既有登录来源对c02407、b00106各正常登录一次，菜单无巡检入口；直接规则路由落到平台404页，未返回业务资料。验收脚本首轮GET误用通用admin-api前缀所得404不计授权通过，原证据保留。两个账号按真实PLT票据契约访问V3文件均无票据，但返回“文件业务授权Provider不可用”。源码定位为SRV文件Owner抛出的明确FORBIDDEN被PLT统一故障包装；只在InspectionFilePolicy边界将该拒绝转为allowed=false，其他异常继续抛出，不改平台或权限。失败测试30项中27通过、3项按预期报FORBIDDEN，日志`file-policy-denial-red.log`；修复后30项全通过、无跳过，见`file-policy-denial-green.log`。完整受控构建2分51秒、22项保护与ValidateOnly通过，冻结清单`6ab04f1a-3aa5-4594-b8ab-805cffcf92f9`；按本任务持续授权部署后，宿主39560成功退出0，后端14780保持存活、local,dac/health200，运行包68ba0461与清单一致，锁释放，DAC/前端/网关不变。见`file-policy-denial-deployment-status.json`。

部署后两个CUT账号各正常登录一次、使用隔离浏览器上下文复验：任务详情页面明确显示“没有巡检任务查询权限”，规则页仍为平台404且不计授权证据；根路径的任务/规则四个GET均返回HTTP200、业务码403、无data。两个账号对精确V3文件版本的票据请求均为`1010001002 无权访问该业务文件范围`、无data，未获取文件内容。原脚本UI文字启发式字段为false，实际目视结论另记`visualReview`，不覆盖原观测；主控已复核两张任务页和规则页截图。见`cut-reuse/cut-actors-browser-denied-retry1.json`及同目录纯UI截图。当前项目、经理成员、设备、五个CUT归档任务与闭环、九个设备范围关联的原字段，均与模拟巡检前相同，见`cut-reuse-post-inspection-integrity.json`；这项只读比较不包含本次新增SRV事实。

无证据闭环申请被正确拒绝后，责任人从真实页面上传明确标记的模拟TXT处理证据，PLT完成上传返回`1010001002`。源码定位为上传完成先锁文件引用组，再锁精确引用；巡检策略把引用组伪键`reference-set`交给精确`issue:{id}:{uuid}`解析，合法责任人也被拒绝。修复已在组入口使用明确的内部标记，按当前同任务责任问题和处理权限进行前置校验，随后仍逐引用验证责任人、状态和冻结历史；普通责任人不能因此读取整组其他问题文件。见`processing-evidence-upload-response.json`、`processing-evidence-upload-request-summary.json`和92截图。15:33只读确认工件/版本/引用均无完成行，session仍INITIALIZED/v0、15:44:48到期，task2保持TRACKING/v23、issue1保持PENDING/v1、处理记录和证据均0，见`evidence-upload-failed-status.json`。本阶段未继续闭环或归档。

引用组修复保留PLT两层校验及所有具体引用保护，仅在巡检Owner内部区分组入口；采用既有单对象Mapper查询，未增加SQL或修改平台规则。失败测试43项中39通过、4个合法组上传用例按预期失败；最小修复后43项全部通过，无错误或跳过，日志为`evidence-namespace-red.log`、`evidence-namespace-green.log`。包含锁后重指派、他人问题、已冻结证据、伪造组键、无权限、归档/取消、租户和项目范围边界，主控已复核源码及日志。标准完整构建2分39秒、22项保护与ValidateOnly通过后，已按`79166286-b105-48c7-bbea-b143ffaca589`清单受控部署：宿主52300成功退出0，新后端48248持续存活，local,dac/health200，运行包2114ad0f与清单匹配，锁释放，其他服务不变，见`evidence-namespace-deployment-status.json`。旧上传session重试明确返回`1010001013 文件上传会话已失效`，见`cut-reuse/processing-evidence-retry-response.json`；UI随后重新初始化属于过期恢复分支，不是绕过幂等。该阶段的实际上传及待办闭环尚未通过，后续完成证据见下段。


修复部署后，真实UI重新初始化过期上传槽并完成处理证据上传，记录PROCESS后申请并确认闭环，issue1为CLOSED/版本4；随后申请并确认归档，task2为ARCHIVED/版本28/customerArchiveId1、currentReport4。CUS归档1保存21条来源引用，与SRV设备、执行、结果、四版报告、标注、问题、处理及证据ID逐类一致；深链刷新显示只读，归档后取得的V1 PDF与处理证据均与原字节一致，V1～3对象及精确引用保留。原CUT项目、经理成员、设备及五个任务/闭环、九个设备范围的六组字段仍与before一致。PLT15条引用实际仍为ACTIVE、archived_at为空，归档只读由Owner按任务ARCHIVED返回IMMUTABLE实施，未把逻辑冻结写成物理归档。见`cut-reuse/processing-save-response.json`、`closure-confirm-response.json`、`task2-archived-final-detail.json`、`customer-inspection-history-task2.json`、`task2-archived-file-verification.json`、`offline-archive-readonly-check.json`及`96-customer-task2-archive-history.png`、`97-archived-issue-evidence-readonly.png`。主控已复核接口、逐字节结果及截图，离线模拟闭环通过；办理者为admin/super_admin，在线和普通角色正向边界仍保留。

在线模拟任务3复用同一CUT项目、两设备和已发布R2，正常UI保存非空计划时间与背景、返回列表重开、二次保存及刷新后均保留；背景明确仅连接本机模拟SSH、不连接真实设备。准备页已提交正式DPtech判定表缺失反馈，未臆造正式规则。两设备独立预检通过，预检仅认证、不执行正式命令。端点不一致的exec10只登记PENDING意图，在正式下发前由服务端拒绝，随后通过UI取消，历史保留；PLT/DAC请求数均0，见`.run/build/inspection-deployment/online-endpoint-rejection-readonly.json`。前端已按当前协议、端点、用户名和凭证上下文过滤预检，并在变化时清除选择与临时密码；HMR后端口及用户名变化均使旧选项排除、选择清空、开始按钮禁用，见`task3-hmr-port-invalidation.json`、`task3-hmr-username-invalidation.json`和104截图。

正确上下文的exec11已实际连接本机模拟SSH并采集成功，fixture记录一次`show version`，平台状态SUCCEEDED；正常UI取得的平台原日志为1235字节、1个commandBlock、stdout148字节、stderr0、truncated=false，startedAt/completedAt均为真实非空时间。首次SRV刷新返回FILE_INVALID，尚未形成巡检结果，该过程时点任务3停在EXECUTING/版本15，没有重复采集；后续修复及归档结果见下述最终验收。原件和结构摘要见`cut-reuse/task3-exec11-platform-raw-log.json`、`task3-exec11-platform-raw-log-summary.json`及`task3-device02-collect-failed-refresh-detail.json`。

根因为PLT callback envelope与DAC normalize要求的schemaVersion=1.0.0标准输入不同。修复仅在SRV对采集包执行命令块7字段投影，保留命令index/status/truncation/exitCode并继续交DAC脱敏；原来源文件及digest保留。29+18+8共55项定向检查全部通过，见`.run/build/inspection-deployment/collection-envelope-green-retry1.log`。此前两次红/绿日志分别在并行资产依赖和PLT测试旧签名编译处停止，实际测试均0项，后由原Owner补齐；不能记录成RED16项失败或本专项修改了其他Owner源码。该轮结束时仍待标准冻结、部署及原exec11真实UI复验；后续已部署并复用原exec11完成刷新，未为修复重采，详见最终验收。

退役准备已对真实源只读备份186行并回读相同，备份ACL仅当前用户；已生成`retirement-batch-review-DO-NOT-EXECUTE.sql`供审阅。固定隔离库演练完成186行恢复、失败回滚、成功清理及18个触发器恢复，均通过；该准备阶段真实源未执行DDL/DML或删除脚本，待具体SQL批准；后续已获批准并执行，见最终退休结果，不公开备份原始内容。

## 在线与失败转离线最终验收及源码退出

采集包投影修复的55项检查通过后，已部署后端清单`dae3239e-99f3-4af7-80df-48123f938b11`（运行后端包摘要`c706d463*`），后端进程54040。真实UI对原exec11刷新成功，未重新采集该设备；第二设备exec13完成。Task3两设备结果4为PASS/10、5为ABNORMAL/99，问题2经处理、申请及确认成为CLOSED/v4，报告5/V1可用；Task3最终ARCHIVED/v33、CUS归档2，18条来源引用。Task4在未监听的22224端口真实预检失败，exec14/ONLINE尝试4的失败保留；显式切换OFFLINE尝试5，exec15生成结果6 PASS/10、报告6/V1，无异常待办，最终ARCHIVED/v16、CUS归档3，5条来源引用。Task2此前ARCHIVED/v28/CUS1的21条来源证据仍适用。

证据为`.run/build/inspection-browser-acceptance/cut-reuse/online-and-fallback-final-summary.json`、`task3-archived-final-detail.json`和`task4-archived-final-detail.json`。本机SSH fixture共执行两条`show version`，进程17088已正常停止且密码清除，见`ssh-fixture-stopped.json`；这些是明确模拟输入，不是真实生产设备验收。独立只读复核`final-acceptance-readonly.json`确认16个报告文件均匹配PLT精确版本，CUS来源一致，CUT原项目、经理、设备、任务、闭环及设备范围六组字段全保持，当前CUT项目PM-10未闭环问题和非终态巡检任务均为0。

源码退出已清理旧54个后端、8个前端专用文件、共享详情旧面板及8个旧字典常量，维保和服务等级常量保留。姓名字段和显示已随最终冻结包部署，最终只读复核已完成，巡检新旧入口、CUS及CUT通过；服务等级经后续V479补齐已恢复，复验见续记。29项Java/MySQL定向检查通过（问题15、查询7、治理6、MySQL1）；前端3新文件Prettier、5文件ESLint、3个SFC编译和本地imports解析通过，HEAD旧共享文件对照ESLint亦通过。巡检目录没有既有Vitest用例，不能计为测试通过；历史迁移保护脚本2项通过。证据见`.run/build/inspection-retirement-inventory/frontend-final-validation.*`，未执行全量Vite构建或全局vue-tsc。

16项菜单退休先在隔离事务验证首次16、重放0、维保4项不变及回滚fixture0，随后获批执行；详见下述最终结果。普通角色正向未验，模拟产品适配UI已验收，正式权威产品、DPtech规则包和UMC后置边界保留。

### 模拟适配、报告复核和退休执行结果

MAIN已全文及截图复核`cut-reuse/product-adaptation-final-summary.json`及发布详情、A/CUT筛选：仅适配A的新规则970485013、修订2110657332224290817为PUBLISHED/v3，A筛出2条（R2及新规则），CUT筛出1条（仅R2）。R2全对象保持，R1仍停用，10条原示例仍DRAFT，修订总数15；初次401及正常刷新认证后创建唯一草稿的过程保留。办理者admin/super_admin，属于模拟验收，不是正式产品或十类生产规则验证。MAIN另实际渲染并看完`task4-report-V1-review-{1,2}.png`：PDF33021字节、2页，内容完整无重叠；长SN跨页连续显示，未宣称每页重复完整SN。

退休迁移包位于`.run/build/inspection-retirement-inventory/migration-package/`：407份旧SQL按已执行checksum保留，新增477.8、477.9、478.4，info精确3项Pending；5项已知历史缺失沿用Compose既有`*:missing`，不扩大忽略。首次独立validate仅因3项预期Pending失败，随后仅含已执行原件的`applied-history`视图validate成功412项；获批后的实际migrate使用完整包默认validate，415项通过，未关闭校验。outOfOrder仅用于当前三脚本批次；五类PLT交付目录type19～23已通过正式管理API初始化，.8/.9执行后现有五类目录行完整未变，.4已退休16项菜单。仅477.8/.9/478.4三项成功，执行退出0，见`migration-package/execution-result.json`及其迁移日志。见该目录info/validate日志及inputs.json。

执行前只读复核`cleanup-before-approval-readonly.json`确认源186行逐行等于备份，18个保护触发器、DDL及0外键不变。已批准预览的删除关联影响为：项目1001的客户更正引用守卫计数由旧3+新1共4变为1，1002～1010各由3变为0；现行新任务继续计入历史，不因清理旧表而排除。用户明确回复“批准按预览执行并完成部署验收”后，真实186行清理提交成功且18个保护触发器恢复，见`retirement-run-20261015T091048223607Z.json`。最终只读`retirement-final-readonly-verification.json`确认六表均0、18个定义原样、4项维保及角色/字典不变，新SRV、CUS、报告引用、PM-10逐字段不变，CUT六组一致。

最终完整构建2分20秒成功、22项保护检查通过、ValidateOnly退出0，冻结清单`79cf3215-5869-4498-8bb5-5b95f033cfa1`；54个旧源码对应jar条目为0，8个新/共享保留条目均存在。首轮虽构建退出0，但构建中并行源码变化使冻结被拒（freezeExit1、无manifest、未运行ValidateOnly），原失败证据`retirement-final-freeze-first-status.json`保留。重试证据为`.run/build/inspection-deployment/retirement-final-freeze-status.json`、`retirement-final-freeze-retry1.log`和`retirement-final-validate.log`。获批后冻结包已受控部署：宿主52560 SUCCEEDED/exit0并退出，新后端42252固定local,dac、监听59191、health200，运行包b48d摘要与清单匹配，22项保护通过、锁释放；DAC8132、前端50732、网关38068保持。见`retirement-final-deployment-status.json`。最终只读复核已完成，巡检新旧入口、CUS及CUT通过；服务等级经后续V479补齐已恢复，复验见续记，实际浏览器证据见下段。

最终退休只读浏览器复核结果：六个旧GET返回HTTP200的JSON业务码404，新任务详情正向基线code0；四个旧页面显示平台404页，SPA导航HTTP仍为200。新workbench/tasks/rules查询均code0，各自菜单高亮且无loading。Task2/3/4仍ARCHIVED/v28、v33、v16，姓名JSON已复核。证据为`cut-reuse/final-retirement-six-old-api-get.json`、`final-retirement-four-old-pages.json`和`final-retirement-new-pages-summary.json`。CUS列表正常UI查询code0，共3条，对应Task2/3/4版本28/33/16；Task3来源深链刷新仍ARCHIVED/v33。客户列表正常；17:21当时服务等级单独500的历史保留，后续恢复见续记。最终证据为`.run/build/inspection-browser-acceptance/cut-reuse/final-retirement-browser-summary.json`、`final-retirement-customer-three-history.json`及`final-retirement-task3-final-stop.png`；MAIN已全文复核并目视最终Task3截图。

17:21当时共享入口复核发现一个真实未通过项：服务等级列表`GET /admin-api/pms/service-level/page`返回HTTP200/业务码500，当时日志`.run/logs/satisfaction-manual-backend.log:4182`为`Unknown column 'erp_customer_code'`，调用链为CustomerQueryApiImpl→CustomerServiceLevelController:94。只读`parallel-crm-schema-gap-readonly.json`确认Flyway最大installed_rank为423、最新478.4，客户表尚无该列；该列来自同工作树并行CRM CustomerMasterDO改动，对应V479尚未应用，不在本次批准477.8/.9/478.4三脚本中。本次仅删除旧六表数据、未修改cus_customer_master；没有擅自应用V479或回滚并行源码。该时点共享入口未通过，需CRM任务完成其schema部署；后续恢复见续记，不抹去该失败。

旧project-detail的native项目页头“项目不存在”属于既有旧模型路径限制；旧巡检tab移除已验证，共享CUT子页code0并显示7条已归档记录。工作台状态首次采样瞬时opacity0，过渡结束后4标签均opacity1、宽49px、高20px、稳定可见，不作为缺陷。CUS列表及Task3来源深链刷新已通过最终复核；17:21的服务等级单独失败按上段保留，后续恢复见续记。

## 部署与仍待闭环事项

用户已明确批准首轮七项迁移、59191后端和48182 DAC部署及真实浏览器验收。后端原进程当时已退出，受控入口按批准的离线启动路径启动PID43924，固定 `local,dac`；DAC核对原进程身份后停止PID29552，再以冻结清单启动PID31480。59192回调网关保留原进程。前端19191当时未运行，通过 `scripts/worktree-frontend.ps1` 启动后开展验收。首轮执行会话因子进程句柄保留而无外层退出码，不能填造为0；成功依据为受控入口、就绪检测及实际接口/浏览器结果。新的项目树版本修复已重新冻结后端，DAC源码和包未变，后述恢复继续使用首轮DAC清单。

后续继续规则保存验收前，发现59191/48182及上述两个进程均已退出，前端19191和网关59192仍在运行。本轮未执行停止操作；现有日志未捕获退出原因或退出码，因此不能认定原因已查明。命令会话/进程树生命周期回收只是推断。证据见 `.run/build/inspection-deployment/process-lifetime-readonly-check.json`。

新增 `.run/build/inspection-deployment/launch-controlled-service.ps1` 用隐藏的独立Windows宿主调用原受控入口，继续保留原冻结校验、独占锁、固定profile和逐次批准要求，不直接启动Java或复制运行包。选择 `Win32_Process.Create` 的依据是其子进程不继承调用方Job关联，见[微软Job Objects说明](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)。启动器/宿主语法及缺少批准时的拒绝检查通过；不涉及服务的独立进程探针完成。

用户再次明确批准恢复两项服务后，通过上述宿主依次调用原受控入口：后端59191/PID30164/local,dac，DAC48182/PID8132/npdms均就绪。两次创建调用退出0、宿主状态SUCCEEDED/exit0；在宿主38944、23232均退出后独立复查，两个Java进程仍存活，实际包路径/profile/监听PID匹配，锁已释放。见 `.run/build/inspection-deployment/restoration-status.json` 及 `controlled-launches/`。网关/前端未重启，没有重复迁移或新建解析任务；这些存活证据不等于确认了首轮退出原因。

用户批准批量查询修复部署后，本次仅重启59191。冻结清单`c1e06aa4-0664-4785-9613-5c1bb72571ba`经原受控入口部署，22项保护检查通过；独立宿主47316返回SUCCEEDED/exit0并退出，后端新PID48240继续存活，监听59191，固定`local,dac`，运行包与清单一致，health返回200，工作树锁已释放。DAC8132、前端44800和网关1324保持原进程，未重复迁移。证据为`.run/build/inspection-deployment/query-backend-deployment-status.json`及`controlled-launches/20261015T041309976Z-36a90b1852df4bc4b7d4d994cd2ccd9b/`。

1. 正式CRM/权威产品来源或既有导入文件仍待提供；见 [Q-INS-AUTHORITATIVE-PRODUCT-DATA-20261015](../decisions/open-questions.md#q-ins-authoritative-product-data-20261015--巡检适配示例与验收的权威产品输入)。用户授权的三个模拟类型和四条设备映射已就绪，可继续模拟业务验收；模拟适配/不适配已补充真实UI证据；不得据此宣称正式产品清单或十类生产规则包齐备。
2. 按用户授权只读查找现有账号：有真实项目指派的项目经理候选包括l05678、liujiazhen_supp、y06496；服务经理候选z01010在项目992203060189～198有有效指派。但新巡检任务/问题权限尚无角色菜单授权，暂无可直接开展普通角色验收的合格账号。super_admin单列，不能作为普通工程师或服务经理证据。定义权限不等于配置实际用户，不能把旧增删改权限自动扩展成执行、确认闭环或归档权。证据见 `.run/build/inspection-browser-acceptance/role-candidates.json`；其中13条是菜单/按钮记录，查询权限在菜单和按钮上重复，不能把记录数当独立权限数，且不覆盖报告、反馈及平台文件全部权限。未读取秘密或联系方式，未修改授权。
   用户随后明确要求“继续复用割接项目的负责人和验收用户”。割接已验证身份为admin办理发起人/服务经理、c02407办理二线、b00106办理研发；本次据此核对当前授权，不执行此前提出的fast/fcus临时角色和主服务经理替换方案。
   当前只读核实三者均启用：admin具有super_admin及SERVICE_MANAGER/PROJECT_MANAGER角色，且是目标项目有效主责负责人；c02407仅probSupporter，b00106仅probRD，均无巡检权限或目标项目成员关系，只有基础文件查询/预览/下载权限。证据为`cut-reuse/cut-actors-current-readonly.json`；文件基础权限不等于巡检对象访问资格。管理员正向办理不得标记为普通角色正向验收，另两账号的实际登录、查询拒绝及文件票据拒绝已通过上述复验。未改变账号权限或项目指派。
3. 离线模拟任务2已完成创建、保存重开、失败恢复、四版报告及历史保留、标注/误报、PROCESS处理证据、问题闭环、申请/确认归档和客户历史，当前ARCHIVED/版本28/customerArchiveId1。无证据闭环曾被正确拒绝，旧上传槽过期及修复历史均保留；详见上述UI、文件逐字节及独立只读交叉证据。在线及在线失败转离线模拟闭环随后已完成，管理员正向办理不等于普通角色正向验收，也不代表正式生产资料和规则组合齐备。
4. 用户批准预览后已完成旧54后端/8前端退出、六表186行真实清理、16项菜单退休、477.8/.9/478.4迁移和79cf冻结包部署；独立只读确认六表0、18触发器定义与新业务/CUT/维保保护均保持，最终只读复核已完成，巡检新旧入口、CUS及CUT通过；服务等级经后续V479补齐已恢复，复验见续记。备份及历史失败证据继续保留，未改现行规则、审计或已执行迁移原件。
5. UMC真实对接及正式DPtech判定规则包按批准范围后置，不能把平台基础报告或配置示例表述为其完成。

尚未执行Git提交或推送；保留工作树中既有CUT并行修改和用户临时文件。

## 服务等级后续复验续记

后续用户要求继续处理服务等级500时，只读核对发现并行会话已于20:44:12应用V479（installed_rank425、checksum1710823682、success1），原SQL与历史checksum一致；当前24306/npdms_domain_test的erp_customer_code已存在，为nullable varchar(50)，最新V480/rank426。证据为`.run/build/inspection-retirement-inventory/crm-service-level-schema-recheck.json`。该迁移不是本会话执行，本轮没有迁移、源码修改或重启。真实浏览器打开、刷新、第2页、标准筛选第1页、清空筛选及退出首页再重开均HTTP200/code0，总数24→标准8→24；原页1/页2各10条且ID无交集。首页10条客户名称齐全，原500恢复复验通过。证据为`.run/build/inspection-browser-acceptance/cut-reuse/crm-service-level-recheck-summary.json`；本轮0业务写、0迁移、0服务操作。部分历史行customerId1/2/3无名称，只读证据`.run/build/inspection-retirement-inventory/crm-service-level-missing-customers-readonly.json`确认cus_customer_master确实无这3个ID，不臆造客户或映射。旧GENERAL/IMPORTANT/STRATEGIC等级标签空白仅记录观察，未扩大本次500处理范围；不能据此宣称24条姓名均齐全或整个CRM验收通过。

## 旧维保与空父目录后续清理续记

用户后续明确授权清理旧父目录、残留页面及旧维保30行，保留新业务；这扩展了前述“旧维保不在删除范围”的历史边界。pms_srv_maintenance_retired的tenant1共30行已提交删除、当前0行，3个原保护触发器定义一致。此前ACL私有备份回读一致，隔离恢复、注入失败回滚、正常清理及3个保护恢复均通过。源码检查无旧维保页/API实现，旧巡检页面此前已退出；本轮未删新页面、未改业务代码或重启。

新增`sql/migrations/V480_1__retire_empty_legacy_service_directory.sql`通过Compose Flyway仅应用480.1，退出0，默认validate419项通过。旧父目录19265已停用、隐藏并逻辑删除，其唯一role_menu逻辑删除，子菜单原样；28张新巡检、CUS历史及客户服务等级业务表全行指纹前后一致。菜单隔离验证使用原隔离schema的两张独立测试副本，仅替换表名，覆盖active child阻断、首次1+1、重放0+0及回滚fixture0；本轮自建空测试表随后删除。原隔离菜单已有目标ID，首次保护检查停止且未修改原种子，没有覆盖种子。

证据位于`.run/build/maintenance-retirement/`：`retirement-batch-preview.json`、`retirement-batch-rehearsal.json`、`menu-isolated-validation.json`、`final-readonly-verification.json`及`migration-package/execution-result.json`。该轮最终浏览器复核通过：刷新权限后父目录19265及“巡检维保”消失，旧`/pms/service/srv-maintenance`显示平台404；新巡检工作台code0，Task3仍ARCHIVED/v33，服务级别code0/total24。证据为`.run/build/inspection-browser-acceptance/cut-reuse/maintenance-retirement-final-summary.json`，MAIN已读取摘要并目视截图；普通角色正向、正式产品/DPtech规则包与UMC边界仍保留，不代表整个新业务全面验收。
