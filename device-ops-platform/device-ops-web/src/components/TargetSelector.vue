<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { findDevices, findProjects } from '@/api/device-ops'
import ConnectionSourceTabs from '@/components/ConnectionSourceTabs.vue'
import PanelHeader from '@/components/PanelHeader.vue'
import ProtocolConnectionForm from '@/components/ProtocolConnectionForm.vue'
import type { RecentConnection } from '@/stores/recent-connections'
import type {
  ConnectionRequest,
  ConnectionSource,
  DeviceProjection,
  ProjectProjection
} from '@/types/collection'

const props = defineProps<{ projectKey: string }>()

const sourceTabsRef = ref<InstanceType<typeof ConnectionSourceTabs>>()
const connectionFormRef = ref<InstanceType<typeof ProtocolConnectionForm>>()
const source = ref<ConnectionSource>('QUICK')
const attachContext = ref(false)
const projects = ref<ProjectProjection[]>([])
const devices = ref<DeviceProjection[]>([])
const selectedProjectKey = ref('')
const selectedDeviceKey = ref('')
const masterDataLoading = ref(false)
const masterDataError = ref('')
const recentDeviceLabel = ref('')
const loadedProjectQuery = ref<string>()

const contextVisible = computed(
  () => source.value === 'PROJECT_DEVICE' || attachContext.value
)
const selectedProject = computed(() =>
  projects.value.find((project) => project.projectKey === selectedProjectKey.value)
)
const selectedDevice = computed(() =>
  devices.value.find((device) => device.deviceKey === selectedDeviceKey.value)
)

let projectGeneration = 0
let deviceGeneration = 0
let pendingProjectQuery: string | undefined

async function loadProjects() {
  const routeKey = props.projectKey
  const query = routeKey === 'direct' ? '' : routeKey
  if (pendingProjectQuery === query || loadedProjectQuery.value === query) return
  const generation = ++projectGeneration
  pendingProjectQuery = query
  masterDataLoading.value = true
  masterDataError.value = ''
  try {
    const response = await findProjects(query)
    if (generation !== projectGeneration) return
    projects.value = response
    loadedProjectQuery.value = query
    const routeProject = response.find((project) => project.projectKey === routeKey)
    if (routeProject) selectedProjectKey.value = routeProject.projectKey
  } catch {
    if (generation !== projectGeneration) return
    projects.value = []
    devices.value = []
    masterDataError.value = '只读主档暂不可用；仍可不带上下文直接连接。'
  } finally {
    if (generation === projectGeneration) {
      masterDataLoading.value = false
      pendingProjectQuery = undefined
    }
  }
}

async function loadDevices() {
  const generation = ++deviceGeneration
  const projectKey = selectedProjectKey.value
  selectedDeviceKey.value = ''
  devices.value = []
  masterDataError.value = ''
  if (!projectKey) return
  try {
    const response = await findDevices(projectKey)
    if (generation === deviceGeneration) devices.value = response
  } catch {
    if (generation !== deviceGeneration) return
    masterDataError.value = '设备选项加载失败；可清除项目上下文后继续连接。'
  }
}

onBeforeUnmount(() => {
  projectGeneration += 1
  deviceGeneration += 1
})

watch(selectedProjectKey, loadDevices)
watch(
  [source, attachContext],
  ([currentSource, currentAttachContext]) => {
    if (currentSource === 'PROJECT_DEVICE' || currentAttachContext) {
      void loadProjects()
    } else {
      projectGeneration += 1
      deviceGeneration += 1
      pendingProjectQuery = undefined
      masterDataLoading.value = false
      masterDataError.value = ''
      selectedProjectKey.value = ''
      selectedDeviceKey.value = ''
      devices.value = []
    }
  }
)
watch(
  () => props.projectKey,
  () => {
    selectedProjectKey.value = ''
    selectedDeviceKey.value = ''
    projects.value = []
    devices.value = []
    loadedProjectQuery.value = undefined
    if (contextVisible.value) void loadProjects()
  }
)

