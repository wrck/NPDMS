# 项目成员部门、职务补齐

## 范围

按本次澄清，项目成员增加与项目客户联系人一致的部门、职务字段，不增加客户列。当前项目客户联系人本身已有部门、职务表单与展示，本次不重复修改 CUS 联系人功能。

- 外部最终用户新增、编辑表单增加可选部门、职务，各最多 64 字符；保存时去除首尾空白。
- 成员列表展示部门、职务。内部成员沿用既有部门资料，未记录的职务显示“—”，不以项目角色代替职务。
- 外部部门复用 `department_name`，职务通过 V497 增加可空 `title VARCHAR(64)`。不回填历史、不创建系统身份或系统部门关系。
- 编辑仍关闭原成员区间、追加新区间，原区间资料保留；原项目范围、版本、幂等、角色资格与状态规则保持生效。

## 已验证

- 后端定向测试 37 项通过：普通成员服务 24、控制器 3、成员规则 9、项目经理应用服务 1。使用固定工作树工具链与同一构建锁；日志 `.run/project-member-profile-tests.log`。
- 前端两文件 15 项通过，覆盖表单回显、提交去空白及历史成员重新加入的资料传递；日志 `.run/project-member-profile-front.log`。
- 真实浏览器以既有服务经理会话打开项目 992203060198：列表表头包含部门、职务，内部成员空值显示“—”，角色仍独立展示；新增成员选择最终用户后可见部门、职务输入。已取消表单，未保存业务数据。
- 只读确认 59191 使用 `127.0.0.1:24306/npdms_domain_test`，已有 `department_name`、尚无 `title`。专项 Flyway 目录仅含 V497，`info` 仅该版本为 Pending；历史版本按 Compose 既有 missing 策略保留。单独 `validate` 因 V497 尚未执行而报 Pending，未更改验证规则、未执行迁移或 repair。

## 部署与验收边界

后端完整构建通过，22 项既有运行修复基线校验通过，冻结发布清单为 `.run/build/releases/71899523-787c-4078-a909-6a059ce9f23e/manifest.json`；日志 `.run/project-member-profile-build.log`。

部署脚本 `-ValidateOnly` 已通过：冻结产物、当前运行包与 `local,dac` 配置检查正常；日志 `.run/project-member-profile-deploy-preflight.log`。

需求方明确批准迁移、部署及样例验收后，已执行以下操作：

- Compose `npdms-domain-test` 仅执行 V497，升级至 497；新增 `title VARCHAR(64) NULL`。178 条原成员记录的原字段摘要在迁移前后完全相同，新增列均为空；证据 `.run/project-member-profile-schema-before.json`、`-after.json`、`.run/project-member-profile-flyway-migrate.log`。
- 迁移后 Flyway `validate` 通过，433 项已执行历史/当前迁移验证成功；证据 `.run/project-member-profile-flyway-validate-after.log`。
- 经固定部署脚本重启 59191，PID 35872，运行 `local,dac`，健康检查通过；证据 `.run/project-member-profile-deploy.log`。48182 未操作。
- 部署后真实接口读取项目 992203060198：仍有 4 条当前成员，响应均包含可空 `title`，未新增成员或修改业务事实；证据 `.run/project-member-profile-api-readback.json`。

## 真实浏览器闭环验收

管理员正式会话已就绪，全部写入均通过真实浏览器操作既有隔离样例 `992203060010`（项目详情专项验收样例）。未重置密码、启用账号或追加角色/项目范围授权，未向真实项目 `992203060198` 写入成员。

1. 新增唯一验收成员“成员字段验收1017”，单位“隔离验收单位1017”，角色最终用户，填写“信息技术部 / 项目主管”。保存后列表正确展示；整页重开再进入项目成员、打开编辑弹窗，两项资料正确回显。
2. 编辑为“网络运维部 / 技术负责人”。保存后整页重开、重新打开编辑弹窗，新值正确回显；历史成员列表保留旧“信息技术部 / 项目主管”及对应失效时间，未覆盖原区间。
3. 通过移出表单结束本次新增成员区间，填写验收完成原因。当前列表恢复原两条管理员 PM/SM；历史列表保留本次两段部门、职务资料及失效时间，不删除历史。

原生接口只读复核与浏览器一致：新增区间 `2109318296744415275`、修改后区间 `2109318296744415276`，两段 `userId` 均为空、角色均为 END_USER；移出后本次成员 CURRENT=0、HISTORY=2。真实项目 `992203060198` 当前成员仍为 4 条。

证据：`.run/project-member-profile-browser-history.txt` 为最终历史列表真实 DOM；`.run/project-member-profile-browser-created.json`、`-edited.json`、`-removed.json` 为各步骤完成后的只读接口核对。新增、修改、移出均由浏览器完成，未使用接口或 SQL 代写验收动作。

本次字段改动尚未提交或推送，其他会话改动保留。
