## Task 4: T-CP-004 从完整仓库导入 BPM 完整迁移单元

从锁定的完整后端仓库提交导入 BPM 源码、依赖、SQL、菜单权限及必要装配。共享文件冲突时以 mini 基线为主，只引入 BPM 所需最小差异；根 POM 与 `yudao-server` 显式启用 BPM。逐文件记录来源和兼容补丁。验证 `mvn -pl yudao-module-bpm -am test`，并为后续启动后的流程定义创建与查询验证保留可执行入口。

