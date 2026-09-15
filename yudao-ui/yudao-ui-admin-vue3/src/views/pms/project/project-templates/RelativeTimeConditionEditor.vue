<template>
  <div class="relative-time-editor">
    <el-select
      :model-value="parameters.anchor"
      :disabled="disabled || !available"
      aria-label="等待起算点"
      @update:model-value="selectAnchor"
    >
      <el-option value="NODE_ACTIVATED" label="本节点激活时间" :disabled="!activationAllowed" />
      <el-option value="NODE_COMPLETED" label="来源节点本轮完成时间" :disabled="!options" />
    </el-select>
    <el-select
      v-if="parameters.anchor === 'NODE_COMPLETED'"
      :model-value="parameters.sourceNodeKey"
      :disabled="disabled || !available"
      filterable
      aria-label="等待来源节点"
      placeholder="选择阶段或任务"
      @update:model-value="selectSource"
    >
      <el-option
        v-for="source in options?.sources ?? []"
        :key="source.key"
        :value="source.key"
        :label="source.label"
        :disabled="source.disabled"
      />
      <el-option
        v-if="parameters.sourceNodeKey && !selectedSource"
        :value="String(parameters.sourceNodeKey)"
        label="来源节点已移除，请重新选择"
        disabled
      />
    </el-select>
    <el-input-number
      :model-value="amount"
      :min="0"
      :disabled="disabled || !available"
      controls-position="right"
      aria-label="等待时长"
      placeholder="输入时长"
      @update:model-value="setAmount"
    />
    <el-select v-model="unit" :disabled="disabled" aria-label="等待时长单位">
      <el-option v-for="(label, value) in waitUnits" :key="value" :value="value" :label="label" />
    </el-select>
    <span class="condition-hint">按当前有效轮次计时；1 天为 24 小时，缺少起算时间时结果未知。</span>
    <span v-if="invalidContext" role="alert" class="condition-hint"
      >{{ invalidContext }}原条件已保留，请重新选择或复制为独立规则。</span
    >
    <span v-if="parameters.duration && amount === undefined" role="alert" class="condition-hint">
      无法按经过时长显示“{{ parameters.duration }}”，请重新输入；未修改前仍保留原值。
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import type { JsonObject } from '@/api/pms/project/project-templates'
import {
  preferredWaitUnit,
  relativeTimeOptionsKey,
  waitAmount,
  waitDuration,
  waitUnits
} from './relativeTimeModel'

const props = defineProps<{ parameters: JsonObject; disabled?: boolean }>()
const emit = defineEmits<{ change: [parameters: JsonObject] }>()
const options = inject(
  relativeTimeOptionsKey,
  computed(() => undefined)
)
const available = computed(() => options.value?.available ?? true)
const activationAllowed = computed(() => options.value?.activation ?? true)
const selectedSource = computed(() =>
  options.value?.sources.find((source) => source.key === props.parameters.sourceNodeKey)
)
const unit = ref(preferredWaitUnit(props.parameters.duration))
const amount = computed(() => waitAmount(props.parameters.duration, unit.value))
const invalidContext = computed(() => {
  if (!available.value) return '此规则用途不能配置相对等待。'
  if (props.parameters.anchor === 'NODE_ACTIVATED' && !activationAllowed.value)
    return '准入或收口不能等待自身激活。'
  if (
    props.parameters.anchor === 'NODE_COMPLETED' &&
    (!selectedSource.value || selectedSource.value.disabled)
  )
    return '请选择本版本中其他来源阶段或任务。'
  return ''
})
const selectAnchor = (anchor: string) => {
  if (props.disabled || !available.value || anchor === props.parameters.anchor) return
  if (anchor === 'NODE_ACTIVATED' && activationAllowed.value)
    emit('change', { anchor, duration: props.parameters.duration })
  if (anchor === 'NODE_COMPLETED' && options.value)
    emit('change', { anchor, duration: props.parameters.duration, sourceNodeKey: '' })
}
const selectSource = (key: string) => {
  if (props.disabled || !available.value || props.parameters.anchor !== 'NODE_COMPLETED') return
  if (options.value?.sources.some((source) => source.key === key && !source.disabled))
    emit('change', { ...props.parameters, sourceNodeKey: key })
}
const setAmount = (value: number | undefined) => {
  if (!props.disabled && available.value)
    emit('change', { ...props.parameters, duration: waitDuration(value, unit.value) })
}
</script>

<style scoped>
.relative-time-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.relative-time-editor :deep(.el-select) {
  width: 200px;
  max-width: 100%;
}
.condition-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
