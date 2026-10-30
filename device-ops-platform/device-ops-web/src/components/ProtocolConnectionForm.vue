<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import {
  deleteSavedConnection,
  getSavedConnection,
  listSavedConnections,
  verifyAndCreateSavedConnection,
  verifyAndReplaceSavedConnection,
  type SavedConnection,
  type SavedConnectionWriteRequest
} from '@/api/saved-connections'
import { testConnection, type ConnectionTestResult } from '@/api/device-ops'
import { listSerialPorts } from '@/api/serial-ports'
import { loadRuntimeConfig } from '@/config/runtime'
import {
  DEFAULT_SERIAL_PARAMS,
  DEFAULT_SERIAL_PROMPTS,
  SERIAL_BAUD_RATES,
  SERIAL_FLOW_CONTROL_OPTIONS,
  SERIAL_PARITY_OPTIONS,
  SERIAL_STOP_BIT_OPTIONS,
  validateSerialConnection,
  type SerialParams,
  type SerialPrompts
} from '@/utils/serial-connection'
import type { RecentConnection } from '@/stores/recent-connections'
import type {
  ConnectionProtocol,
  ConnectionRequest,
  ExecutionMode,
  SavedConnectionWriteConnection,
  TransientConnectionRequest
} from '@/types/collection'

const emit = defineEmits<{ tested: [connection: RecentConnection] }>()
const props = withDefaults(defineProps<{ credentialNamespace?: string }>(), {
  credentialNamespace: 'standalone'
})

const protocol = ref<ConnectionProtocol>('SSH2')
const telnetEnabled = ref(false)
const serialEnabled = ref(false)
const serialPortNames = ref<string[]>([])
const serialComPort = ref('')
const serialParams = reactive<SerialParams>({ ...DEFAULT_SERIAL_PARAMS })
const serialPrompts = reactive<SerialPrompts>({ ...DEFAULT_SERIAL_PROMPTS })
const authenticationType = ref<'PASSWORD' | 'PRIVATE_KEY'>('PASSWORD')
const executionMode = ref<ExecutionMode>('SHELL')
const password = ref('')
const privateKey = ref('')
const passphrase = ref('')
const connectionMode = ref<'TRANSIENT' | 'SAVED'>('TRANSIENT')
const displayName = ref('')
const description = ref('')
const selectedSavedConnectionId = ref('')
const activeSavedConnection = ref<SavedConnection>()
const savedConnections = ref<SavedConnection[]>([])
const savedConnectionsLoading = ref(false)
const savedConnectionsError = ref('')
const savedConnectionSaving = ref(false)
const savedConnectionMessage = ref('')
const savedBaseline = ref('')
const connectTimeoutSeconds = ref(15)
const testing = ref(false)
const testResult = ref<ConnectionTestResult>()
const testError = ref('')
let testController: AbortController | undefined
let savedSelectionRequest = 0
let savedSelectionController: AbortController | undefined
let savedListRequest = 0
let savedListController: AbortController | undefined
let suppressProtocolPortDefault = false

const endpoint = reactive({ host: '', port: 22, username: '', hostKeyFingerprint: '' })
const telnetPrompts = reactive({
  login: '(?i)(login|username)\\s*:\\s*$',
  password: '(?i)password\\s*:\\s*$',
  command: '[>#\\$]\\s*$',
  lineEnding: 'AUTO' as 'AUTO' | 'CRLF' | 'CR' | 'LF'
})

const effectiveAuthenticationType = computed<'PASSWORD' | 'PRIVATE_KEY'>(() =>
  protocol.value === 'SSH2' ? authenticationType.value : 'PASSWORD'
)
const hasSensitiveInput = computed(
  () => Boolean(password.value || privateKey.value || passphrase.value)
)
const savedConnectionDirty = computed(
  () =>
    Boolean(activeSavedConnection.value) &&
    (savedBaseline.value !== savedFormSignature() || hasSensitiveInput.value)
)
const savedConnectionsByProtocol = computed(() =>
  (['SSH2', 'TELNET', 'SERIAL'] as const)
    .map((savedProtocol) => ({
      protocol: savedProtocol,
      label: savedProtocol === 'SSH2' ? 'SSH2 连接' : savedProtocol === 'TELNET' ? 'Telnet 连接' : '串口连接',
      connections: savedConnections.value.filter(
        (connection) => connection.connection.protocol === savedProtocol
      )
    }))
    .filter((group) => group.connections.length > 0)
)
const retainingSavedCredential = computed(
  () => connectionMode.value === 'SAVED' && Boolean(activeSavedConnection.value)
)
watch(protocol, (current, previous) => {
  if (!suppressProtocolPortDefault) {
    if (current === 'TELNET' && previous === 'SSH2' && endpoint.port === 22) endpoint.port = 23
    if (current === 'SSH2' && previous === 'TELNET' && endpoint.port === 23) endpoint.port = 22
    if (current === 'SERIAL') endpoint.port = 0
  }
  if (current !== 'SSH2') executionMode.value = 'SHELL'
  clearTestState()
}, { flush: 'sync' })

