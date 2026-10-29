# 现有离线巡检集成UMC：真实浏览器验收

> 2026-10-28后续验收说明：本文保留当时“报告页闭环”的历史证据，不能替代后来明确的“巡检判定结果全部展示”标准。该缺口已修复并使用需求方提供的插槽、双设备日志重新完成真实浏览器上传、获取结果及下载，当前结论见[执行结果双案例验收](../execution-result-cases/report.md)。

日期：2026-10-28。结论：原离线巡检入口的新日志上传、UMC出具DOC、原报告表结果入库、本地结果展示与报告下载闭环通过。测试使用合成日志及QA设备，不作为实际设备健康结论，也不代表整个巡检任务生命周期完成。

## 实际操作与结果

需求方明确同意改用独立Chrome测试会话后，通过Playwright驱动Chrome测试浏览器151.0.7922.34，使用正常登录页面进入现有任务2（INS-2115106586164690944）。没有使用API上传替代浏览器操作。

在原“离线巡检执行”页面选择qa-umc-plaintext.txt（102223字节），填写来源IP192.0.2.31，点击“上传日志并生成报告”。浏览器实际请求为POST /api/v1/pms/inspection-tasks/2/offline-logs，HTTP200、code=0、任务版本4。没有独立UMC上传卡片或上传请求。

| 核验项 | 实际结果 |
|---|---|
| 原离线执行 | execution 1，当前attempt 1，设备996610240845740001，SN QA-84574-SHIPPED-20261024-01 |
| 输入文件复用 | 报告source_context_json.executionId=1，日志artifact 2115267220692029441 / v1，与原执行记录的LOG引用及SHA完全一致，没有另存一份UMC输入 |
| 新UMC产物 | 任务77、报告692；原报告表本地id=2 / V2 / source=UMC / AVAILABLE |
| 结果入库 | srv_inspection_report_revision.result_json，1台设备、2条规则，结果摘要fabcd29c08df64362f2bdd2cfa596c25c4d874ecd3f6aa0278c4c39820535a54；不是业务JSON文件，也未复制UMC规则/设备模型 |
| DOC归档 | 原srv_inspection_report_file关联PLT artifact 2115267234021527553 / v1；AVAILABLE；1,300,483字节 |
| DOC摘要 | bc4db1c29fa72e933e85417afadac279e4b285fd369bdbedf38fd9c1291abfb6 |
| 结构化展示 | 原报告V2中查看设备SN/IP/型号/软件版本、2条规则及展开后的指标；保留UMC异常/正常原结论，不映射为平台冻结检测判定 |
| 刷新后读取 | 浏览器重新加载原任务页、进入原报告环节，GET /inspection-reports/2/result成功展示已保存结果；完成重试返回完全相同本地数据 |
| 浏览器实际下载 | 从原V2的“下载此版本文件”产生下载事件，access-tickets及本实例receipt内容均200；下载字节长度、SHA与PLT入库记录相同 |
| 实际格式 | 完整XML解析成功，根为Word2003 wordml的wordDocument；保留UMC DOC原件，不伪称DOCX或二进制DOC |

刷新及结果查询只使用本地NPDMS/PLT入口。已完成报告的本地读取/重试不调用UMC由实现与既有失败/恢复测试约束；浏览器网络记录本身只证明浏览器访问路径，不用它单独宣称已观察服务端全部外联。新报告始终绑定UMC77/692，没有生成额外报告版本。

数据库只读复核中原执行仍为PENDING，任务仍EXECUTING，currentReportId为空，原冻结检测/平台报告结果关联均未伪造。UMC报告保存不代替原解析判定和正式业务完成规则。

## 本轮发现与修复

真实页面上传后发现旧V1默认展开：原组件按未排序reports[0]选择，且未监听新报告到达。已改为按页面既有排序选择默认版本，并监听首选报告ID；无当前正式报告时展开最新V2，有当前报告时仍优先当前报告。完整前端构建、定向类型检查和ESLint通过；刷新后的浏览器直接打开V2、查询本地id2并显示结果，已复验。

之前的下载票据跨实例404已修复；本次真实浏览器从59191取得新DOC，证明修复实际生效。此前104项Java定向测试通过、1项真实ClamAV环境测试跳过，原执行组件4项运行测试通过；本轮仅改前端默认展开，不重复执行未受影响的后端测试。

## 运行与授权边界

当前后端使用最新部署更新中的冻结代码，清单1dd6c929-29cd-43e0-b9f2-89741da7f0f0，SHA-256 8e6e16d2c6cf5fa6c56d86557b800f8a5f1ba8a2805fd7ea2c30607a3601d523；13组接入关键类与验证构建逐字节一致，见runtime-code-inclusion.json。Codex重启导致子服务退出后，按已有授权恢复后端59191、前端19191、DAC48182及既有网关59192，保留127.0.0.1上的其他DAC。

178库只有此前明确批准的V520原表三个可空字段和UMC来源CHECK变更，没有新建表、运行Flyway或其他迁移。报告V1历史保留。没有Git提交或推送。

内置浏览器constraints故障在桌面应用重启后仍存在，未通过降低网络策略绕过。后续切换独立Chrome有需求方明确同意，完整验收属于该真实测试浏览器；不得描述成内置浏览器已恢复。排查见browser-tool-diagnosis.md。

## 证据

- integrated-browser-network.json：脱敏后的实际上传、结果查询、文件票据及下载响应，仅记录方法、路径和状态，不记录凭据、Token或下载票据。
- database-result-proof.json：原执行、原日志引用、原报告结果与PLT附件的只读复核。
- platform-verification.json：本地结果及已完成重试、新报告下载核验，reportId=2。
- browser-downloaded-integrated-report.doc：本次浏览器实际下载的新DOC。
- integrated-browser-before-upload.png、integrated-browser-reloaded-new-result.png、integrated-browser-structured-metrics.png：原入口、刷新后V2及展开结构化结果。
- integrated-browser-state.txt：最终可见业务结果文本。

此前UMC76/691及报告V1属于独立卡片阶段的历史证据，不用于本次闭环通过结论。
