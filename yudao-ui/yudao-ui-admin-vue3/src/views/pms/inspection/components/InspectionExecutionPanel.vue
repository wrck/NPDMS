<template>
  <section class="execution-panel" aria-labelledby="inspection-execution-title">
    <div class="section-heading">
      <h2 id="inspection-execution-title">{{ isOffline ? '离线巡检执行' : '巡检执行结果' }}</h2>
      <el-tag v-if="currentAttempt"
        >第 {{ currentAttempt.attemptNo || '—' }} 次尝试 ·
        {{ attemptStatus(currentAttempt.statusCode) }}</el-tag
      >
    </div>
    <el-alert
      v-if="!currentAttempt"
      title="尚无执行尝试，请先完成巡检准备。"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-if="isOffline">
      <el-alert
        title="人工执行巡检命令后上传设备日志。缺少输出或解析失败时可补传，原记录保留。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="upload-form" @submit.prevent="submitUpload">
        <el-form-item label="巡检设备">
          <el-select
            :model-value="String(selectedDeviceId || '')"
            :disabled="disabled"
            placeholder="选择要查看或上传日志的设备"
            @change="changeDevice"
          >
            <el-option
              v-for="device in task.devices"
              :key="String(device.deviceId)"
              :value="String(device.deviceId)"
              :label="deviceLabel(device.deviceId)"
            />
          </el-select>
        </el-form-item>
        <div class="upload-fields">
          <el-form-item label="巡检日志">
            <div
              class="file-drop-zone"
              :class="{ 'is-disabled': disabled || !selectedDeviceId }"
              @dragover.prevent
              @drop.prevent="dropFiles"
            >
              <button
                type="button"
                class="file-picker"
                :disabled="disabled || !selectedDeviceId"
                @click="fileInput?.click()"
              >
                <Icon icon="ep:document" :size="30" />
                <span>点击或拖拽选择巡检日志文件</span>
                <small>支持 .log / .txt / .zip，单文件不超过 50MB</small>
              </button>
              <input
                ref="fileInput"
                class="file-input"
                type="file"
                accept=".log,.txt,.zip"
                aria-label="选择离线巡检日志文件"
                tabindex="-1"
                :disabled="disabled || !selectedDeviceId"
                @change="selectFile"
              />
            </div>
            <div v-if="selectedFile" class="file-summary" role="status">
              <span>{{ selectedFile.name }} · {{ fileSize(selectedFile.size) }}</span>
              <el-button link :disabled="disabled" @click="clearDraft">移除文件</el-button>
            </div>
            <p v-if="fileError" class="file-error" role="alert">{{ fileError }}</p>
          </el-form-item>
          <el-form-item label="命令提示（可选）">
            <el-select
              v-model="commandHint"
              clearable
              filterable
              :disabled="disabled || !selectedDeviceId"
              placeholder="仅从当前设备冻结命令中选择"
            >
              <el-option
                v-for="command in hintCommands"
                :key="command.content"
                :value="command.content"
                :label="`${command.inspectionItem} · ${command.content}`"
              />
            </el-select>
            <p class="help-text">只包含单条命令输出的日志可选择对应提示；多命令日志可留空。</p>
          </el-form-item>
        </div>
        <el-button
          type="primary"
          native-type="submit"
          :disabled="disabled || !selectedFile || !selectedDeviceId"
          >上传并解析</el-button
        >
        <div class="section-heading">
          <h3 class="inspection-section-title">巡检命令清单</h3>
          <div class="section-actions">
            <el-button :disabled="!commands.length" @click="copyCommands">复制命令</el-button>
            <el-button :disabled="!commands.length" @click="downloadCommands"
              >下载 TXT 清单</el-button
            >
          </div>
        </div>
        <el-collapse class="command-list"
          ><el-collapse-item title="查看当前设备巡检命令" name="commands">
            <el-table
              :data="orderedCommands"
              :row-key="commandKey"
              empty-text="当前设备暂无冻结命令，请确认设备选择和巡检准备信息。"
            >
              <el-table-column prop="sequenceNo" label="顺序" width="70" />
              <el-table-column prop="detectionId" label="检测 ID" min-width="140" />
              <el-table-column
                prop="inspectionItem"
                label="检测项目"
                min-width="150"
                show-overflow-tooltip
              />
              <el-table-column prop="revisionNo" label="修订" width="70" />
              <el-table-column label="命令内容" min-width="280"
                ><template #default="{ row }">
                  <pre class="command-content">{{ row.content }}</pre>
                </template></el-table-column
              >
              <el-table-column prop="timeoutSeconds" label="规则超时配置（秒）" width="150" />
              <el-table-column label="规则继续配置" width="120"
                ><template #default="{ row }">{{
                  row.continueOnTimeout ? '是' : '否'
                }}</template></el-table-column
              >
            </el-table>
          </el-collapse-item></el-collapse
        >
      </el-form>
    </template>

    <div class="section-heading history-heading"
      ><h3 class="inspection-section-title">执行记录</h3
      ><span class="help-text">当前与历史尝试均保留</span></div
    >
    <el-table
      :data="task.executions"
      :row-key="(row: ExecutionView) => String(row.id)"
      empty-text="暂无执行记录"
    >
      <el-table-column type="expand" width="42"
        ><template #default="{ row }">
          <el-descriptions :column="smallScreen ? 1 : 2" class="record-details">
            <el-descriptions-item label="识别到的 SN">{{
              row.recognizedSerial || '未取得'
            }}</el-descriptions-item>
            <el-descriptions-item label="解析来源">{{
              row.parserRelease || '未取得'
            }}</el-descriptions-item>
          </el-descriptions>
        </template></el-table-column
      >
      <el-table-column label="执行记录" min-width="170"
        ><template #default="{ row }"
          >{{ row.id
          }}<el-tag v-if="isCurrent(row.attemptId)" size="small" class="current-tag"
            >当前尝试</el-tag
          ><p class="help-text">{{ attemptLabel(row.attemptId) }}</p></template
        ></el-table-column
      >
      <el-table-column label="设备 / SN" min-width="230"
        ><template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template></el-table-column
      >
      <el-table-column label="用途" width="110"
        ><template #default="{ row }">{{
          purposeLabel(row.purposeCode)
        }}</template></el-table-column
      >
      <el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><el-tag :type="executionStatusType(row.statusCode)">{{
            executionStatus(row.statusCode)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column prop="fileName" label="日志文件" min-width="180" show-overflow-tooltip />
      <el-table-column label="失败 / 异常说明" min-width="250"
        ><template #default="{ row }">{{
          row.failureMessage || row.failureCode || '—'
        }}</template></el-table-column
      >
      <el-table-column label="提交时间" min-width="170"
        ><template #default="{ row }">{{
          row.submittedAt ? formatDate(row.submittedAt) : '—'
        }}</template></el-table-column
      >
      <el-table-column label="操作" min-width="180" :fixed="smallScreen ? false : 'right'">
        <template #default="{ row }">
          <el-button
            v-if="
              row.purposeCode === 'OFFLINE' &&
              isCurrent(row.attemptId) &&
              ['PENDING', 'RUNNING'].includes(row.statusCode)
            "
            link
            type="primary"
            :disabled="disabled"
            @click="emit('refresh', row.id)"
            >刷新解析</el-button
          >
          <el-button v-if="hasFrozenLog(row)" link type="primary" @click="emit('downloadLog', row)"
            >下载冻结日志</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <div class="section-heading history-heading"
      ><h3 class="inspection-section-title">巡检判定结果</h3></div
    >
    <el-alert
      title="未判定、判定错误或缺失输出均不表示通过。展开记录可查看来源，历史判定保留。"
      type="warning"
      :closable="false"
      show-icon
      class="mb-16px"
    />
    <el-table
      :data="task.results"
      :row-key="(row: ResultView) => String(row.id)"
      empty-text="暂无判定结果，不能视为巡检通过"
    >
      <el-table-column type="expand" width="42"
        ><template #default="{ row }">
          <el-descriptions :column="smallScreen ? 1 : 3" class="record-details">
            <el-descriptions-item label="结果记录">{{ row.id }}</el-descriptions-item>
            <el-descriptions-item label="执行记录">{{
              row.executionId ?? '未提供'
            }}</el-descriptions-item>
            <el-descriptions-item label="尝试">{{
              attemptLabel(row.attemptId)
            }}</el-descriptions-item>
          </el-descriptions>
        </template></el-table-column
      >
      <el-table-column label="尝试" min-width="170"
        ><template #default="{ row }"
          >{{ attemptLabel(row.attemptId)
          }}<el-tag v-if="isCurrent(row.attemptId)" size="small" class="current-tag"
            >当前尝试</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column label="设备 / SN" min-width="230"
        ><template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template></el-table-column
      >
      <el-table-column label="规则 / 修订 ID" min-width="230"
        ><template #default="{ row }"
          >{{ row.ruleName || '未提供规则名称'
          }}<div class="help-text">{{ row.ruleRevisionId ?? '未提供修订' }}</div></template
        ></el-table-column
      >
      <el-table-column label="分类" min-width="130"
        ><template #default="{ row }">{{
          row.categoryName || row.categoryCode || '未提供'
        }}</template></el-table-column
      >
      <el-table-column label="严重级别" width="110"
        ><template #default="{ row }">{{
          row.severityName || severityLabel(row.severityCode)
        }}</template></el-table-column
      >
      <el-table-column label="判定" width="110"
        ><template #default="{ row }"
          ><el-tag :type="outcomeType(row.outcome)">{{
            outcomeLabel(row.outcome)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="实测值 / 单位" min-width="150"
        ><template #default="{ row }"
          >{{ row.measuredValue ?? '未取得' }} {{ row.measuredUnit || '' }}</template
        ></el-table-column
      >
      <el-table-column label="正则匹配" width="110"
        ><template #default="{ row }">{{
          row.regexMatched == null ? '未判定' : row.regexMatched ? '匹配' : '不匹配'
        }}</template></el-table-column
      >
      <el-table-column
        prop="outputSummary"
        label="输出摘要"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column label="判定说明" min-width="260"
        ><template #default="{ row }">{{
          row.detailMessage || row.detailCode || '未提供说明'
        }}</template></el-table-column
      >
    </el-table>

    <div v-if="currentAttempt" class="finish-actions">
      <p class="help-text">检测结果完整且日志解析结束后，可进入报告阶段。</p>
      <el-button type="primary" :disabled="disabled" @click="emit('finish')"
        >完成执行并进入报告</el-button
      >
    </div>
    <el-collapse class="attempt-history">
      <el-collapse-item
        v-if="task.collectionReceipts?.length"
        title="查看在线采集回传版本"
        name="receipts"
      >
        <p class="help-text">每个回传版本独立保留；后续版本不会替换已采用的巡检结果。</p>
        <el-table :data="task.collectionReceipts" :row-key="(row) => String(row.id)">
          <el-table-column prop="executionId" label="巡检执行记录" min-width="170" />
          <el-table-column prop="collectionTaskId" label="平台采集任务" min-width="220" />
          <el-table-column prop="resultVersion" label="结果版本" width="100" />
          <el-table-column prop="externalStatus" label="采集状态" min-width="130" />
          <el-table-column prop="failureCategory" label="失败分类" min-width="150" />
          <el-table-column label="接收时间" min-width="170"
            ><template #default="{ row }">{{
              formatDate(row.receivedAt)
            }}</template></el-table-column
          >
        </el-table>
      </el-collapse-item>
      <el-collapse-item title="查看全部执行尝试" name="attempts">
        <el-table
          :data="task.attempts"
          :row-key="(row: AttemptView) => String(row.id)"
          empty-text="暂无执行尝试"
        >
          <el-table-column label="尝试" min-width="150"
            ><template #default="{ row }">{{ attemptLabel(row.id) }}</template></el-table-column
          >
          <el-table-column label="方式" width="110"
            ><template #default="{ row }">{{
              row.mode === 'ONLINE' ? '在线巡检' : '离线巡检'
            }}</template></el-table-column
          >
          <el-table-column label="状态" min-width="110"
            ><template #default="{ row }">{{
              attemptStatus(row.statusCode)
            }}</template></el-table-column
          >
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
          <el-table-column label="开始时间" min-width="170"
            ><template #default="{ row }">{{
              row.startedAt ? formatDate(row.startedAt) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="完成时间" min-width="170"
            ><template #default="{ row }">{{
              row.finishedAt ? formatDate(row.finishedAt) : '未完成'
            }}</template></el-table-column
          >
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { TagProps } from 'element-plus'
import type {
  TaskDetail,
  CommandView,
  ExecutionView,
  ResultView,
  AttemptView,
  WireLong
} from '@/api/pms/inspection/tasks'
import { formatDate } from '@/utils/formatTime'
import { getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'InspectionExecutionPanel' })
const props = defineProps<{
  task: TaskDetail
  disabled: boolean
  commands: CommandView[]
  selectedDeviceId: WireLong | string
}>()
const emit = defineEmits<{
  deviceChange: [deviceId: WireLong]
  upload: [input: { deviceId: WireLong; file: File; commandHint?: string }]
  refresh: [executionId: WireLong]
  finish: []
  downloadLog: [execution: ExecutionView]
  draftChanged: [dirty: boolean]
}>()
const message = useMessage()
const smallScreen = useMediaQuery('(max-width: 767px)')
const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const commandHint = ref('')
const fileError = ref('')
const currentAttempt = computed(() =>
  props.task.attempts.find((attempt) => String(attempt.id) === String(props.task.currentAttemptId))
)
const isOffline = computed(() => currentAttempt.value?.mode === 'OFFLINE')
const isCurrent = (attemptId?: WireLong) =>
  attemptId != null &&
  props.task.currentAttemptId != null &&
  String(attemptId) === String(props.task.currentAttemptId)
