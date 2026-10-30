# 全项目页面及弹窗差异裁决

原源提交：`4d149f34faec4b66e93373794693d6bbd738b716`。原目标提交：`c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a`。

范围来自全部PMS页面、全部公共组件，以及源/原目标/当前三侧递归导入。没有按“原来相同的32文件”筛选。文件相同不代表运行所有状态已验收。

本报告附每个片段原差异、当前差异和当前行Git来源。`LOCAL_UNCOMMITTED`只证明本地未提交，不能证明来自测试修复；文件历史提交也不自动证明业务裁决。纯新增及其必需接口适配与有证据的测试修复分开。

原源本就不存在、原目标重复新增的需求分析旧页单列保留废弃；没有恢复双写路径。数据库、权限、历史和审批实例本轮均未修改。

## 001 api/pms/acceptance/deliverable-checklist/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -8,6 +8,10 @@

裁决：保留文件兼容或新增字段。当前行来源：1a93fad14aa0cadcf9300535aa7b9b6617b9c3aa。

```diff
@@ -8,6 +8,10 @@
   acceptanceId?: number
   deliverableType?: string
   deliverableUrl?: string
+  signedFlag?: boolean
+  validFlag?: boolean
+  submittedDate?: Date
+  attachmentUrl?: string
   checkUserId?: number
   checkTime?: Date
   checkResult?: string
```

## 002 api/pms/acceptance/project-deliverable/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -1,5 +1,5 @@

裁决：保留文件兼容或新增字段。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -1,5 +1,5 @@
 import request from '@/config/axios'
-import type { FileBusinessKey } from '@/api/pms/platform/file'
+import type { FileId, FileBusinessKey } from '@/api/pms/platform/file'
 
 // P06R 统一交付件承接后的 ACC 兼容契约：存储在平台 plt_delivery_*，
 // FileSelection 只带 referenceId（上传返回引用，与统一材料登记一致），历史行渲染统一材料。
```

### H02 @@ -16,7 +16,7 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -16,7 +16,7 @@
   observationVersion?: string
   formedAt: string
 }
-export interface FileSelection { referenceId: number }
+export interface FileSelection { referenceId: FileId }
 export interface MaterialLine {
   fileKey?: FileBusinessKey
   id: number
```

## 003 api/pms/asset/device/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留纯新增功能**。新增软件版本、发货事实、主附SN、来源历史和续保API/字段；原设备API保留。
- 证据及验证边界：设备Owner现有接口。

### H01 @@ -1,4 +1,66 @@

裁决：保留纯新增功能。当前行来源：60344c85c0c4c0bd52bd659690c0d7feed91c00a, d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -1,4 +1,66 @@
 import request from '@/config/axios'
+
+export interface SoftwareVersionValues {
+  conpVersion?: string | null
+  conpType?: string | null
+  conpSeries?: string | null
+  conpMark?: string | null
+  bootVersion?: string | null
+  cpldVersion?: string | null
+  pcbVersion?: string | null
+  customized?: boolean | null
+  releaseDate?: string | null
+  versionDesc?: string | null
+}
+export interface SoftwareVersionCurrent {
+  values: SoftwareVersionValues | null
+  version: number
+  recordedAt?: string | number | null
+  canMaintain: boolean
+}
+export interface SoftwareVersionHistory {
+  record: SoftwareVersionValues & {
+    id: number
+    projectId?: number | null
+    previousRecordId?: number | null
+    recordedAt?: string | number | null
+    dataSource: string
+    deviceSn?: string | null
+    contractNo?: string | null
+    productCode?: string | null
+    changeBatchId?: number | null
+    parsedFactId?: number | null
+    executeTime?: string | number | null
+    dataState?: number | null
+    customInfo?: string | null
+    creator?: string | null
+    createTime?: string | number | null
+    updater?: string | null
+    updateTime?: string | number | null
+  }
+  previous: SoftwareVersionValues | null
+  legacyAttributes: Record<string, string | number | null>
+}
+export const getSoftwareVersionCurrent = (deviceId: number | string) =>
+  request.get<SoftwareVersionCurrent>({ url: `/api/v1/pms/devices/${deviceId}/software-version` })
+export const getSoftwareVersionHistory = (deviceId: number | string, params: PageParam) =>
+  request.get<{ list: SoftwareVersionHistory[]; total: number }>({
+    url: `/api/v1/pms/devices/${deviceId}/software-version/history`,
+    params
+  })
+export const registerSoftwareVersion = (
+  deviceId: number | string,
+  data: {
+    projectId: number | string
+    expectedVersion: number
+    requestKey: string
+    values: SoftwareVersionValues
+  }
+) =>
+  request.post<{ recordId: number; replayed: boolean }>({
+    url: `/api/v1/pms/devices/${deviceId}/software-version/registrations`,
+    data
+  })
 
 export type DeviceSyncStatus = 'FRESH' | 'STALE' | 'FAILED' | 'PENDING_MAPPING' | 'NOT_AVAILABLE'
```

### H02 @@ -155,6 +217,37 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -155,6 +217,37 @@
 }
 
 export interface DeviceWarrantyRecordVO {
+  combinedDeviceSn?: string
+  productCode?: string
+  productName?: string
+  productDescription?: string
+  gradeName?: string
+  shipmentTime?: string
+  warrantyFlag?: string
+  sourceWarrantyStartTime?: string
+  sourceWarrantyEndTime?: string
+  originalWarrantyStartTime?: string
+  originalWarrantyEndTime?: string
+  remainingDaysAtSource?: number
+  originalRemainingDaysAtSource?: number
+  contractType?: string
+  contractTypeName?: string
+  projectName?: string
+  customerName?: string
+  finalCustomerName?: string
+  agentName?: string
+  officeCode?: string
+  officeName?: string
+  marketCode?: string
+  marketName?: string
+  systemCode?: string
+  systemName?: string
+  secondarySn?: string
+  secondaryItem?: string
+  secondaryProductName?: string
+  secondaryProductDescription?: string
+  sourceSyncTime?: string
+  sourceShipmentKey?: string
   id: number
   deviceSn: string
   warrantyStartDate?: string
```

### H03 @@ -262,3 +355,119 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -262,3 +355,119 @@
     data,
     headers: { 'If-Match': String(expectedVersion), 'Idempotency-Key': idempotencyKey }
   })
+
+export interface DeviceShipmentVO {
+  id: number | string
+  deviceSn: string
+  packageNo?: string
+  shipmentTime?: string
+  contractNo?: string
+  legacyPackageKey?: string
+  rmaRelatedSn?: string
+  rmaMarked?: number
+  orderNo?: string
+  lineNo?: string
+  productCode?: string
+  productModel?: string
+  productName?: string
+  secondaryProductModel?: string
+  secondaryProductName?: string
+  sourceUpdatedAt?: string
+  secondarySn?: string
+  secondaryItem?: string
+  internalSerialNo?: string
+  isRma?: number
+  profitCenter?: string
+  soleAgentSuffix?: string
+  syncTime?: string
+  uuid?: string
+  warrantyStartDate?: string
+  warrantyMonths?: number
+  rmaNo?: string
+}
+export const getDeviceShipments = (
+  deviceId: number,
+  params: { pageNo: number; pageSize: number }
+): Promise<{ list: DeviceShipmentVO[]; total: number }> =>
+  request.get({ url: '/pms/asset/devices/' + deviceId + '/shipments', params })
+
+export interface DeviceContractRelationship {
+  id: number | string
+  sourceDeviceSn: string
+  targetDeviceSn: string
+  sourceItem?: string | null
+  targetItem?: string | null
+  contractNo?: string | null
+  effectiveFrom?: string | number | null
+  effectiveTo?: string | number | null
+  createTime?: string | number | null
+  updateTime?: string | number | null
+}
+export const getDeviceContractRelationships = (
+  deviceId: number,
+  params: { pageNo: number; pageSize: number }
+): Promise<{ list: DeviceContractRelationship[]; total: number }> =>
+  request.get({ url: '/api/v1/pms/device-source-relationships', params: { ...params, deviceId } })
+
+export interface DeviceSourceSoftwareObservation {
+  sourceId: number | string
+  sourceProjectId: number | string
+  logId?: number | string
+  contractNo?: string
+  itemCode?: string
+  barCode?: string
+  conp?: string
+  conpType?: string
+  conpSeries?: string
+  conpMark?: string
+  conpBak?: string
+  conpChange?: number
+  cpld?: string
+  cpldBak?: string
+  cpldChange?: number
+  boot?: string
+  bootBak?: string
+  bootChange?: number
+  pcb?: string
+  pcbBak?: string
+  pcbChange?: number
+  executeTime?: string
+  datastate?: number
+  sourceCreatedAt?: string
+  sourceCreatedBy?: string
+  sourceUpdatedAt?: string
+  sourceUpdatedBy?: string
+  customInfo?: string
+}
+export interface DeviceSourceFactoryVersion {
+  deviceSn: string
+  conpVersion?: string
+  bootVersion?: string
+  cpldVersion?: string
+  pcbVersion?: string
+}
+export interface DeviceSourceVersionRecord<T> {
+  id: number | string
+  projectId: number | string
+  sourceProjectKey: string
+  sourceSystem: string
+  sourceKey: string
+  sourceVersion?: string
+  sourceReadAt?: string
+  sourceChecksum: string
+  values: T
+}
+export const getDeviceSourceSoftwareObservations = (deviceId: number, params: PageParam) =>
+  request.get<PageResult<DeviceSourceVersionRecord<DeviceSourceSoftwareObservation>[]>>({
+    url: '/api/v1/pms/devices/' + deviceId + '/source-software-observations',
+    params
+  })
+export const getDeviceSourceFactoryVersions = (deviceId: number, params: PageParam) =>
+  request.get<
+    PageResult<
+      { id: number | string; projectId: number | string; values: DeviceSourceFactoryVersion }[]
+    >
+  >({
+    url: '/api/v1/pms/devices/' + deviceId + '/source-factory-versions',
+    params
+  })
```

## 004 api/pms/asset/warranty-renewal.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增软件版本、发货事实、主附SN、来源历史和续保API/字段；原设备API保留。
- 证据及验证边界：设备Owner现有接口。

### H01 @@ -0,0 +1,26 @@

裁决：保留纯新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -0,0 +1,26 @@
+import request from '@/config/axios'
+
+export interface WarrantyRenewalRecord {
+  id: number
+  deviceSn?: string
+  warrantyContractNo?: string
+  warrantyGrade?: string
+  warrantyStartDate?: string
+  warrantyEndDate?: string
+  warrantyMonths?: number
+  extended?: boolean | null
+  isLatest?: boolean | null
+  remark?: string
+}
+
+export const getWarrantyRenewals = (params: {
+  projectId?: number
+  deviceId?: number
+  sn?: string
+  pageNo: number
+  pageSize: number
+}) =>
+  request.get<{ list: WarrantyRenewalRecord[]; total: number }>({
+    url: '/api/v1/pms/asset/warranty-renewals/page',
+    params
+  })
```

## 005 api/pms/commerce/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：5；当前片段：5。
- 裁决：**保留新增商务事实及契约**。新增订单数量、确认标记、合同关系和来源指纹；办事处字段重命名对应当前服务端，非界面任意改名。
- 证据及验证边界：CommerceDeliveryScopeQueryService; ProjectManualCreationApplicationService。

### H01 @@ -124,20 +124,25 @@

裁决：保留新增商务事实及契约。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -124,20 +124,25 @@
   lineNo: string
   productCode: string
   productDesc?: string
+  contractNo?: string | null
+  orderQuantity?: number | null
+  unitCode?: string | null
   allocatedQuantity: number
+  allocationConfirmed: boolean
+  deliveredQuantity?: number | null
+  openQuantity?: number | null
   scopeStatus: string
   allocationVersion: number
   allocationSource: string
   changeReason?: string
-  officeDepartmentId: number
-  officeDepartmentCode: string
-  officeDepartmentName: string
-  officeDepartmentVersion: number
+  departmentId: number
+  departmentCode: string
+  departmentName: string
+  departmentVersion: number
   effectiveFrom: string
   effectiveTo?: string
   version: number
   details: DeliveryScopeDetailRespVO[]
-  contractNo?: string
 }
 
 export interface DeliveryScopePageReqVO extends PageParam {
```

### H02 @@ -222,6 +227,7 @@

裁决：保留新增商务事实及契约。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -222,6 +227,7 @@
 
 /** 合同主档单入口的项目创建取值预览（合同→订单→执行单链 + 老系统规则建议值）。 */
 export interface ContractCreationSourceRespVO {
+  sourceFingerprint: string
   contract: ContractRespVO
   orders: Array<{
     id: number
```

### H03 @@ -262,6 +268,7 @@

裁决：保留新增商务事实及契约。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -262,6 +268,7 @@
   }>
   lineExecutionNos: string[]
   resolved: {
+    salesOrderId?: number
     projectName?: string
     customerCode?: string
     customerName?: string
```

### H04 @@ -284,8 +291,8 @@

裁决：保留新增商务事实及契约。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -284,8 +291,8 @@
   }
 }
 
-export const getContractCreationSource = (id: number) =>
-  request.get<ContractCreationSourceRespVO>({ url: `${baseUrl}/contracts/${id}/creation-source` })
+export const getContractCreationSource = (id: number, salesOrderId?: number) =>
+  request.get<ContractCreationSourceRespVO>({ url: `${baseUrl}/contracts/${id}/creation-source`, params: { salesOrderId } })
 
 export const relateContractProject = (
   contractId: number,
```

### H05 @@ -336,6 +343,31 @@

裁决：保留新增商务事实及契约。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -336,6 +343,31 @@
     headers: { 'If-Match': String(allocationVersion), 'Idempotency-Key': idempotencyKey }
   })
 
+export interface ProjectContractRelation {
+  id: string | number
+  projectId: string | number
+  contractId: string | number
+  contractNo: string
+  contractName?: string
+  companyCode?: string
+  companyName?: string
+  relationRole: string
+  status: string
+  effectiveFrom?: string | number[] | null
+  effectiveTo?: string | number[] | null
+  creator?: string | null
+  createTime?: string | number[] | null
+  updater?: string | null
+  updateTime?: string | number[] | null
+  version: number
+}
+
+export const getProjectContractRelations = (
+  projectId: string | number,
+  params: { pageNo: number; pageSize: number }
+): Promise<{ list: ProjectContractRelation[]; total: number }> =>
+  request.get({ url: `/api/v1/pms/contracts/project/${projectId}/relations`, params })
+
 export const releaseDeliveryScope = (
   id: number,
   data: DeliveryScopeChangeReqVO,
```

## 006 api/pms/customer/contacts/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增历史及其保护**。新增来源联系单位、人员编码、电话和时间；项目历史联系人独立可读但不可修改删除，不将缺客户主档当作无历史联系人。
- 证据及验证边界：contacts既有测试；历史只读契约。

### H01 @@ -17,6 +17,13 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -17,6 +17,13 @@
 }
 
 export interface ContactVO extends CustomerContactVO {
+  projectPartyId?: number | string | null
+  projectPartyName?: string
+  contactCode?: string | null
+  registrationSource?: string | null
+  effectiveFrom?: string | number | null
+  effectiveTo?: string | number | null
+  historicalReadonly?: boolean
   customerName?: string
   projectId?: number
   customerContactId?: number
```

## 007 api/pms/engineering/construction-plan/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：4；当前片段：4。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -1,4 +1,5 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -1,4 +1,5 @@
 import request from '@/config/axios'
+import type { FileId } from '@/api/pms/platform/file'
 
 export type DurationCalculationBasis = 'DATE_RANGE' | 'DURATION_FROM_START'
 export type DurationChangeStatus =
```

### H02 @@ -33,7 +34,7 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -33,7 +34,7 @@
   reasonType: string
   reasonDetail?: string
   customerEvidenceRequired: boolean
-  customerEvidenceFileId?: number
+  customerEvidenceFileId?: FileId
   customerEvidenceFileVersion?: number
   customerEvidenceReferenceKey?: string
   processDefinitionKey?: string
```

### H03 @@ -80,7 +81,7 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -80,7 +81,7 @@
   expectedProjectVersion: number
   reasonType: string
   reasonDetail?: string
-  customerEvidenceFileId?: number
+  customerEvidenceFileId?: FileId
   customerEvidenceFileVersion?: number
   customerEvidenceReferenceKey?: string
 }
```

### H04 @@ -93,7 +94,7 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -93,7 +94,7 @@
   durationDays?: number | null
   reasonType?: string
   reasonDetail?: string | null
-  customerEvidenceFileId?: number | null
+  customerEvidenceFileId?: FileId | null
   customerEvidenceFileVersion?: number | null
   customerEvidenceReferenceKey?: string | null
 }
```

## 008 api/pms/engineering/installation-source-history.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -0,0 +1,60 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,60 @@
+import request from '@/config/axios'
+export interface InstallationSourceDetails {
+  sourceProductCode?: string
+  sourceProductModel?: string
+  sourceProductName?: string
+  sourceReceiverName?: string
+  sourceExpressNo?: string
+  sourceCarrierName?: string
+  sourceShipmentTime?: string
+  sourceContractNo?: string
+  changedProjectKey?: string
+  changedContractNo?: string
+  transferProjectKey?: string
+  transferContractNo?: string
+  sourceTransferFlag?: number
+  sourceCreatedAt?: string
+  sourceCreatedBy?: string
+  sourceUpdatedAt?: string
+  sourceUpdatedBy?: string | null
+}
+export interface HistoricalInstallationSummary {
+  id: number | string
+  projectId: number | string
+  equipmentId: number | string
+  code: string
+  sourceRecordKey: string
+  sourceDeviceSn?: string
+  sourceProjectKey?: string
+  sourceHistory: boolean
+  status?: number
+  installLocation?: string
+  addressId?: number | string
+  addressVersion?: number | string
+  locationResolutionStatus?: string
+  currentEquipmentId?: number | string
+  effectiveFrom?: string
+  effectiveTo?: string
+  version?: number | string
+}
+export interface InstallationSourceRecord {
+  id: number | string
+  projectId: number | string
+  equipmentId: number | string
+  sourceRecordKey: string
+  sourceDeviceSn?: string
+  sourceProjectKey?: string
+  sourceId: number | string
+  sourceProjectId?: number | string
+  installLocation?: string
+  addressId?: number | string
+  addressVersion?: number | string
+  locationResolutionStatus: string
+  values: InstallationSourceDetails
+  historicalInstallation?: HistoricalInstallationSummary | null
+}
+export const getInstallationSourceRecords = (params: PageParam & { equipmentId: number }) =>
+  request.get<PageResult<InstallationSourceRecord[]>>({
+    url: '/api/v1/pms/installation-source-records',
+    params
+  })
```

## 009 api/pms/engineering/preparation/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -1,4 +1,5 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -1,4 +1,5 @@
 import request from '@/config/axios'
+import type { FileId } from '@/api/pms/platform/file'
 
 export interface CursorPage<T> {
   items: T[]
```

### H02 @@ -86,7 +87,7 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -86,7 +87,7 @@
 }
 
 export interface EvidenceReference {
-  artifactId: number
+  artifactId: FileId
   versionNo: number
   referenceKey: string
   fileFactVersion: {
```

### H03 @@ -94,7 +95,7 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -94,7 +95,7 @@
     referenceVersion: number
     availabilityVersion: number
   }
-  scopeVersion: number
+  scopeVersion: FileId
 }
 
 export interface AssignmentCandidateVO {
```

## 010 api/pms/engineering/requestTime.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留历史兼容**。可选历史申请时间为空时不臆造时间，不放宽其他必填字段。
- 证据及验证边界：requestTime既有测试。

### H01 @@ -1,4 +1,6 @@

裁决：保留历史兼容。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -1,4 +1,6 @@
-export const withRequestTimestamp = <T extends { applyTime: string | number }>(data: T) => {
+export const withRequestTimestamp = <T extends { applyTime?: string | number | null }>(data: T) => {
+  // Native historical rows may omit this optional server field; do not invent an application time.
+  if (data.applyTime == null) return { ...data, applyTime: undefined }
   const applyTime = typeof data.applyTime === 'string'
     ? new Date(data.applyTime.replace(' ', 'T')).getTime() : data.applyTime
   if (!Number.isFinite(applyTime)) throw new Error('申请时间无效')
```

## 011 api/pms/engineering/requirement-analysis/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -1,4 +1,5 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -1,4 +1,5 @@
 import request from '@/config/axios'
+import type { FileId } from '@/api/pms/platform/file'
 import type { DynamicFormFileFactVO, JsonObject } from '@/api/pms/platform/dynamic-form'
 import type { StageExecutionContext } from '@/api/pms/project/stage-business'
 import type { TaskExecutionContext } from '@/api/pms/project/task-business'
```

### H02 @@ -27,9 +28,9 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -27,9 +28,9 @@
 
 /** @deprecated 固定章节附件快照已由动态表单fileReferences替代。 */
 export interface RequirementAnalysisAttachmentVO {
-  artifactId: number
+  artifactId: FileId
   versionNo: number
-  referenceId?: number
+  referenceId?: FileId
   referenceKey: string
   name?: string
   sizeBytes?: number
```

### H03 @@ -41,7 +42,7 @@

裁决：保留文件兼容或新增字段。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -41,7 +42,7 @@
     referenceVersion: number
     availabilityVersion: number
   }
-  scopeVersion: number
+  scopeVersion: FileId
 }
 
 /** @deprecated SOL与PLT不再维护附件同步双真值。 */
```

## 012 api/pms/integration/base-sync.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -0,0 +1,34 @@

裁决：保留纯新增及其契约配套。当前行来源：b143cfe890dda1ef51f21d34abb75a5b5fd43ee9。

```diff
@@ -0,0 +1,34 @@
+import request from '@/config/axios'
+import type { Id } from './index'
+
+export interface BaseSyncTask {
+  code: string
+  name: string
+}
+export interface Registration {
+  id: Id
+  connectionId: Id
+  taskCode: string
+  batchSize: number
+  maxRows: number
+  activeRunId?: Id | null
+}
+export interface SyncCounts {
+  read: number
+  inserted: number
+  updated: number
+  unchanged: number
+  deleted: number
+}
+const baseURL = import.meta.env.VITE_BASE_URL
+const root = '/api/v1/pms/integration/base-sync'
+export const getTasks = () => request.get<BaseSyncTask[]>({ baseURL, url: root + '/tasks' })
+export const getRegistrations = () =>
+  request.get<Registration[]>({ baseURL, url: root + '/registrations' })
+export const run = (preview: boolean, taskId: Id, batchSize?: number) =>
+  request.post<SyncCounts>({
+    baseURL,
+    url: root + (preview ? '/preview' : '/execute'),
+    data: { taskId, ...(batchSize === undefined ? {} : { batchSize }) },
+    timeout: 0
+  })
```

## 013 api/pms/integration/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：7；当前片段：7。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -27,6 +27,7 @@

裁决：保留纯新增及其契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -27,6 +27,7 @@
   sql?: string
   parameters: Record<string, unknown>
   sourceKey: string
+  sourceKeyColumns?: string[]
   syncPrimaryKey?: boolean
   updatedAt?: string
   columns: string[]
```

### H02 @@ -55,6 +56,7 @@

裁决：保留纯新增及其契约配套。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398。

```diff
@@ -55,6 +56,7 @@
   autoPaging?: boolean
   maxBytes: number
   readStrategy?: 'SNAPSHOT' | 'KEYSET_PAGING' | 'STREAMING_CURSOR'
+  evidencePolicy?: 'FULL' | 'REUSE_UNCHANGED'
   fetchSize?: number
   chunkSize?: number
   restartPolicy?: 'RESTART_ALL' | 'CHECKPOINT_KEY' | 'NO_RESTART'
```

### H03 @@ -140,8 +142,45 @@

裁决：保留纯新增及其契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, 6a56c3f2f20f46a095dcb1d09439e06688a1c4dc, 7907c8117bce8eae3dc2b3b3dde68f88ffc543d4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -140,8 +142,45 @@
 export const getAdapters = () => request.get<Adapter[]>({ url: base + '/adapters' })
 export const getEhrTemplate = (connectionId: Id) =>
   request.get<Definition>({ url: base + '/templates/ehr', params: { connectionId } })
+export const getDppmsOrganizationTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-organization', params: { connectionId } })
+export const getDppmsDirectOrganizationTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-organization-direct', params: { connectionId } })
+export const getDppmsAccountFoundationTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-account-foundation', params: { connectionId } })
+export const getDppmsDirectAccountTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-account-foundation-direct', params: { connectionId } })
+export const getDppmsDirectDictionaryTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-dictionaries-direct', params: { connectionId } })
+export const getCrmDirectCustomerTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/crm-customers-direct', params: { connectionId } })
+export const getDppmsDirectProductTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-products-direct', params: { connectionId } })
+export const getDppmsCollectionPlanTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-collection-plans', params: { connectionId } })
 export const getDppmsOrderTemplate = (connectionId: Id) =>
   request.get<Definition>({ url: base + '/templates/dppms-orders', params: { connectionId } })
+export const getDppmsProjectProductScopeTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-project-product-scopes', params: { connectionId } })
+export const getDppmsProjectRealProductTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-project-real-products', params: { connectionId } })
+export const getDppmsProjectAssociatedProductTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-project-associated-products', params: { connectionId } })
+export const getDppmsRoleTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-roles', params: { connectionId } })
+export const getDppmsUserTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-users', params: { connectionId } })
+export const getDppmsDepartmentTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-departments', params: { connectionId } })
+export const getDppmsProductTemplate = (params: { connectionId: Id; productCodes: string[] }) =>
+  request.post<Definition>({
+    url: base + '/templates/dppms-products',
+    data: params
+  })
+export const getDppmsUserRoleTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-user-roles', params: { connectionId } })
+export const getDppmsUserScopeTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-user-scopes', params: { connectionId } })
 export const getDppmsExecutionOrderTemplate = (connectionId: Id) =>
   request.get<Definition>({
     url: base + '/templates/dppms-execution-orders',
```

### H04 @@ -214,6 +253,8 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -214,6 +253,8 @@
   parentRunId?: Id
 }) => request.get<Page<Run>>({ url: base + '/runs', params })
 export const getRun = (id: Id) => request.get<Run>({ url: base + '/runs/' + id })
+export const recoverRun = (id: Id) =>
+  request.post<boolean>({ url: base + '/runs/' + id + '/recover' })
 export const getChanges = (id: Id, params = { pageNo: 1, pageSize: 20 }) =>
   request.get<Page<Change>>({ url: base + '/runs/' + id + '/changes', params })
 export const getBindings = (params: { pageNo: number; pageSize: number; taskId: Id }) =>
```

### H05 @@ -228,6 +269,7 @@

裁决：保留纯新增及其契约配套。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398。

```diff
@@ -228,6 +269,7 @@
   FAILED: '失败（未提交）'
 }
 export const changeLabels: Record<string, string> = {
+  EVIDENCE_REUSED: '复用既有证据（未变化子集）',
   CLEARED: '加载前清空',
   CREATED: '新增',
   UPDATED: '更新',
```

### H06 @@ -249,6 +291,7 @@

裁决：保留纯新增及其契约配套。当前行来源：7907c8117bce8eae3dc2b3b3dde68f88ffc543d4。

```diff
@@ -249,6 +291,7 @@
   whenField?: string | null
   newerBy?: string | null
   tieBreaker?: string | null
+  sameVersionColumns?: string[] | null
   mappings: Mapping[]
   lookups: {
     table: string
```

### H07 @@ -276,3 +319,50 @@

裁决：保留纯新增及其契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, 6a56c3f2f20f46a095dcb1d09439e06688a1c4dc, e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -276,3 +319,50 @@
   request.get<GenericTarget[]>({ url: base + '/generic-targets' })
 export const getGenericTemplates = (connectionId: Id) =>
   request.get<GenericTemplate[]>({ url: base + '/templates/generic', params: { connectionId } })
+export const getDppmsWarrantyRenewalTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-warranty-renewals', params: { connectionId } })
+export const getDppmsContractReceivableTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-contract-receivables', params: { connectionId } })
+export const getDppmsProjectFactsTemplate = (connectionId: Id, definitionId?: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-project-facts', params: { connectionId, definitionId } })
+export const getDppmsObsoleteOrderLineTemplate = (connectionId: Id, originalTaskId: Id) =>
+  request.get<Definition>({ url: base + '/templates/dppms-obsolete-order-line-bindings', params: { connectionId, originalTaskId } })
+export const configureDppmsProjectFactsDefinition = (existingRevisionId?: Id) =>
+  request.post<Id>({ url: base + '/templates/dppms-project-facts-definition', data: { existingRevisionId } })
+
+export const getDppmsDirectExecutionOrderTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-execution-orders-direct", params: { connectionId } })
+
+export const getDppmsDirectReceivableTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-receivables-direct", params: { connectionId } })
+
+export const getDppmsDirectWarrantyTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-warranty-renewals-direct", params: { connectionId } })
+
+export const getDppmsDirectProjectTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-projects-direct", params: { connectionId } })
+
+export const getDppmsDirectProjectRelationTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-project-contract-relations-direct", params: { connectionId } })
+
+export const getDppmsDirectProjectFactsTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-project-facts-direct", params: { connectionId } })
+
+export const getDppmsDirectProductScopeTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-product-scopes-direct", params: { connectionId } })
+
+export const getDppmsDirectRealProductTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-real-products-direct", params: { connectionId } })
+
+export const getDppmsDirectDeviceBaseTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-device-base-direct", params: { connectionId } })
+export const getDppmsDirectMemberHistoryTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-member-history-direct", params: { connectionId } })
+export const getDppmsDirectInstallationTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-installations-direct", params: { connectionId } })
+export const getDppmsDirectFactoryHistoryTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-factory-history-direct", params: { connectionId } })
+export const getDppmsDirectSoftwareHistoryTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-software-history-direct", params: { connectionId } })
+export const getDppmsDirectLicenseHistoryTemplate = (connectionId: Id) =>
+  request.get<Definition>({ url: base + "/templates/dppms-secure-license-direct", params: { connectionId } })
```

## 014 api/pms/platform/business/deliveryCollection.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -0,0 +1,12 @@

裁决：保留文件兼容或新增字段。当前行来源：28617c7993b0c6edfc04c7e9def582cc02388510。

```diff
@@ -0,0 +1,12 @@
+import request from '@/config/axios'
+import { createAccessTicket, type FileBusinessKey } from '../file'
+import type { DeliveryRecord } from '../businessmodel/delivery'
+const base = '/api/v1/pms/business-deliverables'
+export const listDeliveries = (params: { projectId:string|number; deliverableType?:string; businessType?:string; businessEntityKey?:string|number; pageNo?:number; pageSize?:number }) =>
+  request.get<{list:DeliveryRecord[];total:number}>({url:base,params,silentError:true})
+export const editDelivery = (row:DeliveryRecord,title:string) => request.put<DeliveryRecord>({url:`${base}/${row.id}`,data:{version:row.version,title},silentError:true})
+export const deleteDelivery = (row:DeliveryRecord) => request.delete<boolean>({url:`${base}/${row.id}`,params:{version:row.version},silentError:true})
+export const downloadDelivery = async (row:DeliveryRecord) => {
+  const key=await request.get<FileBusinessKey>({url:`${base}/${row.id}/file`,silentError:true})
+  return createAccessTicket(row.fileArtifactId,row.fileVersionNo,'DOWNLOAD',{ownerContext:key.ownerContext,objectType:key.objectType,objectId:key.objectId,purposeCode:key.purposeCode,referenceKey:key.referenceKey})
+}
```

## 015 api/pms/platform/business/index.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -0,0 +1,61 @@

裁决：保留文件兼容或新增字段。当前行来源：88dca0e89a04f5f7eeb1c57ec721de296917131f, a0a2287e1de9c5953599488e458cffd463b94b85, e7e601909a738879fdf8709ed4869c8a53341179。

```diff
@@ -0,0 +1,61 @@
+import request from '@/config/axios'
+import type { BusinessEntityData, BusinessEntityFormData, BusinessOperationReceipt, ModelDetailVO, FieldFilter } from '../businessmodel'
+import type { DeliveryRecord, DeliveryScope } from '../businessmodel/delivery'
+import { createAccessTicket, type FileBusinessKey } from '../file'
+
+export type BusinessId = string | number
+export interface BusinessPage { list: BusinessEntityData[]; total: number }
+
+/** One client factory for every inherited business Controller; no catalog/operation-dispatch URL. */
+export const createProjectBusinessApi = (prefix: string) => {
+  const base = prefix.replace(/\/$/, '')
+  if (!/^\/api\/v1\/pms\/[A-Za-z0-9_/-]+$/.test(base)) throw new Error('业务 API 路径不合法')
+  const entity = (id: BusinessId) => {
+    if (typeof id === 'number' && !Number.isSafeInteger(id) || !/^[1-9][0-9]*$/.test(String(id))) throw new Error('业务实体 ID 不合法')
+    return `${base}/${id}`
+  }
+  const material = (id: BusinessId, materialId: BusinessId) => {
+    if (!/^[1-9][0-9]*$/.test(String(materialId))) throw new Error('交付件 ID 不合法')
+    return `${entity(id)}/deliverables/${materialId}`
+  }
+  return {
+    base,
+    model: () => request.get<ModelDetailVO>({ url: `${base}/model`, silentError: true }),
+    page: (pageNo: number, pageSize: number, filters: FieldFilter[]) =>
+      request.post<BusinessPage>({ url: `${base}/page`, data: { pageNo, pageSize, filters }, silentError: true }),
+    get: (id: BusinessId) => request.get<BusinessEntityData>({ url: entity(id), silentError: true }),
+    form: (id: BusinessId) => request.get<BusinessEntityFormData>({url:`${entity(id)}/form`,silentError:true}),
+    create: (values: Record<string, unknown>, key: string) =>
+      request.post<BusinessOperationReceipt>({ url: base, data: { values, idempotencyKey: key }, silentError: true }),
+    update: (id: BusinessId, values: Record<string, unknown>, version: number, key: string) =>
+      request.put<BusinessOperationReceipt>({ url: entity(id), data: { values, version, idempotencyKey: key }, silentError: true }),
+    remove: (id: BusinessId, version: number, key: string) =>
+      request.delete<BusinessOperationReceipt>({ url: entity(id), params: { version }, headers: { 'Idempotency-Key': key }, silentError: true }),
+    action: (operation: string, id: BusinessId, version: number, key: string, values: Record<string, unknown> = {}) => {
+      if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(operation) || ['create','save','delete'].includes(operation)) throw new Error('业务操作路径不合法')
+      return request.post<BusinessOperationReceipt>({url:`${entity(id)}/${operation}`,data:{...values,version,idempotencyKey:key},silentError:true})
+    },
+    receipt: (operation: string, key: string) =>
+      request.get<BusinessOperationReceipt | null>({ url: `${base}/receipts/${encodeURIComponent(key)}`, params: { operation }, silentError: true }),
+    deliveryContext: (id: BusinessId) => request.get<Omit<DeliveryScope, 'deliverableType'>>({ url: `${entity(id)}/deliverables/context`, silentError: true }),
+    upload: (scope: DeliveryScope, file: File, key: string) => {
+      const data = new FormData()
+      Object.entries(scope).forEach(([name, value]) => data.append(name, String(value)))
+      data.append('file', file)
+      return request.post<DeliveryRecord>({ url: `${entity(scope.businessEntityKey)}/deliverables`, data,
+        headersType: 'multipart/form-data', headers: { 'Idempotency-Key': key }, silentError: true })
+    },
+    deliveries: (id: BusinessId, deliverableType?: string, pageNo = 1) =>
+      request.get<{ list: DeliveryRecord[]; total: number }>({ url: `${entity(id)}/deliverables`, params: { deliverableType, pageNo, pageSize: 20 }, silentError: true }),
+    completion: (scope: DeliveryScope) => request.get<{ completed: boolean; latest?: DeliveryRecord }>({ url: `${entity(scope.businessEntityKey)}/deliverables/completion`, params: scope, silentError: true }),
+    editDelivery: (id: BusinessId, row: DeliveryRecord, title: string) =>
+      request.put<DeliveryRecord>({ url: material(id, row.id), data: { version: row.version, title }, silentError: true }),
+    removeDelivery: (id: BusinessId, row: DeliveryRecord) =>
+      request.delete<boolean>({ url: material(id, row.id), params: { version: row.version }, silentError: true }),
+    downloadDelivery: async (id: BusinessId, row: DeliveryRecord) => {
+      const key = await request.get<FileBusinessKey>({ url: `${material(id, row.id)}/file`, silentError: true })
+      return createAccessTicket(row.fileArtifactId, row.fileVersionNo, 'DOWNLOAD', { ownerContext:key.ownerContext, objectType:key.objectType, objectId:key.objectId, purposeCode:key.purposeCode, referenceKey:key.referenceKey })
+    }
+  }
+}
+export type ProjectBusinessApi = ReturnType<typeof createProjectBusinessApi>
```

## 016 api/pms/platform/businessmodel/delivery.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -0,0 +1,34 @@

裁决：保留文件兼容或新增字段。当前行来源：ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -0,0 +1,34 @@
+import request from '@/config/axios'
+import { createAccessTicket, type FileBusinessKey } from '@/api/pms/platform/file'
+export interface DeliveryScope {
+  projectId: string | number
+  businessType: string
+  businessEntityKey: string | number
+  deliverableType: string
+}
+export interface DeliveryRecord extends DeliveryScope {
+  id: string; title: string; fileName: string; fileReferenceId: string; fileArtifactId: string
+  fileVersionNo: number; uploadedAt: string | number; version: number; status: string; ownerModule: string; entityType: string
+}
+const base = '/api/v1/pms/business-models'
+export const deliveryContext = (owner: string, type: string, entityId: string | number) =>
+  request.get<Omit<DeliveryScope, 'deliverableType'>>({ url: `${base}/${owner}/${type}/deliverables/context`, params: { entityId }, silentError: true })
+export const uploadDelivery = (scope: DeliveryScope, file: File, key: string) => {
+  const data = new FormData()
+  Object.entries(scope).forEach(([name, value]) => data.append(name, String(value)))
+  data.append('file', file)
+  return request.post<DeliveryRecord>({ url: `${base}/deliverables/upload`, data, headersType: 'multipart/form-data', headers: { 'Idempotency-Key': key }, silentError: true })
+}
+export const listDeliveries = (params: { projectId: string | number; deliverableType?: string; businessType?: string; businessEntityKey?: string | number; pageNo?: number; pageSize?: number }) =>
+  request.get<{ list: DeliveryRecord[]; total: number }>({ url: `${base}/deliverables`, params, silentError: true })
+export const deliveryCompletion = (params: DeliveryScope) =>
+  request.get<{ completed: boolean; latest?: DeliveryRecord }>({ url: `${base}/deliverables/completion`, params, silentError: true })
+export const getDelivery = (id: string) => request.get<DeliveryRecord>({ url: `${base}/deliverables/${id}`, silentError: true })
+export const editDelivery = (row: DeliveryRecord, title: string) =>
+  request.put<DeliveryRecord>({ url: `${base}/deliverables/${row.id}`, data: { version: row.version, title }, silentError: true })
+export const deleteDelivery = (row: DeliveryRecord) =>
+  request.delete<boolean>({ url: `${base}/deliverables/${row.id}`, params: { version: row.version }, silentError: true })
+export const downloadDelivery = async (row: DeliveryRecord) => {
+  const key = await request.get<FileBusinessKey>({ url: `${base}/deliverables/${row.id}/file`, silentError: true })
+  return createAccessTicket(row.fileArtifactId, row.fileVersionNo, 'DOWNLOAD', { ownerContext: key.ownerContext, objectType: key.objectType, objectId: key.objectId, purposeCode: key.purposeCode, referenceKey: key.referenceKey })
+}
```

## 017 api/pms/platform/businessmodel/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留修复及新增契约**。保留服务端业务错误原文，新增DELETED回执配套新增删除功能。
- 证据及验证边界：useBusinessEntity.runtime.spec.ts; BusinessEntityHost.delete.runtime.spec.ts。

### H01 @@ -89,7 +89,7 @@

裁决：保留修复及新增契约。当前行来源：ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -89,7 +89,7 @@
   unavailableReason?: string
 }
 
-export type ReceiptOutcome = 'SAVED' | 'ACCEPTED' | 'APPROVAL_PENDING' | 'EFFECTED' | 'FAILED'
+export type ReceiptOutcome = 'DELETED' | 'SAVED' | 'ACCEPTED' | 'APPROVAL_PENDING' | 'EFFECTED' | 'FAILED'
 
 export interface ResultReference {
   kind: 'FILE' | 'APPROVAL' | 'RESULT' | 'COMMAND'
```

### H02 @@ -161,7 +161,8 @@

裁决：保留修复及新增契约。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -161,7 +161,8 @@
     params: entityId == null ? undefined : { entityId },
     data,
     // 契约结果（并发冲突、权限拒绝等）由办理页按回执与原因呈现，不重复弹全局错误提示。
-    silentError: true
+    silentError: true,
+    preserveBusinessError: true
   })
 
 export const recoverEntityOperation = (ownerModule: string, entityType: string, operationCode: string,
```

## 018 api/pms/platform/delivery/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -1,7 +1,7 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -1,7 +1,7 @@
 // 统一交付件公共客户端：类型目录、适用配置、要求实例、材料记录、提交台账。
 // 文件上传复用统一文件两段式客户端（api/pms/platform/file），本文件只做交付语义调用。
 import request from '@/config/axios'
-import type { FileBusinessKey } from '@/api/pms/platform/file'
+import type { FileBusinessKey, FileId } from '@/api/pms/platform/file'
 
 const baseUrl = '/api/v1/pms/delivery'
```

### H02 @@ -51,10 +51,10 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -51,10 +51,10 @@
   typeCode: string
   fileName?: string
   title?: string
-  fileArtifactId?: number
+  fileArtifactId?: FileId
   fileVersionNo?: number
   fileSha256?: string
-  fileReferenceId?: number
+  fileReferenceId?: FileId
   sourceKind: string
   status: 'ACTIVE' | 'WITHDRAWN'
   createTime: string
```

### H03 @@ -123,7 +123,7 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -123,7 +123,7 @@
   })
 
 export const registerMaterial = (
-  data: EntityOwnerParam & { typeCode: string; fileReferenceId: number; title?: string; sourceKind?: string }
+  data: EntityOwnerParam & { typeCode: string; fileReferenceId: FileId; title?: string; sourceKind?: string }
 ) => request.post<DeliveryMaterialVO>({ url: `${baseUrl}/materials`, data, silentError: true, preserveBusinessError: true })
 
 export const withdrawMaterial = (materialId: number) =>
```

## 019 api/pms/platform/dynamic-form/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容或新增字段**。这些剩余片段为FileId无损类型、nullable读取或新增清单字段；完整差异逐段附后，不改变原业务动作和授权。
- 证据及验证边界：文件/动态表单/交付既有测试；类型检查。

### H01 @@ -1,4 +1,5 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -1,4 +1,5 @@
 import request from '@/config/axios'
+import type { FileId } from '@/api/pms/platform/file'
 
 export type DynamicFormAction =
   | 'PATCH_TEMPLATE'
```

### H02 @@ -75,11 +76,11 @@

裁决：保留文件兼容或新增字段。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -75,11 +76,11 @@
 }
 
 export interface DynamicFormFileFactVO {
-  artifactId: number
+  artifactId: FileId
   versionNo: number
   referenceKey: string
   fileFactVersion: FileFactVersion
-  scopeVersion: number
+  scopeVersion: FileId
   status: string
 }
```

## 020 api/pms/platform/file/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：9；当前片段：9。
- 裁决：**保留测试修复及兼容**。无损文件ID、稳定引用发现、nullable文件读取；所有业务键、权限和版本校验不变。
- 证据及验证边界：文件API及PmsFileArtifact既有失败测试。

### H01 @@ -3,6 +3,13 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -3,6 +3,13 @@
 
 export type FileUploadMode = 'CREATE_ARTIFACT' | 'ADD_VERSION'
 export type FileAccessOperation = 'DOWNLOAD' | 'PREVIEW'
+// Java Long values are serialized as strings; small numeric fixture IDs remain supported.
+export type FileId = string | number
+
+const requireFileId = (id: FileId): FileId => {
+  if (typeof id === 'number' ? Number.isSafeInteger(id) && id > 0 : /^[1-9]\d*$/.test(id)) return id
+  throw new Error('文件 ID 无效，请刷新后重试')
+}
 
 export interface FileBusinessKey {
   ownerContext: string
```

### H02 @@ -15,7 +22,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -15,7 +22,7 @@
 export interface FileUploadInitReqVO extends FileBusinessKey {
   ownerExecutionContext?: JsonObject
   modeCode: FileUploadMode
-  artifactId?: number | string
+  artifactId?: FileId
   expectedReferenceVersion?: number
   fileName: string
   categoryCode: string
```

### H03 @@ -24,33 +31,33 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -24,33 +31,33 @@
 }
 
 export interface FileUploadInitRespVO {
-  artifactId: number
-  sessionId: number
+  artifactId: FileId
+  sessionId: FileId
   expiresAt: string
 }
 
 export interface FileUploadCompleteRespVO {
-  artifactId: number
+  artifactId: FileId
   versionNo: number
-  referenceId: number
+  referenceId: FileId
   referenceKey: string
   sha256: string
 }
 
 export interface FileReferenceVO extends FileBusinessKey {
-  referenceId: number
-  artifactId: number
+  referenceId: FileId
+  artifactId: FileId
   versionNo: number
   sensitivityCode: string
   status: string
-  scopeVersion: number
+  scopeVersion: FileId
   referenceVersion: number
   createdAt: string
   updatedAt: string
 }
 
 export interface FileArtifactVO {
-  artifactId: number
+  artifactId: FileId
   name: string
   currentVersionFileName?: string
   categoryCode: string
```

### H04 @@ -63,7 +70,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -63,7 +70,7 @@
 }
 
 export interface FileVersionVO {
-  id: number
+  id: FileId
   versionNo: number
   fileName?: string
   sha256: string
```

### H05 @@ -74,7 +81,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -74,7 +81,7 @@
   availabilityVersion: number
   unavailableReasonCode?: string
   versionNote?: string
-  createdBy: number
+  createdBy: FileId
   createdAt: string
 }
```

### H06 @@ -85,15 +92,15 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -85,15 +92,15 @@
 }
 
 export interface FileAccessTicketVO {
-  grantId: number
+  grantId: FileId
   shortLivedUrl: string
   expiresAt: string
 }
 
 export interface FileLifecycleResultVO {
-  artifactId: number
+  artifactId: FileId
   versionNo?: number
-  referenceId?: number
+  referenceId?: FileId
   factVersion: number
   status: string
 }
```

### H07 @@ -103,27 +110,27 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -103,27 +110,27 @@
 export const initializeUpload = (data: FileUploadInitReqVO, idempotencyKey: string) =>
   request.post<FileUploadInitRespVO>({
     url: `${baseUrl}/files:init-upload`,
-    data,
+    data: { ...data, ...(data.artifactId !== undefined ? { artifactId: requireFileId(data.artifactId) } : {}) },
     headers: { 'Idempotency-Key': idempotencyKey }
   })
 
 export const completeUpload = (
-  artifactId: number,
-  sessionId: number,
+  artifactId: FileId,
+  sessionId: FileId,
   file: File,
   idempotencyKey: string,
   onUploadProgress?: (progress: number) => void,
   ownerExecutionContext?: JsonObject
 ) => {
   const data = new FormData()
-  data.append('sessionId', String(sessionId))
+  data.append('sessionId', String(requireFileId(sessionId)))
   data.append('file', file)
   // Spring @RequestPart uses the JSON part's Content-Type for its standard message converter.
   // https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html
   if (ownerExecutionContext) data.append('ownerExecutionContext',
     new Blob([JSON.stringify(ownerExecutionContext)], { type: 'application/json' }))
   return request.post<FileUploadCompleteRespVO>({
-    url: `${baseUrl}/files/${artifactId}:complete-upload`,
+    url: `${baseUrl}/files/${requireFileId(artifactId)}:complete-upload`,
     data,
     headersType: 'multipart/form-data',
     headers: { 'Idempotency-Key': idempotencyKey },
```

### H08 @@ -133,28 +140,31 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -133,28 +140,31 @@
   })
 }
 
-export const getArtifact = (artifactId: number, params: FileBusinessKey) =>
-  request.get<FileArtifactVO>({ url: `${baseUrl}/files/${artifactId}`, params })
+export const getReference = (params: FileBusinessKey) =>
+  request.get<FileReferenceVO | null>({ url: `${baseUrl}/file-references`, params })
+
+export const getArtifact = (artifactId: FileId, params: FileBusinessKey) =>
+  request.get<FileArtifactVO | null>({ url: `${baseUrl}/files/${requireFileId(artifactId)}`, params })
 
 export const getVersions = (
-  artifactId: number,
+  artifactId: FileId,
   params: FileBusinessKey & { cursor?: string; pageSize?: number }
 ) =>
-  request.get<CursorPage<FileVersionVO>>({ url: `${baseUrl}/files/${artifactId}/versions`, params })
+  request.get<CursorPage<FileVersionVO>>({ url: `${baseUrl}/files/${requireFileId(artifactId)}/versions`, params })
 
 export const createAccessTicket = (
-  artifactId: number,
+  artifactId: FileId,
   versionNo: number,
   operationCode: FileAccessOperation,
   key: FileBusinessKey
 ) =>
   request.post<FileAccessTicketVO>({
-    url: `${baseUrl}/files/${artifactId}/access-tickets`,
+    url: `${baseUrl}/files/${requireFileId(artifactId)}/access-tickets`,
     data: { versionNo, operationCode, ...key }
   })
 
 export const detachReference = (
-  referenceId: number,
+  referenceId: FileId,
   referenceVersion: number,
   key: FileBusinessKey,
   reason: string,
```

### H09 @@ -162,7 +172,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -162,7 +172,7 @@
   ownerExecutionContext?: JsonObject
 ) =>
   request.delete<FileLifecycleResultVO>({
-    url: `${baseUrl}/file-references/${referenceId}`,
+    url: `${baseUrl}/file-references/${requireFileId(referenceId)}`,
     data: { ...key, reason, ...(ownerExecutionContext ? { ownerExecutionContext } : {}) },
     headers: { 'If-Match': String(referenceVersion), 'Idempotency-Key': idempotencyKey }
   })
```

## 021 api/pms/project/associated-data.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增项目相关数据只读API契约。
- 证据及验证边界：ProjectAssociatedDataPanel。

### H01 @@ -0,0 +1,893 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,893 @@
+import request from '@/config/axios'
+import type { LegacyId } from './legacy-detail'
+export interface AssociatedRow {
+  id: LegacyId
+  sourceKey: string
+  values: Record<string, unknown>
+}
+export const associatedKinds = [
+  { kind: 'licenses', label: '许可历史' },
+  {
+    kind: 'products',
+    label: '项目产品'
+  },
+  {
+    kind: 'real-products',
+    label: '真实产品清单'
+  },
+  {
+    kind: 'lease-products',
+    label: '租赁产品'
+  },
+  {
+    kind: 'configured-products',
+    label: '产品配置层级'
+  },
+  {
+    kind: 'plans',
+    label: '回款计划'
+  },
+  {
+    kind: 'shipment-packages',
+    label: '装箱单'
+  },
+  { kind: 'executions', label: '执行单属性' },
+  { kind: 'factory-versions', label: '出厂软件版本' },
+  { kind: 'software-versions', label: '软件版本观察' },
+  { kind: 'software-changes', label: '软件变更批次' },
+  { kind: 'installations', label: '安装地址来源' },
+  { kind: 'warranty-services', label: '维保服务来源' },
+  { kind: 'warranty-registrations', label: '保修登记来源' },
+  { kind: 'warranty-changes', label: '保修变更来源' },
+  { kind: 'project', label: '项目附加信息' },
+  { kind: 'orders', label: '销售订单' },
+  { kind: 'order-lines', label: '订单行' }
+] as const
+export type AssociatedKind = (typeof associatedKinds)[number]['kind']
+export const associatedFields: Record<AssociatedKind, { key: string; label: string }[]> = {
+  licenses: [
+    { key: 'contractNo', label: '合同号' },
+    { key: 'deviceSn', label: '设备序列号' },
+    { key: 'productCode', label: '产品编码' },
+    { key: 'productModel', label: '产品型号' },
+    { key: 'contractType', label: '合同类型' },
+    { key: 'licenseStatus', label: '许可状态' },
+    { key: 'licenseCodeState', label: '许可码' },
+    { key: 'deviceId', label: '设备' },
+    { key: 'contractId', label: '合同' },
+    { key: 'creator', label: '迁入人' },
+    { key: 'createTime', label: '迁入时间' }
+  ],
+  'warranty-services': [
+    { key: 'id', label: 'id' },
+    { key: 'con_xb', label: 'con_xb' },
+    { key: 'barcode', label: 'barcode' },
+    { key: 'grade', label: 'grade' },
+    { key: 'begin_date', label: 'begin_date' },
+    { key: 'end_date', label: 'end_date' },
+    { key: 'warranty', label: 'warranty' },
+    { key: 'remark', label: 'remark' },
+    { key: 'isyb', label: 'isyb' },
+    { key: 'state', label: 'state' }
+  ],
+  'warranty-registrations': [
+    { key: 'id', label: 'id' },
+    { key: 'packlistId', label: 'packlistId' },
+    { key: 'contractId', label: 'contractId' },
+    { key: 'barCode', label: 'barCode' },
+    { key: 'officeCode', label: 'officeCode' },
+    { key: 'projectName', label: 'projectName' },
+    { key: 'contractNo', label: 'contractNo' },
+    { key: 'contractType', label: 'contractType' },
+    { key: 'customerName', label: 'customerName' },
+    { key: 'itemCode', label: 'itemCode' },
+    { key: 'itemName', label: 'itemName' },
+    { key: 'warrantyLevel', label: 'warrantyLevel' },
+    { key: 'warrantyStartTime', label: 'warrantyStartTime' },
+    { key: 'warrantyEndTime', label: 'warrantyEndTime' },
+    { key: 'warrantyLimit', label: 'warrantyLimit' }
+  ],
+  'warranty-changes': [
+    { key: 'id', label: 'id' },
+    { key: 'username', label: 'username' },
+    { key: 'updateType', label: 'updateType' },
+    { key: 'barcode', label: 'barcode' },
+    { key: 'warrantyStartTime', label: 'warrantyStartTime' },
+    { key: 'warrantyEndTime', label: 'warrantyEndTime' },
+    { key: 'warrantyTimes', label: 'warrantyTimes' },
+    { key: 'updateTime', label: 'updateTime' }
+  ],
+  installations: [
+    { key: 'sourceId', label: '来源记录ID' },
+    { key: 'sourceProjectId', label: '来源项目ID' },
+    { key: 'sourceDeviceSn', label: '来源设备序列号' },
+    { key: 'installLocation', label: '原安装地址' },
+    { key: 'sourceProductCode', label: '来源产品编码' },
+    { key: 'sourceProductModel', label: '来源产品型号' },
+    { key: 'sourceProductName', label: '来源产品名称' },
+    { key: 'sourceReceiverName', label: '来源收件人' },
+    { key: 'sourceExpressNo', label: '来源物流单号' },
+    { key: 'sourceCarrierName', label: '来源承运商' },
+    { key: 'sourceShipmentTime', label: '来源发货时间' },
+    { key: 'sourceContractNo', label: '来源合同号' },
+    { key: 'changedProjectKey', label: '来源变更项目ID' },
+    { key: 'changedContractNo', label: '来源变更合同号' },
+    { key: 'transferProjectKey', label: '来源转入项目ID' },
+    { key: 'transferContractNo', label: '来源转入合同号' },
+    { key: 'sourceTransferFlag', label: '来源转移标识' },
+    { key: 'sourceCreatedAt', label: '来源创建时间' },
+    { key: 'sourceCreatedBy', label: '来源创建人' },
+    { key: 'sourceUpdatedAt', label: '来源更新时间' },
+    { key: 'sourceUpdatedBy', label: '来源更新人' }
+  ],
+  'software-versions': [
+    {
+      key: 'sourceId',
+      label: '项目软件版本表'
+    },
+    {
+      key: 'sourceProjectId',
+      label: '项目ID'
+    },
+    {
+      key: 'logId',
+      label: '软件版本变更记录'
+    },
+    {
+      key: 'contractNo',
+      label: '合同号'
+    },
+    {
+      key: 'itemCode',
+      label: '产品编码'
+    },
+    {
+      key: 'barCode',
+      label: '设备序列号'
+    },
+    {
+      key: 'conp',
+      label: 'conp'
+    },
+    {
+      key: 'conpType',
+      label: '版本类型'
+    },
+    {
+      key: 'conpSeries',
+      label: '版本系列'
+    },
+    {
+      key: 'conpMark',
+      label: '软件版本掩码'
+    },
+    {
+      key: 'conpBak',
+      label: '备份变更之前的版本'
+    },
+    {
+      key: 'conpChange',
+      label: '0无更新 1有更新'
+    },
+    {
+      key: 'cpld',
+      label: 'cpld'
+    },
+    {
+      key: 'cpldBak',
+      label: 'cpldBak'
+    },
+    {
+      key: 'cpldChange',
+      label: 'cpldChange'
+    },
+    {
+      key: 'boot',
+      label: 'boot'
+    },
+    {
+      key: 'bootBak',
+      label: 'bootBak'
+    },
+    {
+      key: 'bootChange',
+      label: 'bootChange'
+    },
+    {
+      key: 'pcb',
+      label: 'pcb'
+    },
+    {
+      key: 'pcbBak',
+      label: 'pcbBak'
+    },
+    {
+      key: 'pcbChange',
+      label: 'pcbChange'
+    },
+    {
+      key: 'executeTime',
+      label: '若有更新的情况下为执行更新时间，否则没有实际意义'
+    },
+    {
+      key: 'datastate',
+      label: '数据状态 0 失效 1 有效'
+    },
+    {
+      key: 'sourceCreatedAt',
+      label: 'createTime'
+    },
+    {
+      key: 'sourceCreatedBy',
+      label: 'createBy'
+    },
+    {
+      key: 'sourceUpdatedAt',
+      label: 'updateTime'
+    },
+    {
+      key: 'sourceUpdatedBy',
+      label: 'updateBy'
+    },
+    {
+      key: 'customInfo',
+      label: '自定义信息'
+    }
+  ],
+  'software-changes': [
+    {
+      key: 'sourceId',
+      label: '记录版本变更日志'
+    },
+    {
+      key: 'sourceProjectId',
+      label: '项目ID'
+    },
+    {
+      key: 'changeVersion',
+      label: 'V0001'
+    },
+    {
+      key: 'changeRemark',
+      label: '版本变更说明'
+    },
+    {
+      key: 'latest',
+      label: '0 后 1 是'
+    },
+    {
+      key: 'sourceCreatedBy',
+      label: 'createBy'
+    },
+    {
+      key: 'sourceCreatedAt',
+      label: 'createTime'
+    },
+    {
+      key: 'sourceUpdatedBy',
+      label: 'updateBy'
+    },
+    {
+      key: 'sourceUpdatedAt',
+      label: 'updateTime'
+    }
+  ],
+  products: [
+    {
+      key: 'contractNo',
+      label: '合同号'
+    },
+    {
+      key: 'productCode',
+      label: '产品编码'
+    },
+    {
+      key: 'productName',
+      label: '产品名称'
+    },
+    {
+      key: 'projectQuantity',
+      label: '项目数量'
+    },
+    {
+      key: 'orderQuantity',
+      label: '订单数量'
+    },
+    {
+      key: 'deliveredQuantity',
+      label: '已交数量'
+    },
+    {
+      key: 'openQuantity',
+      label: '未交数量'
+    },
+    {
+      key: 'orderNo',
+      label: '销售订单号'
+    },
+    {
+      key: 'lineNo',
+      label: '订单行号'
+    },
+    {
+      key: 'unitCode',
+      label: '单位'
+    },
+    { key: 'allocationConfirmed', label: '已确认分配' },
+    { key: 'scopeStatus', label: '范围状态' },
+    { key: 'allocationVersion', label: '分配版本' },
+    { key: 'effectiveFrom', label: '生效时间' },
+    { key: 'departmentCode', label: '办事处编码' },
+    { key: 'departmentName', label: '办事处名称' }
+  ],
+  'real-products': [
+    {
+      key: 'contractNo',
+      label: '合同号'
+    },
+    {
+      key: 'projectCode',
+      label: '商务项目编码'
+    },
+    {
+      key: 'executionNo',
+      label: '执行单号'
+    },
+    {
+      key: 'productCategoryName',
+      label: '一级产品名称'
+    },
+    {
+      key: 'productName',
+      label: '产品名称'
+    },
+    {
+      key: 'productSubCode',
+      label: '子产品编码'
+    },
+    {
+      key: 'productSubModel',
+      label: '子产品型号'
+    },
+    {
+      key: 'productSubName',
+      label: '子产品名称'
+    },
+    {
+      key: 'quantity',
+      label: '数量'
+    },
+    {
+      key: 'memo',
+      label: '说明'
+    },
+    { key: 'dataSource', label: '数据来源' },
+    { key: 'creator', label: '创建人' },
+    { key: 'createTime', label: '创建时间' },
+    { key: 'updater', label: '更新人' },
+    { key: 'updateTime', label: '更新时间' }
+  ],
+  'lease-products': [
+    {
+      key: 'projectCode',
+      label: '清单项目编码'
+    },
+    {
+      key: 'executionNo',
+      label: '执行单号'
+    },
+    {
+      key: 'productCategory',
+      label: '产品分类'
+    },
+    {
+      key: 'productType',
+      label: '产品类型'
+    },
+    {
+      key: 'productCode',
+      label: '产品编码'
+    },
+    {
+      key: 'productModel',
+      label: '产品型号'
+    },
+    {
+      key: 'productName',
+      label: '产品名称'
+    },
+    {
+      key: 'quantity',
+      label: '数量'
+    },
+    {
+      key: 'memo',
+      label: '说明'
+    },
+    {
+      key: 'leaseDuration',
+      label: '租赁月数'
+    },
+    {
+      key: 'dataSource',
+      label: '数据来源'
+    },
+    { key: 'creator', label: '创建人' },
+    { key: 'createTime', label: '创建时间' },
+    { key: 'updater', label: '更新人' },
+    { key: 'updateTime', label: '更新时间' }
+  ],
+  'configured-products': [
+    {
+      key: 'projectCode',
+      label: '清单项目编码'
+    },
+    {
+      key: 'executionNo',
+      label: '执行单号'
+    },
+    {
+      key: 'itemGroup',
+      label: '产品组'
+    },
+    {
+      key: 'productCode',
+      label: '产品编码'
+    },
+    {
+      key: 'parentCode',
+      label: '上级产品编码'
+    },
+    {
+      key: 'quantity',
+      label: '数量'
+    },
+    {
+      key: 'bomPath',
+      label: '产品层级路径'
+    },
+    { key: 'parentBomPaths', label: '上级配置路径' },
+    { key: 'parentGroupPaths', label: '上级配置组路径' },
+    { key: 'groupPaths', label: '配置组路径' },
+    { key: 'parentPathMissing', label: '上级配置缺失' },
+    {
+      key: 'productModel',
+      label: '产品型号'
+    },
+    {
+      key: 'productDescription',
+      label: '产品描述'
+    },
+    {
+      key: 'level',
+      label: '产品层级'
+    },
+    {
+      key: 'dataSource',
+      label: '数据来源'
+    },
+    { key: 'creator', label: '创建人' },
+    { key: 'createTime', label: '创建时间' },
+    { key: 'updater', label: '更新人' },
+    { key: 'updateTime', label: '更新时间' }
+  ],
+  plans: [
+    {
+      key: 'contractNo',
+      label: '合同号'
+    },
+    {
+      key: 'batchCode',
+      label: '计划批次'
+    },
+    {
+      key: 'collectionItemName',
+      label: '回款款项名称'
+    },
+    {
+      key: 'referenceEventName',
+      label: '参考事件名称'
+    },
+    {
+      key: 'eventPlannedAt',
+      label: '计划时间'
+    },
+    {
+      key: 'afterDays',
+      label: '间隔天数'
+    },
+    {
+      key: 'eventActualFinishedAt',
+      label: '实际完成时间'
+    },
+    {
+      key: 'marketingFeedback',
+      label: '销售反馈'
+    },
+    {
+      key: 'dataSource',
+      label: '来源系统'
+    },
+    {
+      key: 'createTime',
+      label: '创建时间'
+    },
+    {
+      key: 'creator',
+      label: '创建人'
+    },
+    {
+      key: 'updateTime',
+      label: '更新时间'
+    },
+    {
+      key: 'updater',
+      label: '更新人'
+    },
+    {
+      key: 'effectiveFrom',
+      label: '生效时间'
+    },
+    {
+      key: 'effectiveTo',
+      label: '失效时间'
+    }
+  ],
+  'shipment-packages': [
+    {
+      key: 'packageNo',
+      label: '装箱单号'
+    },
+    {
+      key: 'shipmentTime',
+      label: '发货时间'
+    },
+    {
+      key: 'warrantyStartTime',
+      label: '保修开始时间'
+    },
+    {
+      key: 'warrantyEndTime',
+      label: '保修结束时间'
+    },
+    {
+      key: 'receiverName',
+      label: '收货人'
+    },
+    {
+      key: 'expressNo',
+      label: '快递单号'
+    },
+    {
+      key: 'carrierName',
+      label: '承运商名称'
+    },
+    {
+      key: 'carrierCode',
+      label: '承运商编码'
+    }
+  ],
+  orders: [
+    {
+      key: 'contractNo',
+      label: '合同号'
+    },
+    {
+      key: 'executionNo',
+      label: '执行单号'
+    },
+    {
+      key: 'orderCreateTime',
+      label: '订单创建时间'
+    },
+    {
+      key: 'customerRequiredTime',
+      label: '客户要求时间'
+    },
+    {
+      key: 'customerCode',
+      label: '购货方编码'
+    },
+    {
+      key: 'customerName',
+      label: '购货方名称'
+    },
+    {
+      key: 'sourceProjectName',
+      label: '原项目名称'
+    },
+    {
+      key: 'orderComment',
+      label: '订单备注'
+    },
+    {
+      key: 'salesType',
+      label: '销售类型'
+    },
+    {
+      key: 'customInfo',
+      label: '附加业务信息'
+    },
+    {
+      key: 'companyCode',
+      label: '公司编码'
+    },
+    {
+      key: 'sourceSystem',
+      label: '来源ERP系统'
+    },
+    {
+      key: 'orderNo',
+      label: '销售订单号'
+    },
+    {
+      key: 'orderType',
+      label: '订单类型'
+    },
+    {
+      key: 'sourceSyncTime',
+      label: '最近同步时间'
+    },
+    {
+      key: 'sourceUpdatedAt',
+      label: '来源更新时间'
+    }
+  ],
+  'order-lines': [
+    {
+      key: 'productCode',
+      label: '产品编码'
+    },
+    {
+      key: 'productDesc',
+      label: '产品描述'
+    },
+    {
+      key: 'orderQty',
+      label: '订单数量'
+    },
+    {
+      key: 'openQty',
+      label: '未交数量'
+    },
+    {
+      key: 'bundleCode',
+      label: '组合编码'
+    },
+    {
+      key: 'warrantyMonth',
+      label: '保修月数'
+    },
+    {
+      key: 'lineType',
+      label: '订单行类型'
+    },
+    {
+      key: 'profitCenter',
+      label: '利润中心'
+    },
+    {
+      key: 'realExecutionNo',
+      label: '实际执行单号'
+    },
+    {
+      key: 'customInfo',
+      label: '附加业务信息'
+    },
+    {
+      key: 'companyCode',
+      label: '公司编码'
+    },
+    {
+      key: 'sourceSystem',
+      label: '来源ERP系统'
+    },
+    {
+      key: 'orderNo',
+      label: '销售订单号'
+    },
+    {
+      key: 'lineNo',
+      label: '订单行号'
+    },
+    {
+      key: 'unitCode',
+      label: '单位'
+    },
+    {
+      key: 'deliveredQty',
+      label: '已交数量'
+    },
+    {
+      key: 'quantityStatus',
+      label: '数量确认状态'
+    },
+    {
+      key: 'sourceSyncTime',
+      label: '最近同步时间'
+    },
+    {
+      key: 'sourceUpdatedAt',
+      label: '来源更新时间'
+    }
+  ],
+  executions: [
+    {
+      key: 'executionNo',
+      label: '执行单号'
+    },
+    {
+      key: 'sourceSystem',
+      label: '来源系统'
+    },
+    {
+      key: 'projectCode',
+      label: '项目编码'
+    },
+    {
+      key: 'projectName',
+      label: '项目名称'
+    },
+    {
+      key: 'salesRepCode',
+      label: '销售人员编码'
+    },
+    {
+      key: 'salesRepName',
+      label: '销售人员姓名'
+    },
+    {
+      key: 'marketCode',
+      label: '市场编码'
+    },
+    {
+      key: 'marketName',
+      label: '市场名称'
+    },
+    {
+      key: 'systemSourceKey',
+      label: '系统部来源编号'
+    },
+    {
+      key: 'systemName',
+      label: '系统部名称'
+    },
+    {
+      key: 'expendSourceKey',
+      label: '拓展部来源编号'
+    },
+    {
+      key: 'expendName',
+      label: '拓展部名称'
+    },
+    {
+      key: 'industryCode',
+      label: '行业编码'
+    },
+    {
+      key: 'industryName',
+      label: '行业名称'
+    },
+    {
+      key: 'departmentCode',
+      label: '办事处编码'
+    },
+    {
+      key: 'departmentName',
+      label: '办事处名称'
+    },
+    {
+      key: 'serviceTypeName',
+      label: '服务类型'
+    },
+    {
+      key: 'channelName',
+      label: '渠道名称'
+    },
+    {
+      key: 'engineeringFeeRaw',
+      label: '工程费用'
+    },
+    {
+      key: 'sourceObjectId',
+      label: 'CRM对象编号'
+    },
+    {
+      key: 'applyType',
+      label: '申请类型'
+    },
+    {
+      key: 'companyCode',
+      label: '公司编码'
+    },
+    {
+      key: 'customerProjectName',
+      label: '客户项目名称'
+    },
+    {
+      key: 'finalCustomerName',
+      label: '最终客户名称'
+    },
+    {
+      key: 'agentName',
+      label: '代理商名称'
+    },
+    {
+      key: 'majorProjectLevel',
+      label: '重大项目级别'
+    },
+    {
+      key: 'projectAmount',
+      label: '项目金额'
+    },
+    {
+      key: 'submitTime',
+      label: '提交时间'
+    },
+    {
+      key: 'predictedBidTime',
+      label: '预计中标时间'
+    },
+    {
+      key: 'contactName',
+      label: '联系人姓名'
+    },
+    {
+      key: 'contactPhone',
+      label: '联系电话'
+    }
+  ],
+  'factory-versions': [
+    { key: 'deviceSn', label: '原设备序列号' },
+    { key: 'conpVersion', label: 'CONP出厂版本' },
+    { key: 'bootVersion', label: 'BOOT出厂版本' },
+    { key: 'cpldVersion', label: 'CPLD出厂版本' },
+    { key: 'pcbVersion', label: 'PCB出厂版本' }
+  ],
+  project: [
+    { key: 'projectType', label: '项目业务类型' },
+    { key: 'projectCode', label: '项目编码' },
+    { key: 'projectName', label: '项目名称' },
+    { key: 'lifecycleStatus', label: '生命周期状态' },
+    { key: 'currentStage', label: '当前阶段' },
+    { key: 'closedAt', label: '闭环时间' },
+    { key: 'departmentCode', label: '办事处编码' },
+    { key: 'companyId', label: '公司ID' },
+    { key: 'companyCode', label: '公司编码' },
+    { key: 'companyName', label: '公司名称' },
+    { key: 'marketName', label: '市场部名称' },
+    { key: 'systemName', label: '系统部名称' },
+    { key: 'expendName', label: '拓展部名称' },
+    { key: 'industryName', label: '子行业名称' },
+    { key: 'notTrackReason', label: '不予跟踪原因' },
+    { key: 'orderCreateTime', label: '项目创建时订单创建时间' },
+    { key: 'projectCategory', label: '项目类别' },
+    { key: 'signingMethod', label: '签约方式' },
+    { key: 'implementationMode', label: '实施方式' },
+    { key: 'finalCustomerNames', label: '产品实际最终用户' },
+    { key: 'customerProjectName', label: '客户项目名称' },
+    { key: 'salesType', label: '销售类型' },
+    { key: 'majorProjectLevel', label: '重大项目级别' },
+    { key: 'creator', label: '创建人' },
+    { key: 'createTime', label: '创建时间' },
+    { key: 'updater', label: '更新人' },
+    { key: 'updateTime', label: '更新时间' },
+    { key: 'effectiveFrom', label: '项目有效期开始时间' },
+    { key: 'effectiveTo', label: '项目有效期结束时间' },
+    { key: 'disabled', label: '项目失效/核销标记' },
+    { key: 'projectStartTime', label: '项目开始时间' },
+    { key: 'projectRefreshTime', label: '项目刷新时间' },
+    { key: 'projectCloseTime', label: '项目闭环时间' },
+    { key: 'customInfo', label: '项目自定义业务信息' }
+  ]
+}
+export const getAssociatedData = (
+  projectId: LegacyId,
+  kind: AssociatedKind,
+  pageNo: number,
+  pageSize: number
+): Promise<{ list: AssociatedRow[]; total: number }> =>
+  request.get({
+    url: '/api/v1/pms/projects/' + projectId + '/associated-data/records',
+    params: { kind, pageNo, pageSize }
+  })
```

## 022 api/pms/project/legacy-detail.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增历史来源只读API契约。
- 证据及验证边界：ProjectLegacyDetailPanel。

### H01 @@ -0,0 +1,175 @@

裁决：保留纯新增功能。当前行来源：4d522dbc35e528ba385de0f257950b7250744251, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,175 @@
+import request from '@/config/axios'
+
+export type LegacyId = string | number
+export interface LegacyField {
+  key: string
+  label: string
+}
+export interface LegacyDomain {
+  code: string
+  label: string
+  group: string
+  sourceTable: string
+  keyField: string
+  fields: LegacyField[]
+}
+export interface LegacySource {
+  id: LegacyId
+  sourceSystem: string
+  sourceProjectKey: string
+  sourceContractNo: string
+  snapshotId: LegacyId
+  checksum: string
+  sourceReadAt: string
+  capturedAt: string
+  counts: Record<string, number>
+  domains: LegacyDomain[]
+  deferredDomains: string[]
+}
+export interface LegacyRecord {
+  id: LegacyId
+  domain: string
+  sourceTable: string
+  sourceKey: string
+  parentDomain?: string
+  parentSourceKey?: string
+  sourceUpdatedAt?: string
+  checksum: string
+  values: Record<string, string | null>
+  redactedFields: string[]
+  nativeStatus?: LegacyNativeStatus
+}
+export interface LegacyNativeTarget {
+  targetContext: string
+  targetObjectType: string
+  targetTable: string
+  targetId: LegacyId
+  targetRole: string
+  targetSequence: number
+}
+export interface LegacyNativeStatus {
+  classification: string
+  reasonCode?: string
+  reason?: string
+  targets: LegacyNativeTarget[]
+}
+export interface LegacyNativeDomainSummary {
+  domain: string
+  label: string
+  sourceCount: number
+  mappedCount: number
+  retainedCount: number
+  pendingCount: number
+  targetTables: string[]
+  retentionReasons: Record<string, number>
+}
+export interface LegacyNativeReport {
+  sourceId: LegacyId
+  snapshotId: LegacyId
+  sourceCount: number
+  mappedCount: number
+  retainedCount: number
+  pendingCount: number
+  domains: LegacyNativeDomainSummary[]
+}
+export interface LegacyRecordsQuery {
+  sourceId: LegacyId
+  snapshotId: LegacyId
+  domain: string
+  sourceKey?: string
+  parentDomain?: string
+  parentKey?: string
+  pageNo: number
+  pageSize: number
+}
+const base = (projectId: LegacyId) => `/api/v1/pms/projects/${projectId}/legacy-detail`
+export const getLegacySources = (projectId: LegacyId): Promise<LegacySource[]> =>
+  request.get({ url: `${base(projectId)}/sources` })
+export const getLegacyRecords = (
+  projectId: LegacyId,
+  params: LegacyRecordsQuery
+): Promise<{ list: LegacyRecord[]; total: number }> =>
+  request.get({ url: `${base(projectId)}/records`, params })
+export const getLegacyNativeReport = (
+  projectId: LegacyId,
+  params: { sourceId: LegacyId; snapshotId: LegacyId }
+): Promise<LegacyNativeReport> => request.get({ url: `${base(projectId)}/native-status`, params })
+export const getLegacyNativeRecordStatus = (
+  projectId: LegacyId,
+  params: LegacyRecordsQuery
+): Promise<Record<string, LegacyNativeStatus>> =>
+  request.get({ url: `${base(projectId)}/native-record-status`, params })
+export const legacyNativeStatusText = (status?: LegacyNativeStatus): string =>
+  status?.classification === 'MAPPED'
+    ? '已有目标记录'
+    : status?.classification === 'RETAINED'
+      ? '保留历史'
+      : status && status.classification !== 'NOT_MIGRATED'
+        ? '需核对'
+        : '待承接'
+export const legacyNativeTargetLabel = (table: string): string =>
+  (
+    ({
+      proj_project: '项目主档',
+      proj_project_party: '项目参与方',
+      com_contract: '合同',
+      com_sales_order: '销售订单',
+      com_sales_order_line: '销售订单行',
+      com_crm_execution_order: '执行单',
+      com_shipment_package: '发货包裹',
+      ast_device: '设备主档',
+      ast_device_shipment: '设备发货记录',
+      ast_device_project_relationship: '设备项目关系',
+      ast_device_network_version_event: '设备软件版本历史',
+      ast_device_warranty_record: '设备保修历史',
+      imp_eng_installation: '待核对安装地址'
+    }) as Record<string, string>
+  )[table] || '业务实体'
+
+// Historical BIT(1) bytes stay unchanged in evidence; render their numeric values visibly.
+export const legacyValueText = (value: string | null | undefined): string =>
+  value === '\u0000' ? '0' : value === '\u0001' ? '1' : (value ?? '—')
+
+export const legacyChildren: Record<string, string[]> = {
+  project: [
+    'state',
+    'parties',
+    'members',
+    'groups',
+    'executions',
+    'orders',
+    'products',
+    'shipment-facts',
+    'installations',
+    'tasks',
+    'maintenance',
+    'callbacks',
+    'subcontracts',
+    'notifications'
+  ],
+  groups: ['contracts'],
+  orders: ['order-lines'],
+  'software-changes': ['software-versions'],
+  callbacks: ['callback-questionnaires', 'approvals'],
+  questionnaires: ['questionnaire-answers'],
+  'questionnaire-templates': ['questionnaire-questions'],
+  'questionnaire-questions': ['questionnaire-options'],
+  subcontracts: ['subcontract-payments', 'subcontract-prices', 'approvals'],
+  weekly: ['weekly-items', 'weekly-feedback'],
+  tasks: ['tasks'],
+  instructions: ['instructions']
+}
+
+// These are source foreign keys, resolved inside the same imported snapshot.
+export const legacyReferences: Record<string, Record<string, { domain: string; label: string }>> = {
+  maintenance: { quesnaireId: { domain: 'questionnaires', label: '查看答卷' } },
+  supervision: { quesnaireId: { domain: 'questionnaires', label: '查看答卷' } },
+  'callback-questionnaires': { quesnaireId: { domain: 'questionnaires', label: '查看答卷' } },
+  questionnaires: {
+    quesnaireTemplateHeaderId: { domain: 'questionnaire-templates', label: '查看模板' }
+  },
+  'questionnaire-answers': {
+    quesnaireTemplateLineId: { domain: 'questionnaire-questions', label: '查看题目' },
+    questionTemplateOptId: { domain: 'questionnaire-options', label: '查看选项' }
+  }
+}
```

## 023 api/pms/project/projects/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：4；当前片段：4。
- 裁决：**保留新增字段及创建契约**。状态来源/时间为新增只读事实；salesOrderId和sourceFingerprint是新增合同创建链契约。
- 证据及验证边界：正式创建服务与useProjectCreationSource.spec.ts。

### H01 @@ -11,6 +11,7 @@

裁决：保留新增字段及创建契约。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -11,6 +11,7 @@
 
 /** 项目主档（列表行与详情共用） */
 export interface ProjectMasterVO {
+  statusAttributes?: Record<string, unknown>
   id?: number
   projectCode?: string
   codeRootId?: number
```

### H02 @@ -74,6 +75,9 @@

裁决：保留新增字段及创建契约。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -74,6 +75,9 @@
   businessType?: string
   notTrackReason?: string
   projectStartTime?: Date
+  projectRefreshTime?: Date
+  projectCloseTime?: Date
+  closedAt?: Date
   projectEndDate?: string
   createTime?: Date
 }
```

### H03 @@ -97,6 +101,8 @@

裁决：保留新增字段及创建契约。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -97,6 +101,8 @@
   customerName?: string
   contractNo?: string
   contractId?: number
+  salesOrderId?: number
+  sourceFingerprint?: string
   orderOfficeCompanyId: number
   orderOfficeDepartmentId: number
   sites?: ProjectSiteReqVO[]
```

### H04 @@ -579,6 +585,9 @@

裁决：保留新增字段及创建契约。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -579,6 +585,9 @@
 
 /** 创建字段匹配冻结的适用规则；组织编码与固定创建值由服务端解析。 */
 export interface ProjectMatchTemplatesReqVO {
+  contractId?: number
+  salesOrderId?: number
+  sourceFingerprint?: string
   projectName: string
   customerCode?: string
   implementationLocation?: string
```

## 024 api/pms/project/unified-members/index.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增历史字段**。新增迁移人员编码、登记来源、授权确认事实。
- 证据及验证边界：ProjectMembersPanel.runtime.spec.ts。

### H01 @@ -22,6 +22,10 @@

裁决：保留新增历史字段。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -22,6 +22,10 @@
 }
 export interface MemberRecord extends Omit<ProjectMemberAssignmentVO, 'userId'> {
   userId: number | null
+  memberCode?: string | null
+  phoneNumber?: string | null
+  registrationSource?: string | null
+  authorizationConfirmed?: boolean
   mobile?: string | null
   email?: string | null
   assignmentType?: string | null
```

## 025 components/BusinessEntity/ApprovalPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留类型修正**。仅字典类型收窄，原状态标签及行为相同。
- 证据及验证边界：类型检查。

### H01 @@ -292,8 +292,10 @@

裁决：保留类型修正。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -292,8 +292,10 @@
 
 const statusLabel = (status: string) =>
   ({ PENDING: '进行中', APPROVED: '已批准', REJECTED: '已驳回', WITHDRAWN: '已撤回' })[status] ?? status
-const statusTagType = (status: string) =>
-  ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' } as const)[status] ?? 'info'
+const statusTagTypes: Record<string, 'warning' | 'success' | 'danger' | 'info'> = {
+  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info'
+}
+const statusTagType = (status: string) => statusTagTypes[status] ?? 'info'
 const effectStatusLabel = (status: string) =>
   ({ SUCCESS: '成功', PENDING_RECOVERY: '待恢复', FAILED: '失败' })[status] ?? status
 </script>
```

## 026 components/BusinessEntity/BusinessEntityForm.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留新增适配**。losslessNumbers为可选新路径，不启用时保持原NUMBER/BOOLEAN控件；新直承接路径保留大整数文本。
- 证据及验证边界：BusinessEntityForm.runtime.spec.ts。

### H01 @@ -10,12 +10,16 @@

裁决：保留新增适配。当前行来源：a0a2287e1de9c5953599488e458cffd463b94b85。

```diff
@@ -10,12 +10,16 @@
       :prop="field.code"
       :rules="rules[field.code]"
     >
+      <el-input v-if="field.type === 'NUMBER' && losslessNumbers" v-model="form[field.code] as string" inputmode="decimal" />
       <el-input-number
-        v-if="field.type === 'NUMBER'"
+        v-else-if="field.type === 'NUMBER'"
         v-model="form[field.code] as number | undefined"
         :controls="false"
         class="w-full!"
       />
+      <el-select v-else-if="field.type === 'BOOLEAN' && losslessNumbers" v-model="form[field.code] as boolean" clearable>
+        <el-option label="是" :value="true" /><el-option label="否" :value="false" />
+      </el-select>
       <el-switch v-else-if="field.type === 'BOOLEAN'" v-model="form[field.code] as boolean" />
       <el-date-picker
         v-else-if="field.type === 'DATE'"
```

### H02 @@ -65,6 +69,8 @@

裁决：保留新增适配。当前行来源：a0a2287e1de9c5953599488e458cffd463b94b85。

```diff
@@ -65,6 +69,8 @@
   /** 打开实体的字段值；为空表示新建。 */
   initialValues?: Record<string, unknown>
   disabled?: boolean
+  /** Preserve bigint IDs/decimal values as text on the direct inherited path. */
+  losslessNumbers?: boolean
 }>()
 
 const formRef = ref()
```

## 027 components/BusinessEntity/BusinessEntityHost.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：12；当前片段：11。
- 裁决：**部分恢复**。恢复原表单extra槽及原按钮布局；新增删除独立于UPDATE表单禁用，仍按DELETE动作及原只读限制授权。仅在原交付面板未启用时显示新增默认交付，避免两个同功能区域。保留新增筛选和幂等限制。
- 证据及验证边界：BusinessEntityHost*.runtime.spec.ts。

### H01 @@ -65,6 +65,7 @@

裁决：部分恢复。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -65,6 +65,7 @@
         @create="openCreate"
         @open="openEntity"
         @reload="reloadList"
+        @search="(filters) => loadPage(filters, true)"
         @load-more="loadPage()"
       />
       <template v-else>
```

### H02 @@ -100,13 +101,16 @@

裁决：部分恢复。当前行来源：LOCAL_UNCOMMITTED, ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -100,13 +101,16 @@
                 :title="operation.reason"
                 @click="runCustom(operation)"
               >{{ operation.name }}</el-button>
-              <el-button :disabled="executing" @click="backToList">返回列表</el-button>
+              <el-button :disabled="executing || deleting" @click="backToList">返回列表</el-button>
               <el-button v-if="current" :disabled="executing" @click="reopen">
                 重新读取（重开）
               </el-button>
             </el-form-item>
           </template>
         </BusinessEntityForm>
+        <el-button v-if="current && !current.revisionId && deleteOperation" type="danger"
+          :loading="deleting" :disabled="executing || deleting || !deleteOperation.executable"
+          :title="deleteOperation.reason" @click="removeEntity">删除</el-button>
         <el-descriptions
           v-if="current"
           :column="2"
```

### H03 @@ -124,6 +128,12 @@

裁决：部分恢复。当前行来源：LOCAL_UNCOMMITTED, ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -124,6 +128,12 @@
             {{ current.available ? '可用' : current.unavailableReason }}
           </el-descriptions-item>
         </el-descriptions>
+        <DefaultBusinessDelivery
+          v-if="!deliveryEnabled && current && !current.revisionId"
+          :readonly="props.readonly || props.allowedActions !== undefined || !updateOperation?.executable"
+          :owner-module="props.ownerModule" :entity-type="props.entityType" :entity-id="current.ref.entityId"
+          :deliverable-type="props.deliverableType" @changed="reloadCurrent"
+        />
         <DeliveryPanel
           v-if="deliveryEnabled && current"
           :readonly="props.readonly || props.allowedActions !== undefined"
```

### H04 @@ -157,6 +167,7 @@

裁决：部分恢复。当前行来源：ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -157,6 +167,7 @@
 
 <script setup lang="ts">
 import { computed, onMounted, ref, shallowRef } from 'vue'
+import { useMessage } from '@/hooks/web/useMessage'
 import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
 import { isBusinessViewId } from '@/api/pms/platform/business-view/ids'
 import type { BusinessEntityData, BusinessOperationReceipt, OperationVO } from '@/api/pms/platform/businessmodel'
```

### H05 @@ -164,6 +175,7 @@

裁决：部分恢复。当前行来源：ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -164,6 +175,7 @@
 import BusinessEntityList from './BusinessEntityList.vue'
 import BusinessEntityForm from './BusinessEntityForm.vue'
 import DeliveryPanel from './DeliveryPanel.vue'
+import DefaultBusinessDelivery from './DefaultBusinessDelivery.vue'
 import ApprovalPanel from './ApprovalPanel.vue'
 import ContentHistoryPanel from './ContentHistoryPanel.vue'
 import { resolveStandaloneBusinessEntityView } from '@/components/BusinessView/registry'
```

### H06 @@ -173,8 +185,10 @@

裁决：部分恢复。当前行来源：4b76bb1d379ed3d38e6689d9efa66e11c0f21fba, ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83, ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -173,8 +185,10 @@
 
 defineOptions({ name: 'BusinessEntityHost' })
 const props = defineProps<{ ownerModule: string; entityType: string; initialEntityId?: string | number;
-  expectedStableCode?: string; readonly?: boolean; allowedActions?: string[] }>()
-
+  expectedStableCode?: string; deliverableType?: string; readonly?: boolean; allowedActions?: string[] }>()
+
+const message = useMessage()
+const deleting = ref(false)
 const entity = useBusinessEntity(
   () => props.ownerModule,
   () => props.entityType
```

### H07 @@ -219,7 +233,7 @@

裁决：部分恢复。当前行来源：ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -219,7 +233,7 @@
 })
 let leaving: Promise<boolean> | undefined
 const guardContentLeave = (): Promise<boolean> => {
-  if (executing.value) return Promise.resolve(false)
+  if (executing.value || deleting.value) return Promise.resolve(false)
   if (leaving) return leaving
   leaving = (async () => {
     try {
```

### H08 @@ -272,6 +286,7 @@

裁决：部分恢复。当前行来源：ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -272,6 +286,7 @@
   ? { ...operation, executable: false, reason: '当前运行入口未开放该操作' } : operation
 const createOperation = computed(() => availableOperation(declaredCreate.value))
 const updateOperation = computed(() => availableOperation(declaredUpdate.value))
+const deleteOperation = computed(() => availableOperation(detail.value?.operations.find(operation => operation.kind === 'DELETE')))
 const pageTitle = computed(() => (mode.value === 'FORM' ? '统一业务实体办理' : detail.value?.title || '统一业务实体'))
 const customOperations = computed(() => (detail.value?.operations || []).filter(operation => operation.kind === 'DOMAIN_COMMAND').map(operation => availableOperation(operation)!))
 const isCreate = computed(() => editingOperation.value?.kind === 'CREATE')
```

### H09 @@ -298,8 +313,9 @@

裁决：部分恢复。当前行来源：ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -298,8 +313,9 @@
   }
   if (detail.value && !professionalView.value) {
     await loadPage([], true)
-    if (pendingIntent.value) await recoverPending()
-    if (props.initialEntityId != null && !current.value) await openEntity({ ref: { entityId: props.initialEntityId } } as BusinessEntityData)
+    const recovered = pendingIntent.value ? await recoverPending() : undefined
+    if (props.initialEntityId != null && !current.value
+        && !(recovered?.outcome === 'DELETED' && String(recovered.entityRef?.entityId) === String(props.initialEntityId))) await openEntity({ ref: { entityId: props.initialEntityId } } as BusinessEntityData)
   }
 }
```

### H10 @@ -311,16 +327,21 @@

裁决：部分恢复。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55, ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -311,16 +327,21 @@
     const result = await recover()
     if (!result) return
     receipt.value = result
-    if (result.entityRef) {
+    if (result.outcome === 'DELETED') {
+      current.value = undefined
+      formPresentation.value = undefined
+      mode.value = 'LIST'
+    } else if (result.entityRef) {
       await readEntity(result.entityRef.entityId)
       editingOperation.value = updateOperation.value
       mode.value = 'FORM'
     }
     await reloadList()
+    return result
   } catch (error) { operationError.value = serverErrorMessage(error, '上次操作结果暂时无法确认') }
 }
 
-const reloadList = () => loadPage([], true)
+const reloadList = () => loadPage(undefined, true)
 
 const openCreate = () => {
   receipt.value = undefined
```

### H11 @@ -404,6 +425,28 @@

裁决：部分恢复。当前行来源：ad739326473e4d9e71102f90fbca96142d638d0e。

```diff
@@ -404,6 +425,28 @@
   }
 }
 
+const removeEntity = async () => {
+  const target = current.value, operation = deleteOperation.value
+  if (!target || target.revisionId || !operation?.executable || executing.value || deleting.value) return
+  deleting.value = true
+  try {
+    try { await message.confirm('确定删除当前业务记录？有关联引用或受保护历史的记录将由服务端拒绝删除。') }
+    catch { return }
+    // A stale confirmation must not delete a newly selected object or bypass changed entry restrictions.
+    if (current.value !== target || !deleteOperation.value?.executable || props.readonly) return
+    operationError.value = ''; conflictMessage.value = ''
+    const result = await execute(operation, target.ref.entityId, {}, target.concurrencyBasis)
+    receipt.value = result
+    if (result.outcome === 'DELETED') {
+      current.value = undefined; formPresentation.value = undefined; editingOperation.value = undefined
+      mode.value = 'LIST'
+      await reloadList()
+    }
+  } catch (error) {
+    operationError.value = serverErrorMessage(error, '删除失败，当前记录已保留，请刷新后重试')
+  } finally { deleting.value = false }
+}
+
 const runCustom = async (operation: OperationVO) => {
   if (!current.value || !operation.executable || props.readonly || executing.value) return
   operationError.value = ''
```

## 028 components/BusinessEntity/BusinessEntityList.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：4；当前片段：3。
- 裁决：**部分恢复**。恢复原列表最多8个业务字段；保留独立新增筛选功能。
- 证据及验证边界：businessListFilter.spec.ts。

### H01 @@ -1,6 +1,21 @@

裁决：部分恢复。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -1,6 +1,21 @@
 <template>
   <div class="business-entity-list">
     <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
+    <el-form inline @submit.prevent="search">
+      <el-form-item label="筛选字段">
+        <el-select v-model="filterField" placeholder="选择字段" style="width: 180px">
+          <el-option v-for="field in searchableFields" :key="field.code" :label="field.name" :value="field.code" />
+        </el-select>
+      </el-form-item>
+      <el-form-item label="筛选值">
+        <el-input v-model="filterValue" placeholder="文本包含匹配，其他字段精确匹配" clearable />
+      </el-form-item>
+      <el-form-item>
+        <el-button native-type="submit" type="primary" :loading="listLoading">查询</el-button>
+        <el-button :disabled="listLoading" @click="resetSearch">重置</el-button>
+      </el-form-item>
+      <el-alert v-if="filterError" :title="filterError" type="warning" :closable="false" />
+    </el-form>
     <div class="toolbar">
       <el-tooltip
         :disabled="!!createOperation?.executable"
```

### H02 @@ -42,10 +57,12 @@

裁决：部分恢复。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -42,10 +57,12 @@
 </template>
 
 <script setup lang="ts">
-import type { BusinessEntityData, FieldVO, OperationVO } from '@/api/pms/platform/businessmodel'
+import { computed, ref } from 'vue'
+import { buildBusinessListFilter } from './businessListFilter'
+import type { BusinessEntityData, FieldVO, OperationVO, FieldFilter } from '@/api/pms/platform/businessmodel'
 
 defineOptions({ name: 'BusinessEntityList' })
-defineProps<{
+const props = defineProps<{
   rows: BusinessEntityData[]
   readableFields: FieldVO[]
   createOperation?: OperationVO
```

### H03 @@ -53,7 +70,20 @@

裁决：部分恢复。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -53,7 +70,20 @@
   listError?: string
   sliceComplete?: boolean
 }>()
-const emit = defineEmits<{ create: []; open: [row: BusinessEntityData]; reload: []; 'load-more': [] }>()
+const emit = defineEmits<{ create: []; open: [row: BusinessEntityData]; reload: []; 'load-more': []; search: [filters: FieldFilter[]] }>()
+
+const filterField = ref('')
+const filterValue = ref('')
+const filterError = ref('')
+const searchableFields = computed(() => props.readableFields.filter(field => !['TEXT_LIST', 'OBJECT_LIST'].includes(field.type)))
+const search = () => {
+  filterError.value = ''
+  try { emit('search', buildBusinessListFilter(props.readableFields, filterField.value, filterValue.value)) }
+  catch (error: any) { filterError.value = error.message }
+}
+const resetSearch = () => {
+  filterField.value = ''; filterValue.value = ''; filterError.value = ''; emit('search', [])
+}
 
 const displayValue = (value: unknown, type: string) => {
   if (value == null || value === '') return '-'
```

## 029 components/BusinessEntity/ContentHistoryPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留类型兼容**。仅输入视图收窄，不在用户编辑前重写原始草稿值；审批历史不动。
- 证据及验证边界：ContentHistoryPanel.runtime.spec.ts。

### H01 @@ -53,21 +53,35 @@

裁决：保留类型兼容。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -53,21 +53,35 @@
       </h4>
       <el-form label-width="120px" class="mb-8px">
         <el-form-item v-for="field in writableFields" :key="field.code" :label="field.name">
-          <el-switch v-if="field.type === 'BOOLEAN'" v-model="(editForm[field.code] as boolean)" />
-          <el-input-number v-else-if="field.type === 'NUMBER'" v-model="(editForm[field.code] as number | null | undefined)" />
+          <el-switch
+            v-if="field.type === 'BOOLEAN'"
+            :model-value="booleanValue(editForm[field.code])"
+            @update:model-value="editForm[field.code] = $event"
+          />
+          <el-input-number
+            v-else-if="field.type === 'NUMBER'"
+            :model-value="numberValue(editForm[field.code])"
+            @update:model-value="editForm[field.code] = $event"
+          />
           <el-date-picker
             v-else-if="field.type === 'DATE'"
-            v-model="(editForm[field.code] as string)"
+            :model-value="dateValue(editForm[field.code])"
+            @update:model-value="editForm[field.code] = $event"
             type="date"
             value-format="YYYY-MM-DD"
           />
           <el-date-picker
             v-else-if="field.type === 'DATETIME'"
-            v-model="(editForm[field.code] as string)"
+            :model-value="dateValue(editForm[field.code])"
+            @update:model-value="editForm[field.code] = $event"
             type="datetime"
             value-format="YYYY-MM-DD'T'HH:mm:ss"
           />
-          <el-input v-else v-model="(editForm[field.code] as string)" />
+          <el-input
+            v-else
+            :model-value="textValue(editForm[field.code])"
+            @update:model-value="editForm[field.code] = $event"
+          />
         </el-form-item>
         <el-form-item v-if="selected.state === 'DRAFT'">
           <el-button type="primary" :loading="saving" @click="saveDraft">保存修订</el-button>
```

### H02 @@ -113,6 +127,17 @@

裁决：保留类型兼容。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -113,6 +127,17 @@
 const selected = ref<RevisionVO>()
 const createReason = ref('')
 const editForm = ref<Record<string, unknown>>({})
+// Narrow only the input view; keep the original value in the draft until the user edits it.
+const booleanValue = (value: unknown) => typeof value === 'boolean' ? value : undefined
+const numberValue = (value: unknown): number | null | undefined => {
+  if (typeof value === 'number' || value == null) return value
+  if (typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value))) return Number(value)
+  return undefined
+}
+const dateValue = (value: unknown): string | number | Date | null | undefined =>
+  typeof value === 'string' || typeof value === 'number' || value instanceof Date || value == null ? value : undefined
+const textValue = (value: unknown): string | number | null | undefined =>
+  typeof value === 'string' || typeof value === 'number' || value == null ? value : String(value)
 
 const writableFields = computed(() => (props.fields ?? []).filter((field) => field.writable))
 const hasOpenDraft = computed(() => revisions.value.some((revision) => revision.state === 'DRAFT'))
```

## 030 components/BusinessEntity/DefaultBusinessDelivery.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。默认交付登记/上传/记录查询为新增能力；已有DeliveryPanel启用时不再重复展示默认面板。
- 证据及验证边界：BusinessEntityHost.delivery.runtime.spec.ts。

### H01 @@ -0,0 +1,27 @@

裁决：保留纯新增功能。当前行来源：ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -0,0 +1,27 @@
+<template>
+  <el-alert v-if="error" :title="error" type="error" :closable="false" />
+  <template v-if="context">
+    <el-form inline><el-form-item label="交付件类型"><el-input v-model="type" aria-label="交付件类型" :disabled="uploader?.isUploading()" /></el-form-item></el-form>
+    <DefaultDeliveryUpload v-if="type" ref="uploader" :key="`${context.businessType}:${context.businessEntityKey}:${type}`"
+      v-bind="context" :deliverable-type="type" :readonly="readonly" @changed="$emit('changed')" />
+  </template>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import { deliveryContext, type DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
+import DefaultDeliveryUpload from './DefaultDeliveryUpload.vue'
+const props = withDefaults(defineProps<{ ownerModule: string; entityType: string; entityId: string | number; deliverableType?: string; readonly?: boolean }>(), { deliverableType: 'ATTACHMENT' })
+defineEmits<{ changed: [] }>()
+const context = ref<Omit<DeliveryScope, 'deliverableType'>>(), error = ref(''), type = ref(props.deliverableType)
+const uploader = ref<InstanceType<typeof DefaultDeliveryUpload>>()
+let generation = 0
+watch(() => [props.ownerModule, props.entityType, props.entityId], async () => {
+  const active = ++generation; context.value = undefined; error.value = ''
+  try {
+    const result = await deliveryContext(props.ownerModule, props.entityType, props.entityId)
+    if (!result || !/^[1-9][0-9]*$/.test(String(result.projectId)) || !result.businessType || String(result.businessEntityKey) !== String(props.entityId)) throw new Error('交付件业务上下文不匹配')
+    if (active === generation) context.value = result
+  }
+  catch (failure: any) { if (active === generation) error.value = failure?.message || '交付件业务上下文不可用' }
+}, { immediate: true })
+</script>
```

## 031 components/BusinessEntity/DefaultDeliveryRecords.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。默认交付登记/上传/记录查询为新增能力；已有DeliveryPanel启用时不再重复展示默认面板。
- 证据及验证边界：BusinessEntityHost.delivery.runtime.spec.ts。

### H01 @@ -0,0 +1,58 @@

裁决：保留纯新增功能。当前行来源：28617c7993b0c6edfc04c7e9def582cc02388510, ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -0,0 +1,58 @@
+<template>
+  <div class="default-delivery-records">
+    <el-alert v-if="error" :title="error" type="error" :closable="false" />
+    <el-table :data="rows" v-loading="loading" row-key="id">
+      <el-table-column prop="deliverableType" label="交付件类型" />
+      <el-table-column prop="businessType" label="业务类型" />
+      <el-table-column prop="businessEntityKey" label="业务实体键" />
+      <el-table-column label="标题"><template #default="{ row }">
+        <el-input v-if="!readonly" v-model="titles[row.id]" maxlength="255" :aria-label="`标题-${row.id}`" />
+        <span v-else>{{ row.title }}</span>
+      </template></el-table-column>
+      <el-table-column label="文件"><template #default="{ row }"><el-button link @click="download(row)">{{ row.fileName }}</el-button></template></el-table-column>
+      <el-table-column label="上传时间"><template #default="{ row }">{{ new Date(row.uploadedAt).toLocaleString() }}</template></el-table-column>
+      <el-table-column v-if="!readonly" label="操作"><template #default="{ row }">
+        <el-button link :disabled="loading" @click="edit(row)">保存标题</el-button>
+        <el-button link type="danger" :disabled="loading" @click="remove(row)">删除</el-button>
+      </template></el-table-column>
+    </el-table>
+    <el-button :disabled="loading" @click="load">刷新交付件</el-button>
+    <el-button v-if="pageNo > 1" :disabled="loading" @click="pageNo--; load()">上一页</el-button>
+    <el-button v-if="rows.length === 20" :disabled="loading" @click="pageNo++; load()">下一页</el-button>
+  </div>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import * as api from '@/api/pms/platform/businessmodel/delivery'
+const props = defineProps<{ projectId: string | number; deliverableType?: string; businessType?: string; businessEntityKey?: string | number; readonly?: boolean; api?: Pick<typeof api, 'listDeliveries' | 'editDelivery' | 'deleteDelivery' | 'downloadDelivery'> }>()
+const emit = defineEmits<{ changed: [] }>()
+const rows = ref<api.DeliveryRecord[]>([]), titles = ref<Record<string, string>>({}), loading = ref(false), error = ref(''), pageNo = ref(1)
+let generation = 0
+const load = async () => {
+  const active = ++generation; loading.value = true; error.value = ''; rows.value = []
+  try {
+    const result = await (props.api || api).listDeliveries({ projectId: props.projectId, deliverableType: props.deliverableType,
+      businessType: props.businessType, businessEntityKey: props.businessEntityKey, pageNo: pageNo.value, pageSize: 20 })
+    if (active === generation) { rows.value = result.list; titles.value = Object.fromEntries(result.list.map(row => [row.id, row.title])) }
+  } catch (failure: any) { if (active === generation) error.value = failure?.message || '交付件读取失败' }
+  finally { if (active === generation) loading.value = false }
+}
+const download = async (row: api.DeliveryRecord) => {
+  try { const ticket = await (props.api || api).downloadDelivery(row); window.open(ticket.shortLivedUrl, '_blank', 'noopener') }
+  catch (failure: any) { error.value = failure?.message || '文件读取失败' }
+}
+const edit = async (row: api.DeliveryRecord) => {
+  if (props.readonly || loading.value) return
+  loading.value = true; error.value = ''
+  try { await (props.api || api).editDelivery(row, titles.value[row.id]); await load(); emit('changed') }
+  catch (failure: any) { error.value = failure?.message || '保存失败'; loading.value = false }
+}
+const remove = async (row: api.DeliveryRecord) => {
+  if (props.readonly || loading.value) return
+  loading.value = true; error.value = ''
+  try { await (props.api || api).deleteDelivery(row); await load(); emit('changed') }
+  catch (failure: any) { error.value = failure?.message || '删除失败'; loading.value = false }
+}
+watch(() => [props.api, props.projectId, props.deliverableType, props.businessType, props.businessEntityKey], () => { pageNo.value = 1; load() }, { immediate: true })
+defineExpose({ reload: load })
+</script>
```

## 032 components/BusinessEntity/DefaultDeliveryUpload.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。默认交付登记/上传/记录查询为新增能力；已有DeliveryPanel启用时不再重复展示默认面板。
- 证据及验证边界：BusinessEntityHost.delivery.runtime.spec.ts。

### H01 @@ -0,0 +1,51 @@

裁决：保留纯新增功能。当前行来源：ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -0,0 +1,51 @@
+<template>
+  <div class="default-delivery-upload">
+    <h3>交付件</h3>
+    <el-tag :type="completed ? 'success' : 'info'">{{ completed ? '已有最新上传记录' : '尚无有效上传' }}</el-tag>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" />
+    <input v-if="!readonly" ref="input" type="file" aria-label="上传交付件" :disabled="uploading" @change="selected" />
+    <el-button v-if="pending && !readonly" :loading="uploading" @click="upload">重试上传</el-button>
+    <DefaultDeliveryRecords ref="records" :project-id="projectId" :deliverable-type="deliverableType"
+      :business-type="businessType" :business-entity-key="businessEntityKey" :readonly="readonly" @changed="changed" />
+  </div>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import * as api from '@/api/pms/platform/businessmodel/delivery'
+import type { DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
+import DefaultDeliveryRecords from './DefaultDeliveryRecords.vue'
+const props = defineProps<DeliveryScope & { readonly?: boolean }>()
+const emit = defineEmits<{ changed: [] }>()
+const input = ref<HTMLInputElement>(), records = ref<InstanceType<typeof DefaultDeliveryRecords>>()
+const completed = ref(false), uploading = ref(false), error = ref('')
+const pending = ref<{ scope: api.DeliveryScope; file: File; key: string }>()
+let generation = 0
+const scope = (): api.DeliveryScope => ({ projectId: props.projectId, businessType: props.businessType,
+  businessEntityKey: props.businessEntityKey, deliverableType: props.deliverableType })
+const check = async () => {
+  const active = ++generation; completed.value = false; error.value = ''
+  try { const result = await api.deliveryCompletion(scope()); if (active === generation) completed.value = result.completed }
+  catch (failure: any) { if (active === generation) error.value = failure?.message || '完成判断失败' }
+}
+const changed = async () => { await check(); emit('changed') }
+const selected = (event: Event) => {
+  const file = (event.target as HTMLInputElement).files?.[0]
+  if (!file || props.readonly || uploading.value) return
+  pending.value = { scope: scope(), file, key: crypto.randomUUID() }; upload()
+}
+const upload = async () => {
+  if (!pending.value || props.readonly || uploading.value) return
+  const attempt = pending.value, active = generation; uploading.value = true; error.value = ''
+  try {
+    await api.uploadDelivery(attempt.scope, attempt.file, attempt.key)
+    if (active === generation && pending.value === attempt) {
+      pending.value = undefined; if (input.value) input.value.value = ''; await records.value?.reload(); await changed()
+    }
+  } catch (failure: any) { if (active === generation) error.value = failure?.message || '上传失败，可重试' }
+  finally { uploading.value = false }
+}
+watch(() => [props.projectId, props.businessType, props.businessEntityKey, props.deliverableType], () => {
+  pending.value = undefined; if (input.value) input.value.value = ''; check()
+}, { immediate: true })
+defineExpose({ reload: changed, isUploading: () => uploading.value })
+</script>
```

## 033 components/BusinessEntity/businessListFilter.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增类型安全字段筛选；不增加业务权限。
- 证据及验证边界：businessListFilter.spec.ts。

### H01 @@ -0,0 +1,24 @@

裁决：保留纯新增功能。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -0,0 +1,24 @@
+import type { FieldFilter, FieldVO } from '@/api/pms/platform/businessmodel'
+
+/** Build structured filters only; field names and values never become SQL text. */
+export function buildBusinessListFilter(fields: FieldVO[], code: string, raw: string): FieldFilter[] {
+  if (!code && !raw.trim()) return []
+  const field = fields.find(field => field.code === code && field.readable)
+  if (!field) throw new Error('请选择可查询的字段')
+  const value = raw.trim()
+  if (!value) return []
+  if (field.type === 'TEXT') return [{ fieldCode: code, operator: 'LIKE', values: [value] }]
+  if (field.type === 'NUMBER') {
+    if (!/^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/.test(value)) throw new Error('请输入有效数字')
+    // Preserve exact Long/decimal values rather than rounding through JavaScript Number.
+    return [{ fieldCode: code, operator: 'EQ', values: [value] }]
+  }
+  if (field.type === 'BOOLEAN') {
+    if (!['true', 'false', '是', '否'].includes(value)) throw new Error('布尔字段请输入 true 或 false')
+    return [{ fieldCode: code, operator: 'EQ', values: [value === 'true' || value === '是'] }]
+  }
+  if (field.type === 'DATE' || field.type === 'DATETIME') {
+    return [{ fieldCode: code, operator: 'EQ', values: [value] }]
+  }
+  throw new Error('该字段不支持默认筛选')
+}
```

## 034 components/BusinessEntity/useBusinessEntity.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留新增筛选配套**。独立新增筛选功能需保留游标翻页条件，并阻止重复加载和旧对象结果覆盖。
- 证据及验证边界：useBusinessEntity.runtime.spec.ts。

### H01 @@ -76,15 +76,27 @@

裁决：保留新增筛选配套。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -76,15 +76,27 @@
   const cursor = ref<string>()
   const sliceComplete = ref(false)
 
+  const activeFilters = ref<FieldFilter[]>([])
+  let pageGeneration = 0
+
   const loadPage = async (filters?: FieldFilter[], reset = false) => {
+    // Repeated load-more clicks must not append the same cursor twice.
+    if (listLoading.value && !reset && filters === undefined) return
+    if (filters !== undefined) {
+      activeFilters.value = filters.map(filter => ({ ...filter, values: [...(filter.values || [])] }))
+    }
+    const generation = ++pageGeneration
+    const owner = ownerModule(), type = entityType()
+    const currentRequest = () => generation === pageGeneration && owner === ownerModule() && type === entityType()
     listLoading.value = true
     listError.value = ''
     try {
-      const slice: BusinessEntitySlice = await getEntityPage(ownerModule(), entityType(), {
+      const slice: BusinessEntitySlice = await getEntityPage(owner, type, {
         pageSize: 20,
-        filters,
+        filters: activeFilters.value.map(filter => ({ ...filter, values: [...(filter.values || [])] })),
         cursor: reset ? undefined : cursor.value
       })
+      if (!currentRequest()) return
       if (slice.completeness === 'UNAVAILABLE') {
         listError.value = slice.unavailableReason || '查询暂不可用'
         return
```

### H02 @@ -93,9 +105,9 @@

裁决：保留新增筛选配套。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -93,9 +105,9 @@
       cursor.value = slice.nextCursor
       sliceComplete.value = slice.completeness === 'COMPLETE' || !slice.nextCursor
     } catch (error: any) {
-      listError.value = serverErrorMessage(error, '实体查询失败')
+      if (currentRequest()) listError.value = serverErrorMessage(error, '实体查询失败')
     } finally {
-      listLoading.value = false
+      if (generation === pageGeneration) listLoading.value = false
     }
   }
```

### H03 @@ -190,6 +202,7 @@

裁决：保留新增筛选配套。当前行来源：5f222515cd9e64e6cac6e887ed89386941e74e55。

```diff
@@ -190,6 +202,7 @@
     rows,
     sliceComplete,
     loadPage,
+    activeFilters,
     current,
     formPresentation,
     readEntity,
```

## 035 components/DeliveryArtifact/DeliveryUploader.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容**。仅FileId/null引用兼容，避免新文件接口返回字符串Long时丢精度。
- 证据及验证边界：既有附件和交付测试；类型检查。

### H01 @@ -9,6 +9,7 @@

裁决：保留文件兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -9,6 +9,7 @@
 </template>
 <script setup lang="ts">
 import { computed, ref } from 'vue'
+import type { FileId } from '@/api/pms/platform/file'
 import { type DeliveryMaterialVO, type DeliveryTypeVO } from '@/api/pms/platform/delivery'
 import { createDeliveryUploadAttempt, uploadDeliveryFile, type DeliveryUploadAttempt } from './uploadDeliveryFile'
```

### H02 @@ -24,7 +25,7 @@

裁决：保留文件兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -24,7 +25,7 @@
 const fileInput = ref<HTMLInputElement>()
 const busy = ref(false)
 const error = ref('')
-const uploadedReferenceId = ref<number>()
+const uploadedReferenceId = ref<FileId>()
 const attempt = ref<DeliveryUploadAttempt>()
 const accept = computed(() => {
   try { return (JSON.parse(props.type.allowedMediaJson) as string[]).map(value => value.includes('/') || value.startsWith('.') ? value : `.${value}`).join(',') }
```

## 036 components/DeliveryArtifact/uploadDeliveryFile.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容**。仅FileId/null引用兼容，避免新文件接口返回字符串Long时丢精度。
- 证据及验证边界：既有附件和交付测试；类型检查。

### H01 @@ -1,5 +1,5 @@

裁决：保留文件兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -1,5 +1,5 @@
 import { generateUUID } from '@/utils'
-import { initializeUpload, completeUpload } from '@/api/pms/platform/file'
+import { initializeUpload, completeUpload, type FileId } from '@/api/pms/platform/file'
 import { registerMaterial, type EntityOwnerParam, type DeliveryMaterialVO, type DeliveryTypeVO } from '@/api/pms/platform/delivery'
 
 /** One recoverable attempt shared by manual uploads and generated business documents. */
```

### H02 @@ -13,7 +13,7 @@

裁决：保留文件兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -13,7 +13,7 @@
   initKey: string
   completeKey: string
   initialized?: Awaited<ReturnType<typeof initializeUpload>>
-  referenceId?: number
+  referenceId?: FileId
   material?: DeliveryMaterialVO
 }
 export const createDeliveryUploadAttempt = (owner: EntityOwnerParam, type: DeliveryTypeVO,
```

## 037 components/DeviceCollection/semantic/types.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留类型兼容**。FileId无损类型传递，不修改设备命令或业务动作。
- 证据及验证边界：类型检查。

### H01 @@ -189,4 +189,3 @@

裁决：保留类型兼容。当前行来源：SOURCE_BASELINE_DELETION_ONLY。

```diff
@@ -189,4 +189,3 @@
   semanticResult: SemanticParseResult
   createdAt: string
 }
-
```

## 038 components/PmsFileArtifact/PmsFileReferenceList.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：4；当前片段：4。
- 裁决：**保留测试修复及兼容**。保留Long文件ID、无附件槽发现、对象切换/卸载后的旧回调隔离；5MiB调用方限制配套原生附件替换，不增大原业务上传上限。
- 证据及验证边界：PmsFileUploadRefresh.runtime.spec.ts及组件既有文件测试。

### H01 @@ -69,7 +69,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -69,7 +69,7 @@
   defineProps<
     FileBusinessKey & {
       categoryLabel?: string
-      artifactId?: number
+      artifactId?: FileApi.FileId
       versionNo?: number
       editable?: boolean
       ownerExecutionContext?: JsonObject
```

### H02 @@ -89,6 +89,9 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e, b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -89,6 +89,9 @@
 const previewRef = ref<InstanceType<typeof PmsFilePreviewDialog>>()
 const historyRef = ref<InstanceType<typeof PmsFileVersionDrawer>>()
 const detachAttempt = ref<{ signature: string; idempotencyKey: string }>()
+let loadSequence = 0
+let ownerGeneration = 0
+let detachSequence = 0
 const businessKey = computed<FileBusinessKey>(() => ({
   ownerContext: props.ownerContext,
   objectType: props.objectType,
```

### H03 @@ -98,22 +101,33 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e, b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -98,22 +101,33 @@
 }))
 
 const load = async () => {
+  const sequence = ++loadSequence
+  const selectedKey = { ...businessKey.value }
+  const selectedArtifactId = props.artifactId
   artifact.value = undefined
   errorText.value = ''
-  if (!props.artifactId) return
+  if (!selectedKey.objectId || !selectedKey.referenceKey) { loading.value = false; return }
   loading.value = true
   try {
-    artifact.value = await FileApi.getArtifact(props.artifactId, businessKey.value)
-    emit('loaded', artifact.value)
+    const discovered = selectedArtifactId ? undefined : await FileApi.getReference(selectedKey)
+    if (sequence !== loadSequence) return
+    const id = selectedArtifactId || discovered?.artifactId
+    if (!id) return
+    const current = await FileApi.getArtifact(id, selectedKey)
+    if (sequence !== loadSequence) return
+    artifact.value = current || undefined
+    if (current) emit('loaded', current)
   } catch {
-    errorText.value = '文件事实已变化，请刷新业务页面后重试。'
+    if (sequence === loadSequence) errorText.value = '文件事实已变化，请刷新业务页面后重试。'
   } finally {
-    loading.value = false
+    if (sequence === loadSequence) loading.value = false
   }
 }
 const openAccess = async (operation: FileAccessOperation) => {
   if (!artifact.value || downloading.value) return
   const selected = artifact.value
+  const generation = ownerGeneration
+  const isCurrent = () => generation === ownerGeneration
   const versionNo = props.versionNo || selected.reference.versionNo
   const selectedKey = { ...businessKey.value }
   errorText.value = ''
```

### H04 @@ -130,64 +144,98 @@

裁决：保留测试修复及兼容。当前行来源：4f982308c387d0bf50b66f1c2c142813540979b4, 6ef804f0a46185ae0fb102b0932c23fcb2892d1e。

```diff
@@ -130,64 +144,98 @@
       selected.artifactId,
       props.versionNo || selected.reference.versionNo,
       operation,
-      businessKey.value
+      selectedKey
     )
+    if (!isCurrent()) {
+      return
+    }
     if (operation === 'DOWNLOAD') {
       // A cross-origin navigation may display the file inline. Download the authorized
       // bytes via the existing Blob helper without forwarding application credentials.
       // https://developer.mozilla.org/en-US/docs/Web/API/Response/blob
       const response = await fetch(ticket.shortLivedUrl, { credentials: 'omit' })
+      if (!isCurrent()) return
       if (!response.ok) throw new Error('FILE_DOWNLOAD_FAILED')
-      download.file(await response.blob(), selected.name)
+      const blob = await response.blob()
+      if (!isCurrent()) return
+      download.file(blob, selected.name)
     }
   } catch {
+    if (!isCurrent()) return
     errorText.value = '文件下载未完成，请检查权限、存储连接及跨域配置后重试。'
   } finally {
-    downloading.value = false
+    if (isCurrent()) downloading.value = false
   }
 }
 const detach = async () => {
   if (!artifact.value) return
   const selected = artifact.value.reference
+  const generation = ownerGeneration
+  const sequence = ++detachSequence
+  const isCurrent = () => generation === ownerGeneration && sequence === detachSequence
   const selectedKey = { ...businessKey.value }
   const execution = props.ownerExecutionContext
-    ? structuredClone(toRaw(props.ownerExecutionContext)) : undefined
-  const prompt = await message.prompt('请输入解绑原因', '解除材料引用')
-  const signature = JSON.stringify([
-    selected.referenceId,
-    selected.referenceVersion,
-    selectedKey,
-    execution,
-    prompt.value
-  ])
-  if (detachAttempt.value?.signature !== signature) {
-    detachAttempt.value = { signature, idempotencyKey: generateUUID() }
-  }
-  const result = await FileApi.detachReference(
-    selected.referenceId,
-    selected.referenceVersion,
-    selectedKey,
-    prompt.value,
-    detachAttempt.value.idempotencyKey,
-    execution
-  )
-  detachAttempt.value = undefined
-  message.success('材料引用已解除')
-  artifact.value = undefined
-  emit('detached', { ...result, referenceKey: selectedKey.referenceKey })
+    ? structuredClone(toRaw(props.ownerExecutionContext))
+    : undefined
+  try {
+    const prompt = await message.prompt('请输入解绑原因', '解除材料引用')
+    if (!isCurrent()) return
+    const signature = JSON.stringify([
+      selected.referenceId,
+      selected.referenceVersion,
+      selectedKey,
+      execution,
+      prompt.value
+    ])
+    if (detachAttempt.value?.signature !== signature) {
+      detachAttempt.value = { signature, idempotencyKey: generateUUID() }
+    }
+    const selectedAttempt = detachAttempt.value
+    const result = await FileApi.detachReference(
+      selected.referenceId,
+      selected.referenceVersion,
+      selectedKey,
+      prompt.value,
+      selectedAttempt.idempotencyKey,
+      execution
+    )
+    if (!isCurrent() || detachAttempt.value !== selectedAttempt) return
+    detachAttempt.value = undefined
+    message.success('材料引用已解除')
+    artifact.value = undefined
+    emit('detached', { ...result, referenceKey: selectedKey.referenceKey })
+  } catch (error) {
+    if (isCurrent()) throw error
+  }
 }
 const statusLabel = (status: string) =>
   ({ ACTIVE: '已绑定', DETACHED: '已解绑', ARCHIVED: '已归档' })[status] || status
 
 watch(
-  () => [props.artifactId, props.versionNo, businessKey.value],
+  () => [
+    props.artifactId,
+    props.versionNo,
+    props.ownerContext,
+    props.objectType,
+    props.objectId,
+    props.purposeCode,
+    props.referenceKey
+  ],
   () => {
+    ++ownerGeneration
+    ++detachSequence
     previewRef.value?.close()
     detachAttempt.value = undefined
+    downloading.value = false
     load()
   },
-  { immediate: true }
+  { immediate: true, flush: 'sync' }
 )
+onBeforeUnmount(() => {
+  ++loadSequence
+  ++ownerGeneration
+  ++detachSequence
+})
 defineExpose({ refresh: load, detach })
 </script>
```

## 039 components/PmsFileArtifact/PmsFileUploader.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：10；当前片段：10。
- 裁决：**保留测试修复及兼容**。保留Long文件ID、无附件槽发现、对象切换/卸载后的旧回调隔离；5MiB调用方限制配套原生附件替换，不增大原业务上传上限。
- 证据及验证边界：PmsFileUploadRefresh.runtime.spec.ts及组件既有文件测试。

### H01 @@ -16,7 +16,7 @@

裁决：保留测试修复及兼容。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -16,7 +16,7 @@
       <div class="el-upload__text">拖放文件到这里，或<em>点击选择</em></div>
       <template #tip>
         <div class="upload-tip">
-          单文件不超过 50MB；上传完成后由服务端执行适用校验，是否执行安全扫描由部署配置决定。
+          单文件不超过 {{ fileSizeLimitMiB }}MiB；上传完成后由服务端执行适用校验，是否执行安全扫描由部署配置决定。
         </div>
       </template>
     </el-upload>
```

### H02 @@ -44,7 +44,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -44,7 +44,7 @@
 import type { UploadFile, UploadInstance } from 'element-plus'
 import { useMessage } from '@/hooks/web/useMessage'
 import * as FileApi from '@/api/pms/platform/file'
-import type { FileBusinessKey } from '@/api/pms/platform/file'
+import type { FileBusinessKey, FileId } from '@/api/pms/platform/file'
 import type { JsonObject } from '@/api/pms/platform/dynamic-form'
 import type { FileSelection } from './types'
 import { resolveFileUploadMode } from './useFileSlotState'
```

### H03 @@ -54,10 +54,11 @@

裁决：保留测试修复及兼容。当前行来源：LOCAL_UNCOMMITTED, b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -54,10 +54,11 @@
 const props = withDefaults(
   defineProps<
     FileBusinessKey & {
-      artifactId?: number | string
+      artifactId?: FileId
       expectedReferenceVersion?: number
       categoryCode: string
       accept?: string
+      maxSizeMiB?: number
       disabled?: boolean
       hideSubmit?: boolean
       autoSubmit?: boolean
```

### H04 @@ -66,6 +67,7 @@

裁决：保留测试修复及兼容。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -66,6 +67,7 @@
   >(),
   {
     accept: '.pdf,.png,.jpg,.jpeg',
+    maxSizeMiB: 50,
     disabled: false
   }
 )
```

### H05 @@ -79,12 +81,14 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e, LOCAL_UNCOMMITTED。

```diff
@@ -79,12 +81,14 @@
 const progress = ref(0)
 const stage = ref<'UPLOADING' | 'VALIDATING'>('UPLOADING')
 const uploadMode = computed(() => resolveFileUploadMode(props.artifactId))
+const fileSizeLimitMiB = computed(() => Math.min(50, props.maxSizeMiB))
 const attempt = ref<{
   initKey: string
   completeKey: string
   ownerExecutionContext?: JsonObject
   initialized?: FileApi.FileUploadInitRespVO
 }>()
+let generation = 0
 
 const businessKey = (): FileBusinessKey => ({
   ownerContext: props.ownerContext,
```

### H06 @@ -93,12 +97,22 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e。

```diff
@@ -93,12 +97,22 @@
   purposeCode: props.purposeCode,
   referenceKey: props.referenceKey
 })
+const identity = () =>
+  JSON.stringify([
+    businessKey(),
+    props.artifactId,
+    props.expectedReferenceVersion,
+    props.categoryCode
+  ])
 
 const selectFile = (file: UploadFile) => {
   if (selectedFile.value !== file.raw) {
+    ++generation
+    busy.value = false
     attempt.value = undefined
     selectedExecutionContext.value = props.ownerExecutionContext
-      ? structuredClone(toRaw(props.ownerExecutionContext)) : undefined
+      ? structuredClone(toRaw(props.ownerExecutionContext))
+      : undefined
   }
   uploadFailed.value = false
   selectedFile.value = file.raw
```

### H07 @@ -107,6 +121,8 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e。

```diff
@@ -107,6 +121,8 @@
   })
 }
 const removeFile = () => {
+  ++generation
+  busy.value = false
   uploadFailed.value = false
   selectedFile.value = undefined
   selectedExecutionContext.value = undefined
```

### H08 @@ -126,23 +142,36 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e, LOCAL_UNCOMMITTED。

```diff
@@ -126,23 +142,36 @@
 const submit = async () => {
   const file = selectedFile.value
   if (!file || props.disabled || busy.value) return
-  if (file.size <= 0 || file.size > 50 * 1024 * 1024) {
+  if (file.size <= 0 || file.size > fileSizeLimitMiB.value * 1024 * 1024) {
     uploadFailed.value = true
-    return message.error('文件大小必须在 50MB 以内')
+    return message.error(`文件大小必须在 ${fileSizeLimitMiB.value}MiB 以内`)
   }
   uploadFailed.value = false
   busy.value = true
   progress.value = 0
   stage.value = 'UPLOADING'
+  const selectedGeneration = generation
+  const selectedIdentity = identity()
+  const selectedKey = businessKey()
+  attempt.value ||= {
+    initKey: generateUUID(),
+    completeKey: generateUUID(),
+    ownerExecutionContext: selectedExecutionContext.value
+  }
+  const selectedAttempt = attempt.value
+  const isCurrent = () =>
+    generation === selectedGeneration &&
+    identity() === selectedIdentity &&
+    attempt.value === selectedAttempt
   try {
-    attempt.value ||= { initKey: generateUUID(), completeKey: generateUUID(),
-      ownerExecutionContext: selectedExecutionContext.value }
     const initialized =
-      attempt.value.initialized ||
+      selectedAttempt.initialized ||
       (await FileApi.initializeUpload(
         {
-          ...businessKey(),
-          ...(attempt.value.ownerExecutionContext ? { ownerExecutionContext: attempt.value.ownerExecutionContext } : {}),
+          ...selectedKey,
+          ...(selectedAttempt.ownerExecutionContext
+            ? { ownerExecutionContext: selectedAttempt.ownerExecutionContext }
+            : {}),
           modeCode: uploadMode.value,
           artifactId: props.artifactId,
           expectedReferenceVersion: props.expectedReferenceVersion,
```

### H09 @@ -151,20 +180,23 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e。

```diff
@@ -151,20 +180,23 @@
           declaredSizeBytes: file.size,
           declaredMediaType: fileUploadMediaType(file)
         },
-        attempt.value.initKey
+        selectedAttempt.initKey
       ))
-    attempt.value.initialized = initialized
+    if (!isCurrent()) return
+    selectedAttempt.initialized = initialized
     const completed = await FileApi.completeUpload(
       initialized.artifactId,
       initialized.sessionId,
       file,
-      attempt.value.completeKey,
+      selectedAttempt.completeKey,
       (value) => {
+        if (!isCurrent()) return
         progress.value = value
         if (value >= 100) stage.value = 'VALIDATING'
       },
-      attempt.value.ownerExecutionContext
+      selectedAttempt.ownerExecutionContext
     )
+    if (!isCurrent()) return
     progress.value = 100
     message.success('文件已通过服务端校验并绑定')
     emit('completed', completed)
```

### H10 @@ -172,12 +204,24 @@

裁决：保留测试修复及兼容。当前行来源：6ef804f0a46185ae0fb102b0932c23fcb2892d1e, 7e689621c56d763ad496eced59c32e292f25f84c, d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -172,12 +204,24 @@
     attempt.value = undefined
     uploadRef.value?.clearFiles()
   } catch (error) {
-    uploadFailed.value = true
-    throw error
+    if (isCurrent()) { uploadFailed.value = true; throw error }
   } finally {
-    busy.value = false
-  }
-}
+    if (generation === selectedGeneration && identity() === selectedIdentity) busy.value = false
+  }
+}
+watch(
+  identity,
+  () => {
+    removeFile()
+    progress.value = 0
+    stage.value = 'UPLOADING'
+    uploadRef.value?.clearFiles()
+  },
+  { flush: 'sync' }
+)
+onBeforeUnmount(() => {
+  ++generation
+})
 defineExpose({ submit, isBusy: () => busy.value, hasPendingFile: () => !!selectedFile.value })
 </script>
```

## 040 components/PmsFileArtifact/PmsFileVersionDrawer.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留测试修复及兼容**。保留Long文件ID、无附件槽发现、对象切换/卸载后的旧回调隔离；5MiB调用方限制配套原生附件替换，不增大原业务上传上限。
- 证据及验证边界：PmsFileUploadRefresh.runtime.spec.ts及组件既有文件测试。

### H01 @@ -54,7 +54,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -54,7 +54,7 @@
 const drawerSize = computed(() => (narrow.value ? '100%' : '720px'))
 const visible = ref(false)
 const loading = ref(false)
-const artifactId = ref<number>()
+const artifactId = ref<FileApi.FileId>()
 const key = ref<FileBusinessKey>()
 const items = ref<FileVersionVO[]>([])
 const cursor = ref<string>()
```

### H02 @@ -76,7 +76,7 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -76,7 +76,7 @@
     loading.value = false
   }
 }
-const open = (id: number, businessKey: FileBusinessKey) => {
+const open = (id: FileApi.FileId, businessKey: FileBusinessKey) => {
   artifactId.value = id
   key.value = businessKey
   items.value = []
```

## 041 components/PmsFileArtifact/types.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留测试修复及兼容**。保留Long文件ID、无附件槽发现、对象切换/卸载后的旧回调隔离；5MiB调用方限制配套原生附件替换，不增大原业务上传上限。
- 证据及验证边界：PmsFileUploadRefresh.runtime.spec.ts及组件既有文件测试。

### H01 @@ -1,9 +1,9 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -1,9 +1,9 @@
-import type { FileLifecycleResultVO } from '@/api/pms/platform/file'
+import type { FileId, FileLifecycleResultVO } from '@/api/pms/platform/file'
 
 export interface FileSelection {
-  artifactId: number
+  artifactId: FileId
   versionNo: number
-  referenceId: number
+  referenceId: FileId
   referenceKey: string
 }
```

## 042 components/PmsFileArtifact/useFileSlotState.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留测试修复及兼容**。保留Long文件ID、无附件槽发现、对象切换/卸载后的旧回调隔离；5MiB调用方限制配套原生附件替换，不增大原业务上传上限。
- 证据及验证边界：PmsFileUploadRefresh.runtime.spec.ts及组件既有文件测试。

### H01 @@ -1,20 +1,20 @@

裁决：保留测试修复及兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -1,20 +1,20 @@
 import { reactive } from 'vue'
-import type { FileArtifactVO, FileUploadMode } from '@/api/pms/platform/file'
+import type { FileArtifactVO, FileId, FileUploadMode } from '@/api/pms/platform/file'
 import type { DetachedFileSlot, FileSelection } from './types'
 
 export interface FileSlotState {
-  artifactId?: number
+  artifactId?: FileId
   referenceKey?: string
   referenceVersion?: number
 }
 
-export const resolveFileUploadMode = (artifactId?: number | string): FileUploadMode =>
+export const resolveFileUploadMode = (artifactId?: FileId): FileUploadMode =>
   artifactId ? 'ADD_VERSION' : 'CREATE_ARTIFACT'
 
 export const useFileSlotState = () => {
   const state = reactive<FileSlotState>({})
 
-  const reset = (artifactId?: number, referenceKey?: string) => {
+  const reset = (artifactId?: FileId, referenceKey?: string) => {
     state.artifactId = artifactId
     state.referenceKey = referenceKey
     state.referenceVersion = undefined
```

## 043 components/ProjectBusiness/ProjectBusinessDeliveries.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接入并列明重叠**。新增直承接API宿主及默认交付接入，与原通用BusinessEntityHost有表单/列表功能重叠，原专业阶段页不替换；共用列表恢复原8字段，删除/筛选/回执和新增归集仍由各Owner授权。并非整文件都可认作纯新增业务。
- 证据及验证边界：对应ProjectBusiness组件既有测试；本轮未执行写操作。

### H01 @@ -0,0 +1,90 @@

裁决：保留新增接入并列明重叠。当前行来源：88dca0e89a04f5f7eeb1c57ec721de296917131f。

```diff
@@ -0,0 +1,90 @@
+<template>
+  <section class="project-business-deliveries">
+    <h3>交付件</h3>
+    <el-form inline>
+      <el-form-item label="交付件类型"><el-input v-model="type" :disabled="mutating" /></el-form-item>
+      <el-tag :type="completed ? 'success' : 'info'">{{ completed ? '已有最新有效上传' : '尚无有效上传' }}</el-tag>
+    </el-form>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" />
+    <input v-if="!readonly" ref="fileInput" type="file" aria-label="上传交付件" :disabled="mutating || !scope" @change="selectFile" />
+    <el-button v-if="pending && !readonly" :disabled="mutating" @click="upload">重试原上传</el-button>
+    <el-table :data="rows" v-loading="fetching || mutating" row-key="id">
+      <el-table-column prop="deliverableType" label="交付件类型" />
+      <el-table-column label="标题"><template #default="{ row }">
+        <el-input v-if="!readonly" v-model="titles[row.id]" :disabled="mutating" maxlength="255" />
+        <span v-else>{{ row.title }}</span>
+      </template></el-table-column>
+      <el-table-column label="文件"><template #default="{ row }"><el-button link @click="download(row)">{{ row.fileName }}</el-button></template></el-table-column>
+      <el-table-column prop="uploadedAt" label="上传时间" />
+      <el-table-column v-if="!readonly" label="操作"><template #default="{ row }">
+        <el-button :disabled="mutating" link @click="edit(row)">保存标题</el-button>
+        <el-button :disabled="mutating" link type="danger" @click="remove(row)">删除交付件</el-button>
+      </template></el-table-column>
+    </el-table>
+    <el-button :disabled="mutating || fetching" @click="refresh">刷新交付件</el-button>
+    <el-button v-if="page > 1" :disabled="mutating || fetching" @click="page--; refresh()">上一页</el-button>
+    <el-button v-if="page * 20 < total" :disabled="mutating || fetching" @click="page++; refresh()">下一页</el-button>
+  </section>
+</template>
+<script setup lang="ts">
+import { ref, watch, onBeforeUnmount } from 'vue'
+import { useMessage } from '@/hooks/web/useMessage'
+import type { ProjectBusinessApi, BusinessId } from '@/api/pms/platform/business'
+import type { DeliveryRecord, DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
+const props = withDefaults(defineProps<{ api: ProjectBusinessApi; entityId: BusinessId; readonly?: boolean; deliverableType?: string }>(), { deliverableType: 'ATTACHMENT' })
+const message = useMessage(), type = ref(props.deliverableType)
+const scope = ref<DeliveryScope>(), rows = ref<DeliveryRecord[]>([]), titles = ref<Record<string,string>>({})
+const completed = ref(false), error = ref(''), fetching = ref(false), mutating = ref(false), page = ref(1), total = ref(0)
+const fileInput = ref<HTMLInputElement>(), pending = ref<{ file:File;scope:DeliveryScope;key:string }>()
+let generation = 0
+const failureMessage = (failure:any) => failure?.response?.data?.msg || failure?.message || '交付件操作失败'
+const refresh = async () => {
+  const active=++generation, client=props.api, id=props.entityId, deliverableType=type.value
+  fetching.value=true; error.value=''; scope.value=undefined; completed.value=false
+  try {
+    if(!/^[A-Za-z0-9_.:-]{1,64}$/.test(deliverableType))throw new Error('交付件类型不合法')
+    const context=await client.deliveryContext(id)
+    if(String(context.businessEntityKey)!==String(id) || !context.businessType || !/^[1-9][0-9]*$/.test(String(context.projectId)))throw new Error('交付件归属不匹配')
+    const target={...context,deliverableType}
+    const [list,result]=await Promise.all([client.deliveries(id,deliverableType,page.value),client.completion(target)])
+    if(active!==generation)return
+    scope.value=target;rows.value=list.list;total.value=list.total;completed.value=result.completed
+    titles.value=Object.fromEntries(list.list.map(row=>[row.id,row.title]))
+  } catch(failure) {if(active===generation){rows.value=[];error.value=failureMessage(failure)}}
+  finally {if(active===generation)fetching.value=false}
+}
+const selectFile = (event:Event) => {
+  const file=(event.target as HTMLInputElement).files?.[0]
+  if(!file || !scope.value || props.readonly || mutating.value)return
+  pending.value={file,scope:{...scope.value},key:crypto.randomUUID()};upload()
+}
+const upload = async () => {
+  if(!pending.value || props.readonly || mutating.value)return
+  const attempt=pending.value, active=generation, client=props.api
+  mutating.value=true;error.value=''
+  try {
+    await client.upload(attempt.scope,attempt.file,attempt.key)
+    if(active===generation){pending.value=undefined;if(fileInput.value)fileInput.value.value='';await refresh()}
+  } catch(failure){if(active===generation)error.value=failureMessage(failure)}
+  finally{mutating.value=false}
+}
+const edit = async (row:DeliveryRecord) => {
+  if(props.readonly || mutating.value)return
+  const client=props.api,id=props.entityId,active=generation,title=titles.value[row.id];mutating.value=true;error.value=''
+  try{await client.editDelivery(id,row,title);if(active===generation)await refresh()}
+  catch(failure){if(active===generation)error.value=failureMessage(failure)}finally{mutating.value=false}
+}
+const remove = async (row:DeliveryRecord) => {
+  if(props.readonly || mutating.value)return
+  const client=props.api,id=props.entityId,active=generation;mutating.value=true;error.value=''
+  try {
+    try{await message.confirm('确定删除该交付件记录？归档或被引用的记录将由服务端保护。')}catch{return}
+    if(active!==generation || props.readonly)return
+    await client.removeDelivery(id,row);if(active===generation)await refresh()
+  }catch(failure){if(active===generation)error.value=failureMessage(failure)}finally{mutating.value=false}
+}
+const download=async(row:DeliveryRecord)=>{try{const ticket=await props.api.downloadDelivery(props.entityId,row);window.open(ticket.shortLivedUrl,'_blank','noopener')}catch(failure){error.value=failureMessage(failure)}}
+watch(()=>[props.api.base,props.entityId,type.value],()=>{pending.value=undefined;page.value=1;rows.value=[];refresh()},{immediate:true})
+onBeforeUnmount(()=>++generation)
+defineExpose({isBusy:()=>mutating.value,refresh})
+</script>
```

## 044 components/ProjectBusiness/ProjectBusinessPage.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接入并列明重叠**。新增直承接API宿主及默认交付接入，与原通用BusinessEntityHost有表单/列表功能重叠，原专业阶段页不替换；共用列表恢复原8字段，删除/筛选/回执和新增归集仍由各Owner授权。并非整文件都可认作纯新增业务。
- 证据及验证边界：对应ProjectBusiness组件既有测试；本轮未执行写操作。

### H01 @@ -0,0 +1,120 @@

裁决：保留新增接入并列明重叠。当前行来源：744e86b7acc086881c0b4473193419eb3ecfe5f4, 88dca0e89a04f5f7eeb1c57ec721de296917131f, a0a2287e1de9c5953599488e458cffd463b94b85, e7e601909a738879fdf8709ed4869c8a53341179。

```diff
@@ -0,0 +1,120 @@
+<template>
+  <ContentWrap>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" />
+    <el-alert v-if="pending" title="有一次业务操作结果尚未确认" type="warning" :closable="false">
+      <el-button :loading="executing" @click="recoverOperation">查询操作回执</el-button>
+    </el-alert>
+    <el-alert v-if="receipt" :title="`操作结果：${receipt.outcome}`" :type="receipt.outcome === 'FAILED' ? 'error' : 'success'" :closable="false" />
+    <h3>{{ title || model?.title || '业务办理' }}</h3>
+    <BusinessEntityList v-if="!editing" :rows="rows" :readable-fields="readableFields" :create-operation="createOperation"
+      :list-loading="loading" :slice-complete="rows.length >= total" @reload="loadPage(true)" @search="filters => loadPage(true, filters)"
+      @load-more="loadPage(false)" @create="create" @open="row => edit(row.ref.entityId)" />
+    <template v-else>
+      <BusinessEntityForm ref="form" :writable-fields="writableFields" :initial-values="current?.fieldValues"
+        :fields="model?.fields" :presentation="presentation" lossless-numbers :disabled="busy || !saveOperation?.executable || readonly || current?.available === false" />
+      <BusinessEntityForm v-if="current && extraFields.length" ref="extraForm" :writable-fields="extraFields" :initial-values="presentation?.extensions.fields"
+        lossless-numbers :disabled="busy || !saveOperation?.executable || readonly" />
+      <slot name="business-fields" :current="current" :execute="runAction" :busy="busy" :actions="businessActions" />
+      <div class="business-actions">
+        <el-button type="primary" :loading="executing" :disabled="busy || readonly || !saveOperation?.executable || current?.available === false" @click="save">保存</el-button>
+        <el-button v-if="current && deleteOperation" type="danger" :disabled="busy || readonly || !deleteOperation.executable" @click="remove">删除</el-button>
+        <slot name="actions" :current="current" :api="api" :reload="reloadCurrent" :busy="busy" :execute="runAction">
+          <template v-if="current"><el-button v-for="action in businessActions.filter(action=>!hiddenActions.includes(action.code))" :key="action.code" :disabled="busy || readonly || !action.executable" @click="runAction(action.code)">{{ action.name }}</el-button></template>
+        </slot>
+        <el-button v-if="current" :disabled="busy" @click="reloadCurrent">重新读取</el-button>
+        <el-button :disabled="busy" @click="back">返回列表</el-button>
+      </div>
+      <el-descriptions v-if="current && readonlyFields.length" title="只读信息" :column="2" border>
+        <el-descriptions-item v-for="field in readonlyFields" :key="field.code" :label="field.name">{{ current.fieldValues[field.code] ?? '-' }}</el-descriptions-item>
+      </el-descriptions>
+      <ProjectBusinessDeliveries v-if="current" ref="deliveries" :key="String(current.ref.entityId)" :api="api" :entity-id="current.ref.entityId"
+        :readonly="readonly || !updateOperation?.executable" :deliverable-type="deliverableType" />
+      <slot name="details" :current="current" :api="api" />
+    </template>
+  </ContentWrap>
+</template>
+<script setup lang="ts">
+import { computed, ref, watch } from 'vue'
+import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
+import { useMessage } from '@/hooks/web/useMessage'
+import type { BusinessEntityData, BusinessEntityFormData, FieldVO, OperationVO } from '@/api/pms/platform/businessmodel'
+import { createProjectBusinessApi, type BusinessId } from '@/api/pms/platform/business'
+import BusinessEntityList from '../BusinessEntity/BusinessEntityList.vue'
+import BusinessEntityForm from '../BusinessEntity/BusinessEntityForm.vue'
+import ProjectBusinessDeliveries from './ProjectBusinessDeliveries.vue'
+import { useProjectBusiness } from './useProjectBusiness'
+const props = withDefaults(defineProps<{ apiBase: string; title?: string; readonly?: boolean; deliverableType?: string; hiddenActions?: string[]; operationAllowed?: (code:string,current?:BusinessEntityData)=>boolean }>(), { deliverableType: 'ATTACHMENT', hiddenActions:()=>[] })
+const api = computed(() => createProjectBusinessApi(props.apiBase)), message = useMessage()
+const state = useProjectBusiness(() => api.value)
+const { model, current, rows, total, error, loading, executing, receipt, pending, readableFields, writableFields, loadPage } = state
+const editing = ref(false), confirming = ref(false), formLoading=ref(false)
+const presentation=ref<BusinessEntityFormData>(), extraForm=ref<InstanceType<typeof BusinessEntityForm>>()
+let presentationGeneration=0
+const extraFields=computed<FieldVO[]>(()=>{
+  const mapped=new Set(Object.values(presentation.value?.layout?.binding.fieldBindings || {}))
+  return (presentation.value?.definitions || []).filter(field=>!mapped.has(field.code)).map(field=>({code:field.code,name:field.label,type:field.type,required:field.required,readable:true,writable:true}))
+})
+watch(current,async row=>{
+  const generation=++presentationGeneration;presentation.value=undefined;formLoading.value=false
+  if(!row?.available)return
+  formLoading.value=true
+  try{const value=await api.value.form(row.ref.entityId);if(generation===presentationGeneration)presentation.value=value}
+  catch(failure){if(generation===presentationGeneration)error.value='业务表单读取失败，请重新读取后再保存'}
+  finally{if(generation===presentationGeneration)formLoading.value=false}
+})
+const form = ref<InstanceType<typeof BusinessEntityForm>>(), deliveries = ref<InstanceType<typeof ProjectBusinessDeliveries>>()
+const busy = computed(() => executing.value || confirming.value || formLoading.value || !!deliveries.value?.isBusy())
+const effective = (action:OperationVO|undefined) => action ? {...action,executable:action.executable && (!props.operationAllowed || props.operationAllowed(action.code,current.value))} : undefined
+const createOperation = computed(() => { const action=effective(state.operation('CREATE')); return props.readonly && action ? { ...action, executable:false } : action })
+const updateOperation = computed(() => effective(state.operation('UPDATE'))), deleteOperation = computed(() => effective(state.operation('DELETE')))
+const businessActions = computed(()=>model.value?.operations.filter(action=>action.kind==='DOMAIN_COMMAND' && action.code!=='save-form').map(action=>effective(action)!) || [])
+const saveOperation = computed(() => current.value ? updateOperation.value : createOperation.value)
+const readonlyFields = computed(() => readableFields.value.filter(field => !field.writable))
+watch(() => props.apiBase, async () => { editing.value=false; await state.load() }, { immediate:true })
+const create = () => { if (busy.value || props.readonly || !createOperation.value?.executable) return; current.value=undefined;editing.value=true }
+const edit = async (id: BusinessId) => { if (!busy.value && await state.open(id)) editing.value=true }
+const save = async () => {
+  if (busy.value || props.readonly) return
+  try {
+    const input=await form.value!.buildInput(),extras=await extraForm.value?.buildInput()
+    if(current.value && !presentation.value){message.warning('请先重新读取业务表单');return}
+    if(extras && Object.keys(extras).length){
+      const layoutPatch=input.$extensions as {values:Record<string,unknown>}|undefined
+      input.$extensions={definitionRevisionId:presentation.value!.layout?.binding.extensionDefinitionRevisionId ?? presentation.value!.extensions.definitionRevisionId,
+        expectedVersion:presentation.value!.extensions.version,values:{...layoutPatch?.values,...extras}}
+    }
+    const formSave=!!current.value && (!!presentation.value?.layout || !!input.$extensions)
+    const result=await state.execute(current.value ? formSave?'save-form':'save' : 'create',formSave?{values:input}:input)
+    if(result && result.outcome!=='FAILED')editing.value=true
+  }
+  catch { /* Field controls retain their validation messages. */ }
+}
+const runAction = async (code:string,values:Record<string,unknown> = {}) => {
+  if(busy.value || props.readonly || !current.value)return
+  const action=businessActions.value.find(value=>value.code===code);if(!action?.executable)return
+  const changes=await form.value?.buildInput(),extraChanges=await extraForm.value?.buildInput();if(changes && Object.keys(changes).length || extraChanges && Object.keys(extraChanges).length){message.warning('请先保存当前修改，再执行业务操作');return}
+  const selected=current.value;confirming.value=true
+  try {
+    try{await message.confirm(`确定执行“${action.name}”？`)}catch{return}
+    if(current.value!==selected || props.readonly)return
+    const result=await state.execute(code,values);if(result?.outcome==='DELETED')editing.value=false
+    return result
+  }finally{confirming.value=false}
+}
+const remove = async () => {
+  if (busy.value || props.readonly || !current.value || !deleteOperation.value?.executable) return
+  const selected=current.value;confirming.value=true
+  try {
+    try { await message.confirm('确定删除当前业务记录？有关联交付件或历史引用的记录不能删除。') } catch { return }
+    if(current.value!==selected || props.readonly)return
+    const result=await state.execute('delete');if(result?.outcome==='DELETED')editing.value=false
+  } finally {confirming.value=false}
+}
+const recoverOperation = async () => { const result=await state.recover();if(result)editing.value=result.outcome!=='DELETED' && !!current.value }
+const back = () => { if(!busy.value){editing.value=false;loadPage(true)} }
+const reloadCurrent = async () => { if(current.value)await state.open(current.value.ref.entityId) }
+const requestLeave = () => !busy.value
+onBeforeRouteLeave(requestLeave);onBeforeRouteUpdate(requestLeave)
+defineExpose({ requestLeave, reload:state.load })
+</script>
+<style scoped>.business-actions{display:flex;gap:8px;margin:12px 0}</style>
```

## 045 components/ProjectBusiness/ProjectDeliveryCollection.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接入并列明重叠**。新增直承接API宿主及默认交付接入，与原通用BusinessEntityHost有表单/列表功能重叠，原专业阶段页不替换；共用列表恢复原8字段，删除/筛选/回执和新增归集仍由各Owner授权。并非整文件都可认作纯新增业务。
- 证据及验证边界：对应ProjectBusiness组件既有测试；本轮未执行写操作。

### H01 @@ -0,0 +1,19 @@

裁决：保留新增接入并列明重叠。当前行来源：28617c7993b0c6edfc04c7e9def582cc02388510。

```diff
@@ -0,0 +1,19 @@
+<template>
+  <ContentWrap>
+    <h3>项目交付件归集</h3>
+    <el-form inline>
+      <el-form-item label="项目"><PmsEntitySelect v-model="projectId" :api="ProjectApi.getProjectPage" label-field="projectName" value-field="id" query-field="projectName" /></el-form-item>
+      <el-form-item label="交付件类型"><el-input v-model="deliverableType" clearable aria-label="归集交付件类型" /></el-form-item>
+    </el-form>
+    <DefaultDeliveryRecords v-if="projectId" :api="collection" :project-id="projectId" :deliverable-type="deliverableType || undefined" readonly />
+    <el-empty v-else description="选择项目查看归集记录" />
+  </ContentWrap>
+</template>
+<script setup lang="ts">
+import { ref } from 'vue'
+import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
+import * as ProjectApi from '@/api/pms/project/projects'
+import * as collection from '@/api/pms/platform/business/deliveryCollection'
+import DefaultDeliveryRecords from '../BusinessEntity/DefaultDeliveryRecords.vue'
+const projectId=ref<string|number>(),deliverableType=ref('')
+</script>
```

## 046 components/ProjectBusiness/useProjectBusiness.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接入并列明重叠**。新增直承接API宿主及默认交付接入，与原通用BusinessEntityHost有表单/列表功能重叠，原专业阶段页不替换；共用列表恢复原8字段，删除/筛选/回执和新增归集仍由各Owner授权。并非整文件都可认作纯新增业务。
- 证据及验证边界：对应ProjectBusiness组件既有测试；本轮未执行写操作。

### H01 @@ -0,0 +1,104 @@

裁决：保留新增接入并列明重叠。当前行来源：88dca0e89a04f5f7eeb1c57ec721de296917131f, a0a2287e1de9c5953599488e458cffd463b94b85。

```diff
@@ -0,0 +1,104 @@
+import { computed, ref, shallowRef } from 'vue'
+import type { BusinessEntityData, BusinessOperationReceipt, FieldFilter, ModelDetailVO } from '@/api/pms/platform/businessmodel'
+import type { BusinessId, ProjectBusinessApi } from '@/api/pms/platform/business'
+import { businessIntentStorageKey, readBusinessIntent, writeBusinessIntent, clearBusinessIntent,
+  businessIntentFingerprint, type PendingBusinessIntent } from '../BusinessEntity/businessOperationIntent'
+
+const message = (error: any, fallback: string) => error?.response?.data?.msg || error?.message || fallback
+export function useProjectBusiness(api: () => ProjectBusinessApi) {
+  const model = shallowRef<ModelDetailVO>(), current = shallowRef<BusinessEntityData>()
+  const rows = shallowRef<BusinessEntityData[]>([]), total = ref(0), page = ref(1), filters = ref<FieldFilter[]>([])
+  const error = ref(''), loading = ref(false), executing = ref(false), receipt = shallowRef<BusinessOperationReceipt>()
+  const pending = shallowRef<PendingBusinessIntent>()
+  let generation = 0, detailGeneration = 0
+  const storageKey = () => businessIntentStorageKey('DIRECT_BUSINESS_API', api().base)
+  const operation = (kind: string) => model.value?.operations.find(item => item.kind === kind)
+  const readableFields = computed(() => model.value?.fields.filter(field => field.readable) || [])
+  const writableFields = computed(() => model.value?.fields.filter(field => field.writable) || [])
+  const loadPage = async (reset = true, search?: FieldFilter[]) => {
+    if (loading.value && !reset) return
+    if (search) filters.value = search.map(filter => ({ ...filter, values: [...(filter.values || [])] }))
+    const active = ++generation, client = api(), next = reset ? 1 : page.value + 1
+    loading.value = true; error.value = ''
+    try {
+      const result = await client.page(next, 20, filters.value)
+      if (active !== generation || client.base !== api().base) return
+      rows.value = reset ? result.list : [...rows.value, ...result.list]; total.value = result.total; page.value = next
+    } catch (failure) { if (active === generation) error.value = message(failure, '业务列表读取失败') }
+    finally { if (active === generation) loading.value = false }
+  }
+  const load = async () => {
+    const client = api(), active = ++detailGeneration
+    ++generation; current.value = undefined; rows.value = []; model.value = undefined; receipt.value = undefined; filters.value = []; error.value = ''
+    try {
+      const detail = await client.model()
+      if (active !== detailGeneration || client.base !== api().base) return
+      model.value = detail; pending.value = readBusinessIntent(storageKey()); await loadPage(true)
+    } catch (failure) { if (active === detailGeneration) error.value = message(failure, '业务模型读取失败') }
+  }
+  const open = async (id: BusinessId) => {
+    const client = api(), active = ++detailGeneration; error.value = ''
+    try {
+      const data = await client.get(id)
+      if (active !== detailGeneration || client.base !== api().base) return false
+      if (!data.available || String(data.ref.entityId) !== String(id)) throw new Error('业务读取身份不匹配或已不可用')
+      current.value = data; return true
+    } catch (failure) { if (active === detailGeneration) error.value = message(failure, '业务读取失败'); return false }
+  }
+  const applyReceipt = async (result: BusinessOperationReceipt) => {
+    receipt.value = result
+    if (result.outcome === 'DELETED') current.value = undefined
+    else if (result.outcome !== 'FAILED' && result.entityRef) {
+      // Preserve committed identity if the follow-up read fails; never offer another create as a retry.
+      current.value = { ref: result.entityRef, concurrencyBasis: result.newConcurrencyBasis,
+        fieldValues: {}, available: false, unavailableReason: '操作已成功，详情读取尚未完成' }
+      await open(result.entityRef.entityId)
+    }
+    await loadPage(true)
+  }
+  const execute = async (action: string, values: Record<string, unknown> = {}) => {
+    if (executing.value) return
+    const client = api(), target = current.value, key = storageKey()
+    if (!key) { error.value = '登录上下文不可用，不能安全保存操作回执'; return }
+    const allowed = ['create','save','delete'].includes(action) ? operation(action === 'create' ? 'CREATE' : action === 'save' ? 'UPDATE' : 'DELETE') : model.value?.operations.find(item=>item.code===action && item.kind==='DOMAIN_COMMAND')
+    if (!allowed?.executable || action !== 'create' && (!target || target.concurrencyBasis == null)) { error.value = allowed?.reason || '当前操作不可执行'; return }
+    executing.value = true; error.value = ''
+    let attempt: PendingBusinessIntent | undefined, previous = false
+    try {
+      const fingerprint = await businessIntentFingerprint({ base: client.base, action, values,
+        id: action === 'create' ? undefined : String(target!.ref.entityId), version: action === 'create' ? undefined : target!.concurrencyBasis })
+      const stored = readBusinessIntent(key); previous = !!stored
+      if (stored && stored.fingerprint !== fingerprint) throw new Error('上次操作结果仍未知，请先确认回执')
+      attempt = stored || { key: crypto.randomUUID(), fingerprint, operation: action, operationVersion: 1,
+        entityId: action === 'create' ? undefined : String(target!.ref.entityId), concurrencyBasis: action === 'create' ? undefined : target!.concurrencyBasis }
+      writeBusinessIntent(key, attempt); pending.value = attempt
+      const result = action === 'create' ? await client.create(values, attempt.key)
+        : action === 'save' ? await client.update(target!.ref.entityId, values, target!.concurrencyBasis!, attempt.key)
+          : action === 'delete' ? await client.remove(target!.ref.entityId, target!.concurrencyBasis!, attempt.key)
+            : await client.action(action,target!.ref.entityId,target!.concurrencyBasis!,attempt.key,values)
+      clearBusinessIntent(key, attempt)
+      if (client.base === api().base) { pending.value = readBusinessIntent(key); await applyReceipt(result) }
+      return result
+    } catch (failure: any) {
+      const status = failure?.response?.status ?? failure?.status, code = failure?.response?.data?.code ?? failure?.data?.code
+      if (attempt && !previous && (failure === 'error' || (status >= 400 && status < 500 && status !== 408) || (code != null && Number(code) !== 500))) clearBusinessIntent(key, attempt)
+      if (client.base === api().base) { pending.value = readBusinessIntent(key); error.value = message(failure, '业务操作失败，请先确认执行结果再重试') }
+    } finally { executing.value = false }
+  }
+  const recover = async () => {
+    if (executing.value) return
+    const client = api(), key = storageKey(), intent = readBusinessIntent(key)
+    if (!key || !intent) return
+    executing.value = true; error.value = ''
+    try {
+      const result = await client.receipt(intent.operation, intent.key)
+      if (!result) { error.value = '尚未取得回执，请恢复原输入后使用原请求重试'; return }
+      clearBusinessIntent(key, intent)
+      if (client.base === api().base) { pending.value = readBusinessIntent(key); await applyReceipt(result) }
+      return result
+    } catch (failure) { if (client.base === api().base) error.value = message(failure, '回执查询失败') }
+    finally { executing.value = false }
+  }
+  return { model, current, rows, total, filters, error, loading, executing, receipt, pending,
+    readableFields, writableFields, operation, load, loadPage, open, execute, recover }
+}
```

## 047 components/UserTag/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：True；本轮前片段：2；当前片段：2。
- 裁决：**保留测试修复**。Java Long用户ID以字符串匹配，避免真实人员显示丢失。
- 证据及验证边界：UserTag.runtime.spec.ts; z01010周文成回读。

### H01 @@ -16,11 +16,11 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -16,11 +16,11 @@
 })
 
 // 模块级缓存：避免同一用户ID重复请求
-const userCache = new Map<number, string>()
+const userCache = new Map<string, string>()
 
 const displayName = ref<string>('')
 
-const loadUserName = async (id: number) => {
+const loadUserName = async (id: string) => {
   if (!id) {
     displayName.value = '-'
     return
```

### H02 @@ -44,7 +44,7 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -44,7 +44,7 @@
 watch(
   () => props.userId,
   (val) => {
-    if (val) loadUserName(Number(val))
+    if (val) loadUserName(String(val))
     else displayName.value = '-'
   },
   { immediate: true }
```

## 048 router/modules/remaining.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增入口**。保留割接列表、工勘、默认交付件隐藏路由；路由存在不授予业务权限。
- 证据及验证边界：既有路由与BusinessView授权测试。

### H01 @@ -33,6 +33,24 @@

裁决：保留新增入口。当前行来源：103e925d34c772afa3552b3cf7f84441dd96a938, 231a53bc0284c390636bcf5ec2ec646a27fb93da, LOCAL_UNCOMMITTED, ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -33,6 +33,24 @@
  }
  **/
 const remainingRouter: AppRouteRecordRaw[] = [
+  {
+    path: '/pms/cutover/tasks', component: Layout, name: 'PmsCutoverTaskListEntryRoot',
+    meta: { hidden: true },
+    children: [{ path: '', name: 'PmsCutoverTaskListEntry', component: () => import('@/views/pms/cutover/tasks/index.vue'),
+      meta: { hidden: true, canTo: true, noCache: true, title: '割接任务列表', activeMenu: '/pms/cutover/tasks' } }]
+  },
+  {
+    path: '/pms/business/site-surveys',
+    name: 'PmsInheritedSiteSurveys',
+    component: () => import('@/views/pms/business/site-survey/index.vue'),
+    meta: { hidden: true, canTo: true, title: '现场工勘' }
+  },
+  {
+    path: '/pms/deliverables',
+    name: 'PmsDefaultDeliverables',
+    component: () => import('@/views/pms/platform/deliverables/index.vue'),
+    meta: { hidden: true, canTo: true, title: '项目交付件归集' }
+  },
   {
     path: '/pms/inspection/tasks/:taskId', component: Layout, name: 'PmsInspectionTaskDetailRoot',
     meta: { hidden: true },
```

## 049 store/modules/permission.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留等价格式**。只有函数调用格式差异，原角色过滤顺序和作用相同。
- 证据及验证边界：stagePlanMenuVisibility.spec.ts。

### H01 @@ -50,14 +50,7 @@

裁决：保留等价格式。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -50,14 +50,7 @@
           res = roleRouters as AppCustomRouteRecordRaw[]
         }
         const routerMap: AppRouteRecordRaw[] = configurationRoutes(
-          generateRoute(
-            withoutRetiredHistoryMenus(
-              stagePlanMenusForRoles(
-                commerceMenusForRoles(res, useUserStore().getRoles),
-                useUserStore().getRoles
-              )
-            )
-          )
+          generateRoute(withoutRetiredHistoryMenus(stagePlanMenusForRoles(commerceMenusForRoles(res, useUserStore().getRoles), useUserStore().getRoles)))
         )
         // 动态路由，404一定要放到最后面
         // preschooler：vue-router@4以后已支持静态404路由，此处可不再追加
```

## 050 utils/formatSourceDate.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增来源兼容**。只对声明的日期字段格式化，数量和空值原样返回；用于新增迁移事实的epoch日期。
- 证据及验证边界：对应来源API与日期工具。

### H01 @@ -0,0 +1,57 @@

裁决：保留新增来源兼容。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,57 @@
+import { formatDate } from '@/utils/formatTime'
+
+const dateFields = new Set(['warrantyStartDate', 'warrantyEndDate', 'executeTime'])
+const timestampFields = new Set([
+  'createTime',
+  'updateTime',
+  'orderCreateTime',
+  'customerRequiredTime',
+  'projectStartTime',
+  'projectRefreshTime',
+  'projectCloseTime',
+  'closedAt',
+  'sourceEffectiveFrom',
+  'sourceEffectiveTo',
+  'sourceProjectStartTime',
+  'sourceProjectRefreshTime',
+  'sourceProjectCloseTime',
+  'processTime',
+  'begin_date',
+  'end_date',
+  'updatetime',
+  'sourceShipmentTime',
+  'sourceReadAt',
+  'sourceUpdatedTime',
+  'submitTime',
+  'predictedBidTime',
+  'shipmentTime',
+  'syncTime',
+  'sourceSyncTime',
+  'sourceUpdatedAt',
+  'sourceOrderCreateTime',
+  'sourceCustomerRequiredTime',
+  'originalSourceSyncTime',
+  'sourceCreatedAt',
+  'effectiveFrom',
+  'effectiveTo',
+  'eventPlannedAt',
+  'eventActualFinishedAt',
+  'warrantyStartTime',
+  'warrantyEndTime',
+  'sourceWarrantyStartTime',
+  'sourceWarrantyEndTime',
+  'originalWarrantyStartTime',
+  'originalWarrantyEndTime'
+])
+
+/** Native date APIs may return epoch milliseconds; quantities and empty values keep their meaning. */
+export function formatSourceDate(value: unknown, field: string): unknown {
+  if (value == null || value === '' || (typeof value !== 'string' && typeof value !== 'number'))
+    return value
+  if (!dateFields.has(field) && !timestampFields.has(field)) return value
+  const formatted = formatDate(
+    value,
+    dateFields.has(field) ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss.SSS'
+  )
+  return !formatted || formatted === 'Invalid Date' ? value : formatted
+}
```

## 051 views/pms/acceptance/acceptance/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留纯新增附件功能**。旧验收页面新增原生附加附件/材料，不删除原验收表单和审批操作。
- 证据及验证边界：AdditionalNativeAttachments.runtime.spec.ts。

### H01 @@ -166,6 +166,11 @@

裁决：保留纯新增附件功能。当前行来源：1a93fad14aa0cadcf9300535aa7b9b6617b9c3aa, 9b7b1b54b238cad218b58f315243f954c3110392。

```diff
@@ -166,6 +166,11 @@
           </el-form-item>
         </el-col>
         <el-col :span="24">
+          <el-form-item label="附件">
+            <AdditionalNativeAttachments kind="legacyAcceptance" :entity-id="form.id" :readonly="form.status !== 0 || !checkPermi(['pms:acc-acceptance:update'])" @changed="attachmentOwnerChanged" />
+          </el-form-item>
+        </el-col>
+        <el-col :span="24">
           <el-form-item label="备注" prop="remark">
             <el-input v-model="form.remark" type="textarea" />
           </el-form-item>
```

### H02 @@ -180,6 +185,8 @@

裁决：保留纯新增附件功能。当前行来源：9b7b1b54b238cad218b58f315243f954c3110392。

```diff
@@ -180,6 +185,8 @@
 </template>
 
 <script setup lang="ts">
+import AdditionalNativeAttachments from '@/views/pms/engineering/attachment/AdditionalNativeAttachments.vue'
+import { checkPermi } from '@/utils/permission'
 import { onMounted, reactive, ref } from 'vue'
 import { useMessage } from '@/hooks/web/useMessage'
 import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
```

### H03 @@ -238,6 +245,7 @@

裁决：保留纯新增附件功能。当前行来源：9b7b1b54b238cad218b58f315243f954c3110392。

```diff
@@ -238,6 +245,7 @@
   )
   formVisible.value = true
 }
+const attachmentOwnerChanged = (owner: { version?: number; status?: number }) => { form.version = owner.version; form.status = owner.status }
 const save = async () => {
   await formRef.value.validate()
   saving.value = true
```

## 052 views/pms/acceptance/deliverable-checklist/ChecklistAttachments.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**重叠实现修复**。替代原清单附件控件，恢复新建及编辑先选择再保存；保留原生归集、冻结Owner拒绝修改和历史材料。
- 证据及验证边界：ChecklistAttachments.runtime.spec.ts。

### H01 @@ -0,0 +1,75 @@

裁决：重叠实现修复。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -0,0 +1,75 @@
+<template>
+  <div>
+    <el-upload v-if="!readonly" multiple :auto-upload="false" :file-list="pendingFiles"
+      accept=".doc,.xls,.ppt,.txt,.pdf" :limit="5" :disabled="draftBusy" :on-change="selectPending" :before-remove="beforeRemovePending" :on-remove="removePending" v-hasPermi="['pms:file:upload']">
+      <el-button type="primary" :disabled="draftBusy"><Icon icon="ep:upload-filled" />选取文件</el-button>
+    </el-upload>
+    <el-alert v-if="draftError" :title="draftError" type="error" :closable="false" />
+    <div v-for="material in materials" :key="material.id">
+      <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey" :artifact-id="material.fileArtifactId" :editable="!readonly" @detached="refresh" />
+      <el-button v-if="!readonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
+    </div>
+    <el-alert v-if="error" :title="error" type="error" :closable="false">
+      <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
+    </el-alert>
+  </div>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import { PmsFileReferenceList } from '@/components/PmsFileArtifact'
+import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
+import * as ChecklistApi from '@/api/pms/acceptance/deliverable-checklist'
+import { useNativeAttachmentDraft } from '@/views/pms/engineering/attachment/useNativeAttachmentDraft'
+const props = defineProps<{ entityId?: number; readonly: boolean }>()
+const emit = defineEmits<{ changed: [owner?: ChecklistApi.DeliverableChecklistVO] }>()
+const materials = ref<DeliveryMaterialVO[]>([])
+const error = ref('')
+const busy = ref(false)
+const pending = ref(false)
+let sequence = 0
+const refresh = async () => {
+  if (!props.entityId) return
+  const id = props.entityId, current = ++sequence
+  try {
+    const rows = await listMaterials('ACC', 'deliverableChecklist', id, 'ACC.CHECKLIST_ATTACHMENT')
+    if (id !== props.entityId || current !== sequence) return
+    materials.value = rows.filter(row => row.materialKind === 'FILE' && row.fileBusinessKey?.ownerContext === 'ACC'
+      && row.fileBusinessKey.objectType === 'DELIVERABLE_CHECKLIST' && String(row.fileBusinessKey.objectId) === String(id)
+      && row.fileBusinessKey.purposeCode === 'CHECKLIST_ATTACHMENT')
+    error.value = ''; emit('changed')
+  } catch (failure: any) { if (id === props.entityId && current === sequence) error.value = failure?.message || '材料读取失败' }
+}
+const collect = async () => {
+  if (props.readonly || !props.entityId || busy.value) return false
+  const id = props.entityId
+  busy.value = true; error.value = ''
+  pending.value = true
+  try {
+    const owner = await ChecklistApi.getDeliverableChecklist(id)
+    if (id !== props.entityId || props.readonly) return false
+    if (!owner || owner.id !== id || owner.status !== 0) throw new Error('核对清单已不可编辑，请重新读取')
+    await ChecklistApi.updateDeliverableChecklist(owner)
+    const saved = await ChecklistApi.getDeliverableChecklist(id)
+    if (id !== props.entityId || props.readonly) return false
+    pending.value = false; emit('changed', saved)
+    await refresh()
+    return true
+  } catch (failure: any) { if (id === props.entityId) error.value = failure?.message || '附件已上传，归集失败'; return false }
+  finally { busy.value = false }
+}
+const withdraw = async (id: number) => {
+  if (props.readonly || busy.value) return
+  busy.value = true; error.value = ''
+  try { await withdrawMaterial(id); await refresh() }
+  catch (failure: any) { error.value = failure?.message || '材料撤回失败' }
+  finally { busy.value = false }
+}
+const {pendingFiles,draftBusy,draftError,selectPending,beforeRemovePending,removePending,resetPending,savePending}=useNativeAttachmentDraft({
+ context:()=>({module:'ACC',type:'DELIVERABLE_CHECKLIST',purpose:'CHECKLIST_ATTACHMENT',accept:'.doc,.xls,.ppt,.txt,.pdf'}),readonly:()=>props.readonly,collect
+})
+defineExpose({savePending})
+watch(() => props.entityId, (_next,previous) => {
+ if(previous && previous!==props.entityId)resetPending()
+ pending.value = false; materials.value = []; void refresh()
+}, { immediate: true })
+</script>
```

## 053 views/pms/acceptance/deliverable-checklist/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：5；当前片段：6。
- 裁决：**恢复操作**。恢复原“交付件附件”“取消”、保存后关闭；移除未证实的数据版本展示；保留内部并发版本和历史附件只读。
- 证据及验证边界：ChecklistAttachments.runtime.spec.ts; 类型检查。

### H01 @@ -105,7 +105,7 @@

裁决：恢复操作。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -105,7 +105,7 @@
   </ContentWrap>
 
   <Dialog v-model="formVisible" :title="form.id ? '编辑交付件' : '新增交付件'" width="780px">
-    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
+    <el-form ref="formRef" :model="form" :rules="rules" :disabled="readOnly || saving" label-width="100px">
       <el-row :gutter="16">
         <el-col :span="12">
           <el-form-item label="项目" prop="projectId">
```

### H02 @@ -141,8 +141,9 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -141,8 +141,9 @@
           </el-form-item>
         </el-col>
         <el-col :span="24">
-          <el-form-item label="交付件附件" prop="deliverableUrl">
-            <UploadFile v-model="form.deliverableUrl!" />
+          <el-form-item label="交付件附件" prop="attachmentUrl">
+            <ChecklistAttachments :key="attachmentEpoch" ref="attachmentsRef" :entity-id="form.id" :readonly="readOnly" @changed="attachmentsChanged" />
+            <a v-for="url in legacyAttachments" :key="url" :href="url" target="_blank" rel="noopener">查看历史附件</a>
           </el-form-item>
         </el-col>
         <el-col :span="24">
```

### H03 @@ -152,18 +153,20 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -152,18 +153,20 @@
         </el-col>
       </el-row>
     </el-form>
-    <DeliveryPanel v-if="form.id" owner-module="ACC" entity-type="deliverableChecklist" :entity-id="form.id" :readonly="true" />
+    <DeliveryPanel v-if="form.id" :key="`${form.id}-${deliveryEpoch}`" owner-module="ACC" entity-type="deliverableChecklist" :entity-id="form.id" :readonly="true" />
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
-      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
+      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
+      <el-button type="primary" :loading="saving" :disabled="readOnly" @click="save">保存</el-button>
     </template>
   </Dialog>
 </template>
 
 <script setup lang="ts">
 import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
+import ChecklistAttachments from './ChecklistAttachments.vue'
+import { checkPermi } from '@/utils/permission'
 import ProjectDeliverablesPanel from '@/views/pms/project/project-master-detail/components/ProjectDeliverablesPanel.vue'
-import { onMounted, reactive, ref } from 'vue'
+import { computed, nextTick, onMounted, reactive, ref } from 'vue'
 import { useMessage } from '@/hooks/web/useMessage'
 import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
 import * as DeliverableChecklistApi from '@/api/pms/acceptance/deliverable-checklist'
```

### H04 @@ -186,7 +189,16 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -186,7 +189,16 @@
 })
 const formVisible = ref(false)
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof ChecklistAttachments>>()
 const form = reactive<DeliverableChecklistVO>({ projectId: undefined!, name: '' })
+const deliveryEpoch = ref(0)
+const readOnly = computed(() => !!form.id && (form.status !== 0 || !checkPermi(['pms:acc-deliverable-checklist:update'])))
+const legacyAttachments = computed(() => (form.deliverableUrl || form.attachmentUrl || '').split(',').filter(Boolean))
+const attachmentsChanged = (owner?: DeliverableChecklistVO) => {
+  if (owner && owner.id === form.id) { form.version = owner.version; form.status = owner.status }
+  deliveryEpoch.value += 1
+}
 const rules = {
   projectId: [{ required: true, message: '请选择项目' }],
   name: [{ required: true, message: '请输入交付件名称' }],
```

### H05 @@ -204,6 +216,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -204,6 +216,7 @@
   }
 }
 const openForm = (row?: DeliverableChecklistVO) => {
+  attachmentEpoch.value++
   Object.assign(
     form,
     {
```

### H06 @@ -223,12 +236,17 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -223,12 +236,17 @@
   formVisible.value = true
 }
 const save = async () => {
+  if (readOnly.value || saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
-    form.id
-      ? await DeliverableChecklistApi.updateDeliverableChecklist(form)
-      : await DeliverableChecklistApi.createDeliverableChecklist(form)
+    if (form.id) await DeliverableChecklistApi.updateDeliverableChecklist(form)
+    else form.id = await DeliverableChecklistApi.createDeliverableChecklist(form)
+    const current = await DeliverableChecklistApi.getDeliverableChecklist(form.id!)
+    Object.assign(form, current)
+    await nextTick()
+    if (!attachmentsRef.value || !(await attachmentsRef.value.savePending(form.id!))) return
+    deliveryEpoch.value += 1
     message.success('保存成功')
     formVisible.value = false
     await load()
```

## 054 views/pms/asset/device-log-archive/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：7；当前片段：7。
- 裁决：**保留测试性能修复**。移除技术说明文案的修复已在前轮落地；保留分页、仅可见行异步解析、加载反馈和旧请求隔离，不等待解析阻塞整表。
- 证据及验证边界：DeviceLogArchive.runtime.spec.ts; archive-page-correction.md。

### H01 @@ -72,7 +72,7 @@

裁决：保留测试性能修复。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -72,7 +72,7 @@
     <el-table
       v-else
       v-loading="loading"
-      :data="filteredRows"
+      :data="pagedRows"
       row-key="deviceId"
       :empty-text="appliedFilters.serialNumber || appliedFilters.deviceName || appliedFilters.sourceType ? '暂无符合查询条件的设备日志' : '当前项目暂无已归档的设备日志'"
     >
```

### H02 @@ -86,7 +86,7 @@

裁决：保留测试性能修复。当前行来源：f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -86,7 +86,7 @@
               parseStates[String(row.latest.id)].message || parseStates[String(row.latest.id)].state
             }}</div>
           </template>
-          <span v-else>—</span>
+          <span v-else>{{ row.latest?.available ? '解析状态加载中…' : '—' }}</span>
         </template></el-table-column
       >
       <el-table-column label="最新版本" width="110"
```

### H03 @@ -126,6 +126,13 @@

裁决：保留测试性能修复。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -126,6 +126,13 @@
         </template></el-table-column
       >
     </el-table>
+    <Pagination
+      v-if="projectId"
+      v-model:page="pageNo"
+      v-model:limit="pageSize"
+      :total="filteredRows.length"
+      :page-sizes="[20, 50, 100, rows.length || 20]"
+    />
   </ContentWrap>
   <el-dialog v-model="historyVisible" :title="`${selectedSerial} · 文件历史`" width="900px">
     <el-table v-loading="historyLoading" :data="historyRows" row-key="id">
```

### H04 @@ -211,11 +218,16 @@

裁决：保留测试性能修复。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -211,11 +218,16 @@
     && (!query.deviceName || (row.deviceName || '').toLowerCase().includes(query.deviceName))
     && (!query.sourceType || row.latest?.sourceType === query.sourceType))
 })
-const search = () => { appliedFilters.value = { serialNumber: filters.serialNumber.trim().toLowerCase(),
+const search = () => { pageNo.value = 1; appliedFilters.value = { serialNumber: filters.serialNumber.trim().toLowerCase(),
   deviceName: filters.deviceName.trim().toLowerCase(), sourceType: filters.sourceType || '' } }
 const resetSearch = () => {
   filters.serialNumber = ''; filters.deviceName = ''; filters.sourceType = ''; search()
 }
+const pageNo = ref(1)
+const pageSize = ref(20)
+const pagedRows = computed(() =>
+  filteredRows.value.slice((pageNo.value - 1) * pageSize.value, pageNo.value * pageSize.value)
+)
 const loading = ref(false)
 const loadFailed = ref(false)
 const uploading = ref(false)
```

### H05 @@ -251,15 +263,16 @@

裁决：保留测试性能修复。当前行来源：f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -251,15 +263,16 @@
 const parseStates = ref<Record<string, Api.ParseState>>({})
 let parseTimer: ReturnType<typeof setTimeout> | undefined
 let parseAttempts = 0
+let parseSequence = 0
 const refreshParses = async (currentRows: Api.DeviceLog[], sequence: number) => {
   const files = currentRows.flatMap((row) => (row.latest?.available ? [row.latest] : []))
   await Promise.all(
     files.map(async (file) => {
       try {
         const state = await Api.parseState(file.id)
-        if (sequence === loadSequence) parseStates.value[String(file.id)] = state
+        if (sequence === parseSequence) parseStates.value[String(file.id)] = state
       } catch {
-        if (sequence === loadSequence)
+        if (sequence === parseSequence)
           parseStates.value[String(file.id)] = {
             state: 'QUERY_FAILED',
             message: '解析状态加载失败，请刷新重试'
```

### H06 @@ -267,27 +280,36 @@

裁决：保留测试性能修复。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa, f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -267,27 +280,36 @@
       }
     })
   )
-  if (sequence !== loadSequence) return
-  const pending = Object.values(parseStates.value).some((v) =>
-    ['SUBMITTING', 'QUEUED', 'RUNNING'].includes(v.state)
+  if (sequence !== parseSequence) return
+  const pending = files.some((file) =>
+    ['SUBMITTING', 'QUEUED', 'RUNNING'].includes(parseStates.value[String(file.id)]?.state)
   )
   if (pending && ++parseAttempts < 60)
     parseTimer = setTimeout(() => refreshParses(currentRows, sequence), 2000)
 }
 onBeforeUnmount(() => {
   ++loadSequence
+  ++parseSequence
   ++parsedSequence
   if (parseTimer) clearTimeout(parseTimer)
 })
 let loadSequence = 0
 let historySequence = 0
+watch(pagedRows, (visibleRows) => {
+  const sequence = ++parseSequence
+  if (parseTimer) clearTimeout(parseTimer)
+  parseAttempts = 0
+  void refreshParses(visibleRows, sequence)
+})
 const load = async () => {
   const current = projectId.value
   const sequence = ++loadSequence
+  ++parseSequence
   if (parseTimer) clearTimeout(parseTimer)
   parseAttempts = 0
   parseStates.value = {}
   rows.value = []
+  pageNo.value = 1
   loadFailed.value = false
   if (!current) {
     loading.value = false
```

### H07 @@ -298,7 +320,6 @@

裁决：保留测试性能修复。当前行来源：SOURCE_BASELINE_DELETION_ONLY。

```diff
@@ -298,7 +320,6 @@
     const data = await Api.latest(current)
     if (sequence === loadSequence) {
       rows.value = data
-      await refreshParses(data, sequence)
     }
   } catch {
     if (sequence === loadSequence) loadFailed.value = true
```

## 055 views/pms/asset/device/DeviceWorkbenchDialogs.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：7；当前片段：7。
- 裁决：**保留纯新增功能**。新增状态变更、发货事实、主附SN、软件维护入口；原详情/出厂/官网/日志/项目历史仍保留。新增写操作仍经既有权限与Owner。
- 证据及验证边界：设备详情/状态及软件版本对应服务端。

### H01 @@ -7,6 +7,12 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -7,6 +7,12 @@
   >
     <template #actions>
       <div v-if="detail" class="detail-actions">
+        <el-button
+          v-hasPermi="['pms:device:status-change']"
+          :disabled="!detail.archive"
+          @click="statusChangeDialog?.open(detail.archive!)"
+          >状态变更</el-button
+        >
         <el-button @click="assignmentHistoryDrawer?.open(detail.summary.deviceId)"
           >项目历史</el-button
         >
```

### H02 @@ -34,9 +40,15 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -34,9 +40,15 @@
         <el-tab-pane label="位置与变更轨迹" name="location" lazy>
           <DeviceLocationPanel :device-id="detail.summary.deviceId" />
         </el-tab-pane>
+        <el-tab-pane label="发货事实" name="shipments"
+          ><DeviceShipmentPanel :device-id="detail.summary.deviceId"
+        /></el-tab-pane>
         <el-tab-pane label="出厂信息" name="factory"
-          ><DeviceFactoryPanel :slice="detail.factory"
+          ><DeviceFactoryPanel :slice="detail.factory" :device-id="detail.summary.deviceId"
         /></el-tab-pane>
+        <el-tab-pane label="主附SN关系" name="contractRelationships" lazy>
+          <DeviceContractRelationshipPanel :device-id="detail.summary.deviceId" />
+        </el-tab-pane>
         <el-tab-pane label="官网信息" name="official"
           ><DeviceOfficialInfoPanel :slice="detail.official"
         /></el-tab-pane>
```

### H03 @@ -44,6 +56,8 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -44,6 +56,8 @@
           ><DeviceNetworkVersionPanel
             :slice="detail.networkVersion"
             :device-id="detail.summary.deviceId"
+            :project-id="detail.summary.projectId"
+            @updated="refreshSoftwareVersion"
         /></el-tab-pane>
         <el-tab-pane label="技术公告" name="notice"
           ><DeviceTechnicalNoticePanel :slice="detail.technicalNotice"
```

### H04 @@ -65,13 +79,18 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -65,13 +79,18 @@
   <DeviceAssemblyTreeDrawer ref="assemblyTreeDrawer" />
   <DeviceAssignProjectDialog ref="assignProjectDialog" @success="refreshDetail" />
   <DeviceAssignCustomerDialog ref="assignCustomerDialog" @success="refreshDetail" />
+  <DeviceArchiveStatusChangeDialog ref="statusChangeDialog" @success="refreshAll" />
 </template>
 <script setup lang="ts">
 import { ref } from 'vue'
+import { useMessage } from '@/hooks/web/useMessage'
 import * as DeviceApi from '@/api/pms/asset/device'
 import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
 import type { DeviceDetailVO, DeviceListVO, DeviceSummaryVO } from '@/api/pms/asset/device'
 import type { DeviceArchiveVO } from '@/api/pms/asset/device/archive'
+import DeviceShipmentPanel from './components/DeviceShipmentPanel.vue'
+import DeviceContractRelationshipPanel from './components/DeviceContractRelationshipPanel.vue'
+import DeviceArchiveStatusChangeDialog from './components/DeviceArchiveStatusChangeDialog.vue'
 import DeviceLocationPanel from './components/DeviceLocationPanel.vue'
 import DeviceSummaryPanel from './components/DeviceSummaryPanel.vue'
 import DeviceFactoryPanel from './components/DeviceFactoryPanel.vue'
```

### H05 @@ -87,6 +106,7 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -87,6 +106,7 @@
 import DeviceAssignCustomerDialog from './components/DeviceAssignCustomerDialog.vue'
 import DeviceArchiveFormDialog from './components/DeviceArchiveFormDialog.vue'
 
+const message = useMessage()
 const props = defineProps<{ projectId?: number | string }>()
 const emit = defineEmits<{ success: [] }>()
 const detail = ref<DeviceDetailVO & { archive?: DeviceArchiveVO }>()
```

### H06 @@ -101,6 +121,7 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -101,6 +121,7 @@
 const assignProjectDialog = ref<InstanceType<typeof DeviceAssignProjectDialog>>()
 const assignCustomerDialog = ref<InstanceType<typeof DeviceAssignCustomerDialog>>()
 const formDialog = ref<InstanceType<typeof DeviceArchiveFormDialog>>()
+const statusChangeDialog = ref<InstanceType<typeof DeviceArchiveStatusChangeDialog>>()
 
 const openDetail = async (row: DeviceListVO | DeviceSummaryVO) => {
   const request = ++detailRequest
```

### H07 @@ -116,11 +137,32 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -116,11 +137,32 @@
   if (!detail.value) return
   await openDetail(detail.value.summary)
 }
+const refreshSoftwareVersion = async () => {
+  if (!detail.value) return
+  const deviceId = detail.value.summary.deviceId
+  const request = ++detailRequest
+  const data = await DeviceApi.getDevice(deviceId)
+  if (request !== detailRequest || detail.value?.summary.deviceId !== deviceId) return
+  detail.value = { ...data, archive: detail.value.archive }
+  emit('success')
+}
 const refreshAll = async () => {
   clearDetail()
   emit('success')
 }
-defineExpose({ openDetail, clearDetail })
+const openStatusChange = async (row: DeviceListVO) => {
+  const archive = await DeviceArchiveApi.getDeviceArchiveRecord(row.deviceId).catch(() => undefined)
+  if (!archive) return message.error('设备档案不存在')
+  statusChangeDialog.value?.open(archive)
+}
+const remove = async (row: DeviceListVO) => {
+  await message.delConfirm()
+  await DeviceArchiveApi.deleteDeviceArchive(row.deviceId)
+  message.success('删除成功')
+  if (detail.value?.summary.deviceId === row.deviceId) clearDetail()
+  emit('success')
+}
+defineExpose({ openDetail, clearDetail, openStatusChange, remove })
 </script>
 <style scoped>
 .detail-actions {
```

## 056 views/pms/asset/device/components/DeviceContractRelationshipPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,47 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,47 @@
+<template>
+  <section aria-label="主SN与附加SN关系" v-loading="loading">
+    <el-alert v-if="error" title="设备关系加载失败，请重试。" type="error" :closable="false">
+      <el-button @click="refresh++">重新加载</el-button>
+    </el-alert>
+    <template v-else>
+      <el-table :data="rows" row-key="id" empty-text="暂无主SN与附加SN关系">
+        <el-table-column prop="sourceDeviceSn" label="主SN" min-width="190" />
+        <el-table-column prop="sourceItem" label="主产品编码" min-width="150" />
+        <el-table-column prop="targetDeviceSn" label="附加SN" min-width="190" />
+        <el-table-column prop="targetItem" label="附加产品编码" min-width="150" />
+        <el-table-column prop="contractNo" label="合同号" min-width="190" />
+        <el-table-column v-for="field in dates" :key="field.key" :label="field.label" min-width="170">
+          <template #default="{ row }">{{ formatSourceDate(row[field.key], field.key) ?? '--' }}</template>
+        </el-table-column>
+      </el-table>
+      <el-pagination v-model:current-page="pageNo" :page-size="20" :total="total" layout="prev,pager,next,total" />
+    </template>
+  </section>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import { getDeviceContractRelationships, type DeviceContractRelationship } from '@/api/pms/asset/device'
+import { formatSourceDate } from '@/utils/formatSourceDate'
+const props = defineProps<{ deviceId: number }>()
+const dates = [
+  { key: 'effectiveFrom', label: '关系开始时间' },
+  { key: 'effectiveTo', label: '关系结束时间' },
+  { key: 'createTime', label: '创建时间' },
+  { key: 'updateTime', label: '更新时间' }
+] as const
+const rows = ref<DeviceContractRelationship[]>([]), total = ref(0), pageNo = ref(1),
+  refresh = ref(0), loading = ref(false), error = ref(false)
+watch([() => props.deviceId, pageNo, refresh], async ([id, page], old, onCleanup) => {
+  let current = true
+  onCleanup(() => { current = false })
+  rows.value = []
+  if (id !== old?.[0]) total.value = 0
+  loading.value = true
+  error.value = false
+  try {
+    const result = await getDeviceContractRelationships(id, { pageNo: page, pageSize: 20 })
+    if (current) { rows.value = result.list; total.value = result.total }
+  } catch { if (current) error.value = true }
+  finally { if (current) loading.value = false }
+}, { immediate: true })
+</script>
```

## 057 views/pms/asset/device/components/DeviceFactoryPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。保留原出厂信息，附加只读来源版本历史。
- 证据及验证边界：DeviceFactorySourceHistoryPanel。

### H01 @@ -1,10 +1,12 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -1,10 +1,12 @@
 <template>
   <DeviceSourceStatus :slice="slice" />
   <DeviceSourceData :data="slice.data" title="出厂信息" empty-text="暂无可展示的出厂信息" />
+  <DeviceFactorySourceHistoryPanel v-if="deviceId" :device-id="deviceId" />
 </template>
 <script setup lang="ts">
+import DeviceFactorySourceHistoryPanel from './DeviceFactorySourceHistoryPanel.vue'
 import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
 import DeviceSourceStatus from './DeviceSourceStatus.vue'
 import DeviceSourceData from './DeviceSourceData.vue'
-defineProps<{ slice: DeviceSourceSliceVO }>()
+defineProps<{ slice: DeviceSourceSliceVO; deviceId?: number }>()
 </script>
```

## 058 views/pms/asset/device/components/DeviceFactorySourceHistoryPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,133 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,133 @@
+<template>
+  <section class="source-history" v-loading="loading">
+    <h3>来源出厂软件版本</h3>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
+      <el-button link type="primary" @click="load">重新加载</el-button>
+    </el-alert>
+    <el-table v-else-if="rows.length" :data="rows" row-key="id">
+      <el-table-column prop="values.deviceSn" label="来源序列号" min-width="110" />
+      <el-table-column prop="values.conpVersion" label="CONP" min-width="110" />
+      <el-table-column prop="values.bootVersion" label="BOOT" min-width="110" />
+      <el-table-column prop="values.cpldVersion" label="CPLD" min-width="110" />
+      <el-table-column prop="values.pcbVersion" label="PCB" min-width="110" />
+      <el-table-column label="操作" width="90" fixed="right">
+        <template #default="{ row }">
+          <el-button link type="primary" @click="openDetail(row)">查看原字段</el-button>
+        </template>
+      </el-table-column>
+    </el-table>
+    <el-empty v-else-if="!loading" :image-size="70" description="暂无来源业务记录" />
+    <el-pagination
+      v-if="total > 0"
+      v-model:current-page="pageNo"
+      v-model:page-size="pageSize"
+      :total="total"
+      :page-sizes="[20, 50, 100]"
+      layout="total, sizes, prev, pager, next"
+      @size-change="changePageSize"
+      @current-change="load"
+    />
+    <el-drawer
+      v-model="detailVisible"
+      title="来源业务记录原字段"
+      size="min(860px, 95vw)"
+      destroy-on-close
+    >
+      <DeviceReadOnlyFields v-if="selected" title="来源出厂软件版本" :fields="detailFields" />
+    </el-drawer>
+  </section>
+</template>
+<script setup lang="ts">
+import { computed, ref, watch, onBeforeUnmount } from 'vue'
+import {
+  getDeviceSourceFactoryVersions,
+  DeviceSourceFactoryVersion
+} from '@/api/pms/asset/device'
+import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
+const props = defineProps<{ deviceId: number }>()
+const rows = ref<{ id: number | string; projectId: number | string; values: DeviceSourceFactoryVersion }[]>([])
+const selected = ref<{ id: number | string; projectId: number | string; values: DeviceSourceFactoryVersion }>()
+const detailVisible = ref(false)
+const openDetail = (row: (typeof rows.value)[number]) => {
+  selected.value = row
+  detailVisible.value = true
+}
+const loading = ref(false)
+const error = ref('')
+const pageNo = ref(1)
+const pageSize = ref(20)
+const total = ref(0)
+let requestVersion = 0
+const fieldDefinitions = [
+  { key: 'deviceSn', label: '来源设备序列号' },
+  { key: 'conpVersion', label: 'CONP' },
+  { key: 'bootVersion', label: 'BOOT' },
+  { key: 'cpldVersion', label: 'CPLD' },
+  { key: 'pcbVersion', label: 'PCB' }
+] as const
+const detailFields = computed(() => {
+  if (!selected.value) return []
+  const values = selected.value.values
+  return fieldDefinitions.map(({ key, label }) => ({
+    label,
+    value: values[key]
+  }))
+})
+const load = async () => {
+  const version = ++requestVersion
+  loading.value = true
+  error.value = ''
+  try {
+    const page = await getDeviceSourceFactoryVersions(props.deviceId, {
+      pageNo: pageNo.value,
+      pageSize: pageSize.value
+    })
+    if (version !== requestVersion) return
+    rows.value = page.list || []
+    total.value = page.total || 0
+  } catch {
+    if (version === requestVersion) {
+      rows.value = []
+      total.value = 0
+      error.value = '来源业务记录加载失败'
+    }
+  } finally {
+    if (version === requestVersion) loading.value = false
+  }
+}
+const changePageSize = () => {
+  pageNo.value = 1
+  load()
+}
+watch(
+  () => props.deviceId,
+  () => {
+    pageNo.value = 1
+    rows.value = []
+    total.value = 0
+    selected.value = undefined
+    detailVisible.value = false
+    load()
+  },
+  { immediate: true }
+)
+onBeforeUnmount(() => {
+  requestVersion++
+})
+</script>
+<style scoped>
+.source-history {
+  padding-top: 16px;
+  border-top: 1px solid var(--el-border-color-extra-light);
+}
+
+.source-history h3 {
+  margin: 0 0 12px;
+  font-size: 15px;
+}
+
+.source-history :deep(.el-pagination) {
+  margin-top: 12px;
+  justify-content: flex-end;
+}
+</style>
```

## 059 views/pms/asset/device/components/DeviceInstallationSourceHistoryPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,179 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,179 @@
+<template>
+  <section
+    class="source-history"
+    v-loading="loading"
+    data-testid="installation-source-history"
+    :aria-busy="loading"
+  >
+    <h3>安装记录</h3>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
+      <el-button link type="primary" @click="load">重新加载</el-button>
+    </el-alert>
+    <el-table v-else-if="rows.length" :data="rows" row-key="id">
+      <el-table-column label="安装编码" min-width="150">
+        <template #default="{ row }">{{ row.historicalInstallation?.code ?? '—' }}</template>
+      </el-table-column>
+      <el-table-column
+        prop="installLocation"
+        label="安装地址"
+        min-width="240"
+        show-overflow-tooltip
+      />
+      <el-table-column label="安装记录" min-width="280">
+        <template #default="{ row }">
+          <div
+            v-if="row.historicalInstallation"
+            data-testid="historical-installation-summary"
+            :data-installation-id="row.historicalInstallation.id"
+          >
+            <div data-testid="historical-installation-status">{{
+              statusText(row.historicalInstallation.status)
+            }}</div>
+            <div data-testid="historical-installation-effective-time">
+              生效时间：{{
+                formatSourceDate(row.historicalInstallation.effectiveFrom, 'effectiveFrom') ?? '—'
+              }}
+            </div>
+            <div
+              >结束时间：{{
+                formatSourceDate(row.historicalInstallation.effectiveTo, 'effectiveTo') ?? '—'
+              }}</div
+            >
+            <div data-testid="historical-installation-current">{{
+              row.historicalInstallation.currentEquipmentId != null ? '当前安装' : '历史安装'
+            }}</div>
+          </div>
+          <span v-else>安装记录未关联</span>
+        </template>
+      </el-table-column>
+      <el-table-column label="操作" width="90" fixed="right">
+        <template #default="{ row }"
+          ><el-button link type="primary" @click="openDetail(row)">查看详情</el-button></template
+        >
+      </el-table-column>
+    </el-table>
+    <el-empty v-else-if="!loading" :image-size="70" description="暂无安装记录" />
+    <el-pagination
+      v-if="total > 0"
+      v-model:current-page="pageNo"
+      v-model:page-size="pageSize"
+      :total="total"
+      :page-sizes="[20, 50, 100]"
+      layout="total, sizes, prev, pager, next"
+      @size-change="changePageSize"
+      @current-change="load"
+    />
+    <el-drawer
+      v-model="detailVisible"
+      title="安装记录详情"
+      size="min(860px, 95vw)"
+      destroy-on-close
+    >
+      <DeviceReadOnlyFields v-if="selected" title="安装记录" :fields="detailFields" />
+    </el-drawer>
+  </section>
+</template>
+<script setup lang="ts">
+import { computed, ref, watch, onBeforeUnmount } from 'vue'
+import {
+  getInstallationSourceRecords,
+  type InstallationSourceRecord
+} from '@/api/pms/engineering/installation-source-history'
+import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
+const props = defineProps<{ deviceId: number }>()
+const rows = ref<InstallationSourceRecord[]>([])
+const selected = ref<InstallationSourceRecord>()
+const detailVisible = ref(false)
+const openDetail = (row: InstallationSourceRecord) => {
+  selected.value = row
+  detailVisible.value = true
+}
+const installationStatuses: Record<number, string> = {
+  0: '待安装',
+  1: '进行中',
+  2: '已安装',
+  3: '异常'
+}
+const statusText = (status?: number) => installationStatuses[status ?? -1] ?? '未记录状态'
+const loading = ref(false)
+const error = ref('')
+const pageNo = ref(1)
+const pageSize = ref(20)
+const total = ref(0)
+let requestVersion = 0
+const detailFields = computed(() => {
+  const row = selected.value
+  if (!row) return []
+  const history = row.historicalInstallation
+  return [
+    { label: '安装编码', value: history?.code },
+    { label: '设备序列号', value: row.sourceDeviceSn },
+    { label: '安装地址', value: row.installLocation, multiline: true },
+    { label: '安装状态', value: statusText(history?.status) },
+    { label: '生效时间', value: formatSourceDate(history?.effectiveFrom, 'effectiveFrom') },
+    { label: '结束时间', value: formatSourceDate(history?.effectiveTo, 'effectiveTo') },
+    { label: '地址ID', value: row.addressId },
+    { label: '地址版本', value: row.addressVersion },
+    { label: '创建人', value: row.values.sourceCreatedBy },
+    { label: '创建时间', value: formatSourceDate(row.values.sourceCreatedAt, 'createTime') },
+    { label: '更新人', value: row.values.sourceUpdatedBy },
+    { label: '更新时间', value: formatSourceDate(row.values.sourceUpdatedAt, 'updateTime') }
+  ]
+})
+const load = async () => {
+  const version = ++requestVersion
+  loading.value = true
+  error.value = ''
+  try {
+    const page = await getInstallationSourceRecords({
+      equipmentId: props.deviceId,
+      pageNo: pageNo.value,
+      pageSize: pageSize.value
+    })
+    if (version !== requestVersion) return
+    rows.value = page.list || []
+    total.value = page.total || 0
+  } catch {
+    if (version === requestVersion) {
+      rows.value = []
+      total.value = 0
+      error.value = '安装记录加载失败'
+    }
+  } finally {
+    if (version === requestVersion) loading.value = false
+  }
+}
+const changePageSize = () => {
+  pageNo.value = 1
+  load()
+}
+watch(
+  () => props.deviceId,
+  () => {
+    pageNo.value = 1
+    rows.value = []
+    total.value = 0
+    selected.value = undefined
+    detailVisible.value = false
+    load()
+  },
+  { immediate: true }
+)
+onBeforeUnmount(() => {
+  requestVersion++
+})
+</script>
+<style scoped>
+.source-history {
+  padding-top: 16px;
+  border-top: 1px solid var(--el-border-color-extra-light);
+}
+.source-history h3 {
+  margin: 0 0 12px;
+  font-size: 16px;
+}
+.source-history :deep(.el-pagination) {
+  margin-top: 12px;
+}
+</style>
```

## 060 views/pms/asset/device/components/DeviceLocationPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留新增历史及日期兼容**。新增安装来源历史独立展示；原位置仍使用原事实，日期适配epoch返回，空设备名不显示undefined。
- 证据及验证边界：formatSourceDate契约；locationSnapshot既有测试。

### H01 @@ -4,11 +4,11 @@

裁决：保留新增历史及日期兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -4,11 +4,11 @@
       v-if="detail"
       title="当前位置"
       :fields="[
-        { label: '设备', value: `${detail.sn} ${detail.name}` },
+        { label: '设备', value: [detail.sn, detail.name].filter(Boolean).join(' ') },
         { label: '解析状态', value: detail.locationResolutionStatus || 'UNRESOLVED' },
         { label: '站点', value: detail.siteId },
         { label: '站点位置', value: detail.siteLocationId },
-        { label: '生效时间', value: detail.locationEffectiveFrom },
+        { label: '生效时间', value: formatSourceDate(detail.locationEffectiveFrom, 'effectiveFrom') },
         { label: '来源安装记录', value: detail.locationRecordId },
         { label: '发生时位置快照', value: locationSnapshotText(detail.locationSnapshot), multiline: true }
       ]"
```

### H02 @@ -23,14 +23,17 @@

裁决：保留新增历史及日期兼容。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -23,14 +23,17 @@
       :key="item.id"
       :title="`位置变更记录 ${index + 1}`"
       :fields="[
-        { label: '变更时间', value: item.createTime },
+        { label: '变更时间', value: formatSourceDate(item.createTime, 'createTime') },
         { label: '变更类型', value: item.changeType },
         { label: '变更说明', value: item.changeDescription, multiline: true }
       ]"
     />
   </div>
+  <DeviceInstallationSourceHistoryPanel :device-id="deviceId" />
 </template>
 <script setup lang="ts">
+import DeviceInstallationSourceHistoryPanel from './DeviceInstallationSourceHistoryPanel.vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
 import { locationSnapshotText } from '@/utils/locationSnapshot'
 import { ref, watch } from 'vue'
 import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
```

## 061 views/pms/asset/device/components/DeviceNetworkVersionPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留纯新增功能**。保留原解析版本展示，新增人工版本维护独立面板，不取代原日志解析事实。
- 证据及验证边界：DeviceSoftwareVersionMaintenancePanel及服务端。

### H01 @@ -1,4 +1,10 @@

裁决：保留纯新增功能。当前行来源：1b093b7bad0cffb8e88a9ba081e4c2481b93cdaf, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -1,4 +1,10 @@
 <template>
+  <DeviceSoftwareVersionMaintenancePanel
+    v-if="deviceId"
+    :device-id="deviceId"
+    :project-id="projectId"
+    @updated="$emit('updated')"
+  />
   <DeviceSourceStatus :slice="slice" />
   <p class="text-sm text-[var(--el-text-color-secondary)]"
     >在网版本来自设备最新日志的有效解析结果。</p
```

### H02 @@ -22,11 +28,13 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -22,11 +28,13 @@
   />
 </template>
 <script setup lang="ts">
+import DeviceSoftwareVersionMaintenancePanel from './DeviceSoftwareVersionMaintenancePanel.vue'
+import DeviceParsedLogRecords from './DeviceParsedLogRecords.vue'
 import { computed } from 'vue'
 import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
 import DeviceSourceStatus from './DeviceSourceStatus.vue'
 import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
-import DeviceParsedLogRecords from './DeviceParsedLogRecords.vue'
-const props = defineProps<{ slice: DeviceSourceSliceVO; deviceId?: number | string }>()
+const props = defineProps<{ slice: DeviceSourceSliceVO; deviceId?: number | string; projectId?: number | string }>()
+defineEmits<{ updated: [] }>()
 const version = computed(() => (props.slice.data || {}) as Record<string, unknown>)
 </script>
```

## 062 views/pms/asset/device/components/DeviceParsedLogRecords.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留响应隔离**。卸载后递增序号，旧解析响应不写入已离开页面。
- 证据及验证边界：既有解析记录测试。

### H01 @@ -15,7 +15,7 @@

裁决：保留响应隔离。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -15,7 +15,7 @@
   </el-table>
 </template>
 <script setup lang="ts">
-import { ref, watch } from 'vue'
+import { onBeforeUnmount, ref, watch } from 'vue'
 import request from '@/config/axios'
 const props = defineProps<{ deviceId: number | string }>()
 interface RecordRow {
```

### H02 @@ -53,4 +53,5 @@

裁决：保留响应隔离。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -53,4 +53,5 @@
   }
 }
 watch(() => props.deviceId, load, { immediate: true })
+onBeforeUnmount(() => { ++sequence })
 </script>
```

## 063 views/pms/asset/device/components/DeviceShipmentPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,67 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,67 @@
+<template>
+  <section aria-label="设备发货事实" v-loading="loading">
+    <el-alert v-if="error" title="发货信息加载失败，请重试。" type="error" :closable="false">
+      <el-button @click="refresh++">重新加载</el-button>
+    </el-alert>
+    <template v-else>
+      <el-table :data="rows" row-key="id" empty-text="暂无发货记录">
+        <el-table-column
+          v-for="field in shipmentFields"
+          :key="field.key"
+          :label="field.label"
+          min-width="150"
+          show-overflow-tooltip
+        >
+          <template #default="{ row }">{{
+            formatSourceDate(row[field.key], field.key) ?? '--'
+          }}</template>
+        </el-table-column>
+      </el-table>
+      <el-pagination
+        v-model:current-page="pageNo"
+        :page-size="20"
+        :total="total"
+        layout="prev,pager,next,total"
+      />
+    </template>
+  </section>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
+import { getDeviceShipments, type DeviceShipmentVO } from '@/api/pms/asset/device'
+import { deviceShipmentFields } from './device-native-fields'
+const shipmentFields = deviceShipmentFields
+const props = defineProps<{ deviceId: number }>()
+const rows = ref<DeviceShipmentVO[]>([]),
+  total = ref(0),
+  pageNo = ref(1),
+  refresh = ref(0),
+  loading = ref(false),
+  error = ref(false)
+watch(
+  [() => props.deviceId, pageNo, refresh],
+  async ([id, page], _old, onCleanup) => {
+    let current = true
+    onCleanup(() => {
+      current = false
+    })
+    rows.value = []
+    if (id !== _old?.[0]) total.value = 0
+    loading.value = true
+    error.value = false
+    try {
+      const result = await getDeviceShipments(id, { pageNo: page, pageSize: 20 })
+      if (current) {
+        rows.value = result.list
+        total.value = result.total
+      }
+    } catch {
+      if (current) error.value = true
+    } finally {
+      if (current) loading.value = false
+    }
+  },
+  { immediate: true }
+)
+</script>
```

## 064 views/pms/asset/device/components/DeviceSoftwareSourceHistoryPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,198 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,198 @@
+<template>
+  <section class="source-history" v-loading="loading">
+    <h3>软件版本历史</h3>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
+      <el-button link type="primary" @click="load">重新加载</el-button>
+    </el-alert>
+    <el-table v-else-if="rows.length" :data="rows" row-key="record.id">
+      <el-table-column label="登记时间" min-width="175"
+        ><template #default="{ row }">{{ date(row.record.recordedAt) }}</template></el-table-column
+      >
+      <el-table-column label="登记来源" min-width="110"
+        ><template #default="{ row }">{{
+          source(row.record.dataSource)
+        }}</template></el-table-column
+      >
+      <el-table-column
+        v-for="field in coreFields"
+        :key="field.key"
+        :prop="`record.${field.key}`"
+        :label="field.label"
+        min-width="130"
+        show-overflow-tooltip
+      />
+      <el-table-column label="操作" width="100" fixed="right"
+        ><template #default="{ row }"
+          ><el-button link type="primary" @click="selected = row">查看与比较</el-button></template
+        ></el-table-column
+      >
+    </el-table>
+    <el-empty v-else-if="!loading" :image-size="70" description="暂无软件登记记录" />
+    <el-pagination
+      v-if="total > 0"
+      v-model:current-page="pageNo"
+      v-model:page-size="pageSize"
+      :total="total"
+      :page-sizes="[20, 50, 100]"
+      layout="total, sizes, prev, pager, next"
+      @size-change="reload"
+      @current-change="load"
+    />
+    <el-drawer
+      :model-value="!!selected"
+      title="软件版本登记与比较"
+      size="min(900px, 95vw)"
+      destroy-on-close
+      @close="selected = undefined"
+    >
+      <template v-if="selected">
+        <DeviceReadOnlyFields title="本次完整版本" :fields="versionFields" />
+        <el-table v-if="selected.previous" :data="comparison"
+          ><el-table-column prop="label" label="版本项" /><el-table-column
+            prop="before"
+            label="登记前" /><el-table-column prop="after" label="登记后" /><el-table-column
+            prop="changed"
+            label="是否变化"
+        /></el-table>
+        <p v-else>此记录没有可见前记录，未推断历史变更。</p>
+        <DeviceReadOnlyFields title="登记资料" :fields="registrationFields" />
+        <DeviceReadOnlyFields
+          v-if="selected.record.dataSource === 'DPPMS'"
+          title="原变更前备份及原变化标志"
+          :fields="legacyFields"
+        />
+      </template>
+    </el-drawer>
+  </section>
+</template>
+<script setup lang="ts">
+import { computed, onBeforeUnmount, ref, watch } from 'vue'
+import { getSoftwareVersionHistory, type SoftwareVersionHistory } from '@/api/pms/asset/device'
+import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
+const props = defineProps<{ deviceId: number | string }>()
+const rows = ref<SoftwareVersionHistory[]>([]),
+  selected = ref<SoftwareVersionHistory>()
+const pageNo = ref(1),
+  pageSize = ref(20),
+  total = ref(0),
+  loading = ref(false),
+  error = ref('')
+let generation = 0
+const coreFields = [
+  { key: 'conpVersion', label: 'CONP' },
+  { key: 'bootVersion', label: 'BOOT' },
+  { key: 'cpldVersion', label: 'CPLD' },
+  { key: 'pcbVersion', label: 'PCB' }
+] as const
+const text = (value: unknown) =>
+  value == null ? '未记录' : value === '' ? '空字符串' : String(value)
+const date = (value: unknown, field = 'effectiveFrom') => formatSourceDate(value, field) ?? '未记录'
+const sourceNames: Record<string, string> = {
+  PLATFORM_MANUAL: '手工登记',
+  DEVICE_LOG: '日志回填',
+  DPPMS: '历史迁入',
+  CURRENT_BASELINE: '初始当前值'
+}
+const source = (value: string) => sourceNames[value] ?? value
+const versionFields = computed(() => {
+  const row = selected.value?.record
+  return [
+    ...coreFields.map((field) => ({ label: field.label, value: text(row?.[field.key]) })),
+    { label: 'CONP类型', value: text(row?.conpType) },
+    { label: 'CONP系列', value: text(row?.conpSeries) },
+    { label: 'CONP标记', value: text(row?.conpMark) },
+    { label: '定制标记', value: row?.customized },
+    { label: '发布日期', value: date(row?.releaseDate, 'executeTime') },
+    { label: '版本说明', value: text(row?.versionDesc) }
+  ]
+})
+const comparison = computed(() =>
+  coreFields.map((field) => ({
+    label: field.label,
+    before: text(selected.value?.previous?.[field.key]),
+    after: text(selected.value?.record[field.key]),
+    changed:
+      selected.value?.previous?.[field.key] === selected.value?.record[field.key] ? '否' : '是'
+  }))
+)
+const registrationFields = computed(() => {
+  const row = selected.value?.record
+  if (!row) return []
+  return [
+    { label: '记录ID', value: row.id },
+    { label: '设备SN', value: row.deviceSn },
+    { label: '项目ID', value: row.projectId },
+    { label: '前记录ID', value: row.previousRecordId },
+    { label: '来源', value: source(row.dataSource) },
+    { label: '登记时间', value: date(row.recordedAt) },
+    { label: '变更批次', value: row.changeBatchId },
+    { label: '解析记录', value: row.parsedFactId },
+    { label: '合同号', value: row.contractNo },
+    { label: '产品编码', value: row.productCode },
+    { label: '原执行日期', value: date(row.executeTime, 'executeTime') },
+    { label: '原有效标记', value: row.dataState },
+    { label: '原完整JSON', value: row.customInfo, multiline: true },
+    { label: '创建人', value: row.creator },
+    { label: '创建时间', value: date(row.createTime) },
+    { label: '更新人', value: row.updater },
+    { label: '更新时间', value: date(row.updateTime) }
+  ]
+})
+const legacyFields = computed(() =>
+  ['conp', 'boot', 'cpld', 'pcb'].flatMap((key) => [
+    {
+      label: `${key.toUpperCase()}原备份`,
+      value: text(selected.value?.legacyAttributes[`${key}_bak`])
+    },
+    {
+      label: `${key.toUpperCase()}原变化标志`,
+      value: selected.value?.legacyAttributes[`${key}_change`]
+    }
+  ])
+)
+const load = async () => {
+  const request = ++generation
+  loading.value = true
+  error.value = ''
+  try {
+    const page = await getSoftwareVersionHistory(props.deviceId, {
+      pageNo: pageNo.value,
+      pageSize: pageSize.value
+    })
+    if (request !== generation) return
+    rows.value = page.list || []
+    total.value = page.total || 0
+  } catch {
+    if (request === generation) {
+      rows.value = []
+      total.value = 0
+      error.value = '软件版本历史加载失败'
+    }
+  } finally {
+    if (request === generation) loading.value = false
+  }
+}
+const reload = () => {
+  pageNo.value = 1
+  void load()
+}
+watch(
+  () => props.deviceId,
+  () => {
+    selected.value = undefined
+    rows.value = []
+    reload()
+  },
+  { immediate: true }
+)
+onBeforeUnmount(() => {
+  generation++
+})
+defineExpose({ reload })
+</script>
+<style scoped>
+.source-history {
+  margin-top: 20px;
+}
+</style>
```

## 065 views/pms/asset/device/components/DeviceSoftwareVersionMaintenancePanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,187 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,187 @@
+<template>
+  <section v-loading="loading">
+    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
+      <el-button link @click="load">重新加载</el-button>
+    </el-alert>
+    <template v-else-if="current">
+      <div class="software-actions">
+        <span>登记时间：{{ date(current.recordedAt) }}</span>
+        <el-button @click="load">刷新</el-button>
+        <el-button v-if="projectId" v-hasPermi="['pms:device-log-archive:query']" @click="logArchiveVisible = true">项目日志档案</el-button>
+        <el-button v-if="current.canMaintain && projectId" :disabled="loading || saving" type="primary" @click="openForm"
+          >维护软件版本</el-button
+        >
+      </div>
+      <DeviceReadOnlyFields title="当前软件版本" :fields="valueFields(current.values)" />
+      <DeviceSoftwareSourceHistoryPanel ref="historyPanel" :device-id="deviceId" />
+    </template>
+    <el-dialog
+      v-model="formVisible"
+      title="维护软件版本"
+      width="min(650px, 95vw)"
+      :close-on-click-modal="false"
+      :close-on-press-escape="!saving"
+      :show-close="!saving"
+    >
+      <el-form :model="form" label-width="110px" @submit.prevent="save">
+        <el-form-item v-for="field in stringFields" :key="field.key" :label="field.label"
+          ><el-input v-model="form[field.key]" :maxlength="field.max" :disabled="saving"
+        /></el-form-item>
+        <el-form-item label="定制标记"
+          ><el-select v-model="form.customized" :disabled="saving" clearable placeholder="未记录"
+            ><el-option label="是" :value="true" /><el-option
+              label="否"
+              :value="false" /></el-select
+        ></el-form-item>
+        <el-form-item label="发布日期"
+          ><el-date-picker
+            v-model="form.releaseDate"
+            type="date"
+            value-format="YYYY-MM-DD"
+            :disabled="saving"
+        /></el-form-item>
+        <el-form-item label="版本说明"
+          ><el-input
+            v-model="form.versionDesc"
+            type="textarea"
+            :maxlength="1000"
+            :disabled="saving"
+        /></el-form-item>
+      </el-form>
+      <template #footer
+        ><el-button :disabled="saving" @click="formVisible = false">取消</el-button
+        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
+      >
+    </el-dialog>
+  </section>
+  <ProjectDeviceLogArchiveDialog v-if="projectId" v-model="logArchiveVisible" :project-id="projectId" :readonly="!current?.canMaintain" />
+</template>
+<script setup lang="ts">
+import { onBeforeUnmount, reactive, ref, watch } from 'vue'
+import { ElMessage } from 'element-plus'
+import {
+  getSoftwareVersionCurrent,
+  registerSoftwareVersion,
+  type SoftwareVersionCurrent,
+  type SoftwareVersionValues
+} from '@/api/pms/asset/device'
+import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
+import DeviceSoftwareSourceHistoryPanel from './DeviceSoftwareSourceHistoryPanel.vue'
+import ProjectDeviceLogArchiveDialog from '@/components/DeviceCollection/ProjectDeviceLogArchiveDialog.vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
+const props = defineProps<{ deviceId: number | string; projectId?: number | string }>()
+const emit = defineEmits<{ updated: [] }>()
+const coreFields = [
+  { key: 'conpVersion', label: 'CONP' },
+  { key: 'bootVersion', label: 'BOOT' },
+  { key: 'cpldVersion', label: 'CPLD' },
+  { key: 'pcbVersion', label: 'PCB' }
+] as const
+const stringFields = [
+  { key: 'conpVersion', label: 'CONP', max: 255 },
+  { key: 'conpType', label: 'CONP类型', max: 100 },
+  { key: 'conpSeries', label: 'CONP系列', max: 100 },
+  { key: 'conpMark', label: 'CONP标记', max: 255 },
+  { key: 'bootVersion', label: 'BOOT', max: 255 },
+  { key: 'cpldVersion', label: 'CPLD', max: 255 },
+  { key: 'pcbVersion', label: 'PCB', max: 255 }
+] as const
+const date = (value: unknown) => formatSourceDate(value, 'effectiveFrom') ?? '未记录'
+const valueFields = (value?: SoftwareVersionValues | null) => [
+  ...stringFields.map((field) => ({
+    label: field.label,
+    value: value?.[field.key] === '' ? '空字符串' : value?.[field.key]
+  })),
+  { label: '定制标记', value: value?.customized },
+  { label: '发布日期', value: value?.releaseDate },
+  { label: '版本说明', value: value?.versionDesc }
+]
+const current = ref<SoftwareVersionCurrent>()
+const historyPanel = ref<InstanceType<typeof DeviceSoftwareSourceHistoryPanel>>()
+const loading = ref(false),
+  saving = ref(false),
+  error = ref('')
+const formVisible = ref(false)
+const logArchiveVisible = ref(false)
+const form = reactive<SoftwareVersionValues>({})
+let generation = 0,
+  requestKey = '',
+  expectedVersion = 0
+let active = true
+const load = async () => {
+  const version = ++generation
+  loading.value = true
+  error.value = ''
+  try {
+    const nextCurrent = await getSoftwareVersionCurrent(props.deviceId)
+    if (version !== generation) return
+    current.value = nextCurrent
+    historyPanel.value?.reload()
+  } catch {
+    if (version === generation) error.value = '软件版本加载失败，请重试'
+  } finally {
+    if (version === generation) loading.value = false
+  }
+}
+const openForm = () => {
+  for (const field of stringFields) form[field.key] = current.value?.values?.[field.key] ?? null
+  form.customized = current.value?.values?.customized ?? null
+  form.releaseDate = current.value?.values?.releaseDate ?? null
+  form.versionDesc = current.value?.values?.versionDesc ?? null
+  expectedVersion = current.value!.version
+  requestKey = crypto.randomUUID()
+  formVisible.value = true
+}
+const save = async () => {
+  if (saving.value || !props.projectId) return
+  if (coreFields.every((field) => !form[field.key]?.trim())) {
+    ElMessage.warning('至少填写一个软件版本')
+    return
+  }
+  saving.value = true
+  const deviceId = props.deviceId
+  const projectId = props.projectId
+  try {
+    await registerSoftwareVersion(deviceId, {
+      projectId,
+      expectedVersion,
+      requestKey,
+      values: { ...form }
+    })
+    if (!active || deviceId !== props.deviceId || projectId !== props.projectId) return
+    formVisible.value = false
+    ElMessage.success('软件版本已保存')
+    await load()
+    if (active && deviceId === props.deviceId && projectId === props.projectId) emit('updated')
+  } catch {
+    if (active && deviceId === props.deviceId && projectId === props.projectId) {
+      ElMessage.error('软件版本保存失败，请核对提示后重试')
+    }
+  } finally {
+    saving.value = false
+  }
+}
+watch(
+  () => [props.deviceId, props.projectId],
+  () => {
+    current.value = undefined
+    formVisible.value = false
+    logArchiveVisible.value = false
+    void load()
+  },
+  { immediate: true }
+)
+onBeforeUnmount(() => {
+  active = false
+  generation++
+})
+</script>
+<style scoped>
+.software-actions {
+  display: flex;
+  align-items: center;
+  gap: 12px;
+  margin-bottom: 12px;
+  flex-wrap: wrap;
+}
+</style>
```

## 066 views/pms/asset/device/components/DeviceWarrantyPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**修复遗漏并保留新增功能**。维保字段扩展遗漏的extended已补回；保留独立续保表、全量来源字段、分页、失败提示和旧设备响应隔离。
- 证据及验证边界：device-native-fields.ts；续保/维保组件既有测试。

### H01 @@ -1,6 +1,9 @@

裁决：修复遗漏并保留新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -1,6 +1,9 @@
 <template>
   <DeviceSourceStatus :slice="slice" />
-  <el-button :disabled="loading" class="mb-16px" @click="load">加载维保记录</el-button>
+  <el-button :disabled="loading" class="mb-16px" @click="refresh++">加载维保记录</el-button>
+  <h3>续保记录</h3>
+  <DeviceWarrantyRenewalTable :device-id="deviceId" />
+  <el-alert v-if="error" title="维保资料加载失败，请重试。" type="error" :closable="false" />
   <div v-loading="loading">
     <DeviceReadOnlyFields
       v-if="result?.current"
```

### H02 @@ -21,36 +24,61 @@

裁决：修复遗漏并保留新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -21,36 +24,61 @@
       v-for="(item, index) in result?.records.list || []"
       :key="index"
       :title="`维保记录 ${index + 1}`"
-      :fields="[
-        { label: '开始日期', value: item.warrantyStartDate },
-        { label: '结束日期', value: item.warrantyEndDate },
-        { label: '月数', value: item.warrantyMonths },
-        { label: '合同号', value: item.warrantyContractNo },
-        { label: '续保', value: item.extended }
-      ]"
+      :fields="
+        deviceWarrantyFields.map((field) => ({
+          label: field.label,
+          value: formatSourceDate(item[field.key], field.key)
+        }))
+      "
+    />
+    <el-pagination
+      v-if="result"
+      v-model:current-page="pageNo"
+      :page-size="20"
+      :total="result.records.total"
+      layout="prev,pager,next,total"
     />
   </div>
 </template>
 <script setup lang="ts">
-import { computed, ref } from 'vue'
+import { computed, ref, watch } from 'vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
 import * as DeviceApi from '@/api/pms/asset/device'
 import type { DeviceSourceSliceVO, DeviceWarrantyResultVO } from '@/api/pms/asset/device'
 import DeviceSourceStatus from './DeviceSourceStatus.vue'
+import DeviceWarrantyRenewalTable from './DeviceWarrantyRenewalTable.vue'
 import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
+import { deviceWarrantyFields } from './device-native-fields'
 const props = defineProps<{ deviceId: number; slice: DeviceSourceSliceVO }>()
 const loading = ref(false)
+const error = ref(false)
+const pageNo = ref(1)
+const refresh = ref(0)
 const result = ref<DeviceWarrantyResultVO>()
 const warrantyRange = computed(() =>
   result.value?.current
     ? `${result.value.current.warrantyStartDate || '--'} 至 ${result.value.current.warrantyEndDate || '--'}`
     : '--'
 )
-const load = async () => {
-  loading.value = true
-  try {
-    result.value = await DeviceApi.getWarrantyRecords(props.deviceId, { pageNo: 1, pageSize: 20 })
-  } finally {
-    loading.value = false
-  }
-}
+watch(
+  [() => props.deviceId, pageNo, refresh],
+  async ([id, page], _old, onCleanup) => {
+    let current = true
+    onCleanup(() => {
+      current = false
+    })
+    loading.value = true
+    error.value = false
+    result.value = undefined
+    try {
+      const data = await DeviceApi.getWarrantyRecords(id, { pageNo: page, pageSize: 20 })
+      if (current) result.value = data
+    } catch {
+      if (current) error.value = true
+    } finally {
+      if (current) loading.value = false
+    }
+  },
+  { immediate: true }
+)
 </script>
```

## 067 views/pms/asset/device/components/DeviceWarrantyRenewalTable.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增发货/出厂/安装/软件来源历史、主附SN、软件维护及续保表；新增字段清单不替换原事实。
- 证据及验证边界：对应asset API与Owner；本轮只读核查。

### H01 @@ -0,0 +1,109 @@

裁决：保留纯新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -0,0 +1,109 @@
+<template>
+  <section aria-label="续保记录">
+    <el-form inline @submit.prevent="search">
+      <el-form-item v-if="projectId" label="序列号">
+        <el-input v-model="sn" clearable placeholder="查询设备序列号" @keyup.enter="search" />
+      </el-form-item>
+      <el-form-item
+        ><el-button :loading="loading" @click="search">查询续保记录</el-button></el-form-item
+      >
+    </el-form>
+    <el-alert
+      v-if="error"
+      title="续保记录加载失败，请重新查询。"
+      type="error"
+      :closable="false"
+      class="mb-12px"
+    />
+    <el-table
+      v-loading="loading"
+      :data="rows"
+      border
+      stripe
+      row-key="id"
+      empty-text="暂无符合条件的续保记录"
+      data-testid="warranty-renewal-table"
+    >
+      <el-table-column prop="deviceSn" label="设备序列号" min-width="180" show-overflow-tooltip />
+      <el-table-column
+        prop="warrantyContractNo"
+        label="续保合同号"
+        min-width="180"
+        show-overflow-tooltip
+      />
+      <el-table-column
+        prop="warrantyGrade"
+        label="维保等级编码"
+        min-width="130"
+        show-overflow-tooltip
+      />
+      <el-table-column prop="warrantyStartDate" label="实际开始日期" width="130" />
+      <el-table-column prop="warrantyEndDate" label="实际结束日期" width="130" />
+      <el-table-column label="参考月数" width="100">
+        <template #default="{ row }">{{ row.warrantyMonths ?? '—' }}</template>
+      </el-table-column>
+      <el-table-column label="是否延保" width="100">
+        <template #default="{ row }">{{ flag(row.extended) }}</template>
+      </el-table-column>
+      <el-table-column label="最新选择" width="100">
+        <template #default="{ row }">{{ flag(row.isLatest) }}</template>
+      </el-table-column>
+      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
+    </el-table>
+    <Pagination v-model:page="pageNo" v-model:limit="pageSize" :total="total" @pagination="load" />
+  </section>
+</template>
+
+<script setup lang="ts">
+import { onBeforeUnmount, ref, watch } from 'vue'
+import { getWarrantyRenewals, type WarrantyRenewalRecord } from '@/api/pms/asset/warranty-renewal'
+
+const props = defineProps<{ projectId?: number; deviceId?: number }>()
+const rows = ref<WarrantyRenewalRecord[]>([])
+const total = ref(0)
+const pageNo = ref(1)
+const pageSize = ref(20)
+const loading = ref(false)
+const error = ref(false)
+const sn = ref('')
+let sequence = 0
+const flag = (value?: boolean | null) => (value == null ? '未记录' : value ? '是' : '否')
+const load = async () => {
+  const current = ++sequence
+  rows.value = []
+  error.value = false
+  loading.value = true
+  try {
+    const result = await getWarrantyRenewals({
+      projectId: props.projectId,
+      deviceId: props.deviceId,
+      sn: sn.value || undefined,
+      pageNo: pageNo.value,
+      pageSize: pageSize.value
+    })
+    if (current !== sequence) return
+    rows.value = result.list
+    total.value = Number(result.total)
+  } catch {
+    if (current === sequence) error.value = true
+  } finally {
+    if (current === sequence) loading.value = false
+  }
+}
+const search = () => {
+  pageNo.value = 1
+  total.value = 0
+  void load()
+}
+watch(
+  () => [props.projectId, props.deviceId],
+  () => {
+    sn.value = ''
+    search()
+  },
+  { immediate: true }
+)
+onBeforeUnmount(() => {
+  ++sequence
+})
+</script>
```

## 068 views/pms/asset/device/components/device-native-fields.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**恢复遗漏**。补回原维保记录“续保”字段；新增来源字段继续保留。
- 证据及验证边界：原源DeviceWarrantyPanel.vue的extended字段。

### H01 @@ -0,0 +1,241 @@

裁决：恢复遗漏。当前行来源：LOCAL_UNCOMMITTED, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,241 @@
+export const deviceShipmentFields = [
+  { key: 'productModel', label: '产品目录型号' },
+  { key: 'productName', label: '产品目录名称' },
+  { key: 'secondaryProductModel', label: '副产品目录型号' },
+  { key: 'secondaryProductName', label: '副产品目录名称' },
+  { key: 'sourceUpdatedAt', label: '来源发货更新时间' },
+  {
+    key: 'deviceSn',
+    label: '设备序列号'
+  },
+  {
+    key: 'packageNo',
+    label: '装箱单号'
+  },
+  {
+    key: 'shipmentTime',
+    label: '发货时间'
+  },
+  {
+    key: 'contractNo',
+    label: '合同号'
+  },
+  {
+    key: 'legacyPackageKey',
+    label: '发货包编号'
+  },
+  {
+    key: 'rmaRelatedSn',
+    label: 'RMA设备序列号'
+  },
+  {
+    key: 'rmaMarked',
+    label: 'RMA标记'
+  },
+  {
+    key: 'orderNo',
+    label: '销售订单号'
+  },
+  {
+    key: 'lineNo',
+    label: '订单行号'
+  },
+  {
+    key: 'productCode',
+    label: '产品编码'
+  },
+  {
+    key: 'secondarySn',
+    label: '副设备序列号'
+  },
+  {
+    key: 'secondaryItem',
+    label: '副产品编码'
+  },
+  {
+    key: 'internalSerialNo',
+    label: '组合设备序列号'
+  },
+  {
+    key: 'isRma',
+    label: 'RMA标记'
+  },
+  {
+    key: 'profitCenter',
+    label: '利润中心'
+  },
+  {
+    key: 'soleAgentSuffix',
+    label: '代理后缀'
+  },
+  {
+    key: 'syncTime',
+    label: '来源同步时间'
+  },
+  {
+    key: 'uuid',
+    label: '来源唯一标识'
+  },
+  {
+    key: 'warrantyStartDate',
+    label: '保修开始日期'
+  },
+  {
+    key: 'warrantyMonths',
+    label: '保修月数'
+  },
+  {
+    key: 'rmaNo',
+    label: 'RMA单号'
+  }
+] as const
+export const deviceWarrantyFields = [
+  {
+    key: 'warrantyStartDate',
+    label: '开始日期'
+  },
+  {
+    key: 'warrantyEndDate',
+    label: '结束日期'
+  },
+  {
+    key: 'warrantyMonths',
+    label: '月数'
+  },
+  {
+    key: 'warrantyGrade',
+    label: '维保等级编码'
+  },
+  {
+    key: 'warrantyContractNo',
+    label: '合同号'
+  },
+  {
+    key: 'extended',
+    label: '续保'
+  },
+  {
+    key: 'combinedDeviceSn',
+    label: '组合设备序列号'
+  },
+  {
+    key: 'productCode',
+    label: '产品编码'
+  },
+  {
+    key: 'productName',
+    label: '产品名称'
+  },
+  {
+    key: 'productDescription',
+    label: '产品描述'
+  },
+  {
+    key: 'gradeName',
+    label: '服务等级'
+  },
+  {
+    key: 'shipmentTime',
+    label: '发货日期'
+  },
+  {
+    key: 'warrantyFlag',
+    label: '维保内容'
+  },
+  {
+    key: 'sourceWarrantyStartTime',
+    label: '维保开始时间'
+  },
+  {
+    key: 'sourceWarrantyEndTime',
+    label: '维保结束时间'
+  },
+  {
+    key: 'originalWarrantyStartTime',
+    label: '原维保开始时间'
+  },
+  {
+    key: 'originalWarrantyEndTime',
+    label: '原维保结束时间'
+  },
+  {
+    key: 'remainingDaysAtSource',
+    label: '维保剩余天数（来源当时）'
+  },
+  {
+    key: 'originalRemainingDaysAtSource',
+    label: '原剩余天数（来源当时）'
+  },
+  {
+    key: 'contractType',
+    label: '合同类型编码'
+  },
+  {
+    key: 'contractTypeName',
+    label: '合同类型'
+  },
+  {
+    key: 'projectName',
+    label: '来源项目名称'
+  },
+  {
+    key: 'customerName',
+    label: '购货方'
+  },
+  {
+    key: 'finalCustomerName',
+    label: '来源最终客户名称'
+  },
+  {
+    key: 'agentName',
+    label: '代理商'
+  },
+  {
+    key: 'officeCode',
+    label: '办事处编码'
+  },
+  {
+    key: 'officeName',
+    label: '办事处'
+  },
+  {
+    key: 'marketCode',
+    label: '市场部编码'
+  },
+  {
+    key: 'marketName',
+    label: '市场部'
+  },
+  {
+    key: 'systemCode',
+    label: '系统部编号'
+  },
+  {
+    key: 'systemName',
+    label: '系统部'
+  },
+  {
+    key: 'secondarySn',
+    label: '子设备序列号'
+  },
+  {
+    key: 'secondaryItem',
+    label: '子产品编码'
+  },
+  {
+    key: 'secondaryProductName',
+    label: '子产品名称'
+  },
+  {
+    key: 'secondaryProductDescription',
+    label: '子产品描述'
+  },
+  {
+    key: 'sourceSyncTime',
+    label: '来源同步时间'
+  },
+  {
+    key: 'sourceShipmentKey',
+    label: '来源发货编号'
+  }
+] as const
```

## 069 views/pms/asset/device/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留纯新增功能**。新增状态变更和删除按钮，仅调用原服务端正式接口和权限，不执行操作或改历史。
- 证据及验证边界：DeviceWorkbenchDialogs和设备Owner授权。

### H01 @@ -181,7 +181,7 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -181,7 +181,7 @@
         min-width="180"
         show-overflow-tooltip
       />
-      <el-table-column label="操作" width="90" :fixed="isMobile ? false : 'right'">
+      <el-table-column label="操作" width="240" :fixed="isMobile ? false : 'right'">
         <template #default="{ row }">
           <el-button
             link
```

### H02 @@ -190,6 +190,8 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -190,6 +190,8 @@
             :loading="openingDeviceId === row.deviceId"
             >详情</el-button
           >
+          <el-button link type="warning" @click.stop="openStatusChange(row)" v-hasPermi="['pms:device:status-change']">状态变更</el-button>
+          <el-button link type="danger" @click.stop="remove(row)" v-hasPermi="['pms:device:delete']">删除</el-button>
         </template>
       </el-table-column>
     </el-table>
```

### H03 @@ -247,6 +249,8 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -247,6 +249,8 @@
   await nextTick()
   return dialogs.value
 }
+const openStatusChange = async (row: DeviceListVO) => (await ensureDialogs())?.openStatusChange(row)
+const remove = async (row: DeviceListVO) => (await ensureDialogs())?.remove(row)
 const clearDetail = () => {
   dialogRequest++
   dialogs.value?.clearDetail()
```

## 070 views/pms/business/requirement-analysis/index.vue

- 原始状态：TARGET_ORIGINAL_REMOVED；原源/原目标文件相同：False；本轮前片段：0；当前片段：0。
- 裁决：**保留已裁决废弃**。源基线本就不存在该旧目标新增重复页；已按前轮同功能裁决删除，正式需求分析EntityPanel不删除，不恢复重复写路径。
- 证据及验证边界：32-file-hunk-adjudication.md; baseline-correction.md。

当前内容已与原源一致，或属于原源不存在的已裁决废弃路径。

## 071 views/pms/business/site-survey/SurveyBusinessFields.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接入并列明重叠**。新site-survey-business独立承接入口与原工勘专业页有创建/修改/归档/附件重叠；本轮不将它替换进原阶段任务，不删除原EntityPanel。保留新增来源接入，原页面及状态/权限为既有基准，单列为尚未执行写验收的入口。
- 证据及验证边界：工勘业务视图注册与Owner；原阶段工勘浏览器回读。

### H01 @@ -0,0 +1,38 @@

裁决：保留新增接入并列明重叠。当前行来源：2ca967d795d0d1ba4b3861173ec39620d4bb999c。

```diff
@@ -0,0 +1,38 @@
+<template>
+  <section v-if="current" class="survey-business-fields">
+    <el-collapse v-model="expanded">
+      <el-collapse-item title="维护结构化工勘地点" name="location">
+        <el-input v-model="description" aria-label="地点说明" :disabled="busy || !allowed('location')" />
+        <PmsLocationSelector v-if="expanded.includes('location') && allowed('location')" v-model="location" :project-id="projectId" />
+        <el-button :disabled="busy || !allowed('location') || !location || !description.trim()" @click="execute('location',{description,location})">保存结构化地点</el-button>
+      </el-collapse-item>
+      <el-collapse-item title="项目要求完成日期" name="deadline">
+        <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" aria-label="项目要求完成日期" :disabled="busy || !allowed('deadline')" />
+        <el-button :loading="readingProject" :disabled="busy || readingProject || !allowed('deadline') || !endDate" @click="saveDeadline">更新项目期限</el-button>
+        <el-alert v-if="error" :title="error" type="error" :closable="false" />
+      </el-collapse-item>
+    </el-collapse>
+  </section>
+</template>
+<script setup lang="ts">
+import {computed,ref,watch} from 'vue'
+import PmsLocationSelector from '@/components/PmsLocationSelector/index.vue'
+import type {LocationMaintainRequest} from '@/api/pms/asset/location'
+import type {BusinessEntityData,OperationVO} from '@/api/pms/platform/businessmodel'
+import {getProject} from '@/api/pms/project/projects'
+const props=defineProps<{current?:BusinessEntityData;busy:boolean;actions:OperationVO[];execute:(code:string,values:Record<string,unknown>)=>Promise<unknown>}>()
+const expanded=ref<string[]>([])
+const description=ref(''),location=ref<LocationMaintainRequest>(),endDate=ref(''),readingProject=ref(false),error=ref('')
+const projectId=computed(()=>{const value=Number(props.current?.fieldValues.projectId);return Number.isSafeInteger(value)&&value>0?value:undefined})
+const allowed=(code:string)=>props.actions.some(action=>action.code===code&&action.executable)
+watch(()=>props.current,row=>{description.value=String(row?.fieldValues.location||'');endDate.value=String(row?.fieldValues.requiredEndDate||'');location.value=undefined;error.value=''}, {immediate:true})
+const saveDeadline=async()=>{
+  if(!projectId.value || props.busy || readingProject.value)return
+  const selected=props.current;readingProject.value=true;error.value=''
+  try{const project=await getProject(projectId.value);if(props.current!==selected)return
+    if(project.version==null)throw new Error('项目版本不可用，请重新读取项目')
+    await props.execute('deadline',{projectVersion:project.version,endDate:endDate.value})
+  }catch(failure){error.value=failure instanceof Error?failure.message:'项目期限读取失败'}
+  finally{readingProject.value=false}
+}
+</script>
```

## 072 views/pms/business/site-survey/index.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接入并列明重叠**。新site-survey-business独立承接入口与原工勘专业页有创建/修改/归档/附件重叠；本轮不将它替换进原阶段任务，不删除原EntityPanel。保留新增来源接入，原页面及状态/权限为既有基准，单列为尚未执行写验收的入口。
- 证据及验证边界：工勘业务视图注册与Owner；原阶段工勘浏览器回读。

### H01 @@ -0,0 +1,11 @@

裁决：保留新增接入并列明重叠。当前行来源：00dc6b6c9e2c499c7a27bba14df033933f9b1b13, 103e925d34c772afa3552b3cf7f84441dd96a938, 2ca967d795d0d1ba4b3861173ec39620d4bb999c。

```diff
@@ -0,0 +1,11 @@
+<template>
+  <ProjectBusinessPage api-base="/api/v1/pms/site-survey-business" title="现场工勘" deliverable-type="ATTACHMENT" :operation-allowed="allowed" :hidden-actions="['location','deadline']">
+    <template #business-fields="{current,execute,busy,actions}"><SurveyBusinessFields :current="current" :execute="execute" :busy="busy" :actions="actions" /></template>
+  </ProjectBusinessPage>
+</template>
+<script setup lang="ts">
+import type { BusinessEntityData } from '@/api/pms/platform/businessmodel'
+const allowed=(code:string,current?:BusinessEntityData)=>code==='create' || (code==='archive' ? current?.fieldValues.status===1 : current?.fieldValues.status===0)
+import SurveyBusinessFields from './SurveyBusinessFields.vue'
+import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
+</script>
```

## 073 views/pms/commerce/delivery-scope/DeliveryScopeHistoryDrawer.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留新增事实及契约配套**。新增合同/订单数量/分配确认/发货数量；departmentName对应当前服务端字段；未确认分配不能按有效范围维护。
- 证据及验证边界：DeliveryScopeRespVO及CommerceDeliveryScopeQueryService。

### H01 @@ -4,10 +4,17 @@

裁决：保留新增事实及契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -4,10 +4,17 @@
       <el-table-column prop="allocationVersion" label="分配版本" min-width="100" />
       <el-table-column prop="scopeStatus" label="状态" min-width="110" />
       <el-table-column prop="allocatedQuantity" label="数量" min-width="100" />
-      <el-table-column prop="officeDepartmentName" label="发生时办事处" min-width="160" />
-      <el-table-column prop="effectiveFrom" label="生效时间" min-width="170" />
+      <el-table-column label="分配确认" min-width="120">
+        <template #default="{ row }">{{ row.allocationConfirmed ? '已确认' : '未确认' }}</template>
+      </el-table-column>
+      <el-table-column prop="deliveredQuantity" label="来源已发货数量" min-width="140" />
+      <el-table-column prop="openQuantity" label="来源未交数量" min-width="130" />
+      <el-table-column prop="departmentName" label="发生时办事处" min-width="160" />
+      <el-table-column prop="effectiveFrom" label="生效时间" min-width="170">
+        <template #default="{ row }">{{ formatDate(row.effectiveFrom) }}</template>
+      </el-table-column>
       <el-table-column prop="effectiveTo" label="失效时间" min-width="170">
-        <template #default="{ row }">{{ row.effectiveTo || '当前有效' }}</template>
+        <template #default="{ row }">{{ row.effectiveTo ? formatDate(row.effectiveTo) : (row.allocationConfirmed ? '当前有效' : '未确认分配') }}</template>
       </el-table-column>
       <el-table-column type="expand">
         <template #default="{ row }">
```

### H02 @@ -26,6 +33,7 @@

裁决：保留新增事实及契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -26,6 +33,7 @@
 
 <script setup lang="ts">
 import { ref } from 'vue'
+import { formatDate } from '@/utils/formatTime'
 import * as CommerceApi from '@/api/pms/commerce'
 import type { DeliveryScopeRespVO } from '@/api/pms/commerce'
```

## 074 views/pms/commerce/delivery-scope/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留新增事实及契约配套**。新增合同/订单数量/分配确认/发货数量；departmentName对应当前服务端字段；未确认分配不能按有效范围维护。
- 证据及验证边界：DeliveryScopeRespVO及CommerceDeliveryScopeQueryService。

### H01 @@ -61,19 +61,30 @@

裁决：保留新增事实及契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -61,19 +61,30 @@
         :data="rows"
         :empty-text="projectContext ? '当前筛选条件下没有交付范围' : '请先从项目工作台进入交付范围'"
       >
-        <el-table-column prop="orderNo" label="订单号" min-width="150" fixed="left" />
+        <el-table-column prop="contractNo" label="合同号" min-width="155" fixed="left" />
+        <el-table-column prop="orderNo" label="订单号" min-width="150" />
         <el-table-column prop="lineNo" label="行号" min-width="80" />
         <el-table-column prop="productCode" label="产品编码" min-width="130" />
-        <el-table-column prop="allocatedQuantity" label="分配数量" min-width="105" />
+        <el-table-column prop="productDesc" label="产品名称" min-width="220" show-overflow-tooltip />
+        <el-table-column prop="orderQuantity" label="订单数量" min-width="105" />
+        <el-table-column prop="allocatedQuantity" label="范围数量" min-width="105" />
+        <el-table-column prop="unitCode" label="单位" min-width="80" />
+        <el-table-column label="分配确认" min-width="120">
+          <template #default="{ row }">{{ row.allocationConfirmed ? '已确认' : '未确认' }}</template>
+        </el-table-column>
+        <el-table-column prop="deliveredQuantity" label="来源已发货数量" min-width="140" />
+        <el-table-column prop="openQuantity" label="来源未交数量" min-width="130" />
         <el-table-column prop="scopeStatus" label="状态" min-width="110" />
         <el-table-column prop="allocationVersion" label="分配版本" min-width="100" />
         <el-table-column
-          prop="officeDepartmentName"
+          prop="departmentName"
           label="发生时办事处"
           min-width="170"
           show-overflow-tooltip
         />
-        <el-table-column prop="effectiveFrom" label="生效时间" min-width="170" />
+        <el-table-column prop="effectiveFrom" label="生效时间" min-width="170">
+          <template #default="{ row }">{{ formatDate(row.effectiveFrom) }}</template>
+        </el-table-column>
         <el-table-column label="明细主体" min-width="220" show-overflow-tooltip>
           <template #default="{ row }">{{ detailSubjects(row) }}</template>
         </el-table-column>
```

### H02 @@ -120,6 +131,7 @@

裁决：保留新增事实及契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -120,6 +131,7 @@
 
 <script setup lang="ts">
 import { ElMessageBox } from 'element-plus'
+import { formatDate } from '@/utils/formatTime'
 import { computed, onMounted, reactive, ref, watch } from 'vue'
 import { useWindowSize } from '@vueuse/core'
 import { useRoute } from 'vue-router'
```

### H03 @@ -189,7 +201,7 @@

裁决：保留新增事实及契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -189,7 +201,7 @@
   query.includeHistory = false
   search()
 }
-const isCurrent = (row: DeliveryScopeRespVO) => !row.effectiveTo && row.scopeStatus !== 'RELEASED'
+const isCurrent = (row: DeliveryScopeRespVO) => row.allocationConfirmed === true && !row.effectiveTo && row.scopeStatus !== 'RELEASED'
 const detailSubjects = (row: DeliveryScopeRespVO) =>
   row.details
     .map((item) => item.serialNo || item.productCode || item.deviceTypeCode)
```

## 075 views/pms/customer/contacts/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：7；当前片段：7。
- 裁决：**保留新增历史及其保护**。新增来源联系单位、人员编码、电话和时间；项目历史联系人独立可读但不可修改删除，不将缺客户主档当作无历史联系人。
- 证据及验证边界：contacts既有测试；历史只读契约。

### H01 @@ -66,9 +66,14 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -66,9 +66,14 @@
           <template #default="{ row }">{{ row.customerName || context?.customerName || selectedCustomerName }}</template>
         </el-table-column>
         <el-table-column prop="name" label="姓名" min-width="100" />
+        <el-table-column v-if="projectId" label="联系单位" min-width="180">
+          <template #default="{ row }">{{ row.projectPartyId ? `最终客户：${row.projectPartyName || '待关联'}` : (row.customerName || context?.customerName || '-') }}</template>
+        </el-table-column>
+        <el-table-column v-if="projectId" prop="contactCode" label="人员编码" min-width="100" />
         <el-table-column prop="department" label="部门" min-width="120" />
         <el-table-column prop="title" label="职务" min-width="120"><template #default="{ row }">{{ row.title }}</template></el-table-column>
         <el-table-column prop="mobile" label="手机" min-width="130" />
+        <el-table-column prop="phone" label="电话" min-width="130" />
         <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
         <el-table-column prop="primaryFlag" label="主联系人" width="90">
           <template #default="{ row }">
```

### H02 @@ -79,10 +84,19 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -79,10 +84,19 @@
         <el-table-column prop="status" label="状态" width="90">
           <template #default="{ row }"><dict-tag type="pms_contact_status" :value="row.status" /></template>
         </el-table-column>
+        <el-table-column v-if="projectId" label="生效时间" min-width="165">
+          <template #default="{ row }">{{ row.effectiveFrom == null ? '-' : formatDate(row.effectiveFrom) }}</template>
+        </el-table-column>
+        <el-table-column v-if="projectId" label="失效时间" min-width="165">
+          <template #default="{ row }">{{ row.effectiveTo == null ? '-' : formatDate(row.effectiveTo) }}</template>
+        </el-table-column>
         <el-table-column label="操作" width="180" fixed="right">
           <template #default="{ row }">
+            <span v-if="isHistorical(row)">只读历史</span>
+            <template v-else>
             <el-button v-if="projectId || checkPermi(['pms:customer-contact:update'])" link type="primary" :disabled="!canEdit" @click="open(row)">编辑</el-button>
             <el-button v-if="projectId || checkPermi(['pms:customer-contact:delete'])" link type="danger" :disabled="!canEdit" @click="remove(row)">删除</el-button>
+            </template>
           </template>
         </el-table-column>
         <template #empty>
```

### H03 @@ -168,6 +182,7 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -168,6 +182,7 @@
 import ContactFields from './ContactFields.vue'
 import ContactHistoryDialog from './ContactHistoryDialog.vue'
 import { getIntDictOptions } from '@/utils/dict'
+import { formatDate } from '@/utils/formatTime'
 
 defineOptions({ name: 'PmsCustomerContactsWorkbench' })
 const props = withDefaults(defineProps<{ projectId?: number; showCustomerFilter?: boolean; showSectionHeader?: boolean
```

### H04 @@ -183,6 +198,7 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -183,6 +198,7 @@
 const query = reactive({ pageNo: 1, pageSize: 10, customerId: undefined as number | undefined, name: '', status: undefined as number | undefined })
 const selectedCustomerName = ref('')
 const rows = ref<ContactVO[]>([]), total = ref(0)
+const isHistorical = (row: ContactVO) => !!row.projectPartyId || !!row.historicalReadonly
 const displayRows = computed(() => [...rows.value].sort((left, right) => {
   const leftPrimary = left.primaryFlag && left.status === 0 ? 1 : 0
   const rightPrimary = right.primaryFlag && right.status === 0 ? 1 : 0
```

### H05 @@ -221,7 +237,6 @@

裁决：保留新增历史及其保护。当前行来源：SOURCE_BASELINE_DELETION_ONLY。

```diff
@@ -221,7 +237,6 @@
       if (current !== sequence) return
       context.value = result
     }
-    if (props.projectId && !customerId.value) { rows.value = []; total.value = 0; return context.value }
     const page = props.projectId ? await ContactApi.getProjectPage(props.projectId, query)
       : await ContactApi.getMasterPage({ ...query, customerId: customerId.value })
     if (current !== sequence) return
```

### H06 @@ -302,6 +317,7 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -302,6 +317,7 @@
   }
 }
 const open = (row?: ContactVO) => {
+  if (row && isHistorical(row)) return
   if (!canEdit.value || (props.projectId && !customerId.value)) return
   createMode.value = 'new'; sourceRows.clear()
   form.value = { name: '', department: '', title: '', mobile: '', phone: '', email: '', remark: '', primaryFlag: !!props.projectId && total.value === 0, status: 0, customerId: customerId.value, ...row }
```

### H07 @@ -334,6 +350,7 @@

裁决：保留新增历史及其保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -334,6 +350,7 @@
   finally { saving.value = false }
 }
 const remove = async (row: ContactVO) => {
+  if (isHistorical(row)) return
   if (!canEdit.value) return
   if (row.primaryFlag && props.projectId) await message.confirm('删除当前主联系人并确认项目暂不设置主联系人？客户主档和历史快照将保留。')
   else await message.delConfirm()
```

## 076 views/pms/cutover/components/CutoverChecklistField.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。这些片段仅处理文件不存在/失效和FileId无损类型；不迁入更晚来源的审批布局或新增业务规则。
- 证据及验证边界：既有文件、割接失败与只读保护测试；32-file-verification.md。

### H01 @@ -267,10 +267,9 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -267,10 +267,9 @@
   const selectedItemId = String(props.item.itemId)
   const selectedStableItemKey = props.item.stableItemKey
   const selectedFileKey = fileKey(selection.referenceKey)
-  const [artifact, versions] = await Promise.all([
-    FileApi.getArtifact(selection.artifactId, selectedFileKey),
-    FileApi.getVersions(selection.artifactId, { ...selectedFileKey, pageSize: 20 })
-  ])
+  const artifact = await FileApi.getArtifact(selection.artifactId, selectedFileKey)
+  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
+  const versions = await FileApi.getVersions(selection.artifactId, { ...selectedFileKey, pageSize: 20 })
   if (String(props.item.itemId) !== selectedItemId) return
   const version = versions.items.find((entry) => entry.versionNo === selection.versionNo)
   if (!version) throw new Error('PLT 未返回刚完成的文件版本')
```

## 077 views/pms/cutover/components/CutoverClosureEvidencePanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。这些片段仅处理文件不存在/失效和FileId无损类型；不迁入更晚来源的审批布局或新增业务规则。
- 证据及验证边界：既有文件、割接失败与只读保护测试；32-file-verification.md。

### H01 @@ -119,6 +119,7 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -119,6 +119,7 @@
 const completeManualUpload = async (selection: FileSelection) => {
   const key = fileKey(selection.referenceKey)
   const artifact = await FileApi.getArtifact(selection.artifactId, key)
+  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
   const versions = await FileApi.getVersions(selection.artifactId, { ...key, pageSize: 20 })
   const version = versions.items.find((row) => row.versionNo === selection.versionNo)
   if (!version) throw new Error('PLT 未返回人工采集结果版本')
```

## 078 views/pms/cutover/components/CutoverClosureForm.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。这些片段仅处理文件不存在/失效和FileId无损类型；不迁入更晚来源的审批布局或新增业务规则。
- 证据及验证边界：既有文件、割接失败与只读保护测试；32-file-verification.md。

### H01 @@ -141,6 +141,7 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -141,6 +141,7 @@
   try {
     const key = fileKey(purposeCode, selection.referenceKey)
     const artifact = await FileApi.getArtifact(selection.artifactId, key)
+    if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
     references.value[purposeCode] = { artifactId: selection.artifactId, referenceVersion: artifact.reference.referenceVersion }
     const versions = await FileApi.getVersions(selection.artifactId, { ...key, pageSize: 20 })
     const version = versions.items.find((row) => row.versionNo === selection.versionNo)
```

## 079 views/pms/cutover/components/CutoverPlanEditor.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。这些片段仅处理文件不存在/失效和FileId无损类型；不迁入更晚来源的审批布局或新增业务规则。
- 证据及验证边界：既有文件、割接失败与只读保护测试；32-file-verification.md。

### H01 @@ -445,6 +445,7 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -445,6 +445,7 @@
 }
 const resolveFileFact = async (selection: Pick<CutoverPlanFileFact, 'artifactId' | 'versionNo' | 'referenceKey'> & { referenceVersion?: number }) => {
   const artifact = await FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey))
+  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
   if (selection.referenceVersion !== undefined && selection.referenceVersion !== artifact.reference.referenceVersion) {
     throw new Error('材料引用在读取期间已变化，请重新读取文件状态')
   }
```

## 080 views/pms/cutover/components/CutoverPlanPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容**。这些片段仅处理文件不存在/失效和FileId无损类型；不迁入更晚来源的审批布局或新增业务规则。
- 证据及验证边界：既有文件、割接失败与只读保护测试；32-file-verification.md。

### H01 @@ -602,6 +602,7 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -602,6 +602,7 @@
     const reference = await FileApi.getCurrentReference(fileKey(referenceKey))
     if (!reference || reference.status !== 'ACTIVE') return
     const artifact = await FileApi.getArtifact(reference.artifactId, fileKey(referenceKey))
+    if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
     const versions = await FileApi.getVersions(reference.artifactId, { ...fileKey(referenceKey), pageSize: 20 })
     const version = versions.items.find(row => row.versionNo === reference.versionNo)
     if (!version || reference.referenceVersion !== artifact.reference.referenceVersion) throw new Error('方案文件引用已变化')
```

### H02 @@ -627,6 +628,7 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -627,6 +628,7 @@
   createFileReads.value++
   try {
   const artifact = await FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey))
+  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
   const versions = await FileApi.getVersions(selection.artifactId, { ...fileKey(selection.referenceKey), pageSize: 20 })
   const version = versions.items.find((row) => row.versionNo === selection.versionNo)
   if (!version) throw new Error('PLT 未返回刚完成的方案文件版本')
```

## 081 views/pms/cutover/task-detail/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：True；本轮前片段：2；当前片段：2。
- 裁决：**保留测试修复**。保留严格closureOnly/P6只读边界，原候选人材料审核和已归档结果回读不回归。
- 证据及验证边界：cutover/task-detail/index.spec.ts; 32-file-verification.md。

### H01 @@ -111,7 +111,7 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -111,7 +111,7 @@
 const CutoverClosurePanel = defineAsyncComponent(() => import('../components/CutoverClosurePanel.vue').then(module => module.default))
 const CutoverPlanPanel = defineAsyncComponent(() => import('../components/CutoverPlanPanel.vue').then(module => module.default))
 
-const props = defineProps<{ taskId: WireLong; projectId?: WireLong; readonly?: boolean; approvalOnly?: boolean }>()
+const props = defineProps<{ taskId: WireLong; projectId?: WireLong; readonly?: boolean; approvalOnly?: boolean; closureOnly?: boolean }>()
 const emit = defineEmits<{ close: []; changed: [] }>()
 const message = useMessage()
 const planPanel = ref<InstanceType<typeof CutoverPlanPanel>>()
```

### H02 @@ -119,7 +119,8 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -119,7 +119,8 @@
 const approvalPanel = ref<InstanceType<typeof CutoverApprovalPanel>>()
 const stageActions = shallowRef<HTMLElement>()
 const detail = ref<CutoverTaskDetail | null>(null)
-const readonly = computed(() => !!props.readonly || !!detail.value?.approvalReadOnly)
+const readonly = computed(() => !!props.readonly || !!detail.value?.approvalReadOnly ||
+  (!!props.closureOnly && (detail.value?.task.currentStage !== 'P6' || selectedStage.value !== 'P6')))
 const detailLoading = ref(false)
 const detailError = ref('')
 const selectedStage = ref<CutoverApi.CutoverStage | null>(null)
```

## 082 views/pms/engineering/arrival-acceptance/components/ArrivalEvidencePanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容**。证据文件Long ID和失效引用处理，不改变签收与审批规则。
- 证据及验证边界：到货证据既有测试。

### H01 @@ -81,6 +81,7 @@

裁决：保留文件兼容。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -81,6 +81,7 @@
 
 const completeUpload = async (selection: FileSelection) => {
   const artifact = await FileApi.getArtifact(selection.artifactId, keyOf(selection.referenceKey))
+  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
   const versions = await FileApi.getVersions(selection.artifactId, {
     ...keyOf(selection.referenceKey),
     pageSize: 20
```

### H02 @@ -110,7 +111,7 @@

裁决：保留文件兼容。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -110,7 +111,7 @@
     try {
       artifactName.value = (
         await FileApi.getArtifact(artifactId, keyOf(evidence.referenceKey))
-      ).name
+      )?.name ?? '文件信息暂不可用'
     } catch {
       artifactName.value = '文件信息暂不可用'
     }
```

## 083 views/pms/engineering/arrival/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：8；当前片段：8。
- 裁决：**保留测试修复及新增操作**。保留稳定签收槽的引用发现、读取失败保护、归集重试、失效材料撤回；不得回退成刷新后丢附件或重复上传。撤回保留原材料历史，不在本次调用。
- 证据及验证边界：arrivalWorkspace.runtime.spec.ts; 原验收闭环证据。

### H01 @@ -126,11 +126,13 @@

裁决：保留测试修复及新增操作。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -126,11 +126,13 @@
         </el-col>
       </el-row>
     </el-form>
-    <DeliveryPanel v-if="form.id" owner-module="IMP" entity-type="arrival" :entity-id="form.id" :readonly="true" />
+    <DeliveryPanel v-if="form.id" :key="`${form.id}-${deliveryEpoch}`" owner-module="IMP" entity-type="arrival" :entity-id="form.id" :readonly="true" />
     <section class="sign-document-section" aria-label="签收单附件">
       <h4>签收单附件</h4>
       <template v-if="form.id">
+        <el-skeleton v-if="slotLoading" :rows="2" animated />
         <PmsFileReferenceList
+          v-else-if="!slotError"
           :key="`sign-slot-${slotEpoch}`"
           owner-context="IMP"
           object-type="ARRIVAL"
```

### H02 @@ -138,12 +140,13 @@

裁决：保留测试修复及新增操作。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -138,12 +140,13 @@
           purpose-code="ARRIVAL_SIGN_DOCUMENT"
           reference-key="arrival-sign-document"
           :artifact-id="signSlot?.artifactId"
-          :editable="!readOnly"
-          @loaded="onSignDocumentLoaded"
-          @detached="onSignDocumentDetached"
+          :editable="!readOnly && !deliverySaving"
+          @loaded="signDocumentLoaded"
+          @detached="signDocumentDetached"
         />
         <PmsFileUploader
-          v-if="!readOnly"
+          v-if="!readOnly && !deliverySaving && !slotLoading && !slotError"
+          :key="form.id"
           owner-context="IMP"
           object-type="ARRIVAL"
           :object-id="String(form.id)"
```

### H03 @@ -153,8 +156,20 @@

裁决：保留测试修复及新增操作。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -153,8 +156,20 @@
           :artifact-id="signSlot?.artifactId"
           :expected-reference-version="signSlot?.referenceVersion"
           accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx,.xls,.xlsx"
-          @completed="onSignDocumentUploaded"
+          @completed="signDocumentUploaded"
         />
+        <el-alert v-if="slotError" :title="slotError" type="error" :closable="false" show-icon>
+          <template #default><el-button @click="loadSignDocument(form.id)">重新读取附件</el-button></template>
+        </el-alert>
+        <el-alert v-if="deliveryError" :title="deliveryError" type="error" :closable="false" show-icon>
+          <template #default>
+            <el-button :loading="deliverySaving" :disabled="readOnly" @click="collectSignDocument(form.id)">重试归集已上传附件</el-button>
+          </template>
+        </el-alert>
+        <el-button v-if="!readOnly" :loading="deliverySaving"
+          :disabled="slotLoading || !!slotError" @click="withdrawObsoleteSignMaterials(form.id)">
+          撤回已失效旧版本材料
+        </el-button>
         <div v-if="legacyAttachments.length" class="legacy-links">
           <span>历史附件：</span>
           <a v-for="url in legacyAttachments" :key="url" :href="url" target="_blank" rel="noopener"
```

### H04 @@ -165,8 +180,8 @@

裁决：保留测试修复及新增操作。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -165,8 +180,8 @@
       <span v-else class="slot-hint">保存后可上传签收单附件，自动归入交付清单</span>
     </section>
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
-      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
+      <el-button :disabled="deliverySaving" @click="formVisible = false">取消</el-button>
+      <el-button v-if="!readOnly" type="primary" :loading="saving" :disabled="deliverySaving" @click="save">保存</el-button>
     </template>
   </Dialog>
 </template>
```

### H05 @@ -182,7 +197,9 @@

裁决：保留测试修复及新增操作。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -182,7 +197,9 @@
 import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
 import { PmsFileReferenceList, PmsFileUploader } from '@/components/PmsFileArtifact'
 import type { FileSelection } from '@/components/PmsFileArtifact'
-import type { FileArtifactVO } from '@/api/pms/platform/file'
+import type { FileArtifactVO, FileId } from '@/api/pms/platform/file'
+import { getArtifact } from '@/api/pms/platform/file'
+import { listMaterials, withdrawMaterial } from '@/api/pms/platform/delivery'
 import { checkPermi } from '@/utils/permission'
 import { dateFormatter } from '@/utils/formatTime'
```

### H06 @@ -204,8 +221,14 @@

裁决：保留测试修复及新增操作。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -204,8 +221,14 @@
   arrivalTime: [{ required: true, message: '请选择到货时间' }]
 }
 // 槽位只保留挂接事实：completion 先给最小事实驱动列表重载，@loaded 再精化为当前版本。
-const signSlot = ref<{ artifactId?: number; referenceVersion?: number }>()
+const signSlot = ref<{ artifactId?: FileId; referenceVersion?: number }>()
 const slotEpoch = ref(0)
+const slotLoading = ref(false)
+const slotError = ref('')
+let slotSequence = 0
+const deliveryEpoch = ref(0)
+const deliverySaving = ref(false)
+const deliveryError = ref('')
 const legacyAttachments = computed(() =>
   (form.value.attachmentUrl ?? '')
     .split(',')
```

### H07 @@ -215,14 +238,92 @@

裁决：保留测试修复及新增操作。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725, a9f9eb4a04485a562c4203ec479025c8eab84ea4, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -215,14 +238,92 @@
 const onSignDocumentLoaded = (artifact: FileArtifactVO) => {
   signSlot.value = { artifactId: artifact.artifactId, referenceVersion: artifact.reference.referenceVersion }
 }
-const onSignDocumentUploaded = (selection: FileSelection) => {
+const loadSignDocument = async (id: number) => {
+  const sequence = ++slotSequence
+  slotLoading.value = true; slotError.value = ''
+  try {
+    const materials = await listMaterials('IMP', 'arrival', id, 'IMP.ARRIVAL_SIGN_DOCUMENT')
+    const candidates = materials.filter(item => item.materialKind === 'FILE' && item.fileBusinessKey?.ownerContext === 'IMP'
+      && item.fileBusinessKey.objectType === 'ARRIVAL' && String(item.fileBusinessKey.objectId) === String(id)
+      && item.fileBusinessKey.purposeCode === 'ARRIVAL_SIGN_DOCUMENT' && item.fileBusinessKey.referenceKey === 'arrival-sign-document')
+    // Materials retain historical artifacts. Only the current native reference
+    // resolves with this complete stable key; never infer a new file identity.
+    let current: FileArtifactVO | null | undefined
+    for (const candidate of candidates) {
+      try { current = await getArtifact(candidate.fileArtifactId!, candidate.fileBusinessKey!); if (current) break }
+      catch { if (candidate === candidates[candidates.length - 1]) throw new Error('附件引用已变化，请重新读取') }
+    }
+    if (sequence !== slotSequence || id !== form.value.id) return
+    signSlot.value = current?.reference.status === 'ACTIVE'
+      ? { artifactId: current.artifactId, referenceVersion: current.reference.referenceVersion } : undefined
+    slotEpoch.value += 1
+  } catch (error: any) {
+    if (sequence === slotSequence && id === form.value.id) slotError.value = error?.message || '附件读取失败，请重试'
+  } finally { if (sequence === slotSequence) slotLoading.value = false }
+}
+const collectSignDocument = async (id: number) => {
+  if (id !== form.value.id || readOnly.value || deliverySaving.value) return
+  deliverySaving.value = true
+  deliveryError.value = ''
+  try {
+    // Save the persisted Owner solely to collect its locked file set. Local edits
+    // remain in the form until the user explicitly saves them.
+    const saved: ArrivalVO = await ArrivalApi.getArrival(id)
+    if (!saved || saved.id !== id || !editableRecord(saved)) throw new Error('到货记录已不可编辑，请重新读取')
+    await ArrivalApi.updateArrival(saved)
+    const current: ArrivalVO = await ArrivalApi.getArrival(id)
+    if (id === form.value.id) {
+      form.value.version = current.version
+      form.value.status = current.status
+      deliveryEpoch.value += 1
+    }
+  } catch (error: any) {
+    if (id === form.value.id) deliveryError.value = error?.message || '附件已上传，交付件归集失败，请重试'
+  } finally {
+    deliverySaving.value = false
+  }
+}
+const onSignDocumentUploaded = async (id: number, selection: FileSelection) => {
+  if (id !== form.value.id) return
   signSlot.value = { artifactId: selection.artifactId }
   slotEpoch.value += 1
+  await collectSignDocument(id)
+}
+const signDocumentUploaded = computed(() => onSignDocumentUploaded.bind(null, form.value.id!))
+const withdrawObsoleteSignMaterials = async (id: number) => {
+  if (id !== form.value.id || readOnly.value || deliverySaving.value || slotLoading.value || slotError.value) return
+  deliverySaving.value = true; deliveryError.value = ''
+  try {
+    const candidates = await listMaterials('IMP', 'arrival', id, 'IMP.ARRIVAL_SIGN_DOCUMENT')
+    for (const material of candidates) {
+      const key = material.fileBusinessKey
+      if (material.status !== 'ACTIVE' || material.materialKind !== 'FILE' || !key || key.ownerContext !== 'IMP'
+        || key.objectType !== 'ARRIVAL' || String(key.objectId) !== String(id)
+        || key.purposeCode !== 'ARRIVAL_SIGN_DOCUMENT' || key.referenceKey !== 'arrival-sign-document') continue
+      const actual = await getArtifact(material.fileArtifactId!, key)
+      if (!actual) throw new Error('无法读取实际附件，未撤回材料')
+      if (actual.reference.status !== 'ACTIVE' || actual.reference.versionNo !== material.fileVersionNo) {
+        await withdrawMaterial(material.id)
+      }
+    }
+    if (id === form.value.id) deliveryEpoch.value += 1
+  } catch (error: any) {
+    if (id === form.value.id) deliveryError.value = error?.message || '旧版本材料撤回失败，请刷新后重试'
+  } finally { deliverySaving.value = false }
 }
 const onSignDocumentDetached = () => {
   signSlot.value = undefined
   slotEpoch.value += 1
-}
+  deliveryEpoch.value += 1
+}
+const signDocumentLoaded = computed(() => {
+  const id = form.value.id
+  return (artifact: FileArtifactVO) => { if (id === form.value.id) onSignDocumentLoaded(artifact) }
+})
+const signDocumentDetached = computed(() => {
+  const id = form.value.id
+  return () => { if (id === form.value.id) onSignDocumentDetached() }
+})
 
 const load = async () => {
   loading.value = true
```

### H08 @@ -254,8 +355,11 @@

裁决：保留测试修复及新增操作。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -254,8 +355,11 @@
       attachmentUrl: row?.attachmentUrl ?? ''
   }
   signSlot.value = undefined
+  ++slotSequence; slotLoading.value = false; slotError.value = ''
+  deliveryError.value = ''
   slotEpoch.value += 1
   formVisible.value = true
+  if (form.value.id) void loadSignDocument(form.value.id)
 }
 const save = async () => {
   if (readOnly.value) return
```

## 084 views/pms/engineering/attachment/AdditionalNativeAttachments.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**重叠实现修复**。交底书部分替代原附件控件，恢复选择后保存；旧验收附加附件属于新增能力保留。保留归集状态、历史链接和旧响应隔离。
- 证据及验证边界：AdditionalNativeAttachments.runtime.spec.ts。

### H01 @@ -0,0 +1,117 @@

裁决：重叠实现修复。当前行来源：9b7b1b54b238cad218b58f315243f954c3110392, LOCAL_UNCOMMITTED。

```diff
@@ -0,0 +1,117 @@
+<template>
+  <div class="native-attachments">
+    <p v-if="kind !== 'briefing'">附件统一保存到交付材料，单文件上限 5MiB。</p>
+    <div v-if="legacyLinks.length">
+      <p>历史附件（只读）：</p>
+      <ul><li v-for="link in legacyLinks" :key="link"><el-link :href="link" target="_blank" rel="noopener noreferrer" type="primary">{{ link }}</el-link></li></ul>
+    </div>
+    <p v-if="!entityId && kind !== 'briefing'">请先保存业务记录，再打开记录上传附件。</p>
+    <el-upload v-if="kind === 'briefing' && !readonly" multiple :auto-upload="false" :file-list="pendingFiles"
+      :accept="config.accept" :limit="5" :disabled="draftBusy" :on-change="selectPending" :before-remove="beforeRemovePending" :on-remove="removePending" v-hasPermi="['pms:file:upload']">
+      <el-button type="primary" :disabled="draftBusy"><Icon icon="ep:upload-filled" />选取文件</el-button>
+    </el-upload>
+    <el-alert v-if="draftError" :title="draftError" type="error" :closable="false" />
+    <template v-if="entityId">
+      <DeliveryPanel v-if="kind !== 'briefing'" :key="`${kind}-${entityId}-${deliveryEpoch}`" :owner-module="config.module" :entity-type="config.type" :entity-id="entityId" :readonly="true" />
+      <div v-for="material in materials" :key="material.id">
+        <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey"
+          :artifact-id="material.fileArtifactId" :version-no="material.fileVersionNo" :editable="!readonly && material.status === 'ACTIVE'" @detached="refresh" />
+        <el-button v-if="!readonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
+        <span v-else-if="material.status === 'WITHDRAWN'">已撤回</span>
+      </div>
+      <PmsFileUploader v-if="kind !== 'briefing' && !readonly && !error && !pending && !pendingFiles.length" :key="referenceKey"
+        :owner-context="config.module" :object-type="config.type" :object-id="String(entityId)"
+        :purpose-code="config.purpose" :reference-key="referenceKey" :category-code="config.purpose"
+        :accept="config.accept" :max-size-mi-b="5" @completed="completed" />
+      <el-alert v-if="error" :title="error" type="error" :closable="false">
+        <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
+      </el-alert>
+    </template>
+  </div>
+</template>
+<script setup lang="ts">
+import { computed, ref, watch } from 'vue'
+import { useNativeAttachmentDraft } from './useNativeAttachmentDraft'
+import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
+import { PmsFileUploader, PmsFileReferenceList } from '@/components/PmsFileArtifact'
+import { generateUUID } from '@/utils'
+import { isOpenableUrl } from '@/utils/url'
+import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
+import * as Briefing from '@/api/pms/engineering/briefing'
+import * as Acceptance from '@/api/pms/acceptance/acceptance'
+type Kind = 'briefing' | 'legacyAcceptance'
+const props = defineProps<{ kind: Kind; entityId?: number | string; readonly: boolean; legacy?: string }>()
+const emit = defineEmits<{ changed: [owner: { version?: number; status?: number }] }>()
+const office = '.doc,.xls,.ppt,.txt,.pdf'
+const adapters = {
+ briefing: { module: 'SOL', type: 'briefing', purpose: 'BRIEFING_ATTACHMENT', states: [0], accept: office, get: Briefing.getBriefing, update: Briefing.updateBriefing },
+ legacyAcceptance: { module: 'ACC', type: 'acceptance', purpose: 'LEGACY_ACCEPTANCE_ATTACHMENT', states: [0], accept: office, get: Acceptance.getAcceptance, update: Acceptance.updateAcceptance }
+}
+const config = computed(() => adapters[props.kind])
+const legacyLinks = computed(() => (props.legacy || '').split(',').map(value => value.trim()).filter(isOpenableUrl))
+const deliveryEpoch = ref(0)
+const materials = ref<DeliveryMaterialVO[]>([])
+const referenceKey = ref(generateUUID())
+const error = ref('')
+const busy = ref(false)
+const pending = ref(false)
+let sequence = 0
+const context = () => ({ id: props.entityId, kind: props.kind, sequence })
+const current = (seen: ReturnType<typeof context>) => seen.id === props.entityId && seen.kind === props.kind && seen.sequence === sequence
+const refresh = async () => {
+  if (!props.entityId) return
+  const seen = context(), owner = config.value
+  try {
+    const rows = await listMaterials(owner.module, owner.type, seen.id as number, `${owner.module}.${owner.purpose}`)
+    if (!current(seen)) return
+    materials.value = rows.filter(row => row.materialKind === 'FILE' && row.fileBusinessKey?.ownerContext === owner.module
+      && row.fileBusinessKey.objectType === owner.type && String(row.fileBusinessKey.objectId) === String(seen.id)
+      && row.fileBusinessKey.purposeCode === owner.purpose)
+    error.value = ''; deliveryEpoch.value++
+  } catch (failure: any) { if (current(seen)) error.value = failure?.message || '材料读取失败' }
+}
+const collect = async () => {
+  if (props.readonly || !props.entityId || busy.value) return false
+  const seen = context(), adapter = config.value
+  busy.value = true; error.value = ''
+  pending.value = true
+  try {
+    const owner = await adapter.get(seen.id as number)
+    if (!current(seen) || props.readonly) return false
+    if (!owner || String(owner.id) !== String(seen.id) || !adapter.states.includes(owner.status)) throw new Error('记录已不可编辑，请重新读取')
+    // Persist the saved Owner, keeping unsubmitted form edits untouched.
+    const request = { ...owner }
+    await adapter.update(request)
+    const saved = await adapter.get(seen.id as number)
+    if (!current(seen) || props.readonly) return false
+    pending.value = false; referenceKey.value = generateUUID(); emit('changed', saved)
+    await refresh()
+    return true
+  } catch (failure: any) { if (current(seen)) error.value = failure?.message || '附件已上传，归集失败'; return false }
+  finally { if (current(seen)) busy.value = false }
+}
+const completed = computed(() => {
+  const seen = context(), slot = referenceKey.value
+  return async () => {
+    if (!current(seen) || slot !== referenceKey.value) return
+    pending.value = true
+    await collect()
+  }
+})
+const withdraw = async (id: number) => {
+  if (props.readonly || busy.value) return
+  const seen = context()
+  busy.value = true; error.value = ''
+  try { await withdrawMaterial(id); if (current(seen)) await refresh() }
+  catch (failure: any) { if (current(seen)) error.value = failure?.message || '材料撤回失败' }
+  finally { if (current(seen)) busy.value = false }
+}
+const {pendingFiles,draftBusy,draftError,selectPending,beforeRemovePending,removePending,resetPending,savePending}=useNativeAttachmentDraft({context:()=>config.value,readonly:()=>props.readonly,collect})
+defineExpose({savePending})
+watch(() => [props.kind, props.entityId], (_next, previous) => {
+ if(previous && (previous[0]!==props.kind || previous[1] && String(previous[1])!==String(props.entityId)))resetPending()
+ sequence++; pending.value = false; busy.value = false; error.value = ''; materials.value = []; referenceKey.value = generateUUID(); void refresh() }, { immediate: true })
+</script>
+<style scoped>
+.native-attachments { width: 100%; min-width: 0; overflow-x: auto; }
+</style>
```

## 085 views/pms/engineering/attachment/NativeAttachments.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**重叠实现修复**。该新增文件替代原配置/联调/外采/外包/领料UploadFile，不属于可无条件保留的纯新增。恢复原位置选取文件后随表单保存，不嵌入额外交付表；换货独立新增附件保留。保留原生存储、只读历史、归集失败和旧响应隔离。
- 证据及验证边界：NativeAttachments.runtime.spec.ts; nativeAttachmentDraft.runtime.spec.ts; nativeApplicationSave.runtime.spec.ts。

### H01 @@ -0,0 +1,128 @@

裁决：重叠实现修复。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -0,0 +1,128 @@
+<template>
+  <div class="native-attachments">
+    <p v-if="!deferredKinds.includes(kind)">附件统一保存到交付材料，单文件上限 5MiB。</p>
+    <div v-if="legacyLinks.length">
+      <p>历史附件（只读）：</p>
+      <ul><li v-for="link in legacyLinks" :key="link"><el-link :href="link" target="_blank" rel="noopener noreferrer" type="primary">{{ link }}</el-link></li></ul>
+    </div>
+    <p v-if="!entityId && !deferredKinds.includes(kind)">请先保存业务记录，再打开记录上传附件。</p>
+    <el-upload v-if="deferredKinds.includes(kind) && !readonly" multiple :auto-upload="false" :file-list="pendingFiles"
+      :accept="config.accept" :limit="5" :disabled="draftBusy" :on-change="selectPending" :before-remove="beforeRemovePending" :on-remove="removePending" v-hasPermi="['pms:file:upload']">
+      <el-button type="primary" :disabled="draftBusy"><Icon icon="ep:upload-filled" />选取文件</el-button>
+    </el-upload>
+    <el-alert v-if="draftError" :title="draftError" type="error" :closable="false" />
+    <template v-if="entityId">
+      <DeliveryPanel v-if="!deferredKinds.includes(kind)" :key="`${kind}-${entityId}-${deliveryEpoch}`" :owner-module="config.module" :entity-type="config.type" :entity-id="entityId" :readonly="true" />
+      <div v-for="material in materials" :key="material.id">
+        <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey"
+          :artifact-id="material.fileArtifactId" :version-no="material.fileVersionNo" :editable="!readonly && material.status === 'ACTIVE'" @detached="refresh" />
+        <el-button v-if="!readonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
+        <span v-else-if="material.status === 'WITHDRAWN'">已撤回</span>
+      </div>
+      <PmsFileUploader v-if="!deferredKinds.includes(kind) && !readonly && !error && !pending && !pendingFiles.length" :key="referenceKey"
+        :owner-context="config.module" :object-type="config.type" :object-id="String(entityId)"
+        :purpose-code="config.purpose" :reference-key="referenceKey" :category-code="config.purpose"
+        :accept="config.accept" :max-size-mi-b="5" @completed="completed" />
+      <el-alert v-if="error" :title="error" type="error" :closable="false">
+        <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
+      </el-alert>
+    </template>
+  </div>
+</template>
+<script setup lang="ts">
+import { computed, ref, watch } from 'vue'
+import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
+import { PmsFileUploader, PmsFileReferenceList } from '@/components/PmsFileArtifact'
+import { generateUUID } from '@/utils'
+import { isOpenableUrl } from '@/utils/url'
+import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
+import * as Configuration from '@/api/pms/engineering/configuration'
+import * as JointTest from '@/api/pms/engineering/joint-test'
+import * as ExternalProcurement from '@/api/pms/engineering/ext-proc'
+import * as Outsource from '@/api/pms/engineering/outsource'
+import * as MaterialRequisition from '@/api/pms/engineering/material-req'
+import * as MaterialExchange from '@/api/pms/engineering/material-exch'
+import { useNativeAttachmentDraft } from './useNativeAttachmentDraft'
+type Kind = 'configuration' | 'jointTest' | 'externalProcurement' | 'outsourceRequest' | 'materialRequisition' | 'materialExchange'
+const deferredKinds: Kind[] = ['configuration', 'jointTest', 'externalProcurement', 'outsourceRequest', 'materialRequisition']
+const props = defineProps<{ kind: Kind; entityId?: number | string; readonly: boolean; legacy?: string }>()
+const emit = defineEmits<{ changed: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
+const office = '.doc,.xls,.ppt,.txt,.pdf'
+const adapters = {
+  configuration: { module: 'IMP', type: 'configuration', purpose: 'CONFIGURATION_LOG', states: [0, 1, 3], accept: '.log,.cfg,.conf,' + office, get: Configuration.getConfiguration, update: Configuration.updateConfiguration },
+  jointTest: { module: 'IMP', type: 'jointTest', purpose: 'JOINT_TEST_EVIDENCE', states: [0, 1], accept: '.log,.cfg,.conf,' + office, get: JointTest.getJointTest, update: JointTest.updateJointTest },
+  externalProcurement: { module: 'IMP', type: 'externalProcurement', purpose: 'EXTERNAL_PROCUREMENT_ATTACHMENT', states: [0, 4], accept: office, get: ExternalProcurement.getExternalProcurement, update: ExternalProcurement.updateExternalProcurement },
+  outsourceRequest: { module: 'RES', type: 'outsourceRequest', purpose: 'OUTSOURCE_ATTACHMENT', states: [0, 4], accept: office, get: Outsource.getOutsourceRequest, update: Outsource.updateOutsourceRequest },
+  materialRequisition: { module: 'IMP', type: 'materialRequisition', purpose: 'MATERIAL_REQUISITION_ATTACHMENT', states: [0, 4], accept: office, get: MaterialRequisition.getMaterialRequisition, update: MaterialRequisition.updateMaterialRequisition },
+  materialExchange: { module: 'IMP', type: 'materialExchange', purpose: 'MATERIAL_EXCHANGE_REASON', states: [0, 4], accept: office, get: MaterialExchange.getMaterialExchange, update: MaterialExchange.updateMaterialExchange }
+}
+const config = computed(() => adapters[props.kind])
+const legacyLinks = computed(() => (props.legacy || '').split(',').map(value => value.trim()).filter(isOpenableUrl))
+const deliveryEpoch = ref(0)
+const materials = ref<DeliveryMaterialVO[]>([])
+const referenceKey = ref(generateUUID())
+const error = ref('')
+const busy = ref(false)
+const pending = ref(false)
+let sequence = 0
+const context = () => ({ id: props.entityId, kind: props.kind, sequence })
+const current = (seen: ReturnType<typeof context>) => seen.id === props.entityId && seen.kind === props.kind && seen.sequence === sequence
+const refresh = async () => {
+  if (!props.entityId) return
+  const seen = context(), owner = config.value
+  try {
+    const rows = await listMaterials(owner.module, owner.type, seen.id as number, `${owner.module}.${owner.purpose}`)
+    if (!current(seen)) return
+    materials.value = rows.filter(row => row.materialKind === 'FILE' && row.fileBusinessKey?.ownerContext === owner.module
+      && row.fileBusinessKey.objectType === owner.type && String(row.fileBusinessKey.objectId) === String(seen.id)
+      && row.fileBusinessKey.purposeCode === owner.purpose)
+    error.value = ''; deliveryEpoch.value++
+  } catch (failure: any) { if (current(seen)) error.value = failure?.message || '材料读取失败' }
+}
+const collect = async () => {
+  if (props.readonly || !props.entityId || busy.value) return false
+  const seen = context(), adapter = config.value
+  busy.value = true; error.value = ''
+  pending.value = true
+  try {
+    const owner = await adapter.get(seen.id as number)
+    if (!current(seen) || props.readonly) return false
+    if (!owner || String(owner.id) !== String(seen.id) || !adapter.states.includes(owner.status)) throw new Error('记录已不可编辑，请重新读取')
+    // Persist the saved Owner, keeping unsubmitted form edits and exchange serial snapshots untouched.
+    const request = { ...owner }
+    if (seen.kind === 'materialExchange') delete request.serials
+    await adapter.update(request)
+    const saved = await adapter.get(seen.id as number)
+    if (!current(seen) || props.readonly) return false
+    pending.value = false; referenceKey.value = generateUUID(); emit('changed', saved)
+    await refresh()
+    return true
+  } catch (failure: any) { if (current(seen)) error.value = failure?.message || '附件已上传，归集失败'; return false }
+  finally { if (current(seen)) busy.value = false }
+}
+const completed = computed(() => {
+  const seen = context(), slot = referenceKey.value
+  return async () => {
+    if (!current(seen) || slot !== referenceKey.value) return
+    pending.value = true
+    await collect()
+  }
+})
+const withdraw = async (id: number) => {
+  if (props.readonly || busy.value) return
+  const seen = context()
+  busy.value = true; error.value = ''
+  try { await withdrawMaterial(id); if (current(seen)) await refresh() }
+  catch (failure: any) { if (current(seen)) error.value = failure?.message || '材料撤回失败' }
+  finally { if (current(seen)) busy.value = false }
+}
+const {pendingFiles,draftBusy,draftError,selectPending,beforeRemovePending,removePending,resetPending,savePending}=useNativeAttachmentDraft({context:()=>config.value,readonly:()=>props.readonly,collect})
+watch(() => [props.kind, props.entityId], (_next,previous) => {
+ if(previous && (previous[0]!==props.kind || previous[1] && String(previous[1])!==String(props.entityId)))resetPending()
+ sequence++; pending.value = false; busy.value = false; error.value = ''; materials.value = []; referenceKey.value = generateUUID(); void refresh()
+}, { immediate: true })
+defineExpose({savePending})
+</script>
+<style scoped>
+.native-attachments { width: 100%; min-width: 0; overflow-x: auto; }
+</style>
```

## 086 views/pms/engineering/briefing/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：8。
- 裁决：**恢复操作**。交底书恢复未保存记录也能选择附件，一次保存后关闭；保留原生文件归集及Owner状态校验。
- 证据及验证边界：AdditionalNativeAttachments.runtime.spec.ts; 类型检查；新增写入未在历史项目执行。

### H01 @@ -184,7 +184,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -184,7 +184,7 @@
         </el-col>
         <el-col :span="24">
           <el-form-item label="附件" prop="fileUrl">
-            <UploadFile v-model="form.fileUrl!" />
+            <AdditionalNativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="briefing" :entity-id="form.id" :legacy="form.fileUrl" :readonly="form.status !== 0 || !checkPermi([form.id ? 'pms:sol-briefing:update' : 'pms:sol-briefing:create'])" @changed="attachmentOwnerChanged" />
           </el-form-item>
         </el-col>
         <el-col :span="24">
```

### H02 @@ -195,7 +195,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -195,7 +195,7 @@
       </el-row>
     </el-form>
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
+      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
       <el-button type="primary" :loading="saving" @click="save">保存</el-button>
     </template>
   </Dialog>
```

### H03 @@ -292,6 +292,8 @@

裁决：恢复操作。当前行来源：9b7b1b54b238cad218b58f315243f954c3110392。

```diff
@@ -292,6 +292,8 @@
 </template>
 
 <script setup lang="ts">
+import AdditionalNativeAttachments from '@/views/pms/engineering/attachment/AdditionalNativeAttachments.vue'
+import { checkPermi } from '@/utils/permission'
 import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
 import { getGeneratedBriefingFileTicket } from '@/api/pms/engineering/briefing'
 const downloadBriefingFile = async (url: string) => {
```

### H04 @@ -300,7 +302,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -300,7 +302,7 @@
   window.open(ticket, '_blank', 'noopener')
 }
 
-import { onMounted, reactive, ref } from 'vue'
+import { nextTick, onMounted, reactive, ref } from 'vue'
 import { dateFormatter } from '@/utils/formatTime'
 import { useMessage } from '@/hooks/web/useMessage'
 import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
```

### H05 @@ -339,6 +341,8 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -339,6 +341,8 @@
 
 // 新建/编辑
 const formVisible = ref(false)
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof AdditionalNativeAttachments>>()
 const formRef = ref()
 const form = reactive<BriefingVO>({
   projectId: undefined!,
```

### H06 @@ -356,6 +360,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -356,6 +360,7 @@
 }
 
 const openCreate = () => {
+  attachmentEpoch.value++
   Object.assign(form, {
     id: undefined,
     projectId: undefined,
```

### H07 @@ -363,6 +368,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -363,6 +368,7 @@
     briefingType: 'STANDARD',
     content: '',
     fileUrl: '',
+    status: 0,
     creatorUserId: undefined,
     remark: '',
     version: 0
```

### H08 @@ -370,21 +376,26 @@

裁决：恢复操作。当前行来源：9b7b1b54b238cad218b58f315243f954c3110392, LOCAL_UNCOMMITTED。

```diff
@@ -370,21 +376,26 @@
   formVisible.value = true
 }
 const openEdit = async (row: BriefingVO) => {
+  attachmentEpoch.value++
   const detail = await BriefingApi.getBriefing(row.id!)
   Object.assign(form, detail)
   formVisible.value = true
 }
+const attachmentOwnerChanged = (owner: { version?: number; status?: number }) => { form.version = owner.version; form.status = owner.status }
 const save = async () => {
+  if (saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
     if (form.id) {
       await BriefingApi.updateBriefing(form)
-      message.success('更新成功')
     } else {
-      await BriefingApi.createBriefing(form)
-      message.success('创建成功')
+      form.id = await BriefingApi.createBriefing(form)
     }
+    Object.assign(form, await BriefingApi.getBriefing(form.id!))
+    await nextTick()
+    if (!attachmentsRef.value || !(await attachmentsRef.value.savePending(form.id!))) return
+    message.success('保存成功')
     formVisible.value = false
     await load()
   } finally {
```

## 087 views/pms/engineering/configuration/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：5。
- 裁决：**恢复操作**。恢复选择附件→保存→关闭；原生附件绑定替换旧URL写入属于存储兼容，不能恢复服务端已拒绝的URL写入。记录ID在首次创建后保留，失败重试不重复创建。
- 证据及验证边界：configurationWorkspace.runtime.spec.ts; nativeAttachmentDraft.runtime.spec.ts。

### H01 @@ -118,7 +118,8 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -118,7 +118,8 @@
           </el-form-item>
         </el-col>
         <el-col :span="24">
-          <el-form-item label="配置日志" prop="configLogUrl"><UploadFile v-model="form.configLogUrl!" :disabled="readOnly" :file-type="['log', 'txt', 'cfg', 'conf', 'doc', 'xls', 'ppt', 'pdf']" /></el-form-item>
+          <el-form-item label="配置日志" prop="configLogUrl"><NativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="configuration" :entity-id="form.id"
+              :readonly="readOnly" :legacy="form.configLogUrl" @changed="attachmentOwnerChanged" /></el-form-item>
         </el-col>
         <el-col :span="24">
           <el-form-item label="调试结果" prop="debugResult">
```

### H02 @@ -134,14 +135,15 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -134,14 +135,15 @@
     </el-form>
     <BusinessCollectionLogs v-if="formVisible && form.id" entry="configuration" :object-id="form.id" />
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
+      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
       <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
     </template>
   </Dialog>
 </template>
 
 <script setup lang="ts">
-import { computed, onMounted, reactive, ref } from 'vue'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
+import { computed, nextTick, onMounted, reactive, ref } from 'vue'
 import ManualCollectionDialog from './ManualCollectionDialog.vue'
 import BusinessCollectionLogs from '@/components/DeviceCollection/BusinessCollectionLogs.vue'
 const collectionRef = ref<InstanceType<typeof ManualCollectionDialog>>()
```

### H03 @@ -165,6 +167,8 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -165,6 +167,8 @@
 const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId ?? '', status: undefined })
 const formVisible = ref(false)
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
 type ConfigurationForm = Omit<ConfigurationVO, 'debugTime'> & { debugTime?: string | number | null }
 const form = ref<ConfigurationForm>({ projectId: 0, status: 0 })
 const editableRecord = (row: Pick<ConfigurationVO, 'status'>) => [0, 1, 3].includes(row.status ?? -1) && checkPermi(['pms:imp-configuration:update'])
```

### H04 @@ -188,6 +192,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -188,6 +192,7 @@
   }
 }
 const openForm = (row?: ConfigurationVO) => {
+  attachmentEpoch.value++
   form.value = {
       id: undefined,
       projectId: props.projectId ?? 0,
```

### H05 @@ -199,18 +204,27 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -199,18 +204,27 @@
       version: undefined,
       status: 0,
       ...row,
-      // Keep UploadFile on the existing string API contract for NULL legacy logs.
+      // Keep empty legacy metadata when reopening a record without a log.
       configLogUrl: row?.configLogUrl ?? ''
   }
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.value.version = saved.version
+  form.value.status = saved.status
+  form.value.configLogUrl = saved.configLogUrl ?? ''
+}
 const save = async () => {
-  if (readOnly.value) return
+  if (readOnly.value || saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
     const data: ConfigurationVO = { ...form.value, debugTime: form.value.debugTime == null || form.value.debugTime === '' ? undefined : Number(form.value.debugTime) }
-    data.id ? await ConfigurationApi.updateConfiguration(data) : await ConfigurationApi.createConfiguration(data)
+    if (data.id) await ConfigurationApi.updateConfiguration(data)
+    else form.value.id = await ConfigurationApi.createConfiguration(data)
+    Object.assign(form.value, await ConfigurationApi.getConfiguration(form.value.id!))
+    await nextTick()
+    if (!attachmentsRef.value || !(await attachmentsRef.value.savePending(form.value.id!))) return
     message.success('保存成功')
     formVisible.value = false
     await load()
```

## 088 views/pms/engineering/duration-change/ApprovalDetail.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留文件兼容**。仅处理失效文件引用，不恢复已失效附件访问。
- 证据及验证边界：工期审批与文件既有测试。

### H01 @@ -27,7 +27,7 @@

裁决：保留文件兼容。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -27,7 +27,7 @@
       </dl>
       <h4>变更原因</h4>
       <p class="duration-reason">{{ change.reasonDetail || '无' }}</p>
-      <template v-if="change.customerEvidenceFileId">
+      <template v-if="change.customerEvidenceFileId && change.customerEvidenceReferenceKey">
         <h4>变更依据附件</h4>
         <PmsFileReferenceList
           owner-context="SOL"
```

### H02 @@ -40,6 +40,7 @@

裁决：保留文件兼容。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -40,6 +40,7 @@
           :editable="false"
         />
       </template>
+      <el-alert v-else-if="change.customerEvidenceFileId" title="该历史附件缺少业务引用信息，暂无法查看；原文件记录保留。" type="warning" :closable="false" />
       <template v-if="change.approvalOpinion">
         <h4>审批意见</h4>
         <p class="duration-reason">{{ change.approvalOpinion }}</p>
```

## 089 views/pms/engineering/ext-proc/ExternalProcurementDetailDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增附件功能**。原外采表单/明细增加原生材料区，原业务字段和状态操作不替换。
- 证据及验证边界：NativeAttachments.runtime.spec.ts及原生材料Owner。

### H01 @@ -38,9 +38,11 @@

裁决：保留纯新增附件功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -38,9 +38,11 @@
         {{ current.approveOpinion }}
       </el-descriptions-item>
     </el-descriptions>
+    <NativeAttachments v-if="current.id" kind="externalProcurement" :entity-id="current.id" :readonly="true" :legacy="current.attachmentFiles" />
   </Dialog>
 </template>
 <script setup lang="ts">
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { computed } from 'vue'
 import type { ExternalProcurementVO } from '@/api/pms/engineering/ext-proc'
 import { DICT_TYPE } from '@/utils/dict'
```

## 090 views/pms/engineering/ext-proc/ExternalProcurementFormDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：3。
- 裁决：**修复重叠控件**。移除重复的第二附件栏；在原附件位置使用原生文件，恢复新建时选取文件随表单保存，保留原校验和权限。旧UploadFile写URL已被Owner拒绝，不能与新控件并存。
- 证据及验证边界：nativeApplicationSave.runtime.spec.ts; NativeAttachmentRegistration.requireLegacyUnchanged。

### H01 @@ -148,7 +148,8 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -148,7 +148,8 @@
         </el-col>
         <el-col :span="24">
           <el-form-item label="附件" prop="attachmentFiles">
-            <UploadFile v-model="form.attachmentFiles!" />
+            <NativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="externalProcurement" :entity-id="form.id"
+              :readonly="form.id ? ![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:imp-ext-proc:update']) : !checkPermi(['pms:imp-ext-proc:create'])" :legacy="form.attachmentFiles" @changed="emit('attachmentChanged', $event)" />
           </el-form-item>
         </el-col>
         <el-col :span="24">
```

### H02 @@ -177,15 +178,20 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED, d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -177,15 +178,20 @@
   </Dialog>
 </template>
 <script setup lang="ts">
-import { computed, ref } from 'vue'
+import { computed, ref, watch } from 'vue'
+import { checkPermi } from '@/utils/permission'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
 import * as ProjectApi from '@/api/pms/project/projects'
 import * as UserApi from '@/api/system/user'
 import type { ExternalProcurementVO } from '@/api/pms/engineering/ext-proc'
 const props = defineProps<{ modelValue: boolean; form: ExternalProcurementVO; sourceSurveyId?: number | string; saving: boolean; error?: string }>()
-const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [] }>()
+const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: []; attachmentChanged: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
 const visible = computed({ get: () => props.modelValue, set: value => { if (!props.saving) emit('update:modelValue', value) } })
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
+watch(() => props.modelValue, visible => { if (visible) attachmentEpoch.value++ })
 const rules = {
   projectId: [{ required: true, message: '请选择项目' }],
   code: [{ required: true, message: '请输入单号' }],
```

### H03 @@ -196,5 +202,6 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -196,5 +202,6 @@
   applicantUserId: [{ required: true, message: '请选择申请人' }],
   applyTime: [{ required: true, message: '请选择申请时间' }]
 }
-defineExpose({ validate: () => formRef.value.validate() })
+defineExpose({ validate: () => formRef.value.validate(),
+  savePending: (id: number) => attachmentsRef.value?.savePending(id) ?? false })
 </script>
```

## 091 views/pms/engineering/ext-proc/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：3。
- 裁决：**修复附件保存依赖**。创建后保留Owner ID，按真实版本上传/归集所选附件，再关闭并按原工勘入口返回；失败保留表单、ID和上下文，避免重复创建。
- 证据及验证边界：nativeApplicationSave.runtime.spec.ts。

### H01 @@ -150,7 +150,7 @@

裁决：修复附件保存依赖。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -150,7 +150,7 @@
 
   <!-- 新建/编辑对话框 -->
   <ExternalProcurementFormDialog ref="formRef" v-model="formVisible" :form="form"
-    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" />
+    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" @attachment-changed="attachmentOwnerChanged" />
 
   <!-- 明细对话框 -->
   <ExternalProcurementDetailDialog v-model="detailVisible" :current="current" />
```

### H02 @@ -191,7 +191,7 @@

裁决：修复附件保存依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -191,7 +191,7 @@
 <script setup lang="ts">
 import ExternalProcurementFormDialog from './ExternalProcurementFormDialog.vue'
 import ExternalProcurementDetailDialog from './ExternalProcurementDetailDialog.vue'
-import { onMounted, reactive, ref } from 'vue'
+import { nextTick, onMounted, reactive, ref } from 'vue'
 import { useRoute, useRouter } from 'vue-router'
 import { useUserStore } from '@/store/modules/user'
 import { positiveShortcutId, surveyPath } from '@/views/pms/delivery-business/site-survey/siteSurveyOutsource'
```

### H03 @@ -305,18 +305,25 @@

裁决：修复附件保存依赖。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -305,18 +305,25 @@
   Object.assign(form, detail)
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.version = saved.version
+  form.status = saved.status
+}
 const save = async () => {
   if (saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
+    const creating = !form.id
     if (form.id) {
       await ExtProcApi.updateExternalProcurement(form)
-      message.success('更新成功')
     } else {
-      await ExtProcApi.createExternalProcurement(form)
-      message.success('创建成功')
+      form.id = await ExtProcApi.createExternalProcurement(form)
     }
+    Object.assign(form, await ExtProcApi.getExternalProcurement(form.id!))
+    await nextTick()
+    if (!(await formRef.value.savePending(form.id!))) return
+    message.success(creating ? '创建成功' : '更新成功')
     formVisible.value = false
     if (sourceSurveyId.value) await router.push({ path: surveyPath, query: { surveyId: String(sourceSurveyId.value) } })
     else await load()
```

## 092 views/pms/engineering/joint-test/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：6。
- 裁决：**恢复操作**。恢复选择附件→保存→关闭，附件失败留在原窗口；保留原生文件和并发版本校验。
- 证据及验证边界：jointTestWorkspace.runtime.spec.ts; nativeAttachmentDraft.runtime.spec.ts。

### H01 @@ -185,7 +185,8 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -185,7 +185,8 @@
           </el-form-item>
         </el-col>
         <el-col :span="24">
-          <el-form-item label="证据附件" prop="evidenceUrl"><UploadFile v-model="form.evidenceUrl!" :disabled="readOnly" :file-type="['log', 'txt', 'cfg', 'conf', 'doc', 'xls', 'ppt', 'pdf']" /></el-form-item>
+          <el-form-item label="证据附件" prop="evidenceUrl"><NativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="jointTest" :entity-id="form.id"
+              :readonly="readOnly" :legacy="form.evidenceUrl" @changed="attachmentOwnerChanged" /></el-form-item>
         </el-col>
         <el-col v-if="form.exceptionRecord" :span="24">
           <el-form-item label="异常记录"><el-input :model-value="form.exceptionRecord" type="textarea" :rows="4" readonly data-testid="joint-test-exception" /></el-form-item>
```

### H02 @@ -199,7 +200,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -199,7 +200,7 @@
     </el-form>
     <BusinessCollectionLogs v-if="formVisible && form.id" ref="businessLogs" entry="joint-test" :object-id="form.id" />
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
+      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
       <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
     </template>
   </Dialog>
```

### H03 @@ -219,7 +220,8 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -219,7 +220,8 @@
 </template>
 
 <script setup lang="ts">
-import { computed, onMounted, reactive, ref, watch } from 'vue'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
+import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
 import { useMessage } from '@/hooks/web/useMessage'
 import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
 import * as JointTestApi from '@/api/pms/engineering/joint-test'
```

### H04 @@ -243,6 +245,8 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -243,6 +245,8 @@
 const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId ?? '', status: undefined })
 const formVisible = ref(false)
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
 type JointTestForm = Omit<JointTestVO, 'testTime'> & { testTime?: number | string | null }
 const form = ref<JointTestForm>({ projectId: props.projectId ?? 0, testCase: '', status: 0 })
```

### H05 @@ -316,6 +320,7 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -316,6 +320,7 @@
   }
 }
 const openForm = (row?: JointTestVO) => {
+  attachmentEpoch.value++
   form.value = {
       id: undefined,
       projectId: props.projectId ?? 0,
```

### H06 @@ -330,21 +335,29 @@

裁决：恢复操作。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -330,21 +335,29 @@
       version: undefined,
       status: 0,
       ...row,
-      // Keep UploadFile on the existing string API contract for NULL legacy evidence.
+      // Keep empty legacy metadata when reopening a record without evidence.
       evidenceUrl: row?.evidenceUrl ?? ''
   }
   syncMetaFromForm()
   loadDeviceInfo()
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.value.version = saved.version
+  form.value.status = saved.status
+}
 const save = async () => {
-  if (readOnly.value) return
+  if (readOnly.value || saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
     writeMetaToForm()
     const data: JointTestVO = { ...form.value, testTime: form.value.testTime == null || form.value.testTime === '' ? undefined : Number(form.value.testTime) }
-    data.id ? await JointTestApi.updateJointTest(data) : await JointTestApi.createJointTest(data)
+    if (data.id) await JointTestApi.updateJointTest(data)
+    else form.value.id = await JointTestApi.createJointTest(data)
+    Object.assign(form.value, await JointTestApi.getJointTest(form.value.id!))
+    await nextTick()
+    if (!attachmentsRef.value || !(await attachmentsRef.value.savePending(form.value.id!))) return
     message.success('保存成功')
     formVisible.value = false
     await load()
```

## 093 views/pms/engineering/material-exch/MaterialExchangeDetailDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留纯新增功能**。原换货操作保留，新增备注和原生附加附件；归集不重写序列号快照。
- 证据及验证边界：NativeAttachments.runtime.spec.ts。

### H01 @@ -33,7 +33,10 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -33,7 +33,10 @@
       <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
         {{ current.approveOpinion }}
       </el-descriptions-item>
+
+
     </el-descriptions>
+    <NativeAttachments v-if="current.id" kind="materialExchange" :entity-id="current.id" :readonly="true" :legacy="current.reasonFiles" />
     <el-table v-if="current.serials?.length" :data="current.serials" border max-height="360" class="mt-4">
       <el-table-column prop="orderNo" label="订单号" min-width="140" />
       <el-table-column prop="lineNo" label="行号" width="80" />
```

### H02 @@ -51,6 +54,7 @@

裁决：保留纯新增功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -51,6 +54,7 @@
   </Dialog>
 </template>
 <script setup lang="ts">
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { computed } from 'vue'
 import type { MaterialExchangeVO } from '@/api/pms/engineering/material-exch'
 import { DICT_TYPE } from '@/utils/dict'
```

## 094 views/pms/engineering/material-exch/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留纯新增功能**。原换货操作保留，新增备注和原生附加附件；归集不重写序列号快照。
- 证据及验证边界：NativeAttachments.runtime.spec.ts。

### H01 @@ -210,6 +210,17 @@

裁决：保留纯新增功能。当前行来源：1a93fad14aa0cadcf9300535aa7b9b6617b9c3aa, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -210,6 +210,17 @@
             <Editor v-model="form.reason" height="180px" />
           </el-form-item>
         </el-col>
+        <el-col :span="24">
+          <el-form-item label="原因附件" prop="reasonFiles">
+            <NativeAttachments :key="String(form.id || 'new')" kind="materialExchange" :entity-id="form.id"
+              :readonly="![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:imp-material-exch:update'])" :legacy="form.reasonFiles" @changed="attachmentOwnerChanged" />
+          </el-form-item>
+        </el-col>
+        <el-col :span="24">
+          <el-form-item label="备注" prop="remark">
+            <el-input v-model="form.remark" type="textarea" :rows="2" />
+          </el-form-item>
+        </el-col>
       </el-row>
     </el-form>
     <template #footer>
```

### H02 @@ -255,6 +266,8 @@

裁决：保留纯新增功能。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -255,6 +266,8 @@
 </template>
 
 <script setup lang="ts">
+import { checkPermi } from '@/utils/permission'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import MaterialExchangeDetailDialog from './MaterialExchangeDetailDialog.vue'
 import { onMounted, reactive, ref, watch } from 'vue'
 import { useRoute, useRouter } from 'vue-router'
```

### H03 @@ -406,6 +419,10 @@

裁决：保留纯新增功能。当前行来源：be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -406,6 +419,10 @@
   pickerKey.value++
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.version = saved.version
+  form.status = saved.status
+}
 const save = async () => {
   if (saving.value) return
   // 单号自动生成：编号只读展示，创建时无项目前缀也按规则生成
```

## 095 views/pms/engineering/material-req/MaterialRequisitionDetailDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增附件功能**。原领料表单/明细增加原生材料区，仍按原状态和权限维护。
- 证据及验证边界：NativeAttachments.runtime.spec.ts。

### H01 @@ -28,9 +28,11 @@

裁决：保留纯新增附件功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -28,9 +28,11 @@
         {{ current.approveOpinion }}
       </el-descriptions-item>
     </el-descriptions>
+    <NativeAttachments v-if="current.id" kind="materialRequisition" :entity-id="current.id" :readonly="true" :legacy="current.attachmentFiles" />
   </Dialog>
 </template>
 <script setup lang="ts">
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { computed } from 'vue'
 import type { MaterialRequisitionVO } from '@/api/pms/engineering/material-req'
 import { DICT_TYPE } from '@/utils/dict'
```

## 096 views/pms/engineering/material-req/MaterialRequisitionFormDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：3。
- 裁决：**修复重叠控件**。合并重复附件栏，恢复原位置选取文件随申请保存；保留原必填校验、工勘上下文、状态和权限。
- 证据及验证边界：nativeApplicationSave.runtime.spec.ts; NativeAttachmentRegistration.requireLegacyUnchanged。

### H01 @@ -116,7 +116,8 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -116,7 +116,8 @@
         </el-col>
         <el-col :span="24">
           <el-form-item label="附件" prop="attachmentFiles">
-            <UploadFile v-model="form.attachmentFiles!" />
+            <NativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="materialRequisition" :entity-id="form.id"
+              :readonly="form.id ? ![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:imp-material-req:update']) : !checkPermi(['pms:imp-material-req:create'])" :legacy="form.attachmentFiles" @changed="emit('attachmentChanged', $event)" />
           </el-form-item>
         </el-col>
         <el-col :span="24">
```

### H02 @@ -145,16 +146,21 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED, d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -145,16 +146,21 @@
   </Dialog>
 </template>
 <script setup lang="ts">
-import { computed, ref } from 'vue'
+import { computed, ref, watch } from 'vue'
+import { checkPermi } from '@/utils/permission'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
 import * as ProjectApi from '@/api/pms/project/projects'
 import * as UserApi from '@/api/system/user'
 import type { MaterialRequisitionVO } from '@/api/pms/engineering/material-req'
 import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
 const props = defineProps<{ modelValue: boolean; form: MaterialRequisitionVO; sourceSurveyId?: number | string; saving: boolean; error?: string }>()
-const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [] }>()
+const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: []; attachmentChanged: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
 const visible = computed({ get: () => props.modelValue, set: value => { if (!props.saving) emit('update:modelValue', value) } })
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
+watch(() => props.modelValue, visible => { if (visible) attachmentEpoch.value++ })
 const rules = {
   projectId: [{ required: true, message: '请选择项目' }],
   code: [{ required: true, message: '请输入单号' }],
```

### H03 @@ -165,5 +171,6 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -165,5 +171,6 @@
   applicantUserId: [{ required: true, message: '请选择申请人' }],
   applyTime: [{ required: true, message: '请选择申请时间' }]
 }
-defineExpose({ validate: () => formRef.value.validate() })
+defineExpose({ validate: () => formRef.value.validate(),
+  savePending: (id: number) => attachmentsRef.value?.savePending(id) ?? false })
 </script>
```

## 097 views/pms/engineering/material-req/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：3。
- 裁决：**修复附件保存依赖**。保留创建ID、并发版本和工勘返回，附件上传及归集成功后关闭；失败重试不重复创建。
- 证据及验证边界：nativeApplicationSave.runtime.spec.ts。

### H01 @@ -151,7 +151,7 @@

裁决：修复附件保存依赖。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -151,7 +151,7 @@
 
   <!-- 新建/编辑对话框 -->
   <MaterialRequisitionFormDialog ref="formRef" v-model="formVisible" :form="form"
-    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" />
+    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" @attachment-changed="attachmentOwnerChanged" />
 
   <!-- 明细对话框 -->
   <MaterialRequisitionDetailDialog v-model="detailVisible" :current="current" />
```

### H02 @@ -192,7 +192,7 @@

裁决：修复附件保存依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -192,7 +192,7 @@
 <script setup lang="ts">
 import MaterialRequisitionFormDialog from './MaterialRequisitionFormDialog.vue'
 import MaterialRequisitionDetailDialog from './MaterialRequisitionDetailDialog.vue'
-import { onMounted, reactive, ref } from 'vue'
+import { nextTick, onMounted, reactive, ref } from 'vue'
 import { useRoute, useRouter } from 'vue-router'
 import { useUserStore } from '@/store/modules/user'
 import { positiveShortcutId, surveyPath } from '@/views/pms/delivery-business/site-survey/siteSurveyOutsource'
```

### H03 @@ -296,18 +296,25 @@

裁决：修复附件保存依赖。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -296,18 +296,25 @@
   Object.assign(form, detail)
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.version = saved.version
+  form.status = saved.status
+}
 const save = async () => {
   if (saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
+    const creating = !form.id
     if (form.id) {
       await MaterialReqApi.updateMaterialRequisition(form)
-      message.success('更新成功')
     } else {
-      await MaterialReqApi.createMaterialRequisition(form)
-      message.success('创建成功')
+      form.id = await MaterialReqApi.createMaterialRequisition(form)
     }
+    Object.assign(form, await MaterialReqApi.getMaterialRequisition(form.id!))
+    await nextTick()
+    if (!(await formRef.value.savePending(form.id!))) return
+    message.success(creating ? '创建成功' : '更新成功')
     formVisible.value = false
     if (sourceSurveyId.value) await router.push({ path: surveyPath, query: { surveyId: String(sourceSurveyId.value) } })
     else await load()
```

## 098 views/pms/engineering/outsource/OutsourceRequestDetailDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增附件功能**。原外包表单/明细增加原生材料区，仍按原状态和权限维护。
- 证据及验证边界：NativeAttachments.runtime.spec.ts。

### H01 @@ -33,9 +33,11 @@

裁决：保留纯新增附件功能。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -33,9 +33,11 @@
         {{ current.approveOpinion }}
       </el-descriptions-item>
     </el-descriptions>
+    <NativeAttachments v-if="current.id" kind="outsourceRequest" :entity-id="current.id" :readonly="true" :legacy="current.attachmentFiles" />
   </Dialog>
 </template>
 <script setup lang="ts">
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { computed } from 'vue'
 import type { OutsourceRequestVO } from '@/api/pms/engineering/outsource'
 import { DICT_TYPE } from '@/utils/dict'
```

## 099 views/pms/engineering/outsource/OutsourceRequestFormDialog.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：3。
- 裁决：**修复重叠控件**。合并重复附件栏并恢复原位置选取文件/保存顺序；保留原申请必填校验、工勘上下文和权限，不显示不能保存的旧URL上传入口。
- 证据及验证边界：OutsourceRequestFormDialog.runtime.spec.ts; outsourceSurveyContext.runtime.spec.ts。

### H01 @@ -127,7 +127,8 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -127,7 +127,8 @@
         </el-col>
         <el-col :span="24">
           <el-form-item label="附件" prop="attachmentFiles">
-            <UploadFile v-model="form.attachmentFiles!" />
+            <NativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="outsourceRequest" :entity-id="form.id"
+              :readonly="form.id ? ![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:res-outsource:update']) : !checkPermi(['pms:res-outsource:create'])" :legacy="form.attachmentFiles" @changed="emit('attachmentChanged', $event)" />
           </el-form-item>
         </el-col>
         <el-col :span="24">
```

### H02 @@ -156,15 +157,20 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED, d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -156,15 +157,20 @@
   </Dialog>
 </template>
 <script setup lang="ts">
-import { computed, ref } from 'vue'
+import { computed, ref, watch } from 'vue'
+import { checkPermi } from '@/utils/permission'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
 import * as ProjectApi from '@/api/pms/project/projects'
 import * as UserApi from '@/api/system/user'
 import type { OutsourceRequestVO } from '@/api/pms/engineering/outsource'
 const props = defineProps<{ modelValue: boolean; form: OutsourceRequestVO; sourceSurveyId?: number | string; saving: boolean; error?: string }>()
-const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [] }>()
+const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: []; attachmentChanged: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
 const visible = computed({ get: () => props.modelValue, set: value => { if (!props.saving) emit('update:modelValue', value) } })
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
+watch(() => props.modelValue, visible => { if (visible) attachmentEpoch.value++ })
 const rules = {
   projectId: [{ required: true, message: '请选择项目' }],
   code: [{ required: true, message: '请输入单号' }],
```

### H03 @@ -174,7 +180,8 @@

裁决：修复重叠控件。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -174,7 +180,8 @@
   applicantUserId: [{ required: true, message: '请选择申请人' }],
   applyTime: [{ required: true, message: '请选择申请时间' }]
 }
-defineExpose({ validate: () => formRef.value.validate() })
+defineExpose({ validate: () => formRef.value.validate(),
+  savePending: (id: number) => attachmentsRef.value?.savePending(id) ?? false })
 </script>
 <style scoped>
 @media (max-width: 767px) {
```

## 100 views/pms/engineering/outsource/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：3。
- 裁决：**修复附件保存依赖**。原工勘来源及执行轮次继续随正式创建提交；附件失败留在本窗口重试、不重复创建，全部成功才按原路径返回工勘。
- 证据及验证边界：outsourceSurveyContext.runtime.spec.ts; nativeApplicationSave.runtime.spec.ts。

### H01 @@ -149,7 +149,7 @@

裁决：修复附件保存依赖。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -149,7 +149,7 @@
 
   <!-- 新建/编辑对话框 -->
   <OutsourceRequestFormDialog ref="formRef" v-model="formVisible" :form="form"
-    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" />
+    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" @attachment-changed="attachmentOwnerChanged" />
 
   <!-- 明细对话框 -->
   <OutsourceRequestDetailDialog v-model="detailVisible" :current="current" />
```

### H02 @@ -190,7 +190,7 @@

裁决：修复附件保存依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -190,7 +190,7 @@
 <script setup lang="ts">
 import OutsourceRequestFormDialog from './OutsourceRequestFormDialog.vue'
 import OutsourceRequestDetailDialog from './OutsourceRequestDetailDialog.vue'
-import { onMounted, reactive, ref } from 'vue'
+import { nextTick, onMounted, reactive, ref } from 'vue'
 import { useRoute, useRouter } from 'vue-router'
 import { dateFormatter, formatDate } from '@/utils/formatTime'
 import { useMessage } from '@/hooks/web/useMessage'
```

### H03 @@ -296,18 +296,25 @@

裁决：修复附件保存依赖。当前行来源：LOCAL_UNCOMMITTED, be007ec3379b3c3f6951fe7a4f0052eec4c11097。

```diff
@@ -296,18 +296,25 @@
   Object.assign(form, detail, { siteSurveyExecution: undefined })
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.version = saved.version
+  form.status = saved.status
+}
 const save = async () => {
   if (saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
+    const creating = !form.id
     if (form.id) {
       await OutsourceApi.updateOutsourceRequest(form)
-      message.success('更新成功')
     } else {
-      await OutsourceApi.createOutsourceRequest(form)
-      message.success(sourceSurveyId.value ? '转包申请草稿已创建，ID已关联回工勘；尚未提交审批。' : '创建成功')
+      form.id = await OutsourceApi.createOutsourceRequest(form)
     }
+    Object.assign(form, await OutsourceApi.getOutsourceRequest(form.id!))
+    await nextTick()
+    if (!(await formRef.value.savePending(form.id!))) return
+    message.success(creating ? (sourceSurveyId.value ? '转包申请草稿已创建，ID已关联回工勘；尚未提交审批。' : '创建成功') : '更新成功')
     formVisible.value = false
     if (sourceSurveyId.value) await router.push({ path: surveyPath, query: {
       surveyId: String(sourceSurveyId.value), ...surveyExecutionQuery(form.siteSurveyExecution,
```

## 101 views/pms/engineering/solution-reviewed/saveSolutionScripts.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。脚本附件文件ID/nullable读取兼容，不改变方案审核内容。
- 证据及验证边界：saveSolutionScripts既有测试。

### H01 @@ -1,11 +1,11 @@

裁决：保留文件兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -1,11 +1,11 @@
 import { getDeliveryTypes } from '@/api/pms/platform/delivery'
-import { initializeUpload, completeUpload, createAccessTicket } from '@/api/pms/platform/file'
+import { initializeUpload, completeUpload, createAccessTicket, type FileId } from '@/api/pms/platform/file'
 import { createDeliveryUploadAttempt, type DeliveryUploadAttempt } from '@/components/DeliveryArtifact/uploadDeliveryFile'
 
 export type ScriptSection = 'network' | 'businessModule' | 'ha' | 'full'
 export interface PendingScript { section: ScriptSection; file: File; attempt?: DeliveryUploadAttempt; completed?: ScriptReference }
 export interface ScriptReference {
-  section: ScriptSection; name: string; referenceId: number; artifactId: number
+  section: ScriptSection; name: string; referenceId: FileId; artifactId: FileId
   versionNo: number; referenceKey: string; sha256: string
 }
 export const downloadSolutionScript = async (input: { solutionId: number; reference: ScriptReference }) => {
```

## 102 views/pms/integration/BaseSyncPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -0,0 +1,120 @@

裁决：保留纯新增及其契约配套。当前行来源：b143cfe890dda1ef51f21d34abb75a5b5fd43ee9。

```diff
@@ -0,0 +1,120 @@
+<template>
+  <ContentWrap v-hasPermi="['pms:integration:view']">
+    <div class="mb-4">
+      <el-button :disabled="busy" :loading="loading" @click="load">刷新</el-button>
+    </div>
+    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
+    <el-descriptions v-if="result" :title="result.title" border class="mb-4" :column="5">
+      <el-descriptions-item label="读取">{{ result.counts.read }}</el-descriptions-item>
+      <el-descriptions-item label="插入">{{ result.counts.inserted }}</el-descriptions-item>
+      <el-descriptions-item label="更新">{{ result.counts.updated }}</el-descriptions-item>
+      <el-descriptions-item label="未变">{{ result.counts.unchanged }}</el-descriptions-item>
+      <el-descriptions-item label="删除">{{ result.counts.deleted }}</el-descriptions-item>
+    </el-descriptions>
+    <el-table v-loading="loading" :data="rows" row-key="id" class="integration-table">
+      <el-table-column prop="taskCode" label="任务编码" width="140" />
+      <el-table-column label="任务名称" min-width="200">
+        <template #default="{ row }">{{ names[row.taskCode] || row.taskCode }}</template>
+      </el-table-column>
+      <el-table-column prop="connectionId" label="连接 ID" min-width="190" />
+      <el-table-column label="每批数量" width="190">
+        <template #default="{ row }">
+          <el-input-number
+            v-model="row.inputBatchSize"
+            :min="1"
+            :max="2000"
+            :precision="0"
+            :disabled="busy || hasActiveRun"
+            :aria-label="`${names[row.taskCode] || row.taskCode}每批数量`"
+          />
+        </template>
+      </el-table-column>
+      <el-table-column prop="maxRows" label="读取上限" width="120" />
+      <el-table-column label="活动 Run" min-width="190">
+        <template #default="{ row }">{{ row.activeRunId ?? '—' }}</template>
+      </el-table-column>
+      <el-table-column label="操作" width="180" fixed="right">
+        <template #default="{ row }">
+          <el-button
+            v-hasPermi="['pms:integration:execute']"
+            link
+            type="primary"
+            :disabled="busy || loading || hasActiveRun"
+            @click="submit(row, true)"
+            >预览</el-button
+          >
+          <el-button
+            v-hasPermi="['pms:integration:execute']"
+            link
+            type="primary"
+            :disabled="busy || loading || hasActiveRun"
+            @click="submit(row, false)"
+            >执行</el-button
+          >
+        </template>
+      </el-table-column>
+    </el-table>
+  </ContentWrap>
+</template>
+<script setup lang="ts">
+import * as api from '@/api/pms/integration/base-sync'
+type Row = api.Registration & { inputBatchSize?: number }
+const rows = ref<Row[]>([])
+const names = ref<Record<string, string>>({})
+const loading = ref(false)
+const busy = ref(false)
+const error = ref('')
+const result = ref<{ title: string; counts: api.SyncCounts }>()
+const hasActiveRun = computed(() => rows.value.some((row) => row.activeRunId != null))
+const registrations = async () => {
+  const inputs = new Map(rows.value.map((row) => [String(row.id), row.inputBatchSize]))
+  rows.value = (await api.getRegistrations()).map((row) => ({
+    ...row,
+    inputBatchSize: inputs.has(String(row.id)) ? inputs.get(String(row.id)) : row.batchSize
+  }))
+}
+const load = async () => {
+  if (busy.value || loading.value) return
+  loading.value = true
+  error.value = ''
+  try {
+    const [tasks] = await Promise.all([api.getTasks(), registrations()])
+    names.value = Object.fromEntries(tasks.map((task) => [task.code, task.name]))
+  } catch {
+    error.value = '基础同步任务加载失败'
+  } finally {
+    loading.value = false
+  }
+}
+const submit = async (row: Row, preview: boolean) => {
+  if (busy.value || loading.value || hasActiveRun.value) return
+  const batchSize = row.inputBatchSize ?? undefined
+  if (
+    batchSize !== undefined &&
+    (!Number.isInteger(batchSize) || batchSize < 1 || batchSize > 2000)
+  ) {
+    error.value = '每批数量须为1至2000的整数'
+    return
+  }
+  busy.value = true
+  error.value = ''
+  result.value = undefined
+  try {
+    const counts = await api.run(preview, row.id, batchSize)
+    result.value = {
+      title: `${names.value[row.taskCode] || row.taskCode} · ${preview ? '预览' : '执行'}`,
+      counts
+    }
+  } catch {
+    error.value = '请求未成功返回，请刷新核对任务状态后再操作'
+  } finally {
+    try {
+      await registrations()
+    } catch {
+      error.value ||= '任务状态刷新失败'
+    }
+    busy.value = false
+  }
+}
+onMounted(load)
+</script>
```

## 103 views/pms/integration/ChangeDetails.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -10,10 +10,36 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -10,10 +10,36 @@
       </template>
     </el-table-column>
   </el-table>
+  <template v-if="originalFields.length">
+    <el-divider content-position="left">{{ originalFieldsTitle }}</el-divider>
+    <el-table :data="originalFields" class="integration-table" aria-label="原表字段">
+      <el-table-column prop="field" label="原字段" min-width="180" />
+      <el-table-column prop="value" label="原值" min-width="280" />
+    </el-table>
+  </template>
 </template>
 <script setup lang="ts">
 import type { Change } from '@/api/pms/integration'
-import { fieldChanges } from './changePresentation'
+import {
+  dictionaryDataOriginalFields,
+  deviceRelationshipOriginalFields,
+  fieldChanges,
+  projectContractOriginalFields
+} from './changePresentation'
 const props = defineProps<{ change: Change }>()
 const fields = computed(() => fieldChanges(props.change))
+const originalFields = computed(() =>
+  props.change.object === 'fnd_basic_data'
+    ? dictionaryDataOriginalFields(props.change)
+    : props.change.object === 'fb_shipment_barcode_relation'
+      ? deviceRelationshipOriginalFields(props.change)
+      : projectContractOriginalFields(props.change)
+)
+const originalFieldsTitle = computed(() =>
+  props.change.object === 'fnd_basic_data'
+    ? '原表字段：基础字典项'
+    : props.change.object === 'fb_shipment_barcode_relation'
+      ? '原表字段：主附设备关系'
+      : '原表字段：合同及关联桥'
+)
 </script>
```

## 104 views/pms/integration/FieldMappingEditor.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -136,6 +136,8 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -136,6 +136,8 @@
   { value: 'DIRECT', label: '直接映射' },
   { value: 'STRING', label: '转为文本' },
   { value: 'TRIM', label: '去首尾空格' },
+  { value: 'DPPMS_COMPANY_CODE', label: 'DPPMS公司编码规范转换' },
+  { value: 'DPPMS_WARRANTY_YEARS', label: 'DPPMS合同保修年数（空值保留为NULL）' },
   { value: 'LONG', label: '转为整数' },
   { value: 'DECIMAL', label: '转为小数' },
   { value: 'BOOLEAN', label: '转为布尔' },
```

### H02 @@ -146,7 +148,8 @@

裁决：保留纯新增及其契约配套。当前行来源：7907c8117bce8eae3dc2b3b3dde68f88ffc543d4。

```diff
@@ -146,7 +148,8 @@
   ...(props.generic
     ? [
         { value: 'DATE', label: '日期' },
-        { value: 'JSON', label: 'JSON 载荷' }
+        { value: 'JSON', label: 'JSON 载荷' },
+        { value: 'JSON_TEXT', label: '解析 JSON 文本' }
       ]
     : [{ value: 'REFERENCE', label: '来源关系引用' }])
 ])
```

## 105 views/pms/integration/ProductCatalogSetup.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -0,0 +1,54 @@

裁决：保留纯新增及其契约配套。当前行来源：7907c8117bce8eae3dc2b3b3dde68f88ffc543d4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,54 @@
+<template>
+  <el-form label-width="128px" @submit.prevent>
+    <el-form-item label="产品编码">
+      <el-input
+        v-model="codes"
+        type="textarea"
+        :rows="6"
+        placeholder="每行一个原产品编码；只承接填写的产品"
+      />
+    </el-form-item>
+    <el-form-item>
+      <el-button :loading="loading" @click="configure">应用产品目录承接配置</el-button>
+    </el-form-item>
+  </el-form>
+</template>
+<script setup lang="ts">
+import { ref, watch } from 'vue'
+import * as api from '@/api/pms/integration'
+const props = defineProps<{ modelValue: api.Definition }>()
+const emit = defineEmits<{ (event: 'update:modelValue', value: api.Definition): void }>()
+const message = useMessage()
+const codes = ref('')
+const loading = ref(false)
+watch(
+  () => props.modelValue.sources,
+  (sources) => {
+    const parameters = sources[0]?.parameters ?? {}
+    codes.value = Object.keys(parameters)
+      .filter((key) => /^productCode\d+$/.test(key))
+      .sort((left, right) => Number(left.slice(11)) - Number(right.slice(11)))
+      .map((key) => String(parameters[key]))
+      .join('\n')
+  },
+  { immediate: true }
+)
+const configure = async () => {
+  const selected = codes.value.split(/\r?\n/).filter((code) => code.trim().length > 0)
+  if (!selected.length || new Set(selected).size !== selected.length) {
+    message.error('请填写产品编码，且不能重复')
+    return
+  }
+  loading.value = true
+  try {
+    const definition = await api.getDppmsProductTemplate({
+      connectionId: props.modelValue.connectionId,
+      productCodes: selected
+    })
+    emit('update:modelValue', definition)
+    message.success('目录承接配置已应用，请保存任务并预览')
+  } finally {
+    loading.value = false
+  }
+}
+</script>
```

## 106 views/pms/integration/RunPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：14；当前片段：14。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -22,10 +22,10 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -22,10 +22,10 @@
             ><template #default="{ row }">{{
               row.pageNumber
                 ? `第 ${row.pageNumber} 页`
-                : streamingProgress(row)
-                  ? '流式主运行'
+                : isPagedRoot(row)
+                  ? '分页主运行'
                   : row.pagingJson
-                    ? '分页主运行'
+                    ? '流式分块'
                     : '—'
             }}</template></el-table-column
           >
```

### H02 @@ -108,7 +108,7 @@

裁决：保留纯新增及其契约配套。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -108,7 +108,7 @@
             :label="
               selectedProgress
                 ? '本次处理行数'
-                : selected.pagingJson && !selected.pageNumber
+                : isPagedRoot(selected)
                   ? '已完成页行数'
                   : '读取行数'
             "
```

### H03 @@ -126,7 +126,7 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -126,7 +126,7 @@
             label="重试来源"
           >
             <el-button link type="primary" @click="select({ id: selected.parentRunId } as api.Run)"
-              >查看上次运行及已提交数据</el-button
+              >查看上次运行</el-button
             >
           </el-descriptions-item>
           <el-descriptions-item v-if="selected.pageNumber" label="分页归属">
```

### H04 @@ -159,6 +159,16 @@

裁决：保留纯新增及其契约配套。当前行来源：b5599b9059f1916b2d16c8bb0bdefdb6055b3f86, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -159,6 +159,16 @@
           class="my-3"
         />
         <el-button
+          v-if="
+            !selected.pageNumber &&
+            ['QUEUED', 'READING', 'VALIDATING', 'APPLYING'].includes(selected.status)
+          "
+          v-hasPermi="['pms:integration:execute']"
+          :loading="recovering"
+          @click="recoverSelected"
+          >核查中断运行</el-button
+        >
+        <el-button
           v-if="selected.status === 'FAILED' && !selected.pageNumber"
           v-hasPermi="['pms:integration:execute']"
           :loading="retrying"
```

### H05 @@ -166,10 +176,7 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -166,10 +176,7 @@
           >创建关联重试</el-button
         >
       </ContentWrap>
-      <ContentWrap
-        v-if="selected.pagingJson && !selected.pageNumber && !selectedProgress"
-        title="分页批次"
-      >
+      <ContentWrap v-if="isPagedRoot(selected)" title="分页批次">
         <el-alert
           title="每页结果和来源证据独立保存；主运行失败时，已成功的页仍保留，关联重试从已提交游标继续。"
           type="info"
```

### H06 @@ -204,10 +211,17 @@

裁决：保留纯新增及其契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -204,10 +211,17 @@
         <p v-if="selectedProgress && !selected.preview" class="integration-note"
           >此处展示处理样本；全量处理结果以运行汇总为准，关联重试保留已提交分块。</p
         >
+        <el-alert
+          v-if="changeTotal > 0 && (selected.readCount ?? 0) > changeTotal"
+          :title="`当前展示前 ${changeTotal} 条明细样本。完整读取及处理数量见运行结果，全部来源记录保存在迁移证据中。`"
+          type="info"
+          :closable="false"
+          class="mb-3"
+        />
         <el-table
           :data="changes"
           class="integration-table"
-          empty-text="暂无处理样本，处理进度见上方运行汇总"
+          :empty-text="changeEmptyText"
         >
           <el-table-column prop="object" label="对象" width="130" /><el-table-column
             prop="sourceKey"
```

### H07 @@ -222,7 +236,11 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -222,7 +236,11 @@
           <el-table-column prop="targetId" label="目标 ID" min-width="170" />
           <el-table-column label="名称" min-width="180"
             ><template #default="{ row }">{{
-              row.after?.name ?? row.after?.orderNo ?? row.before?.name ?? '—'
+              dictionaryDataLabel(row) ??
+              row.after?.name ??
+              row.after?.orderNo ??
+              row.before?.name ??
+              '—'
             }}</template></el-table-column
           >
           <el-table-column prop="message" label="说明" min-width="180" />
```

### H08 @@ -266,6 +284,7 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -266,6 +284,7 @@
 import { formatDate } from '@/utils/formatTime'
 import { useMediaQuery } from '@vueuse/core'
 import ChangeDetails from './ChangeDetails.vue'
+import { dictionaryDataLabel } from './changePresentation'
 const narrow = useMediaQuery('(max-width: 640px)')
 const detailChange = ref<api.Change>()
 const emit = defineEmits<{ selected: [id: api.Id]; back: [] }>()
```

### H09 @@ -273,11 +292,21 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -273,11 +292,21 @@
 const loading = ref(false),
   error = ref(''),
   retrying = ref(false),
+  recovering = ref(false),
   rows = ref<api.Run[]>([]),
   total = ref(0)
 const selected = ref<api.Run>(),
   changes = ref<api.Change[]>([]),
   changeTotal = ref(0)
+const isPagedRoot = (run: api.Run) => {
+  if (!run.pagingJson || run.pageNumber) return false
+  try {
+    // Paged runs persist upperIds; streaming runs persist committedChunks and have no child runs.
+    return Array.isArray(JSON.parse(run.pagingJson).upperIds)
+  } catch {
+    return false
+  }
+}
 const streamingProgress = (run: api.Run) => {
   if (!run.pagingJson || run.pageNumber) return undefined
   try {
```

### H10 @@ -293,9 +322,11 @@

裁决：保留纯新增及其契约配套。当前行来源：9c61ff976effa84b8aede2ecb2a70f057f17ee08。

```diff
@@ -293,9 +322,11 @@
 const selectedProgress = computed(() => selected.value && streamingProgress(selected.value))
 const runStatusLabel = (run: api.Run) =>
   run.status === 'FAILED' && run.pagingJson && !run.pageNumber
-    ? streamingProgress(run)
-      ? '同步中断（已提交分块保留）'
-      : '分页停止（已成功页保留）'
+    ? run.preview
+      ? '预览停止（未写入）'
+      : isPagedRoot(run)
+        ? '分页停止（已成功页保留）'
+        : '分块停止（已提交块保留）'
     : api.runLabels[run.status] || run.status
 const childPages = ref<api.Run[]>([]),
   childTotal = ref(0)
```

### H11 @@ -307,6 +338,19 @@

裁决：保留纯新增及其契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -307,6 +338,19 @@
     if (!id) selected.value = undefined
   }
 )
+const completedEmptyQuery = computed(
+  () =>
+    !!selected.value &&
+    ['SUCCESS', 'PREVIEW_READY'].includes(selected.value.status) &&
+    selected.value.readCount === 0
+)
+const changeEmptyText = computed(() =>
+  completedEmptyQuery.value
+    ? '本次查询没有记录，无需写入业务数据'
+    : selected.value?.status === 'FAILED'
+      ? '暂无差异结果；失败批次未提交业务数据'
+      : '暂无差异结果'
+)
 const summary = computed(() => {
   try {
     const counts = JSON.parse(selected.value?.summaryJson || '{}') as Record<string, number>
```

### H12 @@ -315,7 +359,7 @@

裁决：保留纯新增及其契约配套。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -315,7 +359,7 @@
         .map(
           ([key, count]) => (key === 'FAILED' ? '失败' : api.changeLabels[key] || key) + ' ' + count
         )
-        .join('，') || '尚无处理结果'
+        .join('，') || (completedEmptyQuery.value ? '本次查询没有记录' : '尚无处理结果')
     )
   } catch {
     return '尚无处理结果'
```

### H13 @@ -326,7 +370,7 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -326,7 +370,7 @@
 let timer: ReturnType<typeof setTimeout> | undefined
 const loadChanges = async () => {
   if (!selected.value) return
-  if (selected.value.pagingJson && !selected.value.pageNumber && !selectedProgress.value) {
+  if (isPagedRoot(selected.value)) {
     const p = await api.getRuns({ ...childPage, parentRunId: selected.value.id })
     childPages.value = p.list.filter((r) => !!r.pageNumber)
     childTotal.value = p.total
```

### H14 @@ -367,6 +411,20 @@

裁决：保留纯新增及其契约配套。当前行来源：b5599b9059f1916b2d16c8bb0bdefdb6055b3f86, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -367,6 +411,20 @@
     error.value = '运行详情加载失败，请重试；不会重复提交任务。'
   } finally {
     loading.value = false
+  }
+}
+const recoverSelected = async () => {
+  if (!selected.value) return
+  recovering.value = true
+  error.value = ''
+  try {
+    const recovered = await api.recoverRun(selected.value.id)
+    await refreshSelected()
+    useMessage().success(recovered ? '已恢复中断回执，可创建关联重试' : '运行已结束，无需恢复')
+  } catch {
+    error.value = '中断核查未成功，请刷新状态后确认运行是否仍在执行。'
+  } finally {
+    recovering.value = false
   }
 }
 const watchRun = () => {
```

## 107 views/pms/integration/TaskEditor.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：10；当前片段：9。
- 裁决：**部分恢复并保留新增功能**。恢复原“同步方式”标签；保留新增独立同步适配器、证据策略和各自参数，不运行迁移或清空数据。
- 证据及验证边界：integration既有测试；只读静态核查。

### H01 @@ -60,6 +60,10 @@

裁决：部分恢复并保留新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -60,6 +60,10 @@
         </el-form>
       </el-tab-pane>
       <el-tab-pane label="来源与字段映射" name="sources">
+        <ProductCatalogSetup
+          v-if="draft.definition.adapter === 'DPPMS_PRODUCT_CATALOG'"
+          v-model="draft.definition"
+        />
         <GenericMappingEditor
           v-if="draft.definition.adapter === 'TABLE_MAPPING'"
           ref="genericEditor"
```

### H02 @@ -179,6 +183,22 @@

裁决：部分恢复并保留新增功能。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398。

```diff
@@ -179,6 +183,22 @@
             :closable="false"
             title="当前业务适配器需要整批上下文，不支持流式分块执行。"
           />
+          <el-form-item label="同步证据策略">
+            <el-select v-model="draft.definition.evidencePolicy" aria-label="同步证据策略">
+              <el-option label="完整记录每次读取（兼容模式）" value="FULL" />
+              <el-option
+                label="复用未变化证据（大批量同步）"
+                value="REUSE_UNCHANGED"
+                :disabled="draft.definition.adapter !== 'TABLE_MAPPING' || draft.definition.readStrategy !== 'STREAMING_CURSOR'"
+              />
+            </el-select>
+          </el-form-item>
+          <el-alert
+            v-if="draft.definition.evidencePolicy === 'REUSE_UNCHANGED'"
+            type="info"
+            :closable="false"
+            title="仍逐行核对来源和目标。完全未变化时复用原证据，不追加重复来源/映射，也不改写绑定；新增、变化及异常照常留证，历史记录保留。"
+          />
           <template v-if="draft.definition.readStrategy === 'STREAMING_CURSOR'">
             <el-form-item label="JDBC Fetch Size">
               <el-input-number v-model="draft.definition.fetchSize" :min="1" :max="10000" />
```

### H03 @@ -262,6 +282,8 @@

裁决：部分恢复并保留新增功能。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -262,6 +282,8 @@
   </el-dialog>
 </template>
 <script setup lang="ts">
+import { ElMessageBox } from 'element-plus'
+import ProductCatalogSetup from './ProductCatalogSetup.vue'
 import * as api from '@/api/pms/integration'
 import SourceEditor from './SourceEditor.vue'
 import GenericMappingEditor from './GenericMappingEditor.vue'
```

### H04 @@ -291,6 +313,7 @@

裁决：部分恢复并保留新增功能。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398。

```diff
@@ -291,6 +313,7 @@
   const definition = draft.value.definition
   if (definition.autoPaging) definition.readStrategy = 'KEYSET_PAGING'
   definition.readStrategy ??= 'SNAPSHOT'
+  definition.evidencePolicy ??= 'FULL'
   definition.fetchSize ??= 2000
   definition.chunkSize ??= 1000
   definition.restartPolicy ??= 'RESTART_ALL'
```

### H05 @@ -302,6 +325,7 @@

裁决：部分恢复并保留新增功能。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398。

```diff
@@ -302,6 +325,7 @@
   draft.value.definition.autoPaging = draft.value.definition.readStrategy === 'KEYSET_PAGING'
   if (draft.value.definition.readStrategy !== 'STREAMING_CURSOR') {
     draft.value.definition.restartPolicy ??= 'RESTART_ALL'
+    draft.value.definition.evidencePolicy = 'FULL'
   }
 }
 const open = async (id?: api.Id) => {
```

### H06 @@ -328,6 +352,140 @@

裁决：部分恢复并保留新增功能。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398, 6a56c3f2f20f46a095dcb1d09439e06688a1c4dc。

```diff
@@ -328,6 +352,140 @@
 }
 const changeAdapter = async () => {
   if (!draft.value || !selectedAdapter.value) return
+  draft.value.definition.evidencePolicy = 'FULL'
+  if (selectedAdapter.value.key === 'EHR_ORGANIZATION') {
+    draft.value.definition = await api.getEhrTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'EHR 公司与部门同步'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_ORGANIZATION') {
+    draft.value.definition = await api.getDppmsOrganizationTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P01 公司与部门同步（原任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_ORGANIZATION_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectOrganizationTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P01 公司与部门同步（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_ACCOUNT_FOUNDATION') {
+    draft.value.definition = await api.getDppmsAccountFoundationTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P04 账号、角色及范围同步（原任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_ACCOUNT_FOUNDATION_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectAccountTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P04 账号、角色及范围同步（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_DICTIONARIES_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectDictionaryTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P08 基础字典同步（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'CRM_CUSTOMER_DIRECT') {
+    draft.value.definition = await api.getCrmDirectCustomerTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P10 CRM客户同步（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PRODUCT_CATALOG_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectProductTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P12 缺失产品补齐（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_CRM_EXECUTION_ORDER_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectExecutionOrderTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P13 执行单主档（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_CONTRACT_RECEIVABLE_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectReceivableTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P24 合同应收信息（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_CONTRACT_RELATION_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectProjectRelationTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P18 项目合同关系（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_FACTS_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectProjectFactsTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P19 项目相关方与状态（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_DEVICE_BOOTSTRAP_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectDeviceBaseTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P30 设备基础（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_MEMBER_HISTORY_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectMemberHistoryTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P31 成员与联系人历史（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_INSTALLATION_HISTORY_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectInstallationTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P33 普通安装历史（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_FACTORY_HISTORY_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectFactoryHistoryTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P34 出厂版本历史（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_SOFTWARE_HISTORY_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectSoftwareHistoryTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P35 软件批次与版本历史（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_SECURE_LICENSE_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectLicenseHistoryTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P37 受控许可历史（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_REAL_PRODUCT_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectRealProductTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P20 项目真实产品清单（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectProductScopeTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P22 项目产品实施范围（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_MASTER_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectProjectTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P16 项目主档与来源身份（直写任务）'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_WARRANTY_RENEWAL_DIRECT') {
+    draft.value.definition = await api.getDppmsDirectWarrantyTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'P25 设备续保记录（直写任务）'
+    normalizePerformance()
+    return
+  }
+
   if (selectedAdapter.value.key === 'TABLE_MAPPING') {
     const templates = await api.getGenericTemplates(draft.value.definition.connectionId)
     draft.value.definition = templates[0].definition
```

### H07 @@ -343,6 +501,77 @@

裁决：部分恢复并保留新增功能。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, e0d836f4ade472f0c53147cfb7db8192f0a8a18e, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -343,6 +501,77 @@
     normalizePerformance()
     return
   }
+  if (selectedAdapter.value.key === 'DPPMS_PRODUCT_CATALOG') {
+    draft.value.name = 'DPPMS 产品目录及正式名称型号承接'
+    draft.value.definition = {
+      ...draft.value.definition,
+      sourceSystem: 'DPPMS',
+      mode: 'ONCE',
+      loadingMode: 'UPSERT',
+      missingPolicy: 'RETAIN',
+      clearBeforeLoad: false,
+      resetMappingsBeforeLoad: false,
+      readStrategy: 'STREAMING_CURSOR',
+      restartPolicy: 'RESTART_ALL',
+      sources: []
+    }
+    normalizePerformance()
+    section.value = 'sources'
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_PRODUCT_SCOPE') {
+    draft.value.definition = await api.getDppmsProjectProductScopeTemplate(
+      draft.value.definition.connectionId
+    )
+    draft.value.name = 'DPPMS 项目产品实施范围迁移'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_REAL_PRODUCT') {
+    draft.value.definition = await api.getDppmsProjectRealProductTemplate(
+      draft.value.definition.connectionId
+    )
+    draft.value.name = 'DPPMS 项目真实产品清单迁移'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_WARRANTY_RENEWAL') {
+    draft.value.definition = await api.getDppmsWarrantyRenewalTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'DPPMS 设备续保记录迁移与同步'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_CONTRACT_RECEIVABLE') {
+    draft.value.definition = await api.getDppmsContractReceivableTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'DPPMS 合同回款信息迁移与同步'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_OBSOLETE_ORDER_LINE_BINDING') {
+    const result = await ElMessageBox.prompt('填写已裁决的原订单迁移任务ID，仅清理该任务中无正式目标的旧01绑定。', '原订单迁移任务', {
+      inputPattern: /^[1-9][0-9]*$/, inputErrorMessage: '请输入有效任务ID'
+    }).catch(() => null)
+    if (!result) return
+    draft.value.definition = await api.getDppmsObsoleteOrderLineTemplate(draft.value.definition.connectionId, result.value)
+    draft.value.name = 'DPPMS 旧01订单行迁移记录清理'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_FACTS') {
+    const definitionId = await api.configureDppmsProjectFactsDefinition()
+    draft.value.definition = await api.getDppmsProjectFactsTemplate(draft.value.definition.connectionId, definitionId)
+    draft.value.name = 'DPPMS 项目相关方及发货执行状态'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_PROJECT_ASSOCIATED_PRODUCTS') {
+    draft.value.definition = await api.getDppmsProjectAssociatedProductTemplate(
+      draft.value.definition.connectionId
+    )
+    draft.value.name = 'DPPMS 项目租赁及产品配置迁移'
+    normalizePerformance()
+    return
+  }
   if (selectedAdapter.value.key === 'PAYMENT_PLAN_ACCEPTANCE') {
     draft.value.definition = await api.getPaymentAcceptanceTemplate(
       draft.value.definition.connectionId
```

### H08 @@ -354,10 +583,50 @@

裁决：部分恢复并保留新增功能。当前行来源：b5599b9059f1916b2d16c8bb0bdefdb6055b3f86, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -354,10 +583,50 @@
     )
     return
   }
+  if (selectedAdapter.value.key === 'DPPMS_CONTRACT_COLLECTION_PLAN') {
+    draft.value.definition = await api.getDppmsCollectionPlanTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'DPPMS 合同回款计划迁移'
+    normalizePerformance()
+    return
+  }
   if (selectedAdapter.value.key === 'DPPMS_ERP_ORDER') {
     draft.value.definition = await api.getDppmsOrderTemplate(draft.value.definition.connectionId)
     normalizePerformance()
     if (draft.value.name === 'EHR 公司与部门同步') draft.value.name = 'DPPMS 销售订单与订单行迁移'
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_ROLES') {
+    draft.value.definition = await api.getDppmsRoleTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'DPPMS 正式角色迁移'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_DEPARTMENTS') {
+    draft.value.definition = await api.getDppmsDepartmentTemplate(
+      draft.value.definition.connectionId
+    )
+    draft.value.name = 'DPPMS 正式部门补齐'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_USERS') {
+    draft.value.definition = await api.getDppmsUserTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'DPPMS 正式用户迁移'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_USER_ROLES') {
+    draft.value.definition = await api.getDppmsUserRoleTemplate(draft.value.definition.connectionId)
+    draft.value.name = 'DPPMS 正式用户角色关系迁移'
+    normalizePerformance()
+    return
+  }
+  if (selectedAdapter.value.key === 'DPPMS_USER_SCOPES') {
+    draft.value.definition = await api.getDppmsUserScopeTemplate(
+      draft.value.definition.connectionId
+    )
+    draft.value.name = 'DPPMS 正式公司部门范围迁移'
+    normalizePerformance()
     return
   }
   draft.value.definition.loadingMode = selectedAdapter.value.loadingModes[0]
```

### H09 @@ -388,6 +657,7 @@

裁决：部分恢复并保留新增功能。当前行来源：5211ac38dce85a9630f67ec31f7a29d28b0f2398。

```diff
@@ -388,6 +657,7 @@
   draft.value.definition.clearBeforeLoad = false
   draft.value.definition.loadingMode = 'UPSERT'
   draft.value.definition.readStrategy = 'SNAPSHOT'
+  draft.value.definition.evidencePolicy = 'FULL'
   draft.value.definition.autoPaging = false
   draft.value.definition.sources.forEach((source) => (source.syncPrimaryKey = true))
 }
```

## 108 views/pms/integration/changePresentation.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -1,4 +1,42 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -1,4 +1,42 @@
 import type { Change } from '@/api/pms/integration'
+import { formatDate } from '@/utils/formatTime'
+
+const isRecord = (value: unknown): value is Record<string, unknown> =>
+  typeof value === 'object' && value !== null && !Array.isArray(value)
+
+const isProjectContractResult = (change: Change) =>
+  change.object === 'pm_project_contract' && isRecord(change.after?.relation)
+
+export const deviceRelationshipOriginalFields = (change: Change) => {
+  if (change.object !== 'fb_shipment_barcode_relation' || !isRecord(change.after?.originalFields)) return []
+  return Object.entries(change.after.originalFields).map(([field, value]) => ({
+    field,
+    value: formatFieldValue(value)
+  }))
+}
+
+export const dictionaryDataLabel = (change: Change): string | undefined => {
+  const item = change.after?.item
+  return change.object === 'fnd_basic_data' && isRecord(item) && typeof item.label === 'string'
+    ? item.label
+    : undefined
+}
+
+export const dictionaryDataOriginalFields = (change: Change) => {
+  if (change.object !== 'fnd_basic_data' || !isRecord(change.after?.originalFields)) return []
+  return Object.entries(change.after.originalFields).map(([field, value]) => ({
+    field,
+    value: formatFieldValue(value)
+  }))
+}
+
+export const projectContractOriginalFields = (change: Change) => {
+  if (!isProjectContractResult(change) || !isRecord(change.after?.originalFields)) return []
+  return Object.entries(change.after.originalFields).map(([field, value]) => ({
+    field,
+    value: formatFieldValue(value)
+  }))
+}
 
 export const formatFieldValue = (value: unknown): string => {
   if (value === undefined) return '—'
```

### H02 @@ -11,7 +49,28 @@

裁决：保留纯新增及其契约配套。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -11,7 +49,28 @@
 // Compare every returned field, including fields added by future adapters.
 export const fieldChanges = (change: Change) => {
   const before = change.before || {}
-  const after = change.after || {}
+  // This Owner returns the formal 17-column relationship alongside separate complete source evidence.
+  const relation = change.after?.relation
+  const item = change.after?.item
+  const relationship = change.after?.relationship
+  const contractResult = change.object === 'pm_project_contract' && isRecord(relation)
+  const dictionaryResult = change.object === 'fnd_basic_data' && isRecord(item)
+  const relationshipResult = change.object === 'fb_shipment_barcode_relation' && isRecord(relationship)
+  const after = contractResult
+    ? relation
+    : dictionaryResult
+      ? item
+      : relationshipResult
+        ? relationship
+        : ['fnd_basic_data', 'fb_shipment_barcode_relation'].includes(change.object) && change.action === 'SKIPPED'
+        ? {}
+        : change.after || {}
+  const present = (field: string, value: unknown) =>
+    (contractResult || dictionaryResult || relationshipResult) &&
+    ['createTime', 'updateTime', 'effectiveFrom', 'effectiveTo'].includes(field) &&
+    typeof value === 'number'
+      ? formatDate(value, 'YYYY-MM-DD HH:mm:ss.SSS')
+      : formatFieldValue(value)
   const allFields = [...new Set([...Object.keys(before), ...Object.keys(after)])]
   // Generic tasks retain raw evidence alongside qualified target snapshots. Compare the targets only.
   const fields = Array.isArray(after._targets)
```

### H03 @@ -19,14 +78,14 @@

裁决：保留纯新增及其契约配套。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -19,14 +78,14 @@
     : allFields
   return fields.map((field) => ({
     field,
-    before: formatFieldValue(before[field]),
+    before: present(field, before[field]),
     after:
       after._previewVirtualIds &&
       field.endsWith('_id') &&
       typeof after[field] === 'number' &&
       after[field] < 0
         ? '待生成（预览）'
-        : formatFieldValue(after[field]),
+        : present(field, after[field]),
     changed: JSON.stringify(before[field]) !== JSON.stringify(after[field])
   }))
 }
```

## 109 views/pms/integration/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留纯新增及其契约配套**。新增直写/基础同步、产品目录、证据复用和中断恢复；原分页与流式运行展示按实际服务端回执区分。不是测试环境修复，不宣称已执行迁移验收。
- 证据及验证边界：对应integration接口与组件；本轮不触发运行。

### H01 @@ -7,6 +7,9 @@

裁决：保留纯新增及其契约配套。当前行来源：b143cfe890dda1ef51f21d34abb75a5b5fd43ee9。

```diff
@@ -7,6 +7,9 @@
       <el-tab-pane label="迁移与同步任务" name="tasks"
         ><TaskPanel v-if="active === 'tasks'" @open-run="openRun"
       /></el-tab-pane>
+      <el-tab-pane label="基础同步任务" name="base-sync" lazy>
+        <BaseSyncPanel />
+      </el-tab-pane>
       <el-tab-pane label="运行记录" name="runs"
         ><RunPanel v-if="active === 'runs'" :run-id="runId" @selected="openRun" @back="closeRun"
       /></el-tab-pane>
```

### H02 @@ -19,13 +22,14 @@

裁决：保留纯新增及其契约配套。当前行来源：b143cfe890dda1ef51f21d34abb75a5b5fd43ee9。

```diff
@@ -19,13 +22,14 @@
 <script setup lang="ts">
 import ConnectionPanel from './ConnectionPanel.vue'
 import TaskPanel from './TaskPanel.vue'
+import BaseSyncPanel from './BaseSyncPanel.vue'
 import RunPanel from './RunPanel.vue'
 import MappingPanel from './MappingPanel.vue'
 import { replaceIntegrationUrlState } from './urlState'
 import type { Id } from '@/api/pms/integration'
 defineOptions({ name: 'PmsDataIntegration' })
 const route = useRoute()
-const workspaceTabs = new Set(['connections', 'tasks', 'runs', 'mappings'])
+const workspaceTabs = new Set(['connections', 'tasks', 'runs', 'mappings', 'base-sync'])
 const initialTab = String(route.query.tab || 'tasks')
 const activeState = ref(workspaceTabs.has(initialTab) ? initialTab : 'tasks')
 const runId = ref<Id | undefined>(route.query.run ? String(route.query.run) : undefined)
```

## 110 views/pms/platform/deliverables/index.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。独立默认交付件归集入口，仍走已有Owner权限与引用保护。
- 证据及验证边界：ProjectBusiness新增交付宿主。

### H01 @@ -0,0 +1,19 @@

裁决：保留纯新增功能。当前行来源：ac017997acbe3cfdac2d6f3c5b9d1f5b59aa0f83。

```diff
@@ -0,0 +1,19 @@
+<template>
+  <ContentWrap>
+    <h3>项目交付件归集</h3>
+    <el-form inline>
+      <el-form-item label="项目"><PmsEntitySelect v-model="projectId" :api="ProjectApi.getProjectPage" label-field="projectName" value-field="id" query-field="projectName" /></el-form-item>
+      <el-form-item label="交付件类型"><el-input v-model="deliverableType" clearable aria-label="归集交付件类型" /></el-form-item>
+    </el-form>
+    <DefaultDeliveryRecords v-if="projectId" :project-id="projectId" :deliverable-type="deliverableType || undefined" readonly />
+    <el-empty v-else description="选择项目查看归集记录" />
+  </ContentWrap>
+</template>
+<script setup lang="ts">
+import { ref } from 'vue'
+import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
+import * as ProjectApi from '@/api/pms/project/projects'
+import DefaultDeliveryRecords from '@/components/BusinessEntity/DefaultDeliveryRecords.vue'
+defineOptions({ name: 'DefaultBusinessDeliverables' })
+const projectId = ref<string | number>(), deliverableType = ref('')
+</script>
```

## 111 views/pms/project/inheritance/detail/DeliveryModuleTable.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：4；当前片段：4。
- 裁决：**保留新增范围入口依赖**。交付范围完整页必须带项目/范围版本，无上下文不导航；不扩大查询。
- 证据及验证边界：commerceInteraction上下文契约。

### H01 @@ -8,7 +8,7 @@

裁决：保留新增范围入口依赖。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -8,7 +8,7 @@
         <el-button v-if="config.create" type="primary" size="small" @click="openCreate">
           <Icon icon="ep:plus" /> 新增
         </el-button>
-        <el-button v-if="config.path" link type="primary" @click="goPage(config.path)">
+        <el-button v-if="config.path" link type="primary" :disabled="config.path === '/customer-asset/delivery-scopes' && !projectContext" @click="goPage(config.path)">
           前往完整页面
         </el-button>
       </div>
```

### H02 @@ -135,6 +135,7 @@

裁决：保留新增范围入口依赖。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -135,6 +135,7 @@
 import { ElMessageBox } from 'element-plus'
 import { formatDate } from '@/utils/formatTime'
 import { useMessage } from '@/hooks/web/useMessage'
+import type { ProjectRouteContext } from '@/views/pms/commerce/commerceInteraction'
 
 defineOptions({ name: 'DeliveryModuleTable' })
```

### H03 @@ -180,7 +181,7 @@

裁决：保留新增范围入口依赖。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -180,7 +181,7 @@
   canDelete?: (row: any) => boolean
 }
 
-const props = defineProps<{ config: DeliveryModuleConfig; projectId: number }>()
+const props = defineProps<{ config: DeliveryModuleConfig; projectId: number; projectContext?: ProjectRouteContext }>()
 
 const router = useRouter()
 const loading = ref(false)
```

### H04 @@ -342,7 +343,13 @@

裁决：保留新增范围入口依赖。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -342,7 +343,13 @@
   detailVisible.value = true
 }
 
-const goPage = (path: string) => router.push(path)
+const goPage = (path: string) => {
+  if (path === '/customer-asset/delivery-scopes') {
+    if (!props.projectContext) return
+    return router.push({ path, query: { ...props.projectContext } })
+  }
+  return router.push(path)
+}
 </script>
 
 <style lang="scss" scoped>
```

## 112 views/pms/project/inheritance/detail/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：2；当前片段：2。
- 裁决：**保留新增范围入口依赖**。只增加projectContext传递和空行，旧业务页内容不变。
- 证据及验证边界：DeliveryModuleTable上下文契约。

### H01 @@ -291,6 +291,7 @@

裁决：保留新增范围入口依赖。当前行来源：b5599b9059f1916b2d16c8bb0bdefdb6055b3f86。

```diff
@@ -291,6 +291,7 @@
           :project="detail"
           @changed="handleContactsChanged"
         />
+
 
         <ProjectAttributePanel
           v-if="detail?.id && activeTab === 'attributes'"
```

### H02 @@ -496,6 +497,7 @@

裁决：保留新增范围入口依赖。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -496,6 +497,7 @@
           <DeliveryModuleTable
             :config="deliveryModules['equipment']"
             :project-id="detail.id"
+            :project-context="scopeContext"
           />
         </div>
         <!-- Demo 1.1.1 序列号详情 / Demo 1.1.1.1 配置Log（配置驱动，消费设备域真实 API） -->
```

## 113 views/pms/project/inheritance/members/OrdinaryMemberForm.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留历史兼容**。无userId历史行不查询人员候选；原编辑权限不变。
- 证据及验证边界：成员历史与候选查询既有测试。

### H01 @@ -113,8 +113,7 @@

裁决：保留历史兼容。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -113,8 +113,7 @@
 }
 const more = () => search(keyword, true)
 onMounted(async () => {
-  if (!props.member) return
-  if (isExternal.value || !candidateReady.value) return
+  if (!props.member || props.member.userId == null || isExternal.value || !candidateReady.value) return
   const request = ++sequence
   searching.value = true
   try {
```

## 114 views/pms/project/inheritance/members/ProjectMembersPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：3；当前片段：3。
- 裁决：**保留新增历史事实保护**。新增人员编码、授权标记；迁移历史未确认授权或无用户ID不得成为可维护有效成员，不扩大权限。
- 证据及验证边界：ProjectMembersPanel.runtime.spec.ts; OrdinaryProjectMemberService历史授权规则。

### H01 @@ -28,6 +28,7 @@

裁决：保留新增历史事实保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -28,6 +28,7 @@
       <el-table-column label="人员" min-width="110"><template #default="{ row }">
         {{ row.memberName || '姓名未记录' }}
       </template></el-table-column>
+      <el-table-column label="人员编码" min-width="100"><template #default="{ row }">{{ row.memberCode || row.employeeNo || '—' }}</template></el-table-column>
       <el-table-column label="单位" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.companyName || '—' }}</template></el-table-column>
       <el-table-column label="部门" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ row.departmentName || '—' }}</template></el-table-column>
       <el-table-column label="职务" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ row.title || '—' }}</template></el-table-column>
```

### H02 @@ -35,6 +36,7 @@

裁决：保留新增历史事实保护。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -35,6 +36,7 @@
       <el-table-column label="联系电话" min-width="125"><template #default="{ row }">{{ row.mobile || '—' }}</template></el-table-column>
       <el-table-column label="邮箱" min-width="155" show-overflow-tooltip><template #default="{ row }">{{ row.email || '—' }}</template></el-table-column>
       <el-table-column label="主责" width="70" align="center"><template #default="{ row }">{{ primaryLabel(row) }}</template></el-table-column>
+      <el-table-column label="授权" width="100"><template #default="{ row }">{{ row.authorizationConfirmed === false ? '未确认授权' : '已确认' }}</template></el-table-column>
       <el-table-column label="生效时间" min-width="155"><template #default="{ row }">{{ time(row.effectiveFrom) }}</template></el-table-column>
       <el-table-column label="失效时间" min-width="155"><template #default="{ row }">{{ time(row.effectiveTo) }}</template></el-table-column>
       <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }">
```

### H03 @@ -106,13 +108,15 @@

裁决：保留新增历史事实保护。当前行来源：LOCAL_UNCOMMITTED, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -106,13 +108,15 @@
 const editable = computed(() => !props.readonly && props.project.lifecycleStatus === 'ACTIVE' && !loading.value && !opening.value && !error.value)
 const removalSubmission = createSubmissionIdempotencyState()
 let sequence = 0
-const canMaintain = (row: Members.MemberRecord) => Members.memberRoleOptions.some(role =>
+const canMaintain = (row: Members.MemberRecord) => (row.memberRole === memberRoles.END_USER
+  || row.authorizationConfirmed !== false && row.userId != null) && Members.memberRoleOptions.some(role =>
   role.value === Members.logicalMemberRole(row.memberRole) && checkPermi([role.permission]))
-const isCurrent = (row: Members.MemberRecord) => row.status === 'ACTIVE'
+const isCurrent = (row: Members.MemberRecord) => (row.memberRole === memberRoles.END_USER || row.authorizationConfirmed !== false) && row.status === 'ACTIVE'
   && (!row.effectiveFrom || new Date(row.effectiveFrom).getTime() <= Date.now())
   && (!row.effectiveTo || new Date(row.effectiveTo).getTime() > Date.now())
 const time = (value?: Date | null) => value ? formatDate(value) : '—'
 const primaryLabel = (row: Members.MemberRecord) => {
+  if (row.authorizationConfirmed === false) return '—'
   if (row.memberRole === memberRoles.PROJECT_MANAGER) return isCurrent(row)
     ? (String(row.userId) === String(managers.value?.primaryUserId) ? '是' : '否') : '—'
   return Members.logicalMemberRole(row.memberRole) === memberRoles.SERVICE_MANAGER
```

## 115 views/pms/project/inheritance/projects/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：11；当前片段：11。
- 裁决：**保留新增接口依赖及测试修复**。与正式项目创建保持相同来源锁定输入，保留显式客户修复；不得只恢复控件后提交缺少指纹的数据。
- 证据及验证边界：同项目创建来源契约；既有创建测试。

### H01 @@ -262,11 +262,11 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -262,11 +262,11 @@
           <el-row :gutter="16">
             <el-col :span="12">
               <el-form-item label="项目名称" prop="projectName">
-                <el-input v-model="createForm.projectName" placeholder="某客户网络优化工程" />
+                <el-input :readonly="!!createForm.contractId" v-model="createForm.projectName" placeholder="某客户网络优化工程" />
               </el-form-item>
             </el-col>
             <el-col :span="12">
-              <el-form-item label="客户主档" prop="customerCode">
+              <el-form-item label="最终客户主档" prop="customerCode">
                 <PmsEntitySelect v-model="createForm.customerCode" :api="getSelectableCustomers"
                   :label-field="['code', 'name']" value-field="code" query-field="keyword"
                   placeholder="按客户名称或编码选择" @change="selectCustomer" />
```

### H02 @@ -281,10 +281,14 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c, 86425d8bcb278b353727652bc6704ccf36bbf9cd。

```diff
@@ -281,10 +281,14 @@
             </el-col>
             <el-col :span="12">
               <el-form-item label="合同主档" prop="contractNo">
-                <PmsEntitySelect v-model="createForm.contractNo" :api="getSelectableContracts"
-                  :label-field="['contractNo', 'customerName', 'companyName']" value-field="contractNo"
+                <PmsEntitySelect v-model="createForm.contractId" :api="getSelectableContracts"
+                  :label-field="['contractNo', 'customerName', 'companyName']" value-field="id"
                   query-field="keyword" placeholder="按合同号选择" @change="selectContract" />
               </el-form-item>
+            </el-col>
+            <el-col v-if="createForm.contractId" :span="24">
+              <ProjectCreationSource :source="creationSource" :loading="creationSourceLoading" :error="sourceError"
+                :sales-order-id="createForm.salesOrderId" @select-order="selectSalesOrder" />
             </el-col>
           </el-row>
           <el-row :gutter="16">
```

### H03 @@ -917,6 +921,8 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -917,6 +921,8 @@
 </template>
 
 <script setup lang="ts">
+import ProjectCreationSource from '@/views/pms/project/projects/ProjectCreationSource.vue'
+import { useProjectCreationSource } from '@/views/pms/project/projects/useProjectCreationSource'
 import { useCreationTemplateMatch } from "@/views/pms/project/projects/useCreationTemplateMatch"
 /**
  * F-PM01 项目手工创建（PM-01）—— 新链页面（复数路由 /pms/projects）
```

### H04 @@ -953,7 +959,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -953,7 +959,7 @@
 import ProjectMembersPanel from '../members/ProjectMembersPanel.vue'
 import { matchCustomerSelectedTemplates, createCustomerSelectedProject, type CustomerSelectedProjectCreate } from '@/api/pms/project/customer-selected'
 import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
-import { getSelectableContracts, type SelectedContract } from '@/views/pms/project/projects/contractSelection'
+import { getSelectableContracts } from '@/views/pms/project/projects/contractSelection'
 import ProjectStatusTag from '@/views/pms/project/projects/ProjectStatusTag.vue'
 import { closedProjectStatuses } from '@/views/pms/project/projects/projectStatus'
```

### H05 @@ -1102,6 +1108,9 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1102,6 +1108,9 @@
   customerCode: '',
   customerName: '',
   contractNo: '',
+  contractId: undefined as number | undefined,
+  salesOrderId: undefined as number | undefined,
+  sourceFingerprint: undefined as string | undefined,
   orderOfficeCompanyId: undefined as number | undefined,
   orderOfficeDepartmentId: undefined as number | undefined,
   locationMode: 'sites' as 'sites' | 'fallback',
```

### H06 @@ -1121,16 +1130,13 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1121,16 +1130,13 @@
   createForm.customerName = customer && customer.code === code ? customer.name : ''
 }
 
-/** 选择合同主档后跟随合同带入客户主档信息；合同号即选择值本身。 */
-const selectContract = (no: unknown, contract?: SelectedContract) => {
-  if (!contract || contract.contractNo !== no) return
-  if (!contract.customerCode) return
-  createForm.customerCode = contract.customerCode
-  createForm.customerName = contract.customerName ?? contract.customerCode
-}
+const { creationSource, creationSourceLoading, sourceError, sourceReady, selectContract, selectSalesOrder, resetSource } =
+  useProjectCreationSource(createForm, companies, departments)
 
 const createRules = {
-  customerCode: [{ required: true, message: '请选择客户主档', trigger: 'change' }],
+  customerCode: [{ validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
+    callback(value?.trim() ? undefined : new Error('请选择最终客户主档'))
+  }, trigger: 'change' }],
   projectName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
   orderOfficeCompanyId: [{ required: true, message: '请选择下单公司', trigger: 'change' }],
   orderOfficeDepartmentId: [{ required: true, message: '请选择下单办事处', trigger: 'change' }],
```

### H07 @@ -1166,11 +1172,15 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1166,11 +1172,15 @@
 
 const openWizard = () => {
   wizardStep.value = 0
+  resetSource()
   Object.assign(createForm, {
     projectName: '',
     customerCode: '',
     customerName: '',
     contractNo: '',
+    contractId: undefined,
+    salesOrderId: undefined,
+    sourceFingerprint: undefined,
     orderOfficeCompanyId: undefined,
     orderOfficeDepartmentId: undefined,
     locationMode: 'sites',
```

### H08 @@ -1190,6 +1200,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1190,6 +1200,7 @@
 }
 
 const wizardNext0 = async () => {
+  if (!sourceReady.value) { message.error('请先选择关联销售订单并加载来源'); return }
   await wizardFormRef.value?.validate()
   if (createForm.locationMode === 'sites') {
     if (createForm.sites.some((item) => !item.siteId || item.siteVersion === undefined)) {
```

### H09 @@ -1210,6 +1221,9 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1210,6 +1221,9 @@
 
 // ============ 模板匹配（步骤②） ============
 const { matchLoading, matchResult, selectedTemplateRevisionId, runMatch } = useCreationTemplateMatch(() => ({
+  contractId: createForm.contractId,
+  salesOrderId: createForm.salesOrderId,
+  sourceFingerprint: createForm.sourceFingerprint,
   projectName: createForm.projectName,
   customerCode: createForm.customerCode || undefined,
   orderOfficeCompanyId: createForm.orderOfficeCompanyId!,
```

### H10 @@ -1276,6 +1290,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1276,6 +1290,7 @@
 
 // ============ 提交创建（步骤③） ============
 const submitCreate = async () => {
+  if (!sourceReady.value) { message.error('请重新加载合同订单来源'); return }
   const sites: ProjectSiteReqVO[] | undefined =
     createForm.locationMode === 'sites'
       ? createForm.sites.map((item, index) => ({
```

### H11 @@ -1288,6 +1303,9 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1288,6 +1303,9 @@
     projectName: createForm.projectName,
     customerCode: createForm.customerCode,
     contractNo: createForm.contractNo || undefined,
+    contractId: createForm.contractId,
+    salesOrderId: createForm.salesOrderId,
+    sourceFingerprint: createForm.sourceFingerprint,
     orderOfficeCompanyId: createForm.orderOfficeCompanyId!,
     orderOfficeDepartmentId: createForm.orderOfficeDepartmentId!,
     sites,
```

## 116 views/pms/project/project-master-detail/components/PreparationItemDrawer.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。文件失效时给出错误，避免空引用触发异常。
- 证据及验证边界：工前准备文件测试。

### H01 @@ -317,6 +317,7 @@

裁决：保留文件兼容。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -317,6 +317,7 @@
   const artifact = await FileApi.getArtifact(selection.artifactId, {
     ...businessKey
   })
+  if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
   let cursor: string | undefined
   let version: FileApi.FileVersionVO | undefined
   do {
```

## 117 views/pms/project/project-master-detail/components/ProjectAssociatedDataPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。新增产品、回款计划、设备许可等只读来源事实，保持服务端授权与历史只读。
- 证据及验证边界：associated-data客户端/对应服务端；ProjectBaseInfo既有测试。

### H01 @@ -0,0 +1,143 @@

裁决：保留纯新增功能。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,143 @@
+<template>
+  <section :aria-label="title" data-testid="project-associated-data">
+    <div class="associated-heading"
+      ><h4>{{ title }}</h4><el-button @click="refresh++">刷新</el-button></div
+    >
+    <el-tabs :model-value="kind" @tab-change="selectKind">
+      <el-tab-pane
+        v-for="item in visibleKinds"
+        :key="item.kind"
+        :name="item.kind"
+        :label="item.label"
+      />
+    </el-tabs>
+    <div v-loading="loading" :aria-busy="loading">
+      <el-alert v-if="error" title="项目资料加载失败，请重试。" type="error" :closable="false">
+        <el-button @click="refresh++">重新加载</el-button>
+      </el-alert>
+      <template v-else>
+        <el-table
+          :data="rows"
+          row-key="id"
+          :empty-text="`暂无${currentLabel || title}`"
+          @row-dblclick="selected = $event"
+        >
+          <el-table-column
+            v-for="field in columns"
+            :key="field.key"
+            :label="field.label"
+            min-width="160"
+            show-overflow-tooltip
+          >
+            <template #default="{ row }">{{
+              valueText(row.values[field.key], field.key)
+            }}</template>
+          </el-table-column>
+          <el-table-column label="操作" width="90" fixed="right"
+            ><template #default="{ row }">
+              <el-button link type="primary" @click="selected = row">详情</el-button>
+            </template></el-table-column
+          >
+        </el-table>
+        <el-pagination
+          v-model:current-page="pageNo"
+          :page-size="pageSize"
+          :total="total"
+          layout="prev,pager,next,total"
+        />
+      </template>
+    </div>
+    <el-drawer
+      :model-value="!!selected"
+      :title="currentLabel"
+      size="60%"
+      @close="selected = undefined"
+    >
+      <el-descriptions v-if="selected" :column="1" border>
+        <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
+          {{ valueText(selected.values[field.key], field.key) }}
+        </el-descriptions-item>
+      </el-descriptions>
+    </el-drawer>
+  </section>
+</template>
+<script setup lang="ts">
+import { computed, ref, watch } from 'vue'
+import { formatSourceDate } from '@/utils/formatSourceDate'
+import {
+  associatedFields,
+  associatedKinds,
+  getAssociatedData,
+  type AssociatedKind,
+  type AssociatedRow
+} from '@/api/pms/project/associated-data'
+import { legacyValueText, type LegacyId } from '@/api/pms/project/legacy-detail'
+const valueText = (value: unknown, field: string) => {
+  if (field === 'allocationConfirmed' && typeof value === 'boolean') return value ? '是' : '否'
+  const formatted = formatSourceDate(value, field)
+  return legacyValueText(
+    formatted == null
+      ? null
+      : typeof formatted === 'object'
+        ? JSON.stringify(formatted)
+        : String(formatted)
+  )
+}
+const props = withDefaults(defineProps<{ projectId: LegacyId; title?: string; kinds?: readonly AssociatedKind[] }>(), { title: '系统项目资料' })
+const visibleKinds = computed(() => associatedKinds.filter(item => !props.kinds || props.kinds.includes(item.kind)))
+const kind = ref<AssociatedKind>(props.kinds?.[0] ?? 'products')
+const rows = ref<AssociatedRow[]>([]),
+  total = ref(0),
+  pageNo = ref(1),
+  refresh = ref(0)
+const pageSize = 20
+const loading = ref(false),
+  error = ref(false),
+  selected = ref<AssociatedRow>()
+const fields = computed(() => associatedFields[kind.value])
+const columns = computed(() => fields.value.slice(0, 7))
+const currentLabel = computed(() => visibleKinds.value.find((x) => x.kind === kind.value)?.label)
+const selectKind = (name: string | number) => {
+  if (!visibleKinds.value.some((x) => x.kind === name)) return
+  pageNo.value = 1
+  kind.value = name as AssociatedKind
+}
+watch(
+  [() => props.projectId, kind, pageNo, refresh],
+  async ([projectId, category, page], _previous, onCleanup) => {
+    let current = true
+    onCleanup(() => {
+      current = false
+    })
+    loading.value = true
+    error.value = false
+    rows.value = []
+    if (projectId !== _previous?.[0] || category !== _previous?.[1]) total.value = 0
+    selected.value = undefined
+    try {
+      const result = await getAssociatedData(projectId, category, page, pageSize)
+      if (current) {
+        rows.value = result.list
+        total.value = result.total
+      }
+    } catch {
+      if (current) error.value = true
+    } finally {
+      if (current) loading.value = false
+    }
+  },
+  { immediate: true }
+)
+</script>
+<style scoped>
+.associated-heading {
+  display: flex;
+  align-items: center;
+  justify-content: space-between;
+  gap: 1rem;
+}
+.el-pagination {
+  margin-top: 1rem;
+  justify-content: flex-end;
+}
+</style>
```

## 118 views/pms/project/project-master-detail/components/ProjectBaseInfo.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：9；当前片段：9。
- 裁决：**部分恢复**。恢复显式项目相关方为事实来源，取消执行单替代值；保留独立合同关系、回款、许可信息，以及慢请求分离、失败重试。
- 证据及验证边界：ProjectBaseInfo.runtime.spec.ts; projectBaseInfoContracts.runtime.spec.ts。

### H01 @@ -4,16 +4,24 @@

裁决：部分恢复。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa, f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -4,16 +4,24 @@
       <dl class="fact-grid">
         <div v-for="field in baseFields" :key="field.label" class="fact" :class="{ 'fact-wide': field.wide }">
           <dt>{{ field.label }}</dt>
-          <dd>{{ field.value || '—' }}</dd>
+          <dd>
+            {{ field.value || '—' }}
+            <template v-if="field.label === '合同号'">
+              <span v-if="loading" role="status" class="text-[var(--el-text-color-secondary)]">合同信息加载中…</span>
+              <el-button v-if="error || relationError" link type="primary" @click="load">合同信息加载失败，重试</el-button>
+            </template>
+          </dd>
         </div>
       </dl>
     </section>
     <section v-if="checkRole(['admin'])" class="fact-section" v-loading="loading">
-      <div class="section-heading"><h3>合同与执行单</h3><el-button v-if="error" link type="primary" @click="load">重新加载</el-button></div>
+      <div class="section-heading"><h3>合同与执行单</h3><el-button v-if="error || relationError || marketError" link type="primary" @click="load">重新加载</el-button></div>
       <el-alert v-if="error" :title="error" type="warning" :closable="false" />
-      <p v-else-if="!canReadCommerce" class="empty-note">暂无商务资料查看权限。</p>
+      <el-alert v-if="relationError" :title="relationError" type="warning" :closable="false" />
+      <el-alert v-if="marketError" :title="marketError" type="warning" :closable="false" />
+      <p v-if="!canReadCommerce" class="empty-note">暂无商务资料查看权限。</p>
       <template v-else>
-        <div v-for="contract in commerce?.contracts || []" :key="contract.id" class="source-record">
+        <div v-for="contract in contracts" :key="contract.id" class="source-record">
           <div class="record-heading"><el-tag effect="plain">合同</el-tag><strong>{{ contract.contractNo }}</strong><span>{{ contract.contractName }}</span></div>
           <dl class="fact-grid">
             <div class="fact"><dt>签约公司</dt><dd>{{ contract.companyName || contract.companyCode || '—' }}</dd></div>
```

### H02 @@ -21,10 +29,18 @@

裁决：部分恢复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -21,10 +29,18 @@
             <div class="fact"><dt>合同金额</dt><dd>{{ amount(contract.contractAmount, contract.currencyCode) }}</dd></div>
             <div class="fact"><dt>币种</dt><dd>{{ contract.currencyCode || '—' }}</dd></div>
           </dl>
+          <dl v-for="relation in contractRelations.filter(item => String(item.contractId) === String(contract.id))" :key="relation.id" class="fact-grid relation-facts">
+            <div class="fact"><dt>关联状态</dt><dd>{{ relation.status === 'HISTORICAL' ? '历史关联' : relation.status === 'ACTIVE' ? '有效关联' : relation.status }}</dd></div>
+            <div class="fact"><dt>关联开始时间</dt><dd>{{ time(relation.effectiveFrom) }}</dd></div>
+            <div class="fact"><dt>关联结束时间</dt><dd>{{ time(relation.effectiveTo) }}</dd></div>
+            <div class="fact"><dt>创建时间</dt><dd>{{ time(relation.createTime) }}</dd></div>
+            <div class="fact"><dt>更新时间</dt><dd>{{ time(relation.updateTime) }}</dd></div>
+          </dl>
         </div>
         <div v-for="order in commerce?.orders || []" :key="order.id" class="source-record">
           <div class="record-heading"><el-tag type="info" effect="plain">销售订单</el-tag><strong>{{ order.orderNo }}</strong></div>
           <dl class="fact-grid">
+            <div class="fact"><dt>订单购货方</dt><dd>{{ order.customerName || order.customerCode || '—' }}</dd></div>
             <div class="fact"><dt>订单创建时间</dt><dd>{{ time(order.orderCreateTime) }}</dd></div>
             <div class="fact"><dt>下单公司</dt><dd>{{ order.companyName || '—' }}</dd></div>
             <div class="fact"><dt>订单金额</dt><dd>{{ amount(order.orderAmount, order.currencyCode) }}</dd></div>
```

### H03 @@ -36,13 +52,27 @@

裁决：部分恢复。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, 341b7705a239267ce041ceee95ce20d96e14aa9c, 88049734dff6b5581a0158fba57b5430ca6d4e78, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -36,13 +52,27 @@
         <div v-for="order in commerce?.executionOrders || []" :key="order.id" class="source-record">
           <div class="record-heading"><el-tag type="info" effect="plain">执行单</el-tag><strong>{{ order.executionNo }}</strong></div>
           <dl class="fact-grid">
-            <div v-for="field in executionFields" :key="field.key" class="fact"><dt>{{ field.label }}</dt><dd>{{ field.dict ? label(order[field.key], field.dict) : order[field.key] || '—' }}</dd></div>
+            <div v-for="field in executionFields" :key="field.key" class="fact"><dt>{{ field.label }}</dt><dd>{{ field.dict ? label(order[field.key], field.dict) : order[field.key] ?? '—' }}</dd></div>
             <div class="fact"><dt>提交时间</dt><dd>{{ time(order.submitTime) }}</dd></div>
             <div class="fact"><dt>同步时间</dt><dd>{{ time(order.sourceSyncTime) }}</dd></div>
-          </dl>
-        </div>
-        <p v-if="!loading && !error && !commerce?.contracts.length && !commerce?.orders.length && !commerce?.executionOrders.length" class="empty-note">尚未关联商务资料；关联后展示合同、订单及执行单信息。</p>
+            <div class="fact"><dt>执行单来源</dt><dd>{{ order.sourceSystem || '—' }}</dd></div>
+          </dl>
+        </div>
+        <div v-for="item in commerce?.receivables || []" :key="item.id" class="source-record">
+          <div class="record-heading"><el-tag effect="plain">合同回款信息</el-tag><strong>{{ item.contractNo }}</strong></div>
+          <dl class="fact-grid">
+            <div class="fact"><dt>客户</dt><dd>{{ item.customerName || item.customerCode || '—' }}</dd></div>
+            <div class="fact"><dt>币种</dt><dd>{{ item.currencyName || '—' }}</dd></div>
+            <div v-for="field in receivableAmountFields" :key="field.key" class="fact"><dt>{{ field.label }}</dt><dd>{{ item[field.key] == null ? '—' : item[field.key] }}</dd></div>
+            <div v-for="field in receivableFields" :key="field.key" class="fact"><dt>{{ field.label }}</dt><dd>{{ field.time ? time(item[field.key]) : item[field.key] ?? '—' }}</dd></div>
+          </dl>
+        </div>
+        <p v-if="!loading && !error && !contracts.length && !commerce?.orders.length && !commerce?.executionOrders.length && !commerce?.receivables?.length" class="empty-note">尚未关联商务资料；关联后展示合同、订单及执行单信息。</p>
+        <ProjectAssociatedDataPanel v-if="project.id" :project-id="project.id" title="回款计划" :kinds="['plans']" />
       </template>
+    </section>
+    <section v-if="project.id" class="fact-section">
+      <ProjectAssociatedDataPanel :project-id="project.id" title="设备许可" :kinds="['licenses']" />
     </section>
     <section v-if="checkRole(['admin'])" class="fact-section">
       <h3>补充信息</h3>
```

### H04 @@ -61,33 +91,54 @@

裁决：部分恢复。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, 341b7705a239267ce041ceee95ce20d96e14aa9c, 9610d1ae72a31ba214992df6955f5db59273b725, LOCAL_UNCOMMITTED, e21f02d2d29385076edbaf1a776ad9743a7782fa, f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -61,33 +91,54 @@
 </template>
 
 <script setup lang="ts">
+import { computed, ref, watch, onBeforeUnmount } from 'vue'
 import request from '@/config/axios'
 import { checkPermi, checkRole } from '@/utils/permission'
 import { DICT_TYPE, getDictLabel } from '@/utils/dict'
 import { formatDate } from '@/utils/formatTime'
 import type { ProjectMasterVO } from '@/api/pms/project/projects'
 import { getMemberPage, type MemberRecord } from '@/api/pms/project/unified-members'
+import { getProjectContractRelations, type ProjectContractRelation } from '@/api/pms/commerce'
+import ProjectAssociatedDataPanel from './ProjectAssociatedDataPanel.vue'
 
 const props = defineProps<{ project: ProjectMasterVO }>()
 interface CommerceOverview {
-  contracts: { id: number; contractNo: string; contractName?: string; companyName?: string; companyCode?: string; customerName?: string; currencyCode?: string; contractAmount?: number | null }[]
-  orders: { id: number; orderNo: string; companyName?: string; orderCreateTime?: string; orderAmount?: number | null; currencyCode?: string; salesType?: string; customerRequiredTime?: string; executionNo?: string }[]
+  contracts: { id: number | string; contractNo: string; contractName?: string; companyName?: string; companyCode?: string; customerCode?: string; customerName?: string; currencyCode?: string; contractAmount?: number | null }[]
+  orders: { id: number; orderNo: string; customerCode?: string; customerName?: string; companyName?: string; orderCreateTime?: string; orderAmount?: number | null; currencyCode?: string; salesType?: string; customerRequiredTime?: string; executionNo?: string }[]
   executionOrders: ({ id: number; executionNo: string } & Record<string, any>)[]
+  receivables?: ({ id: number; contractNo: string } & Record<string, any>)[]
 }
 const commerce = ref<CommerceOverview>()
+const contractRelations = ref<ProjectContractRelation[]>([])
+const contracts = computed(() => {
+  const result = [...(commerce.value?.contracts || [])]
+  for (const relation of contractRelations.value) {
+    if (!result.some(contract => String(contract.id) === String(relation.contractId))) {
+      result.push({ id: relation.contractId, contractNo: relation.contractNo,
+        contractName: relation.contractName, companyCode: relation.companyCode, companyName: relation.companyName })
+    }
+  }
+  return result
+})
 type MarketSource = Pick<ProjectMasterVO, 'marketName' | 'marketCode' | 'systemName' | 'systemCode' | 'expendName' | 'expendCode' | 'industryName' | 'industryCode'>
 const scopedMarketSources = ref<MarketSource[]>([])
 const marketPath = (source: MarketSource) => [source.marketName || source.marketCode, source.systemName || source.systemCode, source.expendName || source.expendCode, source.industryName || source.industryCode].filter(Boolean).join(' / ')
 const members = ref<MemberRecord[]>([])
 const loading = ref(false)
 const error = ref('')
+const relationError = ref('')
+const marketError = ref('')
 const memberError = ref(false)
 const canReadCommerce = computed(() => checkPermi(['pms:commerce:contract:query']))
 const label = (value: string | null | undefined, dict: DICT_TYPE) => value ? getDictLabel(dict, value) || value : '—'
 const time = (value: any) => value ? formatDate(value) : '—'
 const amount = (value?: number | null, currency?: string) => value == null ? '—' : `${currency || ''} ${value}`.trim()
 const names = (roles: string[], exclude?: number) => members.value.filter(m => roles.includes(m.memberRole) && m.userId !== exclude).map(m => m.memberName || m.employeeNo).filter(Boolean).join('、') || (memberError.value ? '人员信息加载失败' : '—')
-const party = (role: string) => props.project.parties?.filter(p => p.role === role).map(p => p.name || p.code).filter(Boolean).join('、') || '—'
+const party = (role: string) => {
+  const roles = role === 'AGENT' ? ['AGENT', '30'] : [role]
+  const assigned = [...new Set(props.project.parties?.filter(p => roles.includes(p.role)).map(p => p.name || p.code).filter(Boolean) || [])].join('、')
+  return assigned || '—'
+}
 interface FactField { label: string; value?: string | null; wide?: boolean }
 const salesRepresentative = computed(() => {
   const assigned = names(['SALES_REPRESENTATIVE'])
```

### H05 @@ -101,7 +152,7 @@

裁决：部分恢复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -101,7 +152,7 @@
   const orderCreated = orders.map(order => orders.length > 1
     ? `${order.orderNo}：${time(order.orderCreateTime)}` : time(order.orderCreateTime)).join('\n')
   return [
-    { label: '合同号', value: [...new Set([p.contractNo, ...(commerce.value?.contracts || []).map(contract => contract.contractNo)].filter(Boolean))].join('、') },
+    { label: '合同号', value: [...new Set([p.contractNo, ...contracts.value.map(contract => contract.contractNo)].filter(Boolean))].join('、') },
     { label: '销售代表', value: salesRepresentative.value },
     { label: '办事处', value: p.departmentName || p.departmentCode },
     { label: '所属公司', value: p.companyName || p.companyCode },
```

### H06 @@ -122,6 +173,23 @@

裁决：部分恢复。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -122,6 +173,23 @@
     { label: '不予跟踪原因', value: p.notTrackReason, wide: true }
   ]
 })
+const receivableAmountFields = [
+  { key: 'contractAmount', label: '合同金额' }, { key: 'deliveredAmount', label: '发货金额' },
+  { key: 'collectedAmount', label: '已回款金额' }, { key: 'collectedRatio', label: '回款比例' },
+  { key: 'receivableAmount', label: '应收金额' }, { key: 'overdueAmount', label: '逾期金额' }
+]
+const receivableFields = [
+  { key: 'projectName', label: '合同项目名称' }, { key: 'projectCode', label: '合同项目编码' },
+  { key: 'orderNo', label: '销售订单号' }, { key: 'marketName', label: '市场' },
+  { key: 'departmentName', label: '办事处' }, { key: 'industryName', label: '行业' },
+  { key: 'marketingRepresentativeName', label: '销售代表' }, { key: 'marketingRepresentativeCode', label: '销售代表编码' },
+  { key: 'secondaryRepresentativeCode', label: '第二销售代表编码' }, { key: 'marketCode', label: '市场编码' },
+  { key: 'departmentCode', label: '办事处编码' }, { key: 'industryCode', label: '行业编码' },
+  { key: 'systemSourceKey', label: '系统编码' }, { key: 'expendSourceKey', label: '拓展编码' },
+  { key: 'contractCreateTime', label: '合同创建日期', time: true }, { key: 'latestShipTime', label: '最近发货日期', time: true },
+  { key: 'effectiveFrom', label: '生效日期', time: true }, { key: 'effectiveTo', label: '失效日期', time: true },
+  { key: 'batchCode', label: '批次' }, { key: 'importBatchNo', label: '导入批次' }, { key: 'dataSource', label: '数据来源' }
+]
 const executionFields: { key: string; label: string; dict?: DICT_TYPE }[] = [
   { key: 'salesRepName', label: '销售代表' }, { key: 'departmentName', label: '办事处' },
   { key: 'marketName', label: '市场' }, { key: 'systemName', label: '系统' },
```

### H07 @@ -139,9 +207,10 @@

裁决：部分恢复。当前行来源：d1dcdfc3c63276c718d307517222c861332e902f, f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -139,9 +207,10 @@
 const load = async () => {
   const id = props.project.id
   const current = ++requestId
-  commerce.value = undefined; scopedMarketSources.value = []; members.value = []; error.value = ''; memberError.value = false
+  commerce.value = undefined; contractRelations.value = []; scopedMarketSources.value = []; members.value = []; error.value = ''; memberError.value = false
+  relationError.value = ''; marketError.value = ''
   if (!id) return
-  loading.value = true
+  loading.value = canReadCommerce.value
   await Promise.all([
     (async () => {
       if (!canReadCommerce.value) return
```

### H08 @@ -149,12 +218,29 @@

裁决：部分恢复。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa, f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -149,12 +218,29 @@
         const value = await request.get<CommerceOverview>({ url: `/api/v1/pms/contracts/project/${id}/overview` })
         if (current !== requestId) return
         commerce.value = value
+        loading.value = false
         if (!marketPath(props.project)) {
-          const executionNos = new Set(value.orders.map(order => order.executionNo).filter(Boolean))
-          const sources = await Promise.all(value.contracts.map(contract => request.get<{ executionOrders: (MarketSource & { executionNo: string })[] }>({ url: `/api/v1/pms/contracts/${contract.id}/creation-source` })))
-          if (current === requestId) scopedMarketSources.value = sources.flatMap(source => source.executionOrders).filter(execution => executionNos.has(execution.executionNo))
+          try {
+            const executionNos = new Set(value.orders.map(order => order.executionNo).filter(Boolean))
+            const sources = await Promise.all(value.contracts.map(contract => request.get<{ executionOrders: (MarketSource & { executionNo: string })[] }>({ url: `/api/v1/pms/contracts/${contract.id}/creation-source` })))
+            if (current === requestId) scopedMarketSources.value = sources.flatMap(source => source.executionOrders).filter(execution => executionNos.has(execution.executionNo))
+          } catch { if (current === requestId) marketError.value = '市场补充信息加载失败，请重试。' }
         }
       } catch { if (current === requestId) error.value = '商务资料加载失败，请重试。' }
+      finally { if (current === requestId) loading.value = false }
+    })(),
+    (async () => {
+      if (!canReadCommerce.value) return
+      try {
+        const relations: ProjectContractRelation[] = []
+        for (let pageNo = 1; ; pageNo++) {
+          const page = await getProjectContractRelations(id, { pageNo, pageSize: 100 })
+          if (current !== requestId) return
+          relations.push(...page.list)
+          if (relations.length >= page.total || !page.list.length) break
+        }
+        contractRelations.value = relations
+      } catch { if (current === requestId) relationError.value = '合同关联信息加载失败，请重试。' }
     })(),
     (async () => {
       try {
```

### H09 @@ -192,6 +278,7 @@

裁决：部分恢复。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -192,6 +278,7 @@
 .record-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 18px; font-size: 14px; }
 .record-heading span { color: var(--el-text-color-secondary); }
 .section-heading { display: flex; justify-content: space-between; align-items: baseline; }
+.relation-facts { margin-top: 22px; }
 .empty-note { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); line-height: 22px; }
 @media (max-width: 600px) { .project-facts { padding: 0 4px; } .fact-grid { gap: 18px; } .source-record { padding: 16px; } }
 </style>
```

## 119 views/pms/project/project-master-detail/components/ProjectDeviceConfigLogDrawer.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：True；本轮前片段：4；当前片段：4。
- 裁决：**保留测试修复**。文件归档与配置记录分别加载，旧项目/旧设备响应不能覆盖新选择；失败独立可重试。
- 证据及验证边界：ProjectDeviceConfigLogDrawer.runtime.spec.ts。

### H01 @@ -8,6 +8,7 @@

裁决：保留测试修复。当前行来源：f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -8,6 +8,7 @@
   >
     <div data-testid="device-config-log-drawer">
       <el-button :loading="loading" class="mb-4" @click="load">刷新</el-button>
+      <p v-if="pendingSources" role="status">正在加载其余配置日志…</p>
       <el-alert
         v-for="error in errors"
         :key="error"
```

### H02 @@ -78,6 +79,7 @@

裁决：保留测试修复。当前行来源：f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -78,6 +79,7 @@
 const deviceId = ref<number>()
 const serialNumber = ref('')
 const loading = ref(false)
+const pendingSources = ref(0)
 const errors = ref<string[]>([])
 interface LogRow {
   key: string
```

### H03 @@ -107,35 +109,48 @@

裁决：保留测试修复。当前行来源：f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -107,35 +109,48 @@
   parseStates.value = {}
   const canQueryArchive = checkPermi(['pms:device-log-archive:query'])
   const canQueryLegacy = checkPermi(['pms:device:query'])
-  const [archive, legacy] = await Promise.allSettled([
-    canQueryArchive ? ArchiveApi.history(props.projectId, currentDeviceId) : Promise.resolve([]),
-    canQueryLegacy ? DeviceApi.getConfigurationLogs(currentDeviceId) : Promise.resolve([])
-  ])
-  if (current !== sequence) return
+  pendingSources.value = Number(canQueryArchive) + Number(canQueryLegacy)
+  const projectId = props.projectId
   if (!canQueryArchive && !canQueryLegacy) errors.value.push('暂无配置日志查看权限')
-  if (archive.status === 'fulfilled') {
-    rows.value.push(
-      ...archive.value.map((file: ArchiveApi.LogFile) => ({
+  const sourceFinished = () => {
+    if (current !== sequence) return
+    --pendingSources.value
+    rows.value.sort((a, b) => new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime())
+    loading.value = rows.value.length === 0 && pendingSources.value > 0
+  }
+  await Promise.all([
+    canQueryArchive ? (async () => {
+      try {
+        const files = await ArchiveApi.history(projectId, currentDeviceId)
+        if (current !== sequence) return
+        rows.value.push(...files.map((file: ArchiveApi.LogFile) => ({
         key: `archive:${file.id}`,
         name: file.fileName,
         source: file.sourceType === 'MANUAL_UPLOAD' ? '手工上传' : '采集回传',
         time: file.archivedAt,
         file
-      }))
-    )
-  } else errors.value.push('设备归档日志加载失败，请刷新重试')
-  if (legacy.status === 'fulfilled') {
-    rows.value.push(
-      ...legacy.value.map((log: DeviceApi.DeviceConfigurationLogVO) => ({
+        })))
+      } catch {
+        if (current === sequence) errors.value.push('设备归档日志加载失败，请刷新重试')
+      } finally { sourceFinished() }
+    })() : Promise.resolve(),
+    canQueryLegacy ? (async () => {
+      try {
+        const logs = await DeviceApi.getConfigurationLogs(currentDeviceId)
+        if (current !== sequence) return
+        rows.value.push(...logs.map((log: DeviceApi.DeviceConfigurationLogVO) => ({
         key: `legacy:${log.id}`,
         name: log.configType || '配置日志',
         source: log.sourceSystem || '—',
         time: log.collectedAt,
         legacy: log
-      }))
-    )
-  } else errors.value.push('历史配置日志加载失败，请刷新重试')
-  rows.value.sort((a, b) => new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime())
+        })))
+      } catch {
+        if (current === sequence) errors.value.push('历史配置日志加载失败，请刷新重试')
+      } finally { sourceFinished() }
+    })() : Promise.resolve()
+  ])
+  if (current !== sequence) return
   loading.value = false
   await Promise.all(
     rows.value
```

### H04 @@ -179,6 +194,7 @@

裁决：保留测试修复。当前行来源：f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -179,6 +194,7 @@
   if (!value) {
     ++sequence
     loading.value = false
+    pendingSources.value = 0
     rows.value = []
     errors.value = []
     parseStates.value = {}
```

## 120 views/pms/project/project-master-detail/components/ProjectLegacyDetailPanel.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。迁移来源历史明细只读，不覆写现有项目或旧事实。
- 证据及验证边界：legacy-detail客户端与服务端。

### H01 @@ -0,0 +1,434 @@

裁决：保留纯新增功能。当前行来源：4d522dbc35e528ba385de0f257950b7250744251, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,434 @@
+<template>
+  <ContentWrap class="legacy-detail" data-testid="project-legacy-detail" v-loading="loading">
+    <ProjectAssociatedDataPanel :project-id="projectId" />
+    <el-divider />
+    <div class="panel-heading"
+      ><h3>DPPMS 关联资料</h3><el-button @click="refresh++">刷新资料</el-button></div
+    >
+    <el-alert
+      v-if="sourceError"
+      title="关联资料加载失败，请重试。"
+      type="error"
+      :closable="false"
+    />
+    <el-empty v-else-if="!loading && !source" description="尚未同步 DPPMS 关联资料" />
+    <template v-if="source">
+      <el-select v-if="sources.length > 1" v-model="sourceId" aria-label="选择来源项目">
+        <el-option
+          v-for="item in sources"
+          :key="String(item.id)"
+          :value="item.id"
+          :label="`${item.sourceContractNo} · 原项目 ${item.sourceProjectKey}`"
+        />
+      </el-select>
+      <p class="source-meta"
+        >原项目 {{ source.sourceProjectKey }} · 合同 {{ source.sourceContractNo }} · 资料读取时间
+        {{ source.sourceReadAt.replace('T', ' ') }}</p
+      >
+      <el-alert
+        title="以下为旧系统历史资料，原状态和人员关系按历史展示。"
+        description="业务承接正在复核，已有目标记录不代表承接完成。"
+        type="info"
+        :closable="false"
+      />
+      <p v-for="note in source.deferredDomains" :key="note" class="deferred-note">{{ note }}</p>
+      <p v-if="nativeReport" role="status" class="source-meta" data-testid="legacy-native-summary">
+        已有目标记录 {{ nativeReport.mappedCount }} 条 · 保留来源资料
+        {{ nativeReport.retainedCount }} 条
+        <span v-if="nativeReport.pendingCount"> · 待承接 {{ nativeReport.pendingCount }} 条</span>
+      </p>
+      <el-alert
+        v-if="nativeReportError"
+        title="业务承接状态加载失败，请刷新资料重试。"
+        type="error"
+        :closable="false"
+      />
+      <el-collapse v-if="nativeReport?.retainedCount" aria-label="保留历史的缺项">
+        <el-collapse-item title="查看保留历史的缺项及原因" name="retained">
+          <p
+            v-for="item in nativeReport.domains.filter((d) => d.retainedCount > 0)"
+            :key="item.domain"
+          >
+            {{ item.label }}（{{ item.retainedCount }} 条）：
+            {{
+              Object.entries(item.retentionReasons)
+                .map(([reason, count]) => `${reason}（${count}）`)
+                .join('；')
+            }}
+          </p>
+        </el-collapse-item>
+      </el-collapse>
+      <div class="group-tabs" role="group" aria-label="关联资料类别">
+        <button
+          v-for="name in groups"
+          :key="name"
+          :aria-pressed="group === name"
+          :class="{ selected: group === name }"
+          @click="selectGroup(name)"
+          >{{ name }}</button
+        >
+      </div>
+      <div class="domain-layout">
+        <nav aria-label="资料类型" class="domain-nav">
+          <button
+            v-for="item in groupDomains"
+            :key="item.code"
+            :class="{ selected: domainCode === item.code }"
+            :aria-current="domainCode === item.code ? 'true' : undefined"
+            @click="selectDomain(item.code)"
+          >
+            <span>{{ item.label }}</span
+            ><span>{{ source.counts[item.code] ?? 0 }}</span>
+          </button>
+        </nav>
+        <section class="records" v-loading="recordsLoading" :aria-label="domain?.label">
+          <div class="records-heading"
+            ><h4>{{ domain?.label }}</h4
+            ><span>共 {{ total }} 条</span></div
+          >
+          <p v-if="filter.sourceKey || filter.parentKey" class="filter-note"
+            >当前展示关联记录
+            <el-button link type="primary" @click="filter = {}">显示此类全部记录</el-button></p
+          >
+          <el-alert
+            v-if="recordError"
+            title="资料记录加载失败，请重试。"
+            type="error"
+            :closable="false"
+            ><el-button @click="recordRefresh++">重新加载</el-button></el-alert
+          >
+          <template v-else>
+            <el-alert
+              v-if="nativeRecordError"
+              title="业务承接状态暂不可用，历史资料仍可查看。"
+              type="warning"
+              :closable="false"
+            />
+            <el-table
+              :data="records"
+              row-key="sourceKey"
+              empty-text="旧系统无此类关联记录"
+              @row-dblclick="selected = $event"
+            >
+              <el-table-column
+                prop="sourceKey"
+                label="来源编号"
+                min-width="130"
+                show-overflow-tooltip
+              />
+              <el-table-column label="业务承接" min-width="120">
+                <template #default="{ row }">{{
+                  nativeRecordError ? '状态暂不可用' : legacyNativeStatusText(row.nativeStatus)
+                }}</template>
+              </el-table-column>
+              <el-table-column
+                v-for="field in summaryFields"
+                :key="field.key"
+                :label="field.label"
+                min-width="150"
+                show-overflow-tooltip
+              >
+                <template #default="{ row }">{{ legacyValueText(row.values[field.key]) }}</template>
+              </el-table-column>
+              <el-table-column label="操作" width="100" fixed="right"
+                ><template #default="{ row }"
+                  ><el-button link type="primary" @click="selected = row"
+                    >查看详情</el-button
+                  ></template
+                ></el-table-column
+              >
+            </el-table>
+            <Pagination
+              v-model:page="pageNo"
+              v-model:limit="pageSize"
+              :total="total"
+              :page-sizes="[20, 50, 100]"
+            />
+          </template>
+        </section>
+      </div>
+      <ProjectLegacyRecordDetails
+        :record="selected"
+        :domain="domain"
+        :domains="source.domains"
+        @close="selected = undefined"
+        @parent="showParent"
+        @children="showChildren"
+        @reference="showReference"
+      />
+    </template>
+  </ContentWrap>
+</template>
+
+<script setup lang="ts">
+import { computed, ref, watch } from 'vue'
+import {
+  getLegacySources,
+  getLegacyRecords,
+  getLegacyNativeReport,
+  getLegacyNativeRecordStatus,
+  legacyNativeStatusText,
+  legacyValueText,
+  type LegacyId,
+  type LegacyRecord,
+  type LegacySource,
+  type LegacyNativeReport
+} from '@/api/pms/project/legacy-detail'
+import ProjectLegacyRecordDetails from './ProjectLegacyRecordDetails.vue'
+import ProjectAssociatedDataPanel from './ProjectAssociatedDataPanel.vue'
+const props = defineProps<{ projectId: LegacyId }>()
+const sources = ref<LegacySource[]>([])
+const sourceId = ref<LegacyId>()
+const source = computed(() =>
+  sources.value.find((item) => String(item.id) === String(sourceId.value))
+)
+const loading = ref(false)
+const sourceError = ref(false)
+const nativeReport = ref<LegacyNativeReport>()
+const nativeReportError = ref(false)
+const refresh = ref(0)
+const group = ref('')
+const domainCode = ref('project')
+const groups = computed(() => [...new Set(source.value?.domains.map((item) => item.group) || [])])
+const groupDomains = computed(
+  () => source.value?.domains.filter((item) => item.group === group.value) || []
+)
+const domain = computed(() => source.value?.domains.find((item) => item.code === domainCode.value))
+const summaryFields = computed(
+  () =>
+    domain.value?.fields
+      .filter(
+        (field) =>
+          ![
+            'id',
+            'projectId',
+            'projectIds',
+            'createBy',
+            'updateBy',
+            domain.value?.keyField
+          ].includes(field.key)
+      )
+      .slice(0, 4) || []
+)
+const records = ref<LegacyRecord[]>([])
+const total = ref(0)
+const pageNo = ref(1)
+const pageSize = ref(20)
+const recordsLoading = ref(false)
+const recordError = ref(false)
+const nativeRecordError = ref(false)
+const recordRefresh = ref(0)
+const selected = ref<LegacyRecord>()
+const filter = ref<{ sourceKey?: string; parentDomain?: string; parentKey?: string }>({})
+
+// Vue watcher cleanup prevents a late response from another project/domain replacing the current panel.
+// https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup
+watch(
+  [() => props.projectId, refresh],
+  async ([projectId], _old, onCleanup) => {
+    let current = true
+    onCleanup(() => {
+      current = false
+    })
+    sources.value = []
+    sourceId.value = undefined
+    selected.value = undefined
+    loading.value = true
+    sourceError.value = false
+    try {
+      const result = await getLegacySources(projectId)
+      if (current) {
+        sources.value = result
+        sourceId.value = result[0]?.id
+      }
+    } catch {
+      if (current) sourceError.value = true
+    } finally {
+      if (current) loading.value = false
+    }
+  },
+  { immediate: true }
+)
+watch(source, () => {
+  group.value = source.value?.domains[0]?.group || ''
+  selectDomain('project')
+})
+watch(source, async (currentSource, _old, onCleanup) => {
+  let current = true
+  onCleanup(() => {
+    current = false
+  })
+  nativeReport.value = undefined
+  nativeReportError.value = false
+  if (!currentSource) return
+  try {
+    const report = await getLegacyNativeReport(props.projectId, {
+      sourceId: currentSource.id,
+      snapshotId: currentSource.snapshotId
+    })
+    if (current) nativeReport.value = report
+  } catch {
+    if (current) nativeReportError.value = true
+  }
+})
+watch(
+  [source, domainCode, filter, pageNo, pageSize, recordRefresh],
+  async (_new, _old, onCleanup) => {
+    let current = true
+    onCleanup(() => {
+      current = false
+    })
+    const currentSource = source.value
+    records.value = []
+    // Keep the immutable scope's count during pagination; a temporary zero clamps page two to page one.
+    if (_new[0] !== _old?.[0] || _new[1] !== _old?.[1] || _new[2] !== _old?.[2]) total.value = 0
+    recordError.value = false
+    nativeRecordError.value = false
+    if (!currentSource) {
+      total.value = 0
+      recordsLoading.value = false
+      return
+    }
+    recordsLoading.value = true
+    try {
+      const query = {
+        sourceId: currentSource.id,
+        snapshotId: currentSource.snapshotId,
+        domain: domainCode.value,
+        ...filter.value,
+        pageNo: pageNo.value,
+        pageSize: pageSize.value
+      }
+      const [recordsResult, statusResult] = await Promise.allSettled([
+        getLegacyRecords(props.projectId, query),
+        getLegacyNativeRecordStatus(props.projectId, query)
+      ])
+      if (recordsResult.status === 'rejected') throw recordsResult.reason
+      const result = recordsResult.value
+      const statuses = statusResult.status === 'fulfilled' ? statusResult.value : {}
+      if (current) {
+        nativeRecordError.value = statusResult.status === 'rejected'
+        records.value = result.list.map((row) => ({
+          ...row,
+          nativeStatus: statuses[row.sourceKey]
+        }))
+        total.value = result.total
+      }
+    } catch {
+      if (current) recordError.value = true
+    } finally {
+      if (current) recordsLoading.value = false
+    }
+  },
+  { immediate: true }
+)
+const selectDomain = (code: string) => {
+  domainCode.value = code
+  pageNo.value = 1
+  filter.value = {}
+  selected.value = undefined
+}
+const selectGroup = (name: string) => {
+  group.value = name
+  selectDomain(groupDomains.value[0]?.code || 'project')
+}
+const navigate = (code: string) => {
+  selectDomain(code)
+  group.value = source.value?.domains.find((item) => item.code === code)?.group || ''
+}
+const showParent = (record: LegacyRecord) => {
+  if (record.parentDomain) {
+    navigate(record.parentDomain)
+    filter.value = { sourceKey: record.parentSourceKey }
+  }
+}
+const showChildren = (record: LegacyRecord, code: string) => {
+  navigate(code)
+  filter.value = { parentDomain: record.domain, parentKey: record.sourceKey }
+}
+const showReference = (code: string, sourceKey: string) => {
+  navigate(code)
+  filter.value = { sourceKey }
+}
+</script>
+
+<style scoped>
+.panel-heading,
+.records-heading {
+  display: flex;
+  justify-content: space-between;
+  align-items: center;
+  gap: 16px;
+}
+h3,
+h4 {
+  margin: 0;
+}
+.source-meta,
+.deferred-note,
+.filter-note {
+  color: var(--el-text-color-secondary);
+  overflow-wrap: anywhere;
+}
+.deferred-note {
+  font-size: 13px;
+}
+.group-tabs {
+  display: flex;
+  flex-wrap: wrap;
+  gap: 8px;
+  margin: 20px 0 16px;
+}
+button {
+  font: inherit;
+  cursor: pointer;
+  background: var(--el-bg-color);
+  color: var(--el-text-color-regular);
+  border: 1px solid var(--el-border-color-light);
+  border-radius: var(--el-border-radius-base);
+  padding: 8px 12px;
+}
+button.selected {
+  color: var(--el-color-primary);
+  border-color: var(--el-color-primary);
+  background: var(--el-color-primary-light-9);
+}
+button:focus-visible {
+  outline: 2px solid var(--el-color-primary);
+  outline-offset: 2px;
+}
+.domain-layout {
+  display: grid;
+  grid-template-columns: 220px minmax(0, 1fr);
+  gap: 20px;
+}
+.domain-nav {
+  display: flex;
+  flex-direction: column;
+  gap: 8px;
+}
+.domain-nav button {
+  display: flex;
+  justify-content: space-between;
+  gap: 12px;
+  text-align: left;
+}
+.records {
+  min-width: 0;
+}
+.records-heading {
+  margin-bottom: 16px;
+}
+@media (max-width: 768px) {
+  .domain-layout {
+    grid-template-columns: minmax(0, 1fr);
+  }
+  .domain-nav {
+    flex-direction: row;
+    flex-wrap: wrap;
+  }
+  .domain-nav button {
+    flex: 1 1 180px;
+  }
+}
+</style>
```

## 121 views/pms/project/project-master-detail/components/ProjectLegacyRecordDetails.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留纯新增功能**。迁移来源历史明细只读，不覆写现有项目或旧事实。
- 证据及验证边界：legacy-detail客户端与服务端。

### H01 @@ -0,0 +1,124 @@

裁决：保留纯新增功能。当前行来源：4d522dbc35e528ba385de0f257950b7250744251, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -0,0 +1,124 @@
+<template>
+  <el-drawer
+    :model-value="Boolean(record)"
+    title="DPPMS 资料详情"
+    size="min(720px, 95vw)"
+    @close="$emit('close')"
+  >
+    <template v-if="record">
+      <p class="record-origin">{{ domain?.label }} · 来源编号 {{ record.sourceKey }}</p>
+      <p role="status">业务承接：{{ legacyNativeStatusText(record.nativeStatus) }}</p>
+      <p v-if="record.nativeStatus?.reason">{{ record.nativeStatus.reason }}</p>
+      <p v-if="record.nativeStatus?.targets.length">
+        已关联：{{
+          [
+            ...new Set(
+              record.nativeStatus.targets.map((t) => legacyNativeTargetLabel(t.targetTable))
+            )
+          ].join('、')
+        }}
+      </p>
+      <div class="related-actions">
+        <el-button v-if="record.parentDomain" @click="$emit('parent', record)"
+          >查看上级记录</el-button
+        >
+        <el-button
+          v-for="child in children"
+          :key="child.code"
+          @click="$emit('children', record, child.code)"
+        >
+          {{ child.label }}
+        </el-button>
+      </div>
+      <dl class="record-fields">
+        <div v-for="field in domain?.fields || []" :key="field.key">
+          <dt>{{ field.label }}</dt>
+          <dd
+            >{{ legacyValueText(record.values[field.key]) }}
+            <el-button
+              v-if="reference(field.key)"
+              link
+              type="primary"
+              @click="$emit('reference', reference(field.key)!.domain, record.values[field.key]!)"
+              >{{ reference(field.key)!.label }}</el-button
+            >
+          </dd>
+        </div>
+      </dl>
+      <el-alert
+        v-if="record.redactedFields.length"
+        title="本条记录的敏感内容已隐藏。"
+        type="info"
+        :closable="false"
+      />
+    </template>
+  </el-drawer>
+</template>
+
+<script setup lang="ts">
+import { computed } from 'vue'
+import {
+  legacyChildren,
+  legacyReferences,
+  legacyValueText,
+  legacyNativeStatusText,
+  legacyNativeTargetLabel,
+  type LegacyDomain,
+  type LegacyRecord
+} from '@/api/pms/project/legacy-detail'
+const props = defineProps<{
+  record?: LegacyRecord
+  domain?: LegacyDomain
+  domains: LegacyDomain[]
+}>()
+defineEmits<{
+  close: []
+  parent: [LegacyRecord]
+  children: [LegacyRecord, string]
+  reference: [string, string]
+}>()
+const children = computed(() =>
+  props.domains.filter((d) => (legacyChildren[props.record?.domain || ''] || []).includes(d.code))
+)
+const reference = (field: string) => {
+  const value = props.record?.values[field]
+  const target = legacyReferences[props.record?.domain || '']?.[field]
+  return value && value !== '0' && target && props.domains.some((d) => d.code === target.domain)
+    ? target
+    : undefined
+}
+</script>
+
+<style scoped>
+.record-origin {
+  color: var(--el-text-color-secondary);
+  overflow-wrap: anywhere;
+}
+.related-actions {
+  display: flex;
+  flex-wrap: wrap;
+  gap: 8px;
+  margin-bottom: 16px;
+}
+.related-actions .el-button {
+  margin-left: 0;
+}
+.record-fields {
+  margin: 0 0 16px;
+}
+.record-fields > div {
+  display: grid;
+  grid-template-columns: minmax(100px, 30%) minmax(0, 1fr);
+  gap: 16px;
+  padding: 12px 0;
+  border-bottom: 1px solid var(--el-border-color-lighter);
+}
+dt {
+  color: var(--el-text-color-secondary);
+}
+dd {
+  margin: 0;
+  white-space: pre-wrap;
+  overflow-wrap: anywhere;
+}
+</style>
```

## 122 views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留类型修正**。唯一审批候选userId允许null转undefined；不添加候选人或放宽权限。
- 证据及验证边界：ProjectSchedulePanel.runtime.spec.ts。

### H01 @@ -550,7 +550,7 @@

裁决：保留类型修正。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -550,7 +550,7 @@
           .map((person) => [String(person.userId), person])
       ).values()
     ]
-    if (approvers.value.length === 1) approverId.value = approvers.value[0].userId
+    if (approvers.value.length === 1) approverId.value = approvers.value[0].userId ?? undefined
     else if (!approvers.value.length)
       approverError.value = '本项目尚无有效服务经理，请先在项目成员中完成指派。'
   } catch {
```

## 123 views/pms/project/project-master-detail/components/ProjectSerialPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：4；当前片段：4。
- 裁决：**保留纯新增功能**。原序列号表保持，新加续保记录页签。
- 证据及验证边界：DeviceWarrantyRenewalTable与projectPanelVisibility测试。

### H01 @@ -8,6 +8,12 @@

裁决：保留纯新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -8,6 +8,12 @@
       <span class="panel-title"><Icon icon="ep:cpu" /> 序列号详情</span>
       <span class="serial-total num">共 {{ total }} 台</span>
     </div>
+    <el-tabs v-model="activeSection">
+      <el-tab-pane label="设备序列号" name="devices" />
+      <el-tab-pane label="续保记录" name="renewals" />
+    </el-tabs>
+    <DeviceWarrantyRenewalTable v-if="activeSection === 'renewals'" :project-id="projectId" />
+    <template v-else>
     <el-form inline class="serial-filters" @submit.prevent="search">
       <el-form-item label="序列号"><el-input v-model="sn" clearable placeholder="请输入序列号" /></el-form-item>
       <el-form-item label="设备名称"><el-input v-model="deviceName" clearable placeholder="请输入设备名称" /></el-form-item>
```

### H02 @@ -92,6 +98,7 @@

裁决：保留纯新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -92,6 +98,7 @@
       :total="total"
       @pagination="load"
     />
+    </template>
   </ContentWrap>
 </template>
```

### H03 @@ -99,6 +106,7 @@

裁决：保留纯新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -99,6 +106,7 @@
 import { locationSnapshotText } from '@/utils/locationSnapshot'
 import { onBeforeUnmount, ref, watch } from 'vue'
 import * as DeviceApi from '@/api/pms/asset/device'
+import DeviceWarrantyRenewalTable from '@/views/pms/asset/device/components/DeviceWarrantyRenewalTable.vue'
 
 defineOptions({ name: 'ProjectSerialPanel' })
```

### H04 @@ -106,6 +114,7 @@

裁决：保留纯新增功能。当前行来源：e0d836f4ade472f0c53147cfb7db8192f0a8a18e。

```diff
@@ -106,6 +114,7 @@
 const emit = defineEmits<{ (e: 'open-config-log', deviceId: number, serialNumber: string): void }>()
 
 const NOT_INTEGRATED = '—'
+const activeSection = ref('devices')
 
 interface SerialRow {
   deviceId: number
```

## 124 views/pms/project/project-master-detail/components/RequirementAnalysisSectionCard.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。文件失效时给出错误，避免空引用触发异常。
- 证据及验证边界：需求分析文件测试。

### H01 @@ -435,6 +435,7 @@

裁决：保留文件兼容。当前行来源：9610d1ae72a31ba214992df6955f5db59273b725。

```diff
@@ -435,6 +435,7 @@
       referenceKey: frozenReferenceKey
     }
     const artifact = await FileApi.getArtifact(selection.artifactId, businessKey)
+    if (!artifact) throw new Error('文件引用已失效，请刷新后重试')
     let cursor: string | undefined
     let version: FileApi.FileVersionVO | undefined
     do {
```

## 125 views/pms/project/project-master-detail/components/TaskBusinessPanel.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：True；本轮前片段：5；当前片段：5。
- 裁决：**保留测试修复**。仅完成的CUT/CUTOVER_APPROVAL任务可进入原割接P6办理；Owner动作、绑定对象、原状态机与权限仍负责授权。
- 证据及验证边界：TaskBusinessPanel.runtime.spec.ts; 32-file-verification.md。

### H01 @@ -14,6 +14,11 @@

裁决：保留测试修复。当前行来源：6fa26979c713d428c5a2114d87739bfaea418a59, LOCAL_UNCOMMITTED。

```diff
@@ -14,6 +14,11 @@
         :project="{ ...project, id: project.id == null ? undefined : legacyOwnerId(project.id) }"
         :readonly="maintenanceReadonly"
         @changed="ownerChanged"
+      />
+      <CutoverTaskDetail
+        v-else-if="completedCutover && project && selectedObject"
+        ref="cutoverRef" :task-id="selectedObject" :project-id="project.id"
+        closure-only :readonly="maintenanceReadonly" @changed="ownerChanged"
       />
       <BusinessViewHost
         v-else-if="registration && project"
```

### H02 @@ -52,6 +57,7 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -52,6 +57,7 @@
 import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
 import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'
 import ProjectMembersBusinessView from '@/components/BusinessView/ProjectMembersBusinessView.vue'
+const CutoverTaskDetail = defineAsyncComponent(() => import('@/views/pms/cutover/task-detail/index.vue').then(module => module.default))
 import * as BusinessApi from '@/api/pms/project/task-business'
 import type { TaskBusinessContext, TaskBusinessObject } from '@/api/pms/project/task-business'
 import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
```

### H03 @@ -84,6 +90,7 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -84,6 +90,7 @@
 const registration = ref<BusinessViewRegistrationVO>()
 const hostRef = ref<InstanceType<typeof BusinessViewHost>>()
 const membersRef = ref<InstanceType<typeof ProjectMembersBusinessView>>()
+const cutoverRef = ref<InstanceType<typeof CutoverTaskDetail>>()
 // 业务视图宿主的首判定完成状态（expose 的 viewSettled）：宿主未挂载或执行检查中视为未就绪
 const hostSettled = computed(() => hostRef.value?.viewSettled === true)
 const loading = ref(false), dirty = ref(false)
```

### H04 @@ -116,6 +123,13 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -116,6 +123,13 @@
   context.value?.ownerActions.includes('QUERY'))
 const maintenanceReadonly = computed(() => Boolean(props.readonly || loading.value || error.value ||
   context.value?.recoverableError || !sameTask(context.value?.taskId, props.taskId)))
+// 审批完成只结束项目任务；关联割接的P6继续由割接Owner授权和状态机办理。
+const completedCutover = computed(() => props.completed &&
+  sameTask(context.value?.taskId, props.taskId) && context.value?.ownerContext === 'CUT' &&
+  context.value?.objectType === 'CUTOVER_APPROVAL' &&
+  registration.value?.componentKey === 'CUT_CUTOVER_APPROVAL_WORKBENCH' &&
+  context.value?.ownerActions.includes('QUERY') &&
+  context.value.links.some(row => row.objectId === selectedObject.value))
 // Owner pages enforce each row's state. Automatic links can lag behind a saved draft.
 // 只读（未进入阶段/已完成/执行不允许）只保留 QUERY：视图照常渲染，写操作由 Owner 页面隐藏
 const ownerActions = computed(() => {
```

### H05 @@ -219,6 +233,7 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -219,6 +233,7 @@
 }
 const requestLeave = async () => {
   try {
+    if (cutoverRef.value) return await cutoverRef.value.saveBeforeLeave()
     const host = membersRef.value || hostRef.value
     if (!host) return !dirty.value
     return (await host.requestLeave()) !== false
```

## 126 views/pms/project/project-master-detail/components/durationChangeFormState.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留文件兼容**。证据文件ID沿用正式API类型，不损失Long精度。
- 证据及验证边界：durationChangeFormState既有测试。

### H01 @@ -8,7 +8,7 @@

裁决：保留文件兼容。当前行来源：b54632ade7176d824744862945800318bdb7563f。

```diff
@@ -8,7 +8,7 @@
   durationDays?: number
   reasonType: string
   reasonDetail: string
-  customerEvidenceFileId?: number
+  customerEvidenceFileId?: ConstructionPlanChangeVO['customerEvidenceFileId']
   customerEvidenceFileVersion?: number
   customerEvidenceReferenceKey?: string
 }
```

## 127 views/pms/project/project-master-detail/components/saveArrivalReceipt.ts

- 原始状态：DIFFERENT；原源/原目标文件相同：True；本轮前片段：1；当前片段：1。
- 裁决：**保留测试修复**。首次上传不提交null文件ID；保留后续版本更新，防止初始化失败。
- 证据及验证边界：saveArrivalReceipt.runtime.spec.ts。

### H01 @@ -30,7 +30,8 @@

裁决：保留测试修复。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -30,7 +30,8 @@
     }
     attempt.initialized ||= await initializeUpload({
       modeCode: form.signDocumentArtifactId ? 'ADD_VERSION' : 'CREATE_ARTIFACT',
-      artifactId: form.signDocumentArtifactId, expectedReferenceVersion: form.signDocumentReferenceVersion,
+      ...(form.signDocumentArtifactId ? { artifactId: form.signDocumentArtifactId,
+        expectedReferenceVersion: form.signDocumentReferenceVersion } : {}),
       ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: String(form.id),
       purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'arrival-sign-document', categoryCode: 'ARRIVAL_SIGN_DOCUMENT',
       fileName: attempt.file.name, declaredSizeBytes: attempt.file.size,
```

## 128 views/pms/project/project-master-detail/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：11；当前片段：11。
- 裁决：**保留新增功能及范围上下文**。新增产品清单/实施范围，设备清单完整页携带项目和范围版本；routeProjectId同义别名不改变原ID校验。
- 证据及验证边界：projectPanelVisibility.runtime.spec.ts; projectEquipment及scope既有测试。

### H01 @@ -211,9 +211,13 @@

裁决：保留新增功能及范围上下文。当前行来源：4d522dbc35e528ba385de0f257950b7250744251, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -211,9 +211,13 @@
           @changed="handleContactsChanged"
         />
 
+        <div v-if="detail?.id && visitedTabs.has('product-lists')" v-show="activeTab === 'product-lists'" class="min-w-0" data-testid="project-pane-product-lists">
+          <ContentWrap><ProjectAssociatedDataPanel :project-id="detail.id" title="产品清单" :kinds="['real-products', 'lease-products', 'configured-products']" /></ContentWrap>
+        </div>
+
         <!-- ============ 项目概览：设备清单（与 pms-inheritance/project-detail 同一配置驱动表格） ============ -->
         <div v-if="detail?.id && visitedTabs.has('equipment')" v-show="activeTab === 'equipment'" class="min-w-0" data-testid="project-pane-equipment">
-          <DeliveryModuleTable :config="deliveryModules['equipment']" :project-id="detail.id" />
+          <DeliveryModuleTable :config="deliveryModules['equipment']" :project-id="detail.id" :project-context="scopeContext" />
         </div>
         <!-- 序列号详情（DemoV2 1.1.1 十八列明细：设备列表 + 档案位置关联 + 在网版本/维保逐台补齐） -->
         <div v-if="detail?.id && visitedTabs.has('sn-result')" v-show="activeTab === 'sn-result'" class="min-w-0" data-testid="project-pane-sn-result">
```

### H02 @@ -249,6 +253,10 @@

裁决：保留新增功能及范围上下文。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa, eb9f0709c08889c580ff06f98f15a413605f55c1。

```diff
@@ -249,6 +253,10 @@
             />
           </div>
         </template>
+
+        <div v-if="detail?.id && visitedTabs.has('scope')" v-show="activeTab === 'scope'" class="min-w-0" data-testid="project-pane-scope">
+          <ProjectDeliveryScopePanel :key="`scope-${detail.id}`" :project-context="scopeContext" />
+        </div>
 
         <ProjectAttributePanel
           v-if="detail?.id && activeTab === 'attributes'"
```

### H03 @@ -552,12 +560,15 @@

裁决：保留新增功能及范围上下文。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa, f0f72c9241e0778362cd5bc7886e08085d43e79a。

```diff
@@ -552,12 +560,15 @@
 import type { ProjectFlowSelection } from './components/project-flow'
 import type { TaskNode } from '@/api/pms/project/task-workbench'
 import * as ContactsApi from '@/api/pms/customer/contacts'
+import type { ProjectRouteContext } from '@/views/pms/commerce/commerceInteraction'
 import { loadProjectEquipmentList } from '@/api/pms/commerce/projectEquipment'
 import { checkPermi, checkRole } from '@/utils/permission'
 import { isBusinessViewId, legacyOwnerId } from '@/api/pms/platform/business-view/ids'
 import type { ProjectMasterVO, ProjectInstancesVO } from '@/api/pms/project/projects'
 
 // Only load business workspaces when their existing tab host is first mounted.
+const ProjectAssociatedDataPanel = defineAsyncComponent(() => import('./components/ProjectAssociatedDataPanel.vue'))
+const ProjectDeliveryScopePanel = defineAsyncComponent(() => import('@/views/pms/commerce/delivery-scope/index.vue'))
 const ProjectSplitWizard = defineAsyncComponent(() => import('./components/ProjectSplitPanel.vue'))
 const ProjectTreePanel = defineAsyncComponent(() => import('@/views/pms/project/inheritance/tree/ProjectTreePanel.vue'))
 const ProjectProgressPanel = defineAsyncComponent(() => import('./components/ProjectProgressSummaryPanel.vue'))
```

### H04 @@ -603,6 +614,7 @@

裁决：保留新增功能及范围上下文。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -603,6 +614,7 @@
 const route = useRoute()
 const projectId = () => isBusinessViewId(route.query.projectId) ? legacyOwnerId(route.query.projectId) : undefined
 const router = useRouter()
+const routeProjectId = () => isBusinessViewId(route.query.projectId) ? legacyOwnerId(route.query.projectId) : undefined
 const approvalDrawerRef = ref<InstanceType<typeof ProjectApprovalDrawer>>()
 provide(projectApprovalDetailKey, (id) => approvalDrawerRef.value?.open(id))
```

### H05 @@ -613,6 +625,12 @@

裁决：保留新增功能及范围上下文。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -613,6 +625,12 @@
 const majorProjectLevelLabel = computed(() => detail.value?.majorProjectLevel
   ? getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, detail.value.majorProjectLevel).trim() : '')
 const instances = ref<ProjectInstancesVO | null>(null)
+const scopeVersion = ref<number>()
+const scopeContext = computed<ProjectRouteContext | undefined>(() => {
+  const project = detail.value
+  if (!project?.id || project.version === undefined || scopeVersion.value === undefined) return undefined
+  return { projectId: project.id, projectVersion: project.version, projectScopeVersion: scopeVersion.value }
+})
 // 身份卡右侧进度/任务/风险统计（设计稿 hstat）；进度与任务由新链实例视图计算，风险走 /pms/project-risk
 // 注意：/pms/project-panoramic 面向旧链 pms_project，ID 空间与本页主数据不同，不能混用
 const riskCount = ref<number>()
```

### H06 @@ -657,6 +675,7 @@

裁决：保留新增功能及范围上下文。当前行来源：e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -657,6 +675,7 @@
     'stage-status',
     'stage',
     'customer',
+    'product-lists',
     'equipment',
     'sn-result',
     'config-log',
```

### H07 @@ -680,11 +699,13 @@

裁决：保留新增功能及范围上下文。当前行来源：88049734dff6b5581a0158fba57b5430ca6d4e78, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -680,11 +699,13 @@
 const overviewSteps: { key: string; label: string; icon: string; pending?: boolean; permission?: string[] }[] = [
   { key: 'base', label: '基本信息', icon: 'ep:document' },
   { key: 'customer', label: '客户信息', icon: 'ep:office-building' },
+  { key: 'product-lists', label: '产品清单', icon: 'ep:collection', permission: ['pms:project:query'] },
   { key: 'tree', label: '项目树', icon: 'ep:share' },
   { key: 'members', label: '项目成员', icon: 'ep:user-filled' },
   { key: 'equipment', label: '设备清单', icon: 'ep:cpu', permission: ['pms:device:query'] },
   { key: 'sn-result', label: '序列号详情', icon: 'ep:cpu', permission: ['pms:device:query'] },
-  { key: 'config-log', label: '配置Log', icon: 'ep:document-copy' }
+  { key: 'config-log', label: '配置Log', icon: 'ep:document-copy' },
+  { key: 'scope', label: '实施范围', icon: 'ep:location', permission: ['pms:commerce:scope:query'] }
   // 属性判定/匹配历史/生命周期实例三项按需求方 2026-09-30 要求从概览导航隐藏；对应内容面板保留，深链回退基本信息
 ]
```

### H08 @@ -747,7 +768,7 @@

裁决：保留新增功能及范围上下文。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -747,7 +768,7 @@
 const primaryContact = ref<ContactsApi.ContactVO>()
 const primaryContactPending = ref(false)
 const loadPrimaryContact = async () => {
-  const id = projectId()
+  const id = routeProjectId()
   if (!id || !checkPermi(['pms:customer-contact:query'])) return
   try {
     const page = await ContactsApi.getProjectPage(id, { pageNo: 1, pageSize: 1, status: 0 })
```

### H09 @@ -875,7 +896,7 @@

裁决：保留新增功能及范围上下文。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -875,7 +896,7 @@
 
 // ============ 数据加载 ============
 const loadDetail = async () => {
-  const id = projectId()
+  const id = routeProjectId()
   if (!id) return
   detail.value = await ProjectsApi.getProject(id)
   parentProject.value = null
```

### H10 @@ -903,12 +924,12 @@

裁决：保留新增功能及范围上下文。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4。

```diff
@@ -903,12 +924,12 @@
   stageTreeToken.value++
 }
 const loadInstances = async () => {
-  const id = projectId()
+  const id = routeProjectId()
   if (!id) return
   instances.value = await ProjectsApi.getProjectInstances(id)
 }
 const loadRiskCount = async () => {
-  const id = projectId()
+  const id = routeProjectId()
   if (!id || !checkPermi(['pms:project-risk:query'])) return
   try {
     riskCount.value = ((await RiskApi.getProjectRiskListByProjectId(id)) || []).length
```

### H11 @@ -916,10 +937,21 @@

裁决：保留新增功能及范围上下文。当前行来源：2a023e23f6014e01a64b20780e3e6ac0a9ac47f4, e21f02d2d29385076edbaf1a776ad9743a7782fa。

```diff
@@ -916,10 +937,21 @@
     riskCount.value = undefined
   }
 }
+const loadScopeVersion = async () => {
+  const id = routeProjectId()
+  scopeVersion.value = undefined
+  if (!id || !checkPermi(['pms:commerce:scope:query'])) return
+  try {
+    const scope = await ProjectsApi.queryTree(id, { queryType: 'LOCATE', pageSize: 1 })
+    if (id === routeProjectId()) scopeVersion.value = scope.updating ? undefined : scope.treeVersion
+  } catch {
+    scopeVersion.value = undefined
+  }
+}
 const loadAll = async () => {
   loading.value = !detail.value || String(detail.value.id) !== String(projectId())
   try {
-    await Promise.all([loadDetail(), loadInstances(), loadPrimaryContact(), loadRiskCount()])
+    await Promise.all([loadDetail(), loadInstances(), loadPrimaryContact(), loadRiskCount(), loadScopeVersion()])
     stageTreeToken.value++
     // 深链直达阶段和任务页签时实例尚未就绪，首帧补选必然空转；
     // 在实例加载完成后回填默认选中，避免中栏空白、侧栏完成条件以空 stageCode 请求门禁报错
```

## 129 views/pms/project/projects/ProjectCreationSource.vue

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接口依赖**。合同创建的订单选择与来源指纹是新增来源锁定链的必要输入，不能仅回退前端使正式创建失败；与旧展示重叠的字段单列，非源一致。
- 证据及验证边界：ProjectManualCreationApplicationService合同来源校验；useProjectCreationSource.spec.ts。

### H01 @@ -0,0 +1,65 @@

裁决：保留新增接口依赖。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c, LOCAL_UNCOMMITTED。

```diff
@@ -0,0 +1,65 @@
+<template>
+  <div v-loading="loading" class="source-preview">
+    <el-alert v-if="error" :title="error" type="error" :closable="false" />
+    <el-button v-if="error" link type="primary" @click="$emit('select-order', salesOrderId)">重新加载来源</el-button>
+    <template v-if="source">
+      <el-form-item label="关联销售订单">
+        <el-select :model-value="salesOrderId" placeholder="选择此合同下的销售订单" class="!w-full"
+          :disabled="loading" @change="$emit('select-order', $event)">
+          <el-option v-for="order in source.orders" :key="order.id" :value="order.id"
+            :label="`${order.orderNo} · 执行单 ${order.executionNo || '未关联'}`" />
+        </el-select>
+      </el-form-item>
+      <el-alert v-if="!source.orders.length" title="此合同没有有效销售订单，请核对同步数据。" type="warning" :closable="false" />
+      <el-descriptions v-if="salesOrderId" title="来源基本信息" :column="mobile ? 1 : 4" border size="small">
+        <el-descriptions-item label="订单项目名称">{{ source.resolved.projectName ?? '—' }}</el-descriptions-item>
+        <el-descriptions-item label="关联执行单">{{ source.resolved.executionNo ?? '未关联' }}</el-descriptions-item>
+        <el-descriptions-item label="订单购货方">{{ source.resolved.customerName ?? '—' }}（{{ source.resolved.customerCode ?? '—' }}）</el-descriptions-item>
+        <el-descriptions-item label="客户项目名称">{{ source.resolved.customerProjectName ?? '—' }}</el-descriptions-item>
+        <el-descriptions-item label="重大项目级别">{{ dictText(source.resolved.majorProjectLevel, DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL) }}</el-descriptions-item>
+        <el-descriptions-item label="项目类型">{{ dictText(source.resolved.projectType, DICT_TYPE.PMS_PROJECT_TYPE) }}</el-descriptions-item>
+        <el-descriptions-item label="下单时间">{{ source.resolved.orderCreateTime ? formatDate(source.resolved.orderCreateTime) : '—' }}</el-descriptions-item>
+        <el-descriptions-item label="销售代表">{{ execution?.salesRepName ?? '—' }}</el-descriptions-item>
+        <el-descriptions-item label="市场 / 系统">{{ source.resolved.marketName ?? '—' }} / {{ source.resolved.systemName ?? '—' }}</el-descriptions-item>
+        <el-descriptions-item label="拓展 / 行业">{{ source.resolved.expendName ?? '—' }} / {{ source.resolved.industryName ?? '—' }}</el-descriptions-item>
+      </el-descriptions>
+      <div v-if="source.orders.length" class="source-block">
+        <h4>关联销售订单</h4>
+        <el-table :data="source.orders" size="small" max-height="180">
+          <el-table-column prop="orderNo" label="订单号" min-width="150" />
+          <el-table-column prop="salesType" label="销售类型" width="90" />
+          <el-table-column label="订单金额" width="150"><template #default="{ row }">{{ amountText(row.orderAmount, row.currencyCode) }}</template></el-table-column>
+          <el-table-column label="下单时间" width="170"><template #default="{ row }">{{ timeText(row.orderCreateTime) }}</template></el-table-column>
+          <el-table-column prop="executionNo" label="主执行单号" min-width="130" />
+        </el-table>
+      </div>
+      <div v-if="source.executionOrders.length" class="source-block">
+        <h4>关联执行单</h4>
+        <el-table :data="source.executionOrders" size="small" max-height="180">
+          <el-table-column prop="executionNo" label="执行单号" min-width="140" />
+          <el-table-column prop="projectName" label="CRM项目名称" min-width="160" show-overflow-tooltip />
+          <el-table-column prop="departmentName" label="办事处" width="120" show-overflow-tooltip />
+          <el-table-column label="项目金额" width="140"><template #default="{ row }">{{ row.projectAmount ?? '—' }}</template></el-table-column>
+          <el-table-column label="提交时间" width="170"><template #default="{ row }">{{ timeText(row.submitTime) }}</template></el-table-column>
+        </el-table>
+      </div>
+    </template>
+  </div>
+</template>
+<script setup lang="ts">
+import { computed } from 'vue'
+import { useMediaQuery } from '@vueuse/core'
+import { DICT_TYPE, getDictLabel } from '@/utils/dict'
+import { formatDate } from '@/utils/formatTime'
+import type { ContractCreationSourceRespVO } from '@/api/pms/commerce'
+const props = defineProps<{ source: ContractCreationSourceRespVO | null; loading: boolean; error: string; salesOrderId?: number }>()
+defineEmits<{ 'select-order': [id?: number] }>()
+const execution = computed(() => props.source?.executionOrders.find(item => item.id === props.source?.resolved.executionOrderId))
+const mobile = useMediaQuery('(max-width: 767px)')
+const dictText = (value: string | undefined, dict: DICT_TYPE) => value ? getDictLabel(dict, value) || value : '—'
+const timeText = (value?: string) => value ? formatDate(value) : '—'
+const amountText = (value?: number, currency?: string) => value == null ? '—' : `${currency || ''} ${value}`.trim()
+</script>
+<style scoped>
+.source-preview { margin: 0 0 20px; }
+</style>
```

## 130 views/pms/project/projects/index.vue

- 原始状态：DIFFERENT；原源/原目标文件相同：False；本轮前片段：13；当前片段：13。
- 裁决：**保留新增接口依赖及测试修复**。合同ID/订单/指纹配套新创建链，合同项目名服务端决定；显式最终客户为已有测试修复。与原手工无合同路径并存，不回退服务端校验。
- 证据及验证边界：ProjectManualCreationApplicationService; useProjectCreationSource.spec.ts。

### H01 @@ -448,13 +448,12 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c, b6b66a92ae4496ca0d3329f98da4f7cbb55209a6。

```diff
@@ -448,13 +448,12 @@
           <el-row :gutter="16">
             <el-col :span="12">
               <el-form-item label="项目名称" prop="projectName">
-                <el-input v-model="createForm.projectName" placeholder="某客户网络优化工程" />
+                <el-input :readonly="!!createForm.contractId" v-model="createForm.projectName" placeholder="某客户网络优化工程" />
               </el-form-item>
             </el-col>
             <el-col :span="12">
-              <el-form-item label="客户主档" prop="customerCode">
-                <PmsEntitySelect
-v-model="createForm.customerCode" :api="getSelectableCustomers"
+              <el-form-item label="最终客户主档" prop="customerCode">
+                <PmsEntitySelect v-model="createForm.customerCode" :api="getSelectableCustomers"
                   :label-field="['code', 'name']" value-field="code" query-field="keyword"
                   placeholder="按客户名称或编码选择" @change="selectCustomer" />
               </el-form-item>
```

### H02 @@ -469,73 +468,14 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c, d1dcdfc3c63276c718d307517222c861332e902f。

```diff
@@ -469,73 +468,14 @@
             <el-col :span="12">
               <el-form-item label="合同主档" prop="contractNo">
                 <PmsEntitySelect
-v-model="createForm.contractNo" :api="getSelectableContracts"
-                  :label-field="['contractNo', 'customerName', 'companyName']" value-field="contractNo"
+v-model="createForm.contractId" :api="getSelectableContracts"
+                  :label-field="['contractNo', 'customerName', 'companyName']" value-field="id"
                   query-field="keyword" placeholder="按合同号选择" @change="selectContract" />
               </el-form-item>
             </el-col>
             <el-col v-if="createForm.contractId" :span="24">
-              <div v-loading="creationSourceLoading" class="creation-source">
-                <template v-if="creationSource">
-                  <el-descriptions
-                    title="合同链取值预览（CRM权威值提交后由服务端落库）"
-                    :column="mobile ? 1 : 4"
-                    size="small"
-                    border
-                  >
-                    <el-descriptions-item label="客户项目名称">
-                      {{ creationSource.resolved?.customerProjectName || '—' }}
-                    </el-descriptions-item>
-                    <el-descriptions-item label="重大项目级别">{{ majorLevelText }}</el-descriptions-item>
-                    <el-descriptions-item label="项目类型">
-                      {{ dictText(creationSource.resolved?.projectType, DICT_TYPE.PMS_PROJECT_TYPE) }}
-                    </el-descriptions-item>
-                    <el-descriptions-item label="下单时间">
-                      {{ timeText(creationSource.resolved?.orderCreateTime) }}
-                    </el-descriptions-item>
-                    <el-descriptions-item label="市场">
-                      {{ creationSource.resolved?.marketName || creationSource.resolved?.marketCode || '—' }}
-                    </el-descriptions-item>
-                    <el-descriptions-item label="系统">
-                      {{ creationSource.resolved?.systemName || creationSource.resolved?.systemCode || '—' }}
-                    </el-descriptions-item>
-                    <el-descriptions-item label="拓展">
-                      {{ creationSource.resolved?.expendName || creationSource.resolved?.expendCode || '—' }}
-                    </el-descriptions-item>
-                    <el-descriptions-item label="行业">
-                      {{ creationSource.resolved?.industryName || creationSource.resolved?.industryCode || '—' }}
-                    </el-descriptions-item>
-                  </el-descriptions>
-                  <div v-if="creationSource.orders?.length" class="source-block">
-                    <div class="source-title">关联销售订单</div>
-                    <el-table :data="creationSource.orders" size="small" max-height="180">
-                      <el-table-column prop="orderNo" label="订单号" min-width="150" />
-                      <el-table-column prop="salesType" label="销售类型" width="90" />
-                      <el-table-column label="订单金额" width="150">
-                        <template #default="{ row }">{{ amountText(row.orderAmount, row.currencyCode) }}</template>
-                      </el-table-column>
-                      <el-table-column label="下单时间" width="170">
-                        <template #default="{ row }">{{ timeText(row.orderCreateTime) }}</template>
-                      </el-table-column>
-                      <el-table-column prop="executionNo" label="主执行单号" min-width="130" />
-                    </el-table>
-                  </div>
-                  <div v-if="creationSource.executionOrders?.length" class="source-block">
-                    <div class="source-title">关联执行单</div>
-                    <el-table :data="creationSource.executionOrders" size="small" max-height="180">
-                      <el-table-column prop="executionNo" label="执行单号" min-width="140" />
-                      <el-table-column prop="projectName" label="CRM项目名称" min-width="160" show-overflow-tooltip />
-                      <el-table-column prop="departmentName" label="办事处" width="120" show-overflow-tooltip />
-                      <el-table-column label="项目金额" width="140">
-                        <template #default="{ row }">{{ row.projectAmount == null ? '—' : row.projectAmount }}</template>
-                      </el-table-column>
-                      <el-table-column label="提交时间" width="170">
-                        <template #default="{ row }">{{ timeText(row.submitTime) }}</template>
-                      </el-table-column>
-                    </el-table>
-                  </div>
-                </template>
-              </div>
+              <ProjectCreationSource :source="creationSource" :loading="creationSourceLoading" :error="sourceError"
+                :sales-order-id="createForm.salesOrderId" @select-order="selectSalesOrder" />
             </el-col>
           </el-row>
           <el-row :gutter="16">
```

### H03 @@ -1163,6 +1103,8 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1163,6 +1103,8 @@
 </template>
 
 <script setup lang="ts">
+import ProjectCreationSource from '@/views/pms/project/projects/ProjectCreationSource.vue'
+import { useProjectCreationSource } from '@/views/pms/project/projects/useProjectCreationSource'
 import { useCreationTemplateMatch } from "@/views/pms/project/projects/useCreationTemplateMatch"
 /**
  * F-PM01 项目手工创建（PM-01）—— 新链页面（复数路由 /pms/projects）
```

### H04 @@ -1202,8 +1144,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1202,8 +1144,7 @@
 import { getSelectableCustomers, type SelectedCustomer } from '../inheritance/projects/customerSelection'
 import { getCustomerPage } from '@/api/pms/customer'
 import ProjectOverdueTags from './ProjectOverdueTags.vue'
-import { getSelectableContracts, type SelectedContract } from './contractSelection'
-import { getContractCreationSource, type ContractCreationSourceRespVO } from '@/api/pms/commerce'
+import { getSelectableContracts } from './contractSelection'
 import TemplateMatchDiagnostics from '../project-templates/TemplateMatchDiagnostics.vue'
 import { closedProjectStatuses } from './projectStatus'
```

### H05 @@ -1448,9 +1389,6 @@

裁决：保留新增接口依赖及测试修复。当前行来源：SOURCE_BASELINE_DELETION_ONLY。

```diff
@@ -1448,9 +1389,6 @@
 const companies = ref<CompanyVO[]>([])
 const departments = ref<DeptVO[]>([])
 const availableSites = ref<SiteVO[]>([])
-/** 合同链取值预览（合同→订单→执行单，CRM权威建议值） */
-const creationSource = ref<ContractCreationSourceRespVO | null>(null)
-const creationSourceLoading = ref(false)
 const primarySiteIndex = ref(0)
 
 const createForm = reactive({
```

### H06 @@ -1459,6 +1397,8 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1459,6 +1397,8 @@
   customerName: '',
   contractNo: '',
   contractId: undefined as number | undefined,
+  salesOrderId: undefined as number | undefined,
+  sourceFingerprint: undefined as string | undefined,
   orderOfficeCompanyId: undefined as number | undefined,
   orderOfficeDepartmentId: undefined as number | undefined,
   locationMode: 'sites' as 'sites' | 'fallback',
```

### H07 @@ -1478,60 +1418,13 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1478,60 +1418,13 @@
   createForm.customerName = customer && customer.code === code ? customer.name : ''
 }
 
-/** 选择合同主档后带入合同号与合同客户，并按合同→订单→执行单链预览取值、带入建议字段。 */
-const selectContract = async (no: unknown, contract?: SelectedContract) => {
-  const selected = contract && contract.contractNo === no ? contract : undefined
-  creationSource.value = null
-  createForm.contractId = selected?.id
-  createForm.contractNo = selected?.contractNo ?? ''
-  if (!selected?.customerCode) return
-  createForm.customerCode = selected.customerCode
-  createForm.customerName = selected.customerName ?? selected.customerCode
-  if (!selected.id) return
-  creationSourceLoading.value = true
-  try {
-    const source = await getContractCreationSource(selected.id)
-    creationSource.value = source
-    applyCreationSource(source)
-  } catch {
-    // 预览失败保持既有手工录入值，不阻断创建
-  } finally {
-    creationSourceLoading.value = false
-  }
-}
-
-/** 按解析链建议带入可编辑字段；CRM权威四维/客户项目名称/重大级别由服务端按执行单落库，页面仅预览。 */
-const applyCreationSource = (source: ContractCreationSourceRespVO) => {
-  const resolved = source.resolved
-  if (!resolved) return
-  if (resolved.projectName) createForm.projectName = resolved.projectName
-  if (resolved.customerCode) {
-    createForm.customerCode = resolved.customerCode
-    createForm.customerName = resolved.customerName ?? resolved.customerCode
-  }
-  const company = companies.value.find((item) => item.code === resolved.companyCode)
-  if (company?.id != null) createForm.orderOfficeCompanyId = company.id
-  const execution =
-    source.executionOrders?.find((item) => item.id === resolved.executionOrderId) ??
-    source.executionOrders?.[0]
-  const department = departments.value.find((item) => item.code === execution?.departmentCode)
-  if (department?.id != null) createForm.orderOfficeDepartmentId = department.id
-  // 命中CRM重大项目级别时按工程类预填项目类别（模板匹配维度，可改）
-  if (resolved.majorProjectLevel) createForm.projectCategory = 'ENGINEERING'
-}
-
-const majorLevelText = computed(() => {
-  const level = creationSource.value?.resolved?.majorProjectLevel
-  return level ? getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, level) || level : '空值'
-})
-const dictText = (value: string | null | undefined, dict: DICT_TYPE) =>
-  value ? getDictLabel(dict, value) || value : '—'
-const timeText = (value?: string | null) => (value ? formatDate(value) : '—')
-const amountText = (value?: number | null, currency?: string | null) =>
-  value == null ? '—' : `${currency || ''} ${value}`.trim()
+const { creationSource, creationSourceLoading, sourceError, sourceReady, selectContract, selectSalesOrder, resetSource } =
+  useProjectCreationSource(createForm, companies, departments)
 
 const createRules = {
-  customerCode: [{ required: true, message: '请选择客户主档', trigger: 'change' }],
+  customerCode: [{ validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
+    callback(value?.trim() ? undefined : new Error('请选择最终客户主档'))
+  }, trigger: 'change' }],
   projectName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
   orderOfficeCompanyId: [{ required: true, message: '请选择下单公司', trigger: 'change' }],
   orderOfficeDepartmentId: [{ required: true, message: '请选择下单办事处', trigger: 'change' }],
```

### H08 @@ -1572,6 +1465,8 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1572,6 +1465,8 @@
     customerName: '',
     contractNo: '',
     contractId: undefined,
+    salesOrderId: undefined,
+    sourceFingerprint: undefined,
     orderOfficeCompanyId: undefined,
     orderOfficeDepartmentId: undefined,
     locationMode: 'sites',
```

### H09 @@ -1582,7 +1477,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1582,7 +1477,7 @@
     implementationMode: '',
     creationReason: ''
   })
-  creationSource.value = null
+  resetSource()
   primarySiteIndex.value = 0
   selectedTemplateRevisionId.value = undefined
   matchResult.value = null
```

### H10 @@ -1597,6 +1492,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1597,6 +1492,7 @@
 }
 
 const wizardNext0 = async () => {
+  if (!sourceReady.value) { message.error('请先选择关联销售订单并加载来源'); return }
   await wizardFormRef.value?.validate()
   if (createForm.locationMode === 'sites') {
     if (createForm.sites.some((item) => !item.siteId || item.siteVersion === undefined)) {
```

### H11 @@ -1617,6 +1513,9 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1617,6 +1513,9 @@
 
 // ============ 模板匹配（步骤②） ============
 const { matchLoading, matchResult, selectedTemplateRevisionId, runMatch } = useCreationTemplateMatch(() => ({
+  contractId: createForm.contractId,
+  salesOrderId: createForm.salesOrderId,
+  sourceFingerprint: createForm.sourceFingerprint,
   projectName: createForm.projectName,
   customerCode: createForm.customerCode || undefined,
   orderOfficeCompanyId: createForm.orderOfficeCompanyId!,
```

### H12 @@ -1683,6 +1582,7 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1683,6 +1582,7 @@
 
 // ============ 提交创建（步骤③） ============
 const submitCreate = async () => {
+  if (!sourceReady.value) { message.error('请重新加载合同订单来源'); return }
   const sites: ProjectSiteReqVO[] | undefined =
     createForm.locationMode === 'sites'
       ? createForm.sites.map((item, index) => ({
```

### H13 @@ -1697,6 +1597,8 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c。

```diff
@@ -1697,6 +1597,8 @@
     customerName: createForm.customerName || undefined,
     contractNo: createForm.contractNo || undefined,
     contractId: createForm.contractId,
+    salesOrderId: createForm.salesOrderId,
+    sourceFingerprint: createForm.sourceFingerprint,
     orderOfficeCompanyId: createForm.orderOfficeCompanyId!,
     orderOfficeDepartmentId: createForm.orderOfficeDepartmentId!,
     sites,
```

## 131 views/pms/project/projects/useProjectCreationSource.ts

- 原始状态：ADDED；原源/原目标文件相同：False；本轮前片段：1；当前片段：1。
- 裁决：**保留新增接口依赖及测试修复**。保留来源订单/指纹、旧响应隔离及显式最终客户，不再将购货方当最终客户。
- 证据及验证边界：useProjectCreationSource.spec.ts; 已验证客户显式关联修复。

### H01 @@ -0,0 +1,64 @@

裁决：保留新增接口依赖及测试修复。当前行来源：341b7705a239267ce041ceee95ce20d96e14aa9c, LOCAL_UNCOMMITTED。

```diff
@@ -0,0 +1,64 @@
+import { computed, onBeforeUnmount, ref, type Ref } from 'vue'
+import { getContractCreationSource, type ContractCreationSourceRespVO } from '@/api/pms/commerce'
+import type { SelectedContract } from './contractSelection'
+
+interface SourceForm {
+  contractId?: number; contractNo: string; salesOrderId?: number; sourceFingerprint?: string
+  projectName: string; customerCode: string; customerName: string
+  projectCategory?: string
+  orderOfficeCompanyId?: number; orderOfficeDepartmentId?: number
+}
+interface Organization { id?: number; code?: string }
+
+/** Both creation pages consume the same linked headers and ignore superseded responses. */
+export const useProjectCreationSource = (form: SourceForm, companies: Ref<Organization[]>, departments: Ref<Organization[]>) => {
+  const creationSource = ref<ContractCreationSourceRespVO | null>(null)
+  const creationSourceLoading = ref(false)
+  const sourceError = ref('')
+  let generation = 0
+  const clearFields = () => {
+    form.salesOrderId = undefined; form.sourceFingerprint = undefined
+    form.projectName = ''
+    form.orderOfficeCompanyId = undefined; form.orderOfficeDepartmentId = undefined
+  }
+  const resetSource = () => {
+    ++generation; creationSource.value = null; creationSourceLoading.value = false; sourceError.value = ''
+  }
+  const loadSource = async (salesOrderId?: number) => {
+    const contractId = form.contractId
+    const current = ++generation
+    clearFields(); sourceError.value = ''; creationSourceLoading.value = !!contractId
+    if (!contractId) { creationSource.value = null; return }
+    try {
+      const source = await getContractCreationSource(contractId, salesOrderId)
+      if (current !== generation) return
+      creationSource.value = source
+      const resolved = source.resolved
+      if (!resolved.salesOrderId) return
+      form.salesOrderId = resolved.salesOrderId
+      form.sourceFingerprint = source.sourceFingerprint
+      form.projectName = resolved.projectName ?? ''
+      if (resolved.majorProjectLevel) form.projectCategory = 'ENGINEERING'
+      form.contractNo = source.contract.contractNo
+      form.orderOfficeCompanyId = companies.value.find(item => item.code === resolved.companyCode)?.id
+      const execution = source.executionOrders.find(item => item.id === resolved.executionOrderId)
+      form.orderOfficeDepartmentId = departments.value.find(item => item.code === execution?.departmentCode)?.id
+    } catch (error: any) {
+      if (current !== generation) return
+      sourceError.value = error?.response?.data?.msg || error?.message || '来源加载失败，请重新选择合同或重试'
+    } finally {
+      if (current === generation) creationSourceLoading.value = false
+    }
+  }
+  const selectContract = async (id: unknown, contract?: SelectedContract) => {
+    resetSource(); clearFields()
+    const selected = contract && String(contract.id) === String(id) ? contract : undefined
+    form.contractId = selected?.id; form.contractNo = selected?.contractNo ?? ''
+    await loadSource()
+  }
+  const sourceReady = computed(() => !form.contractId || (!creationSourceLoading.value && !sourceError.value
+    && !!form.salesOrderId && !!form.sourceFingerprint))
+  onBeforeUnmount(() => { ++generation })
+  return { creationSource, creationSourceLoading, sourceError, sourceReady, selectContract,
+    selectSalesOrder: loadSource, resetSource }
+}
```

## 132 views/pms/engineering/configuration/with-device-logs.vue

- 原始状态：SAME_BEFORE_RUNTIME_REPAIR；原源/原目标文件相同：True；本轮前片段：0；当前片段：6。
- 裁决：**修复原源相同文件的运行依赖**。阶段任务实际使用此页。虽然界面代码与原源相同，当前后端requireLegacyUnchanged已拒绝旧URL写入；改用原生附件，保持选择→保存→关闭，并保留调试人员查询、导入日志只读和状态动作。
- 证据及验证边界：configurationWithDeviceLogsUsers.runtime.spec.ts; ConfigurationServiceImpl.create/updateConfiguration。

### H01 @@ -184,10 +184,10 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -184,10 +184,10 @@
         </el-col>
         <el-col :span="24">
           <el-form-item label="配置日志" prop="configLogUrl"
-            ><UploadFile
-              v-model="form.configLogUrl!"
-              :disabled="readOnly"
-              :file-type="['log', 'txt', 'cfg', 'conf', 'doc', 'xls', 'ppt', 'pdf']"
+            ><NativeAttachments
+              :key="attachmentEpoch" ref="attachmentsRef" kind="configuration"
+              :entity-id="form.id" :readonly="readOnly" :legacy="form.configLogUrl"
+              @changed="attachmentOwnerChanged"
           /></el-form-item>
         </el-col>
         <el-col :span="24">
```

### H02 @@ -208,14 +208,15 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -208,14 +208,15 @@
       :object-id="form.id"
     />
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
+      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
       <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
     </template>
   </Dialog>
 </template>
 
 <script setup lang="ts">
-import { computed, onMounted, reactive, ref } from 'vue'
+import { computed, nextTick, onMounted, reactive, ref } from 'vue'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import ManualCollectionDialog from '@/components/DeviceCollection/CollectionWithDeviceLogsDialog.vue'
 import ProjectDeviceLogArchiveDialog from '@/components/DeviceCollection/ProjectDeviceLogArchiveDialog.vue'
 import BusinessCollectionLogs from '@/components/DeviceCollection/BusinessCollectionLogs.vue'
```

### H03 @@ -284,6 +285,8 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -284,6 +285,8 @@
 })
 const formVisible = ref(false)
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
 type ConfigurationForm = Omit<ConfigurationVO, 'debugTime'> & { debugTime?: string | number | null }
 const form = ref<ConfigurationForm>({ projectId: 0, status: 0 })
 const editableRecord = (row: Pick<ConfigurationVO, 'status' | 'importedLogFileId'>) =>
```

### H04 @@ -315,6 +318,7 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -315,6 +318,7 @@
   }
 }
 const openForm = (row?: ConfigurationVO) => {
+  attachmentEpoch.value++
   debuggerUsersError.value = ''
   form.value = {
     id: undefined,
```

### H05 @@ -327,13 +331,18 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -327,13 +331,18 @@
     version: undefined,
     status: 0,
     ...row,
-    // Keep UploadFile on the existing string API contract for NULL legacy logs.
+    // Keep legacy metadata readonly; new files use the native Owner binding.
     configLogUrl: row?.configLogUrl ?? ''
   }
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
+  form.value.version = saved.version
+  form.value.status = saved.status
+  form.value.configLogUrl = saved.configLogUrl ?? ''
+}
 const save = async () => {
-  if (readOnly.value) return
+  if (readOnly.value || saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
```

### H06 @@ -344,9 +353,11 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -344,9 +353,11 @@
           ? undefined
           : Number(form.value.debugTime)
     }
-    data.id
-      ? await ConfigurationApi.updateConfiguration(data)
-      : await ConfigurationApi.createConfiguration(data)
+    if (data.id) await ConfigurationApi.updateConfiguration(data)
+    else form.value.id = await ConfigurationApi.createConfiguration(data)
+    Object.assign(form.value, await ConfigurationApi.getConfiguration(form.value.id!))
+    await nextTick()
+    if (!attachmentsRef.value || !(await attachmentsRef.value.savePending(form.value.id!))) return
     message.success('保存成功')
     formVisible.value = false
     await load()
```

## 133 views/pms/engineering/joint-test/with-device-logs.vue

- 原始状态：SAME_BEFORE_RUNTIME_REPAIR；原源/原目标文件相同：True；本轮前片段：0；当前片段：7。
- 裁决：**修复原源相同文件的运行依赖**。阶段任务实际使用此页；当前JointTestServiceImpl拒绝旧evidenceUrl写入。恢复原证据附件位置和选取文件→保存→关闭交互，使用原生Owner绑定，不移除状态、权限和日志采集规则。
- 证据及验证边界：jointTestWorkspace.runtime.spec.ts; JointTestServiceImpl.create/updateJointTest。

### H01 @@ -258,10 +258,8 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -258,10 +258,8 @@
         </el-col>
         <el-col :span="24">
           <el-form-item label="证据附件" prop="evidenceUrl"
-            ><UploadFile
-              v-model="form.evidenceUrl!"
-              :disabled="readOnly"
-              :file-type="['log', 'txt', 'cfg', 'conf', 'doc', 'xls', 'ppt', 'pdf']"
+            ><NativeAttachments :key="attachmentEpoch" ref="attachmentsRef" kind="jointTest"
+              :entity-id="form.id" :readonly="readOnly" :legacy="form.evidenceUrl" @changed="attachmentOwnerChanged"
           /></el-form-item>
         </el-col>
         <el-col v-if="form.exceptionRecord" :span="24">
```

### H02 @@ -288,7 +286,7 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -288,7 +286,7 @@
       :object-id="form.id"
     />
     <template #footer>
-      <el-button @click="formVisible = false">取消</el-button>
+      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
       <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
     </template>
   </Dialog>
```

### H03 @@ -324,7 +322,8 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -324,7 +322,8 @@
 </template>
 
 <script setup lang="ts">
-import { computed, onMounted, reactive, ref, watch } from 'vue'
+import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
+import NativeAttachments from '../attachment/NativeAttachments.vue'
 import { useMessage } from '@/hooks/web/useMessage'
 import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
 import * as JointTestApi from '@/api/pms/engineering/joint-test'
```

### H04 @@ -359,6 +358,8 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -359,6 +358,8 @@
 })
 const formVisible = ref(false)
 const formRef = ref()
+const attachmentEpoch = ref(0)
+const attachmentsRef = ref<InstanceType<typeof NativeAttachments>>()
 type JointTestForm = Omit<JointTestVO, 'testTime'> & { testTime?: number | string | null }
 const form = ref<JointTestForm>({ projectId: props.projectId ?? 0, testCase: '', status: 0 })
```

### H05 @@ -477,6 +478,7 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -477,6 +478,7 @@
   }
 }
 const openForm = (row?: JointTestVO) => {
+  attachmentEpoch.value++
   form.value = {
     id: undefined,
     projectId: props.projectId ?? 0,
```

### H06 @@ -491,15 +493,19 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -491,15 +493,19 @@
     version: undefined,
     status: 0,
     ...row,
-    // Keep UploadFile on the existing string API contract for NULL legacy evidence.
+    // Legacy evidence stays readonly; new files bind to the native Owner.
     evidenceUrl: row?.evidenceUrl ?? ''
   }
   syncMetaFromForm()
   loadDeviceInfo()
   formVisible.value = true
 }
+const attachmentOwnerChanged = (saved: { version?: number; status?: number }) => {
+  form.value.version = saved.version
+  form.value.status = saved.status
+}
 const save = async () => {
-  if (readOnly.value) return
+  if (readOnly.value || saving.value) return
   await formRef.value.validate()
   saving.value = true
   try {
```

### H07 @@ -511,7 +517,11 @@

裁决：修复原源相同文件的运行依赖。当前行来源：LOCAL_UNCOMMITTED。

```diff
@@ -511,7 +517,11 @@
           ? undefined
           : Number(form.value.testTime)
     }
-    data.id ? await JointTestApi.updateJointTest(data) : await JointTestApi.createJointTest(data)
+    if (data.id) await JointTestApi.updateJointTest(data)
+    else form.value.id = await JointTestApi.createJointTest(data)
+    Object.assign(form.value, await JointTestApi.getJointTest(form.value.id!))
+    await nextTick()
+    if (!attachmentsRef.value || !(await attachmentsRef.value.savePending(form.value.id!))) return
     message.success('保存成功')
     formVisible.value = false
     await load()
```

## 134 views/pms/engineering/attachment/useNativeAttachmentDraft.ts

- 原始状态：ADDED_BY_THIS_REPAIR；原源/原目标文件相同：True；本轮前片段：0；当前片段：1。
- 裁决：**本次修复依赖**。复用现有两段式原生文件接口；只暂存选择，实际保存后绑定Owner；上传/归集失败重用会话，切换对象和只读时中止后续操作。
- 证据及验证边界：nativeAttachmentDraft.runtime.spec.ts。

### H01 @@ -0,0 +1,152 @@

裁决：本次修复依赖。当前行来源：LOCAL_UNTRACKED。

```diff
@@ -0,0 +1,152 @@
+import { onBeforeUnmount, ref } from 'vue'
+import type { UploadFile } from 'element-plus'
+import * as FileApi from '@/api/pms/platform/file'
+import { generateUUID } from '@/utils'
+import { fileUploadMediaType } from '@/components/PmsFileArtifact/fileMediaType'
+import { registerDraftUpload } from '@/hooks/web/useDraftUploads'
+import { checkPermi } from '@/utils/permission'
+
+interface DraftOptions {
+  context: () => { module: string; type: string; purpose: string; accept: string }
+  readonly: () => boolean
+  collect: () => Promise<boolean>
+}
+/** Select before the first save; bind only after the Owner has its real identity. */
+export const useNativeAttachmentDraft = (options: DraftOptions) => {
+  const pendingFiles = ref<UploadFile[]>([]),
+    draftBusy = ref(false),
+    draftError = ref('')
+  const attempts = new Map<
+    number,
+    {
+      initKey: string
+      completeKey: string
+      referenceKey: string
+      initialized?: FileApi.FileUploadInitRespVO
+      bound?: boolean
+    }
+  >()
+  let generation = 0
+  const selectPending = (file: UploadFile) => {
+    if (options.readonly() || draftBusy.value || !file.raw) return
+    const accepted = options
+      .context()
+      .accept.split(',')
+      .some((ext) => file.name.toLowerCase().endsWith(ext.toLowerCase()))
+    if (!accepted || file.raw.size <= 0 || file.raw.size > 5 * 1024 * 1024) {
+      draftError.value = '请选择支持类型且不超过5MiB的非空附件'
+      return
+    }
+    if (pendingFiles.value.length >= 5) {
+      draftError.value = '一次最多选择5个附件'
+      return
+    }
+    draftError.value = ''
+    if (!pendingFiles.value.some((value) => value.uid === file.uid)) pendingFiles.value.push(file)
+  }
+  const beforeRemovePending = (file: UploadFile) => {
+    if (options.readonly() || draftBusy.value) return false
+    if (attempts.get(file.uid)?.initialized) {
+      draftError.value = '附件已进入保存，请先重试完成保存，再从附件记录解除引用'
+      return false
+    }
+    return true
+  }
+  const removePending = (file: UploadFile) => {
+    if (!beforeRemovePending(file)) return
+    pendingFiles.value = pendingFiles.value.filter((value) => value.uid !== file.uid)
+    attempts.delete(file.uid)
+  }
+  const resetPending = () => {
+    ++generation
+    pendingFiles.value = []
+    attempts.clear()
+    draftBusy.value = false
+    draftError.value = ''
+  }
+  const savePending = async (entityId: number | string) => {
+    if (!pendingFiles.value.length) return true
+    if (options.readonly() || !checkPermi(['pms:file:upload']) || draftBusy.value || !entityId)
+      return false
+    const currentGeneration = generation,
+      owner = { ...options.context() }
+    const current = () => currentGeneration === generation && !options.readonly()
+    draftBusy.value = true
+    draftError.value = ''
+    try {
+      for (const file of [...pendingFiles.value]) {
+        if (!current()) return false
+        let attempt = attempts.get(file.uid)
+        if (!attempt) {
+          attempt = {
+            initKey: generateUUID(),
+            completeKey: generateUUID(),
+            referenceKey: generateUUID()
+          }
+          attempts.set(file.uid, attempt)
+        }
+        if (!attempt.bound) {
+          attempt.initialized ||= await FileApi.initializeUpload(
+            {
+              modeCode: 'CREATE_ARTIFACT',
+              ownerContext: owner.module,
+              objectType: owner.type,
+              objectId: String(entityId),
+              purposeCode: owner.purpose,
+              referenceKey: attempt.referenceKey,
+              categoryCode: owner.purpose,
+              fileName: file.name,
+              declaredSizeBytes: file.raw!.size,
+              declaredMediaType: fileUploadMediaType(file.raw!)
+            },
+            attempt.initKey
+          )
+          if (!current()) return false
+          await FileApi.completeUpload(
+            attempt.initialized.artifactId,
+            attempt.initialized.sessionId,
+            file.raw!,
+            attempt.completeKey
+          )
+          if (!current()) return false
+          attempt.bound = true
+        }
+      }
+      if (!current() || !(await options.collect()) || !current()) return false
+      pendingFiles.value = []
+      attempts.clear()
+      return true
+    } catch (failure: unknown) {
+      if (currentGeneration === generation)
+        draftError.value =
+          (typeof failure === 'string'
+            ? failure
+            : failure instanceof Error
+              ? failure.message
+              : '') || '附件未保存，请重试'
+      return false
+    } finally {
+      if (currentGeneration === generation) draftBusy.value = false
+    }
+  }
+  registerDraftUpload({
+    isBusy: () => draftBusy.value,
+    hasPendingFile: () => pendingFiles.value.length > 0,
+    discard: () => {
+      if (!draftBusy.value) resetPending()
+    }
+  })
+  onBeforeUnmount(() => {
+    ++generation
+  })
+  return {
+    pendingFiles,
+    draftBusy,
+    draftError,
+    selectPending,
+    beforeRemovePending,
+    removePending,
+    resetPending,
+    savePending
+  }
+}
```
