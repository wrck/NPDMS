<template>
  <div class="local-dac-status" role="status" aria-live="polite">
    <el-tag :type="state === 'ready' ? 'success' : 'info'">{{ message }}</el-tag>
    <a
      v-if="state === 'unavailable' || state === 'incompatible' || state === 'connection-required'"
      :href="installerUrl"
      @click.prevent="downloadInstaller"
      :aria-disabled="downloading"
      data-testid="dac-installer-download"
      class="local-dac-start"
    >{{ downloading ? '正在生成安装包…' : state === 'unavailable' ? '下载并安装 DAC' : '下载更新 DAC' }}</a>
    <el-button v-if="state !== 'ready'" link :loading="checking" @click="check">重新检测</el-button>
    <div v-if="state === 'connection-required' || state === 'unavailable' || connecting" class="local-dac-pairing">
      <a v-if="prepared && !connecting" :href="prepared.href" class="local-dac-start" data-testid="dac-connect" @click="connect">连接本机 DAC</a>
      <span v-else-if="connecting || !connectionError">{{ connecting ? '请允许打开客户端，正在等待本机连接确认…' : '正在准备本机连接…' }}</span>
      <el-button v-if="connecting" link @click="cancelConnection">取消连接</el-button>
      <span v-if="connectionError" role="alert">{{ connectionError }}</span>
      <el-button v-if="connectionError && !connecting" link @click="prepare">重试连接</el-button>
    </div>
    <span v-if="state === 'unavailable'" class="local-dac-hint">
      尚未安装可下载安装；已安装请启动服务，并检查浏览器本机访问权限。
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { discoverLocalDac } from './localDac'
import type { LocalDacDiscovery } from './localDac'
import { prepareLocalDacConnection } from './localDacConnection'
import type { PreparedLocalConnection } from './localDacConnection'

