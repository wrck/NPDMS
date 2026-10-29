<template>
  <SystemUserSelect
    ref="selector"
    presentation="transfer"
    @confirm="(id, rows) => emit('confirm', id, rows)"
  />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import SystemUserSelect from '@/components/SystemUserSelect/index.vue'
defineOptions({ name: 'UserSelectForm' })
const selector = ref<{ open: (id: number, selectedList?: any[]) => Promise<void> | undefined }>()
const emit = defineEmits<{ confirm: [id: any, userList: any[]] }>()
defineExpose({ open: (id: number, selectedList?: any[]) => selector.value?.open(id, selectedList) })
</script>
