<template>
  <el-select v-model="model" :multiple="multiple" placeholder="选择部门" filterable remote :remote-method="search">
    <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
  </el-select>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { get } from '@/utils/request'

const props = defineProps<{ modelValue: any; multiple?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const depts = ref<any[]>([])
const model = ref(props.modelValue)
watch(model, (v) => emit('update:modelValue', v))

async function search(query: string) {
  if (!query) return
  try {
    depts.value = await get<any[]>('/api/system/dept/search', { keyword: query })
  } catch {
    /* 静默 */
  }
}
</script>
