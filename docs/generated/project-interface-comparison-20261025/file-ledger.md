# 逐文件核查清单

源 HEAD：`e0ae05247e6662c155c5503919506405eed07cf9`；测试 HEAD：`c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a`。双方工作文件均可能含未提交内容，以摘要为准。

共 555 个生产文件，121 个存在差异。恢复/保留均为建议，不代表已实施。下表每个文件在 ui-differences.diff 中均有完整片段；文件一致不代表数据库定义或运行包一致。

| 序号 | 文件 | 差异 | 裁决关联 |
|---|---|---|---|
| 1 | `api/pms/acceptance/deliverable-checklist/index.ts` | DIFFERENT | ATT-09：原生附件、保存后不关闭、签署/有效/提交日期契约增量待裁决 |
| 2 | `api/pms/acceptance/project-deliverable/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 3 | `api/pms/asset/device/index.ts` | DIFFERENT | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 4 | `api/pms/asset/warranty-renewal.ts` | TARGET_ONLY | PM-04：续保新增展示及契约待来源裁决 |
| 5 | `api/pms/commerce/index.ts` | DIFFERENT | PM-02/08：合同订单来源与关联资料契约增量，不能当无语义重构 |
| 6 | `api/pms/customer/contacts/index.ts` | DIFFERENT | PM-06：历史只读/人员关系保留；新增展示字段独立裁决 |
| 7 | `api/pms/cutover/task.ts` | DIFFERENT | CUT-04/05：恢复创建项目服务等级完整契约和防串请求 |
| 8 | `api/pms/engineering/construction-plan/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 9 | `api/pms/engineering/installation-source-history.ts` | TARGET_ONLY | DEV-03：来源安装历史 API 新增契约，随历史只读能力裁决，不转当前授权 |
| 10 | `api/pms/engineering/preparation/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 11 | `api/pms/engineering/requestTime.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 12 | `api/pms/engineering/requirement-analysis/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 13 | `api/pms/platform/business/index.ts` | TARGET_ONLY | HOST-02/03：新增通用业务/交付件契约与入口需追来源 |
| 14 | `api/pms/platform/businessmodel/delivery.ts` | TARGET_ONLY | HOST-02/03：新增通用业务/交付件契约与入口需追来源 |
| 15 | `api/pms/platform/businessmodel/index.ts` | DIFFERENT | HOST-02/03：新增通用业务/交付件契约与入口需追来源 |
| 16 | `api/pms/platform/delivery/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 17 | `api/pms/platform/dynamic-form/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 18 | `api/pms/platform/file/index.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 19 | `api/pms/project/associated-data.ts` | TARGET_ONLY | PM-01/02：关联产品/回款/许可展示增量待裁决 |
| 20 | `api/pms/project/legacy-detail.ts` | TARGET_ONLY | PM-10：保留真实历史只读；新整套入口需来源证据 |
| 21 | `api/pms/project/projects/index.ts` | DIFFERENT | PM-08/09：合同订单/指纹/只读项目名/新前置条件必须前后端一起裁决 |
| 22 | `api/pms/project/unified-members/index.ts` | DIFFERENT | PM-07：保留未确认身份和历史人员的授权边界 |
| 23 | `components/BusinessEntity/ApprovalPanel.vue` | DIFFERENT | HOST-06：类型兼容；未发现对应操作被删除 |
| 24 | `components/BusinessEntity/BusinessEntityForm.vue` | DIFFERENT | HOST-06：无损数值输入新增模式，精度保护保留；启用范围另裁决 |
| 25 | `components/BusinessEntity/BusinessEntityHost.vue` | DIFFERENT | HOST-01/02：筛选、删除、交付件与回执增量逐项裁决；保留授权历史保护 |
| 26 | `components/BusinessEntity/BusinessEntityList.vue` | DIFFERENT | HOST-01/02：筛选、删除、交付件与回执增量逐项裁决；保留授权历史保护 |
| 27 | `components/BusinessEntity/ContentHistoryPanel.vue` | DIFFERENT | HOST-06：类型兼容；未发现对应操作被删除 |
| 28 | `components/BusinessEntity/DefaultBusinessDelivery.vue` | TARGET_ONLY | HOST-02：默认交付件整套新增操作，按模型契约裁决 |
| 29 | `components/BusinessEntity/DefaultDeliveryRecords.vue` | TARGET_ONLY | HOST-02：默认交付件整套新增操作，按模型契约裁决 |
| 30 | `components/BusinessEntity/DefaultDeliveryUpload.vue` | TARGET_ONLY | HOST-02：默认交付件整套新增操作，按模型契约裁决 |
| 31 | `components/BusinessEntity/businessListFilter.ts` | TARGET_ONLY | HOST-01/02：筛选、删除、交付件与回执增量逐项裁决；保留授权历史保护 |
| 32 | `components/BusinessEntity/useBusinessEntity.ts` | DIFFERENT | HOST-01/02：筛选、删除、交付件与回执增量逐项裁决；保留授权历史保护 |
| 33 | `components/PmsFileArtifact/PmsFileReferenceList.vue` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 34 | `components/PmsFileArtifact/PmsFileUploader.vue` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 35 | `components/PmsFileArtifact/PmsFileVersionDrawer.vue` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 36 | `components/PmsFileArtifact/types.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 37 | `components/PmsFileArtifact/useFileSlotState.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 38 | `components/ProjectBusiness/ProjectBusinessDeliveries.vue` | TARGET_ONLY | HOST-03：新增业务接入体系，先追批准来源；不能自动保留 |
| 39 | `components/ProjectBusiness/ProjectBusinessPage.vue` | TARGET_ONLY | HOST-03：新增业务接入体系，先追批准来源；不能自动保留 |
| 40 | `components/ProjectBusiness/useProjectBusiness.ts` | TARGET_ONLY | HOST-03：新增业务接入体系，先追批准来源；不能自动保留 |
| 41 | `router/modules/remaining.ts` | DIFFERENT | HOST-04：恢复源割接直达能力；新增入口另裁决 |
| 42 | `views/pms/acceptance/acceptance/index.vue` | DIFFERENT | ATT-08：见对应业务逐项裁决；不得整文件恢复 |
| 43 | `views/pms/acceptance/deliverable-checklist/ChecklistAttachments.vue` | TARGET_ONLY | ATT-09：原生附件、保存后不关闭、签署/有效/提交日期契约增量待裁决 |
| 44 | `views/pms/acceptance/deliverable-checklist/index.vue` | DIFFERENT | ATT-09：原生附件、保存后不关闭、签署/有效/提交日期契约增量待裁决 |
| 45 | `views/pms/asset/device-log-archive/index.vue` | DIFFERENT | LOG-01/03/04：恢复失败反馈；保留性能修复；布局增量另裁决 |
| 46 | `views/pms/asset/device/DeviceWorkbenchDialogs.vue` | DIFFERENT | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 47 | `views/pms/asset/device/components/DeviceContractRelationshipPanel.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 48 | `views/pms/asset/device/components/DeviceFactoryPanel.vue` | DIFFERENT | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 49 | `views/pms/asset/device/components/DeviceFactorySourceHistoryPanel.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 50 | `views/pms/asset/device/components/DeviceInstallationSourceHistoryPanel.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 51 | `views/pms/asset/device/components/DeviceLocationPanel.vue` | DIFFERENT | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 52 | `views/pms/asset/device/components/DeviceNetworkVersionPanel.vue` | DIFFERENT | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 53 | `views/pms/asset/device/components/DeviceParsedLogRecords.vue` | DIFFERENT | DEV-04：保留错误/分页/生命周期保护；不扩大业务范围 |
| 54 | `views/pms/asset/device/components/DeviceShipmentPanel.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 55 | `views/pms/asset/device/components/DeviceSoftwareSourceHistoryPanel.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 56 | `views/pms/asset/device/components/DeviceSoftwareVersionMaintenancePanel.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 57 | `views/pms/asset/device/components/DeviceWarrantyPanel.vue` | DIFFERENT | DEV-04：保留错误/分页/生命周期保护；不扩大业务范围 |
| 58 | `views/pms/asset/device/components/DeviceWarrantyRenewalTable.vue` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 59 | `views/pms/asset/device/components/device-native-fields.ts` | TARGET_ONLY | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 60 | `views/pms/asset/device/index.vue` | DIFFERENT | DEV-01/02/03、PM-04：设备关联新增展示/操作逐项追来源；历史只读和权限保护保留 |
| 61 | `views/pms/business/site-survey/SurveyBusinessFields.vue` | TARGET_ONLY | HOST-03：新增业务接入体系，先追批准来源；不能自动保留 |
| 62 | `views/pms/business/site-survey/index.vue` | TARGET_ONLY | HOST-03：新增业务接入体系，先追批准来源；不能自动保留 |
| 63 | `views/pms/commerce/delivery-scope/DeliveryScopeHistoryDrawer.vue` | DIFFERENT | PM-05：范围展示增量待裁决；确认状态与版本保护保留 |
| 64 | `views/pms/commerce/delivery-scope/index.vue` | DIFFERENT | PM-05：范围展示增量待裁决；确认状态与版本保护保留 |
| 65 | `views/pms/customer/contacts/index.vue` | DIFFERENT | PM-06：历史只读/人员关系保留；新增展示字段独立裁决 |
| 66 | `views/pms/cutover/components/CutoverApprovalPanel.vue` | DIFFERENT | CUT-07：恢复标准服务中文标签 |
| 67 | `views/pms/cutover/components/CutoverChecklistField.vue` | DIFFERENT | CUT-08：恢复折叠说明；FileId/空文件保护保留 |
| 68 | `views/pms/cutover/components/CutoverChecklistPanel.vue` | DIFFERENT | CUT-08：恢复折叠说明；FileId/空文件保护保留 |
| 69 | `views/pms/cutover/components/CutoverClosureEvidencePanel.vue` | DIFFERENT | ATT-12：保留有效 FileId、空文件保护，不影响源主体功能 |
| 70 | `views/pms/cutover/components/CutoverClosureForm.vue` | DIFFERENT | CUT-09/10：恢复只读文本及展开条件；保留安全保护 |
| 71 | `views/pms/cutover/components/CutoverClosurePanel.vue` | DIFFERENT | CUT-09/10：恢复只读文本及展开条件；保留安全保护 |
| 72 | `views/pms/cutover/components/CutoverPlanEditor.vue` | DIFFERENT | ATT-12：保留有效 FileId、空文件保护，不影响源主体功能 |
| 73 | `views/pms/cutover/components/CutoverPlanPanel.vue` | DIFFERENT | ATT-12：保留有效 FileId、空文件保护，不影响源主体功能 |
| 74 | `views/pms/cutover/task-create/index.vue` | DIFFERENT | CUT-04/05：恢复创建项目服务等级完整契约和防串请求 |
| 75 | `views/pms/cutover/task-detail/index.vue` | DIFFERENT | CUT-11：保留已证实的限定 P6 closureOnly 修复 |
| 76 | `views/pms/cutover/tasks/index.vue` | DIFFERENT | CUT-06：恢复仅审批权限用户入口，不扩大权限 |
| 77 | `views/pms/engineering/arrival-acceptance/components/ArrivalEvidencePanel.vue` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 78 | `views/pms/engineering/arrival/index.vue` | DIFFERENT | ATT-10：原生材料扩展待来源裁决；已有 null ID 修复保留 |
| 79 | `views/pms/engineering/attachment/AdditionalNativeAttachments.vue` | TARGET_ONLY | ATT-01～08：共享原生附件/先保存门槛/材料动作；按每个业务独立裁决 |
| 80 | `views/pms/engineering/attachment/NativeAttachments.vue` | TARGET_ONLY | ATT-01～08：共享原生附件/先保存门槛/材料动作；按每个业务独立裁决 |
| 81 | `views/pms/engineering/briefing/index.vue` | DIFFERENT | ATT-07：见对应业务逐项裁决；不得整文件恢复 |
| 82 | `views/pms/engineering/configuration/index.vue` | DIFFERENT | ATT-01：见对应业务逐项裁决；不得整文件恢复 |
| 83 | `views/pms/engineering/configuration/with-device-logs.vue` | DIFFERENT | LOG-02：恢复源业务说明与任务证据区别 |
| 84 | `views/pms/engineering/duration-change/ApprovalDetail.vue` | DIFFERENT | ATT-11：保留历史缺引用时的安全提示 |
| 85 | `views/pms/engineering/ext-proc/ExternalProcurementDetailDialog.vue` | DIFFERENT | ATT-03：见对应业务逐项裁决；不得整文件恢复 |
| 86 | `views/pms/engineering/ext-proc/ExternalProcurementFormDialog.vue` | DIFFERENT | ATT-03：见对应业务逐项裁决；不得整文件恢复 |
| 87 | `views/pms/engineering/ext-proc/index.vue` | DIFFERENT | ATT-03：见对应业务逐项裁决；不得整文件恢复 |
| 88 | `views/pms/engineering/joint-test/index.vue` | DIFFERENT | ATT-02：见对应业务逐项裁决；不得整文件恢复 |
| 89 | `views/pms/engineering/material-exch/MaterialExchangeDetailDialog.vue` | DIFFERENT | ATT-06：见对应业务逐项裁决；不得整文件恢复 |
| 90 | `views/pms/engineering/material-exch/index.vue` | DIFFERENT | ATT-06：见对应业务逐项裁决；不得整文件恢复 |
| 91 | `views/pms/engineering/material-req/MaterialRequisitionDetailDialog.vue` | DIFFERENT | ATT-05：见对应业务逐项裁决；不得整文件恢复 |
| 92 | `views/pms/engineering/material-req/MaterialRequisitionFormDialog.vue` | DIFFERENT | ATT-05：见对应业务逐项裁决；不得整文件恢复 |
| 93 | `views/pms/engineering/material-req/index.vue` | DIFFERENT | ATT-05：见对应业务逐项裁决；不得整文件恢复 |
| 94 | `views/pms/engineering/outsource/OutsourceRequestDetailDialog.vue` | DIFFERENT | ATT-04：见对应业务逐项裁决；不得整文件恢复 |
| 95 | `views/pms/engineering/outsource/OutsourceRequestFormDialog.vue` | DIFFERENT | ATT-04：见对应业务逐项裁决；不得整文件恢复 |
| 96 | `views/pms/engineering/outsource/index.vue` | DIFFERENT | ATT-04：见对应业务逐项裁决；不得整文件恢复 |
| 97 | `views/pms/engineering/solution-reviewed/saveSolutionScripts.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 98 | `views/pms/platform/deliverables/index.vue` | TARGET_ONLY | HOST-02/03：新增通用业务/交付件契约与入口需追来源 |
| 99 | `views/pms/project/inheritance/detail/DeliveryModuleTable.vue` | DIFFERENT | PM-05：范围展示增量待裁决；确认状态与版本保护保留 |
| 100 | `views/pms/project/inheritance/detail/TaskStateActions.vue` | DIFFERENT | HOST-07：排版差异，无业务裁决 |
| 101 | `views/pms/project/inheritance/detail/index.vue` | DIFFERENT | PM-03：共享基础信息替换；字段和角色逐项对齐，不误报结束日期丢失 |
| 102 | `views/pms/project/inheritance/members/OrdinaryMemberForm.vue` | DIFFERENT | PM-07：保留未确认身份和历史人员的授权边界 |
| 103 | `views/pms/project/inheritance/members/ProjectMembersPanel.vue` | DIFFERENT | PM-07：保留未确认身份和历史人员的授权边界 |
| 104 | `views/pms/project/inheritance/projects/index.vue` | DIFFERENT | PM-08/09：合同订单/指纹/只读项目名/新前置条件必须前后端一起裁决 |
| 105 | `views/pms/project/project-closure/NormalClosureProcessView.vue` | DIFFERENT | HOST-05：恢复中文、日期与折叠证据；保留冻结原件 |
| 106 | `views/pms/project/project-master-detail/components/PreparationItemDrawer.vue` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 107 | `views/pms/project/project-master-detail/components/ProjectAssociatedDataPanel.vue` | TARGET_ONLY | PM-01/02：关联产品/回款/许可展示增量待裁决 |
| 108 | `views/pms/project/project-master-detail/components/ProjectBaseInfo.vue` | DIFFERENT | PM-02：商务/回款/许可展示增量待裁决；已有加载修复保留 |
| 109 | `views/pms/project/project-master-detail/components/ProjectDeviceConfigLogDrawer.vue` | DIFFERENT | LOG-05：保留独立来源加载及防串设备修复 |
| 110 | `views/pms/project/project-master-detail/components/ProjectLegacyDetailPanel.vue` | TARGET_ONLY | PM-10：保留真实历史只读；新整套入口需来源证据 |
| 111 | `views/pms/project/project-master-detail/components/ProjectLegacyRecordDetails.vue` | TARGET_ONLY | PM-10：保留真实历史只读；新整套入口需来源证据 |
| 112 | `views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue` | DIFFERENT | HOST-07：单候选人 undefined 类型兼容，保留 |
| 113 | `views/pms/project/project-master-detail/components/ProjectSerialPanel.vue` | DIFFERENT | PM-04：续保新增展示及契约待来源裁决 |
| 114 | `views/pms/project/project-master-detail/components/RequirementAnalysisSectionCard.vue` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 115 | `views/pms/project/project-master-detail/components/TaskBusinessPanel.vue` | DIFFERENT | CUT-11：保留已证实的限定 P6 closureOnly 修复 |
| 116 | `views/pms/project/project-master-detail/components/durationChangeFormState.ts` | DIFFERENT | ATT-12/13：保留 FileId/空引用/并发安全保护；接口增量见差异 |
| 117 | `views/pms/project/project-master-detail/components/saveArrivalReceipt.ts` | DIFFERENT | ATT-10：保留首次上传省略 null fileId 的已复验修复 |
| 118 | `views/pms/project/project-master-detail/index.vue` | DIFFERENT | PM-01：新增产品和实施范围入口；保留范围版本检查 |
| 119 | `views/pms/project/projects/ProjectCreationSource.vue` | TARGET_ONLY | PM-08/09：合同订单/指纹/只读项目名/新前置条件必须前后端一起裁决 |
| 120 | `views/pms/project/projects/index.vue` | DIFFERENT | PM-08/09：合同订单/指纹/只读项目名/新前置条件必须前后端一起裁决 |
| 121 | `views/pms/project/projects/useProjectCreationSource.ts` | TARGET_ONLY | PM-08/09：合同订单/指纹/只读项目名/新前置条件必须前后端一起裁决 |

## 一致文件

下列仅指当前文本归一化后相同，未据此推定数据库配置、授权、历史状态或运行后端一致。

- `api/pms/acceptance/acceptance-report/index.ts`
- `api/pms/acceptance/acceptance-report/operationActions.ts`
- `api/pms/acceptance/acceptance-report/operationResults.ts`
- `api/pms/acceptance/acceptance/index.ts`
- `api/pms/acceptance/archive-document/index.ts`
- `api/pms/acceptance/completion-certificate/index.ts`
- `api/pms/acceptance/normal-closure/index.ts`
- `api/pms/acceptance/satisfaction/index.ts`
- `api/pms/asset/device-log-archive/index.ts`
- `api/pms/asset/device/archive.ts`
- `api/pms/asset/location/index.ts`
- `api/pms/asset/product-official.ts`
- `api/pms/commerce/projectEquipment.ts`
- `api/pms/customer/index.ts`
- `api/pms/customer/service-level/index.ts`
- `api/pms/customerConfirmation.ts`
- `api/pms/cutover/approval.ts`
- `api/pms/cutover/checklist.ts`
- `api/pms/cutover/closure.ts`
- `api/pms/cutover/configuration.ts`
- `api/pms/cutover/index.ts`
- `api/pms/cutover/plan.ts`
- `api/pms/cutover/request.ts`
- `api/pms/cutover/types.ts`
- `api/pms/engineering/announcement-check/index.ts`
- `api/pms/engineering/announcement/index.ts`
- `api/pms/engineering/arrival-acceptance/index.ts`
- `api/pms/engineering/arrival/index.ts`
- `api/pms/engineering/authorization/index.ts`
- `api/pms/engineering/briefing/index.ts`
- `api/pms/engineering/configuration/index.ts`
- `api/pms/engineering/deliverable/index.ts`
- `api/pms/engineering/doc-template/index.ts`
- `api/pms/engineering/ext-proc/index.ts`
- `api/pms/engineering/form-instance/index.ts`
- `api/pms/engineering/form-template/index.ts`
- `api/pms/engineering/installation/index.ts`
- `api/pms/engineering/issue/index.ts`
- `api/pms/engineering/joint-test/index.ts`
- `api/pms/engineering/material-exch/index.ts`
- `api/pms/engineering/material-req/index.ts`
- `api/pms/engineering/outsource/index.ts`
- `api/pms/engineering/requirement-analysis/entity.ts`
- `api/pms/engineering/requirement-analysis/operationActions.ts`
- `api/pms/engineering/requirement-analysis/operationResults.ts`
- `api/pms/engineering/requirement/index.ts`
- `api/pms/engineering/resource/index.ts`
- `api/pms/engineering/risk/index.ts`
- `api/pms/engineering/site-survey/entity.ts`
- `api/pms/engineering/site-survey/index.ts`
- `api/pms/engineering/site-survey/operationActions.ts`
- `api/pms/engineering/site-survey/operationInput.ts`
- `api/pms/engineering/site-survey/operationResults.ts`
- `api/pms/engineering/solution-review/index.ts`
- `api/pms/engineering/solution/index.ts`
- `api/pms/engineering/stage-plan-suggestion-rule/index.ts`
- `api/pms/engineering/stage-plan/index.ts`
- `api/pms/engineering/training/index.ts`
- `api/pms/inspection/online.ts`
- `api/pms/inspection/request.ts`
- `api/pms/inspection/rules.ts`
- `api/pms/inspection/tasks.ts`
- `api/pms/platform/approval/index.ts`
- `api/pms/platform/business-view/ids.ts`
- `api/pms/platform/business-view/index.ts`
- `api/pms/platform/deviceCollection.ts`
- `api/pms/platform/entityversion/index.ts`
- `api/pms/platform/file/version-reference.ts`
- `api/pms/platform/processdefinition/index.ts`
- `api/pms/platform/resultsubscription/index.ts`
- `api/pms/project/customer-selected/index.ts`
- `api/pms/project/execution-operations/index.ts`
- `api/pms/project/members/index.ts`
- `api/pms/project/project-closure/index.ts`
- `api/pms/project/project-risk/index.ts`
- `api/pms/project/project-splits/contract-scope.ts`
- `api/pms/project/project-splits/index.ts`
- `api/pms/project/project-splits/parent-scope.ts`
- `api/pms/project/project-splits/workspace.ts`
- `api/pms/project/project-templates/definitions.ts`
- `api/pms/project/project-templates/designerAssets.ts`
- `api/pms/project/project-templates/directBinding.ts`
- `api/pms/project/project-templates/execution.ts`
- `api/pms/project/project-templates/index.ts`
- `api/pms/project/project-templates/operationTypes.ts`
- `api/pms/project/project-templates/operations.ts`
- `api/pms/project/project-templates/requirementBinding.ts`
- `api/pms/project/project-templates/rules.ts`
- `api/pms/project/projects/nodeExecutions.ts`
- `api/pms/project/projects/projectPlan.ts`
- `api/pms/project/projects/rework.ts`
- `api/pms/project/selection.ts`
- `api/pms/project/stage-business/index.ts`
- `api/pms/project/stage-gates.ts`
- `api/pms/project/task-business/index.ts`
- `api/pms/project/task-workbench/index.ts`
- `components/BusinessEntity/DeclaredBusinessFormLayout.vue`
- `components/BusinessEntity/DeliveryPanel.vue`
- `components/BusinessEntity/businessOperationIntent.ts`
- `components/BusinessEntity/declaredFormPresentation.ts`
- `components/BusinessView/BusinessViewHost.vue`
- `components/BusinessView/DeclaredBusinessView.vue`
- `components/BusinessView/ProjectMembersBusinessView.vue`
- `components/BusinessView/businessActionBar.ts`
- `components/BusinessView/operationClient.ts`
- `components/BusinessView/operationHost.ts`
- `components/BusinessView/operationPresentation.ts`
- `components/BusinessView/presentationRoute.ts`
- `components/BusinessView/registry.ts`
- `components/PmsEntitySelect/index.vue`
- `components/PmsFileArtifact/PmsFilePreviewDialog.vue`
- `components/PmsFileArtifact/PmsFileVersionReferenceHistory.vue`
- `components/PmsFileArtifact/PmsFileVersionReferenceList.vue`
- `components/PmsFileArtifact/PmsPdfPreview.vue`
- `components/PmsFileArtifact/fileMediaType.ts`
- `components/PmsFileArtifact/index.ts`
- `components/PmsLocationSelector/index.vue`
- `hooks/web/useDraftLeaveGuard.ts`
- `hooks/web/useDraftUploads.ts`
- `views/pms/acceptance/acceptance-report/AcceptanceReportForm.vue`
- `views/pms/acceptance/acceptance-report/ReportDraftEditor.vue`
- `views/pms/acceptance/acceptance-report/ReportVersionHistoryDrawer.vue`
- `views/pms/acceptance/acceptance-report/acceptanceUploadIntent.ts`
- `views/pms/acceptance/acceptance-report/detail.vue`
- `views/pms/acceptance/acceptance-report/index.vue`
- `views/pms/acceptance/acceptance-report/reportState.ts`
- `views/pms/acceptance/archive-document/index.vue`
- `views/pms/acceptance/completion-certificate/CompletionCertificateForm.vue`
- `views/pms/acceptance/completion-certificate/CompletionCertificatePrintV2.vue`
- `views/pms/acceptance/completion-certificate/ConfirmationLinkDialog.vue`
- `views/pms/acceptance/completion-certificate/ConfirmationPrintPreview.vue`
- `views/pms/acceptance/completion-certificate/ConfirmationPrintTemplates.vue`
- `views/pms/acceptance/completion-certificate/ConfirmationRecordActions.vue`
- `views/pms/acceptance/completion-certificate/QuestionnairePrintField.vue`
- `views/pms/acceptance/completion-certificate/SatisfactionPrintV2.vue`
- `views/pms/acceptance/completion-certificate/WordPrintFrameV2.vue`
- `views/pms/acceptance/completion-certificate/arrivalServiceLabel.ts`
- `views/pms/acceptance/completion-certificate/confirmationPdfDelivery.ts`
- `views/pms/acceptance/completion-certificate/confirmationPrintForm.ts`
- `views/pms/acceptance/completion-certificate/index.vue`
- `views/pms/acceptance/completion-certificate/wordPrintFormat.ts`
- `views/pms/acceptance/completion-certificate/wordPrintPdf.ts`
- `views/pms/acceptance/satisfaction/CompletionCertificateSheet.vue`
- `views/pms/acceptance/satisfaction/QuestionnaireDesigner.vue`
- `views/pms/acceptance/satisfaction/QuestionnaireFields.vue`
- `views/pms/acceptance/satisfaction/QuestionnaireTable.vue`
- `views/pms/acceptance/satisfaction/TemplatePanel.vue`
- `views/pms/acceptance/satisfaction/index.vue`
- `views/pms/acceptance/satisfaction/questionnaire.vue`
- `views/pms/acceptance/satisfaction/questionnaireDesigner.ts`
- `views/pms/asset/device/archive/index.vue`
- `views/pms/asset/device/components/DeviceArchiveFormDialog.vue`
- `views/pms/asset/device/components/DeviceArchiveStatusChangeDialog.vue`
- `views/pms/asset/device/components/DeviceAssemblyTreeDrawer.vue`
- `views/pms/asset/device/components/DeviceAssignCustomerDialog.vue`
- `views/pms/asset/device/components/DeviceAssignProjectDialog.vue`
- `views/pms/asset/device/components/DeviceAssignmentHistoryDrawer.vue`
- `views/pms/asset/device/components/DeviceConfigurationLogPanel.vue`
- `views/pms/asset/device/components/DeviceCustomerRelationshipDrawer.vue`
- `views/pms/asset/device/components/DeviceOfficialInfoPanel.vue`
- `views/pms/asset/device/components/DeviceReadOnlyFields.vue`
- `views/pms/asset/device/components/DeviceSourceData.vue`
- `views/pms/asset/device/components/DeviceSourceStatus.vue`
- `views/pms/asset/device/components/DeviceSummaryPanel.vue`
- `views/pms/asset/device/components/DeviceTechnicalNoticePanel.vue`
- `views/pms/asset/device/config-log/index.vue`
- `views/pms/commerce/commerceInteraction.ts`
- `views/pms/commerce/delivery-scope/DeliveryScopeEditor.vue`
- `views/pms/customer/contacts/ContactFields.vue`
- `views/pms/customer/contacts/ContactHistoryDialog.vue`
- `views/pms/cutover/approval-form/index.vue`
- `views/pms/cutover/approval-form/route.vue`
- `views/pms/cutover/components/CutoverApprovalDecisionForm.vue`
- `views/pms/cutover/components/CutoverApprovalReassignmentPanel.vue`
- `views/pms/cutover/components/CutoverAssessmentPanel.vue`
- `views/pms/cutover/components/CutoverBusinessEntry.vue`
- `views/pms/cutover/components/CutoverCreateWizard.vue`
- `views/pms/cutover/components/CutoverDashboardKpis.vue`
- `views/pms/cutover/components/CutoverExternalBulletinField.vue`
- `views/pms/cutover/components/CutoverFrozenAnswer.vue`
- `views/pms/cutover/components/CutoverLeadTimeComplianceCard.vue`
- `views/pms/cutover/components/CutoverProjectContextCard.vue`
- `views/pms/cutover/components/CutoverSchemaInput.vue`
- `views/pms/cutover/components/CutoverSupportArrangements.vue`
- `views/pms/cutover/components/CutoverTaskDevices.vue`
- `views/pms/cutover/components/CutoverTaskTable.vue`
- `views/pms/cutover/components/CutoverWorkbenchSteps.vue`
- `views/pms/cutover/configuration/components/CutoverBindingConditionsEditor.vue`
- `views/pms/cutover/configuration/components/CutoverConfigurationEditor.vue`
- `views/pms/cutover/configuration/components/CutoverExternalSourceEditor.vue`
- `views/pms/cutover/configuration/components/CutoverRiskMatrixEditor.vue`
- `views/pms/cutover/configuration/components/CutoverSchemaEditor.vue`
- `views/pms/cutover/configuration/components/CutoverSchemaNodeEditor.vue`
- `views/pms/cutover/configuration/components/CutoverSurveyMatrixEditor.vue`
- `views/pms/cutover/configuration/components/cutoverMatrix.ts`
- `views/pms/cutover/configuration/index.vue`
- `views/pms/cutover/cutoverTestHarness.ts`
- `views/pms/cutover/cutoverTestSetup.ts`
- `views/pms/cutover/project-coverage/index.vue`
- `views/pms/cutover/project-tasks/index.vue`
- `views/pms/cutover/shared/cutoverChecklistValidation.ts`
- `views/pms/cutover/shared/cutoverDeviceSource.ts`
- `views/pms/cutover/shared/cutoverFieldLabels.ts`
- `views/pms/cutover/shared/cutoverPlanDraft.ts`
- `views/pms/cutover/shared/cutoverPlanSurvey.ts`
- `views/pms/cutover/shared/cutoverPlanValidation.ts`
- `views/pms/cutover/shared/cutoverSubmittedPlanDownload.ts`
- `views/pms/cutover/shared/cutoverTaskInteraction.ts`
- `views/pms/cutover/shared/taskPresentation.ts`
- `views/pms/cutover/task-create/route.vue`
- `views/pms/cutover/task-detail/route.vue`
- `views/pms/cutover/workbench/index.vue`
- `views/pms/delivery-business/requirement-analysis/entity/CompareDrawer.vue`
- `views/pms/delivery-business/requirement-analysis/entity/EntityForm.vue`
- `views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue`
- `views/pms/delivery-business/requirement-analysis/entity/RequirementBusinessTable.vue`
- `views/pms/delivery-business/requirement-analysis/entity/RequirementLoggingChoices.vue`
- `views/pms/delivery-business/requirement-analysis/entity/RevisionDrawer.vue`
- `views/pms/delivery-business/requirement-analysis/entity/RevisionFiles.vue`
- `views/pms/delivery-business/requirement-analysis/entity/entityForm.ts`
- `views/pms/delivery-business/requirement-analysis/entity/presentation.ts`
- `views/pms/delivery-business/site-survey/SiteSurveyDynamicForm.vue`
- `views/pms/delivery-business/site-survey/SurveyApplicationDialog.vue`
- `views/pms/delivery-business/site-survey/SurveyMaterialExchangeDialog.vue`
- `views/pms/delivery-business/site-survey/SurveyMaterialSelector.vue`
- `views/pms/delivery-business/site-survey/SurveyOutsourceShortcut.vue`
- `views/pms/delivery-business/site-survey/SurveyPreparationBranches.vue`
- `views/pms/delivery-business/site-survey/SurveyProcurementLinks.vue`
- `views/pms/delivery-business/site-survey/SurveyProjectEndDate.vue`
- `views/pms/delivery-business/site-survey/index.vue`
- `views/pms/delivery-business/site-survey/siteSurveyExecutionShortcut.ts`
- `views/pms/delivery-business/site-survey/siteSurveyForm.ts`
- `views/pms/delivery-business/site-survey/siteSurveyOutsource.ts`
- `views/pms/delivery-business/site-survey/surveyActionContext.ts`
- `views/pms/delivery-business/site-survey/surveyBranchApplications.ts`
- `views/pms/delivery-business/site-survey/surveyBusinessForm.ts`
- `views/pms/delivery-business/site-survey/surveyPreparationBranches.ts`
- `views/pms/delivery-business/site-survey/surveyReceiptIntent.ts`
- `views/pms/engineering/announcement-check/index.vue`
- `views/pms/engineering/announcement/index.vue`
- `views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts`
- `views/pms/engineering/arrival-acceptance/components/ArrivalAcceptanceForm.vue`
- `views/pms/engineering/arrival-acceptance/components/ArrivalDifferencePanel.vue`
- `views/pms/engineering/arrival-acceptance/components/ArrivalLineEditor.vue`
- `views/pms/engineering/arrival-acceptance/index.vue`
- `views/pms/engineering/authorization/index.vue`
- `views/pms/engineering/configuration/ConfigurationLogImportDialog.vue`
- `views/pms/engineering/configuration/ManualCollectionDialog.vue`
- `views/pms/engineering/deliverable/index.vue`
- `views/pms/engineering/deliverable/summary/index.vue`
- `views/pms/engineering/doc-template/index.vue`
- `views/pms/engineering/form-instance/index.vue`
- `views/pms/engineering/form-template/index.vue`
- `views/pms/engineering/installation/ProjectInstallationWorkbench.vue`
- `views/pms/engineering/installation/index.vue`
- `views/pms/engineering/issue/index.vue`
- `views/pms/engineering/joint-test/resolveLogTask.ts`
- `views/pms/engineering/joint-test/with-device-logs.vue`
- `views/pms/engineering/material-exch/MaterialDevicePicker.vue`
- `views/pms/engineering/material-exch/ProductOfficialSelect.vue`
- `views/pms/engineering/recordNaming.ts`
- `views/pms/engineering/requirement/index.vue`
- `views/pms/engineering/resource/index.vue`
- `views/pms/engineering/risk/index.vue`
- `views/pms/engineering/site-survey/SiteSurveyDynamicForm.vue`
- `views/pms/engineering/site-survey/SurveyMaterialSelector.vue`
- `views/pms/engineering/site-survey/SurveyOutsourceShortcut.vue`
- `views/pms/engineering/site-survey/SurveyProcurementLinks.vue`
- `views/pms/engineering/site-survey/SurveyProjectEndDate.vue`
- `views/pms/engineering/site-survey/index.vue`
- `views/pms/engineering/site-survey/siteSurveyExecutionShortcut.ts`
- `views/pms/engineering/site-survey/siteSurveyForm.ts`
- `views/pms/engineering/site-survey/siteSurveyOutsource.ts`
- `views/pms/engineering/site-survey/surveyActionContext.ts`
- `views/pms/engineering/site-survey/surveyBusinessForm.ts`
- `views/pms/engineering/solution-reviewed/CustomerSolutionDocumentPicker.vue`
- `views/pms/engineering/solution-reviewed/ReviewDetail.vue`
- `views/pms/engineering/solution-reviewed/SolutionDocMetaForm.vue`
- `views/pms/engineering/solution-reviewed/SolutionReviewChapterForm.vue`
- `views/pms/engineering/solution-reviewed/SolutionTieredReviewDialog.vue`
- `views/pms/engineering/solution-reviewed/index.vue`
- `views/pms/engineering/solution-reviewed/saveCustomerSolutionDocuments.ts`
- `views/pms/engineering/solution/ResourceConfirmationEditor.vue`
- `views/pms/engineering/solution/SolutionChapterForm.vue`
- `views/pms/engineering/solution/SolutionEquipmentTable.vue`
- `views/pms/engineering/solution/index.vue`
- `views/pms/engineering/solution/prepareGeneratedSolutionWord.ts`
- `views/pms/engineering/solution/resourceConfirmation.ts`
- `views/pms/engineering/solution/saveGeneratedSolutionHtml.ts`
- `views/pms/engineering/solution/solutionEquipment.ts`
- `views/pms/engineering/stage-plan-rule/index.vue`
- `views/pms/engineering/stage-plan/ApprovalDetail.vue`
- `views/pms/engineering/stage-plan/index.vue`
- `views/pms/engineering/training/TrainingPrintPreview.vue`
- `views/pms/engineering/training/TrainingPrintTemplates.vue`
- `views/pms/engineering/training/index.vue`
- `views/pms/engineering/training/public-confirm.vue`
- `views/pms/engineering/training/trainingPrintForm.ts`
- `views/pms/inspection/components/InspectionAnnotationPanel.vue`
- `views/pms/inspection/components/InspectionAttachmentPanel.vue`
- `views/pms/inspection/components/InspectionExecutionPanel.vue`
- `views/pms/inspection/components/InspectionIssuePanel.vue`
- `views/pms/inspection/components/InspectionOnlinePanel.vue`
- `views/pms/inspection/components/InspectionReportPanel.vue`
- `views/pms/inspection/components/InspectionSteps.vue`
- `views/pms/inspection/shared/interaction.ts`
- `views/pms/inspection/task-detail/index.vue`
- `views/pms/platform/businessEntity/index.vue`
- `views/pms/platform/dynamic-form/components/PmsFileArtifactField.vue`
- `views/pms/platform/dynamic-form/components/dynamicFormCodec.ts`
- `views/pms/platform/dynamic-form/components/dynamicFormRuntime.ts`
- `views/pms/platform/dynamic-form/components/registerDynamicFormComponents.ts`
- `views/pms/platform/dynamic-form/components/runtimeTestHarness.ts`
- `views/pms/platform/dynamic-form/components/usePmsFileArtifactDesignerRule.ts`
- `views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue`
- `views/pms/platform/dynamic-form/template/DynamicFormTemplateEditor.vue`
- `views/pms/platform/processDefinition/index.vue`
- `views/pms/platform/resultSubscription/index.vue`
- `views/pms/project/inheritance/detail/ProjectFlowStageNavigation.vue`
- `views/pms/project/inheritance/detail/StageApprovalPanel.vue`
- `views/pms/project/inheritance/detail/StageBusinessPanel.vue`
- `views/pms/project/inheritance/detail/TaskApprovalForm.vue`
- `views/pms/project/inheritance/detail/TaskApprovalPanel.vue`
- `views/pms/project/inheritance/detail/flowTaskPaging.ts`
- `views/pms/project/inheritance/projects/CustomerCorrectionDialog.vue`
- `views/pms/project/inheritance/projects/customerSelection.ts`
- `views/pms/project/inheritance/tree/ProjectTreePanel.vue`
- `views/pms/project/inheritance/tree/projectTree.ts`
- `views/pms/project/normal-closure/index.vue`
- `views/pms/project/project-closure/index.vue`
- `views/pms/project/project-master-detail/components/BusinessFlowNav.vue`
- `views/pms/project/project-master-detail/components/ChildTemplatePicker.vue`
- `views/pms/project/project-master-detail/components/PreparationReadinessDrawer.vue`
- `views/pms/project/project-master-detail/components/PreparationWaiverDrawer.vue`
- `views/pms/project/project-master-detail/components/ProjectApprovalDrawer.vue`
- `views/pms/project/project-master-detail/components/ProjectArrivalReceiptPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectAttributePanel.vue`
- `views/pms/project/project-master-detail/components/ProjectAuthorizationPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectClosureGuardPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectContractScopeSyncPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectCurrentPlanTag.vue`
- `views/pms/project/project-master-detail/components/ProjectCustomerExtensionDrawer.vue`
- `views/pms/project/project-master-detail/components/ProjectCustomerOverview.vue`
- `views/pms/project/project-master-detail/components/ProjectDeliverableDialog.vue`
- `views/pms/project/project-master-detail/components/ProjectDeliverablesPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectDeviceLogsPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectDurationFormDrawer.vue`
- `views/pms/project/project-master-detail/components/ProjectDurationHistoryDrawer.vue`
- `views/pms/project/project-master-detail/components/ProjectDurationPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectEndUsersWorkbench.vue`
- `views/pms/project/project-master-detail/components/ProjectExecutionHistory.vue`
- `views/pms/project/project-master-detail/components/ProjectFlowNavigation.vue`
- `views/pms/project/project-master-detail/components/ProjectFlowPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectGovernancePanel.vue`
- `views/pms/project/project-master-detail/components/ProjectMemberForm.vue`
- `views/pms/project/project-master-detail/components/ProjectMemberPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectMemberServiceFields.vue`
- `views/pms/project/project-master-detail/components/ProjectNodeWorkbenchDrawer.vue`
- `views/pms/project/project-master-detail/components/ProjectNormalClosurePanel.vue`
- `views/pms/project/project-master-detail/components/ProjectParentScopePanel.vue`
- `views/pms/project/project-master-detail/components/ProjectPlanEditor.vue`
- `views/pms/project/project-master-detail/components/ProjectPreparationPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectProgressPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectProgressSummaryPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectReworkPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectServiceManagerPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectSplitPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectSplitTemplatePicker.vue`
- `views/pms/project/project-master-detail/components/ProjectSplitWizard.vue`
- `views/pms/project/project-master-detail/components/ProjectStageGatePanel.vue`
- `views/pms/project/project-master-detail/components/ProjectStageStatusPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectTaskDetailsEditor.vue`
- `views/pms/project/project-master-detail/components/ProjectTaskPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectTaskTree.vue`
- `views/pms/project/project-master-detail/components/ProjectTemplateMatchHistoryPanel.vue`
- `views/pms/project/project-master-detail/components/ProjectTreePanel.vue`
- `views/pms/project/project-master-detail/components/RequirementAnalysisCompareDrawer.vue`
- `views/pms/project/project-master-detail/components/RequirementAnalysisDynamicForm.vue`
- `views/pms/project/project-master-detail/components/RequirementAnalysisHistoryDrawer.vue`
- `views/pms/project/project-master-detail/components/SchedulePlanningTable.vue`
- `views/pms/project/project-master-detail/components/StageGateProcessPanel.vue`
- `views/pms/project/project-master-detail/components/StageGateResultsPanel.vue`
- `views/pms/project/project-master-detail/components/StageProgressRail.vue`
- `views/pms/project/project-master-detail/components/backwardDuration.ts`
- `views/pms/project/project-master-detail/components/businessModules.ts`
- `views/pms/project/project-master-detail/components/durationEntry.ts`
- `views/pms/project/project-master-detail/components/formCreateKeyboardRows.ts`
- `views/pms/project/project-master-detail/components/preparationInteraction.ts`
- `views/pms/project/project-master-detail/components/project-flow.ts`
- `views/pms/project/project-master-detail/components/project-task-update.ts`
- `views/pms/project/project-master-detail/components/projectApprovalDetail.ts`
- `views/pms/project/project-master-detail/components/requirementAnalysisInteraction.ts`
- `views/pms/project/project-master-detail/components/schedulePresentation.ts`
- `views/pms/project/project-master-detail/components/useProjectContractScopeSync.ts`
- `views/pms/project/project-master-detail/components/useProjectSplitWorkspace.ts`
- `views/pms/project/project-master-detail/taskViewPrefetch.ts`
- `views/pms/project/project-templates/ApprovalDefinitionSelect.vue`
- `views/pms/project/project-templates/ChildProjectWaitEditor.vue`
- `views/pms/project/project-templates/DecisionTableConditionEditor.vue`
- `views/pms/project/project-templates/DecisionTableEditor.vue`
- `views/pms/project/project-templates/DefinitionSelect.vue`
- `views/pms/project/project-templates/DeliverableRuleDialog.vue`
- `views/pms/project/project-templates/ExecutionConfigurationEditor.vue`
- `views/pms/project/project-templates/GateReferencesEditor.vue`
- `views/pms/project/project-templates/NodeDeliverableChecklist.vue`
- `views/pms/project/project-templates/OperationContractEditor.vue`
- `views/pms/project/project-templates/RelativeTimeConditionEditor.vue`
- `views/pms/project/project-templates/RuleDecisionDesigner.vue`
- `views/pms/project/project-templates/RuleEditor.vue`
- `views/pms/project/project-templates/RulePredicateEditor.vue`
- `views/pms/project/project-templates/RuleSimulationPanel.vue`
- `views/pms/project/project-templates/RuleSlotEditor.vue`
- `views/pms/project/project-templates/RuleTreeGroup.vue`
- `views/pms/project/project-templates/TaskBindingEditor.vue`
- `views/pms/project/project-templates/TemplateContentEditor.vue`
- `views/pms/project/project-templates/TemplateFlowCanvas.vue`
- `views/pms/project/project-templates/TemplateMatchDiagnostics.vue`
- `views/pms/project/project-templates/decisionTableModel.ts`
- `views/pms/project/project-templates/editorModel.ts`
- `views/pms/project/project-templates/lifecycleStageOptions.ts`
- `views/pms/project/project-templates/operationContract.ts`
- `views/pms/project/project-templates/relativeTimeModel.ts`
- `views/pms/project/project-templates/ruleBusinessSources.ts`
- `views/pms/project/project-templates/ruleNativeOptions.ts`
- `views/pms/project/project-templates/ruleTreeModel.ts`
- `views/pms/project/project-templates/templateCanvasModel.ts`
- `views/pms/project/project-templates/versionRuleModel.ts`
- `views/pms/project/projects/ProjectOverdueTags.vue`
- `views/pms/project/projects/ProjectStatusTag.vue`
- `views/pms/project/projects/contractSelection.ts`
- `views/pms/project/projects/projectStatus.ts`
- `views/pms/project/projects/submissionIdempotency.ts`
- `views/pms/project/projects/useCreationTemplateMatch.ts`
