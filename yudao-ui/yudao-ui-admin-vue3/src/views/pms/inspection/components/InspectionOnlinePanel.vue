<template>
  <section
    v-show="onlineAttempt || onlineHistory.length || pending"
    class="online-panel"
    aria-labelledby="inspection-online-title"
  >
    <div v-show="onlineAttempt || pending" class="section-heading"
      ><h2 id="inspection-online-title">在线巡检执行</h2
      ><el-tag v-if="currentAttempt">第 {{ currentAttempt.attemptNo ?? '—' }} 次尝试</el-tag></div
    >
    <el-alert
      v-if="pending && !sameOrigin"
      :title="`任务 ${pending.taskId} 的原操作尚未确认，请返回原任务处理；本任务暂不能新建在线请求。`"
      type="warning"
      :closable="false"
      show-icon
      class="mb-16px"
      ><template #default
        ><el-button type="primary" :disabled="busy" @click="returnToOrigin"
          >返回原任务</el-button
        ></template
      ></el-alert
    >
    <el-alert
      v-if="operationError"
      :title="operationError"
      :type="pending ? 'warning' : 'error'"
      :closable="false"
      show-icon
      class="mb-16px"
    />
    <el-alert
      v-if="pending && sameOrigin && !onlineAttempt"
      :title="`${pendingLabel}。请刷新任务资料后继续处理原操作。`"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default
        ><el-button :disabled="busy" @click="emit('changed')">刷新原任务资料</el-button></template
      >
    </el-alert>
    <el-alert
      v-if="!fresh && onlineAttempt"
      title="执行上下文待刷新，暂不能继续写入。请刷新任务资料后办理。"
      type="warning"
      :closable="false"
      show-icon
      class="mb-16px"
      ><template #default
        ><el-button :disabled="busy" @click="emit('changed')">刷新任务资料</el-button></template
      ></el-alert
    >
    <template v-if="onlineAttempt && (!pending || sameOrigin)">
      <el-alert
        title="先完成设备连接预检，通过后再开始巡检。"
        type="info"
        :closable="false"
        show-icon
      />
      <h3 class="inspection-section-title">设备连接信息</h3>
      <el-form
        ref="connectionForm"
        :model="form"
        :rules="connectionRules"
        label-position="top"
        autocomplete="off"
        class="connection-form"
      >
        <el-form-item label="当前设备"
          ><el-select
            :model-value="String(selectedDeviceId || '')"
            :disabled="disabled || busy || !!pending"
            placeholder="选择巡检设备"
            @change="changeDevice"
            ><el-option
              v-for="device in task.devices"
              :key="String(device.deviceId)"
              :value="String(device.deviceId)"
              :label="deviceLabel(device.deviceId)" /></el-select
        ></el-form-item>
        <div class="form-grid">
          <el-form-item label="认证方式"
            ><el-radio-group v-model="form.authentication" :disabled="contextLocked"
              ><el-radio value="TEMPORARY">临时认证</el-radio
              ><el-radio value="SAVED">保存的连接凭证</el-radio></el-radio-group
            ></el-form-item
          >
          <el-form-item label="协议"
            ><el-radio-group
              v-model="form.protocol"
              :disabled="contextLocked"
              @change="changeProtocol"
              ><el-radio value="SSH">SSH</el-radio
              ><el-radio value="TELNET">Telnet</el-radio></el-radio-group
            ></el-form-item
          >
        </div>
        <template v-if="form.authentication === 'SAVED'">
          <el-form-item label="可用保存连接" prop="credentialId"
            ><el-select
              v-model="form.credentialId"
              :disabled="contextLocked || connectionsLoading || !!connectionsError"
              :loading="connectionsLoading"
              filterable
              clearable
              placeholder="选择当前设备和协议下的授权连接"
              ><el-option
                v-for="connection in connections"
                :key="String(connection.id)"
                :value="String(connection.id)"
                :label="`${connection.name} · ${connection.username}@${connection.host}:${connection.port}`" /></el-select
          ></el-form-item>
          <el-alert
            v-if="connectionsError"
            :title="connectionsError"
            type="error"
            :closable="false"
            class="mb-16px"
            ><template #default
              ><el-button
                link
                type="primary"
                :disabled="busy || !!pending || connectionsLoading"
                @click="loadConnections"
                >重新加载连接</el-button
              ></template
            ></el-alert
          >
          <el-descriptions
            v-if="selectedConnection"
            :column="smallScreen ? 1 : 3"
            border
            class="mb-16px"
            ><el-descriptions-item label="地址"
              >{{ selectedConnection.host }}:{{ selectedConnection.port }}</el-descriptions-item
            ><el-descriptions-item label="用户名">{{
              selectedConnection.username
            }}</el-descriptions-item
            ><el-descriptions-item label="凭证 ID">{{
              selectedConnection.id
            }}</el-descriptions-item></el-descriptions
          >
        </template>
        <template v-else>
          <div class="form-grid"
            ><el-form-item label="设备地址" prop="host"
              ><el-input
                v-model="form.host"
                :disabled="contextLocked"
                maxlength="253"
                placeholder="IP 或主机名" /></el-form-item
            ><el-form-item label="端口" prop="port"
              ><el-input-number
                v-model="form.port"
                :disabled="contextLocked"
                :min="1"
                :max="65535"
                :precision="0"
                controls-position="right" /></el-form-item
          ></div>
          <div class="form-grid"
            ><el-form-item label="用户名" prop="username"
              ><el-input
                v-model="form.username"
                :disabled="contextLocked"
                maxlength="128"
                autocomplete="off" /></el-form-item
            ><el-form-item label="临时密码"
              ><el-input
                v-model="password"
                type="password"
                show-password
                autocomplete="new-password"
                :disabled="secretLocked"
                placeholder="每次正式下发重新输入" /></el-form-item
          ></div>
          <p class="help-text"
            >临时密码仅用于本次连接操作，结束或切换任务、设备后清除。正式巡检需重新输入。</p
          >
        </template>
        <div class="online-section">
          <div class="section-heading"
            ><h3 class="inspection-section-title">连接预检结果</h3>
            <el-button
              v-if="!pending"
              type="primary"
              :disabled="!canPrecheck || hasUnfinishedExecution"
              :loading="busy"
              @click="startReserve('PRECHECK')"
              >连接预检</el-button
            >
          </div>
          <el-form-item label="已通过的连接预检"
            ><el-select
              v-model="form.precheckId"
              :disabled="contextLocked"
              clearable
              placeholder="选择同设备、同尝试、同连接上下文且未过期的通过记录"
              ><el-option
                v-for="precheck in validPrechecks"
                :key="String(precheck.id)"
                :value="String(precheck.id)"
                :label="`${precheck.id} · ${precheck.protocol || ''} ${precheck.host || '保存连接'} · 到期 ${formatDate(precheck.precheckExpiresAt)}`" /></el-select
          ></el-form-item>
          <p class="help-text"
            >通过记录默认有效15分钟，以实际到期时间为准。仅显示与当前认证方式和连接信息一致的记录；连接信息变化后需重新选择或预检，全部历史记录保留在下方。</p
          >
          <el-descriptions
            v-if="selectedPrecheck"
            :column="smallScreen ? 1 : 3"
            class="precheck-summary"
          >
            <el-descriptions-item label="预检结果">{{
              statusLabel(selectedPrecheck.statusCode)
            }}</el-descriptions-item>
            <el-descriptions-item label="预检记录">{{ selectedPrecheck.id }}</el-descriptions-item>
            <el-descriptions-item label="到期时间">{{
              formatDate(selectedPrecheck.precheckExpiresAt) || '未取得'
            }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="online-section">
          <h3 class="inspection-section-title">正式巡检</h3>
          <p class="help-text">使用已通过的预检记录，执行当前设备的整批巡检命令。</p>
          <div v-if="pending && sameOrigin" class="recovery-panel">
            <p class="help-text">{{ pendingLabel }}。上下文已固定，继续办理使用原请求身份。</p>
            <el-button
              type="primary"
              :disabled="
                disabled ||
                busy ||
                !canExecute ||
                !fresh ||
                (pending.kind === 'dispatch' &&
                  !pending.lookup &&
                  needsTemporaryPassword &&
                  !password)
              "
              :loading="busy"
              @click="retryPending"
              >{{
                pending.kind === 'dispatch'
                  ? pending.lookup
                    ? '重试原查询请求'
                    : '继续原下发请求'
                  : '重试原请求'
              }}</el-button
            >
            <el-button
              v-if="pending.kind === 'dispatch'"
              :disabled="disabled || busy || !canExecute || !fresh"
              @click="queryOriginal"
              >查询原平台请求</el-button
            >
          </div>
          <div v-else-if="!pending" class="section-actions"
            ><el-button
              type="primary"
              :disabled="!canCollect || hasUnfinishedExecution || !selectedPrecheck"
              :loading="busy"
              @click="startReserve('COLLECT')"
              >开始巡检</el-button
            ></div
          >
          <p v-if="hasUnfinishedExecution && !pending" class="help-text"
            >该设备已有未结束的执行记录，请先恢复、刷新或取消原记录。</p
          >
          <el-button v-if="!pending" link :disabled="busy || disabled" @click="clearForm"
            >清除连接输入</el-button
          >
        </div>
      </el-form>
      <el-collapse class="command-list"
        ><el-collapse-item title="查看本次巡检命令清单（连接预检不执行命令）" name="commands"
          ><el-table :data="commands" :row-key="commandKey" empty-text="当前设备暂无冻结命令"
            ><el-table-column label="批次顺序" width="100"
              ><template #default="{ row, $index }">{{
                row.sequenceNo ?? $index + 1
              }}</template></el-table-column
            ><el-table-column
              prop="inspectionItem"
              label="检测项目"
              min-width="170"
            /><el-table-column label="规则修订" min-width="160"
              ><template #default="{ row }"
                >R{{ row.revisionNo }} · {{ row.ruleRevisionId }}</template
              ></el-table-column
            ><el-table-column label="命令" min-width="280"
              ><template #default="{ row }">
                <pre class="command-content">{{ row.content }}</pre>
              </template></el-table-column
            ></el-table
          ></el-collapse-item
        ></el-collapse
      >
    </template>

    <el-collapse
      :model-value="onlineAttempt ? ['records'] : historySections"
      @update:model-value="historySections = $event"
      class="online-history"
      :class="{ 'online-history-current': onlineAttempt }"
    >
      <el-collapse-item name="records" :title="`在线巡检历史（${onlineHistory.length} 条）`">
        <div class="section-heading history-heading"
          ><h3 class="inspection-section-title">连接预检记录</h3
          ><span class="help-text">保留各设备与历史尝试</span></div
        >
        <el-table
          :data="precheckHistory"
          :row-key="(row: ExecutionView) => String(row.id)"
          empty-text="暂无在线预检记录"
        >
          <el-table-column label="设备 / SN" min-width="220"
            ><template #default="{ row }">{{
              deviceLabel(row.deviceId)
            }}</template></el-table-column
          ><el-table-column label="预检记录 / 尝试" min-width="180"
            ><template #default="{ row }"
              >{{ row.id }}<p class="help-text">{{ attemptLabel(row.attemptId) }}</p></template
            ></el-table-column
          ><el-table-column label="状态" width="110"
            ><template #default="{ row }"
              ><el-tag :type="statusType(row.statusCode)">{{
                statusLabel(row.statusCode)
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="设备执行时间" min-width="220"
            ><template #default="{ row }"
              >{{ `开始：${row.deviceStartedAt ? formatDate(row.deviceStartedAt) : '未取得'}`
              }}<p class="help-text"
                >结束：{{ row.deviceCompletedAt ? formatDate(row.deviceCompletedAt) : '未取得' }}</p
              ></template
            ></el-table-column
          ><el-table-column label="预检到期" min-width="190"
            ><template #default="{ row }"
              >{{ row.precheckExpiresAt ? formatDate(row.precheckExpiresAt) : '未取得'
              }}<p v-if="row.statusCode === 'PASSED' && row.precheckExpiresAt" class="help-text">{{
                row.precheckExpiresAt > now ? '尚未过期' : '已过期，须重新预检'
              }}</p></template
            ></el-table-column
          >
        </el-table>
        <div class="section-heading history-heading"
          ><h3 class="inspection-section-title">在线执行历史</h3></div
        >
        <el-table
          :data="onlineHistory"
          :row-key="(row: ExecutionView) => String(row.id)"
          empty-text="暂无在线执行记录"
        >
          <el-table-column prop="id" label="执行记录" min-width="150" /><el-table-column
            label="设备 / SN"
            min-width="220"
            ><template #default="{ row }">{{
              deviceLabel(row.deviceId)
            }}</template></el-table-column
          ><el-table-column label="用途 / 尝试" min-width="180"
            ><template #default="{ row }"
              >{{ row.purposeCode === 'PRECHECK' ? '连接预检' : '正式采集'
              }}<p class="help-text">{{ attemptLabel(row.attemptId) }}</p></template
            ></el-table-column
          >
          <el-table-column label="冻结连接" min-width="240"
            ><template #default="{ row }"
              >{{ row.protocol || '—' }}
              {{ row.host || (row.credentialId ? `凭证 ${row.credentialId}` : '未提供地址')
              }}<span v-if="row.port">:{{ row.port }}</span
              ><p class="help-text">{{ row.username || '用户名由保存连接解析' }}</p></template
            ></el-table-column
          ><el-table-column label="状态" min-width="130"
            ><template #default="{ row }"
              ><el-tag :type="statusType(row.statusCode)">{{ statusLabel(row.statusCode) }}</el-tag
              ><p class="help-text">{{ row.collectionStatus || '' }}</p></template
            ></el-table-column
          >
          <el-table-column label="失败 / 取消说明" min-width="230"
            ><template #default="{ row }">{{
              row.failureMessage || row.failureCode || row.collectionFailureCategory || '—'
            }}</template></el-table-column
          ><el-table-column label="提交时间" min-width="170"
            ><template #default="{ row }">{{
              row.submittedAt ? formatDate(row.submittedAt) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="操作" min-width="310" :fixed="smallScreen ? false : 'right'"
            ><template #default="{ row }"
              ><template v-if="isCurrent(row) && onlineAttempt"
                ><el-button
                  v-if="row.statusCode === 'PENDING'"
                  link
                  type="primary"
                  :disabled="writeLocked"
                  @click="resumeDispatch(row)"
                  >恢复原下发</el-button
                ><el-button link type="primary" :disabled="writeLocked" @click="startRefresh(row)"
                  >刷新</el-button
                ><el-button
                  v-if="unfinishedStatuses.includes(row.statusCode)"
                  link
                  type="warning"
                  :disabled="writeLocked"
                  @click="openCancel(row)"
                  >{{ row.statusCode === 'CANCEL_REQUESTED' ? '继续取消' : '取消执行' }}</el-button
                ></template
              ><el-button
                v-if="canQuery && row.collectionExecutionId != null"
                link
                type="primary"
                :disabled="busy"
                @click="downloadLog(row)"
                >平台原日志</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-collapse-item>
    </el-collapse>
    <el-dialog
      class="inspection-page online-dialog"
      v-model="cancelVisible"
      title="取消原在线执行"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
      :show-close="!busy && !pending"
      :close-on-press-escape="!busy && !pending"
      :before-close="closeCancel"
      ><el-alert
        v-if="operationError"
        :title="operationError"
        :type="pending ? 'warning' : 'error'"
        :closable="false"
        class="mb-16px"
      /><p class="help-text">执行记录 {{ cancelRow?.id }}，原采集及取消事实保留。</p
      ><el-form
        ref="cancelForm"
        :model="cancellation"
        :disabled="busy || !!pending"
        label-position="top"
        @submit.prevent="submitCancel"
        ><el-form-item
          label="取消原因"
          prop="reason"
          :rules="[
            {
              required: true,
              whitespace: true,
              max: 2000,
              message: '请填写取消原因（不超过2000字）',
              trigger: 'blur'
            }
          ]"
          ><el-input
            v-model="cancellation.reason"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="busy || !!pending" @click="cancelVisible = false">关闭</el-button
        ><el-button
          v-if="pending?.kind === 'cancel'"
          type="primary"
          :loading="busy"
          :disabled="disabled || !fresh || !sameOrigin || !canExecute"
          @click="retryPending"
          >重试原取消请求</el-button
        ><el-button
          v-else
          type="warning"
          :loading="busy"
          :disabled="writeLocked"
          @click="submitCancel"
          >确认取消</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import type {
  TaskDetail,
  ExecutionView,
  CommandView,
  WireLong,
  TaskSummary
} from '@/api/pms/inspection/tasks'
import * as Api from '@/api/pms/inspection/online'
import type { ReserveInput } from '@/api/pms/inspection/online'
import {
  usableConnections,
  type Connection as SavedConnection
} from '@/api/pms/platform/deviceCollection'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { inspectionFailure } from '../shared/interaction'