watch(
  () => props.credentialNamespace,
  () => {
    invalidateSavedSelection()
    selectedSavedConnectionId.value = ''
    void loadSavedConnections()
  },
  { flush: 'sync' }
)

watch(selectedSavedConnectionId, (id) => {
  const request = invalidateSavedSelection()
  if (id) void selectSavedConnection(id, request)
}, { flush: 'sync' })

function requireValue(value: string, message: string): string {
  if (!value.trim()) throw new Error(message)
  return value.trim()
}

function safeErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && !('response' in error)) return error.message
  return fallback
}

function clearTestState() {
  testResult.value = undefined
  testError.value = ''
}

function clearActiveSavedConnection() {
  activeSavedConnection.value = undefined
  savedBaseline.value = ''
  savedConnectionMessage.value = ''
  clearCredentials()
  clearTestState()
}

function invalidateSavedSelection(): number {
  savedSelectionRequest += 1
  savedSelectionController?.abort()
  savedSelectionController = undefined
  clearActiveSavedConnection()
  return savedSelectionRequest
}

function setLoadedProtocolAndPort(value: ConnectionProtocol, port: number) {
  suppressProtocolPortDefault = true
  protocol.value = value
  suppressProtocolPortDefault = false
  endpoint.port = port
}

function directConnectionFields() {
  if (protocol.value === 'TELNET' && !telnetEnabled.value) {
    throw new Error('当前部署未启用 Telnet。')
  }
  if (protocol.value === 'SERIAL' && !serialEnabled.value) {
    throw new Error('当前部署未启用串口。')
  }
  if (protocol.value === 'SERIAL') {
    const host = serialComPort.value.trim() || endpoint.host.trim()
    const serialErrors = validateSerialConnection({
      host,
      port: 0,
      serialParams: { ...serialParams },
      serialPrompts: { ...serialPrompts }
    })
    if (serialErrors.length) throw new Error(serialErrors[0])
    return {
      host,
      port: 0,
      username: requireValue(endpoint.username, '请输入用户名。'),
      connectTimeoutSeconds: connectTimeoutSeconds.value
    }
  }
  return {
    host: requireValue(endpoint.host, '请输入 IP 或主机名。'),
    port: endpoint.port,
    username: requireValue(endpoint.username, '请输入用户名。'),
    connectTimeoutSeconds: connectTimeoutSeconds.value
  }
}

function currentTelnetPrompts() {
  return {
    login: requireValue(telnetPrompts.login, '请输入登录名提示符。'),
    password: requireValue(telnetPrompts.password, '请输入密码提示符。'),
    command: requireValue(telnetPrompts.command, '请输入命令提示符。'),
    lineEnding: telnetPrompts.lineEnding
  }
}

function currentSerialParams(): SerialParams {
  return { ...serialParams }
}

function currentSerialPrompts(): SerialPrompts {
  return {
    login: requireValue(serialPrompts.login, '请输入登录名提示符。'),
    password: requireValue(serialPrompts.password, '请输入密码提示符。'),
    command: requireValue(serialPrompts.command, '请输入命令提示符。'),
    lineEnding: serialPrompts.lineEnding
  }
}

function optionalHostKeyFingerprint() {
  const fingerprint = endpoint.hostKeyFingerprint.trim()
  return fingerprint ? { hostKeyFingerprint: fingerprint } : {}
}