function applyRecent(item: RecentConnection) {
  recentDeviceLabel.value = item.deviceLabel ?? ''
  connectionFormRef.value?.applyRecent(item)
}

function deviceLabel(): string {
  return (
    selectedDevice.value?.deviceName ||
    selectedDevice.value?.deviceKey ||
    recentDeviceLabel.value ||
    connectionFormRef.value?.connectionLabel() ||
    'device'
  )
}

function remember(item: RecentConnection) {
  sourceTabsRef.value?.remember({
    ...item,
    deviceLabel:
      selectedDevice.value?.deviceName ||
      selectedDevice.value?.deviceKey ||
      recentDeviceLabel.value ||
      item.host
  })
}

function clearContext() {
  selectedProjectKey.value = ''
  selectedDeviceKey.value = ''
}

function buildSelection(): {
  namespace: string
  project?: ProjectProjection
  device?: DeviceProjection
  connection: ConnectionRequest
  deviceLabel: string
} {
  const connection = connectionFormRef.value?.buildConnection()
  if (!connection) throw new Error('连接表单尚未就绪。')
  const project =
    contextVisible.value && selectedProject.value ? { ...selectedProject.value } : undefined
  const device =
    contextVisible.value && selectedDevice.value ? { ...selectedDevice.value } : undefined
  if (device && !project) throw new Error('设备上下文必须关联项目。')
  return {
    namespace: project?.namespace || 'standalone',
    project,
    device,
    connection,
    deviceLabel: deviceLabel()
  }
}

function rememberCurrent() {
  const selection = buildSelection()
  const current = connectionFormRef.value?.describeRecent(selection.deviceLabel)
  if (current) sourceTabsRef.value?.remember(current)
}

function clearCredentials() {
  connectionFormRef.value?.clearCredentials()
}

defineExpose({ buildSelection, clearCredentials, rememberCurrent })
</script>

<template>
  <el-card class="connection-workbench" shadow="never" aria-label="快速连接">
    <template #header>
    <PanelHeader
      title="快速连接"
      icon="⌁"
    />
    </template>

    <ConnectionSourceTabs
      ref="sourceTabsRef"
      v-model="source"
      @select="applyRecent"
    />

    <div v-if="source !== 'PROJECT_DEVICE'" class="context-toggle">
      <div>
        <strong>业务上下文（可选）</strong>
        <p>关闭后不关联项目/设备</p>
      </div>
      <el-switch v-model="attachContext" aria-label="附加项目设备上下文" />
    </div>

    <div v-if="contextVisible" class="context-panel">
      <header>
        <strong>项目 / 设备上下文</strong>
        <el-button v-if="selectedProjectKey" text @click="clearContext">不带上下文</el-button>
      </header>
      <el-alert
        v-if="masterDataError"
        :title="masterDataError"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="form-grid">
        <el-form-item label="项目（可选，只读代理）">
          <el-select
            v-model="selectedProjectKey"
            :loading="masterDataLoading"
            filterable
            clearable
            placeholder="不选择则使用通用连接"
          >
            <el-option
              v-for="project in projects"
              :key="project.projectKey"
              :label="`${project.projectName || project.projectKey} · ${project.projectCode}`"
              :value="project.projectKey"
              :disabled="projectKey !== 'direct' && project.projectKey !== projectKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备（可选，只读代理）">
          <el-select
            v-model="selectedDeviceKey"
            :disabled="!selectedProjectKey"
            filterable
            clearable
            placeholder="选择设备或仅保留项目上下文"
          >
            <el-option
              v-for="device in devices"
              :key="device.deviceKey"
              :label="`${device.deviceName || device.deviceKey} · ${device.vendor} ${device.model}`"
              :value="device.deviceKey"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <ProtocolConnectionForm
      ref="connectionFormRef"
      :credential-namespace="selectedProject?.namespace || 'standalone'"
      @tested="remember"
    />

  </el-card>
</template>
