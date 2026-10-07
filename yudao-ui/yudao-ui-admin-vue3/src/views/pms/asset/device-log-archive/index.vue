<template>
  <ContentWrap>
    <el-form inline>
      <el-form-item v-if="!props.projectId" label="项目">
        <PmsEntitySelect
          v-model="projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-300px"
          :disabled="uploading"
        />
      </el-form-item>
      <el-form-item
        ><el-button :disabled="!projectId || uploading" :loading="loading" @click="load"
          >刷新</el-button
        ></el-form-item
      >
      <el-form-item
        v-if="!props.readonly && checkPermi(['pms:file:upload'])"
        v-hasPermi="['pms:device-log-archive:import']"
      >
        <el-upload
          ref="uploadRef"
          :show-file-list="false"
          accept=".txt,.log,.cfg,.conf"
          :http-request="uploadFile"
          :disabled="!projectId || uploading"
          :before-upload="checkFile"
        >
          <el-button type="primary" :disabled="!projectId" :loading="uploading"
            >上传设备日志</el-button
          >
        </el-upload>
      </el-form-item>
    </el-form>
    <p class="text-sm text-[var(--el-text-color-secondary)] mt-0">
      支持包含多台设备的 UTF-8 日志文本，最大 20
      MiB。按内容中的设备序列号拆分并关联当前项目设备，历史文件全部保留。
    </p>
    <el-alert
      v-if="result"
      class="mb-4"
      :type="result.outcome === 'ARCHIVED' ? 'success' : 'error'"
      :closable="false"
      :title="
        result.outcome === 'ARCHIVED'
          ? `已归档 ${result.deviceCount} 台设备的日志，脱敏 ${result.redactedValueCount} 处敏感内容`
          : result.rejectionReason
      "
    />
    <el-alert
      v-if="loadFailed"
      type="error"
      title="日志加载失败，请重试刷新"
      :closable="false"
      class="mb-4"
    />
    <el-empty v-if="!projectId" description="选择项目后查看各设备的最新日志" />
    <el-table
      v-else
      v-loading="loading"
      :data="rows"
      row-key="deviceId"
      empty-text="当前项目没有已关联的设备"
    >
      <el-table-column prop="serialNumber" label="设备序列号" min-width="180" />
      <el-table-column prop="deviceName" label="设备名称" min-width="160" />
      <el-table-column label="最新版本" width="110"
        ><template #default="{ row }">{{
          row.latest ? `v${row.latest.deviceVersion}` : '尚无日志'
        }}</template></el-table-column
      >
      <el-table-column label="来源" width="130"
        ><template #default="{ row }">{{
          row.latest ? sourceName(row.latest.sourceType) : '—'
        }}</template></el-table-column
      >
      <el-table-column label="归档时间" min-width="180"
        ><template #default="{ row }">{{
          formatTime(row.latest?.archivedAt)
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="160"
        ><template #default="{ row }">
          <el-button link type="primary" :disabled="!row.latest" @click="openHistory(row)"
            >文件历史</el-button
          >
          <el-button
            v-if="checkPermi(['pms:file:download'])"
            v-hasPermi="['pms:device-log-archive:download']"
            link
            type="primary"
            :disabled="!row.latest"
            @click="download(row.latest.id)"
            >下载</el-button
          >
        </template></el-table-column
      >
    </el-table>
  </ContentWrap>
  <el-dialog v-model="historyVisible" :title="`${selectedSerial} · 文件历史`" width="900px">
    <el-table v-loading="historyLoading" :data="historyRows" row-key="id">
      <el-table-column label="版本" width="85"
        ><template #default="{ row }">v{{ row.deviceVersion }}</template></el-table-column
      >
      <el-table-column prop="fileName" label="设备日志" min-width="190" />
      <el-table-column label="来源" width="115"
        ><template #default="{ row }">{{ sourceName(row.sourceType) }}</template></el-table-column
      >
      <el-table-column label="归档时间" min-width="175"
        ><template #default="{ row }">{{ formatTime(row.archivedAt) }}</template></el-table-column
      >
      <el-table-column label="操作" width="180"
        ><template #default="{ row }">
          <el-button
            v-if="checkPermi(['pms:file:download'])"
            v-hasPermi="['pms:device-log-archive:download']"
            link
            type="primary"
            :disabled="!row.available"
            @click="download(row.id)"
            >下载</el-button
          >
          <el-button
            v-if="checkPermi(['pms:file:download'])"
            v-hasPermi="['pms:device-log-archive:download']"
            link
            type="primary"
            @click="downloadSource(row.importId)"
            >来源文件</el-button
          >
        </template></el-table-column
      >
    </el-table>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions, UploadInstance } from 'element-plus'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import * as ProjectApi from '@/api/pms/project/projects'