function buildDirectConnection(): TransientConnectionRequest {
  const fields = directConnectionFields()
  if (protocol.value === 'TELNET') {
    return {
      ...fields,
      protocol: 'TELNET',
      authenticationType: 'PASSWORD',
      executionMode: 'SHELL',
      telnetPrompts: currentTelnetPrompts(),
      password: requireValue(password.value, '请输入本次连接使用的密码。')
    }
  }
  if (protocol.value === 'SERIAL') {
    return {
      ...fields,
      protocol: 'SERIAL',
      authenticationType: 'PASSWORD',
      executionMode: 'SHELL',
      serialParams: currentSerialParams(),
      serialPrompts: currentSerialPrompts(),
      password: requireValue(password.value, '请输入本次连接使用的密码。')
    }
  }
  if (authenticationType.value === 'PASSWORD') {
    return {
      ...fields,
      protocol: 'SSH2',
      authenticationType: 'PASSWORD',
      executionMode: executionMode.value,
      ...optionalHostKeyFingerprint(),
      password: requireValue(password.value, '请输入本次连接使用的密码。')
    }
  }
  return {
    ...fields,
    protocol: 'SSH2',
    authenticationType: 'PRIVATE_KEY',
    executionMode: executionMode.value,
    ...optionalHostKeyFingerprint(),
    privateKey: requireValue(privateKey.value, '请输入本次连接使用的私钥。'),
    ...(passphrase.value ? { passphrase: passphrase.value } : {})
  }
}

function buildSavedConnectionWrite(): SavedConnectionWriteConnection {
  const fields = directConnectionFields()
  const credentialRequired = !activeSavedConnection.value
  if (protocol.value === 'TELNET') {
    return {
      ...fields,
      protocol: 'TELNET',
      authenticationType: 'PASSWORD',
      executionMode: 'SHELL',
      telnetPrompts: currentTelnetPrompts(),
      ...(password.value || credentialRequired
        ? { password: requireValue(password.value, '请输入本次连接使用的密码。') }
        : {})
    }
  }
  if (protocol.value === 'SERIAL') {
    return {
      ...fields,
      protocol: 'SERIAL',
      authenticationType: 'PASSWORD',
      executionMode: 'SHELL',
      serialParams: currentSerialParams(),
      serialPrompts: currentSerialPrompts(),
      ...(password.value || credentialRequired
        ? { password: requireValue(password.value, '请输入本次连接使用的密码。') }
        : {})
    }
  }
  if (authenticationType.value === 'PASSWORD') {
    return {
      ...fields,
      protocol: 'SSH2',
      authenticationType: 'PASSWORD',
      executionMode: executionMode.value,
      ...optionalHostKeyFingerprint(),
      ...(password.value || credentialRequired
        ? { password: requireValue(password.value, '请输入本次连接使用的密码。') }
        : {})
    }
  }
  return {
    ...fields,
    protocol: 'SSH2',
    authenticationType: 'PRIVATE_KEY',
    executionMode: executionMode.value,
    ...optionalHostKeyFingerprint(),
    ...(privateKey.value || credentialRequired
      ? { privateKey: requireValue(privateKey.value, '请输入本次连接使用的私钥。') }
      : {}),
    ...(passphrase.value ? { passphrase: passphrase.value } : {})
  }
}

function buildConnection(): ConnectionRequest {
  if (connectionMode.value !== 'SAVED') return buildDirectConnection()
  if (!activeSavedConnection.value) {
    throw new Error('请先选择或验证并保存连接，再提交采集。')
  }
  if (savedConnectionDirty.value) {
    throw new Error('已保存连接存在未验证修改，请先“验证并保存连接”或切换为临时连接。')
  }
  return {
    savedConnectionId: activeSavedConnection.value.id,
    credentialNamespace: props.credentialNamespace
  }
}

function savedFormSignature(): string {
  return JSON.stringify({
    displayName: displayName.value.trim(),
    description: description.value.trim(),
    protocol: protocol.value,
    host: protocol.value === 'SERIAL' ? serialComPort.value.trim() || endpoint.host.trim() : endpoint.host.trim(),
    port: endpoint.port,
    username: endpoint.username.trim(),
    authenticationType: effectiveAuthenticationType.value,
    executionMode: protocol.value === 'TELNET' || protocol.value === 'SERIAL' ? 'SHELL' : executionMode.value,
    connectTimeoutSeconds: connectTimeoutSeconds.value,
    hostKeyFingerprint: endpoint.hostKeyFingerprint.trim(),
    telnetPrompts: protocol.value === 'TELNET' ? { ...telnetPrompts } : undefined,
    serialParams: protocol.value === 'SERIAL' ? { ...serialParams } : undefined,
    serialPrompts: protocol.value === 'SERIAL' ? { ...serialPrompts } : undefined
  })
}

