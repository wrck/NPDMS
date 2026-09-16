<template>
  <div class="widget-placeholder date-range-picker">
    <el-date-picker v-model="model" type="daterange" :format="format" range-separator="至" start-placeholder="开始" end-placeholder="结束" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any; format?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const format = props.format ?? 'YYYY-MM-DD'
watch(model, (v) => emit('update:modelValue', v))
</script>