defineOptions({ name: 'InspectionOnlinePanel' })
const props = defineProps<{
  task: TaskDetail
  disabled: boolean
  commands: CommandView[]
  selectedDeviceId: WireLong | string
}>()
const emit = defineEmits<{
  deviceChange: [deviceId: WireLong]
  changed: []
  busyChanged: [busy: boolean]
  draftChanged: [dirty: boolean]
}>()
const message = useMessage()
const router = useRouter()
const smallScreen = useMediaQuery('(max-width: 767px)')
const historySections = ref<string | number | Array<string | number>>([])
const busy = ref(false)
const fresh = ref(true)
const operationError = ref('')
const contextVersion = ref<WireLong>(props.task.version)
let contextTaskId = String(props.task.id)
const password = ref('')
const now = ref(Date.now())
let alive = true
let timer: ReturnType<typeof setInterval> | undefined
const sameId = (left?: WireLong, right?: WireLong) =>
  left != null && right != null && String(left) === String(right)
const currentAttempt = computed(() =>
  props.task.attempts.find((attempt) => sameId(attempt.id, props.task.currentAttemptId))
)
const onlineAttempt = computed(() => currentAttempt.value?.mode === 'ONLINE')
const canExecute = computed(() => checkPermi(['pms:inspection-task:execute']))
const canQuery = computed(() => checkPermi(['pms:inspection-task:query']))
const unfinishedStatuses = ['PENDING', 'RUNNING', 'COLLECTED', 'CANCEL_REQUESTED']
const onlineHistory = computed(() =>
  props.task.executions.filter(
    (row) => row.purposeCode === 'PRECHECK' || row.purposeCode === 'COLLECT'
  )
)
const precheckHistory = computed(() =>
  onlineHistory.value.filter((row) => row.purposeCode === 'PRECHECK')
)
const isCurrent = (row: ExecutionView) => sameId(row.attemptId, props.task.currentAttemptId)
const hasUnfinishedExecution = computed(() =>
  onlineHistory.value.some(
    (row) =>
      isCurrent(row) &&
      sameId(row.deviceId, props.selectedDeviceId) &&
      unfinishedStatuses.includes(row.statusCode)
  )
)
const form = reactive({
  authentication: 'TEMPORARY' as 'TEMPORARY' | 'SAVED',
  protocol: 'SSH' as 'SSH' | 'TELNET',
  host: '',
  port: 22,
  username: '',
  credentialId: '',
  precheckId: ''
})
const baseline = ref(JSON.stringify(form))
const connectionForm = ref<FormInstance>()
const connectionRules: FormRules = {
  host: [
    {
      required: true,
      pattern: /^[a-zA-Z0-9.:-]{1,253}$/,
      message: '请填写有效设备地址',
      trigger: 'blur'
    }
  ],
  port: [
    {
      required: true,
      type: 'number',
      min: 1,
      max: 65535,
      message: '请填写有效端口',
      trigger: 'change'
    }
  ],
  username: [
    { required: true, whitespace: true, max: 128, message: '请填写用户名', trigger: 'blur' }
  ],
  credentialId: [{ required: true, message: '请选择可用保存连接', trigger: 'change' }]
}
type Origin = { taskId: string; attemptId: string }
type Pending =
  | (Origin & { kind: 'reserve'; version: WireLong; key: string; data: ReserveInput })
  | (Origin & {
      kind: 'dispatch'
      execution: ExecutionView
      version: WireLong
      taskVersion: WireLong
      key: string
      lookup?: { version: WireLong; key: string }
      lookupCompleted?: boolean
    })
  | (Origin & { kind: 'refresh'; executionId: WireLong; version: WireLong; key: string })
  | (Origin & {
      kind: 'cancel'
      executionId: WireLong
      version: WireLong
      key: string
      reason: string
    })