function savedConnectionRequest(): SavedConnectionWriteRequest {
  return {
    namespace: props.credentialNamespace,
    displayName: requireValue(displayName.value, '请输入保存连接名称。'),
    description: description.value.trim() || null,
    connection: buildSavedConnectionWrite()
  }
}

function applySavedConnection(saved: SavedConnection) {
  activeSavedConnection.value = saved
  selectedSavedConnectionId.value = saved.id
  displayName.value = saved.displayName
  description.value = saved.description ?? ''
  setLoadedProtocolAndPort(saved.connection.protocol, saved.connection.port)
  endpoint.host = saved.connection.host
  endpoint.username = saved.connection.username
  authenticationType.value = saved.connection.authenticationType
  executionMode.value = saved.connection.executionMode
  endpoint.hostKeyFingerprint = saved.connection.expectedHostKeyFingerprint ?? ''
  connectTimeoutSeconds.value = durationSeconds(saved.connection.connectTimeout)
  if (saved.connection.telnetPrompts) Object.assign(telnetPrompts, saved.connection.telnetPrompts)
  if (saved.connection.serialParams) Object.assign(serialParams, saved.connection.serialParams)
  if (saved.connection.serialPrompts) Object.assign(serialPrompts, saved.connection.serialPrompts)
  if (saved.connection.protocol === 'SERIAL') serialComPort.value = saved.connection.host
  clearCredentials()
  savedBaseline.value = savedFormSignature()
  clearTestState()
}

function durationSeconds(value: SavedConnection['connection']['connectTimeout']): number {
  if (typeof value === 'number' && value > 0) return value
  if (typeof value === 'string') {
    const seconds = /^PT(\d+)S$/.exec(value)
    if (seconds?.[1]) return Number(seconds[1])
  }
  return 15
}

async function loadSavedConnections() {
  savedListController?.abort()
  const controller = new AbortController()
  const request = ++savedListRequest
  const namespace = props.credentialNamespace
  savedListController = controller
  savedConnectionsLoading.value = true
  savedConnectionsError.value = ''
  try {
    const connections = await listSavedConnections(namespace, controller.signal)
    if (request !== savedListRequest || namespace !== props.credentialNamespace) return
    savedConnections.value = connections
  } catch {
    if (request !== savedListRequest || controller.signal.aborted) return
    savedConnections.value = []
    savedConnectionsError.value = '保存的连接暂时不可用；仍可使用临时连接。'
  } finally {
    if (request === savedListRequest) savedConnectionsLoading.value = false
  }
}

async function selectSavedConnection(id: string, request: number) {
  const namespace = props.credentialNamespace
  const controller = new AbortController()
  savedSelectionController = controller
  try {
    const saved = await getSavedConnection(id, namespace, controller.signal)
    if (
      request !== savedSelectionRequest ||
      id !== selectedSavedConnectionId.value ||
      namespace !== props.credentialNamespace
    ) return
    applySavedConnection(saved)
  } catch {
    if (request !== savedSelectionRequest || controller.signal.aborted) return
    selectedSavedConnectionId.value = ''
    savedConnectionMessage.value = '无法加载保存连接，请刷新列表后重试。'
  } finally {
    if (request === savedSelectionRequest) savedSelectionController = undefined
  }
}

function startNewSavedConnection() {
  invalidateSavedSelection()
  selectedSavedConnectionId.value = ''
  displayName.value = ''
  description.value = ''
  endpoint.host = ''
  endpoint.port = protocol.value === 'TELNET' ? 23 : protocol.value === 'SERIAL' ? 0 : 22
  if (protocol.value === 'SERIAL') {
    serialComPort.value = ''
    Object.assign(serialParams, DEFAULT_SERIAL_PARAMS)
    Object.assign(serialPrompts, DEFAULT_SERIAL_PROMPTS)
  }
  endpoint.username = ''
  endpoint.hostKeyFingerprint = ''
  clearCredentials()
  clearTestState()
  savedConnectionMessage.value = '新建保存连接需要验证成功后才会写入。'
}

async function verifyAndSaveConnection() {
  savedConnectionSaving.value = true
  savedConnectionMessage.value = ''
  clearTestState()
  try {
    const request = savedConnectionRequest()
    const result = activeSavedConnection.value
      ? await verifyAndReplaceSavedConnection(activeSavedConnection.value.id, {
          ...request,
          version: activeSavedConnection.value.version
        })
      : await verifyAndCreateSavedConnection(request)
    testResult.value = result.test
    if (!result.saved || !result.connection) {
      savedConnectionMessage.value = result.test.safeMessage || '连接验证未通过，未保存任何修改。'
      return
    }
    await loadSavedConnections()
    applySavedConnection(result.connection)
    savedConnectionMessage.value = '连接已验证并保存；凭据仅以“已保存凭据”状态保留。'
  } catch (error) {
    savedConnectionMessage.value = safeErrorMessage(error, '连接未保存，请检查输入后重试。')
  } finally {
    savedConnectionSaving.value = false
  }
}

