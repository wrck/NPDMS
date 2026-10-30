# 原提交一致位置复核队列

> 该32文件队列已逐片段核查，69个片段的来源、裁决与修复后内容核对见 [逐片段裁决回执](32-file-hunk-adjudication.md)。27文件恢复原提交，5文件保留实证测试修复。下表为核查前队列原件，不再表示这些文件尚未核查；运行验收状态另见验证回执。

对照提交见 committed-baseline-recheck.json。以下32个文件仅表示原提交一致、当前工作区不同，不直接判为错误；须逐片段区分源后续增量、实证测试修复及其他改动。源后续提交不能再自动称为合并回归。

| 文件 | 来源分类 |
|---|---|
| `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/ProjectDeviceInstallationFact.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverContextSupport.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverExecutionReadinessPolicy.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverTaskCreationQueryService.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/port/CutoverReadinessPort.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApi.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/implementationreadiness/ImplementationReadinessService.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectBusinessContextApi.java` | 源后续提交有变化，需重核其迁入依据 |
| `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectBusinessContextApiImpl.java` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/task.ts` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/BusinessViewHost.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/components/UserTag/index.vue` | 测试工作区差量，需核对实证修复依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/satisfaction/questionnaire.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverApprovalPanel.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverChecklistPanel.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverClosurePanel.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/task-create/index.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/task-detail/index.vue` | 测试工作区差量，需核对实证修复依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/tasks/index.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/training/index.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/training/public-confirm.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/inheritance/detail/TaskStateActions.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-closure/NormalClosureProcessView.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectApprovalDrawer.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDeviceConfigLogDrawer.vue` | 测试工作区差量，需核对实证修复依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectFlowPanel.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectNormalClosurePanel.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/StageGateResultsPanel.vue` | 源后续提交有变化，需重核其迁入依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/TaskBusinessPanel.vue` | 测试工作区差量，需核对实证修复依据 |
| `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/saveArrivalReceipt.ts` | 测试工作区差量，需核对实证修复依据 |