const pending = shallowRef<Pending>()
const sameOrigin = computed(
  () =>
    !!pending.value &&
    pending.value.taskId === String(props.task.id) &&
    pending.value.attemptId === String(props.task.currentAttemptId)
)
const contextLocked = computed(
  () => props.disabled || busy.value || !!pending.value || !fresh.value || !canExecute.value
)
const writeLocked = computed(
  () =>
    contextLocked.value ||
    !onlineAttempt.value ||
    !['PRECHECK_PENDING', 'EXECUTING'].includes(props.task.statusCode)
)
const secretLocked = computed(
  () =>
    props.disabled ||
    busy.value ||
    !fresh.value ||
    !canExecute.value ||
    (!!pending.value && (pending.value.kind !== 'dispatch' || !sameOrigin.value))
)
const canPrecheck = computed(() => !writeLocked.value && !!props.selectedDeviceId)
const canCollect = computed(
  () => canPrecheck.value && props.task.statusCode === 'EXECUTING' && props.commands.length > 0
)
const needsTemporaryPassword = computed(() =>
  pending.value?.kind === 'dispatch'
    ? pending.value.execution.credentialId == null
    : form.authentication === 'TEMPORARY'
)
const pendingLabel = computed(() =>
  pending.value?.kind === 'reserve'
    ? '预留请求待确认'
    : pending.value?.kind === 'dispatch'
      ? `执行记录 ${pending.value.execution.id} 待确认下发${pending.value.execution.credentialId == null ? '，临时认证需重新输入密码' : '，使用已冻结保存凭证'}`
      : pending.value?.kind === 'cancel'
        ? '原取消请求待确认'
        : '原刷新请求待确认'
)
const normalizeHost = (host?: string) => (host ?? '').trim().toLowerCase()
const matchesPrecheckConnection = (row: ExecutionView) => {
  if (row.protocol !== form.protocol) return false
  if (form.authentication === 'SAVED') return sameId(row.credentialId, form.credentialId)
  return (
    row.credentialId == null &&
    normalizeHost(row.host) === normalizeHost(form.host) &&
    row.port === form.port &&
    (row.username ?? '').trim() === form.username.trim()
  )
}
const validPrechecks = computed(() =>
  precheckHistory.value.filter(
    (row) =>
      isCurrent(row) &&
      sameId(row.deviceId, props.selectedDeviceId) &&
      matchesPrecheckConnection(row) &&
      row.statusCode === 'PASSED' &&
      row.precheckExpiresAt != null &&
      row.precheckExpiresAt > now.value
  )
)
const selectedPrecheck = computed(() =>
  validPrechecks.value.find((row) => String(row.id) === form.precheckId)
)
const deviceLabel = (deviceId: WireLong) => {
  const device = props.task.devices.find((item) => sameId(item.deviceId, deviceId))
  return device
    ? [device.serialNumber || '未提供 SN', device.deviceName, device.productModel]
        .filter(Boolean)
        .join(' · ')
    : `设备 ${deviceId}`
}
const attemptLabel = (attemptId?: WireLong) => {
  const attempt = props.task.attempts.find((item) => sameId(item.id, attemptId))
  return attempt
    ? `第 ${attempt.attemptNo ?? '—'} 次 · ${attemptId}`
    : String(attemptId ?? '未提供尝试')
}
const commandKey = (command: CommandView) =>
  `${command.sequenceNo ?? props.commands.indexOf(command)}:${command.ruleRevisionId}:${command.stableCommandKey}`
