# 原离线巡检：执行结果回传与双案例浏览器验收

> 后续整改说明：需求方实际使用发现正式流程不能继续，已在原任务3复现complete-execution返回409。本文仅证明当时的结果回传、展示及下载范围，不能作为完整流程通过结论。界面整改及正式判定来源待裁决情况见[流程缺口记录](../workflow-adaptation/report.md)。

日期：2026-10-28。本轮按需求方最新标准验收：在原巡检执行页面上传所给案例、点击“获取执行结果”，UMC实际返回的全部规则、指标和值在“巡检判定结果”展示；DOC和结果已保存，刷新后仍可查看、下载。结论：该范围通过。

此前仅在报告页展示结构化结果，没有进入原执行页判定结果，不能满足本轮标准。本轮补齐原任务详情的执行结果投影、原获取结果动作及原判定结果表，不新建独立巡检页面或第二份结果存储。

## 实际案例与结果

需求方提供的两个文件只在内存替换设备主序列号，通过真实Chrome测试浏览器151.0.7922.34 / Playwright在原页面选择文件并上传，未通过API替代日志上传。原文件哈希再次核对未变，未持久化原始日志副本。仅替换指定主序列号，槽位/板卡序列号保留。来源IP由验收明确填写为192.0.2.31、192.0.2.32。

| 案例 | 原页面/执行 | 本地报告 / UMC任务与报告 | 实际返回且展示的规则 / 指标 / 值 | DOC |
|---|---|---|---|---|
| 插槽show tech.txt | 任务2，execution 2 | report 3 / 78、693 | 2 / 8 / 7 | 958,334字节 |
| 多设备日志，设备01 | 任务3，execution 3 | report 4 / 79、694 | 1 / 7 / 7 | 941,277字节 |
| 多设备日志，设备02 | 任务3，execution 4 | report 5 / 80、695 | 1 / 7 / 7 | 941,270字节 |

插槽案例识别DPX17000-A12，返回“软硬件检测”“接口收发包检查”。双设备案例均识别FW1000-TM-C，各返回“软硬件检测”。“全部”指本次UMC实际返回的结果，不虚构未返回的检测项。空指标值列表仍展示为空，不视为通过。

主序列号映射与替换次数：

- 插槽：02051992d233000002 → QA-84574-SHIPPED-20261024-01，2处。
- 双设备：02010078T231000329 → QA-84574-SHIPPED-20261024-01，55处；02010078T231000338 → QA-84574-UMC-20261028-02，55处。

任务2保留原有1条平台判定和2条旧UMC结果，新增2条，共5条；5/5在原表展示。任务3新建时经原设备选择、规则选择和离线执行动作进入执行态，两台设备共2条，2/2展示。三个新执行均实际点击“获取执行结果”，原refresh-log-parse请求成功。刷新两个任务后计数仍为5/5和2/2。

展开两个新案例共4条规则，核对22个指标及21个值全部存在，指标名、对象和值与本地任务接口返回一致（显示文本仅统一浏览器折叠空白）；完整命令输出与结果字段逐字一致。双设备异常特征库结论显示“异常”，未误判通过。

## 实现与归档

原TaskDetail新增umcResults，按原报告的executionId与原执行、设备、尝试、日志artifact/version/reference/SHA校验后读取本地结果。AVAILABLE结果先经原摘要校验加载。原“巡检判定结果”保留平台判定，同时展示UMC规则及来源报告、设备、结论、全部指标/值、错误、说明、建议和完整输出。

原refresh-log-parse在有关联UMC报告时获取/恢复该报告；已完成时直接读取本地快照。旧平台解析执行保留原路径。上传成功后停留原执行环节，执行行显示“UMC结果已获取”。UMC外部规则ID保持外部身份，不伪造平台ruleRevisionId。

