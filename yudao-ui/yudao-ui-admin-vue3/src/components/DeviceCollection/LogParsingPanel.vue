<template>
  <section aria-label="设备日志解析">
    <el-alert
      :title="
        executionId
          ? '解析已保存的采集日志，不重新连接设备。原日志和业务状态保持不变。'
          : '上传设备日志即可解析，无需连接设备或选择命令模板。日志中的密码、密钥等内容会脱敏。'
      "
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <el-form v-if="canParse" label-width="110px" :disabled="busy" @submit.prevent>
      <el-form-item v-if="projectId && !executionId">
        <el-button
          v-hasPermi="['pms:device-log-archive:import']"
          @click="$router.push({ path: '/pms/asset/device-log-archive', query: { projectId } })"
          >多设备日志上传与归档</el-button
        >
      </el-form-item>
      <template v-if="!executionId">
        <el-form-item label="日志文件" required>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".txt,.log,.json"
            :on-change="chooseFile"
          >
            <el-button>选择设备日志</el-button>
          </el-upload>
          <span class="ml-12px break-all">{{
            inputName || '支持 .txt / .log / .json，最大 8 MiB'
          }}</span>
        </el-form-item>
        <el-form-item label="日志命令">
          <el-input
            v-model="commandHint"
            maxlength="256"
            placeholder="可选，如 show run；日志包含命令提示符时可留空"
          />
        </el-form-item>
      </template>
      <el-form-item>
        <el-button
          type="primary"
          :loading="busy"
          :disabled="!executionId && !inputText"
          @click="submit"
        >
          {{ pendingRetry ? '重试原解析请求' : '开始结构化解析' }}
        </el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="mb-12px" />
    <div class="flex justify-between items-center mb-8px">
      <strong>解析历史</strong
      ><el-button :loading="loading" @click="loadHistory">刷新历史</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无日志解析记录">
      <el-table-column prop="inputName" label="来源日志" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" min-width="130">
        <template #default="{ row }"
          ><el-tag :type="statusType(row.state)">{{
            states[row.state] || row.state
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="releaseVersion" label="解析版本" width="110" />
      <el-table-column
        prop="createTime"
        label="提交时间"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" width="120">
        <template #default="{ row }"
          ><el-button link type="primary" @click="select(row)">查看结果</el-button></template
        >
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="pageNo"
      :total="total"
      :page-size="10"
      layout="total, prev, pager, next"
      class="my-12px"
      @current-change="loadHistory"
    />
    <section v-if="selected" class="mt-20px border-t border-[var(--el-border-color)] pt-12px">
      <el-alert
        v-if="selected.deviceProjection"
        :title="selected.deviceProjection.message"
        :type="selected.deviceProjection.status === 'LINKED' ? 'success' : 'warning'"
        :closable="false"
        class="mb-12px"
      />
      <div class="flex justify-between items-center gap-12px mb-12px">
        <strong>{{ selected.inputName }} · {{ states[selected.state] || selected.state }}</strong>
        <el-button :loading="refreshing" @click="refreshSelected(true)">刷新解析状态</el-button>
      </div>
      <el-alert
        v-if="selected.message"
        :title="selected.message"
        type="warning"
        :closable="false"
        class="mb-8px"
      />
      <el-alert
        v-if="selected.waitReason"
        :title="`解析等待处理：${selected.waitReason}，可稍后刷新。`"
        type="warning"
        :closable="false"
        class="mb-8px"
      />
      <el-alert
        v-for="warning in selected.warnings"
        :key="warning"
        :title="warnings[warning] || warning"
        type="info"
        :closable="false"
        class="mb-8px"
      />
      <p v-if="pollingPaused" class="text-[var(--el-text-color-secondary)]"
        >已暂停自动刷新，解析任务继续在后台处理；稍后可手动刷新或重新打开此记录。</p
      >
      <LogParseResult
        v-if="selected.result"
        :key="selected.result.resultId"
        :result="selected.result"
      />
      <el-empty
        v-else-if="selected.state === 'FAILED'"
        description="解析失败，原日志保留；可检查输入后新建解析任务。"
        :image-size="60"
      />
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { UploadFile } from 'element-plus'
import type { Entry, Id } from '@/api/pms/platform/deviceCollection'
import * as Api from '@/api/pms/platform/deviceLogParsing'
import { checkPermi } from '@/utils/permission'
import { generateUUID } from '@/utils'
import { dateFormatter } from '@/utils/formatTime'
import LogParseResult from './LogParseResult.vue'
import { decodeLogText } from './decodeLogText'

const props = defineProps<{ projectId: Id; entry?: Entry; objectId?: Id; executionId?: Id }>()
const canParse = computed(() =>
  checkPermi([
    props.entry === 'configuration'
      ? 'pms:imp-configuration:update'
      : props.entry === 'joint-test'
        ? 'pms:imp-joint-test:update'
        : 'pms:device-collection:execute'
  ])
)
const states: Record<string, string> = {
  SUBMITTING: '受理待确认',
  QUEUED: '排队中',
  RUNNING: '解析中',
  WAITING: '等待处理',
  SUCCEEDED: '解析完成',
  FAILED: '解析失败',
  CANCELLED: '已取消'
}
const warnings: Record<string, string> = {
  UPLOADED_LOG_EXECUTION_NOT_VERIFIED: '上传日志仅用于内容解析，不证明原设备命令执行成功。',
  COMMAND_NOT_IDENTIFIED:
    '部分日志未识别出命令，保留文本及可识别的通用结构；可补充日志命令后重新解析。',
  SENSITIVE_VALUES_REDACTED: '已在保存解析输入前隐藏密码、密钥或令牌。'
}
const statusType = (state: string) =>
  state === 'SUCCEEDED'
    ? 'success'
    : state === 'FAILED'
      ? 'danger'
      : state === 'WAITING'
        ? 'warning'
        : 'info'
const busy = ref(false),
  loading = ref(false),
  refreshing = ref(false)
const rows = ref<Api.LogParse[]>([]),
  selected = ref<Api.LogParse>()
const pageNo = ref(1),
  total = ref(0),
  error = ref('')
const inputName = ref(''),
  inputText = ref(''),
  commandHint = ref('')
const pendingRetry = ref(false),
  pollingPaused = ref(false)
let selectedFile: File | undefined
let requestKey = generateUUID(),
  generation = 0,
  fileGeneration = 0,
  attempts = 0
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
const stop = () => {
  if (timer) clearTimeout(timer)
  timer = undefined
}
const resetRequest = () => {
  requestKey = generateUUID()
  pendingRetry.value = false
}
watch(commandHint, resetRequest)

const readFile = async () => {
  const current = ++fileGeneration
  inputText.value = ''
  error.value = ''
  resetRequest()
  if (!selectedFile) return
  try {
    if (
      !/\.(txt|log|json)$/i.test(selectedFile.name) ||
      selectedFile.size === 0 ||
      selectedFile.size > 8 * 1024 * 1024
    )
      throw new Error()
    const decoded = decodeLogText(new Uint8Array(await selectedFile.arrayBuffer()))
    if (current !== fileGeneration || disposed) return
    if (
      !decoded.trim() ||
      decoded.includes('\0') ||
      new TextEncoder().encode(decoded).length > 8 * 1024 * 1024
    )
      throw new Error()
    inputText.value = decoded
  } catch {
    if (current === fileGeneration)
      error.value = '无法自动读取日志：请选择有效的非空文本文件，转换后的 UTF-8 内容不得超过 8 MiB。'
  }
}
const chooseFile = (file: UploadFile) => {
  selectedFile = file.raw
  inputName.value = file.name
  void readFile()
}
const loadHistory = async () => {
  loading.value = true
  try {
    const result = await Api.pageLogParses({
      projectId: props.projectId,
      entry: props.entry,
      objectId: props.objectId,
      executionId: props.executionId,
      pageNo: pageNo.value
    })
    if (!disposed) {
      rows.value = result.list
      total.value = result.total
    }
  } catch {
    if (!disposed) error.value = '解析历史读取失败，请刷新重试。'
  } finally {
    if (!disposed) loading.value = false
  }
}
const refreshSelected = async (manual = false) => {
  if (!selected.value || disposed) return
  stop()
  if (manual) {
    attempts = 0
    pollingPaused.value = false
  }
  const current = generation,
    id = selected.value.id
  refreshing.value = true
  try {
    const result = await Api.getLogParse(id)
    if (disposed || current !== generation) return
    selected.value = result
    rows.value = rows.value.map((row) => (String(row.id) === String(id) ? result : row))
    if (['SUBMITTING', 'QUEUED', 'RUNNING'].includes(result.state)) {
      if (++attempts < 60) timer = setTimeout(() => void refreshSelected(), 2000)
      else pollingPaused.value = true
    }
  } catch {
    if (current === generation && !disposed) {
      error.value = '解析状态暂不可用，请手动刷新；不会重复提交任务。'
      pollingPaused.value = true
    }
  } finally {
    if (current === generation && !disposed) refreshing.value = false
  }
}
const select = async (row: Api.LogParse) => {
  stop()
  generation++
  attempts = 0
  pollingPaused.value = false
  selected.value = row
  await refreshSelected()
}
const submit = async () => {
  busy.value = true
  error.value = ''
  try {
    const result = await Api.submitLogParse({
      requestKey,
      projectId: props.projectId,
      ...(props.executionId
        ? { entry: props.entry, objectId: props.objectId, executionId: props.executionId }
        : {
            inputName: inputName.value,
            inputText: inputText.value,
            commandHint: commandHint.value || undefined
          })
    })
    if (disposed) return
    pendingRetry.value = result.state === 'SUBMITTING'
    if (!pendingRetry.value) resetRequest()
    pageNo.value = 1
    await loadHistory()
    await select(result)
  } catch {
    if (!disposed) {
      pendingRetry.value = true
      error.value = '解析提交未确认，请先查看历史；重试会沿用原请求标识。'
    }
  } finally {
    if (!disposed) busy.value = false
  }
}
onMounted(loadHistory)
onBeforeUnmount(() => {
  disposed = true
  generation++
  fileGeneration++
  stop()
  inputText.value = ''
  selectedFile = undefined
})
</script>
