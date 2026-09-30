<template>
  <Dialog
    v-model="visible"
    :title="`${source?.title || '设备'} · 命令采集与日志`"
    width="min(1080px, 96vw)"
    @closed="onClosed"
  >
    <el-alert
      title="请核对设备和下发命令。采集日志自动回传到发起的业务记录，业务完成仍按原流程确认。"
      type="warning"
      :closable="false"
      class="mb-16px"
    />
    <el-form
      v-if="canDispatch"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="90px"
      :disabled="busy || submissionUncertain"
      autocomplete="off"
    >
      <el-form-item v-if="props.entry === 'center'" label="目标设备" prop="deviceId">
        <ProjectDeviceSelect v-model="form.deviceId" :project-id="source?.projectId" />
      </el-form-item>
      <el-form-item label="连接协议" prop="protocol">
        <el-radio-group
          v-model="form.protocol"
          @change="form.port = form.protocol === 'SSH' ? 22 : 23"
        >
          <el-radio value="SSH">SSH</el-radio><el-radio value="TELNET">Telnet</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="命令来源">
        <el-radio-group v-model="commandMode">
          <el-radio value="template">已发布模板</el-radio>
          <el-radio v-if="source?.manualAllowed" value="manual">手工命令</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="commandMode === 'template'" label="命令模板" prop="templateId">
        <el-select
          v-model="form.templateId"
          filterable
          placeholder="请选择适用的已发布模板"
          class="!w-full"
        >
          <el-option
            v-for="item in templates"
            :key="item.id"
            :value="item.id!"
            :label="`${item.name} · v${item.revision}${item.deviceModel ? ' · ' + item.deviceModel : ''}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="认证方式">
        <el-radio-group v-model="credentialMode">
          <el-radio value="temporary">本次临时密码</el-radio>
          <el-radio value="saved" :disabled="!checkPermi(['pms:device-credential:use'])"
            >已保存连接</el-radio
          >
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="credentialMode === 'saved'" label="保存连接" prop="credentialId">
        <el-select
          v-model="form.credentialId"
          placeholder="仅显示当前设备、协议和命令范围内的有效授权"
          class="!w-full"
        >
          <el-option
            v-for="item in connections"
            :key="item.id"
            :value="item.id"
            :label="`${item.name} · ${item.host}:${item.port} · ${item.username}`"
          />
        </el-select>
      </el-form-item>
      <el-row v-else :gutter="16">
        <el-col :xs="24" :sm="16"
          ><el-form-item label="设备地址" prop="host"
            ><el-input v-model="form.host" placeholder="IP 或主机名" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="8"
          ><el-form-item label="端口" prop="port"
            ><el-input-number
              v-model="form.port"
              :min="1"
              :max="65535"
              controls-position="right"
              class="!w-full" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="用户名" prop="username"
            ><el-input v-model="form.username" autocomplete="off" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="本次密码" prop="password"
            ><el-input
              v-model="form.password"
              type="password"
              autocomplete="new-password" /></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="执行命令" prop="commands">
        <el-input
          v-if="commandMode === 'manual'"
          v-model="form.commands"
          type="textarea"
          :rows="6"
          :maxlength="65536"
          placeholder="每行一条命令；请勿在命令中填写密码或密钥"
        />
        <el-input
          v-else
          :model-value="selectedTemplate?.commands || ''"
          type="textarea"
          :rows="6"
          readonly
          placeholder="选择模板后显示本次执行命令"
        />
      </el-form-item>
      <el-alert
        v-if="form.retryOfId"
        title="重新执行将创建新任务，保留原任务及日志。请核对后下发。"
        type="info"
        :closable="false"
        class="mb-12px"
      />
      <el-form-item
        ><el-button type="primary" :loading="busy" :disabled="submissionUncertain" @click="submit"
          >确认下发命令</el-button
        ></el-form-item
      >
    </el-form>
    <div class="flex items-center justify-between mb-12px"
      ><strong>执行历史</strong
      ><div>
        <el-button v-if="hasPendingTask" @click="toggleAutoRefresh">
          {{ autoRefresh ? '暂停自动刷新' : '继续自动刷新' }}
        </el-button>
        <el-button :loading="loading" @click="refresh()">刷新状态</el-button>
      </div></div
    >
    <el-alert
      v-if="submissionUncertain"
      title="下发响应超时或连接中断，正在按原请求查询任务状态，请勿重复下发。"
      type="warning"
      :closable="false"
      class="mb-12px"
    />
    <p v-if="hasPendingTask" class="text-[var(--el-text-color-secondary)]">
      {{
        autoRefresh
          ? '正在等待执行结果，每 2 秒自动刷新状态。'
          : '页面自动刷新已暂停，后台仍会查询执行结果；可手动刷新状态。'
      }}
    </p>
    <el-table v-loading="loading" :data="rows" empty-text="暂无执行记录">
      <el-table-column label="设备 / 目标" min-width="180"
        ><template #default="{ row }"
          >{{ row.task.deviceName }}<br /><span class="text-[var(--el-text-color-secondary)]"
            >{{ row.task.protocol }} {{ row.task.host }}:{{ row.task.port }}</span
          ></template
        ></el-table-column
      >
      <el-table-column label="状态" min-width="130"
        ><template #default="{ row }"
          ><el-tag :type="tagType(row)">{{ statusText(row) }}</el-tag>
          <div v-if="row.task.failureCategory" class="text-xs mt-4px">
            {{ failureText(row.task.failureCategory) }}
          </div></template
        ></el-table-column
      >
      <el-table-column
        prop="createdAt"
        label="发起时间"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="下发命令" min-width="200"
        ><template #default="{ row }"
          ><div
            v-if="row.commandText"
            class="whitespace-pre-wrap break-all max-h-120px overflow-auto"
            >{{ row.commandText }}</div
          >
          <span v-else class="text-[var(--el-text-color-secondary)]"
            >历史命令请查看日志</span
          ></template
        ></el-table-column
      >
      <el-table-column label="操作" width="285"
        ><template #default="{ row }">
          <el-button
            v-if="row.task.fileVersionId"
            link
            type="primary"
            v-hasPermi="['pms:file:download']"
            @click="viewLog(row)"
            >查看日志</el-button
          >
          <el-button
            v-if="row.task.fileVersionId"
            link
            type="primary"
            v-hasPermi="['pms:file:download']"
            @click="download(row)"
            >下载日志</el-button
          >
          <el-button
            v-if="row.task.status === 'RESULT_AVAILABLE' && !row.consumedResultVersion"
            link
            type="success"
            :disabled="busy || !canOperate"
            @click="consume(row)"
            >重试回传</el-button
          >
          <el-button
            v-if="isRunning(row)"
            link
            type="warning"
            :disabled="busy || !canOperate"
            @click="cancel(row)"
            >请求取消</el-button
          >
          <el-button
            v-if="!isRunning(row) && canDispatch"
            link
            type="primary"
            :disabled="busy || submissionUncertain"
            @click="retry(row)"
            >重新执行</el-button
          >
          <span v-if="row.consumedResultVersion" class="text-[var(--el-color-success)]"
            >已回传业务</span
          >
        </template></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="pageNo"
      :page-size="10"
      :total="total"
      layout="total, prev, pager, next"
      class="mt-16px"
      @current-change="refresh()"
    />
    <div v-if="semanticExecutionId" class="mt-16px">
      <div class="flex items-center justify-between mb-8px"
        ><strong>结构化解析结果</strong
        ><el-button link type="primary" :loading="semanticLoading" @click="loadSemantic(semanticExecutionId)"
          >刷新解析</el-button
        ></div
      >
      <el-alert
        v-if="semanticError && !semanticRows.length && !semanticLoading"
        title="解析结果查询失败，请点击“刷新解析”重试。"
        type="warning"
        :closable="false"
        class="mb-8px"
      />
      <el-alert
        v-else-if="!semanticRows.length && !semanticLoading"
        title="本次下发没有结构化解析记录：可能未启用结构化解析、无已激活解析版本，或该任务不适用解析。"
        type="info"
        :closable="false"
        class="mb-8px"
      />
      <el-table v-else v-loading="semanticLoading" :data="semanticRows" size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="px-24px">
              <p
                v-if="!observationRows(row).length"
                class="text-[var(--el-text-color-secondary)]"
                >暂无命令块解析明细</p
              >
              <ul v-else class="list-none p-0 m-0 flex flex-col gap-6px">
                <li
                  v-for="(obs, index) in observationRows(row)"
                  :key="index"
                  class="flex items-center gap-8px flex-wrap"
                >
                  <code class="max-w-420px truncate" :title="semanticCommand(row, obs)">{{
                    semanticCommand(row, obs)
                  }}</code>
                  <el-tag size="small" :type="obsType(obs.status)">{{ obs.status }}</el-tag>
                  <span
                    v-if="obs.confidence != null"
                    class="text-xs text-[var(--el-text-color-secondary)]"
                    >置信 {{ Math.round(obs.confidence * 100) }}%</span
                  >
                  <span
                    v-for="warning in obs.warnings || []"
                    :key="warning"
                    class="text-xs text-[var(--el-color-warning)]"
                    >{{ warning }}</span
                  >
                </li>
              </ul>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标" width="80" />
        <el-table-column label="解析状态" min-width="180">
          <template #default="{ row }">
            <el-tag size="small" :type="semanticStateType(row.state)">{{
              semanticStateText(row.state)
            }}</el-tag>
            <div v-if="row.waitReason" class="text-xs text-[var(--el-color-danger)] mt-4px">
              {{ row.waitReason }}
            </div>
          </template></el-table-column
        >
        <el-table-column label="解析版本" min-width="120"
          ><template #default="{ row }">{{ row.coordinate?.releaseVersion || '—' }}</template
        ></el-table-column>
        <el-table-column label="命令块" min-width="120"
          ><template #default="{ row }">{{ observationSummary(row) }}</template
        ></el-table-column>
      </el-table>
    </div>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </Dialog>
  <Dialog v-model="logVisible" title="设备执行日志" width="min(1080px, 96vw)" @closed="clearLog">
    <el-alert
      v-if="logRejected"
      title="设备返回命令错误，请核对命令；收到日志不代表命令执行成功。"
      type="error"
      :closable="false"
      class="mb-12px"
    />
    <el-input v-loading="logLoading" :model-value="logText" type="textarea" :rows="22" readonly />
    <template #footer><el-button @click="logVisible = false">关闭</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { isAxiosError } from 'axios'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import { dateFormatter } from '@/utils/formatTime'