const props = defineProps<{ serverUrl: string }>()
const installerFile = import.meta.env.VITE_DAC_INSTALLER_FILENAME || 'SDP-DAC-1.1.8-x64.msi'
const installerUrl = `${import.meta.env.BASE_URL}downloads/dac/${installerFile}`
const downloading = ref(false)
async function downloadInstaller() {
  if (downloading.value) return
  downloading.value = true
  try {
    const server = new URL(props.serverUrl)
    if (!['http:', 'https:'].includes(server.protocol) || server.username || server.password || server.search || server.hash) throw new Error('服务器地址无效')
    const response = await fetch(installerUrl)
    if (!response.ok) throw new Error('安装包下载失败')
    const installer = new Uint8Array(await response.arrayBuffer())
    if (![0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1].every((byte, index) => installer[index] === byte)) throw new Error('下载内容不是 MSI 安装包')
    const { default: JSZip } = await import('jszip')
    const zip = new JSZip()
    const version = installerFile.match(/-(\d+\.\d+\.\d+)-x64(?:-[a-f0-9]+)?\.msi$/)?.[1]
    if (!version) throw new Error('安装包版本信息缺失')
    const packageName = `SDP-DAC-${version}-x64`
    zip.file(`${packageName}.msi`, installer)
    zip.file('dac-environment.ini', `[Environment]\r\nServerUrl=${server.href.replace(/\/$/, '')}\r\nWebOrigin=${window.location.origin}\r\n`)
    zip.file('安装说明.txt', `产品：SDP DAC\r\n版本：${version}\r\n平台：Windows x64\r\n请先解压全部文件，再运行 ${packageName}.msi。安装器将读取同目录的 dac-environment.ini，自动填入当前服务器地址。请在安装界面核对环境。`)
    const url = URL.createObjectURL(await zip.generateAsync({ type: 'blob' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${packageName}.zip`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch {
    ElMessage.error('DAC 安装包生成失败，请检查服务器地址和安装包是否已发布')
  } finally { downloading.value = false }
}
const emit = defineEmits<{ ready: [ready: boolean] }>()
const state = ref<LocalDacDiscovery['state'] | 'checking'>('checking')
const checking = ref(false)
const prepared = ref<PreparedLocalConnection>()
const connecting = ref(false)
const connectionError = ref('')
let preparing = false
let connectionLifetime: AbortController | undefined
let preparationTimer: ReturnType<typeof setTimeout> | undefined
const messages = {
  checking: '正在检测本机 DAC',
  ready: '本机 DAC 已就绪',
  starting: '本机 DAC 正在启动',
  unavailable: '未连接到本机 DAC',
  incompatible: '请更新 DAC 客户端',
  'connection-required': '首次使用，请连接本机 DAC，无需输入配对码',
  'identity-mismatch': 'DAC 身份校验失败，请核对本机安装，不会发送设备密码',
  'wrong-environment': 'DAC 环境不一致，请在客户端设置中修改'
}
const message = computed(() => messages[state.value])
const lifetime = new AbortController()
let timer: ReturnType<typeof setTimeout> | undefined
let startupDeadline = 0

async function prepare() {
  if (preparing || connecting.value || lifetime.signal.aborted || prepared.value) return
  preparing = true
  connectionError.value = ''
  try {
    const connection = await prepareLocalDacConnection({ serverUrl: props.serverUrl, webOrigin: window.location.origin }, lifetime.signal)
    if (lifetime.signal.aborted || !['connection-required', 'unavailable'].includes(state.value)) { void connection.cancel(); return }
    prepared.value = connection
    clearTimeout(preparationTimer)
    preparationTimer = setTimeout(() => {
      if (connecting.value) return
      prepared.value = undefined
      void connection.cancel()
      void prepare()
    }, Math.max(0, connection.expiresAt - Date.now() - 5000))
  } catch {
    if (!lifetime.signal.aborted) connectionError.value = '无法准备本机连接，请检查登录和网络后重试'
  } finally { preparing = false }
}

function cancelConnection() {
  connectionLifetime?.abort()
  const connection = prepared.value
  prepared.value = undefined
  clearTimeout(preparationTimer)
  void connection?.cancel()
}

async function connect(event: MouseEvent) {
  const connection = prepared.value
  if (!connection || connecting.value || lifetime.signal.aborted || Date.now() >= connection.expiresAt) {
    event.preventDefault()
    cancelConnection()
    void prepare()
    return
  }
  // Let the actual anchor click launch the protocol synchronously; polling never launches it.
  clearTimeout(timer)
  clearTimeout(preparationTimer)
  connecting.value = true
  connectionError.value = ''
  const attempt = new AbortController()
  connectionLifetime = attempt
  try {
    const result = await connection.finish(attempt.signal)
    if (lifetime.signal.aborted || attempt.signal.aborted) return
    state.value = result.state
    emit('ready', result.state === 'ready')
  } catch (error) {
    if (!lifetime.signal.aborted && !attempt.signal.aborted) connectionError.value = error instanceof Error ? error.message : '连接失败，请重新连接'
  } finally {
    connecting.value = false
    if (prepared.value === connection) prepared.value = undefined
    void connection.cancel()
    if (!lifetime.signal.aborted && state.value !== 'ready') timer = setTimeout(check, 15000)
  }
}

async function check() {
  if (checking.value || connecting.value || lifetime.signal.aborted) return
  clearTimeout(timer)
  checking.value = true
  const result = await discoverLocalDac({
    serverUrl: props.serverUrl,
    webOrigin: window.location.origin,
    signal: lifetime.signal
  })
  checking.value = false
  if (lifetime.signal.aborted) return
  state.value = result.state
  emit('ready', result.state === 'ready')
  if (['connection-required', 'unavailable'].includes(result.state) && !connectionError.value) void prepare()
  else if (!['connection-required', 'unavailable'].includes(result.state)) cancelConnection()
  if (result.state === 'ready') return
  if (Date.now() < startupDeadline) {
    timer = setTimeout(check, 1500)
  } else {
    timer = setTimeout(check, 15000)
  }
}

onMounted(() => {
  startupDeadline = Date.now() + 60000
  void check()
})
onBeforeUnmount(() => {
  lifetime.abort()
  cancelConnection()
  clearTimeout(timer)
})
</script>

<style scoped>
.local-dac-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.local-dac-start {
  color: var(--el-color-primary);
  text-decoration: underline;
}

.local-dac-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
