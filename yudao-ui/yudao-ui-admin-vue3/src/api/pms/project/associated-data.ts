import request from '@/config/axios'
import type { LegacyId } from './legacy-detail'
export interface AssociatedRow {
  id: LegacyId
  sourceKey: string
  values: Record<string, unknown>
}
export const associatedKinds = [
  { kind: 'licenses', label: '许可历史' },
  {
    kind: 'products',
    label: '项目产品'
  },
  {
    kind: 'real-products',
    label: '真实产品清单'
  },
  {
    kind: 'lease-products',
    label: '租赁产品'
  },
  {
    kind: 'configured-products',
    label: '产品配置层级'
  },
  {
    kind: 'plans',
    label: '回款计划'
  },
  {
    kind: 'shipment-packages',
    label: '装箱单'
  },
  { kind: 'executions', label: '执行单属性' },
  { kind: 'factory-versions', label: '出厂软件版本' },
  { kind: 'software-versions', label: '软件版本观察' },
  { kind: 'software-changes', label: '软件变更批次' },
  { kind: 'installations', label: '安装地址来源' },
  { kind: 'warranty-services', label: '维保服务来源' },
  { kind: 'warranty-registrations', label: '保修登记来源' },
  { kind: 'warranty-changes', label: '保修变更来源' },
  { kind: 'project', label: '项目附加信息' },
  { kind: 'orders', label: '销售订单' },
  { kind: 'order-lines', label: '订单行' }
] as const
export type AssociatedKind = (typeof associatedKinds)[number]['kind']
export const associatedFields: Record<AssociatedKind, { key: string; label: string }[]> = {
  licenses: [
    { key: 'contractNo', label: '合同号' },
    { key: 'deviceSn', label: '设备序列号' },
    { key: 'productCode', label: '产品编码' },
    { key: 'productModel', label: '产品型号' },
    { key: 'contractType', label: '合同类型' },
    { key: 'licenseStatus', label: '许可状态' },
    { key: 'licenseCodeState', label: '许可码' },
    { key: 'deviceId', label: '设备' },
    { key: 'contractId', label: '合同' },
    { key: 'creator', label: '迁入人' },
    { key: 'createTime', label: '迁入时间' }
  ],
  'warranty-services': [
    { key: 'id', label: 'id' },
    { key: 'con_xb', label: 'con_xb' },
    { key: 'barcode', label: 'barcode' },
    { key: 'grade', label: 'grade' },
    { key: 'begin_date', label: 'begin_date' },
    { key: 'end_date', label: 'end_date' },
    { key: 'warranty', label: 'warranty' },
    { key: 'remark', label: 'remark' },
    { key: 'isyb', label: 'isyb' },
    { key: 'state', label: 'state' }
  ],
  'warranty-registrations': [
    { key: 'id', label: 'id' },
    { key: 'packlistId', label: 'packlistId' },
    { key: 'contractId', label: 'contractId' },
    { key: 'barCode', label: 'barCode' },
    { key: 'officeCode', label: 'officeCode' },
    { key: 'projectName', label: 'projectName' },
    { key: 'contractNo', label: 'contractNo' },
    { key: 'contractType', label: 'contractType' },
    { key: 'customerName', label: 'customerName' },
    { key: 'itemCode', label: 'itemCode' },
    { key: 'itemName', label: 'itemName' },
    { key: 'warrantyLevel', label: 'warrantyLevel' },
    { key: 'warrantyStartTime', label: 'warrantyStartTime' },
    { key: 'warrantyEndTime', label: 'warrantyEndTime' },
    { key: 'warrantyLimit', label: 'warrantyLimit' }
  ],
  'warranty-changes': [
    { key: 'id', label: 'id' },
    { key: 'username', label: 'username' },
    { key: 'updateType', label: 'updateType' },
    { key: 'barcode', label: 'barcode' },
    { key: 'warrantyStartTime', label: 'warrantyStartTime' },
    { key: 'warrantyEndTime', label: 'warrantyEndTime' },
    { key: 'warrantyTimes', label: 'warrantyTimes' },
    { key: 'updateTime', label: 'updateTime' }
  ],
  installations: [
    { key: 'sourceId', label: '来源记录ID' },
    { key: 'sourceProjectId', label: '来源项目ID' },
    { key: 'sourceDeviceSn', label: '来源设备序列号' },
    { key: 'installLocation', label: '原安装地址' },
    { key: 'sourceProductCode', label: '来源产品编码' },
    { key: 'sourceProductModel', label: '来源产品型号' },
    { key: 'sourceProductName', label: '来源产品名称' },
    { key: 'sourceReceiverName', label: '来源收件人' },
    { key: 'sourceExpressNo', label: '来源物流单号' },
    { key: 'sourceCarrierName', label: '来源承运商' },
    { key: 'sourceShipmentTime', label: '来源发货时间' },
    { key: 'sourceContractNo', label: '来源合同号' },
    { key: 'changedProjectKey', label: '来源变更项目ID' },
    { key: 'changedContractNo', label: '来源变更合同号' },
    { key: 'transferProjectKey', label: '来源转入项目ID' },
    { key: 'transferContractNo', label: '来源转入合同号' },
    { key: 'sourceTransferFlag', label: '来源转移标识' },
    { key: 'sourceCreatedAt', label: '来源创建时间' },
    { key: 'sourceCreatedBy', label: '来源创建人' },
    { key: 'sourceUpdatedAt', label: '来源更新时间' },
    { key: 'sourceUpdatedBy', label: '来源更新人' }
  ],
  'software-versions': [
    {
      key: 'sourceId',
      label: '项目软件版本表'
    },
    {
      key: 'sourceProjectId',
      label: '项目ID'
    },
    {
      key: 'logId',
      label: '软件版本变更记录'
    },
    {
      key: 'contractNo',
      label: '合同号'
    },
    {
      key: 'itemCode',
      label: '产品编码'
    },
    {
      key: 'barCode',
      label: '设备序列号'
    },
    {
      key: 'conp',
      label: 'conp'
    },
    {
      key: 'conpType',
      label: '版本类型'
    },
    {
      key: 'conpSeries',
      label: '版本系列'
    },
    {
      key: 'conpMark',
      label: '软件版本掩码'
    },
    {
      key: 'conpBak',
      label: '备份变更之前的版本'
    },
    {
      key: 'conpChange',
      label: '0无更新 1有更新'
    },
    {
      key: 'cpld',
      label: 'cpld'
    },
    {
      key: 'cpldBak',
      label: 'cpldBak'
    },
    {
      key: 'cpldChange',
      label: 'cpldChange'
    },
    {
      key: 'boot',
      label: 'boot'
    },
    {
      key: 'bootBak',
      label: 'bootBak'
    },
    {
      key: 'bootChange',
      label: 'bootChange'
    },
    {
      key: 'pcb',
      label: 'pcb'
    },
    {
      key: 'pcbBak',
      label: 'pcbBak'
    },
    {
      key: 'pcbChange',
      label: 'pcbChange'
    },
    {
      key: 'executeTime',
      label: '若有更新的情况下为执行更新时间，否则没有实际意义'
    },
    {
      key: 'datastate',
      label: '数据状态 0 失效 1 有效'
    },
    {
      key: 'sourceCreatedAt',
      label: 'createTime'
    },
    {
      key: 'sourceCreatedBy',
      label: 'createBy'
    },
    {
      key: 'sourceUpdatedAt',
      label: 'updateTime'
    },
    {
      key: 'sourceUpdatedBy',
      label: 'updateBy'
    },
    {
      key: 'customInfo',
      label: '自定义信息'
    }
  ],
  'software-changes': [
    {
      key: 'sourceId',
      label: '记录版本变更日志'
    },
    {
      key: 'sourceProjectId',
      label: '项目ID'
    },
    {
      key: 'changeVersion',
      label: 'V0001'
    },
    {
      key: 'changeRemark',
      label: '版本变更说明'
    },
    {
      key: 'latest',
      label: '0 后 1 是'
    },
    {
      key: 'sourceCreatedBy',
      label: 'createBy'
    },
    {
      key: 'sourceCreatedAt',
      label: 'createTime'
    },
    {
      key: 'sourceUpdatedBy',
      label: 'updateBy'
    },
    {
      key: 'sourceUpdatedAt',
      label: 'updateTime'
    }
  ],
  products: [
    {
      key: 'contractNo',
      label: '合同号'
    },
    {
      key: 'productCode',
      label: '产品编码'
    },
    {
      key: 'productName',
      label: '产品名称'
    },
    {
      key: 'projectQuantity',
      label: '项目数量'
    },
    {
      key: 'orderQuantity',
      label: '订单数量'
    },
    {
      key: 'deliveredQuantity',
      label: '已交数量'
    },
    {
      key: 'openQuantity',
      label: '未交数量'
    },
    {
      key: 'orderNo',
      label: '销售订单号'
    },
    {
      key: 'lineNo',
      label: '订单行号'
    },
    {
      key: 'unitCode',
      label: '单位'
    },
    { key: 'allocationConfirmed', label: '已确认分配' },
    { key: 'scopeStatus', label: '范围状态' },
    { key: 'allocationVersion', label: '分配版本' },
    { key: 'effectiveFrom', label: '生效时间' },
    { key: 'departmentCode', label: '办事处编码' },
    { key: 'departmentName', label: '办事处名称' }
  ],
  'real-products': [
    {
      key: 'contractNo',
      label: '合同号'
    },
    {
      key: 'projectCode',
      label: '商务项目编码'
    },
    {
      key: 'executionNo',
      label: '执行单号'
    },
    {
      key: 'productCategoryName',
      label: '一级产品名称'
    },
    {
      key: 'productName',
      label: '产品名称'
    },
    {
      key: 'productSubCode',
      label: '子产品编码'
    },
    {
      key: 'productSubModel',
      label: '子产品型号'
    },
    {
      key: 'productSubName',
      label: '子产品名称'
    },
    {
      key: 'quantity',
      label: '数量'
    },
    {
      key: 'memo',
      label: '说明'
    },
    { key: 'dataSource', label: '数据来源' },
    { key: 'creator', label: '创建人' },
    { key: 'createTime', label: '创建时间' },
    { key: 'updater', label: '更新人' },
    { key: 'updateTime', label: '更新时间' }
  ],
  'lease-products': [
    {
      key: 'projectCode',
      label: '清单项目编码'
    },
    {
      key: 'executionNo',
      label: '执行单号'
    },
    {
      key: 'productCategory',
      label: '产品分类'
    },
    {
      key: 'productType',
      label: '产品类型'
    },
    {
      key: 'productCode',
      label: '产品编码'
    },
    {
      key: 'productModel',
      label: '产品型号'
    },
    {
      key: 'productName',
      label: '产品名称'
    },
    {
      key: 'quantity',
      label: '数量'
    },
    {
      key: 'memo',
      label: '说明'
    },
    {
      key: 'leaseDuration',
      label: '租赁月数'
    },
    {
      key: 'dataSource',
      label: '数据来源'
    },
    { key: 'creator', label: '创建人' },
    { key: 'createTime', label: '创建时间' },
    { key: 'updater', label: '更新人' },
    { key: 'updateTime', label: '更新时间' }
  ],
  'configured-products': [
    {
      key: 'projectCode',
      label: '清单项目编码'
    },
    {
      key: 'executionNo',
      label: '执行单号'
    },
    {
      key: 'itemGroup',
      label: '产品组'
    },
    {
      key: 'productCode',
      label: '产品编码'
    },
    {
      key: 'parentCode',
      label: '上级产品编码'
    },
    {
      key: 'quantity',
      label: '数量'
    },
    {
      key: 'bomPath',
      label: '产品层级路径'
    },
    { key: 'parentBomPaths', label: '上级配置路径' },
    { key: 'parentGroupPaths', label: '上级配置组路径' },
    { key: 'groupPaths', label: '配置组路径' },
    { key: 'parentPathMissing', label: '上级配置缺失' },
    {
      key: 'productModel',
      label: '产品型号'
    },
    {
      key: 'productDescription',
      label: '产品描述'
    },
    {
      key: 'level',
      label: '产品层级'
    },
    {
      key: 'dataSource',
      label: '数据来源'
    },
    { key: 'creator', label: '创建人' },
    { key: 'createTime', label: '创建时间' },
    { key: 'updater', label: '更新人' },
    { key: 'updateTime', label: '更新时间' }
  ],
  plans: [
    {
      key: 'contractNo',
      label: '合同号'
    },
    {
      key: 'batchCode',
      label: '计划批次'
    },
    {
      key: 'collectionItemName',
      label: '回款款项名称'
    },
    {
      key: 'referenceEventName',
      label: '参考事件名称'
    },
    {
      key: 'eventPlannedAt',
      label: '计划时间'
    },
    {
      key: 'afterDays',
      label: '间隔天数'
    },
    {
      key: 'eventActualFinishedAt',
      label: '实际完成时间'
    },
    {
      key: 'marketingFeedback',
      label: '销售反馈'
    },
    {
      key: 'dataSource',
      label: '来源系统'
    },
    {
      key: 'createTime',
      label: '创建时间'
    },
    {
      key: 'creator',
      label: '创建人'
    },
    {
      key: 'updateTime',
      label: '更新时间'
    },
    {
      key: 'updater',
      label: '更新人'
    },
    {
      key: 'effectiveFrom',
      label: '生效时间'
    },
    {
      key: 'effectiveTo',
      label: '失效时间'
    }
  ],
  'shipment-packages': [
    {
      key: 'packageNo',
      label: '装箱单号'
    },
    {
      key: 'shipmentTime',
      label: '发货时间'
    },
    {
      key: 'warrantyStartTime',
      label: '保修开始时间'
    },
    {
      key: 'warrantyEndTime',
      label: '保修结束时间'
    },
    {
      key: 'receiverName',
      label: '收货人'
    },
    {
      key: 'expressNo',
      label: '快递单号'
    },
    {
      key: 'carrierName',
      label: '承运商名称'
    },
    {
      key: 'carrierCode',
      label: '承运商编码'
    }
  ],
  orders: [
    {
      key: 'contractNo',
      label: '合同号'
    },
    {
      key: 'executionNo',
      label: '执行单号'
    },
    {
      key: 'orderCreateTime',
      label: '订单创建时间'
    },
    {
      key: 'customerRequiredTime',
      label: '客户要求时间'
    },
    {
      key: 'customerCode',
      label: '购货方编码'
    },
    {
      key: 'customerName',
      label: '购货方名称'
    },
    {
      key: 'sourceProjectName',
      label: '原项目名称'
    },
    {
      key: 'orderComment',
      label: '订单备注'
    },
    {
      key: 'salesType',
      label: '销售类型'
    },
    {
      key: 'customInfo',
      label: '附加业务信息'
    },
    {
      key: 'companyCode',
      label: '公司编码'
    },
    {
      key: 'sourceSystem',
      label: '来源ERP系统'
    },
    {
      key: 'orderNo',
      label: '销售订单号'
    },
    {
      key: 'orderType',
      label: '订单类型'
    },
    {
      key: 'sourceSyncTime',
      label: '最近同步时间'
    },
    {
      key: 'sourceUpdatedAt',
      label: '来源更新时间'
    }
  ],
  'order-lines': [
    {
      key: 'productCode',
      label: '产品编码'
    },
    {
      key: 'productDesc',
      label: '产品描述'
    },
    {
      key: 'orderQty',
      label: '订单数量'
    },
    {
      key: 'openQty',
      label: '未交数量'
    },
    {
      key: 'bundleCode',
      label: '组合编码'
    },
    {
      key: 'warrantyMonth',
      label: '保修月数'
    },
    {
      key: 'lineType',
      label: '订单行类型'
    },
    {
      key: 'profitCenter',
      label: '利润中心'
    },
    {
      key: 'realExecutionNo',
      label: '实际执行单号'
    },
    {
      key: 'customInfo',
      label: '附加业务信息'
    },
    {
      key: 'companyCode',
      label: '公司编码'
    },
    {
      key: 'sourceSystem',
      label: '来源ERP系统'
    },
    {
      key: 'orderNo',
      label: '销售订单号'
    },
    {
      key: 'lineNo',
      label: '订单行号'
    },
    {
      key: 'unitCode',
      label: '单位'
    },
    {
      key: 'deliveredQty',
      label: '已交数量'
    },
    {
      key: 'quantityStatus',
      label: '数量确认状态'
    },
    {
      key: 'sourceSyncTime',
      label: '最近同步时间'
    },
    {
      key: 'sourceUpdatedAt',
      label: '来源更新时间'
    }
  ],
  executions: [
    {
      key: 'executionNo',
      label: '执行单号'
    },
    {
      key: 'sourceSystem',
      label: '来源系统'
    },
    {
      key: 'projectCode',
      label: '项目编码'
    },
    {
      key: 'projectName',
      label: '项目名称'
    },
    {
      key: 'salesRepCode',
      label: '销售人员编码'
    },
    {
      key: 'salesRepName',
      label: '销售人员姓名'
    },
    {
      key: 'marketCode',
      label: '市场编码'
    },
    {
      key: 'marketName',
      label: '市场名称'
    },
    {
      key: 'systemSourceKey',
      label: '系统部来源编号'
    },
    {
      key: 'systemName',
      label: '系统部名称'
    },
    {
      key: 'expendSourceKey',
      label: '拓展部来源编号'
    },
    {
      key: 'expendName',
      label: '拓展部名称'
    },
    {
      key: 'industryCode',
      label: '行业编码'
    },
    {
      key: 'industryName',
      label: '行业名称'
    },
    {
      key: 'departmentCode',
      label: '办事处编码'
    },
    {
      key: 'departmentName',
      label: '办事处名称'
    },
    {
      key: 'serviceTypeName',
      label: '服务类型'
    },
    {
      key: 'channelName',
      label: '渠道名称'
    },
    {
      key: 'engineeringFeeRaw',
      label: '工程费用'
    },
    {
      key: 'sourceObjectId',
      label: 'CRM对象编号'
    },
    {
      key: 'applyType',
      label: '申请类型'
    },
    {
      key: 'companyCode',
      label: '公司编码'
    },
    {
      key: 'customerProjectName',
      label: '客户项目名称'
    },
    {
      key: 'finalCustomerName',
      label: '最终客户名称'
    },
    {
      key: 'agentName',
      label: '代理商名称'
    },
    {
      key: 'majorProjectLevel',
      label: '重大项目级别'
    },
    {
      key: 'projectAmount',
      label: '项目金额'
    },
    {
      key: 'submitTime',
      label: '提交时间'
    },
    {
      key: 'predictedBidTime',
      label: '预计中标时间'
    },
    {
      key: 'contactName',
      label: '联系人姓名'
    },
    {
      key: 'contactPhone',
      label: '联系电话'
    }
  ],
  'factory-versions': [
    { key: 'deviceSn', label: '原设备序列号' },
    { key: 'conpVersion', label: 'CONP出厂版本' },
    { key: 'bootVersion', label: 'BOOT出厂版本' },
    { key: 'cpldVersion', label: 'CPLD出厂版本' },
    { key: 'pcbVersion', label: 'PCB出厂版本' }
  ],
  project: [
    { key: 'projectType', label: '项目业务类型' },
    { key: 'projectCode', label: '项目编码' },
    { key: 'projectName', label: '项目名称' },
    { key: 'lifecycleStatus', label: '生命周期状态' },
    { key: 'currentStage', label: '当前阶段' },
    { key: 'closedAt', label: '闭环时间' },
    { key: 'departmentCode', label: '办事处编码' },
    { key: 'companyId', label: '公司ID' },
    { key: 'companyCode', label: '公司编码' },
    { key: 'companyName', label: '公司名称' },
    { key: 'marketName', label: '市场部名称' },
    { key: 'systemName', label: '系统部名称' },
    { key: 'expendName', label: '拓展部名称' },
    { key: 'industryName', label: '子行业名称' },
    { key: 'notTrackReason', label: '不予跟踪原因' },
    { key: 'orderCreateTime', label: '项目创建时订单创建时间' },
    { key: 'projectCategory', label: '项目类别' },
    { key: 'signingMethod', label: '签约方式' },
    { key: 'implementationMode', label: '实施方式' },
    { key: 'finalCustomerNames', label: '产品实际最终用户' },
    { key: 'customerProjectName', label: '客户项目名称' },
    { key: 'salesType', label: '销售类型' },
    { key: 'majorProjectLevel', label: '重大项目级别' },
    { key: 'creator', label: '创建人' },
    { key: 'createTime', label: '创建时间' },
    { key: 'updater', label: '更新人' },
    { key: 'updateTime', label: '更新时间' },
    { key: 'effectiveFrom', label: '项目有效期开始时间' },
    { key: 'effectiveTo', label: '项目有效期结束时间' },
    { key: 'disabled', label: '项目失效/核销标记' },
    { key: 'projectStartTime', label: '项目开始时间' },
    { key: 'projectRefreshTime', label: '项目刷新时间' },
    { key: 'projectCloseTime', label: '项目闭环时间' },
    { key: 'customInfo', label: '项目自定义业务信息' }
  ]
}
export const getAssociatedData = (
  projectId: LegacyId,
  kind: AssociatedKind,
  pageNo: number,
  pageSize: number
): Promise<{ list: AssociatedRow[]; total: number }> =>
  request.get({
    url: '/api/v1/pms/projects/' + projectId + '/associated-data/records',
    params: { kind, pageNo, pageSize }
  })
