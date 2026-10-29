# UMC PDF 诊断与平台渲染复用验证

日期：2026-10-27。延续已授权UMC测试；本轮新增任务73、报告688，旧任务和报告保留。

## PDF 诊断

已有报告685的DOC可用、PDF不可用。增加apiversion=2后结论不变。新任务73设置doc=false、pdf=true，输入为已验证的提示符日志；报告688完成两项检测、任务恢复待启动，超过4分钟仍为doc=false/pdf=false，下载PDF返回500“文件不存在”。

服务器任务页面的生成配置只有DOC开关；保留读取pdf配置及列表下载字段。只读日志关联到本次请求的 `InspectionController.report_download(InspectionController.java:1085)`，异常为文件不存在；未取得PDF转换器的失败日志。未调整日志等级、运行配置、时钟或安装组件。

因此已确认的是UMC未产出文件、下载端据此失败；未确认内部生成分支为何未产出。不能据此断言缺少LibreOffice、许可证失效或某个转换器崩溃。进一步定位需要对应UMC版本的生成代码及任务73的生成阶段日志。

只保存相关异常摘录和请求摘要，未保存完整调试日志。会话已正常注销，随后任务列表401。详见 `evidence.json`。

## 规则核对

`umc-rule-definitions.json`保存本轮只读取得的规则1、85配置。规则1的指标定义为272～278；规则85为180，均不同于每次运行生成的指标结果行ID。

规则1包含型号、SN、运行时间、版本及三种特征库指标，不能直接映射成一条平台数值检测项。规则85的配置命令是 `show running-config | include linkup-delay`，外部基线180，而报告中的源命令为 `show run`、valuelist为空；其result=false不能证明平台冻结命令及阈值检查通过。

## 渲染复用

探针：`.run/umc-pdf-diagnosis/run-probe.ps1`。使用固定JDK25和工作树独占锁，编译当前Renderer/Validator源码到隔离目录；依赖从本工作树已有后端包提取，仅复用依赖库，不采用该包中的业务实现。输入为真实报告685的脱敏结构化数据，未接NPDMS数据库、PLT或状态机。

- PDF：35,537字节，2页，嵌入中文字体，PDFBox及原文件校验器通过；逐页PNG已查看。
- DOCX：3,576字节，POI及原文件校验器通过；原生Word加打包render_docx管线渲染2页，均已查看。原模板列宽较窄、出现多字拆行，版式未达到正式交付标准，需要增强新模板，不作为正式DOCX交付。
- XML：2,494字节，安全解析及原文件校验器通过；总数2、UMC来源正常1/异常1，设备和两项规则内容完整。

样张不代表已建立平台规则绑定。严重级别刻意保留“UMC级别1”，没有伪造平台字典映射，因此其严重级别分桶不是正式业务统计。主报告应使用平台冻结结果和严重级别，仅将已映射UMC内容作为补充。

平台直接生成PDF不依赖Word/LibreOffice；Word仅用于本机DOCX视觉检查。本机未装LibreOffice导致打包DOCX渲染器首次调用失败，定位后复用已有原生Word导出适配接回同一PNG管线，未修改系统配置。隔离classpath没有日志实现的提示不代表生产日志依赖缺失。

输出和源码锚见上级 `platform-renderer-probe/renderer-probe.json`、`source-evidence.json`。原生产Renderer、Validator未修改。

## 推荐实施

采用“UMC结构化分析＋平台生成PDF/DOCX/XML＋平台版本归档”，不等待UMC原生PDF。平台判定与统计沿用冻结结果；建立版本化指标绑定和独立来源文件关联，WordML可选保留。

具体映射、最小数据关系、公共扩展点、文件授权和分步验收见 `docs/superpowers/plans/2026-10-27-umc-structured-report-integration.md`。
