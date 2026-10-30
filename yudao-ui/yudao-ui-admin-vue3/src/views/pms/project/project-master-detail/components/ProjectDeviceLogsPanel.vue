<template>
  <DeviceLogArchive
    v-if="checkPermi(['pms:device-log-archive:query'])"
    :project-id="projectId"
    :readonly="readonly"
  />
  <el-empty v-else description="暂无设备日志归档查看权限" />
  <el-alert
    v-if="legacyLoadFailed"
    title="历史配置日志加载失败"
    type="error"
    :closable="false"
    class="mt-4"
  >
    <el-button link type="primary" @click="loadLegacyHistory">重试</el-button>
  </el-alert>
  <el-collapse v-if="hasLegacyHistory" v-model="expandedHistory" class="mt-4">
    <el-collapse-item title="历史配置日志" name="legacy">
      <ConfigLogWorkbench v-if="expandedHistory.includes('legacy')" :project-id="projectId" />
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { checkPermi } from '@/utils/permission'
import DeviceLogArchive from '@/views/pms/asset/device-log-archive/index.vue'
import ConfigLogWorkbench from '@/views/pms/asset/device/config-log/index.vue'
import { getDeviceConfigLogPage } from '@/api/pms/asset/device/archive'

const props = defineProps<{ projectId: number; readonly?: boolean; active?: boolean }>()
const hasLegacyHistory = ref(false)
const legacyLoadFailed = ref(false)
const expandedHistory = ref<string[]>([])
let sequence = 0
const loadLegacyHistory = async () => {
  const current = ++sequence
  legacyLoadFailed.value = false
  if (!props.projectId || !checkPermi(['pms:device:query'])) return
  try {
    const page = await getDeviceConfigLogPage({
      projectId: props.projectId,
      pageNo: 1,
      pageSize: 1
    })
    if (current === sequence) hasLegacyHistory.value = Number(page.total) > 0
  } catch {
    if (current === sequence) legacyLoadFailed.value = true
  }
}
watch(
  () => props.projectId,
  () => {
    ++sequence
    hasLegacyHistory.value = false
    expandedHistory.value = []
    if (props.active !== false) void loadLegacyHistory()
  },
  { immediate: true }
)
watch(
  () => props.active,
  (value) => {
    if (value) void loadLegacyHistory()
  }
)
onBeforeUnmount(() => {
  ++sequence
})
</script>
