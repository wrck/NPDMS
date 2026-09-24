<template>
  <div class="material-picker">
    <el-alert v-if="!validProject" title="请先选择项目" type="info" :closable="false" />
    <el-alert v-else title="设备清单为合同对应销售订单行的交付分配汇总；直接勾选清单行并填写换货数量，无需指定序列号。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table ref="table" data-testid="device-candidates" v-loading="loading" :data="pageRows" row-key="key" border max-height="300"
      @select="selectRow" @select-all="selectPage">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="orderNo" label="订单号" min-width="150" />
      <el-table-column prop="lineNo" label="行号" width="90" />
      <el-table-column prop="itemCode" label="物料编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="productCode" label="产品编码" min-width="130" show-overflow-tooltip />
      <el-table-column prop="deviceTypeCode" label="设备类型" width="110" />
      <el-table-column prop="allocatedQuantity" label="分配数量" width="90" />
      <el-table-column prop="status" label="状态" width="100" show-overflow-tooltip />
      <el-table-column label="换货数量" width="150">
        <template #default="{ row }">
          <el-input-number :model-value="quantities[row.key] ?? 1" :min="1" :precision="0" size="small"
            :disabled="!isSelected(row)" aria-label="换货数量" class="!w-130px"
            @update:model-value="setQuantity(row.key, $event)" />
        </template>
      </el-table-column>
    </el-table>
    <Pagination data-testid="device-pagination" v-model:page="pageNo" v-model:limit="pageSize" :total="rows.length"
      @pagination="restoreChecks" />
    <div class="selection-summary">已选择 {{ modelValue.length }} 行，换货数量合计 {{ quantityTotal }}（翻页保留选择）</div>
    <el-table v-if="modelValue.length" :data="modelValue" row-key="key" border max-height="220" size="small">
      <el-table-column prop="orderNo" label="订单号" min-width="140" />
      <el-table-column prop="lineNo" label="行号" width="80" />
      <el-table-column prop="itemCode" label="物料编码" min-width="130" show-overflow-tooltip />
      <el-table-column prop="deviceTypeCode" label="设备类型" width="100" />
      <el-table-column label="换货产品" min-width="190">
        <template #default="{ row }">
          <ProductOfficialSelect
            :model-value="row.productId"
            @update:model-value="setExchangeProduct(keyOf(row), $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="换货数量" width="150">
        <template #default="{ row }">
          <el-input-number :model-value="row.quantity ?? 1" :min="1" :precision="0" size="small"
            aria-label="换货数量" class="!w-130px" @update:model-value="setQuantity(keyOf(row), $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ row }"><el-button link type="danger" @click="remove(keyOf(row))">移除</el-button></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { getDeliveryScopePage } from '@/api/pms/commerce'
import type { MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'
import ProductOfficialSelect from './ProductOfficialSelect.vue'

/** 设备清单行 = 合同对应销售订单行的交付范围分配（范围明细拆分行或未拆分范围行），与项目详情设备清单Tab同口径。 */
interface ScopeLineRow {
  key: string
  scopeDetailId?: number
  scopeId: number
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

const props = defineProps<{ projectId?: number; modelValue: MaterialExchangeSerialVO[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: MaterialExchangeSerialVO[]] }>()
const validProject = computed(() => Number.isSafeInteger(props.projectId) && Number(props.projectId) > 0)
const table = ref()
const loading = ref(false)
const error = ref('')
const rows = ref<ScopeLineRow[]>([])
const quantities = reactive<Record<string, number>>({})
const pageNo = ref(1)
const pageSize = ref(20)
const pageRows = computed(() =>
  rows.value.slice((pageNo.value - 1) * pageSize.value, pageNo.value * pageSize.value))
const quantityTotal = computed(() =>
  props.modelValue.reduce((sum, row) => sum + (row.quantity ?? 1), 0))
let requestVersion = 0

const keyOf = (row: { scopeDetailId?: number; scopeId?: number; deviceId?: number }) =>
  row.scopeDetailId ? `D${row.scopeDetailId}`
    : row.scopeId ? `S${row.scopeId}`
    : row.deviceId ? `V${row.deviceId}` : ''
const isSelected = (row: ScopeLineRow) => props.modelValue.some(item => keyOf(item) === row.key)
const setQuantity = (key: string, value: number | undefined) => {
  if (!value || value < 1) return
  quantities[key] = value
  emit('update:modelValue', props.modelValue.map(item =>
    keyOf(item) === key ? { ...item, quantity: value } : item))
}
/** 换货产品按行设置；可留空草稿后补，快照组由服务端按产品引用写入 */
const setExchangeProduct = (key: string, productId: number | undefined) => {
  emit('update:modelValue', props.modelValue.map(item =>
    keyOf(item) === key ? { ...item, productId } : item))
}
const restoreChecks = async () => {
  await nextTick()
  const keys = new Set(props.modelValue.map(keyOf))
  for (const row of pageRows.value) table.value?.toggleRowSelection(row, keys.has(row.key))
}
/** 展平交付范围为清单行：明细拆分行带稳定明细ID；未拆分范围为基行，与设备清单Tab同口径。 */
const flatten = (result: { list?: Array<Record<string, any>> }) => (result.list || []).flatMap((scope: any) => {
  const base = { scopeId: scope.id, orderNo: scope.orderNo || '', lineNo: scope.lineNo || '',
    itemCode: scope.itemCode || '', deviceTypeName: '' }
  const details = scope.details || []
  if (!details.length) {
    return [{ ...base, key: `S${scope.id}`, scopeDetailId: undefined, name: scope.itemDesc || '',
      productCode: '', deviceTypeCode: '',
      allocatedQuantity: scope.allocatedQuantity, status: scope.scopeStatus || '' }]
  }
  return details.map((detail: any) => ({
    ...base, key: `D${detail.id}`, scopeDetailId: detail.id,
    name: detail.productName || '',
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
  if (!validProject.value) return
  loading.value = true
  try {
    const result = await getDeliveryScopePage({ projectId: props.projectId, pageNo: 1, pageSize: 200, includeHistory: false })
    if (version !== requestVersion) return
    rows.value = flatten(result)
    await restoreChecks()
  } catch {
    if (version === requestVersion) error.value = '设备清单加载失败，请重试'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
const snapshot = (row: ScopeLineRow): MaterialExchangeSerialVO => ({
  scopeDetailId: row.scopeDetailId, scopeId: row.scopeId, quantity: quantities[row.key] ?? 1,
  orderNo: row.orderNo, lineNo: row.lineNo, itemCode: row.itemCode,
  productName: row.name, productCode: row.productCode,
  deviceTypeCode: row.deviceTypeCode, deviceTypeName: row.deviceTypeName
})
const selectRow = (selection: ScopeLineRow[], row: ScopeLineRow) => {
  const remaining = props.modelValue.filter(item => keyOf(item) !== row.key)
  emit('update:modelValue', selection.some(item => item.key === row.key)
    ? [...remaining, snapshot(row)] : remaining)
}
const selectPage = (selection: ScopeLineRow[]) => {
  const pageKeys = new Set(pageRows.value.map(row => row.key))
  emit('update:modelValue', [
    ...props.modelValue.filter(item => !pageKeys.has(keyOf(item))),
    ...selection.map(snapshot)
  ])
}
const remove = (key: string) => emit('update:modelValue', props.modelValue.filter(item => keyOf(item) !== key))
watch(() => props.modelValue, restoreChecks, { deep: true })
watch(() => props.projectId, load, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>

<style scoped>
.material-picker { width: 100%; }
.selection-summary { clear: both; padding: 12px 0; color: var(--el-text-color-secondary); }
</style>