const orderedCommands = computed(() => props.commands)
const commandText = computed(() =>
  orderedCommands.value.map((command) => command.content).join('\n\n')
)
const commandKey = (command: CommandView) => String(command.sequenceNo)
const hintCommands = computed(() =>
  orderedCommands.value.filter(
    (command, index, all) =>
      all.findIndex((candidate) => candidate.content === command.content) === index
  )
)
const deviceLabel = (id: WireLong) => {
  const device = props.task.devices.find((item) => String(item.deviceId) === String(id))
  return device
    ? [device.serialNumber || '未提供 SN', device.deviceName, device.productModel]
        .filter(Boolean)
        .join(' · ')
    : `设备 ${id}`
}
const attemptLabel = (id?: WireLong) => {
  if (id == null) return '未提供尝试'
  const attempt = props.task.attempts.find((item) => String(item.id) === String(id))
  return attempt?.attemptNo != null ? `第 ${attempt.attemptNo} 次 · ${id}` : String(id)
}
const attemptStatuses: Record<string, string> = {
  ACTIVE: '进行中',
  RUNNING: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FAILED: '失败'
}
const executionStatuses: Record<string, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  COLLECTED: '已采集，待解析',
  PASSED: '预检通过',
  CANCEL_REQUESTED: '取消待确认',
  COMPLETE: '已完成',
  PARTIAL: '部分失败',
  FAILED: '失败',
  CANCELLED: '已取消',
  SUCCEEDED: '成功',
  TIMED_OUT: '超时'
}
const purposes: Record<string, string> = {
  OFFLINE: '离线上传',
  PRECHECK: '连接预检',
  COLLECT: '在线采集'
}
const attemptStatus = (status: string) => attemptStatuses[status] || status
const executionStatus = (status: string) => executionStatuses[status] || status
const purposeLabel = (purpose?: string) => (purpose ? purposes[purpose] || purpose : '未提供')
const executionStatusType = (status: string): TagProps['type'] =>
  ['FAILED', 'TIMED_OUT'].includes(status)
    ? 'danger'
    : status === 'PARTIAL'
      ? 'warning'
      : ['COMPLETE', 'SUCCEEDED'].includes(status)
        ? 'success'
        : 'info'
