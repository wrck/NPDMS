<template>
  <div class="widget-placeholder chart-preview" :style="{ width: '100%', height: height + 'px' }">
    <div style="padding: 16px; color: #909399; text-align: center">{{ displayName }}（chartType={{ chartType }}）— 占位</div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{ modelValue: any; chartType?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const displayName = '图表预览'
const chartType = props.chartType ?? 'bar'
const height = 300
const model = ref(props.modelValue)
</script>
