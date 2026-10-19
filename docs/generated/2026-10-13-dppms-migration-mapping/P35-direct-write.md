# P35 软件批次与版本历史直写实现与验证记录

2026-10-16。属于已批准迁移专项的实现记录，不变更PRD，不证明真实迁移完成。

审计原路径：DppmsAssetSourceHistoryService软件路径 / 软件Mapper / SoftwareVersionLegacyExtensionService / SoftwareVersionRecordEntityProvider。原类、页面、接口、历史与数据保持；复制被本任务实际使用的规则至DirectSoftwareHistoryPersistence / DirectSoftwareHistoryMapper / DirectSoftwareBatchMapper。主批次与版本由任务私有SQL写入。原八扩展属性通过新增的窄公开DppmsSoftwareHistoryExtensionApi复用原初始化上下文及不可变校验；不发布定义、不调用原主记录导入接口。原公开查询、修正、出厂分支未复制。

独立模板配置为 `migration/dppms/tasks/P35-direct.json`，使用本次已审核完整候选配置；保留原任务入口。新任务仅支持完整ONCE、UPSERT/RETAIN，不清空、不接管、不重置原绑定；预览不写业务记录。任务运行身份、租户、配置版本和权限仍由服务端校验。

实际 MySQL XML 验证 4 项通过，证据 `D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-043905.log`。固定127.0.0.1:24306/npdms_reverify_20261014，所有写入表名在单连接上先CREATE TEMPORARY TABLE遮蔽；关闭连接释放，未对常规业务表执行DDL或写入。覆盖预览零写入、正常承接、重放/人工变更保护、租户/权限及后续SQL失败整批回滚。

前端 TaskEditor 当前29项测试通过，实际Chromium组件检查通过，证据 `D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/browser/browser-result.json`。使用实际TaskEditor和Element Plus、API/子组件夹具；仅证明任务选择与零保存，不替代真实业务浏览器验收。

当前未加载59284，未创建/改存正式任务，未执行真实业务迁移，不操作59191。正式预览与实际数据依赖待候选包获准加载后核验。

正式八属性定义当前缺失仍阻塞真实预览；不为此发布定义或虚构版本ID。临时测试使用公开扩展接口替身验证错误回滚；真实Provider初始化授权仍由原服务执行。