const statuses: Record<string, string> = {
  PENDING: '待下发',
  RUNNING: '进行中',
  COLLECTED: '采集后待解析',
  CANCEL_REQUESTED: '取消待确认',
  CANCELLED: '已取消',
  PASSED: '预检通过',
  COMPLETE: '已完成',
  PARTIAL: '部分失败',
  FAILED: '失败'
}
const statusLabel = (status: string) => statuses[status] || status
const statusType = (status: string): TagProps['type'] =>
  ['PASSED', 'COMPLETE'].includes(status)
    ? 'success'
    : status === 'FAILED'
      ? 'danger'
      : ['PARTIAL', 'CANCEL_REQUESTED'].includes(status)
        ? 'warning'
        : 'info'

const connections = ref<SavedConnection[]>([])
const connectionsLoading = ref(false)
const connectionsError = ref('')
let connectionSequence = 0
const selectedConnection = computed(() =>
  connections.value.find((row) => String(row.id) === form.credentialId)
)
const connectionIdentity = () =>
  `${props.task.id}:${props.task.projectId}:${props.selectedDeviceId}:${form.protocol}`
const loadConnections = async () => {
  const current = ++connectionSequence
  connections.value = []
  connectionsError.value = ''
  if (
    !alive ||
    !onlineAttempt.value ||
    !props.selectedDeviceId ||
    form.authentication !== 'SAVED'
  ) {
    connectionsLoading.value = false
    return
  }
  const identity = connectionIdentity()
  connectionsLoading.value = true
  try {
    const result = await usableConnections(
      props.task.projectId,
      props.selectedDeviceId,
      form.protocol,
      undefined
    )
    if (!alive || current !== connectionSequence || identity !== connectionIdentity()) return
    connections.value = result
    if (form.credentialId && !result.some((row) => String(row.id) === form.credentialId))
      form.credentialId = ''
  } catch {
    if (!alive || current !== connectionSequence || identity !== connectionIdentity()) return
    connections.value = []
    connectionsError.value = '可用连接加载失败，请重新加载。任务上下文须刷新后继续。'
    fresh.value = false
    emit('changed')
  } finally {
    if (alive && current === connectionSequence) connectionsLoading.value = false
  }
}
const clearForm = () => {
  password.value = ''
  Object.assign(form, {
    authentication: 'TEMPORARY',
    protocol: 'SSH',
    host: '',
    port: 22,
    username: '',
    credentialId: '',
    precheckId: ''
  })
  baseline.value = JSON.stringify(form)
  connectionForm.value?.clearValidate()
}
const changeDevice = (id: WireLong) => {
  if (busy.value || pending.value) return
  clearForm()
  emit('deviceChange', id)
}
const changeProtocol = () => {
  password.value = ''
  form.port = form.protocol === 'SSH' ? 22 : 23
  form.credentialId = ''
  form.precheckId = ''
}
const origin = (): Origin => ({
  taskId: String(props.task.id),
  attemptId: String(props.task.currentAttemptId)
})
const latestVersion = (left: WireLong, right: WireLong): WireLong => {
  const leftText = String(left).replace(/^0+/, '')
  const rightText = String(right).replace(/^0+/, '')
  return leftText.length === rightText.length
    ? leftText >= rightText
      ? left
      : right
    : leftText.length > rightText.length
      ? left
      : right
}
const updateContext = (task: TaskSummary, source: Origin) => {
  if (
    !alive ||
    source.taskId !== String(props.task.id) ||
    source.attemptId !== String(props.task.currentAttemptId)
  )
    return
  contextVersion.value = latestVersion(contextVersion.value, task.version)
  emit('changed')
}
const startReserve = async (purpose: ReserveInput['purpose']) => {
  now.value = Date.now()
  if (
    writeLocked.value ||
    hasUnfinishedExecution.value ||
    !props.selectedDeviceId ||
    (purpose === 'COLLECT' && (!canCollect.value || !selectedPrecheck.value))
  )
    return
  if (!(await connectionForm.value?.validate().catch(() => false)) || writeLocked.value) return
  if (
    form.authentication === 'SAVED' &&
    (!selectedConnection.value || connectionsLoading.value || connectionsError.value)
  )
    return
  if (form.authentication === 'TEMPORARY' && !password.value) {
    message.warning('请填写本次下发使用的临时密码')
    return
  }
  pending.value = {
    ...origin(),
    kind: 'reserve',
    version: contextVersion.value,
    key: generateUUID(),
    data: {
      deviceId: props.selectedDeviceId,
      purpose,
      ...(purpose === 'COLLECT' ? { precheckExecutionId: selectedPrecheck.value!.id } : {}),
      connection:
        form.authentication === 'SAVED'
          ? { protocol: form.protocol, credentialId: form.credentialId }
          : {
              protocol: form.protocol,
              host: form.host.trim(),
              port: form.port,
              username: form.username.trim()
            }
    }
  }
  await retryPending()
}
const bindDispatchForm = (execution: ExecutionView) => {
  password.value = ''
  Object.assign(form, {
    authentication: execution.credentialId != null ? 'SAVED' : 'TEMPORARY',
    protocol: execution.protocol || 'SSH',
    host: execution.host || '',
    port: execution.port || (execution.protocol === 'TELNET' ? 23 : 22),
    username: execution.username || '',
    credentialId: execution.credentialId != null ? String(execution.credentialId) : '',
    precheckId: execution.precheckExecutionId != null ? String(execution.precheckExecutionId) : ''
  })
}
const resumeDispatch = (row: ExecutionView) => {
  if (writeLocked.value || !isCurrent(row) || row.statusCode !== 'PENDING') return
  bindDispatchForm(row)
  pending.value = {
    ...origin(),
    kind: 'dispatch',
    execution: { ...row },
    version: row.version,
    taskVersion: contextVersion.value,
    key: generateUUID()
  }
  operationError.value = '已选定原执行记录，请重新输入临时密码继续，或查询原平台请求。'
}
const startRefresh = async (row: ExecutionView) => {
  if (writeLocked.value || !isCurrent(row)) return
  pending.value = {
    ...origin(),
    kind: 'refresh',
    executionId: row.id,
    version: contextVersion.value,
    key: generateUUID()
  }
  await retryPending()
}
const cancelVisible = ref(false)
const cancelRow = shallowRef<ExecutionView>()
const cancellation = reactive({ reason: '' })
const cancelForm = ref<FormInstance>()
const openCancel = (row: ExecutionView) => {
  if (writeLocked.value || !isCurrent(row)) return
  cancelRow.value = { ...row }
  cancellation.reason = row.statusCode === 'CANCEL_REQUESTED' ? row.failureMessage || '' : ''
  operationError.value = ''
  cancelVisible.value = true
  cancelForm.value?.clearValidate()
}
const closeCancel = (done: () => void) => {
  if (!busy.value && !pending.value) done()
}
watch(cancelVisible, (open) => {
  if (!open && !busy.value && !pending.value) {
    cancellation.reason = ''
    cancelRow.value = undefined
  }
})
const submitCancel = async () => {
  if (
    writeLocked.value ||
    !cancelRow.value ||
    !(await cancelForm.value?.validate().catch(() => false)) ||
    writeLocked.value
  )
    return
  pending.value = {
    ...origin(),
    kind: 'cancel',
    executionId: cancelRow.value.id,
    version: contextVersion.value,
    key: generateUUID(),
    reason: cancellation.reason.trim()
  }
  await retryPending()
}
const markFailure = (error: unknown, source: Origin) => {
  if (!alive) return
  const failure = inspectionFailure(error)
  const currentContext =
    source.taskId === String(props.task.id) &&
    source.attemptId === String(props.task.currentAttemptId)
  operationError.value = currentContext
    ? failure.message
    : `原任务 ${source.taskId}：${failure.message}`
  if (!failure.unknown) {
    pending.value = undefined
    if (currentContext) {
      fresh.value = false
      emit('changed')
    }
    operationError.value += ' 请刷新任务资料后，依据最新执行记录继续办理。'
  }
}
const retryPending = async () => {
  if (!pending.value || busy.value || props.disabled || !canExecute.value || !fresh.value) return
  if (!sameOrigin.value) {
    operationError.value = '原操作属于其他任务或执行尝试，请返回原上下文恢复。'
    return
  }
  if (pending.value.kind === 'dispatch' && pending.value.lookup) {
    await queryOriginal()
    return
  }
  busy.value = true
  operationError.value = ''
  const source = pending.value
  try {
    while (pending.value && alive) {
      const intent = pending.value
      if (
        intent.taskId !== String(props.task.id) ||
        intent.attemptId !== String(props.task.currentAttemptId)
      )
        break
      if (props.disabled || !fresh.value || !canExecute.value) {
        operationError.value = '当前写入条件已变化，原请求保留，请刷新资料后继续。'
        break
      }
      if (intent.kind === 'reserve') {
        const result = await Api.reserve(intent.taskId, intent.data, intent.version, intent.key)
        if (!alive) return
        pending.value = {
          taskId: intent.taskId,
          attemptId: intent.attemptId,
          kind: 'dispatch',
          execution: result.execution,
          version: result.execution.version,
          taskVersion: result.task.version,
          key: generateUUID()
        }
        if (sameOrigin.value)
          contextVersion.value = latestVersion(contextVersion.value, result.task.version)
        if (!sameOrigin.value) break
      } else if (intent.kind === 'dispatch') {
        if (intent.execution.credentialId == null && !password.value) {
          operationError.value = '请重新输入临时密码继续原下发请求，或查询原平台请求。'
          emit('changed')
          break
        }
        try {
          const result = await Api.dispatch(
            intent.taskId,
            intent.execution.id,
            intent.execution.credentialId == null ? password.value : undefined,
            intent.version,
            intent.key
          )
          if (!alive) return
          pending.value = undefined
          if (
            intent.taskId === String(props.task.id) &&
            intent.attemptId === String(props.task.currentAttemptId)
          )
            baseline.value = JSON.stringify(form)
          updateContext(result.task, intent)
          if (
            intent.taskId === String(props.task.id) &&
            intent.attemptId === String(props.task.currentAttemptId)
          )
            message.success('原请求已提交平台，请刷新查看真实预检或采集状态')
        } finally {
          password.value = ''
        }
      } else if (intent.kind === 'refresh') {
        const result = await Api.refresh(
          intent.taskId,
          intent.executionId,
          intent.version,
          intent.key
        )
        if (!alive) return
        pending.value = undefined
        updateContext(result, intent)
      } else {
        const result = await Api.cancel(
          intent.taskId,
          intent.executionId,
          intent.reason,
          intent.version,
          intent.key
        )
        if (!alive) return
        pending.value = undefined
        if (
          intent.taskId === String(props.task.id) &&
          intent.attemptId === String(props.task.currentAttemptId)
        ) {
          cancelVisible.value = false
          cancellation.reason = ''
        }
        updateContext(result, intent)
      }
    }
  } catch (error) {
    markFailure(error, source)
  } finally {
    password.value = ''
    if (alive) busy.value = false
  }
}
const queryOriginal = async () => {
  if (
    busy.value ||
    props.disabled ||
    !canExecute.value ||
    !fresh.value ||
    pending.value?.kind !== 'dispatch' ||
    !sameOrigin.value
  )
    return
  const intent = pending.value
  if (!intent.lookup)
    pending.value = {
      ...intent,
      lookup: { version: contextVersion.value, key: generateUUID() }
    }
  const lookup = (pending.value as Extract<Pending, { kind: 'dispatch' }>).lookup!
  busy.value = true
  operationError.value = ''
  try {
    const result = await Api.refresh(intent.taskId, intent.execution.id, lookup.version, lookup.key)
    if (!alive) return
    if (
      pending.value?.kind === 'dispatch' &&
      pending.value.taskId === intent.taskId &&
      sameId(pending.value.execution.id, intent.execution.id)
    )
      pending.value = { ...pending.value, lookup: undefined, lookupCompleted: true }
    updateContext(result, intent)
    operationError.value =
      intent.taskId === String(props.task.id) &&
      intent.attemptId === String(props.task.currentAttemptId)
        ? '已查询原平台请求，正在刷新任务资料；尚未确认下发时，可重新输入密码继续原请求。'
        : `已查询原任务 ${intent.taskId} 的平台请求，请返回原上下文查看。`
  } catch (error) {
    if (!alive) return
    const failure = inspectionFailure(error)
    const currentContext =
      intent.taskId === String(props.task.id) &&
      intent.attemptId === String(props.task.currentAttemptId)
    operationError.value = currentContext
      ? failure.message
      : `原任务 ${intent.taskId}：${failure.message}`
    if (!failure.unknown) {
      if (pending.value?.kind === 'dispatch')
        pending.value = { ...pending.value, lookup: undefined }
      if (currentContext) {
        fresh.value = false
        emit('changed')
      }
    }
  } finally {
    password.value = ''
    if (alive) busy.value = false
  }
}
const downloadLog = async (row: ExecutionView) => {
  if (!canQuery.value || busy.value) return
  const taskId = String(props.task.id)
  const target = window.open('about:blank', '_blank')
  if (!target) {
    message.warning('浏览器阻止了下载窗口，请允许弹窗后重试')
    return
  }
  target.opener = null
  try {
    const url = await Api.download(taskId, row.id)
    if (!alive || taskId !== String(props.task.id)) {
      target.close()
      return
    }
    target.location.replace(url)
  } catch {
    target.close()
    if (alive && taskId === String(props.task.id))
      message.error('平台原日志尚不可用或无权下载，请刷新记录后重试')
  }
}
const returnToOrigin = () =>
  pending.value && router.push(`/pms/inspection/tasks/${encodeURIComponent(pending.value.taskId)}`)
