<template>
  <el-button
    v-if="canExport"
    :loading="exporting"
    :disabled="disabled"
    data-testid="project-device-export"
    @click="exportFile"
  >
    <Icon icon="ep:download" /> {{ kind === 'serials' ? '导出查询结果' : '导出' }}
  </el-button>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { exportProjectDevices, type ProjectSerialExportFilters } from '@/api/pms/asset/projectDeviceExport'
import { checkPermi } from '@/utils/permission'
import { officeProjectReadContext } from '@/utils/officeProjectReadonly'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'

const props = defineProps<{
  projectId: number | string
  kind: 'equipment' | 'serials'
  filters?: ProjectSerialExportFilters
  disabled?: boolean
}>()
const exporting = ref(false)
const message = useMessage()
const canExport = computed(() => !officeProjectReadContext() && !!props.projectId &&
  checkPermi(['pms:project-device:export']) && checkPermi(['pms:device:query']))

const exportFile = async () => {
  if (!canExport.value || props.disabled || exporting.value) return
  exporting.value = true
  const projectId = props.projectId
  const kind = props.kind
  try {
    const file = await exportProjectDevices({ projectId, kind, filters: props.filters })
    if (projectId !== props.projectId || officeProjectReadContext()) return
    download.excel(file, `项目${projectId}-${kind === 'serials' ? '序列号详情' : '设备清单'}.xlsx`)
  } catch {
    message.error('导出失败，请重试。')
  } finally {
    exporting.value = false
  }
}
</script>
