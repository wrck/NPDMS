<template>
  <div class="project-facts">
    <section class="fact-section legacy-project-fields" aria-label="项目基础信息">
      <dl class="fact-grid">
        <div v-for="field in baseFields" :key="field.label" class="fact" :class="{ 'fact-wide': field.wide }">
          <dt>{{ field.label }}</dt>
          <dd>{{ field.value || '—' }}</dd>
        </div>
      </dl>
    </section>
    <section class="fact-section" v-loading="loading">
      <div class="section-heading"><h3>合同与执行单</h3><el-button v-if="error" link type="primary" @click="load">重新加载</el-button></div>
      <el-alert v-if="error" :title="error" type="warning" :closable="false" />
      <p v-else-if="!canReadCommerce" class="empty-note">暂无商务资料查看权限。</p>
      <template v-else>
        <div v-for="contract in commerce?.contracts || []" :key="contract.id" class="source-record">
          <div class="record-heading"><el-tag effect="plain">合同</el-tag><strong>{{ contract.contractNo }}</strong><span>{{ contract.contractName }}</span></div>
          <dl class="fact-grid">
            <div class="fact"><dt>签约公司</dt><dd>{{ contract.companyName || contract.companyCode || '—' }}</dd></div>
            <div class="fact"><dt>合同客户</dt><dd>{{ contract.customerName || '—' }}</dd></div>
            <div class="fact"><dt>合同金额</dt><dd>{{ amount(contract.contractAmount, contract.currencyCode) }}</dd></div>
            <div class="fact"><dt>币种</dt><dd>{{ contract.currencyCode || '—' }}</dd></div>
          </dl>
        </div>
        <div v-for="order in commerce?.orders || []" :key="order.id" class="source-record">
          <div class="record-heading"><el-tag type="info" effect="plain">销售订单</el-tag><strong>{{ order.orderNo }}</strong></div>
          <dl class="fact-grid">
            <div class="fact"><dt>订单创建时间</dt><dd>{{ time(order.orderCreateTime) }}</dd></div>
            <div class="fact"><dt>下单公司</dt><dd>{{ order.companyName || '—' }}</dd></div>
            <div class="fact"><dt>订单金额</dt><dd>{{ amount(order.orderAmount, order.currencyCode) }}</dd></div>
            <div class="fact"><dt>销售类型</dt><dd>{{ order.salesType || '—' }}</dd></div>
            <div class="fact"><dt>客户要求时间</dt><dd>{{ time(order.customerRequiredTime) }}</dd></div>
            <div class="fact"><dt>主执行单号</dt><dd>{{ order.executionNo || '—' }}</dd></div>
          </dl>
        </div>
        <div v-for="order in commerce?.executionOrders || []" :key="order.id" class="source-record">
          <div class="record-heading"><el-tag type="info" effect="plain">执行单</el-tag><strong>{{ order.executionNo }}</strong></div>
          <dl class="fact-grid">
            <div v-for="field in executionFields" :key="field.key" class="fact"><dt>{{ field.label }}</dt><dd>{{ field.dict ? label(order[field.key], field.dict) : order[field.key] || '—' }}</dd></div>
            <div class="fact"><dt>提交时间</dt><dd>{{ time(order.submitTime) }}</dd></div>
            <div class="fact"><dt>同步时间</dt><dd>{{ time(order.sourceSyncTime) }}</dd></div>
          </dl>
        </div>
        <p v-if="!loading && !error && !commerce?.contracts.length && !commerce?.orders.length && !commerce?.executionOrders.length" class="empty-note">尚未关联商务资料；关联后展示合同、订单及执行单信息。</p>
      </template>
    </section>
    <section class="fact-section">
      <h3>补充信息</h3>
      <dl class="fact-grid">
        <div class="fact"><dt>客户单位</dt><dd>{{ project.customerName || project.customerCode || '—' }}</dd></div>
        <div class="fact"><dt>用户服务等级</dt><dd>{{ project.serviceLevelCode || '—' }}</dd></div>
        <div class="fact"><dt>业务层级</dt><dd>{{ project.businessLevelName || project.businessLevelCode || '—' }}</dd></div>
        <div class="fact"><dt>项目结束日期（工勘要求）</dt><dd>{{ project.projectEndDate || '—' }}</dd></div>
        <div class="fact fact-wide"><dt>实施地点</dt><dd>{{ project.implementationLocation || '—' }}</dd></div>
        <div class="fact fact-wide"><dt>创建原因</dt><dd>{{ project.creationReason || '—' }}</dd></div>
        <div class="fact"><dt>创建时间</dt><dd>{{ time(project.createTime) }}</dd></div>
        <div class="fact"><dt>创建来源</dt><dd>{{ label(project.sourceType, DICT_TYPE.PMS_PROJECT_SOURCE_TYPE) }}</dd></div>
      </dl>
    </section>
  </div>
