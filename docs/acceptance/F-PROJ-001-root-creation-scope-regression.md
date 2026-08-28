# F-PROJ-001 根项目初始投影与创建人查看范围回归验收

> 验收日期：2026-08-25
> 范围：关闭 F-PROJ-001 与 F-PROJ-003 集成后暴露的创建人详情访问回归
> 边界：不新增项目角色、授权记录、状态轴、接口或数据库迁移

## 问题与修复

手工创建根项目成功后，创建事务未建立 `proj_project_tree_version` 与
`proj_project_tree_path`，而统一 ProjectTreeScope 只合并项目成员和显式授权，导致
F-PROJ-001 定义的创建人详情查看能力丢失。

本次最小修复：

- 根项目创建事务内发布版本 1 树投影，生成根节点自路径；投影失败时整体回滚。
- ProjectTreeScope 在 `VIEW` 动作中合并项目 `creator` 对应的基础范围。
- 创建人范围不进入 `MANAGE`，也不伪造 `PROJECT_MANAGER` 成员关系。
- 查询使用场景化 Query 对象；空候选集合直接返回空结果，租户条件始终生效。

## 自动验证

- `ProjectManualCreationApplicationServiceTest`：7/7。
- `ProjectTreeScopeServiceTest`：11/11，覆盖创建人可查看且不可管理。
- `ProjectManualCreationMySqlIntegrationTest`：13/13，覆盖成功投影及各失败点原子回滚。
- `pms-module-project` 完整回归：256 项，0 失败，0 错误，16 项按环境条件跳过。
- `yudao-server` 宿主机构建：PASS。
- 独立裁决：`GO`，无必须修改项。
- 规格回写提交：`975107a`，受管快照同步校验无冲突。

## 真实浏览器与数据库事实

使用管理员（稳定用户 ID `1`）从项目创建页面创建根项目
`992002000102`，随后直接进入项目详情并刷新：

- 首次进入及刷新后均可见项目 `创建人范围验收-1787606770170`。
- 浏览器未出现 5xx 响应或页面异常。
- 树版本为 `1 / ACTIVE`，`node_count=1`、`path_count=1`。
- 根节点距离为 0 的自路径记录为 1 条。
- 该项目成员角色记录为 0 条，未将创建人隐式提升为项目经理。

截图保存在本地忽略目录
`yudao-ui/yudao-ui-admin-vue3/node_modules/.cache/root-project-creator-visibility.png`，
仅作运行态辅助证据，不纳入源码提交。

## 结论

回归已在原 Feature 语义边界内关闭。该结论只恢复 F-PROJ-001 的创建人查看能力，
不改变 F-PROJ-003 的成员与显式授权模型，也不代表 Deployment、SIT、UAT 或 Release。