三个新报告均AVAILABLE，结构化结果只入原srv_inspection_report_revision.result_json，来源和恢复检查点在source_context_json，规范化DTO摘要在result_sha256。MySQL JSON返回的文本排序、空白不作为DTO摘要计算依据。未新增结果表或业务JSON文件，未把UMC规则、设备模型全部入库。只读审计验证报告与原执行日志绑定、文件版本和SHA，未产生第二份UMC输入日志。

DOC使用原报告附件及PLT文件能力。三次真实浏览器下载的字节长度、SHA与对应入库文件完全一致。文件均可完整解析为Word2003 WordML wordDocument，按UMC实际DOC原件归档；本轮未做Word排版渲染验收，不声称DOCX/PDF/XML导出能力完成。

完成结果的刷新、查看、下载走本地存储；已完成重试不再调用UMC由实现及回归测试约束。浏览器网络证据证明实际页面请求，不单独用它推断服务端全部外联。原任务状态机、平台冻结检测和currentReportId未被UMC成功直接改写，本轮不宣称整个巡检任务生命周期完成。

## 测试设备及发现的修复

第二台QA设备通过现有正式设备创建接口产生：ID 2115280234702983169，SN QA-84574-UMC-20261028-02，项目84574。需求方明确批准仅为该新设备补一条INSPECTION_MOCK_ACCEPTANCE模拟发货来源并回写其发货字段；已按.run/umc-adapter/qa-second-device-shipment.py执行，来源ID 996610280845740002。未修改已有发货历史、未改表、未跑迁移、未放宽设备选择校验。

类型绑定使用现有正式受控导入接口。发现原MyBatis更新跳过null，使合法较新来源事实无法清除当前冲突字段，触发原CHECK。已为四个冲突字段设置ALWAYS更新策略；先用真实Mapper和CHECK复现失败，再验证修复通过，正式接口重试成功。冲突审计保留，未通过删约束或直接改映射绕过。

## 构建与验证

- Java定向测试109项通过，1项需要真实ClamAV的测试跳过；新增类型冲突恢复持久化回归1项通过，保留修复前失败证据。
- 原执行组件运行测试5项通过；定向vue-tsc、ESLint及完整前端构建通过。
- 后端使用scripts/worktree-build.ps1完整构建成功，发布前22项既有启动修复基线校验通过；构建期间遵守工作树互斥锁。
- 59191通过scripts/worktree-backend.ps1按已有明确授权重启，冻结清单87e9ebdc-24ea-4841-9204-654570bb00eb，产物SHA256为4c62cb5146ac047881ec52f2300b8c16d9bf8478f2ae10a6d90a3b5bd1452906。前端19191、DAC及既有网关参与真实链路。本轮未Git提交或推送。

## 可复核证据

- [双设备原判定结果截图](two-device-rule-summary.png)、[插槽案例及保留历史截图](slot-rule-summary.png)。
- [双设备展开指标截图](two-device-first-metrics.png)、[异常特征库值截图](two-device-abnormal-metrics.png)。
- [双设备刷新后规则计数](two-device-after-reload.json)、[插槽刷新后规则计数](slot-after-reload.json)。
- [双设备全部指标/值及输出比较](two-device-detail-equality.json)、[插槽全部指标/值及输出比较](slot-detail-equality.json)。
- [数据库及三份下载的只读核验](database-cases-proof.json)、[原文件/序列号映射及DOC格式](case-provenance.json)。
- [真实浏览器请求记录](integrated-browser-network.json)：只含方法、路径、状态及上传业务码，不包含凭据、Token、Cookie或下载票据。
- [插槽DOC](browser-download-1.doc)、[双设备02 DOC](browser-download-2.doc)、[双设备01 DOC](browser-download-3.doc)。

生成入口：.run/umc-adapter/browser-acceptance.py（真实UI）、audit-cases.py（只读DB，ROLLBACK）、finalize-cases-evidence.py（原文件哈希与格式核验）；测试日志在.run/umc-adapter/execution-result-*.log及source-conflict-regression-before.log。设备种子授权及正式接口事实见上一阶段[QA设备证据](../business-integration/second-qa-device-fixture.json)。
