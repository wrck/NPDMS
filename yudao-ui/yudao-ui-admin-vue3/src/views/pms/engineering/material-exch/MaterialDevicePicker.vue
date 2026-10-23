<template>
  <div class="material-picker">
    <el-alert v-if="!validProject" title="请先选择项目" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table ref="table" data-testid="device-candidates" v-loading="loading" :data="pageRows" row-key="key" border max-height="300" size="small"
      @select="selectRow" @select-all="selectPage">
      <el-table-column type="selection" width="45" />
      <el-table-column label="销售订单" min-width="155" show-overflow-tooltip>
        <template #default="{ row }">{{ row.orderNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="产品编码" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.itemCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="型号" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.name || '-' }}</template>
      </el-table-column>
      <el-table-column label="项目数量" width="100">
        <template #default="{ row }">{{ row.allocatedQuantity ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="换货数量" width="150">
        <template #default="{ row }">
          <el-input-number :model-value="quantityOf(row)" :min="1" :max="Math.floor(row.allocatedQuantity)" :precision="0" size="small"
            :disabled="!isSelected(row)" aria-label="换货数量" class="!w-130px"
            @update:model-value="setQuantity(row.key, $event)" />
        </template>
      </el-table-column>
    </el-table>
    <Pagination data-testid="device-pagination" v-model:page="pageNo" v-model:limit="pageSize" :total="groupRows.length"
      @pagination="restoreChecks" />
    <div class="selection-summary">已选择 {{ selectedRows.length }} 行，换货数量合计 {{ quantityTotal }}（翻页保留选择）</div>
    <el-table v-if="modelValue.length" :data="selectedRows" row-key="key" border max-height="220" size="small">
      <el-table-column label="销售订单" min-width="155" show-overflow-tooltip>
        <template #default="{ row }">{{ row.orderNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="产品编码" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.itemCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="型号" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.name || '-' }}</template>
      </el-table-column>
      <el-table-column label="项目数量" width="100">
        <template #default="{ row }">{{ row.allocatedQuantity ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="换货产品" min-width="190">
        <template #default="{ row }">
          <ProductOfficialSelect
            :model-value="row.productId"
            @update:model-value="setExchangeProduct(row.key, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="换货数量" width="150">
        <template #default="{ row }">
          <el-input-number :model-value="row.quantity ?? 1" :min="1" :max="row.allocatedQuantity === undefined ? undefined : Math.floor(row.allocatedQuantity)" :precision="0" size="small"
            aria-label="换货数量" class="!w-130px" @update:model-value="setQuantity(row.key, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ row }"><el-button link type="danger" @click="remove(row.key)">移除</el-button></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { getDeliveryScopePage } from '@/api/pms/commerce'
