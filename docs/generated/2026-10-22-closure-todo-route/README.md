# 闭环审批待办办理跳转验证

2026-10-22，按用户确认方案：闭环审批待办办理时直接打开对应项目详情的闭环页签，其他审批保持不变。

## 实现

- 待办入口仅识别 `PMS_MINIMAL_NORMAL_CLOSURE` 流程定义，读取原流程实例业务键，再通过既有闭环申请查询获取项目 ID；核对申请、项目、流程实例及流程定义关联后跳转 `/pms/project-management/project-master-detail?projectId=...&tab=closure`。
- 项目和申请 ID 沿用无损 ID 校验；查询失败或关联不一致时停留待办页并提示。其他审批保留 `BpmProcessInstanceDetail` 及原 `id/taskId` 参数，不新增接口请求。
- 复用既有项目闭环面板及 BPM 审批入口，无后端、授权、状态机、审批历史或数据库改动。

## 验证

- `pnpm exec vitest run --config vitest.pms-file.config.ts src/views/bpm/task/todo/handlingRoute.spec.ts src/views/pms/project/project-master-detail/components/ProjectNormalClosurePanel.runtime.spec.ts`：2 文件、45 项通过。覆盖长 ID、其他审批无额外请求、非法业务键、申请关联不一致、权限拒绝，以及原闭环面板的保护测试。
- 本次三个前端文件 ESLint 通过；`git diff --check` 通过。
- 真实 Chromium 登录当前 `19191` 前端，连接既有 `59191` 后端。实际普通审批待办点击办理仍进入原 BPM 详情，保留实例和任务 ID：[截图](other-approval.png)。
- 当前登录账号共 8 条待办，无正常闭环待办。仅在浏览器内替换闭环待办、流程实例及申请查询响应，点击真实办理按钮后跳转项目 `992203060005`，使用真实项目接口加载并显示闭环面板：[截图](closure-tab.png)。申请查询拒绝时仍留在待办页并展示失败提示：[截图](permission-denied.png)。替身未写入服务端。

## 验证边界

闭环正向跳转和拒绝路径采用浏览器响应替身，不能作为真实闭环审批待办业务验收。未创建申请或执行审批，未重启服务；当前账号无真实闭环待办，真实待办关联仍待有相应待办的账号验收。截图中的既有项目未冻结闭环规则及闭环守卫版本提示不属于本次修改。