const outcomes: Record<string, string> = {
  PASS: '通过',
  ABNORMAL: '异常',
  UNJUDGED: '未判定',
  ERROR: '判定错误'
}
const outcomeLabel = (outcome?: string) => outcomes[outcome || ''] || outcome || '未判定'
const outcomeType = (outcome?: string): TagProps['type'] =>
  outcome === 'PASS'
    ? 'success'
    : outcome === 'ERROR' || outcome === 'ABNORMAL'
      ? 'danger'
      : 'warning'
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const severityLabel = (code?: string) =>
  severities.value.find((item) => item.value === code)?.label || code || '未提供'
const hasFrozenLog = (execution: ExecutionView) =>
  execution.artifactId != null && execution.fileVersionNo != null && !!execution.fileReferenceKey
const fileSize = (size: number) =>
  size >= 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)} MB` : `${(size / 1024).toFixed(1)} KB`

const clearDraft = () => {
  selectedFile.value = null
  commandHint.value = ''
  fileError.value = ''
  if (fileInput.value) fileInput.value.value = ''
  emit('draftChanged', false)
}
const changeDevice = (deviceId: WireLong) => {
  clearDraft()
  emit('deviceChange', deviceId)
}
const acceptFile = (file: File | undefined) => {
  if (props.disabled || !isOffline.value || !props.selectedDeviceId) return
  selectedFile.value = null
  fileError.value = ''
  if (fileInput.value) fileInput.value.value = ''
  if (!file) return
  if (!/\.(log|txt|zip)$/i.test(file.name)) fileError.value = '仅支持 .log、.txt、.zip 文件。'
  else if (file.size > 50 * 1024 * 1024) fileError.value = '单文件不能超过 50MB，请重新选择。'
  if (fileError.value) {
    return
  }
  selectedFile.value = file
}
const selectFile = (event: Event) => {
  const files = (event.target as HTMLInputElement).files
  if (props.disabled || !isOffline.value || !props.selectedDeviceId) return
  if (files && files.length > 1) {
    acceptFile(undefined)
    fileError.value = '一次只能选择一个日志文件。'
    return
  }
  acceptFile(files?.[0])
}
const dropFiles = (event: DragEvent) => {
  if (props.disabled || !isOffline.value || !props.selectedDeviceId) return
  const files = event.dataTransfer?.files
  if (!files || files.length !== 1) {
    acceptFile(undefined)
    fileError.value = '一次只能选择一个日志文件。'
    return
  }
  acceptFile(files[0])
}
const submitUpload = () => {
  if (
    props.disabled ||
    !isOffline.value ||
    !currentAttempt.value ||
    !selectedFile.value ||
    !props.selectedDeviceId
  )
    return
  if (
    commandHint.value &&
    !props.commands.some((command) => command.content === commandHint.value)
  ) {
    fileError.value = '命令提示已不在当前冻结清单中，请重新选择。'
    return
  }
  emit('upload', {
    deviceId: props.selectedDeviceId,
    file: selectedFile.value,
    ...(commandHint.value ? { commandHint: commandHint.value } : {})
  })
}
const copyCommands = async () => {
  if (!commandText.value) return
  try {
    await navigator.clipboard.writeText(commandText.value)
    message.success('冻结命令已复制')
  } catch {
    message.warning('复制失败，请从命令清单中手动复制。')
  }
}
const downloadCommands = () => {
  if (!commandText.value) return
  const url = URL.createObjectURL(
    new Blob([commandText.value], { type: 'text/plain;charset=utf-8' })
  )
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${props.task.taskCode}-${String(props.selectedDeviceId)}-命令清单.txt`.replace(
    /[<>:"/\\|?*]/g,
    '_'
  )
  anchor.click()
  URL.revokeObjectURL(url)
}
watch(() => [String(props.task.id), String(props.task.version)], clearDraft)
watch(() => String(props.selectedDeviceId), clearDraft)
watch(
  () => props.commands,
  () => {
    if (
      commandHint.value &&
      !props.commands.some((command) => command.content === commandHint.value)
    )
      commandHint.value = ''
  }
)
watch([selectedFile, commandHint], () =>
  emit('draftChanged', !!selectedFile.value || !!commandHint.value)
)
</script>

