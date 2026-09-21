<script setup lang="ts">
import { statusLabel, statusTone } from '@/management/presentation'
defineProps<{ status?: string | null }>()
</script>
<template>
  <el-tag :type="statusTone(status)" effect="light" :title="status || 'UNKNOWN'">
    {{ statusLabel(status) }}
  </el-tag>
</template>