const mayLeave = () => {
  if (busy.value || (pending.value && pending.value.taskId === String(props.task.id))) {
    message.warning('请先确认原在线操作结果，再切换任务。')
    return false
  }
  return true
}
onBeforeRouteLeave(mayLeave)
onBeforeRouteUpdate(mayLeave)
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (busy.value || pending.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
watch(busy, (value) => emit('busyChanged', value), { immediate: true, flush: 'sync' })
watch(
  () =>
    !!password.value ||
    JSON.stringify(form) !== baseline.value ||
    !!cancellation.reason ||
    !!pending.value,
  (dirty) => emit('draftChanged', dirty),
  { immediate: true }
)
watch([() => String(props.task.id), () => String(props.selectedDeviceId)], () => {
  connectionSequence++
  connections.value = []
  connectionsError.value = ''
  connectionsLoading.value = false
  password.value = ''
  cancelVisible.value = false
  cancellation.reason = ''
  cancelRow.value = undefined
  if (!pending.value) clearForm()
})
watch(
  () => props.task,
  (task) => {
    contextVersion.value =
      contextTaskId === String(task.id)
        ? latestVersion(contextVersion.value, task.version)
        : task.version
    contextTaskId = String(task.id)
    fresh.value = true
    if (
      !pending.value ||
      pending.value.taskId !== String(task.id) ||
      pending.value.attemptId !== String(task.currentAttemptId) ||
      busy.value
    )
      return
    const intent = pending.value
    if (intent.kind === 'dispatch' && intent.lookupCompleted) {
      const row = task.executions.find((execution) => sameId(execution.id, intent.execution.id))
      if (
        row &&
        (row.collectionExecutionId != null || !unfinishedStatuses.includes(row.statusCode))
      ) {
        pending.value = undefined
        baseline.value = JSON.stringify(form)
        operationError.value = ''
      }
    } else if (intent.kind === 'cancel') {
      const row = task.executions.find((execution) => sameId(execution.id, intent.executionId))
      if (row && row.statusCode === 'CANCELLED') {
        pending.value = undefined
        cancelVisible.value = false
        cancellation.reason = ''
        operationError.value = ''
      }
    }
  }
)
watch(
  [
    () => String(props.task.currentAttemptId),
    () => form.authentication,
    () => form.protocol,
    () => (form.authentication === 'SAVED' ? form.credentialId : ''),
    () => (form.authentication === 'TEMPORARY' ? normalizeHost(form.host) : ''),
    () => (form.authentication === 'TEMPORARY' ? form.port : undefined),
    () => (form.authentication === 'TEMPORARY' ? form.username.trim() : '')
  ],
  () => {
    // Restoring a pending dispatch must retain its frozen connection and precheck selection.
    if (pending.value) return
    form.precheckId = ''
    password.value = ''
  }
)
watch(
  [
    () => String(props.task.id),
    () => String(props.selectedDeviceId),
    () => form.protocol,
    () => form.authentication
  ],
  () => {
    password.value = ''
    if (!pending.value) {
      form.credentialId = ''
      form.precheckId = ''
      void loadConnections()
    }
  },
  { immediate: true }
)
onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 10000)
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  alive = false
  connectionSequence++
  password.value = ''
  if (timer) clearInterval(timer)
  window.removeEventListener('beforeunload', beforeUnload)
  emit('busyChanged', false)
  emit('draftChanged', false)
})
</script>