import { generateUUID } from '@/utils'
import * as Api from '@/api/pms/platform/deviceCollection'
import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
const props = defineProps<{ entry: Api.Entry }>()
const emit = defineEmits<{ closed: [] }>()
const message = useMessage()
const visible = ref(false)
const busy = ref(false)
const loading = ref(false)
const source = ref<Api.Source>()
const objectId = ref<Api.Id>(0)
const templates = ref<Api.Template[]>([])
const connections = ref<Api.Connection[]>([])
const commandMode = ref('template')
const credentialMode = ref('temporary')
const formRef = ref()
const form = reactive({
  host: '',
  port: 22,
  protocol: 'SSH',
  username: '',
  password: '',
  commands: '',
  deviceId: undefined as Api.Id | undefined,
  templateId: undefined as Api.Id | undefined,
  credentialId: undefined as Api.Id | undefined,
  retryOfId: undefined as Api.Id | undefined
})
const requestKey = ref('')
const trackedRequestKey = ref('')
const submissionUncertain = ref(false)
const autoRefresh = ref(true)
const rows = ref<Api.Execution[]>([])
const hasPendingTask = computed(() => !!trackedRequestKey.value || rows.value.some(isRunning))
const total = ref(0)
const pageNo = ref(1)
const semanticRows = ref<Api.SemanticResult[]>([])
const semanticExecutionId = ref<Api.Id>()
const semanticLoading = ref(false)
const semanticError = ref(false)
const semanticCommandLines = ref<string[]>([])
let semanticGeneration = 0
const observationRows = (row: Api.SemanticResult) => row.result?.semanticResult?.observations || []
const observationSummary = (row: Api.SemanticResult) => {
  const rows = observationRows(row)
  if (!rows.length) return '—'
  return `${rows.filter((item) => item.status === 'OBSERVED').length}/${rows.length} 命中`
}
const semanticCommand = (row: Api.SemanticResult, obs: Api.SemanticObservation) =>
  semanticCommandLines.value[obs.commandIndex - 1] || `命令 #${obs.commandIndex}`
