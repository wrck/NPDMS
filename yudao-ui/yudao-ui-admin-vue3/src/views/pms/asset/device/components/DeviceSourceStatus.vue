<template>
  <div class="source-status">
    <el-tag :type="tagType" effect="plain">{{ slice.syncStatus }}</el-tag>
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="来源">{{ slice.sourceSystem || '--' }}</el-descriptions-item>
      <el-descriptions-item label="来源版本">{{
        slice.sourceVersion || '--'
      }}</el-descriptions-item>
      <el-descriptions-item label="来源更新时间">{{
        formatDate(slice.sourceUpdatedAt)
      }}</el-descriptions-item>
      <el-descriptions-item label="最近成功同步">{{
        formatDate(slice.syncedAt)
      }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="slice.syncStatus === 'NOT_AVAILABLE'"
      title="当前来源能力尚未接入，仅展示稳定接口降级状态。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="slice.syncStatus === 'FAILED' || slice.syncStatus === 'STALE'"
      title="来源异常，页面保留最近一次成功事实。"
      type="warning"
      :closable="false"
      show-icon
    />
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { formatDate } from '@/utils/formatTime'
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
const props = defineProps<{ slice: DeviceSourceSliceVO }>()
const tagType = computed(() => {
  if (props.slice.syncStatus === 'FRESH') return 'success'
  if (props.slice.syncStatus === 'FAILED') return 'danger'
  if (props.slice.syncStatus === 'STALE' || props.slice.syncStatus === 'PENDING_MAPPING')
    return 'warning'
  return 'info'
})
</script>
<style scoped>
.source-status {
  display: grid;
  gap: 12px;
}

.source-status > .el-tag {
  justify-self: start;
}
</style>