import { isBusinessViewId, legacyOwnerId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import type { MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'
import ProductOfficialSelect from './ProductOfficialSelect.vue'

/** 设备清单行 = 合同对应销售订单行的交付范围分配（范围明细拆分行或未拆分范围行），与项目详情设备清单Tab同口径。 */
interface ScopeLineRow {
  key: string
  scopeDetailId?: number
  scopeId: number
  contractNo?: string
  orderNo: string
  lineNo: string
  itemCode: string
  name: string
  productCode: string
  deviceTypeCode: string
  deviceTypeName: string
  allocatedQuantity: number
  status: string
}

const props = defineProps<{ projectId?: BusinessViewId; modelValue: MaterialExchangeSerialVO[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: MaterialExchangeSerialVO[]] }>()
const validProject = computed(() => isBusinessViewId(props.projectId))
const table = ref()
const loading = ref(false)
const error = ref('')
const rows = ref<ScopeLineRow[]>([])
const quantities = reactive<Record<string, number>>({})
const pageNo = ref(1)
const pageSize = ref(20)
interface ScopeGroup extends ScopeLineRow { members: ScopeLineRow[] }
const groupRows = computed<ScopeGroup[]>(() => {
  const groups = new Map<string, ScopeGroup>()
  for (const row of rows.value) {
    const key = row.contractNo && row.itemCode ? JSON.stringify([row.contractNo, row.itemCode]) : row.key
    const group = groups.get(key)
    if (group) {
      group.members.push(row)
      group.allocatedQuantity += Number(row.allocatedQuantity || 0)
      group.orderNo = [...new Set(group.members.map(item => item.orderNo).filter(Boolean))].sort().join('、')
    } else groups.set(key, { ...row, key, allocatedQuantity: Number(row.allocatedQuantity || 0), members: [row] })
  }
  return [...groups.values()].sort((a, b) =>
    a.itemCode.localeCompare(b.itemCode, 'zh-CN', { numeric: true }))
})
const pageRows = computed(() =>
  groupRows.value.slice((pageNo.value - 1) * pageSize.value, pageNo.value * pageSize.value))
const quantityTotal = computed(() =>
  props.modelValue.reduce((sum, row) => sum + (row.quantity ?? 1), 0))
let requestVersion = 0

const keyOf = (row: { scopeDetailId?: number; scopeId?: number; deviceId?: number }) =>
  row.scopeDetailId ? `D${row.scopeDetailId}`
    : row.scopeId ? `S${row.scopeId}`
    : row.deviceId ? `V${row.deviceId}` : ''
const selectedMembers = (group: ScopeGroup) => props.modelValue.filter(item => group.members.some(row => keyOf(item) === row.key))
const isSelected = (group: ScopeGroup) => selectedMembers(group).length > 0
const quantityOf = (group: ScopeGroup) => isSelected(group)
  ? selectedMembers(group).reduce((sum, item) => sum + (item.quantity ?? 1), 0) : quantities[group.key] ?? 1
const selectedRows = computed(() => {
  const selected = groupRows.value.filter(isSelected).map(group => ({ ...group,
    quantity: quantityOf(group), productId: selectedMembers(group)[0]?.productId }))
  const known = new Set(rows.value.map(row => row.key))
  return [...selected, ...props.modelValue.filter(item => !known.has(keyOf(item))).map(item => ({
    ...item, key: keyOf(item), name: '-', allocatedQuantity: undefined
  }))]
})
const setQuantity = (key: string, value: number | undefined) => {
  const group = groupRows.value.find(row => row.key === key)
  if (!group || !value || !Number.isInteger(value) || value < 1 || value > Math.floor(group.allocatedQuantity)) {
    error.value = '换货数量须为正整数，且不能超过项目数量'
    return
  }
  error.value = ''
  quantities[key] = value
  if (isSelected(group)) emit('update:modelValue', [
    ...props.modelValue.filter(item => !group.members.some(row => row.key === keyOf(item))), ...allocate(group, value)
  ])
}
/** 换货产品按行设置；可留空草稿后补，快照组由服务端按产品引用写入 */
const setExchangeProduct = (key: string, productId: number | undefined) => {
  const group = groupRows.value.find(row => row.key === key)
  emit('update:modelValue', props.modelValue.map(item =>
    (group ? group.members.some(row => row.key === keyOf(item)) : keyOf(item) === key) ? { ...item, productId } : item))
}
const restoreChecks = async () => {
  await nextTick()
  for (const row of pageRows.value) table.value?.toggleRowSelection(row, isSelected(row))
}
/** 展平交付范围为清单行：明细拆分行带稳定明细ID；未拆分范围为基行，与设备清单Tab同口径。 */
const flatten = (result: { list?: Array<Record<string, any>> }) => (result.list || []).flatMap((scope: any) => {
  const base = { scopeId: scope.id, contractNo: scope.contractNo, orderNo: scope.orderNo || '', lineNo: scope.lineNo || '',
    itemCode: scope.productCode || scope.itemCode || '', deviceTypeName: '' }
  const details = scope.details || []
  if (!details.length) {
    return [{ ...base, key: `S${scope.id}`, scopeDetailId: undefined, name: scope.productDesc || scope.itemDesc || '',
      productCode: scope.productCode || '', deviceTypeCode: '',
      allocatedQuantity: scope.allocatedQuantity, status: scope.scopeStatus || '' }]
  }
  return details.map((detail: any) => ({
    ...base, key: `D${detail.id}`, scopeDetailId: detail.id,
    name: scope.productDesc || scope.itemDesc || '',
    productCode: detail.productCode || '', deviceTypeCode: detail.deviceTypeCode || '',
    deviceTypeName: detail.deviceTypeName || '',
    allocatedQuantity: detail.allocatedQuantity, status: detail.status || scope.scopeStatus || ''
  }))
})
const load = async () => {
  const version = ++requestVersion
  rows.value = []
  error.value = ''
  pageNo.value = 1
  loading.value = false
  const projectId = props.projectId
  if (!isBusinessViewId(projectId)) return
  loading.value = true
  try {
    const all: Array<Record<string, any>> = []
    for (let page = 1; ; page++) {
      const result = await getDeliveryScopePage({ projectId: legacyOwnerId(projectId), pageNo: page, pageSize: 200, includeHistory: false })
      if (version !== requestVersion) return
      all.push(...result.list)
      if (all.length >= result.total) break
      if (!result.list.length) throw new Error('交付范围分页不完整')
    }
    rows.value = flatten({ list: all })
    await restoreChecks()
  } catch {
    if (version === requestVersion) error.value = '设备清单加载失败，请重试'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
const snapshot = (row: ScopeLineRow): MaterialExchangeSerialVO => ({
  scopeDetailId: row.scopeDetailId, scopeId: row.scopeId, contractNo: row.contractNo, quantity: 1,
  orderNo: row.orderNo, lineNo: row.lineNo, itemCode: row.itemCode,
  productName: row.name, productCode: row.productCode,
  deviceTypeCode: row.deviceTypeCode, deviceTypeName: row.deviceTypeName
})
const allocate = (group: ScopeGroup, quantity: number): MaterialExchangeSerialVO[] => {
  let remaining = quantity
  const productId = selectedMembers(group)[0]?.productId
  const result: MaterialExchangeSerialVO[] = []
  const sorted = [...group.members].sort((a, b) => a.orderNo.localeCompare(b.orderNo, 'zh-CN', { numeric: true })
    || a.lineNo.localeCompare(b.lineNo, 'zh-CN', { numeric: true }) || a.key.localeCompare(b.key))
  for (const row of sorted) {
    const allocated = Math.min(remaining, Math.max(0, Math.floor(Number(row.allocatedQuantity || 0))))
    if (allocated > 0) result.push({ ...snapshot(row), quantity: allocated, productId })
    remaining -= allocated
    if (!remaining) break
  }
  return result
}
const selectRow = (selection: ScopeGroup[], row: ScopeGroup) => {
  const remaining = props.modelValue.filter(item => !row.members.some(member => member.key === keyOf(item)))
  const selected = selection.some(item => item.key === row.key)
  if (selected && Math.floor(row.allocatedQuantity) < 1) {
    error.value = '该产品没有可分配的项目数量'
    void restoreChecks()
    return
  }
  error.value = ''
  emit('update:modelValue', selected ? [...remaining, ...allocate(row, quantityOf(row))] : remaining)
}
const selectPage = (selection: ScopeGroup[]) => {
  const pageKeys = new Set(pageRows.value.flatMap(row => row.members.map(member => member.key)))
  emit('update:modelValue', [
    ...props.modelValue.filter(item => !pageKeys.has(keyOf(item))),
    ...selection.filter(row => Math.floor(row.allocatedQuantity) >= 1).flatMap(row => allocate(row, quantityOf(row)))
  ])
}
const remove = (key: string) => {
  const group = groupRows.value.find(row => row.key === key)
  emit('update:modelValue', props.modelValue.filter(item =>
    !(group ? group.members.some(row => row.key === keyOf(item)) : keyOf(item) === key)))
}
watch(() => props.modelValue, restoreChecks, { deep: true })
watch(() => props.projectId, load, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>

<style scoped>
.material-picker { width: 100%; }
.selection-summary { clear: both; padding: 12px 0; color: var(--el-text-color-secondary); }
</style>
