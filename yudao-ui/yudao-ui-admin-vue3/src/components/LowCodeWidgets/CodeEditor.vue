<template>
  <div class="widget-placeholder code-editor">
    <el-input v-model="model" type="textarea" :rows="10" :placeholder="displayName" style="font-family: monospace" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const displayName = '代码编辑器'
watch(model, (v) => emit('update:modelValue', v))
</script>