const semanticStateText = (state: string) =>
  ({ SUCCEEDED: '解析成功', FAILED: '解析失败', CANCELLED: '已取消' })[state] ?? '解析中'
const semanticStateType = (state: string): 'success' | 'danger' | 'warning' | 'info' =>
  state === 'SUCCEEDED' ? 'success' : state === 'FAILED' ? 'danger' : state === 'CANCELLED' ? 'info' : 'warning'
const obsType = (status: string): 'success' | 'danger' | 'warning' | 'info' =>
  status === 'OBSERVED'
    ? 'success'
    : ['EXECUTION_FAILED', 'SOURCE_CORRUPTED'].includes(status)
      ? 'danger'
      : ['UNPARSED', 'NO_DATA', 'PARTIAL'].includes(status)
        ? 'warning'
        : 'info'
const stopSemantic = () => {
  semanticGeneration++
  semanticRows.value = []
  semanticExecutionId.value = undefined
  semanticError.value = false
  semanticCommandLines.value = []
}
const loadSemantic = async (executionId: Api.Id, attempts = 0) => {
  const generation = ++semanticGeneration
  semanticLoading.value = attempts === 0
  semanticError.value = false
  try {
    const data = await Api.semanticResults(props.entry, objectId.value, executionId)
    if (generation !== semanticGeneration || !visible.value) return
    semanticRows.value = data
    // 采集终态时解析任务可能尚未挂接；空结果短暂重试后再判定为无解析记录。
    const parsing = data.some((item) => !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(item.state))
    if ((parsing || !data.length) && visible.value && autoRefresh.value && attempts < 5) {
      setTimeout(() => {
        if (generation === semanticGeneration && visible.value) void loadSemantic(executionId, attempts + 1)
      }, 2000)
    }
  } catch {
    if (generation === semanticGeneration) semanticError.value = true
  } finally {
    if (generation === semanticGeneration) semanticLoading.value = false
  }
}
const logVisible = ref(false)
const logLoading = ref(false)
const logText = ref('')
const logRejected = ref(false)
let logGeneration = 0
const clearLog = () => {
  logGeneration++
  logText.value = ''
  logLoading.value = false
}
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let refreshGeneration = 0
const stopRefresh = () => {
  clearTimeout(refreshTimer)
  refreshTimer = undefined
}
const canDispatch = computed(() => !!source.value?.canExecute)
const canOperate = computed(() =>
  checkPermi([
    props.entry === 'center'
      ? 'pms:device-collection:execute'
      : props.entry === 'configuration'
        ? 'pms:imp-configuration:update'
        : 'pms:imp-joint-test:update'
  ])
)
const selectedTemplate = computed(() => templates.value.find((t) => t.id === form.templateId))
const selectedConnection = computed(() => connections.value.find((c) => c.id === form.credentialId))
const rules = computed(() =>
  Object.fromEntries(
    [
      'protocol',
      ...(props.entry === 'center' ? ['deviceId'] : []),
      ...(commandMode.value === 'template' ? ['templateId'] : ['commands']),
      ...(credentialMode.value === 'saved'
        ? ['credentialId']
        : ['host', 'port', 'username', 'password'])
    ].map((key) => [key, [{ required: true, message: '请填写此项', trigger: 'change' }]])
  )
)
let choiceGeneration = 0
const loadChoices = async () => {
  const generation = ++choiceGeneration
  if (!source.value || !visible.value) return
  const list = checkPermi(['pms:collection-template:use'])
    ? await Api.templates({
        purpose: props.entry === 'center' ? undefined : props.entry,
        protocol: form.protocol,
        publishedOnly: true
      })
    : []
  if (generation !== choiceGeneration) return
  templates.value = list
  if (!list.some((r) => r.id === form.templateId)) form.templateId = undefined
  await loadConnections()
}
let connectionGeneration = 0
const loadConnections = async () => {
  const generation = ++connectionGeneration
  if (!source.value || !visible.value) return
  const list =
    checkPermi(['pms:device-credential:use']) && form.deviceId
      ? await Api.usableConnections(
          source.value.projectId,
          form.deviceId,
          form.protocol,
          commandMode.value === 'template' ? form.templateId : undefined
        )
      : []
  if (generation !== connectionGeneration) return
  connections.value = list
  if (!list.some((r) => r.id === form.credentialId)) form.credentialId = undefined
}
watch(
  () => form.protocol,
  () => {
    void loadChoices().catch(() => {})
  }
)
watch(
  () => [form.deviceId, form.templateId, commandMode.value],
  () => {
    void loadConnections().catch(() => {})
  }
)
watch(credentialMode, () => {
  // 切换认证方式只重置保存连接选择；本次密码在当前窗口内保留，避免重复输入。
  form.credentialId = undefined
})
const clearSecret = () => {
  form.password = ''
  form.commands = ''
}
const onClosed = () => {
  clearSecret()
  emit('closed')
}
watch(
  () => [
    form.host,
    form.port,
    form.protocol,
    form.username,
    form.commands,
    form.templateId,
    form.credentialId,
    form.deviceId,
    commandMode.value,
    credentialMode.value,
    form.retryOfId
  ],
  () => {
    requestKey.value = ''
  }
)
watch(visible, (value) => {
  if (!value) {
    choiceGeneration++
    connectionGeneration++
    clearSecret()
    stopRefresh()
    refreshGeneration++
    stopSemantic()
    logVisible.value = false
    clearLog()
  }
})
onBeforeUnmount(() => {
  clearSecret()
  stopRefresh()
  refreshGeneration++
  stopSemantic()
  logVisible.value = false
  clearLog()
})
const open = async (id: Api.Id) => {
  source.value = await Api.context(props.entry, id)
  objectId.value = id
  commandMode.value = source.value.manualAllowed ? 'manual' : 'template'
  credentialMode.value = 'temporary'
  Object.assign(form, {
    host: '',
    port: 22,
    protocol: 'SSH',
    username: '',
    password: '',
    commands: '',
    deviceId: source.value.deviceId,
    templateId: undefined,
    credentialId: undefined,
    retryOfId: undefined
  })
  requestKey.value = ''
  trackedRequestKey.value = ''
  submissionUncertain.value = false
  stopSemantic()
  autoRefresh.value = true
  pageNo.value = 1
  rows.value = []
  total.value = 0
  visible.value = true
  await nextTick()
  await Promise.all([loadChoices(), refresh()])
}
const refresh = async (silent = false) => {
  if (!objectId.value) return
  stopRefresh()
  const generation = ++refreshGeneration
  const sourceId = objectId.value
  if (!silent) loading.value = true
  try {
    const data = await Api.page(props.entry, sourceId, pageNo.value)
    const tracked = trackedRequestKey.value
      ? await Api.findByRequestKey(props.entry, sourceId, trackedRequestKey.value)
      : null
    if (generation !== refreshGeneration || !visible.value) return
    rows.value = data.list
    total.value = data.total
    if (tracked) {
      submissionUncertain.value = false
      requestKey.value = ''
      if (pageNo.value === 1) {
        rows.value = [tracked, ...data.list.filter((row) => row.id !== tracked.id)].slice(0, 10)
        total.value = Math.max(data.total, rows.value.length)
      }
      if (!isRunning(tracked)) {
        trackedRequestKey.value = ''
        // 任务到达终态后立即同步拉取本次下发的结构化解析结果与报错。
        semanticExecutionId.value = tracked.id
        semanticCommandLines.value = (tracked.commandText || '').split('\n').filter(Boolean)
        void loadSemantic(tracked.id)
      }
    }
  } finally {
    if (generation === refreshGeneration) {
      loading.value = false
      if (visible.value && autoRefresh.value && hasPendingTask.value) {
        refreshTimer = setTimeout(() => {
          void refresh(true).catch(() => {
            // A transient query failure must not stop tracking an accepted/uncertain execution.
          })
        }, 2000)
      }
    }
  }
}
const toggleAutoRefresh = () => {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    void refresh(true).catch(() => {
      /* scheduled polling continues after a transient query failure */
    })
  } else {
    stopRefresh()
  }
}
const submit = async () => {
  if (submissionUncertain.value) return
  await formRef.value.validate()
  const target = credentialMode.value === 'saved' ? selectedConnection.value : form
  await message.confirm(`确认向 ${target?.host}:${target?.port} 执行当前命令？`)
  busy.value = true
  autoRefresh.value = true
  try {
    requestKey.value ||= generateUUID()
    trackedRequestKey.value = requestKey.value
    const result = await Api.submit(props.entry, objectId.value, {
      protocol: form.protocol,
      deviceId: form.deviceId,
      templateId: commandMode.value === 'template' ? form.templateId : undefined,
      commands: commandMode.value === 'manual' ? form.commands : undefined,
      ...(credentialMode.value === 'saved'
        ? { credentialId: form.credentialId }
        : { host: form.host, port: form.port, username: form.username, password: form.password }),
      requestKey: requestKey.value,
      expectedVersion: source.value!.version,
      retryOfId: form.retryOfId
    })
    message.success(`执行记录已保存：${statusText(result)}`)
    requestKey.value = ''
    pageNo.value = 1
    await refresh().catch(() => {
      /* keep tracking the accepted task after a query failure */
    })
  } catch (error) {
    // The shared client displays the error; never let an Axios request containing credentials reach Vue's error logger.
    const outcomeUnknown = isAxiosError(error) && (!error.response || error.response.status >= 500)
    if (outcomeUnknown) {
      submissionUncertain.value = true
      message.warning('同步响应未取得，已转为异步查询；不会重复执行命令')
      pageNo.value = 1
      void refresh().catch(() => {
        /* scheduled polling continues */
      })
    } else {
      trackedRequestKey.value = ''
      message.error('下发未完成，请查看提示及执行历史；请核对后重新下发')
    }
  } finally {
    // 本次密码保留在当前窗口，便于连续下发；关闭对话框时才随 clearSecret 清空。
    busy.value = false
  }
}
const consume = async (row: Api.Execution) => {
  busy.value = true
  try {
    await Api.consume(props.entry, objectId.value, row.id)
    message.success('日志已回传到当前业务记录')
    await refresh()
  } finally {
    busy.value = false
  }
}
const cancel = async (row: Api.Execution) => {
  await message.confirm('确认请求停止该任务？最终状态以设备执行结果为准。')
  busy.value = true
  try {
    await Api.cancel(props.entry, objectId.value, row.id)
    message.success('取消请求已受理，请刷新查看最终状态')
    await refresh()
  } finally {
    busy.value = false
  }
}
const download = async (row: Api.Execution) => {
  const url = await Api.download(props.entry, objectId.value, row.id)
  const link = document.createElement('a')
  link.href = url
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.click()
}
const viewLog = async (row: Api.Execution) => {
  const generation = ++logGeneration
  logText.value = ''
  logRejected.value = row.task.failureCategory === 'COMMAND_REJECTED'
  logVisible.value = true
  logLoading.value = true
  try {
    const url = await Api.download(props.entry, objectId.value, row.id)
    const response = await fetch(url, { cache: 'no-store', referrerPolicy: 'no-referrer' })
    if (!response.ok) throw new Error('LOG_READ_FAILED')
    const raw = await response.text()
    if (generation !== logGeneration || !logVisible.value || !visible.value) return
    const log = JSON.parse(raw)
    const commands =
      row.commandText ||
      (Array.isArray(log.commandBlocks)
        ? log.commandBlocks
            .map((block: { commandText?: string }) => block.commandText)
            .filter(Boolean)
            .join('\n')
        : '')
    logText.value = [
      `执行状态：${log.status ?? row.task.externalStatus ?? row.task.status}`,
      log.outcome ? `执行结果：${log.outcome}` : '',
      commands ? `\n下发命令：\n${commands}` : '',
      '',
      '设备输出：',
      typeof log.stdout === 'string' ? log.stdout : '',
      log.stderr ? `\n错误输出：\n${log.stderr}` : ''
    ].join('\n')
    logRejected.value ||= /(^|\n)%\s*Unknown command\.?\s*(\r?\n|$)/i.test(log.stdout ?? '')
  } catch {
    if (generation === logGeneration) {
      message.error('日志读取失败，请刷新状态后重试')
      logVisible.value = false
    }
  } finally {
    if (generation === logGeneration) logLoading.value = false
  }
}
const failureText = (category: string) =>
  ({
    COMMAND_REJECTED: '设备拒绝命令，请查看日志',
    AUTHENTICATION_FAILED: '设备认证失败，请核对凭证',
    AUTH_FAILED: '设备认证失败，请核对凭证',
    UNREACHABLE: '设备不可达，请核对地址和端口',
    EXECUTION_TIMEOUT: '执行超时，请查看日志',
    EXECUTION_FAILED: '执行失败，请查看设备回显',
    CONNECT_TIMEOUT: '连接超时，请核对网络',
    HOST_KEY_MISMATCH: '设备主机密钥不匹配',
    PROMPT_NOT_FOUND: '未识别设备提示符，请查看日志',
    PROTOCOL_DISABLED: '设备协议尚未启用',
    CONNECTION_CLOSED: '设备连接已关闭',
    CONNECTION_BUSY_TIMEOUT: '等待设备连接超时',
    OUTPUT_TRUNCATED: '输出超限，日志不完整',
    CLIENT_DISPATCH_ERROR: '下发失败，请核对服务及请求配置',
    EXPLICIT_REJECTION: 'DAC 拒绝下发请求',
    NETWORK_UNKNOWN: '下发结果待确认，请勿重复执行',
    LOCAL_ACK_FAILED: '已下发，正在恢复本地确认',
    CANCELLED_BEFORE_DISPATCH: '已取消，命令未下发',
    CREDENTIAL_AUTHORIZATION_REVOKED: '保存连接授权已失效，命令未下发'
  })[category] ?? category
