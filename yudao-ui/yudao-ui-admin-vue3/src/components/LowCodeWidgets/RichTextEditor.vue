<template>
  <div class="widget-placeholder rich-text-editor">
    <el-input v-model="model" type="textarea" :rows="8" :placeholder="displayName" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const displayName = '富文本编辑器'
watch(model, (v) => emit('update:modelValue', v))
</script>
