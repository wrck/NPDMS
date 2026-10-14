<template>
  <div class="table-editor">
    <el-table :data="rows" border size="small">
      <el-table-column label="产品编码" min-width="160">
        <template #default="{ row }">
          <span v-if="readOnly">{{ row.productCode || '—' }}</span>
          <el-select v-else v-model="row.productCode" filterable placeholder="选择项目产品编码" @change="selectProduct(row)">
            <el-option v-for="product in products" :key="product.productCode" :value="product.productCode" :label="product.productCode" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column v-for="column in columns" :key="column.field" :label="column.label" :min-width="column.width">
        <template #default="{ row }">
          <span v-if="readOnly">{{ row[column.field] || '—' }}</span>
          <el-input v-else v-model="row[column.field]" :aria-label="column.label" />
        </template>
      </el-table-column>
      <el-table-column v-if="!readOnly" label="操作" width="70" fixed="right">
        <template #default="{ $index }"><el-button link type="danger" @click="removeRow($index)">删除</el-button></template>
      </el-table-column>
    </el-table>
    <template v-if="!readOnly">
      <el-button class="mt-4px" @click="addRow">添加行</el-button>
      <el-button class="mt-4px" type="primary" plain :disabled="!products.length" @click="synchronize">从项目设备清单同步</el-button>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectEquipmentProduct } from '@/api/pms/commerce/projectEquipment'
import { equipmentRow, type EquipmentRow } from './solutionEquipment'
const props = defineProps<{ products: ProjectEquipmentProduct[]; readOnly: boolean; section: 'deploy' | 'software' }>()
const rows = defineModel<EquipmentRow[]>({ required: true })
const columns = computed(() => [
  { field: 'deviceName', label: '设备名称', width: 170 },
  { field: 'quantity', label: '数量（订单数量）', width: 130 },
  { field: 'location', label: '安装位置（机房/机柜/U数）', width: 220 },
  { field: 'version', label: '软件版本', width: 140 },
  { field: 'remark', label: '备注', width: 160 }
].filter(column => column.field !== (props.section === 'deploy' ? 'version' : 'location')))
const selectProduct = (row: EquipmentRow) => {
  if (props.readOnly) return
  const product = props.products.find(product => product.productCode === row.productCode)
  if (!product) return
  const selected = equipmentRow(product)
  row.deviceName = selected.deviceName
  row.quantity = selected.quantity
}
const addRow = () => { if (!props.readOnly) rows.value = [...rows.value, equipmentRow()] }
const removeRow = (index: number) => { if (!props.readOnly) rows.value = rows.value.filter((_, i) => i !== index) }
const synchronize = () => { if (!props.readOnly) rows.value = props.products.map(product => equipmentRow(product)) }
</script>