</template>

<script setup lang="ts">
import request from '@/config/axios'
import { checkPermi } from '@/utils/permission'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import { getMemberPage, type MemberRecord } from '@/api/pms/project/unified-members'

const props = defineProps<{ project: ProjectMasterVO }>()
interface CommerceOverview {
  contracts: { id: number; contractNo: string; contractName?: string; companyName?: string; companyCode?: string; customerName?: string; currencyCode?: string; contractAmount?: number | null }[]
  orders: { id: number; orderNo: string; companyName?: string; orderCreateTime?: string; orderAmount?: number | null; currencyCode?: string; salesType?: string; customerRequiredTime?: string; executionNo?: string }[]
  executionOrders: ({ id: number; executionNo: string } & Record<string, any>)[]
}
const commerce = ref<CommerceOverview>()
type MarketSource = Pick<ProjectMasterVO, 'marketName' | 'marketCode' | 'systemName' | 'systemCode' | 'expendName' | 'expendCode' | 'industryName' | 'industryCode'>
const scopedMarketSources = ref<MarketSource[]>([])
const marketPath = (source: MarketSource) => [source.marketName || source.marketCode, source.systemName || source.systemCode, source.expendName || source.expendCode, source.industryName || source.industryCode].filter(Boolean).join(' / ')
const members = ref<MemberRecord[]>([])
const loading = ref(false)
const error = ref('')
const memberError = ref(false)
const canReadCommerce = computed(() => checkPermi(['pms:commerce:contract:query']))
const label = (value: string | null | undefined, dict: DICT_TYPE) => value ? getDictLabel(dict, value) || value : '—'
const time = (value: any) => value ? formatDate(value) : '—'
const amount = (value?: number | null, currency?: string) => value == null ? '—' : `${currency || ''} ${value}`.trim()
const names = (roles: string[], exclude?: number) => members.value.filter(m => roles.includes(m.memberRole) && m.userId !== exclude).map(m => m.memberName || m.employeeNo).filter(Boolean).join('、') || (memberError.value ? '人员信息加载失败' : '—')
const party = (role: string) => props.project.parties?.filter(p => p.role === role).map(p => p.name || p.code).filter(Boolean).join('、') || '—'
interface FactField { label: string; value?: string | null; wide?: boolean }
const salesRepresentative = computed(() => {
  const assigned = names(['SALES_REPRESENTATIVE'])
  if (assigned !== '—' && !memberError.value) return assigned
  const sourceNames = [...new Set((commerce.value?.executionOrders || []).map(order => order.salesRepName || order.salesRepCode).filter(Boolean))]
  return sourceNames.length ? `${sourceNames.join('、')}（执行单）` : assigned
})
const baseFields = computed<FactField[]>(() => {
  const p = props.project
  const orders = commerce.value?.orders || []
  const orderCreated = orders.map(order => orders.length > 1
    ? `${order.orderNo}：${time(order.orderCreateTime)}` : time(order.orderCreateTime)).join('\n')
  return [
    { label: '合同号', value: [...new Set([p.contractNo, ...(commerce.value?.contracts || []).map(contract => contract.contractNo)].filter(Boolean))].join('、') },
    { label: '销售代表', value: salesRepresentative.value },
    { label: '办事处', value: p.departmentName || p.departmentCode },
    { label: '所属公司', value: p.companyName || p.companyCode },
    { label: '市场部', value: marketPath(p) || [...new Set(scopedMarketSources.value.map(marketPath).filter(Boolean))].join('\n') },
    { label: '订单创建时间', value: orderCreated },
    { label: '项目开始时间', value: p.projectStartTime ? formatDate(p.projectStartTime, 'YYYY-MM-DD') : '—' },
    { label: '项目类别', value: label(p.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD) },
    { label: '重大项目级别', value: label(p.majorProjectLevel, DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL) },
    { label: '服务经理', value: names(['SERVICE_MANAGER', 'SERVICE_MANAGER_L1', 'SERVICE_MANAGER_L2']) },
    { label: '项目类型', value: label(p.projectCategory, DICT_TYPE.PMS_PROJECT_CATEGORY) },
    { label: '主责项目经理', value: p.managerName },
    { label: '其他项目经理', value: names(['PROJECT_MANAGER'], p.managerId) },
    { label: '实施方式', value: label(p.implementationMode, DICT_TYPE.PMS_IMPLEMENTATION_METHOD) },
    { label: '下单/施工代理商', value: party('AGENT') },
    { label: '服务提供商', value: party('SERVICE_PROVIDER') },
    { label: '最终客户单位', value: party('FINAL_CUSTOMER') },
    { label: '客户项目名称', value: p.customerProjectName },
    { label: '不予跟踪原因', value: p.notTrackReason, wide: true }
  ]
})
const executionFields: { key: string; label: string; dict?: DICT_TYPE }[] = [
  { key: 'salesRepName', label: '销售代表' }, { key: 'departmentName', label: '办事处' },
  { key: 'marketName', label: '市场' }, { key: 'systemName', label: '系统' },
  { key: 'expendName', label: '拓展' }, { key: 'industryName', label: '行业' },
  { key: 'companyName', label: '所属公司' }, { key: 'customerProjectName', label: '客户项目名称' },
  { key: 'finalCustomerName', label: '最终客户单位' }, { key: 'agentName', label: '下单代理商' },
  { key: 'projectManagerName', label: '执行单项目经理' }, { key: 'serviceTypeName', label: '服务类型' },
  { key: 'channelName', label: '渠道' },
  { key: 'projectCode', label: 'CRM项目编码' }, { key: 'projectName', label: 'CRM项目名称' },
  { key: 'projectAmount', label: '项目金额' },
  { key: 'majorProjectLevel', label: '重大项目级别', dict: DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL },
  { key: 'projectType', label: '项目类型', dict: DICT_TYPE.PMS_PROJECT_TYPE }
]
let requestId = 0
const load = async () => {
  const id = props.project.id
  const current = ++requestId
  commerce.value = undefined; scopedMarketSources.value = []; members.value = []; error.value = ''; memberError.value = false
  if (!id) return
  loading.value = true
  await Promise.all([
    (async () => {
      if (!canReadCommerce.value) return
      try {
        const value = await request.get<CommerceOverview>({ url: `/api/v1/pms/contracts/project/${id}/overview` })
        if (current !== requestId) return
        commerce.value = value
        if (!marketPath(props.project)) {
          const executionNos = new Set(value.orders.map(order => order.executionNo).filter(Boolean))
          const sources = await Promise.all(value.contracts.map(contract => request.get<{ executionOrders: (MarketSource & { executionNo: string })[] }>({ url: `/api/v1/pms/contracts/${contract.id}/creation-source` })))
          if (current === requestId) scopedMarketSources.value = sources.flatMap(source => source.executionOrders).filter(execution => executionNos.has(execution.executionNo))
        }
      } catch { if (current === requestId) error.value = '商务资料加载失败，请重试。' }
    })(),
    (async () => {
      try {
        let pageNo = 1
        const values: MemberRecord[] = []
        while (true) {
          const result = await getMemberPage(id, { pageNo, pageSize: 100, state: 'CURRENT' })
          if (current !== requestId) return
          values.push(...result.list)
          if (values.length >= result.total || !result.list.length) break
          pageNo++
        }
        members.value = values
      } catch { if (current === requestId) memberError.value = true }
    })()
  ])
  if (current === requestId) loading.value = false
}
watch(() => [props.project.id, props.project.version], load, { immediate: true })
onBeforeUnmount(() => { requestId++ })
</script>

