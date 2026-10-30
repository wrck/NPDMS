<template>
  <div class="business-entity-list">
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
    <div class="toolbar">
      <el-tooltip
        :disabled="!!createOperation?.executable"
        :content="createOperation?.reason || '没有可用的创建操作'"
      >
        <span>
          <el-button
            type="primary"
            :disabled="!createOperation?.executable"
            @click="emit('create')"
          >
            新建
          </el-button>
        </span>
      </el-tooltip>
      <el-button :loading="listLoading" @click="emit('reload')">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="listLoading" @row-click="(row: any) => emit('open', row)">
      <el-table-column
        v-for="field in readableFields.slice(0, 8)"
        :key="field.code"
        :label="field.name"
        :prop="field.code"
        min-width="140"
      >
        <template #default="{ row }">{{ displayValue(row.fieldValues[field.code], field.type) }}</template>
      </el-table-column>
      <el-table-column label="并发依据" width="100">
        <template #default="{ row }">{{ row.concurrencyBasis ?? '-' }}</template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <span v-if="!rows.length && !listLoading">暂无数据</span>
      <el-button v-if="!sliceComplete && rows.length" :loading="listLoading" link type="primary" @click="emit('load-more')">
        加载更多
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { BusinessEntityData, FieldVO, OperationVO } from '@/api/pms/platform/businessmodel'

defineOptions({ name: 'BusinessEntityList' })
defineProps<{
  rows: BusinessEntityData[]
  readableFields: FieldVO[]
  createOperation?: OperationVO
  listLoading?: boolean
  listError?: string
  sliceComplete?: boolean
}>()
const emit = defineEmits<{ create: []; open: [row: BusinessEntityData]; reload: []; 'load-more': [] }>()

const displayValue = (value: unknown, type: string) => {
  if (value == null || value === '') return '-'
  if (Array.isArray(value)) return value.join('、')
  if (type === 'BOOLEAN') return value ? '是' : '否'
  if (type === 'OBJECT_LIST') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.business-entity-list .toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
