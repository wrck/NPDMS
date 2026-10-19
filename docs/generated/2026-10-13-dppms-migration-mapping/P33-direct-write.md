# P33 普通安装历史直写实现与验证记录

2026-10-16。属于已批准迁移专项的实现记录，不变更PRD，不证明真实迁移完成。

审计原路径：InstallationHistoryInsertService / InstallationHistoricalCarryRules / InstallationHistoryInsertMapper / InstallationMapper / EngineeringRecordCodeGenerator。原类、页面、接口、历史与数据保持；复制被本任务实际使用的规则至DirectInstallationPersistence / DirectInstallationCarryRules / DirectInstallationMapper。原id顺序读取，再按设备聚合完整历史；仅原普通安装范围，转移仍拒绝。保留时间区间、不可变回放、完整地址快照。地址及仅补缺失当前位置使用原AssetLocationApi基础契约；不覆盖既有当前位置。

独立模板配置为 `migration/dppms/tasks/P33-direct.json`，使用本次已审核完整候选配置；保留原任务入口。新任务仅支持完整ONCE、UPSERT/RETAIN，不清空、不接管、不重置原绑定；预览不写业务记录。任务运行身份、租户、配置版本和权限仍由服务端校验。

实际 MySQL XML 验证 4 项通过，证据 `D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043708.log`。固定127.0.0.1:24306/npdms_reverify_20261014，所有写入表名在单连接上先CREATE TEMPORARY TABLE遮蔽；关闭连接释放，未对常规业务表执行DDL或写入。覆盖预览零写入、正常承接、重放/人工变更保护、租户/权限及后续SQL失败整批回滚。

前端 TaskEditor 当前29项测试通过，实际Chromium组件检查通过，证据 `D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/browser/browser-result.json`。使用实际TaskEditor和Element Plus、API/子组件夹具；仅证明任务选择与零保存，不替代真实业务浏览器验收。

当前未加载59284，未创建/改存正式任务，未执行真实业务迁移，不操作59191。正式预览与实际数据依赖待候选包获准加载后核验。

位置接口在测试中使用同一事务的临时夹具模拟其写入，已验证后续安装SQL失败会回滚这些模拟副作用；尚未宣称真实AssetLocationApi联调通过。
