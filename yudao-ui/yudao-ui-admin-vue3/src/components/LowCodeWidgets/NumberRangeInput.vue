<template>
  <div class="widget-placeholder number-range-input" style="display: inline-flex; gap: 8px; align-items: center">
    <el-input-number v-model="minVal" :placeholder="'最小值'" />
    <span>-</span>
    <el-input-number v-model="maxVal" :placeholder="'最大值'" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any; min?: number; max?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const minVal = ref<number | undefined>(undefined)
const maxVal = ref<number | undefined>(undefined)
watch([minVal, maxVal], () => {
  emit('update:modelValue', { min: minVal.value, max: maxVal.value })
})
</script>