const isRunning = (row: Api.Execution) =>
  ['CREATED', 'AUTHORIZED', 'DISPATCHED', 'EXECUTING', 'CALLBACK_PROCESSING'].includes(
    row.task.status
  )
const statusText = (row: Api.Execution) =>
  ['RECONCILING', 'DISPATCHING'].includes(row.task.technicalStage)
    ? '结果确认中'
    : ({
        CREATED: '已创建',
        AUTHORIZED: '已授权',
        DISPATCHED: '已下发',
        EXECUTING: '执行中',
        CALLBACK_PROCESSING: '接收日志中',
        RESULT_AVAILABLE: '等待回传业务',
        COMPLETED: row.task.completionMode === 'CALLBACK_TERMINAL' ? '执行完成' : '日志已回传',
        FAILED: '执行失败',
        CANCELLED: '已取消',
        SECURITY_EXCEPTION: '日志已隔离'
      }[row.task.status] ?? row.task.status)
const tagType = (row: Api.Execution): 'success' | 'danger' | 'warning' | 'info' =>
  row.task.status === 'COMPLETED'
    ? 'success'
    : ['FAILED', 'SECURITY_EXCEPTION'].includes(row.task.status)
      ? 'danger'
      : isRunning(row)
        ? 'warning'
        : 'info'
const retry = async (row: Api.Execution) => {
  source.value = await Api.context(props.entry, objectId.value)
  if (!source.value.canExecute) return
  form.deviceId = props.entry === 'center' ? row.task.deviceId : source.value.deviceId
  form.protocol = row.task.protocol
  form.host = row.task.host
  form.port = row.task.port
  form.retryOfId = row.id
  // Let protocol watchers start before awaiting the authoritative choice reload.
  await nextTick()
  await loadChoices()
  const template = templates.value.find(
    (t) => String(t.id) === row.task.templateId && String(t.revision) === row.task.templateVersion
  )
  commandMode.value = template || !source.value.manualAllowed ? 'template' : 'manual'
  form.templateId = template?.id
  form.commands = row.commandText || ''
  credentialMode.value = 'temporary'
  requestKey.value = ''
  message.info('已填入原任务内容，请核对模板与凭证后下发')
}
defineExpose({ open })
</script>
