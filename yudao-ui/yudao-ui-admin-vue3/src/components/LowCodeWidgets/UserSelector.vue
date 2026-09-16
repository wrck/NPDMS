<template>
  <el-select v-model="model" :multiple="multiple" placeholder="选择用户" filterable remote :remote-method="search">
    <el-option v-for="u in users" :key="u.id" :label="u.name" :value="u.id" />
  </el-select>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { get } from '@/utils/request'

const props = defineProps<{ modelValue: any; multiple?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const users = ref<any[]>([])

const model = ref(props.modelValue)
function sync(v: any) {
  model.value = v
  emit('update:modelValue', v)
}
defineExpose({ sync })

watch(model, (v) => emit('update:modelValue', v))

async function search(query: string) {
  if (!query) return
  try {
    users.value = await get<any[]>('/api/system/user/search', { keyword: query })
  } catch {
    /* 静默失败 */
  }
}
</script>
