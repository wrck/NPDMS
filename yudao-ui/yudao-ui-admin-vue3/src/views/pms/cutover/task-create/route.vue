<template><CutoverTaskCreate @close="close" @created="created" /></template>
<script setup lang="ts">
import CutoverTaskCreate from './index.vue'
import type { WireLong } from '@/api/pms/cutover'
defineOptions({ name: 'PmsCutoverTaskCreate' })
const router = useRouter()
const close = () => { void router.push('/pms/cutover/tasks') }
const created = (taskId: WireLong) => { void router.replace(`/pms/cutover/tasks/${taskId}`) }
</script>
