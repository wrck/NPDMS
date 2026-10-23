<template>
  <el-drawer
    v-model="visible"
    :title="`${serialNumber} · 配置Log`"
    size="min(1200px, 100%)"
    append-to-body
    destroy-on-close
  >
    <div data-testid="device-config-log-drawer">
      <el-button :loading="loading" class="mb-4" @click="load">刷新</el-button>
      <el-alert
        v-for="error in errors"
        :key="error"
        :title="error"
        type="error"
        :closable="false"
        class="mb-4"
      />
      <el-table
        v-loading="loading"
        :data="rows"
        row-key="key"
        :empty-text="errors.length ? '日志未完整加载，请刷新重试' : '该设备暂无配置日志'"
      >
        <el-table-column label="版本" width="80">
          <template #default="{ row }">{{
            row.file ? `v${row.file.deviceVersion}` : '—'
          }}</template>
        </el-table-column>
        <el-table-column prop="name" label="设备日志" min-width="220" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column label="记录时间" min-width="175">
          <template #default="{ row }">{{ formatDate(row.time) || '—' }}</template>
        </el-table-column>
        <el-table-column label="解析与软件版本" min-width="260">
          <template #default="{ row }">
            <template v-if="row.file && parseStates[String(row.file.id)]">
              <div>{{ parseStates[String(row.file.id)].conpVersion || '尚未识别软件版本' }}</div>
              <div class="text-sm">{{
                parseStates[String(row.file.id)].message || parseStates[String(row.file.id)].state
              }}</div>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canDownload(row)" link type="primary" @click="download(row)"
              >下载</el-button
            >
            <el-button
              v-if="row.file && canDownloadArchive"
              link
              type="primary"
              @click="downloadSource(row.file.importId)"
              >来源文件</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-drawer>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as DeviceApi from '@/api/pms/asset/device'
import * as ArchiveApi from '@/api/pms/asset/device-log-archive'
import downloadFile from '@/utils/download'
import { formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'

const props = defineProps<{ projectId: number }>()
const visible = ref(false)
const deviceId = ref<number>()
const serialNumber = ref('')
const loading = ref(false)
const errors = ref<string[]>([])
interface LogRow {
  key: string
  name: string
  source: string
  time?: string | number
  file?: ArchiveApi.LogFile
  legacy?: DeviceApi.DeviceConfigurationLogVO
}
const rows = ref<LogRow[]>([])
const parseStates = ref<Record<string, ArchiveApi.ParseState>>({})
const canDownloadArchive = computed(
  () => checkPermi(['pms:file:download']) && checkPermi(['pms:device-log-archive:download'])
)
const canDownload = (row: LogRow) =>
  row.file
    ? row.file.available && canDownloadArchive.value
    : row.legacy?.downloadable && checkPermi(['pms:device-configuration-log:download'])
let sequence = 0
const load = async () => {
  const currentDeviceId = deviceId.value
  if (!visible.value || !currentDeviceId) return
  const current = ++sequence
  loading.value = true
  rows.value = []
  errors.value = []
  parseStates.value = {}
  const canQueryArchive = checkPermi(['pms:device-log-archive:query'])
  const canQueryLegacy = checkPermi(['pms:device:query'])
  const [archive, legacy] = await Promise.allSettled([
    canQueryArchive ? ArchiveApi.history(props.projectId, currentDeviceId) : Promise.resolve([]),
    canQueryLegacy ? DeviceApi.getConfigurationLogs(currentDeviceId) : Promise.resolve([])
  ])
  if (current !== sequence) return
  if (!canQueryArchive && !canQueryLegacy) errors.value.push('暂无配置日志查看权限')
  if (archive.status === 'fulfilled') {
    rows.value.push(
      ...archive.value.map((file: ArchiveApi.LogFile) => ({
        key: `archive:${file.id}`,
        name: file.fileName,
        source: file.sourceType === 'MANUAL_UPLOAD' ? '手工上传' : '采集回传',
        time: file.archivedAt,
        file
      }))
    )
  } else errors.value.push('设备归档日志加载失败，请刷新重试')
  if (legacy.status === 'fulfilled') {
    rows.value.push(
      ...legacy.value.map((log: DeviceApi.DeviceConfigurationLogVO) => ({
        key: `legacy:${log.id}`,
        name: log.configType || '配置日志',
        source: log.sourceSystem || '—',
        time: log.collectedAt,
        legacy: log
      }))
    )
  } else errors.value.push('历史配置日志加载失败，请刷新重试')
  rows.value.sort((a, b) => new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime())
  loading.value = false
  await Promise.all(
    rows.value
      .flatMap((row) => (row.file?.available ? [row.file] : []))
      .map(async (file) => {
        try {
          const state = await ArchiveApi.parseState(file.id)
          if (current === sequence) parseStates.value[String(file.id)] = state
        } catch {
          if (current === sequence)
            parseStates.value[String(file.id)] = {
              state: 'QUERY_FAILED',
              message: '解析状态加载失败，请刷新重试'
            }
        }
      })
  )
}
const download = async (row: LogRow) => {
  if (!canDownload(row)) return
  if (row.file) window.open(await ArchiveApi.download(row.file.id), '_blank', 'noopener')
  else if (row.legacy && deviceId.value) {
    const grant = await DeviceApi.createConfigurationLogDownloadUrl(deviceId.value, row.legacy.id)
    const data = await DeviceApi.downloadConfigurationLog(grant.downloadPath)
    downloadFile.markdown(data, `configuration-log-${row.legacy.id}.txt`)
  }
}
const downloadSource = async (importId: ArchiveApi.Identifier) => {
  if (!canDownloadArchive.value) return
  const source = (await ArchiveApi.getImport(importId)).files.find((file) => !file.deviceId)
  if (source?.available) window.open(await ArchiveApi.download(source.id), '_blank', 'noopener')
  else ElMessage.error('来源文件不可用')
}
const open = (id: number, sn: string) => {
  deviceId.value = id
  serialNumber.value = sn
  visible.value = true
  void load()
}
watch(visible, (value) => {
  if (!value) {
    ++sequence
    loading.value = false
    rows.value = []
    errors.value = []
    parseStates.value = {}
  }
})
watch(
  () => props.projectId,
  () => {
    visible.value = false
  }
)
onBeforeUnmount(() => {
  ++sequence
})
defineExpose({ open })
</script>