<style scoped>
.execution-panel {
  min-width: 0;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.section-heading h2,
.section-heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.section-heading h3 {
  font-size: 14px;
}
.inspection-section-title {
  display: inline-block;
  padding-bottom: 6px;
  color: #1a2332;
  border-bottom: 2px solid var(--inspection-blue, #4a90e2);
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.upload-form {
  margin-top: 16px;
}
.upload-form :deep(.el-select) {
  width: 100%;
}
.upload-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  margin-top: 16px;
}
.upload-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.command-list {
  margin-bottom: 16px;
  --el-collapse-border-color: #f0f2f5;
}
.upload-form > .section-heading {
  margin-top: 24px;
}
.command-content {
  margin: 0;
  font-family: Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.help-text {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.file-drop-zone {
  position: relative;
  width: 100%;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}
.file-drop-zone:not(.is-disabled):hover {
  color: #4a90e2;
  border-color: #4a90e2;
}
.file-picker {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  min-height: 100px;
  padding: 12px;
  color: #888;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-radius: 4px;
}
.file-picker small {
  font-size: 12px;
}
.file-picker:disabled {
  cursor: not-allowed;
  background: #fafbfc;
}
.record-details {
  padding: 12px 20px;
}
.file-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
  overflow-wrap: anywhere;
}
.file-error {
  width: 100%;
  margin: 8px 0;
  color: var(--el-color-danger);
}
.history-heading {
  margin-top: 20px;
}
.current-tag {
  margin-left: 8px;
}
.finish-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 24px;
}
.attempt-history {
  margin-top: 24px;
}
@media (max-width: 767px) {
  .section-heading,
  .finish-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .upload-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .section-actions {
    flex-wrap: wrap;
  }
}
</style>
