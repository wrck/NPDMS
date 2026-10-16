<template>
  <section class="device-card" aria-label="设备序列号表">
    <header class="device-card__header">
      <h2><Icon icon="ep:monitor" />设备序列号表</h2>
      <small>已选择 {{ modelValue.length }} 台设备</small>
    </header>
    <div class="device-card__body">
      <div class="device-toolbar">
        <slot name="serial-query">
          <el-input
            v-model="serialText"
            :disabled="disabled || resolving || !projectId || projectLoading || !!projectError"
            placeholder="输入设备序列号；多个序列号可用逗号分隔"
            aria-label="设备序列号"
            data-testid="create-serials"
            @keyup.enter="resolveSerials"
          />
          <el-button
            :disabled="disabled || !projectId || projectLoading || !!projectError || !serialText.trim()"
            :loading="resolving"
            data-testid="resolve-create-serials"
            @click="resolveSerials"
            >查询并添加</el-button
          >
        </slot>
        <el-button
          :disabled="disabled || !projectId || projectLoading || !!projectError"
          data-testid="choose-project-devices"
          @click="openPicker"
          >选择项目已发货设备</el-button
        >
      </div>
      <p class="device-help"
        ><slot name="hint"
          >请选择项目后添加已发货设备。创建前可增删设备，创建后设备范围只读。</slot
        ></p
      >
      <el-alert v-if="serialError" :title="serialError" type="error" :closable="false" />
      <el-table
        :data="modelValue"
        row-key="deviceId"
        :empty-text="emptyText"
        data-testid="create-device-table"
      >
        <el-table-column type="index" label="序号" width="65" />
        <el-table-column prop="serialNumber" label="序列号" min-width="220" />
        <el-table-column prop="productCode" label="产品编码" min-width="180" />
        <el-table-column prop="productModel" label="产品型号" min-width="180" />
        <el-table-column label="当前软件版本" min-width="180"><template #default="{ row }">{{ row.currentSoftwareVersion || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="80"
          ><template #default="{ row }">
            <el-button
              link
              type="danger"
              :disabled="disabled"
              :data-testid="`remove-create-device-${row.deviceId}`"
              @click="removeDevice(row.deviceId)"
              >移除</el-button
            >
          </template></el-table-column
        >
      </el-table>
    </div>
    <el-dialog
      v-model="pickerVisible"
      title="选择项目已发货设备"
      width="min(960px, 94vw)"
      append-to-body
      align-center
    >
      <div class="device-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="按序列号、产品编码或型号查询"
          aria-label="查找项目设备"
          @keyup.enter="search"
        />
        <el-button :loading="loading" @click="search">查询</el-button>
      </div>
      <el-alert v-if="pickerError" :title="pickerError" type="error" :closable="false" />
      <el-table
        v-loading="loading"
        :data="choices"
        row-key="deviceId"
        empty-text="当前项目没有匹配的已发货设备"
      >
        <el-table-column prop="serialNumber" label="序列号" min-width="210" />
        <el-table-column prop="productCode" label="产品编码" min-width="150" />
        <el-table-column prop="productModel" label="产品型号" min-width="180" />
        <el-table-column label="当前软件版本" min-width="180"><template #default="{ row }">{{ row.currentSoftwareVersion || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="85"
          ><template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="disabled || isSelected(row.deviceId)"
              :data-testid="`add-project-device-${row.deviceId}`"
              @click="addDevices([row])"
              >{{ isSelected(row.deviceId) ? '已添加' : '添加' }}</el-button
            >
          </template></el-table-column
        >
      </el-table>
      <Pagination
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="loadDevices"
      />
      <template #footer
        ><el-button type="primary" @click="pickerVisible = false">完成</el-button></template
      >
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  getProjectDevicePage,
  type ProjectDevice,
  type ProjectSelectionId
} from '@/api/pms/project/selection'