async function removeActiveSavedConnection() {
  const saved = activeSavedConnection.value
  if (!saved) return
  if (!window.confirm(`删除保存连接“${saved.displayName}”后将不能再用它执行采集，确认删除吗？`)) return
  savedConnectionSaving.value = true
  savedConnectionMessage.value = ''
  try {
    await deleteSavedConnection(saved.id, props.credentialNamespace, saved.version)
    startNewSavedConnection()
    await loadSavedConnections()
    savedConnectionMessage.value = '保存连接已删除。'
  } catch {
    savedConnectionMessage.value = '删除保存连接失败；原记录仍保留。'
  } finally {
    savedConnectionSaving.value = false
  }
}

function describeRecent(deviceLabel?: string): RecentConnection {
  return {
    protocol: protocol.value,
    host: (protocol.value === 'SERIAL' ? serialComPort.value : endpoint.host).trim(),
    port: endpoint.port,
    username: endpoint.username.trim(),
    ...(protocol.value === 'SSH2' && endpoint.hostKeyFingerprint.trim()
      ? { hostKeyFingerprint: endpoint.hostKeyFingerprint.trim() }
      : {}),
    ...(deviceLabel ? { deviceLabel } : {}),
    usedAt: new Date().toISOString()
  }
}

function connectionLabel(): string {
  return (
    activeSavedConnection.value?.displayName ||
    (protocol.value === 'SERIAL' ? serialComPort.value : endpoint.host).trim()
  )
}

async function runTest() {
  testing.value = true
  clearTestState()
  try {
    const result = await testConnection(buildConnection(), (testController = new AbortController()).signal)
    testResult.value = result
    if (result.reachable) emit('tested', describeRecent())
  } catch (error) {
    if (!testController?.signal.aborted) {
      testError.value = safeErrorMessage(error, '连接测试未完成，请检查端点、协议开关和授权后重试。')
    }
  } finally {
    testController = undefined
    testing.value = false
  }
}

function clearCredentials() {
  password.value = ''
  privateKey.value = ''
  passphrase.value = ''
}

function applyRecent(item: RecentConnection) {
  connectionMode.value = 'TRANSIENT'
  invalidateSavedSelection()
  selectedSavedConnectionId.value = ''
  setLoadedProtocolAndPort(item.protocol, item.port)
  endpoint.host = item.host
  if (item.protocol === 'SERIAL') serialComPort.value = item.host
  endpoint.username = item.username
  endpoint.hostKeyFingerprint = item.hostKeyFingerprint ?? ''
  executionMode.value = 'SHELL'
  clearCredentials()
  clearTestState()
}

defineExpose({ applyRecent, buildConnection, clearCredentials, connectionLabel, describeRecent })

onMounted(async () => {
  const runtime = await loadRuntimeConfig()
  telnetEnabled.value = runtime.telnetEnabled
  serialEnabled.value = runtime.serialEnabled
  if (runtime.serialEnabled) {
    listSerialPorts()
      .then((names) => {
        serialPortNames.value = names
      })
      .catch(() => {
        serialPortNames.value = []
      })
  }
  await loadSavedConnections()
})
onBeforeUnmount(() => {
  testController?.abort()
  savedSelectionController?.abort()
  savedListController?.abort()
  clearCredentials()
})
</script>

