# 需求分析保存后多余 workspace 预取修复

2026-10-29；范围为当前工作树 `TaskBusinessPanel.vue` 的入口预取条件及对应 runtime 测试。未部署、重启服务或执行数据库迁移。

原行为：Owner 保存后的权威回读已请求 workspace，随后 `ownerChanged -> load()` 又无条件预取 workspace。同一任务身份未变化，EntityPanel 不重新装载，因此这次预取无人消费。显式刷新还会在该预取后执行 Owner 自身的权威刷新。

修复仅将预取限制在 `load(true)`：首次进入和切换任务，并继续要求 enclosing 项目与业务上下文匹配。保存后的 task-business-context、Owner 项目读取、权限及业务结果刷新保留；EntityPanel 保存后的权威回读和显式刷新保持原流程。

验证命令在 `yudao-ui/yudao-ui-admin-vue3` 执行：

```powershell
pnpm.cmd exec vitest run --config vitest.pms-file.config.ts src/views/pms/project/project-master-detail/components/TaskBusinessPanel.runtime.spec.ts src/views/pms/project/project-master-detail/components/TaskBusinessPanel.recovery.runtime.spec.ts src/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.runtime.spec.ts
```

- 红测 `.run/feedback-20261029-prefetch-red.log`：41 项中 2 项准确失败；OwnerChanged 多读一次 workspace，显式刷新多读一次 workspace。
- 绿测 `.run/feedback-20261029-prefetch-green.log`：3 个 suite、57 项通过，退出码 0。
- 新增覆盖入口/切任务保留预取、不同项目不预取、OwnerChanged 保留事实与拒绝权限刷新、显式刷新保留 Owner 权威 workspace 读取。
- `git diff --check` 两个改动文件通过；精确修改前副本位于 `.run/feedback-20261029-prefetch-before/`。

真实浏览器耗时及请求数量复验由父线程继续；本记录不将 runtime 测试视为业务验收。
