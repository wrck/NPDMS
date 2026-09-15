<template>
  <div class="child-wait-editor">
    <el-select
      :model-value="parameters.scope"
      :disabled="disabled"
      aria-label="子项目等待范围"
      placeholder="选择等待范围"
      @update:model-value="set('scope', $event)"
    >
      <el-option value="DIRECT" label="直接子项目" />
      <el-option value="DESCENDANTS" label="全部子孙项目" />
    </el-select>
    <el-select
      :model-value="parameters.acceptedClosureTypes"
      :disabled="disabled"
      multiple
      aria-label="认可的关闭类型"
      placeholder="选择认可的关闭类型"
      @update:model-value="set('acceptedClosureTypes', $event)"
    >
      <el-option value="NORMAL_CLOSED" label="正常关闭" />
      <el-option value="EXCEPTION_CLOSED" label="异常关闭" />
    </el-select>
    <el-select
      :model-value="parameters.quantifier"
      :disabled="disabled"
      aria-label="子项目满足方式"
      placeholder="选择全部或任一"
      @update:model-value="set('quantifier', $event)"
    >
      <el-option value="ALL" label="全部满足" /><el-option value="ANY" label="任一满足" />
    </el-select>
    <el-select
      :model-value="parameters.emptyResult"
      :disabled="disabled"
      aria-label="无子项目时的结果"
      placeholder="选择无子项目时的结果"
      @update:model-value="set('emptyResult', $event)"
    >
      <el-option :value="true" label="无子项目时满足" /><el-option
        :value="false"
        label="无子项目时不满足"
      />
    </el-select>
    <span class="condition-hint"
      >每个子项目命中任一认可关闭类型即符合要求。关闭事实不可用时结果未知；满足后仍按父项目自身规则运行，不直接关闭或级联关闭。</span
    >
  </div>
</template>

<script setup lang="ts">
import type { JsonObject, JsonValue } from '@/api/pms/project/project-templates'
const props = defineProps<{ parameters: JsonObject; disabled?: boolean }>()
const emit = defineEmits<{ change: [parameters: JsonObject] }>()
const set = (key: string, value: JsonValue) => {
  if (!props.disabled) emit('change', { ...props.parameters, [key]: value })
}
</script>

<style scoped>
.child-wait-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.child-wait-editor :deep(.el-select) {
  width: 220px;
  max-width: 100%;
}
.condition-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
