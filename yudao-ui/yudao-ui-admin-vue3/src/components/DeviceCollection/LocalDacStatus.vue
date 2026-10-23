<template>
  <div class="local-dac-status" role="status" aria-live="polite">
    <el-tag :type="state === 'ready' ? 'success' : 'info'">{{ message }}</el-tag>
    <a
      v-if="state === 'unavailable'"
      :href="LOCAL_DAC_START_URI"
      class="local-dac-start"
      @click="waitForStartup"
    >启动 DAC</a>
    <a
      v-if="state === 'unavailable' || state === 'incompatible'"
      :href="installerUrl"
      data-testid="dac-installer-download"
      download="NPDMS-DAC-1.0.6-x64.msi"
      class="local-dac-start"
    >{{ state === 'incompatible' ? '下载更新 DAC' : '下载并安装 DAC' }}</a>
    <el-button v-if="state !== 'ready'" link :loading="checking" @click="check">重新检测</el-button>
    <span v-if="state === 'unavailable'" class="local-dac-hint">
      尚未安装可下载安装；已安装请启动服务，并检查浏览器本机访问权限。
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { discoverLocalDac, LOCAL_DAC_START_URI } from './localDac'
import type { LocalDacDiscovery } from './localDac'

const props = defineProps<{ serverUrl: string }>()
const installerUrl = `${import.meta.env.BASE_URL}downloads/dac/NPDMS-DAC-1.0.6-x64.msi`
const emit = defineEmits<{ ready: [ready: boolean] }>()
const state = ref<LocalDacDiscovery['state'] | 'checking'>('checking')
const checking = ref(false)
const messages = {
  checking: '正在检测本机 DAC',
  ready: '本机 DAC 已就绪',
  starting: '本机 DAC 正在启动',
  unavailable: '未连接到本机 DAC',
  incompatible: '请更新 DAC 客户端',
  'wrong-environment': 'DAC 环境不一致，请在客户端设置中修改'
}
const message = computed(() => messages[state.value])
const lifetime = new AbortController()
let timer: ReturnType<typeof setTimeout> | undefined
let startupDeadline = 0

async function check() {
  if (checking.value || lifetime.signal.aborted) return
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
  if (result.state !== 'ready' && Date.now() < startupDeadline) {
    timer = setTimeout(check, 1500)
  } else {
    timer = setTimeout(check, 15000)
  }
}

function waitForStartup() {
  // Only the user's anchor click launches the protocol; polling never opens the application.
  clearTimeout(timer)
  startupDeadline = Date.now() + 60000
  timer = setTimeout(check, 1500)
}

onMounted(() => {
  startupDeadline = Date.now() + 60000
  void check()
})
onBeforeUnmount(() => {
  lifetime.abort()
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
