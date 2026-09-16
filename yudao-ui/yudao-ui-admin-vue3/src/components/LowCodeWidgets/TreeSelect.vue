<template>
  <div class="widget-placeholder tree-select">
    <el-tree-select v-model="model" :data="data" placeholder="请选择" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any; data?: any[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const data = props.data ?? []
watch(model, (v) => emit('update:modelValue', v))
</script>