<template>
  <section class="connection-toolbar" aria-label="连接方式">
    <div class="protocol-selector">
      <span>连接协议</span>
      <el-radio-group v-model="protocol" size="small" aria-label="连接协议">
        <el-radio-button value="SSH2">SSH2</el-radio-button>
        <el-radio-button value="TELNET" :disabled="!telnetEnabled">Telnet</el-radio-button>
        <el-radio-button value="SERIAL" :disabled="!serialEnabled">串口</el-radio-button>
      </el-radio-group>
      <small
        v-if="!telnetEnabled || !serialEnabled"
        class="connection-helper"
      >{{ [!telnetEnabled ? 'Telnet 未由当前部署启用' : '', !serialEnabled ? '串口未由当前部署启用' : ''].filter(Boolean).join('；') }}</small>
    </div>
    <el-radio-group v-model="connectionMode" size="small" aria-label="连接使用方式">
      <el-radio-button value="TRANSIENT">临时连接</el-radio-button>
      <el-radio-button value="SAVED">保存的连接</el-radio-button>
    </el-radio-group>
  </section>

  <el-alert
    v-if="protocol === 'TELNET'"
    title="Telnet 明文传输，仅限受控网络"
    type="warning"
    :closable="false"
    show-icon
  />
  <el-alert
    v-if="protocol === 'SERIAL'"
    title="串口明文传输，仅限受控网络"
    type="warning"
    :closable="false"
    show-icon
  />

  <section class="saved-connections" aria-labelledby="saved-connections-title">
    <div class="saved-connections__heading">
      <div>
        <strong id="saved-connections-title">凭据保存</strong>
        <small class="connection-helper">凭据加密存储，不回显</small>
      </div>
    </div>

    <template v-if="connectionMode === 'SAVED'">
      <div class="saved-connections__controls">
        <el-select
          v-model="selectedSavedConnectionId"
          :loading="savedConnectionsLoading"
          clearable
          placeholder="选择保存的连接"
          aria-label="保存的连接"
        >
          <el-option-group
            v-for="group in savedConnectionsByProtocol"
            :key="group.protocol"
            :label="group.label"
          >
            <el-option
              v-for="connection in group.connections"
              :key="connection.id"
              :label="connection.connection.protocol === 'SERIAL'
                ? `${connection.displayName} · ${connection.connection.username}@${connection.connection.host}`
                : `${connection.displayName} · ${connection.connection.username}@${connection.connection.host}:${connection.connection.port}`"
              :value="connection.id"
            />
          </el-option-group>
        </el-select>
        <el-button size="small" @click="startNewSavedConnection">新建保存连接</el-button>
        <el-button size="small" :loading="savedConnectionsLoading" @click="loadSavedConnections">刷新</el-button>
        <el-button
          size="small"
          type="danger"
          plain
          :disabled="!activeSavedConnection"
          :loading="savedConnectionSaving"
          @click="removeActiveSavedConnection"
        >
          删除
        </el-button>
      </div>
      <p v-if="savedConnectionsError" class="connection-helper saved-connections__hint" role="alert">
        {{ savedConnectionsError }}
      </p>
      <p v-else-if="!savedConnectionsLoading && !savedConnections.length" class="connection-helper saved-connections__hint" role="status">
        暂无保存的连接
      </p>
      <p v-if="activeSavedConnection" class="connection-helper saved-credential-state" role="status">
        {{ activeSavedConnection.credentialSaved ? '凭据已保存' : '状态异常，请重新保存' }}
      </p>
      <p v-if="savedConnectionDirty" class="connection-helper saved-connections__dirty" role="alert">
        有未验证修改，请先验证保存
      </p>
      <p v-if="savedConnectionMessage" class="connection-helper saved-connections__hint" role="status">
        {{ savedConnectionMessage }}
      </p>
    </template>
  </section>

  <section aria-label="基本连接">
    <el-divider content-position="left">基本连接</el-divider>
    <el-form label-position="top" class="connection-form connection-form--basic">
      <template v-if="connectionMode === 'SAVED'">
        <el-form-item label="保存连接名称">
          <el-input v-model="displayName" maxlength="200" autocomplete="off" />
        </el-form-item>
        <el-form-item label="说明（可选）">
          <el-input v-model="description" maxlength="2000" autocomplete="off" />
        </el-form-item>
      </template>
      <el-form-item :label="protocol === 'SERIAL' ? 'COM 口' : 'IP / 主机名'">
        <el-input
          v-if="protocol === 'SERIAL'"
          v-model="serialComPort"
          autocomplete="off"
          placeholder="COM3"
          aria-label="串口名称"
        />
        <el-input v-else v-model="endpoint.host" autocomplete="off" placeholder="192.0.2.10" />
        <el-select
          v-if="protocol === 'SERIAL' && serialPortNames.length"
          :model-value="serialComPort"
          class="connection-form__serial-select"
          aria-label="本机串口列表"
          placeholder="选择串口"
          @update:model-value="serialComPort = String($event)"
        >
          <el-option v-for="name in serialPortNames" :key="name" :label="name" :value="name" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="protocol !== 'SERIAL'" label="端口">
        <el-input-number v-model="endpoint.port" :min="1" :max="65535" controls-position="right" />
      </el-form-item>
    </el-form>
  </section>

  <section aria-label="认证信息">
    <el-divider content-position="left">认证信息</el-divider>
    <el-form label-position="top" class="connection-form connection-form--authentication">
      <el-form-item label="用户名">
        <el-input v-model="endpoint.username" autocomplete="off" />
      </el-form-item>
      <template v-if="protocol === 'SSH2'">
        <el-form-item label="认证方式">
          <el-radio-group v-model="authenticationType" size="small">
            <el-radio value="PASSWORD">密码</el-radio>
            <el-radio value="PRIVATE_KEY">私钥</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="authenticationType === 'PASSWORD'" :label="retainingSavedCredential ? '新密码（留空保留已保存凭据）' : '密码'" class="connection-form__wide">
          <el-input v-model="password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <template v-else>
          <el-form-item :label="retainingSavedCredential ? '新私钥（留空保留已保存凭据）' : '私钥'" class="connection-form__wide">
            <el-input v-model="privateKey" type="textarea" :rows="5" autocomplete="off" aria-label="私钥内容" />
          </el-form-item>
          <el-form-item label="私钥口令（可选）" class="connection-form__wide">
            <el-input v-model="passphrase" type="password" show-password autocomplete="new-password" />
          </el-form-item>
        </template>
      </template>
      <el-form-item v-else :label="retainingSavedCredential ? '新密码（留空保留已保存凭据）' : '密码'" class="connection-form__wide">
        <el-input v-model="password" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>
  </section>

  <details class="advanced-settings">
    <summary>高级连接设置</summary>
    <p class="connection-helper">连接超时：{{ connectTimeoutSeconds }} 秒。</p>
    <el-form label-position="top" class="form-grid">
      <el-form-item v-if="protocol === 'SSH2'" label="执行方式">
        <el-select v-model="executionMode" aria-label="SSH 执行方式">
          <el-option label="交互式 Shell（支持分页）" value="SHELL" />
          <el-option label="单命令 Exec" value="EXEC" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="protocol === 'SSH2'" label="主机密钥指纹（可选）">
        <el-input v-model="endpoint.hostKeyFingerprint" autocomplete="off" placeholder="SHA256:..." />
        <small class="connection-helper security-warning" role="note">
          留空不校验设备身份
        </small>
      </el-form-item>
      <template v-else-if="protocol === 'SERIAL'">
        <el-form-item label="波特率">
          <el-select v-model="serialParams.baudRate" aria-label="波特率">
            <el-option v-for="rate in SERIAL_BAUD_RATES" :key="rate" :label="String(rate)" :value="rate" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据位">
          <el-select v-model="serialParams.dataBits" aria-label="数据位">
            <el-option label="8" :value="8" />
            <el-option label="7" :value="7" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验">
          <el-select v-model="serialParams.parity" aria-label="校验">
            <el-option v-for="option in SERIAL_PARITY_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="停止位">
          <el-select v-model="serialParams.stopBits" aria-label="停止位">
            <el-option v-for="option in SERIAL_STOP_BIT_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="流控">
          <el-select v-model="serialParams.flowControl" aria-label="流控">
            <el-option v-for="option in SERIAL_FLOW_CONTROL_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="输入换行方式">
          <el-select v-model="serialPrompts.lineEnding" aria-label="串口输入换行方式">
            <el-option label="自动（终端回车）" value="AUTO" />
            <el-option label="CRLF" value="CRLF" />
            <el-option label="CR" value="CR" />
            <el-option label="LF" value="LF" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录名提示符"><el-input v-model="serialPrompts.login" class="command-input" autocomplete="off" /></el-form-item>
        <el-form-item label="密码提示符"><el-input v-model="serialPrompts.password" class="command-input" autocomplete="off" /></el-form-item>
        <el-form-item label="命令提示符" class="form-grid__wide"><el-input v-model="serialPrompts.command" class="command-input" autocomplete="off" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="输入换行方式">
          <el-select v-model="telnetPrompts.lineEnding" aria-label="Telnet 输入换行方式">
            <el-option label="自动（终端回车）" value="AUTO" />
            <el-option label="CRLF" value="CRLF" />
            <el-option label="CR" value="CR" />
            <el-option label="LF" value="LF" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录名提示符"><el-input v-model="telnetPrompts.login" class="command-input" autocomplete="off" /></el-form-item>
        <el-form-item label="密码提示符"><el-input v-model="telnetPrompts.password" class="command-input" autocomplete="off" /></el-form-item>
        <el-form-item label="命令提示符" class="form-grid__wide"><el-input v-model="telnetPrompts.command" class="command-input" autocomplete="off" /></el-form-item>
      </template>
    </el-form>
  </details>

  <div class="connection-actions">
    <el-button v-if="connectionMode === 'SAVED'" size="small" type="primary" :loading="savedConnectionSaving" @click="verifyAndSaveConnection">
      {{ savedConnectionSaving ? '正在验证并保存' : '验证并保存连接' }}
    </el-button>
    <el-button size="small" type="primary" :loading="testing" @click="runTest">
      {{ testing ? '正在测试' : '测试连接' }}
    </el-button>
    <el-button size="small" @click="clearCredentials">清除凭据</el-button>
    <el-button size="small" disabled aria-label="交互式终端，规划中">交互终端</el-button>
    <el-tag size="small" type="info">规划中</el-tag>
  </div>

  <dl v-if="testResult" class="connection-diagnostics" aria-live="polite">
    <div><dt>结果</dt><dd>{{ testResult.reachable ? '连接成功' : '连接失败' }}</dd></div>
    <div><dt>阶段</dt><dd>{{ testResult.stage || 'COMPLETE' }}</dd></div>
    <div><dt>耗时</dt><dd>{{ testResult.durationMillis }} ms</dd></div>
    <div v-if="testResult.errorCode"><dt>错误码</dt><dd>{{ testResult.errorCode }}</dd></div>
    <p>{{ testResult.safeMessage }}</p>
  </dl>
  <p v-if="testError" class="inline-error" role="alert">{{ testError }}</p>
