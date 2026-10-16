<template>
  <ContentWrap>
    <div :class="{ 'panel-header': props.projectId }">
    <span v-if="props.projectId" class="panel-title"><Icon icon="ep:document" /> 配置Log</span>
    <el-form inline :class="{ 'panel-header-actions': props.projectId }">
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
    </div>
    <el-form inline class="log-filters" @submit.prevent="search">
      <el-form-item label="序列号"><el-input v-model="filters.serialNumber" clearable placeholder="请输入序列号" /></el-form-item>
      <el-form-item label="设备名称"><el-input v-model="filters.deviceName" clearable placeholder="请输入设备名称" /></el-form-item>
      <el-form-item label="日志来源"><el-select v-model="filters.sourceType" clearable placeholder="全部来源">
        <el-option label="手工上传" value="MANUAL_UPLOAD" />
        <el-option label="采集回传" value="COLLECTION_CALLBACK" />
      </el-select></el-form-item>
      <el-form-item><el-button native-type="submit" type="primary" :loading="loading"><Icon icon="ep:search" />查询</el-button>
        <el-button :disabled="loading" @click="resetSearch"><Icon icon="ep:refresh" />重置</el-button></el-form-item>
    </el-form>
    <el-alert v-if="uploadError" :title="uploadError" type="error" :closable="false" class="mb-4" />
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
      :data="filteredRows"
      row-key="deviceId"
      :empty-text="appliedFilters.serialNumber || appliedFilters.deviceName || appliedFilters.sourceType ? '暂无符合查询条件的设备日志' : '当前项目暂无已归档的设备日志'"
    >
      <el-table-column prop="serialNumber" label="设备序列号" min-width="180" />
      <el-table-column prop="deviceName" label="设备名称" min-width="160" />
      <el-table-column label="解析与在网版本" min-width="260"
        ><template #default="{ row }">
          <template v-if="row.latest && parseStates[String(row.latest.id)]">
            <div>{{ parseStates[String(row.latest.id)].conpVersion || '尚未识别软件版本' }}</div>
            <div class="text-sm">{{
              parseStates[String(row.latest.id)].message || parseStates[String(row.latest.id)].state
            }}</div>
          </template>
          <span v-else>—</span>
        </template></el-table-column
      >
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
      <el-table-column label="操作" width="250" fixed="right"
        ><template #default="{ row }">
          <el-button
            v-if="checkPermi(['pms:device-log-archive:query'])"
            link type="primary" :disabled="!row.latest" @click="viewParsed(row.latest)"
            >查看解析结果</el-button
          >
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
      <el-table-column label="操作" width="270" fixed="right"
        ><template #default="{ row }">
          <el-button
            v-if="checkPermi(['pms:device-log-archive:query'])"
            link type="primary" :disabled="!row.available" @click="viewParsed(row)"
            >查看解析结果</el-button
          >
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
  <el-dialog v-model="parsedVisible" :title="`${parsedName} · 解析结果`" width="90%">
    <div v-loading="parsedLoading">
      <el-alert v-if="parsedError" :title="parsedError" type="error" :closable="false" />
      <LogParseResult v-if="parsedResult?.result" :result="parsedResult.result" />
      <el-empty v-else-if="!parsedLoading && !parsedError" :description="parsedResult?.message || '暂无解析结果'" />
    </div>
  </el-dialog>
