# F-ACC-005 现场培训PDF与独立打印模板

- Requirement：ACC-01；规格：[F-ACC-005](../../specs/features/F-ACC-005-training-pdf-print-template.md)。
- Implementation Status：IN_PROGRESS；未声明Feature Done。
- 当前计划：复用审计及范围收敛 → 独立模板配置、保存快照与PDF下载 → 迁移、定向失败测试和PDF版式检查 → 可用环境下真实浏览器验收。
- 本次在用户明确允许的现场培训未提交增量上继续，不覆盖其他模块改动；不修改PLT生产实现或既有归档。

## 2026-09-20 实施与验证

- 已实现独立打印模板配置页面，复用PLT模板草稿、修订、发布与启停API；培训保存时捕获发布版式，下载走独立PDF接口。
- 验证通过：TrainingPdfRendererTest 3项、TrainingPrintServiceTest 3项、TrainingServiceTest 6项、TrainingConfirmationFormsTest 4项；前端vue-tsc检查通过，两个相关Vue组件编译通过。
- PDF样张已生成并检查中文正文、长内容分页、页眉页脚和签字图片。证据位于`pms-module-engineering/target/training-pdf-evidence/`（生成文件不提交）。
- 用户指定运行环境为19191/59191及Compose项目npdms-domain-test；已核对分支配置与容器，实际Schema为npdms_domain_test，MySQL/Redis为24306/24379。V319已在此隔离库执行成功（318→319，一项迁移）；未操作npdms开发库。
- 浏览器实际登录并打开现场培训列表；配置对话框完整操作链尚未验收。第一次打包因运行中的应用JAR被占用而失败；后续限定59191的进程核对/重启尝试及命令执行通道超时，未取得重打包、重启和端到端PDF下载成功证据。
- 上述首次运行阻塞已在下述FormCreate调整中恢复并完成相关浏览器验证。

## 2026-09-20 FormCreate配置与实际PDF导出

- 用户明确要求复用FormCreate/动态表单；已替换专用打印参数编辑器，培训入口直接复用DynamicFormTemplateEditor及原发布、启停、版本API。新模板分类TRAINING_PRINT_FORM，初始内容为可编辑原生规则；无Demo标题。
- 培训记录保存完整已发布配置快照，管理端预览加载记录、同名补充评价和签字PNG；核心数据覆盖同名补充值。原版式与已绑定历史保留，公开客户接口不返回管理端配置。
- 下载使用实际FormCreate渲染结果，生成前等待字体/图片；针对canvas文本域换行截断、确认时间戳及选中项对比度完成修正。PDF为前端栅格化呈现，不覆盖归档文件。
- 环境：19191/59191，npdms-domain-test（npdms_domain_test），未执行其他数据库迁移。已在该隔离环境通过管理员页面创建、设计、保存、发布、启用“现场培训标准表单”；模板2101693184017952769。初始化内容通过管理员正常配置路径保存，不改写V319已发布模板。
- 浏览器验收：真实登录 → 培训页 → 设计器保存并发布启用 → 新建培训记录选择模板 → 保存 → 重新读取预览 → 下载PDF。记录13的评价和明确标识为验收的签字图片通过公开确认API准备，公开签字UI不作为本次新增验收项。
- 记录13（PJT2026000007-PX-009）一页A4含中文、四行培训内容、三项评价、确认时间及签字图片；记录14（PJT2026000007-PX-010）80行长内容导出3页，检查中间页结束标记及最后页评价/签字区域。文件：output/pdf/training-record.pdf、training-record-rendered.png、training-record-long.pdf；浏览器脚本和过程截图在.run/training-form-*（本地证据不提交）。
- 历史保护：通过PLT API新建并发布修订2、停用模板，重新读取记录13，printRevisionId及printLayoutSnapshot完全不变；完成后恢复启用。未改写旧发布修订。
- 验证：后端17项定向测试及yudao-server打包通过；前端trainingPrintForm.spec.ts 3项通过，覆盖核心字段优先、补充字段/PNG保留、未签字空值、时间格式。vue-tsc本次培训文件无报错；全量检查仍有configuration/installation/joint-test既有类型错误，未越界修复。自审检查模板权限复用、公开响应隔离、快照保留及导出失败重试。
- 当前交付：本次FormCreate配置→记录预览→PDF下载闭环已验收；保留Feature IN_PROGRESS，不把本次局部验收等同于整个ACC Feature Done。未提交或推送。

## 2026-09-21 确认样张接入实际下载

- 修正之前只更新独立PDF样张、实际下载未切换的遗漏。trainingPrintForm.ts初始规则替换为FormCreate原生fcTable表格；TrainingPrintPreview支持确认样张、已发布模板及记录原生历史快照。index.vue所有记录下载统一进入预览，不再让旧记录自动走后端旧版PDF。
- 通过管理员真实页面创建、设计保存、发布并启用“现场培训确认样张”（TRAINING_PRINT_APPROVED，模板2101708257444720641）；通过既有修订API发布修订2（2101708768902344705）调整签字/日期布局。没有改写旧发布修订或培训记录快照。
- 实际页面依据记录回填评价、意见、签字PNG及确认日期；导出克隆修复文本域换行/左对齐和签字图片宽高比，使用标准表格、合并评价单元格及签字日期区域。已有APPROVED_TABLE快照优先沿用，其他旧记录默认确认样张，可在预览选择历史原生版式。
- 浏览器使用用户提供的10.210.0.11:19191项目详情地址。已验证旧记录11、原普通FormCreate记录13均下载确认样张表格；长内容记录14导出分页。成品在output/pdf/training-approved-legacy.pdf、training-approved.pdf、training-approved-long.pdf；此前training-record-sample.pdf仅为参考样张，不是本次功能验收证据。
- 定向数据回填测试3项通过；Vue编译通过；vue-tsc培训代码无报错，全量仍受configuration/installation/joint-test既有错误阻挡。未改后端业务/数据库Schema/公开确认接口，未提交推送。

- 最终实测：确认样张与记录历史版式可切换再下载（HISTORY_SWITCH_PRESERVED）；旧记录11和记录13实际下载成功。确认样张一页A4；手签图片使用原PNG，未以姓名文本代替。后端运行恢复至59191指定隔离库，未操作其他库。

## 2026-09-21 作废培训记录禁止下载 PDF

- 用户追加要求（ACC-01 / F-ACC-005）：作废单据不允许下载 PDF。列表及详情禁用下载按钮，打开预览和实际导出前读取最新状态；作废后关闭已有预览并提示。后端兼容 PDF 接口在解析版式前拒绝 VOID 状态，保持查询权限、历史快照及作废状态机不变，无数据库变更。
- 真实浏览器在 19191 / 59191 指定隔离环境使用专用验收记录 17：打开预览后通过既有 API 作废，下载被阻止且未产生 download 事件；刷新列表及打开详情，下载按钮均禁用。脚本 .run/training-void-browser.py。
- TrainingPdfControllerTest 与 TrainingPdfRendererTest 共 4 项通过，覆盖旧版式和原生版式的作废拦截及原有正常渲染；本次修改文件 diff --check 通过。后端新拦截尚未重打包部署到共享 59191 进程，未中断其他任务正在使用的后端；当前前端限制已生效。未提交或推送。
