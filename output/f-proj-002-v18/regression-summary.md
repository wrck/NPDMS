# F-PROJ-002 V1.8 回归摘要

结论：`PASS`。

- 规格快照：锁定规格仓库`52dffd8286e619576086a72ab66bd6b050e80354`，离线校验通过；该提交已回写NPDMS `57923b1`实施完成状态，两仓进度一致。
- SDS状态：Phase 1/2/3保持已审核`BASELINE`，本Feature不触发重审。
- 后端：`mvn -pl pms-module-commerce,pms-module-asset,pms-module-project,yudao-server -am test`通过，32个Reactor模块零失败；项目模块194项、7项条件跳过。
- 静态契约：F-PROJ-002迁移与前端合同测试16/16通过。
- 前端：8GB堆运行`vue-tsc --noEmit`通过；pnpm 9.15.5生产构建通过。
- 数据库：空库、V69→V76、重复迁移和当前库validate均通过。
- 性能：五类查询最大P95为139.93ms，阈值2秒；SQL数均为1。
- 浏览器：组合拆分、树、进度、闭环、权限和四档响应式闭环通过，API及控制台零错误。
- 存量分类：V1.7项目树实现已逐路径收敛为`ADAPTED/REPLACED/RETIRED/REUSED`，清单覆盖校验通过。

生产构建仍会报告上游既有的环境标题占位符和旧CSS `*zoom`警告；后端日志仍有本地Quartz表缺失警告。它们未由F-PROJ-002引入，也不影响本Feature业务验收，作为后续平台质量债处理。