<style scoped>
.project-facts { padding: 0 10px; }
.fact-section { padding: 24px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.fact-section:first-child { padding-top: 12px; }
.fact-section:last-child { border-bottom: 0; }
h3 { margin: 0 0 20px; font-size: 15px; line-height: 22px; font-weight: 600; color: var(--el-text-color-primary); }
.fact-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 210px), 1fr)); gap: 22px 32px; margin: 0; }
.fact { min-width: 0; }
dt { margin-bottom: 7px; font-size: 13px; line-height: 20px; color: var(--el-text-color-secondary); }
dd { margin: 0; font-size: 14px; line-height: 22px; color: var(--el-text-color-primary); overflow-wrap: anywhere; white-space: pre-wrap; }
.fact-wide { grid-column: 1 / -1; }
.source-record { padding: 18px 20px; margin-bottom: 12px; background: var(--el-fill-color-extra-light); border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); }
.record-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 18px; font-size: 14px; }
.record-heading span { color: var(--el-text-color-secondary); }
.section-heading { display: flex; justify-content: space-between; align-items: baseline; }
.empty-note { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); line-height: 22px; }
@media (max-width: 600px) { .project-facts { padding: 0 4px; } .fact-grid { gap: 18px; } .source-record { padding: 16px; } }
</style>