const props = withDefaults(
  defineProps<{
    modelValue: ProjectDevice[]
    projectId?: ProjectSelectionId
    disabled?: boolean
    projectLoading?: boolean
    projectError?: string
    emptyText?: string
  }>(),
  { emptyText: '请添加本次任务设备' }
)
const emit = defineEmits<{
  'update:modelValue': [devices: ProjectDevice[]]
  busy: [value: boolean]
}>()
const serialText = ref('')
const resolving = ref(false)
const serialError = ref('')
const pickerVisible = ref(false)
const loading = ref(false)
const pickerError = ref('')
const choices = ref<ProjectDevice[]>([])
const total = ref(0)
const query = reactive({ keyword: '', pageNo: 1, pageSize: 20 })
let projectSequence = 0
let pageSequence = 0
const busy = computed(() => resolving.value || loading.value)
watch(busy, (value) => emit('busy', value), { immediate: true })
const isSelected = (id: ProjectSelectionId) =>
  props.modelValue.some((device) => String(device.deviceId) === String(id))
const addDevices = (devices: ProjectDevice[]) => {
  if (props.disabled) return
  const selected = new Map(props.modelValue.map((device) => [String(device.deviceId), device]))
  devices.forEach((device) => selected.set(String(device.deviceId), device))
  emit('update:modelValue', [...selected.values()])
}
const removeDevice = (id: ProjectSelectionId) => {
  if (!props.disabled)
    emit(
      'update:modelValue',
      props.modelValue.filter((device) => String(device.deviceId) !== String(id))
    )
}
const loadDevices = async () => {
  if (!props.projectId || props.disabled) return
  const project = String(props.projectId)
  const request = ++pageSequence
  loading.value = true
  pickerError.value = ''
  try {
    const page = await getProjectDevicePage(project, { ...query })
    if (request !== pageSequence || project !== String(props.projectId)) return
    choices.value = page.list
    total.value = Number(page.total)
  } catch {
    if (request === pageSequence) {
      choices.value = []
      total.value = 0
      pickerError.value = '设备列表加载失败，请重试查询。'
    }
  } finally {
    if (request === pageSequence) loading.value = false
  }
}
const search = async () => {
  query.pageNo = 1
  await loadDevices()
}
const openPicker = async () => {
  if (!props.projectId || props.disabled || props.projectLoading || props.projectError) return
  choices.value = []
  total.value = 0
  Object.assign(query, { keyword: '', pageNo: 1, pageSize: 20 })
  pickerVisible.value = true
  await loadDevices()
}
const resolveSerials = async () => {
  if (!props.projectId || props.disabled || resolving.value || !serialText.value.trim()) return
  const serials = [...new Set(serialText.value.split(/[,，;；\s]+/).filter(Boolean))]
  if (serials.length > 100) {
    serialError.value = '每次最多查询100个设备序列号'
    return
  }
  const project = String(props.projectId)
  const request = projectSequence
  resolving.value = true
  serialError.value = ''
  try {
    const selected: ProjectDevice[] = []
    for (const serial of serials) {
      let found: ProjectDevice | undefined
      for (let pageNo = 1; !found; pageNo++) {
        const page = await getProjectDevicePage(project, { keyword: serial, pageNo, pageSize: 100 })
        if (request !== projectSequence) return
        found = page.list.find((device) => device.serialNumber === serial)
        if (!page.list.length || pageNo * 100 >= Number(page.total)) break
      }
      if (!found) {
        serialError.value = `当前项目未找到已发货设备：${serial}，本次未添加任何设备。`
        return
      }
      selected.push(found)
    }
    if (request === projectSequence && !props.disabled) {
      addDevices(selected)
      serialText.value = ''
    }
  } catch {
    if (request === projectSequence) serialError.value = '序列号查询失败，请重试。'
  } finally {
    if (request === projectSequence) resolving.value = false
  }
}
watch(
  () => props.projectId,
  () => {
    ++projectSequence
    ++pageSequence
    pickerVisible.value = false
    resolving.value = false
    loading.value = false
    serialText.value = ''
    serialError.value = ''
    pickerError.value = ''
    choices.value = []
    total.value = 0
  }
)
onBeforeUnmount(() => {
  ++projectSequence
  ++pageSequence
})
</script>

<style scoped>
.device-card {
  width: 100%;
  margin-bottom: 16px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}
.device-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f2f5;
}
.device-card__header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.device-card__header small,
.device-help {
  color: #888;
  font-size: 12px;
}
.device-card__body {
  padding: 20px;
}
.device-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.device-toolbar :deep(.el-input) {
  flex: 1;
  min-width: 180px;
}
@media (width <= 767px) {
  .device-toolbar {
    flex-wrap: wrap;
  }
}
</style>