import * as Api from '@/api/pms/asset/device-log-archive'
import { formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
defineOptions({ name: 'DeviceLogArchive' })
const props = defineProps<{ projectId?: Api.Identifier; readonly?: boolean }>()
const route = useRoute()
const initial = route.query.projectId
const projectId = ref<Api.Identifier | undefined>(
  props.projectId ??
    (typeof initial === 'string' && /^[1-9]\d{0,18}$/.test(initial) ? initial : undefined)
)
watch(
  () => props.projectId,
  (value) => {
    if (value) projectId.value = value
  }
)
const rows = ref<Api.DeviceLog[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const uploading = ref(false)
const result = ref<Api.ImportResult>()
const uploadRef = ref<UploadInstance>()
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRows = ref<Api.LogFile[]>([])
const selectedSerial = ref('')
let loadSequence = 0
let historySequence = 0
const load = async () => {
  const current = projectId.value
  const sequence = ++loadSequence
  rows.value = []
  loadFailed.value = false
  if (!current) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await Api.latest(current)
    if (sequence === loadSequence) rows.value = data
  } catch {
    if (sequence === loadSequence) loadFailed.value = true
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
watch(
  projectId,
  () => {
    result.value = undefined
    historyVisible.value = false
    historySequence++
    void load()
  },
  { immediate: true }
)
const checkFile = (file: File) => {
  if (!file.size || file.size > 20 * 1024 * 1024) {
    ElMessage.error('日志文件须小于等于 20 MiB')
    return false
  }
  return true
}
const uploadFile = async (options: UploadRequestOptions) => {
  if (!projectId.value || uploading.value || props.readonly) return
  uploading.value = true
  result.value = undefined
  try {
    result.value = await Api.upload(projectId.value, crypto.randomUUID(), options.file)
    if (result.value.outcome === 'ARCHIVED') {
      ElMessage.success('设备日志已归档')
      await load()
    } else ElMessage.error(result.value.rejectionReason || '日志未归档')
  } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}
const openHistory = async (row: Api.DeviceLog) => {
  const current = projectId.value
  if (!current) return
  const sequence = ++historySequence
  selectedSerial.value = row.serialNumber
  historyRows.value = []
  historyVisible.value = true
  historyLoading.value = true
  try {
    const data = await Api.history(current, row.deviceId)
    if (sequence === historySequence) historyRows.value = data
  } finally {
    if (sequence === historySequence) historyLoading.value = false
  }
}
const download = async (id: Api.Identifier) => {
  window.open(await Api.download(id), '_blank', 'noopener')
}
const downloadSource = async (id: Api.Identifier) => {
  const source = (await Api.getImport(id)).files.find((file) => !file.deviceId)
  if (source?.available) await download(source.id)
  else ElMessage.error('来源文件不可用')
}
const sourceName = (type: string) => (type === 'MANUAL_UPLOAD' ? '手工上传' : '采集回传')
const formatTime = (value?: number) => (value ? formatDate(value, 'YYYY-MM-DD HH:mm:ss') : '—')
</script>