<style scoped>
.online-panel {
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
  margin: 16px 0 10px;
  color: #1a2332;
  font-size: 14px;
  border-bottom: 2px solid var(--inspection-blue, #4a90e2);
}
.section-heading .inspection-section-title {
  margin: 0;
}
.online-section {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid #f0f2f5;
}
.online-section > .inspection-section-title {
  margin-top: 0;
}
.precheck-summary {
  padding: 12px;
  background: #fafbfc;
  border-radius: 4px;
}
.precheck-summary :deep(.el-descriptions__body) {
  background: transparent;
}
.help-text {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.connection-form {
  margin-top: 4px;
}
.connection-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.connection-form :deep(.el-select),
.connection-form :deep(.el-input-number) {
  width: 100%;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.section-actions,
.recovery-panel {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.recovery-panel .help-text {
  width: 100%;
}
.section-actions :deep(.el-button + .el-button),
.recovery-panel :deep(.el-button + .el-button) {
  margin-left: 0;
}
.command-list,
.history-heading {
  margin-top: 16px;
}
.online-history {
  margin-top: 16px;
  --el-collapse-header-height: 44px;
  --el-collapse-border-color: #f0f2f5;
}
.online-history-current {
  border-top: 0;
}
.online-history-current :deep(.el-collapse-item__header) {
  display: none;
}
.online-history :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}
.online-history :deep(.el-collapse-item__header) {
  color: #1a2332;
  font-size: 13px;
}
.online-dialog :deep(.el-dialog__title) {
  font-size: 15px;
}
.command-content {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 767px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .section-heading {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
