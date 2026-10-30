<template>
  <UserSelectDialogV2
    ref="dialog"
    title="选择店员"
    multiple
    default-status="all"
    show-create-time
    allow-empty
    retain-draft-on-reopen
    @selected="confirm"
  />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import UserSelectDialogV2 from '@/views/system/user/components/UserSelectDialogV2.vue'
import type { UserVO } from '@/api/system/user'
import type { SystemUserSelectionUser } from '@/api/system/user/selection'

const dialog = ref<InstanceType<typeof UserSelectDialogV2>>()
const emit = defineEmits<{ change: [users: UserVO[]] }>()
const confirm = (rows: SystemUserSelectionUser[]) => emit('change', rows as UserVO[])
defineExpose({ open: () => dialog.value?.open() })
</script>
