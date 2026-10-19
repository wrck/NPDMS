# 独立同步套件

遵守仓库 `docs/coding/independent-base-sync.md`。本约定依据用户在 2026-10-17 的明确实现要求，仅适用于全新 basesync 套件。

- 每个任务一个类，继承 AbstractSyncTask 或其公共默认实现 DefaultSyncTask；任务专用方法、数据对象和 Mapper 声明内聚到任务类。
- 公共引用 API 在基类默认流程调用；默认查询、清理、批量插入及公共辅助能力必须继承复用。来源绑定由需要该保护的任务在前后处理调用公共存储方法。
- 以旧 SyncMesShipmentInfoJob 作为子类编排用法示例；每次 syncData 独立指定数据名、clear 和 useRowHandler。逐行读取使用 MyBatis 原生 ResultHandler，不另定义 Handler 或任务级 ClearPolicy，不要求业务行实现动作接口。
- SourceMapper/TargetMapper 配合 XML 命名约定提供默认执行能力；插入使用一批一条 SQL；已批准的单表 upsert 使用 MySQL ON DUPLICATE KEY UPDATE，每批一条 SQL，不逐行查询或判断。其他显式条件更新保留原版本校验。目标连接和事务交给现有 SqlSessionTemplate 与 Spring。
- 集成任务通过 MyBatis XML 直接操作已审核表；不转调原同步任务或另建专用 API/Service。
- 只重载专用字段映射、SQL 和必要前后处理。原有同步任务与主线程改动保持不变。

- 基类设计以原始 AbstractSynchronizeTask 的全部执行能力为依据，不以已有任务实现作反向约束；默认流式按批释放，显式全量和分页及其返回语义必须保留。子类流式前后处理不得跨批累积源行或无界引用集合。
