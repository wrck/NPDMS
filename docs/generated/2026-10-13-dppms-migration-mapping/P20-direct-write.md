# P20 项目真实产品直写实现与验证记录

2026-10-16。属于已批准迁移专项的实现记录，不变更PRD，不证明真实迁移完成。

审计原路径：ProjectRealProductMigrationService / ProjectRealProductImportService / ProjectRealProductMapper。原类、页面、接口、历史与数据保持；复制被本任务实际使用的规则至DirectRealProductPersistence / DirectRealProductPreparation / DirectRealProductMapper。原12字段、唯一上游10字段、整数数量、已存在整项目清单禁止追加未绑定记录、完整前值和租户/管理权限；不改实施范围或设备分配。

独立模板配置为 `migration/dppms/tasks/P20-direct.json`，使用本次已审核完整候选配置；保留原任务入口。新任务仅支持完整ONCE、UPSERT/RETAIN，不清空、不接管、不重置原绑定；预览不写业务记录。任务运行身份、租户、配置版本和权限仍由服务端校验。

实际 MySQL XML 验证 5 项通过，证据 `D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/targeted-042817.log`。固定127.0.0.1:24306/npdms_reverify_20261014，所有写入表名在单连接上先CREATE TEMPORARY TABLE遮蔽；关闭连接释放，未对常规业务表执行DDL或写入。覆盖预览零写入、正常承接、重放/人工变更保护、租户/权限及后续SQL失败整批回滚。

前端 TaskEditor 当前29项测试通过，实际Chromium组件检查通过，证据 `D:/CodexTaskArtifacts/project-detail-followup/all-reviewed-direct-final-20261016/browser/browser-result.json`。使用实际TaskEditor和Element Plus、API/子组件夹具；仅证明任务选择与零保存，不替代真实业务浏览器验收。

当前未加载59284，未创建/改存正式任务，未执行真实业务迁移，不操作59191。正式预览与实际数据依赖待候选包获准加载后核验。
