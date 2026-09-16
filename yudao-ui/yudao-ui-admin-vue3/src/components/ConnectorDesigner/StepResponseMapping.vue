<!-- src/components/ConnectorDesigner/StepResponseMapping.vue -->
<script setup lang="ts">
/**
 * 连接器分步表单 — Step 4 响应映射。
 *
 * <p>响应字段 → 实体字段映射表格：sourcePath（JSONPath）+ targetField + transform。
 * 用于将连接器返回的原始响应转换为业务实体字段结构。</p>
 */
import type { ResponseMapping } from '@/api/lowcode-connector'

const props = defineProps<{
  responseMapping: ResponseMapping[]
}>()

const emit = defineEmits<{
  'update:responseMapping': [value: ResponseMapping[]]
}>()

function addRow() {
  emit('update:responseMapping', [
    ...props.responseMapping,
    { sourcePath: '', targetField: '', transform: null }
  ])
}

function removeRow(index: number) {
  const updated = [...props.responseMapping]
  updated.splice(index, 1)
  emit('update:responseMapping', updated)
}

function updateRow(index: number, patch: Partial<ResponseMapping>) {
  const updated = [...props.responseMapping]
  updated[index] = { ...updated[index], ...patch }
  emit('update:responseMapping', updated)
}
</script>

<template>
  <div class="step-response-mapping">
    <div class="step-toolbar">
      <el-button type="primary" size="small" @click="addRow">新增映射</el-button>
      <span class="hint">将响应字段（JSONPath）映射到目标实体字段</span>
    </div>
    <el-table :data="props.responseMapping" border size="small">
      <el-table-column label="序号" width="60" align="center" type="index" />
      <el-table-column label="响应字段 (JSONPath)" min-width="220">
        <template #default="{ row, $index }">
          <el-input
            :model-value="row.sourcePath"
            placeholder="$.data.id"
            @update:model-value="(v: string) => updateRow($index, { sourcePath: v })"
          />
        </template>
      </el-table-column>
      <el-table-column label="目标字段" min-width="160">
        <template #default="{ row, $index }">
          <el-input
            :model-value="row.targetField"
            placeholder="id"
            @update:model-value="(v: string) => updateRow($index, { targetField: v })"
          />
        </template>
      </el-table-column>
      <el-table-column label="转换表达式" min-width="200">
        <template #default="{ row, $index }">
          <el-input
            :model-value="row.transform || ''"
            placeholder="可选，如 toString(#value)"
            @update:model-value="(v: string) => updateRow($index, { transform: v || null })"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ $index }">
          <el-button size="small" link type="danger" @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="props.responseMapping.length === 0"
      description="暂无映射规则，可点击「新增映射」"
      :image-size="60"
    />
  </div>
</template>

<script lang="ts">
export default { name: 'StepResponseMapping' }
</script>

<style scoped lang="scss">
.step-response-mapping {
  .step-toolbar {
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
