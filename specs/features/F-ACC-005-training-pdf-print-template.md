# F-ACC-005 现场培训 PDF 与独立打印模板

- Requirement：ACC-01；补充授权：2026-09-20用户要求下载导出PDF，管理员统一配置，培训记录选择已发布的独立打印模板。
- 范围：现有培训记录的打印呈现与下载；不修改客户确认模板、生命周期、外发契约或已有归档文件。
- 复用审计：培训已有HTML生成与签字归档，保留；BPM打印模板绑定审批流程，不直接复用；PLT动态表单已有模板、不可变发布修订、启停、权限、幂等和乐观锁，通过公开API复用，无PLT业务表直读写。
- 用户追加确认：复用FormCreate与动态表单，加载培训记录内容和签字图片，前端预览生成PDF；打印表单去掉Demo字样。
- 管理员在培训打印模板入口复用PLT动态表单设计器，配置标题、字段标签、列宽与排列、评价及签字布局。新建时提供可编辑的培训表单初始规则，保存后通过既有修订发布、启用；不新增专用布局编辑器。
- 新模板分类TRAINING_PRINT_FORM，原TRAINING_PRINT历史版式兼容保留。客户确认模板仍独立；打印表单核心绑定name、content、trainingTime、signatureImageDataUrl必须保留，其余字段及自定义评价按同名字段加载。
- 培训保存时选取当前发布修订，服务端重验可用性并保存打印模板ID、修订ID及完整FormCreate配置和规则快照（核心业务绑定校验）。普通保存保留原选择的快照；草稿更换模板时重新捕获。已外发/已确认记录不得改绑打印模板。
- 下载入口统一进入FormCreate预览。未绑定确认样张版式的历史记录默认使用管理员已发布的TRAINING_PRINT_APPROVED模板；不可读取该模板时使用同结构标准初始规则。已绑定APPROVED_TABLE版式的记录优先保留自身快照；预览可选择其他已发布模板或记录原生FormCreate历史快照，仅改变本次导出，不回写记录。停用模板不影响已有快照打印。
- 原服务端PDF接口保持兼容，但不再作为页面默认下载路径；FormCreate快照由管理端get接口提供，前端使用既有动态表单解码器加载真实记录与签字图片，预览后以html2pdf生成A4 PDF。管理端高信任表单配置沿用PLT现有执行边界，不下发公开确认端。生成前等待字体及图片加载，失败允许重试。模板停用和新修订不改变已绑定快照。
- PDF基于当前记录数据和绑定版式快照生成，不宣称字节级归档原件；不覆盖原HTML、归档交付件或文件元数据。
- Code：engineering/service/training/TrainingPrint*、TrainingPdfRenderer；controller/admin/training/TrainingPdfController；training/TrainingPrintTemplates.vue、TrainingPrintPreview.vue、trainingPrintForm.ts及index.vue；V319。
- Test：TrainingPdfRendererTest、TrainingPrintServiceTest及现场培训原有失败测试。浏览器验收、迁移验证和运行状态在tasks/features/F-ACC-005.md记录；未完成前不标记Feature Done。

- 确认样张通过原生fcTable布局实现：基本信息三列、整行培训内容、客户评价合并单元格、综合意见、签字图片与日期；模板配置仍由既有FormCreate设计器维护。样张文件不是生产渲染源。
