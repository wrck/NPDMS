<template>
  <div class="widget-placeholder barcode-input" style="display: inline-flex; gap: 8px">
    <el-input v-model="model" :placeholder="displayName" />
    <el-button @click="scan">扫码</el-button>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const displayName = '条码扫描'
watch(model, (v) => emit('update:modelValue', v))
function scan() {
  // 占位：摄像头扫码待后续迭代接入
}
</script>