</template>

<style scoped>
.connection-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.5rem; padding: 0.375rem 0.5rem; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-lighter); }
.connection-form--basic { display: grid; grid-template-columns: minmax(0, 1fr) 5.5rem; gap: 0 0.75rem; }
.connection-form--authentication { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0; }
.connection-form__wide { grid-column: 1 / -1; }
.connection-form__serial-select { width: 10rem; flex: 0 0 auto; margin-left: 0.5rem; }
.advanced-settings { margin: 0.625rem 0; padding: 0.625rem; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-lighter); }
.saved-connections { margin: 0.5rem 0; padding: 0.625rem; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-bg-color); }
.saved-connections__heading, .saved-connections__controls { display: flex; align-items: center; gap: 0.5rem; }
.saved-connections__heading { margin-bottom: 0; }
.saved-connections__heading strong { font-size: var(--el-font-size-base); line-height: 1.25; }
.saved-connections:has(.saved-connections__controls) .saved-connections__heading { margin-bottom: 0.5rem; }
.connection-helper { font-size: var(--el-font-size-small); line-height: 1.45; }
.saved-connections__heading .connection-helper { display: block; margin-top: 0.125rem; color: var(--el-text-color-secondary); }
.saved-connections__controls { flex-wrap: wrap; }
.saved-connections__controls :deep(.el-select) { flex: 1 1 12rem; min-width: 0; }
.saved-connections__hint, .saved-credential-state, .saved-connections__dirty { margin: 0.5rem 0 0; }
.saved-connections__hint, .saved-credential-state { color: var(--el-text-color-secondary); }
.saved-connections__dirty { color: var(--el-color-warning); }
.advanced-settings summary { cursor: pointer; font-size: var(--el-font-size-base); font-weight: 700; line-height: 1.25; }
.advanced-settings[open] summary { margin-bottom: 0.75rem; }
.advanced-settings > .connection-helper { margin: 0 0 0.75rem; color: var(--el-text-color-secondary); }
.security-warning { display: block; margin-top: 0.35rem; color: var(--el-color-warning); line-height: 1.45; }
.connection-actions { align-items: center; }
@media (max-width: 767px) {
  .connection-form--basic, .connection-form--authentication { grid-template-columns: 1fr; }
  .connection-form__wide { grid-column: auto; }
  .connection-toolbar, .saved-connections__heading { align-items: flex-start; flex-direction: column; }
  .connection-toolbar > :last-child { width: 100%; }
  .connection-toolbar :deep(.el-radio-button) { flex: 1 1 0; }
  .connection-toolbar :deep(.el-radio-button__inner) { width: 100%; }
  .saved-connections__controls > * { width: 100%; }
  .connection-actions :deep(.el-button) { flex: 1 1 auto; }
}
</style>