</template>
<script setup lang="ts">
import { generateUUID } from '@/utils'
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions, UploadInstance } from 'element-plus'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import * as ProjectApi from '@/api/pms/project/projects'
import * as Api from '@/api/pms/asset/device-log-archive'
import { formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import { decodeLogText } from '@/components/DeviceCollection/decodeLogText'
import LogParseResult from '@/components/DeviceCollection/LogParseResult.vue'
defineOptions({ name: 'DeviceLogArchive' })
const props = defineProps<{ projectId?: Api.Identifier; originTaskId?: Api.Identifier; readonly?: boolean }>()
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
const filters = reactive({ serialNumber: '', deviceName: '', sourceType: '' })
const appliedFilters = ref({ ...filters })
const filteredRows = computed(() => {
  const query = appliedFilters.value
  return rows.value.filter(row => (!query.serialNumber || row.serialNumber.toLowerCase().includes(query.serialNumber))
    && (!query.deviceName || (row.deviceName || '').toLowerCase().includes(query.deviceName))
    && (!query.sourceType || row.latest?.sourceType === query.sourceType))
})
const search = () => { appliedFilters.value = { serialNumber: filters.serialNumber.trim().toLowerCase(),
  deviceName: filters.deviceName.trim().toLowerCase(), sourceType: filters.sourceType || '' } }
const resetSearch = () => {
  filters.serialNumber = ''; filters.deviceName = ''; filters.sourceType = ''; search()
}
const loading = ref(false)
const loadFailed = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const result = ref<Api.ImportResult>()
const uploadRef = ref<UploadInstance>()
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRows = ref<Api.LogFile[]>([])
const selectedSerial = ref('')
const parsedVisible = ref(false)
const parsedLoading = ref(false)
const parsedName = ref('')
const parsedError = ref('')
const parsedResult = ref<Api.ParseResult>()
let parsedSequence = 0
const viewParsed = async (file: Api.LogFile) => {
  const sequence = ++parsedSequence
  parsedName.value = file.fileName
  parsedResult.value = undefined
  parsedError.value = ''
  parsedVisible.value = true
  parsedLoading.value = true
  try {
    const data = await Api.parseResult(file.id)
    if (sequence === parsedSequence) parsedResult.value = data
  } catch {
    if (sequence === parsedSequence) parsedError.value = '解析结果加载失败，请稍后重试。'
  } finally {
    if (sequence === parsedSequence) parsedLoading.value = false
  }
}
const parseStates = ref<Record<string, Api.ParseState>>({})
let parseTimer: ReturnType<typeof setTimeout> | undefined
let parseAttempts = 0
const refreshParses = async (currentRows: Api.DeviceLog[], sequence: number) => {
  const files = currentRows.flatMap((row) => (row.latest?.available ? [row.latest] : []))
  await Promise.all(
    files.map(async (file) => {
      try {
        const state = await Api.parseState(file.id)
        if (sequence === loadSequence) parseStates.value[String(file.id)] = state
      } catch {
        if (sequence === loadSequence)
          parseStates.value[String(file.id)] = {
            state: 'QUERY_FAILED',
            message: '解析状态加载失败，请刷新重试'
          }
      }
    })
  )
  if (sequence !== loadSequence) return
  const pending = Object.values(parseStates.value).some((v) =>
    ['SUBMITTING', 'QUEUED', 'RUNNING'].includes(v.state)
  )
  if (pending && ++parseAttempts < 60)
    parseTimer = setTimeout(() => refreshParses(currentRows, sequence), 2000)
}
onBeforeUnmount(() => {
  ++loadSequence
  ++parsedSequence
  if (parseTimer) clearTimeout(parseTimer)
})
let loadSequence = 0
let historySequence = 0
const load = async () => {
  const current = projectId.value
  const sequence = ++loadSequence
  if (parseTimer) clearTimeout(parseTimer)
  parseAttempts = 0
  parseStates.value = {}
  rows.value = []
  loadFailed.value = false
  if (!current) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await Api.latest(current)
    if (sequence === loadSequence) {
      rows.value = data
      await refreshParses(data, sequence)
    }
  } catch {
    if (sequence === loadSequence) loadFailed.value = true
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
watch(
  projectId,
  () => {
    resetSearch()
    result.value = undefined
    ++parsedSequence
    parsedVisible.value = false
    parsedResult.value = undefined
    uploadError.value = ''
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
  uploadError.value = ''
  try {
    let file: File
    try {
      const text = decodeLogText(new Uint8Array(await options.file.arrayBuffer()))
      if (!text.trim() || text.includes('\0')) throw new Error('INVALID_LOG_TEXT')
      file = new File([text], options.file.name, { type: 'text/plain;charset=utf-8' })
      if (file.size > 20 * 1024 * 1024) {
        uploadError.value = '转换后的 UTF-8 日志超过 20 MiB，请拆分后上传。'
        return
      }
    } catch {
      uploadError.value = '无法识别日志编码或内容不是有效文本，请核对文件后重新上传。'
      ElMessage.error(uploadError.value)
      return
    }
    result.value = await Api.upload({ projectId: projectId.value, operationId: generateUUID(), file, originTaskId: props.originTaskId })
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

<style scoped>
.panel-header, .panel-header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.panel-header { justify-content: space-between; padding-bottom: 8px; margin-bottom: 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.panel-title { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.panel-header-actions :deep(.el-form-item) { margin: 0; }
.log-filters :deep(.el-input), .log-filters :deep(.el-select) { width: 180px; }
@media (max-width: 767px) { .panel-header { align-items: flex-start; } }
</style>
