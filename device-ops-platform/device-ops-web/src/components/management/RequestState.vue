<script setup lang="ts">
defineProps<{ loading: boolean; error: string; empty?: boolean; emptyText?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div
    v-if="loading"
    role="status"
    aria-live="polite"
  >
    <el-skeleton
      :rows="3"
      animated
    /><p>正在加载…</p>
  </div>
  <div v-else-if="error">
    <el-alert
      :title="error"
      type="error"
      :closable="false"
      show-icon
    /><el-button
      style="margin-top: 12px"
      @click="$emit('retry')"
    >
      重试
    </el-button>
  </div>
  <el-empty
    v-else-if="empty"
    :description="emptyText || '暂无记录'"
  />
  <slot v-else />
</template>
