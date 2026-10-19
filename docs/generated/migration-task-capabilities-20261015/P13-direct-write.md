# P13 执行单直写实施记录

新增DPPMS_CRM_EXECUTION_ORDER_DIRECT及独立GET模板入口，原COM适配器、任务及历史不变。INT私有来源解释和XML承接既有执行单头规则，来源SQL冻结已审核P13有效项目范围和整源最大id修订，根读取使用原id。多来源同执行单仍逐条绑定，不重写来源主键。

目标查询锁定并校验租户、来源系统和执行单号精确身份；不因大小写近似接管目标。原执行单状态、AF证据、主项目及后续消费者字段不更新；组织查询沿公开契约，保留原可用引用语义和九个批准部门别名。写入要求有效任务/运行/配置、原权限及实际REPEATABLE_READ事务，后续SQL失败回滚前序写入。

targeted-035221.log首次5项中大小写保护1项失败，定位为目标按实际大小写建索引后未命中输入键导致重复插入，已改为查询返回时精确核对并拒绝。targeted-035334.log实际XML事务5项全部通过，包括失败回滚、既有业务字段保护、租户/权限/版本和整源模板原键校验。前端TaskEditor运行测试16项通过；P12/P13真实Chromium组件选择通过，使用API fixture，未保存任务，证据D:/CodexTaskArtifacts/project-detail-followup/p13-direct-20261016/browser/browser-result.json。

当前未打包部署、保存新任务、真实业务预览或迁入。旧任务未改。
