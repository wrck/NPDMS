<template>
  <div class="widget-placeholder signature-pad">
    <canvas ref="canvasRef" :width="width" :height="height" style="border: 1px solid #dcdfe6; background: #fafafa" />
    <div style="margin-top: 8px"><el-button size="small" @click="clear">清空</el-button></div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{ modelValue: any; width?: number; height?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const width = props.width ?? 400
const height = props.height ?? 200
const canvasRef = ref<HTMLCanvasElement | null>(null)
function clear() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height)
  emit('update:modelValue', '')
}
</script>
